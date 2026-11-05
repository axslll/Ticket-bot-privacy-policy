# dB Meter

An Android app that shows how loud the audio your phone is playing is, in decibels, with a
traffic-light safety level:

| Colour | Level | Meaning |
|--------|-------|---------|
| 🟢 Green  | under 70 dB | Safe – fine for all-day listening |
| 🟠 Orange | 70–85 dB    | Caution – take breaks, avoid long sessions |
| 🔴 Red    | 85 dB and up | Dangerous – the app also shows the recommended maximum daily time (8 h at 85 dB, halving every +3 dB; NIOSH/WHO guidance) |

It measures **what the phone plays** (music, videos, games), not the microphone, using Android's
`AudioPlaybackCapture` API. A foreground-service notification also changes with the level, so you
get warned when the app is in the background.

## How the number is calculated

A phone can't know how loud its speaker or headphones really are, so the figure is an **estimate**:

```
dB ≈ 100 + (RMS level in dBFS) + 20·log10(media volume) + calibration
```

i.e. a full-scale signal at full volume is assumed to be 100 dB, which is typical for headphones.
Use the **Calibration** slider (±20 dB) to match a real sound-level meter. It's a guide, not a
medical device. The maths lives in `SoundLevel.kt` and is unit-tested.

## Limitations

* Android 10 (API 29) or newer.
* Apps can opt out of audio capture (DRM-protected video, some streaming apps) – they show as
  "Nothing playing". Phone calls can't be captured.
* Android requires the **microphone permission** and the **screen-capture consent dialog** to allow
  playback capture. No microphone audio and no screen content is recorded, stored or sent anywhere;
  the app has no internet permission.

## Build

Requires JDK 17+ and the Android SDK (platform 34).

```bash
cd DbMeter
echo "sdk.dir=/path/to/Android/sdk" > local.properties   # or set ANDROID_HOME
./gradlew testDebugUnitTest assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Or open the `DbMeter` folder in Android Studio and press Run. To install the APK on a phone, copy
it over and open it (allow "install unknown apps"), or `adb install app-debug.apk`.

## Layout

```
app/src/main/java/com/dbmeter/app/
  SoundLevel.kt     dB maths + green/orange/red thresholds (pure Kotlin, unit-tested)
  CaptureService.kt foreground service: AudioPlaybackCapture -> level, ~10x per second
  LevelMonitor.kt   shared state between the service and the UI
  MainActivity.kt   permissions flow + UI
  LevelBar.kt       custom green/orange/red scale with a marker
```
