# HARK Hearing Aid Fork — Development Notes

记录开发过程中遇到的问题、解决方法和坑点，供后续维护参考。

## 架构概览

```
手机麦克风
    └─► AudioRecord (Java) ──► DefaultAudioProcessor (增益/EQ) ──► AudioTrack (Java)
                                                                          └─► 有线耳机输出
```

**当前使用 Java 引擎**（AudioRecord + AudioTrack），而非 Oboe C++ 引擎。原因见下文。

---

## 已知问题与坑点

### ❌ 坑1: Oboe 双 DataCallback 在 USB-C 音频适配器上不可靠

**现象：** Samsung S21/S22 插 USB-C 转 3.5mm 耳机，Native engine 启动成功（`nativeStart result: true`），但耳机没有任何声音，MIC IN 电平始终为 0。

**根因：** Oboe 的架构使用同一个 `DataCallback` 处理 input 和 output 两个 stream。在 USB-C 音频路由下，input stream 的 callback 被系统静默忽略（已知 Oboe issue #1523）。

**修复：** `AudioEngine.kt` 中强制走 Java 引擎：
```kotlin
// Force Java engine - Oboe dual-DataCallback unreliable on USB-C adapters
HarkLog.i(TAG, "Using Java AudioRecord/AudioTrack engine")
streamManager.start(currentConfig)
return
```

**影响：** 延迟从 15ms 增加到 ~50ms，可接受。

---

### ❌ 坑2: Oboe `InputPreset::VoiceCommunication` 需要配合 AudioManager 模式

**现象：** OnePlus 5T (API 29) 无声，但 Oboe 启动不报错。

**根因：** `InputPreset::VoiceCommunication` 需要 `AudioManager.MODE_IN_COMMUNICATION`，但 app 默认不设置此模式，导致 input 路由错误。

**修复：** 改为 `InputPreset::Unprocessed`（在 `hark_audio_engine.cpp`）：
```cpp
inBuilder.setInputPreset(oboe::InputPreset::Unprocessed)
```

---

### ❌ 坑3: `enableBluetoothHeadsetSupport` 默认 false 导致有线耳机被拒绝

**现象：** 点击 Start 按钮弹出 `Connect your hearing system first`，明明已插耳机。

**根因：** 原版 HARK 仅支持 ASHA 助听器（`TYPE_HEARING_AID`）。`enableBluetoothHeadsetSupport = false` 时，有线/BT 耳机不被识别为有效输出设备。

**修复：** `UserPreferences.kt` 默认值改为 `true`：
```kotlin
val enableBluetoothHeadsetSupport: Boolean = true  // 改为 true
```
同步修改 `UserPreferencesRepository.kt` 的 DataStore 默认值。

---

### ❌ 坑4: Samsung S21 在首次启动时耳机未插入，`Hearing aid connected: false` 一直保持

**现象：** 先开 app 再插耳机，`Hearing aid connected` 变为 true（因为有 `AudioDeviceCallback`）。但如果先插好耳机再开 app，会出现一段时间 false 然后变 true 的时序问题。

**根因：** `AudioStreamingService.onCreate` 时设备扫描比 preference change 更早，第一次扫描是在 `Bluetooth headset support preference changed: true` 之前完成的，所以即使耳机已接，也不识别。

**表现：** 需要等 `Bluetooth headset support preference changed: true` 这条 log 后才能识别，通常延迟 100-200ms，自动恢复。

---

### ❌ 坑5: `No microphone input detected` 警告循环显示

**现象：** 1.1.x 版本中，启动后 MIC IN 电平永远是 0，`⚠️` 警告持续显示。

**根因：** 电平读取路径只读 native（Oboe）层的 `nativeGetInputLevel`。当 Java 引擎运行时，native 层无数据，始终返回 0。

**修复：**
1. `DefaultAudioProcessor.kt` 中用 `AtomicReference` 记录 RMS：
```kotlin
private val _inputLevel = AtomicReference(0f)
// 在 applyGain 中计算 inRms 并更新
_inputLevel.set(_inputLevel.get() * 0.85f + inRms * 0.15f)
```
2. `AudioEngine.kt` 中级联读取：native > 0 则用 native，否则从 `DefaultAudioProcessor` 读。

---

### ⚠️ 坑6: PowerShell here-string 写文件换行丢失

**现象：** 通过 PowerShell `@'...'@` 写文件时，换行符被吞，文件变成一行。

**修复：** 用 Python 中转写文件，或用 `cat > /tmp/file.py << 'PYEOF'` 的 shell heredoc 写 Python 脚本到 Codespace，再执行。

---

## 设备兼容性

| 设备 | OS | 引擎 | 延迟 | 状态 |
|------|-----|------|------|------|
| Samsung SM-G986U (S21) | Android 12 (API 31) | Java | ~50ms | ✅ v1.2.0 |
| OnePlus A5010 (5T) | Android 10 (API 29) | Java (forced) | ~80ms | ✅ v1.1.1 |
| 目标新设备 (API 33+) | Android 13+ | Java | ~50ms | ✅ 通用 |

---

## 音频设备类型参考

```
type=1  = TYPE_EARPIECE (听筒)
type=2  = TYPE_SPEAKER (扬声器)
type=3  = TYPE_WIRED_HEADSET (有线耳机,含麦克风)
type=4  = TYPE_WIRED_HEADPHONES (有线耳机,无麦克风)
type=8  = TYPE_BLUETOOTH_SCO
type=14 = TYPE_BUS
type=15 = TYPE_USB_DEVICE
type=18 = TYPE_BUILTIN_MIC (内置麦克风)
type=22 = TYPE_USB_HEADSET (USB-C 耳机)
type=25 = TYPE_TELEPHONY (电话)
```

Samsung S21 插 USB-C 耳机时，IN 设备中**不会**出现 type=22（耳机 MIC），只有 type=15/18/25，说明 USB-C 耳机麦克风不被 Android `getDevices()` 枚举——音频从**手机内置麦克风**录制，从 USB-C 耳机输出。

---

## 版本历史

| 版本 | 主要变更 |
|------|---------|
| v1.0.0 | 初始 fork：去 Whisper、5频段EQ、听力测试、有线模式 |
| v1.0.1 | 修复 `enableBluetoothHeadsetSupport` 默认 false |
| v1.0.2 | Oboe Shared 模式 + 日志增强 |
| v1.0.3 | C++ 层诊断日志（buffer 状态、callback 计数）|
| v1.1.0 | 10频段EQ + L/R 独立 + 限幅器 + 降噪 + 电平表 |
| v1.1.1 | 修复音频路由（InputPreset Unprocessed、API<30 Java fallback、电平条移顶部）|
| v1.2.0 | **强制所有设备使用 Java 引擎**，修复 Samsung USB-C 无声，电平表实际工作 |

---

## 后续优化方向

1. **Oboe 引入**: 待 Oboe #1523 修复后，可重新启用 native 引擎（仅限新设备+3.5mm 直插）
2. **USB-C 麦克风支持**: 需要 `AUDIO_DEVICE_IN_USB_HEADSET` 路由，当前无法用耳机自带 MIC
3. **NoiseSuppressor**: Java 引擎下 `NoiseSuppressor.create(audioRecord.audioSessionId)` 可附加
4. **自动更新检测**: 调 GitHub Releases API 检测新版本
5. **听力测试精度**: 目前纯音 250-8kHz，可用校准耳机提高精度
