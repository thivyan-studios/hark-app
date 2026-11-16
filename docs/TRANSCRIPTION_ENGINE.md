# HARK Speech-to-Text (STT) & Transcription Pipeline

This document details the offline, privacy-first Speech-to-Text (STT) engine, model management, real-time decoding loop, and Room persistence integrated into HARK.

---

## Overview

HARK provides live continuous transcription of spoken audio directly on-device without requiring an internet connection or cloud API calls. This allows hearing-impaired users to read live captions in noisy classrooms, lectures, or meetings.

### Key Capabilities
- **100% On-Device & Private**: No audio data or transcripts leave the phone.
- **Low Latency Streaming**: Powered by **Sherpa-ONNX** with Zipformer streaming transducer models.
- **Continuous Endpointing**: Automatic sentence segmentation and real-time caption updates.

---

## Pipeline Architecture

```mermaid
flowchart TD
    MIC["Microphone"] --> AGC["<b>HarkAudioEngine</b><br/>automatic level control"]
    AGC --> TXF["Transcription FIFO"]
    TXF --> READ["Read up to 100 ms<br/>every 30 ms"]
    READ --> ACCEPT["acceptWaveform<br/>at the mic's real rate"]
    ACCEPT --> READY{"isReady?"}
    READY -- "yes" --> DECODE["decode"]
    DECODE --> READY
    READY -- "no" --> RESULT["getResult"]
    RESULT --> PARTIAL["Show partial text"]
    RESULT --> EP{"isEndpoint?"}
    EP -- "yes" --> FINAL["Add finished sentence,<br/>then reset"]
    PARTIAL --> UI["TranscribeScreen"]
    FINAL --> UI
    UI -- "stop or Clear" --> DB[("Saved to History<br/>Room")]

    classDef ui fill:#dbeafe,stroke:#3b82f6,color:#1f2328
    classDef svc fill:#ede9fe,stroke:#8b5cf6,color:#1f2328
    classDef native fill:#fee2e2,stroke:#ef4444,color:#1f2328
    classDef ml fill:#dcfce7,stroke:#22c55e,color:#1f2328
    classDef data fill:#fef3c7,stroke:#f59e0b,color:#1f2328
    classDef io fill:#f3f4f6,stroke:#6b7280,color:#1f2328
    class MIC io
    class AGC,TXF native
    class READ,ACCEPT,READY,DECODE,RESULT,EP,FINAL,PARTIAL ml
    class UI ui
    class DB data
```

---

## Model Management (`STTModelManager`)

The STT system uses modular streaming models packaged into 4 ONNX files:
1. `encoder-epoch-99-avg-1.int8.onnx`: Audio feature encoder network (int8-quantised).
2. `decoder-epoch-99-avg-1.onnx`: Embedding decoder network (small, so kept at full precision).
3. `joiner-epoch-99-avg-1.int8.onnx`: Transducer joiner network combining encoder and decoder states (int8-quantised).
4. `tokens.txt`: Vocabulary token map.

The int8 encoder and joiner make the download about 129 MB instead of about 296 MB and run roughly twice as fast on phone CPUs, with near-identical accuracy.

### Built-in Model Definition
- **Name**: Streaming Zipformer (English)
- **Source**: `sherpa-onnx-streaming-zipformer-en-2023-02-21`
- **Download Source**: HuggingFace (`csukuangfj/sherpa-onnx-streaming-zipformer-en-2023-02-21`)

### Download & File Storage
- Models are downloaded asynchronously using `OkHttp`, with overall progress weighted by file size (`_downloadProgress`).
- Each file downloads to `<name>.part` and is only renamed once complete, so an interrupted download never leaves a half-written model behind.
- Files are saved to internal app storage (`context.filesDir`). `isModelDownloaded()` checks that all 4 files exist **and** match their expected sizes.

---

## Sherpa-ONNX Recognition Engine Configuration

Inside [AudioStreamingService.kt](../app/src/main/java/com/thivyanstudios/hark/service/AudioStreamingService.kt), the recognizer is initialized with:

```kotlin
val config = OnlineRecognizerConfig()

// Model paths
config.modelConfig.transducer.encoder = File(filesDir, model.encoderFileName).absolutePath
config.modelConfig.transducer.decoder = File(filesDir, model.decoderFileName).absolutePath
config.modelConfig.transducer.joiner = File(filesDir, model.joinerFileName).absolutePath
config.modelConfig.tokens = File(filesDir, model.tokensFileName).absolutePath

// Decoding & Feature Config
config.decodingMethod = "modified_beam_search"
config.maxActivePaths = 4
config.featConfig.sampleRate = 16000
config.featConfig.featureDim = 80
config.modelConfig.numThreads = prefs.sttThreads // Settings → CPU Threads (default: 4)

// Endpoint Rules (Silence Detection)
config.enableEndpoint = true
config.endpointConfig.rule1.minTrailingSilence = 1.5f  // Long silence threshold
config.endpointConfig.rule2.minTrailingSilence = 0.8f  // Short silence threshold
```

---

## Real-time Audio Streaming Loop

1. **Buffer Polling**: Every 30 ms, the service calls `audioEngine.readTranscriptionData(audioBuffer)` to pull up to 3200 PCM float samples (100 ms chunks) from the C++ FIFO.
2. **Resampling & Feature Extraction**: The PCM float array is passed into `sherpaStream.acceptWaveform(activeSamples, sampleRate = audioEngine.getTranscriptionSampleRate())`, the rate the mic is really delivering. Sherpa resamples it (e.g. from 48 kHz) to the 16 kHz the model expects.
3. **Beam Search Decoding**: While `recognizer.isReady(sherpaStream)` returns true, `recognizer.decode(sherpaStream)` processes frame batches.
4. **Partial Result Updates**: `recognizer.getResult(sherpaStream)` updates the `_transcription` StateFlow for real-time feedback on screen. `TranscriptFormatter` turns the model's upper-case words into normal sentence casing.
5. **Endpoint Detection**: When `recognizer.isEndpoint(sherpaStream)` detects trailing silence (0.8s - 1.5s), the sentence is finalized into `fullTranscript` and `recognizer.reset(sherpaStream)` clears decoder memory for the next sentence.

---

## Persistence & History Management

The current transcript is saved to History automatically when streaming stops, and when you tap **Clear** on the Transcribe screen. Saved transcripts can be opened, edited, copied, shared or deleted from History.

### Database Schema
- **Database**: [HarkDatabase](../app/src/main/java/com/thivyanstudios/hark/data/local/HarkDatabase.kt) (Room DB v1)
- **Table**: `transcriptions`
- **Entity**: [TranscriptionEntity](../app/src/main/java/com/thivyanstudios/hark/data/local/TranscriptionEntity.kt)
  - `id: Long` (Auto-generate Primary Key)
  - `text: String`
  - `timestamp: Long` (System epoch timestamp)

### Repository Operations ([TranscriptionRepository](../app/src/main/java/com/thivyanstudios/hark/data/TranscriptionRepository.kt))
- `allTranscriptions: Flow<List<TranscriptionEntity>>`: Reactive flow sorted by timestamp descending.
- `insert(text: String)`: Inserts new transcript entry.
- `update(id: Long, text: String)`: Saves an edited transcript (blank edits are ignored).
- `delete(id: Long)`: Deletes individual entry by ID.
- `deleteAll()`: Clears transcript history.
