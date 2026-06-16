# Microphone Orientation Problem: Research & Fix

> **TL;DR** — The `setPreferredMicrophoneDirection` API is near-useless for the table scenario.
> The real fix is two things: (1) route to the headset mic when a wired headset WITH mic is connected,
> and (2) call `setPreferredMicrophoneDirection(MIC_DIRECTION_TOWARDS_USER)` as a best-effort hint
> on API 29+. There are two existing bugs in hark-app blocking fix (1) from working.

---

## 1. What Happens Physically

Most Android phones have their **primary bottom-edge microphone** at the charging-port end.
A secondary mic (for noise cancellation) is usually at the top edge. Some phones also have a
back-panel mic near the camera (used for `AWAY_FROM_USER` direction).

**Phone flat on table scenarios:**

| Placement | Bottom mic | Top mic | Screen mic |
|---|---|---|---|
| Face-up, bottom toward speaker | ✅ open air | ✅ open air | N/A |
| Face-up, bottom against table edge | ⚠️ partially blocked | ✅ open air | N/A |
| Face-down | ⚠️ muffled by table | ✅ but facing away | ✅ muffled by table |
| Standing upright (portrait) | ✅ open | ✅ open | N/A |

The primary failure mode is **face-down** or **bottom mic pressed against table surface**.
Face-up with the mic port in open air is actually fine.

**Root cause of muffling:** The bottom mic port is typically 1–3mm from the table surface.
The air column becomes a resonant cavity that attenuates high frequencies (>2kHz), making
speech sound muffled and "boxy".

---

## 2. Android API Landscape

### 2.1 `AudioRecord.setPreferredMicrophoneDirection()` (API 29+)

```java
// Values from MicrophoneDirection interface:
MicrophoneDirection.MIC_DIRECTION_UNSPECIFIED   = 0  // system chooses
MicrophoneDirection.MIC_DIRECTION_TOWARDS_USER  = 1  // screen side ("selfie")
MicrophoneDirection.MIC_DIRECTION_AWAY_FROM_USER = 2 // camera/back side
MicrophoneDirection.MIC_DIRECTION_EXTERNAL      = 3  // off-device mic
```

**What it actually does:** This API was designed for **video recording** — to match the audio
capture direction to which camera is in use. It tells the HAL "prefer the mic on this side of
the device."

**Critical limitations for the table scenario:**
- It only knows "toward user" vs "away from user" — it has no concept of "facing up" vs "facing down"
- It is a **hint**, not a guarantee. The HAL may ignore it
- On most OEM devices (OnePlus, Xiaomi, older Samsung), the HAL honors it only when
  `AudioSource` is `CAMCORDER` or `VOICE_RECOGNITION`. With `MIC` source it often has **no effect**
- Pixel devices honor it more reliably due to closer-to-AOSP HAL implementation
- Even when honored, it selects the physical mic but cannot prevent muffling from surface proximity

**Bottom line:** Calling `setPreferredMicrophoneDirection(MIC_DIRECTION_TOWARDS_USER)` is
worthwhile as a **low-cost hint** — it may select the screen-side mic which faces upward on a
face-up phone. But it will not fix the fundamental orientation problem and is silently ignored
on many devices.

### 2.2 `AudioRecord.getActiveMicrophones()` (API 28+)

```kotlin
val mics: List<MicrophoneInfo> = audioRecord.activeMicrophones
// MicrophoneInfo provides:
//   .location: LOCATION_MAINBODY, LOCATION_PERIPHERAL, LOCATION_UNKNOWN
//   .orientation: Coordinate3F (x, y, z orientation vector)
//   .directionality: OMNI, CARDIOID, HYPER_CARDIOID, etc.
//   .address: matches AudioDeviceInfo.address
```

Can tell you WHICH mic is active and its position on the device. Useful for diagnostics
(log which mic is being used). Cannot be used to detect table proximity at runtime.
Returns `LOCATION_PERIPHERAL` for wired/external mics — useful to confirm headset mic routing.

### 2.3 `AudioRecord.setPreferredDevice(AudioDeviceInfo)` (API 23+)

```kotlin
val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
val headsetMic = inputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET }
if (headsetMic != null) {
    audioRecord.setPreferredDevice(headsetMic)  // routes to in-line headset mic
}
```

This is the **most reliable API**. It explicitly routes the AudioRecord to a specific hardware
device. When a wired headset with mic is connected, this bypasses the phone's built-in mics
entirely. Returns `true` if successful; the actual routing is confirmed via `getRoutedDevice()`.

### 2.4 `AudioDeviceInfo` Input Types With Microphones

| Type constant | Has mic? | Notes |
|---|---|---|
| `TYPE_BUILTIN_MIC` | ✅ yes | Phone's built-in mic(s) |
| `TYPE_WIRED_HEADSET` | ✅ yes | Headset = headphones + in-line mic |
| `TYPE_WIRED_HEADPHONES` | ❌ no | Headphones only (no mic) |
| `TYPE_USB_HEADSET` | ✅ yes | USB headset with mic |
| `TYPE_BLUETOOTH_SCO` | ✅ yes | BT headset mic via SCO profile |
| `TYPE_BLE_HEADSET` | ✅ yes (API 31+) | BT LE headset |

The `HeadphoneDetector` in hark-app currently does NOT distinguish `WIRED_HEADSET` (has mic)
from `WIRED_HEADPHONES` (no mic) — it lumps both into `isWiredConnected`. This needs fixing.

---

## 3. What Production Apps Do

### Google Sound Amplifier
Source: Play Store description + accessibility docs.

- **Requires headphones** to function at all — this sidesteps the orientation problem entirely.
  The phone stays near the user (or on a desk near the user), not placed at a distance.
- Uses **Pixel's front-facing mic array** with `DIRECTION_TOWARDS_USER` for "Conversation Mode"
  on Pixel 3+. This is a Pixel-specific hardware feature, not general Android.
- For audio sources that are "farther away", recommends **Bluetooth headphones** so the phone can
  be placed closer to the speaker while the user listens via BT.
- **Key insight:** Sound Amplifier avoids the table problem by always keeping the phone near the
  user's face (in-hand or clipped to clothing) and listening via headphones.

### Petralex
Based on product research (app is closed-source). Their "Remote Microphone" feature:

- Lets the phone act as a standalone remote microphone placed on a table; audio streams wirelessly
  to Petralex hearing aids via Bluetooth
- The phone IS placed on a table, away from user
- Their solution: **explicit user instructions** — "place phone face-up, screen toward speaker"
- No special Android API magic — they use standard `MIC` source
- They likely apply **higher gain** in this mode (since mic is at distance)
- The "remote" part is about BT streaming to hearing aids, not about mic direction APIs
- They show a visual guide with phone placement arrows

**Key insight from Petralex:** The only reliable solution for "phone on table" is UI guidance
to the user to position the phone face-up, plus boosted gain. The APIs don't solve it.

### vatbub/HearingAid (open-source reference)
- Uses `SL_ANDROID_RECORDING_PRESET_UNPROCESSED` via OpenSL ES for raw, unprocessed audio
- No mic direction logic at all — just takes whatever the system gives
- Focused on signal processing (FFT, frequency domain), not mic selection

### Summary: What No Production App Does
No production hearing aid app was found using `setPreferredMicrophoneDirection` in the wild.
The API simply isn't used for hearing aid apps — it was designed for camera apps.

---

## 4. Wired Headset Mic as the Real Fix

When a user plugs in a **wired headset with an in-line mic** (TYPE_WIRED_HEADSET):
- The mic is body-worn (usually near the chest or chin)
- **Completely unaffected by phone orientation**
- Works well for ambient amplification — picks up sounds in the user's environment naturally
- Available API 23+, extremely reliable

**The catch:** `TYPE_WIRED_HEADPHONES` (no mic) should NOT trigger this path — the phone mic
should still be used, and we should still show the orientation hint.

**Detection:**
```kotlin
val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
val hasWiredMic = inputs.any {
    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
    it.type == AudioDeviceInfo.TYPE_USB_HEADSET
}
// Note: TYPE_WIRED_HEADPHONES shows as OUTPUT only, not INPUT
// So GET_DEVICES_INPUTS will only show WIRED_HEADSET (has mic), never WIRED_HEADPHONES
```

**Shortcut:** You don't need to distinguish headset vs headphones when querying inputs —
`GET_DEVICES_INPUTS` will only return `TYPE_WIRED_HEADSET` (mic present), never
`TYPE_WIRED_HEADPHONES` (output-only). So any wired device appearing in inputs list has a mic.

---

## 5. Bugs Found in hark-app

### Bug 1: `preferredInputDevice` Is Always `null`
**File:** `AudioStreamingService.kt` line 51 & 338

```kotlin
private var preferredInputDevice: AudioDeviceInfo? = null  // initialized null
// ...
audioEngine.start(preferredOutputDevice, preferredInputDevice)  // always null!
```

There is no code that ever assigns a device to `preferredInputDevice`. The
`refreshDeviceNames()` function (line 267) finds the wired headset device but only stores
its *name* for display — it never sets `preferredInputDevice`.

### Bug 2: `AudioStreamManager` Ignores `preferredInputDevice` for AudioRecord
**File:** `AudioStreamManager.kt`, `createAudioRecord()` function

```kotlin
private fun createAudioTrack(...): AudioTrack {
    val track = AudioTrack.Builder()...build()
    preferredOutputDevice?.let {
        track.setPreferredDevice(it)  // ✅ Output device IS set
    }
    return track
}

private fun createAudioRecord(...): AudioRecord {
    val record = AudioRecord(MediaRecorder.AudioSource.MIC, ...)
    // ❌ preferredInputDevice is NEVER applied to the AudioRecord!
    return record
}
```

The output path correctly calls `setPreferredDevice()`, but the input path does not.

---

## 6. Concrete Fix for hark-app

### Strategy
```
Wired headset WITH mic (WIRED_HEADSET in inputs)?
  → route AudioRecord to headset mic via setPreferredDevice()
  → call setPreferredMicrophoneDirection(MIC_DIRECTION_EXTERNAL) [API 29+]
  → NO mic orientation UI hint needed

Wired headphones WITHOUT mic (WIRED_HEADPHONES, output-only)?
  → use phone mic (preferredInputDevice = null)
  → call setPreferredMicrophoneDirection(MIC_DIRECTION_TOWARDS_USER) [API 29+ hint]
  → show mic orientation hint in UI

No wired device?
  → use phone mic (same as above)
  → show mic orientation hint in UI
```

### Fix A: `AudioStreamingService.kt` — Set `preferredInputDevice`

In `updateAudioRouting()` (or a new `selectInputDevice()` helper), detect the wired headset
input and set `preferredInputDevice`:

```kotlin
private fun selectInputDevice(): AudioDeviceInfo? {
    val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
    val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
    // WIRED_HEADSET appears in inputs (has mic); WIRED_HEADPHONES does NOT (output-only)
    return inputs.firstOrNull {
        it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
        it.type == AudioDeviceInfo.TYPE_USB_HEADSET
    }
    // null = use phone's built-in mic
}
```

Call this before `audioEngine.start()`:
```kotlin
preferredInputDevice = selectInputDevice()
audioEngine.start(preferredOutputDevice, preferredInputDevice)
```

### Fix B: `AudioStreamManager.kt` — Apply Device to AudioRecord

In `createAudioRecord()`, apply `preferredInputDevice` and set mic direction:

```kotlin
@SuppressLint("MissingPermission")
private fun createAudioRecord(channelConfig: Int, audioFormat: Int, bufferSize: Int): AudioRecord {
    val record = AudioRecord(
        MediaRecorder.AudioSource.MIC,
        Constants.Audio.SAMPLE_RATE,
        channelConfig,
        audioFormat,
        bufferSize
    )

    // Route to preferred input device (e.g. wired headset mic)
    preferredInputDevice?.let { device ->
        val result = record.setPreferredDevice(device)
        Log.i(TAG, "AudioRecord.setPreferredDevice(${device.productName}, type=${device.type}) = $result")
    }

    // Best-effort mic direction hint (API 29+, often ignored by OEM HALs)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val direction = if (preferredInputDevice != null) {
            MicrophoneDirection.MIC_DIRECTION_EXTERNAL
        } else {
            MicrophoneDirection.MIC_DIRECTION_TOWARDS_USER  // prefer screen-side/top mic
        }
        val dirResult = record.setPreferredMicrophoneDirection(direction)
        Log.i(TAG, "setPreferredMicrophoneDirection($direction) = $dirResult")
    }

    Log.i(TAG, "AudioRecord created: state=${record.state} source=MIC routedDevice=${record.routedDevice?.productName}")
    return record
}
```

### Fix C: Expose Wired-Headset-With-Mic Status from `HeadphoneDetector`

Currently `HeadphoneDetector.isWiredConnected` is `true` for BOTH headset-with-mic AND
headphones-without-mic. Add a separate flag:

```kotlin
// In HeadphoneDetector:
private val _hasWiredMic = MutableStateFlow(false)
val hasWiredMic: StateFlow<Boolean> = _hasWiredMic.asStateFlow()

private fun updateStatus() {
    val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    val inputs  = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)

    val hasWired = outputs.any {
        it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
        it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
        it.type == AudioDeviceInfo.TYPE_USB_HEADSET
    }
    // WIRED_HEADSET shows in inputs (has mic); WIRED_HEADPHONES does not
    val wiredMic = inputs.any {
        it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
        it.type == AudioDeviceInfo.TYPE_USB_HEADSET
    }

    _isWiredConnected.value = hasWired
    _hasWiredMic.value = wiredMic
    _isBluetoothOnly.value = /* ... */ !hasWired && hasBluetooth
}
```

### Fix D: UI Orientation Hint (only when using phone mic)

In `HomeScreen.kt` or wherever the listening state is shown, add a dismissible card:

```kotlin
// Show only when: streaming AND no wired mic AND no external mic preference
val showMicHint by remember {
    derivedStateOf {
        isStreaming && !headphoneDetector.hasWiredMic.value && !prefs.preferExternalMic
    }
}

if (showMicHint) {
    Card(/* ... */) {
        Row {
            Icon(Icons.Default.Mic, ...)
            Text("For best results, hold the phone upright or place it face-up on a flat surface.")
        }
    }
}
```

---

## 7. What Does NOT Work

- **`setPreferredMicrophoneDirection` as a table fix**: It can't distinguish face-up from face-down.
  Use it as a hint only; don't document it to users as a fix.

- **`AudioSource.UNPROCESSED`**: Better audio quality but same mic selection — doesn't help with
  orientation. Also breaks Android's noise cancellation and AGC that might actually help.

- **`AudioSource.VOICE_RECOGNITION`**: Unstable on OEM devices (session dies after 20-30s on
  OnePlus/older Samsung) — already documented in the existing code comments. Don't switch to it.

- **Accelerometer-based mic selection**: Theoretically could detect face-up vs face-down using
  `SensorManager` and switch mic direction dynamically. In practice:
  - Most devices don't have multiple usable front/back mics
  - `setPreferredMicrophoneDirection` doesn't map to face-up/face-down anyway
  - Significant complexity for marginal gain

- **`REMOTE_SUBMIX` AudioSource**: Captures audio being *played* on the device, not external
  ambient sound. Not relevant here.

---

## 8. Summary Table

| Scenario | API to Use | Expected Reliability |
|---|---|---|
| Wired HEADSET (3.5mm/USB with mic) | `setPreferredDevice(wiredHeadsetInput)` | ✅ Very reliable — solves orientation entirely |
| Wired HEADPHONES (no mic) | `setPreferredMicrophoneDirection(TOWARDS_USER)` + UI hint | ⚠️ Hint only — depends on OEM HAL |
| No wired device | `setPreferredMicrophoneDirection(TOWARDS_USER)` + UI hint | ⚠️ Same as above |
| Bluetooth SCO mic | `setCommunicationDevice()` (existing code) | ✅ Works (already implemented) |

---

## 9. Files to Change

| File | Change |
|---|---|
| `AudioStreamingService.kt` | Add `selectInputDevice()`, assign `preferredInputDevice` before `audioEngine.start()` |
| `AudioStreamManager.kt` | In `createAudioRecord()`, call `record.setPreferredDevice()` and `record.setPreferredMicrophoneDirection()` |
| `HeadphoneDetector.kt` | Add `hasWiredMic: StateFlow<Boolean>` to distinguish headset-with-mic from headphones |
| `HomeScreen.kt` (or ViewModel) | Add dismissible orientation hint shown only when using phone mic |
