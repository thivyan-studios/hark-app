# HARK – Hearing Aid Relay Kit

[![Android SDK](https://img.shields.io/badge/API-28%2B-blue.svg)](https://developer.android.com/about/versions/pie)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin%20%2F%20C%2B%2B-purple.svg)](https://kotlinlang.org/)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-green.svg)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-BSD%203--Clause-lightgrey.svg)](LICENSE)

**HARK** turns your Android phone into an ultra-low-latency remote microphone and real-time offline transcription streamer. It streams enhanced, processed speech directly to your hearing aids or headsets while rendering live, privacy-first on-device captions.

Designed to replace expensive proprietary "table mic" hardware accessories, HARK makes assistive listening and clear classroom or meeting speech accessible to everyone with a single tap.

---

## Features & Highlights

- **Ultra-Low Latency Audio Relay**: Native C++ audio engine built on [Google Oboe](https://github.com/google/oboe) for minimal delay between spoken voice and hearing aids.
- **Hearing Aid & Bluetooth Support**: Built specifically for **ASHA (Audio Streaming for Hearing Aids)** devices, with optional support for A2DP, SCO, and BLE headsets.
- **Custom DSP Audio Enhancement**:
  - **Bi-quad High-Shelf Treble Boost** (+8dB @ 3kHz) to enhance consonant clarity for high-frequency hearing loss.
  - **Soft-Knee Dynamic Range Limiter & Compressor**, on by default, to stop sudden loud noises from hurting ears.
  - **Hardware Noise Suppression** integration.
- **100% Offline Real-Time Transcription**: Powered by **Sherpa-ONNX** with a quantised (int8) Zipformer streaming model. Captions get their own automatic level control and are shown as proper sentences. No cloud APIs, zero network latency, complete privacy.
- **Transcript-Only Mode**: Option to mute audio relay while keeping live captions running for quiet environments.
- **Modern Material 3 UI**: Jetpack Compose interface in a liquid glass style, with a side-by-side tablet layout, adjustable caption font size, dark/light themes, and saved, editable transcript history.
- **Fault Tolerant**: Automatic background stream recovery on audio route changes or screen-off events.

---

## System Architecture Overview

HARK uses a high-performance multi-tier architecture connecting native C++ DSP audio processing, Android foreground service management, offline ML inferencing, and modern Jetpack Compose state management.

```mermaid
flowchart TD
    UI["<b>Compose screens</b><br/>Stream · Transcribe · History · Settings"]
    VM["<b>ViewModels</b><br/>MainViewModel · SettingsViewModel"]
    SVC["<b>AudioStreamingService</b><br/>foreground service"]
    AE["<b>AudioEngine</b><br/>Kotlin wrapper"]
    NATIVE["<b>HarkAudioEngine</b><br/>C++ / Oboe"]
    DSP["Treble boost → gain → limiter"]
    AID["Hearing aids / headset"]
    STT["<b>Sherpa-ONNX</b><br/>on-device captions"]
    DB[("<b>Room</b><br/>transcript history")]
    PREFS[("<b>DataStore</b><br/>settings")]

    UI --> VM --> SVC
    VM --> PREFS
    VM --> DB
    SVC --> AE --> NATIVE --> DSP --> AID
    NATIVE -- "captions copy" --> STT
    STT --> SVC
    SVC --> DB

    classDef ui fill:#dbeafe,stroke:#3b82f6,color:#1f2328
    classDef svc fill:#ede9fe,stroke:#8b5cf6,color:#1f2328
    classDef native fill:#fee2e2,stroke:#ef4444,color:#1f2328
    classDef ml fill:#dcfce7,stroke:#22c55e,color:#1f2328
    classDef data fill:#fef3c7,stroke:#f59e0b,color:#1f2328
    classDef io fill:#f3f4f6,stroke:#6b7280,color:#1f2328
    class UI,VM ui
    class SVC,AE svc
    class NATIVE,DSP native
    class STT ml
    class DB,PREFS data
    class AID io
```

For complete architectural breakdown, threading diagrams, and module responsibilities, read the **[System Architecture Guide](docs/ARCHITECTURE.md)**.

---

## Technical Documentation

Detailed documentation is available in the [`docs/`](docs/) directory:

- **[System Architecture](docs/ARCHITECTURE.md)**: Deep dive into application layers, thread boundaries, audio callbacks, and service lifecycle.
- **[Audio Processing Engine](docs/AUDIO_ENGINE.md)**: Details on native C++ Oboe integration, high-shelf biquad filter equations, soft-knee dynamics limiter formulas, and stream recovery logic.
- **[Speech-to-Text Pipeline](docs/TRANSCRIPTION_ENGINE.md)**: Complete guide to Sherpa-ONNX offline recognition, model management, audio resampling, and Room database persistence.

---

## Project Structure

```
hark-app/
├── app/
│   ├── src/main/cpp/               # Native C++ Audio Engine
│   │   ├── hark_audio_engine.h     # Oboe stream callbacks & DSP header
│   │   ├── hark_audio_engine.cpp   # Biquad filter, limiter, ring buffers
│   │   ├── jni_bridge.cpp          # JNI native interface bindings
│   │   └── CMakeLists.txt          # NDK & Oboe build configuration
│   └── src/main/java/com/thivyanstudios/hark/
│       ├── audio/                  # Audio engine Kotlin wrapper & fallbacks
│       ├── data/                   # DataStore, STT Model Manager, Room DB
│       ├── di/                     # Dependency Injection (Hilt modules)
│       ├── service/                # Foreground Service & Audio Device Routing
│       ├── ui/                     # Jetpack Compose Screens & ViewModels
│       └── util/                   # Loggers, Permissions, System Utilities
├── docs/                           # Detailed Technical Documentation
│   ├── ARCHITECTURE.md
│   ├── AUDIO_ENGINE.md
│   └── TRANSCRIPTION_ENGINE.md
└── README.md
```

---

## Building & Setup

### Prerequisites
- **Android Studio**: Ladybug / 2024.2+ or Android Studio Jellyfish+
- **Android SDK**: `compileSdk = 37`, `minSdk = 28` (Android 9.0+)
- **Android NDK**: Version 25+ with CMake 3.22+ installed via Android SDK Manager
- **JDK**: Java 11 or Java 17

### Build Steps

1. **Clone the repository**:
   ```bash
   git clone https://github.com/thivyan-studios/hark-app.git
   cd hark-app
   ```

2. **Open in Android Studio**:
   Open the project root directory in Android Studio and let Gradle sync.

3. **Build APK**:
   ```bash
   ./gradlew assembleDebug
   ```

4. **Run on Device**:
   Connect an Android phone (API 28+) via USB or Wireless ADB, and run:
   ```bash
   ./gradlew installDebug
   ```

> [!NOTE]
> For low latency audio streaming, testing on a physical Android device with ASHA-compatible hearing aids or Bluetooth headphones is recommended (Android Emulators lack native Bluetooth ASHA streaming support).

---

## Target Users

- Hearing aid users in classrooms, lectures, family dinners, or noisy environments.
- Anyone who needs an instant remote table mic without carrying extra hardware dongles.
- Optimized for devices with **ASHA (Audio Streaming for Hearing Aids)** support.

---

## Author & Credits

**Author**: [Thivyan Pillay](https://www.linkedin.com/in/thivyan-pillay/)

> *"Hearing aid tech should be accessible — not locked behind overpriced accessories."*

<sub>Built and architected with assistance from Gemini.</sub>