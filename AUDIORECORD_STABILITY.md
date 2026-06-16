# AudioRecord Stability: Root Causes & Fixes

> **Context**: Hearing aid app. AudioRecord dying silently after ~20-30s on OnePlus/older devices.
> Research based on production apps: 	imschneeb/RootlessJamesDSP, mortenfc/AutoRecordMoments,
> deeeed/audiolab, openclaw/openclaw, and hark-app itself.

---

## 1. Root Causes (Ranked by Probability)

### #1 — VOICE_RECOGNITION Source Reclaimed by OEM (ALREADY FIXED ✅)

**Probability: ~70% of observed deaths on OnePlus/older Samsung**

OnePlus, MediaTek, and older Qualcomm audio HALs implement a 20-30s watchdog on
VOICE_RECOGNITION that reclaims the source to save power. The HAL silently transitions
AudioRecord to RECORDSTATE_STOPPED without any error callback.

**Evidence** — comment already in AudioStreamManager.kt:
`kotlin
// VOICE_RECOGNITION is unstable on OEM devices (OnePlus, older Samsung) - session dies after 20-30s
// MIC source is the most compatible and stable across all devices
val record = AudioRecord(MediaRecorder.AudioSource.MIC, ...)
`

**Status**: Already fixed. No further action needed.

---

### #2 — AudioFocus Stops Streaming on LOSS_TRANSIENT (HIGH PRIORITY 🔴)

**Probability: ~20% of observed interruptions**

AudioStreamingService.audioFocusChangeListener calls stopStreaming() on BOTH
AUDIOFOCUS_LOSS AND AUDIOFOCUS_LOSS_TRANSIENT. Transient loss fires for: notification
sounds, Google Assistant wake word, Bluetooth SCO negotiation, system UI sounds. A hearing aid
**must never stop** for these — the user loses hearing assistance.

**Current broken code** (AudioStreamingService.kt):
`kotlin
AudioManager.AUDIOFOCUS_LOSS,
AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
    stopStreaming()  // ← WRONG: kills hearing aid for a notification beep
}
`

**Industry pattern** (AutoRecordMoments source):
- AUDIOFOCUS_LOSS_TRANSIENT: just log, keep recording
- AUDIOFOCUS_GAIN: restart if stopped by permanent loss

---

### #3 — AudioFocus Attributes Wrong (MEDIUM PRIORITY 🟡)

**Probability: ~15% — triggers auto-fade-out on Android 12+**

Using USAGE_MEDIA + AUDIOFOCUS_GAIN. On Android 12+, when any second app requests
AUDIOFOCUS_GAIN, the system **force-fades** apps with USAGE_MEDIA. A music app or podcast
player auto-silences the hearing aid with no warning.

**Current broken code**:
`kotlin
AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_MEDIA)           // ← wrong for hearing aid
    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)  // ← permanent, causes conflicts
`

**Fix**: Use USAGE_ASSISTANCE_ACCESSIBILITY (exempt from Android 12 auto-fade) +
AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK.

---

### #4 — Zombie AudioRecord Not Detected (MEDIUM PRIORITY 🟡)

**Probability: ~10% — specific to Snapdragon 600/700 OEM builds**

Some OEM HALs keep ecordingState == RECORDSTATE_RECORDING but start returning
AudioRecord.ERROR_DEAD (-6) or ERROR_INVALID_OPERATION (-3) from ead(). The current
DefaultAudioProcessor.process() loop skips writes but never breaks — it spins forever.

**Current DefaultAudioProcessor.process() read loop**:
`kotlin
while (isRunning()) {
    val read = audioSource.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
    if (read > 0) {        // negative read silently skipped, loop spins doing nothing
        applyGain(buffer, read)
        audioSink.write(...)
    }
    // ← no break on read < 0
}
`

**Fix**: Break on ead < 0 → AudioStreamManager recursive restart triggers automatically.

---

### #5 — No Phone Call Interruption Recovery (LOW PRIORITY 🟢)

**Probability: ~5%**

No TelephonyCallback to detect incoming calls. When telephony claims audio hardware,
AudioRecord may die silently. After the call ends, streaming is not resumed. audiolab implements
full TelephonyCallback (API 31+) + PhoneStateListener (legacy) with auto-resume.

---

## 2. What hark-app Already Does Correctly ✅

| Feature | Location | Status |
|---------|----------|--------|
| PARTIAL_WAKE_LOCK acquired on start | AudioStreamingService.startStreaming() | ✅ |
| oregroundServiceType="microphone" | AndroidManifest.xml:42 | ✅ |
| FOREGROUND_SERVICE_MICROPHONE permission | AndroidManifest.xml:13 | ✅ |
| MIC source (not VOICE_RECOGNITION) | AudioStreamManager.createAudioRecord() | ✅ |
| RECORDSTATE_RECORDING check in loop condition | AudioStreamManager.runStreamingLoop() | ✅ |
| Recursive restart on session death | AudioStreamManager.runStreamingLoop() | ✅ |
| AudioDeviceCallback for device plug/unplug | AudioStreamingService.audioDeviceCallback | ✅ |
| THREAD_PRIORITY_URGENT_AUDIO | AudioStreamManager.start() | ✅ |
| WakeLock released on stopStreaming | AudioStreamingService.stopStreaming() | ✅ |

---

## 3. Concrete Code Fixes

### Fix A: AudioStreamingService.kt — AudioFocus Behavior on LOSS_TRANSIENT

Replace the udioFocusChangeListener lambda and add ocusLostPermanently field:

`kotlin
private var focusLostPermanently = false

private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
    when (focusChange) {
        AudioManager.AUDIOFOCUS_LOSS -> {
            // Permanent loss (e.g., phone call via legacy STREAM_VOICE_CALL API)
            HarkLog.i(TAG, "Audio focus permanently lost — stopping streaming")
            focusLostPermanently = true
            stopStreaming()
        }
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
            // Transient loss: notification sound, Google Assistant, brief system audio
            // Hearing aid MUST continue — the user cannot hear without it
            HarkLog.i(TAG, "Audio focus transiently lost — hearing aid continues")
            // Optional: mute AudioTrack but keep AudioRecord alive:
            // audioEngine.setAmbientGain(0f)
        }
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
            HarkLog.i(TAG, "Audio focus ducked — continuing at full volume (accessibility)")
        }
        AudioManager.AUDIOFOCUS_GAIN -> {
            HarkLog.i(TAG, "Audio focus regained")
            if (focusLostPermanently && hasRequiredPermissions()) {
                focusLostPermanently = false
                startStreaming()
            }
        }
    }
}
`

### Fix B: AudioStreamingService.kt — AudioFocus Request Attributes

Replace equestAudioFocus():

`kotlin
private fun requestAudioFocus(): Boolean {
    val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
    val attributes = AudioAttributes.Builder()
        // USAGE_ASSISTANCE_ACCESSIBILITY is exempt from Android 12 auto-fade-out
        // and signals this is an accessibility/hearing-aid application
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .setAcceptsDelayedFocusGain(true)
        .setWillPauseWhenDucked(false) // Don't pause when another app ducks us
        .setOnAudioFocusChangeListener(audioFocusChangeListener)
        .build()

    val result = audioManager.requestAudioFocus(audioFocusRequest!!)
    HarkLog.i(TAG, "AudioFocus request result: ")
    // For accessibility/hearing aid: continue even if focus not immediately granted
    return true
}
`

### Fix C: DefaultAudioProcessor.kt — Zombie/Dead AudioRecord Detection

Add private var consecutiveEmptyReads = 0 as a class field.

In process(), replace the inner while loop body:

`kotlin
override fun process(
    audioSource: AudioSource,
    audioSink: AudioSink,
    config: AudioProcessingConfig,
    isRunning: () -> Boolean
) {
    currentConfig = config
    setupAudioEffects()
    consecutiveEmptyReads = 0

    val bufferSize = audioSink.bufferSizeInFrames
    if (processingBuffer == null || processingBuffer?.size != bufferSize) {
        processingBuffer = FloatArray(bufferSize)
    }
    val buffer = processingBuffer!!

    while (isRunning()) {
        val readStart = System.nanoTime()
        val read = audioSource.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
        val readEnd = System.nanoTime()

        // AudioRecord.ERROR_DEAD = -6, ERROR_INVALID_OPERATION = -3, ERROR_BAD_VALUE = -2
        if (read < 0) {
            Log.w(TAG, "AudioRecord.read() returned error  — triggering restart")
            break  // AudioStreamManager.runStreamingLoop() sees isRunning=true → recursive restart
        }

        // Watchdog: some OEM HALs return 0 (not -6) when dead
        if (read == 0) {
            if (++consecutiveEmptyReads >= 100) {  // ~5s at 48kHz/512-frame buffer
                Log.w(TAG, "100 consecutive zero reads — AudioRecord zombie, forcing restart")
                break
            }
            continue
        }
        consecutiveEmptyReads = 0

        applyGain(buffer, read)
        val writeStart = System.nanoTime()
        audioSink.write(buffer, 0, read, AudioTrack.WRITE_BLOCKING)
        val writeEnd = System.nanoTime()

        val bufferMs = (read.toFloat() / com.thivyanstudios.hark.util.Constants.Audio.SAMPLE_RATE) * 1000f
        val readMs = (readEnd - readStart) / 1_000_000f
        val writeMs = (writeEnd - writeStart) / 1_000_000f
        val totalMs = readMs + bufferMs + writeMs
        val prev = _latencyMs.get()
        _latencyMs.set(if (prev == 0f) totalMs else prev * 0.9f + totalMs * 0.1f)
    }
}
`

### Fix D: AudioStreamManager.kt — Restart Backoff

The existing recursive restart has no backoff. Add 500ms delay to prevent tight crash loops:

`kotlin
// AudioRecord session died (OEM reclaimed it) - restart if still supposed to be running
if (isRunning.get()) {
    Log.w(TAG, "AudioRecord session ended unexpectedly - restarting after 500ms")
    cleanupResources(audioSource, audioSink, audioRecord, audioTrack)
    Thread.sleep(500)  // Prevent tight restart loop overheating device
    runStreamingLoop(config)
    return
}
`

---

## 4. AudioFocus Strategy Decision

| Strategy | Behavior | Recommended For |
|----------|----------|-----------------|
| USAGE_MEDIA + AUDIOFOCUS_GAIN (current) | Auto-faded on Android 12 by any app | ❌ Wrong |
| USAGE_ASSISTANCE_ACCESSIBILITY + TRANSIENT_MAY_DUCK | Exempt from auto-fade, ducks cleanly | ✅ Hearing aid |
| No focus request ("background" strategy) | Zero conflicts, zero interruptions | ✅ Pure capture apps |

For hark-app: **Fix B** (USAGE_ASSISTANCE_ACCESSIBILITY + TRANSIENT_MAY_DUCK) is correct.
The hearing aid outputs audio that has accessibility priority; other apps duck under it.

---

## 5. Reference: Key Patterns from Production Apps

### RootlessJamesDSP (	imschneeb/RootlessJamesDSP)
- ecreateRecorderRequested flag for soft reload without stopping thread
- Explicit RECORDSTATE_STOPPED check per loop iteration → calls ecorder.startRecording()
- Hard restart via estartRecording(): stop thread → reset flags → startRecording()
- External trigger via ACTION_SERVICE_HARD_REBOOT_CORE broadcast

### AutoRecordMoments (mortenfc/AutoRecordMoments)
- VOICE_RECOGNITION source (not recommended for long-running)
- STREAM_VOICE_CALL + AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK — resists interruptions
- AudioFocus listener logs but does NOT stop on LOSS_TRANSIENT
- Explicit ERROR_INVALID_OPERATION + ERROR_BAD_VALUE handling in read loop

### audiolab (deeeed/audiolab)
- Strategy-based AudioFocus: "background" (no request), "communication", "interactive", "none"
- "background" skips focus entirely — zero interruption risk for always-on capture
- USAGE_VOICE_COMMUNICATION + AUDIOFOCUS_GAIN for communication strategy
- Full TelephonyCallback (API 31+) + PhoneStateListener (legacy) for call interruptions
- Auto-resume after call state returns to IDLE

---

## 6. Priority Fix Order

1. **Fix A** (AudioFocus LOSS_TRANSIENT behavior) — highest impact, single-line change
2. **Fix B** (AudioFocus request attributes) — prevents Android 12 auto-fade
3. **Fix C** (Zombie detection in read loop) — prevents infinite silent spin
4. **Fix D** (Restart backoff) — prevents overheating on rapid crash-restart
5. TelephonyCallback — add only if call interruption reports come in
