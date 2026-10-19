# Development and device testing

This document describes the Android project **as it exists locally**. The desktop StepMania reference and exported chart archive are deliberately separate.

## Toolchain and current build caveats

The checked-in project uses Kotlin/Jetpack Compose, Android Gradle Plugin **9.1.1** (see `gradle/libs.versions.toml`), and Gradle **9.3.1** in `gradle/wrapper/gradle-wrapper.properties`.

`app/build.gradle.kts` currently configures:
- App ID: `com.rhythmpad7.app`
- Version: `0.1.1` until newer code is integrated
- Compile SDK: **Android 36.1**
- Target SDK: 36
- Android source namespace: `com.example` (distinct from application ID)
- A local debug signing configuration expecting `debug.keystore`

**Important known gaps in the Git checkout:** `gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar` are not currently checked in; `debug.keystore` is also local-only. Until these are addressed, this checkout alone is not a turnkey reproducible build. Do not edit the signing credentials or invent build results.

For local builds, install a compatible JDK, Android SDK **platform 36.1**, and compatible Gradle **9.3.1**. Where the executable is available:

```bash
gradle :app:assembleDebug
gradle :app:testDebugUnitTest
```

When a complete official Gradle wrapper has been added, prefer:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Check and solve local signing/key issues before publishing artifacts. Do not commit a private signing key or assume it is generated automatically when the Gradle configuration explicitly references a custom file.

## Google AI Studio workflow

1. Select **Android** in AI Studio Build, not Web.
2. Modify and compile the **existing** project, preserving its application ID and mappings.
3. Validate UI/gameplay logic in the integrated emulator.
4. Export the Android source changes before integrating them into this checkout.
5. Check `git diff` before and after import; preserve documentation and unrelated local data.
6. Do **not** assume AI Studio can access `/home/paul/Projects/RhythmPad7/patterns/`. Copy or attach curated assets explicitly when implementing chart import.

AI Studio's emulator cannot prove that the real physical dance mat generates events.

## Xiaomi Pad 7 USB hub workaround

The verified controller is **Microntek USB Joystick (0079:0006)** and the working topology is:

```text
Physical dance mat -> USB hub -> USB-C data/OTG adapter -> Xiaomi Pad 7
```

On the tested Android 16 / HyperOS build, enable **Settings → More connection options → OTG** before attaching hardware. The OTG toggle may turn off after inactivity. If no controller is detected, disconnect devices, toggle OTG again, and reconnect through the tested hub. The adapter-only connection failed USB enumeration in prior tests, so software remapping cannot cure that lower-level failure.

The existing Dance Pad 1.3 application already demonstrated real direct USB input. The initial RhythmPad7 app also displayed a connected Microntek device, but that is **not by itself proof of four-way timing or scoring**.

Mapping details, additional physical buttons, and hardware-verification limits are in [../ANDROID_MAPPING.md](../ANDROID_MAPPING.md).

## Wireless ADB verification

Because the mat uses the tablet's USB-C port, use **Wireless debugging** and a computer on the same network. Pairing is needed only if the computer is not already authorized.

```bash
adb devices -l
adb mdns services
# When pairing is needed, use the address in the tablet's pair-code dialog:
adb pair TABLET_IP:PAIRING_PORT
# Use the separate address and port from Wireless debugging:
adb connect TABLET_IP:DEBUG_PORT
adb devices -l
```

Addresses, port numbers, and ADB serials can change after reconnecting. Substitute the current device serial for `TABLET_SERIAL` below; **do not hardcode an old IP or device serial into app code or scripts**.

After obtaining a successful APK build:

```bash
adb -s TABLET_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s TABLET_SERIAL shell pm list packages | grep rhythmpad7
adb -s TABLET_SERIAL shell am start -n com.rhythmpad7.app/com.example.MainActivity
adb -s TABLET_SERIAL logcat -d -t 300
```

Use narrower logcat filters after confirming the actual tag used by a given build. Do not treat unrelated log messages as RhythmPad7 failures.

## Physical dance-mat acceptance checklist

- App launches in landscape and shows the connected Microntek joystick.
- `BUTTON_1` press/release highlights **LEFT** without activating another lane.
- `BUTTON_2` highlights **DOWN**, `BUTTON_3` highlights **UP**, and `BUTTON_4` highlights **RIGHT**.
- Holding LEFT + RIGHT (and other combinations) keeps both active until each is released.
- Holding a button does not repeatedly increment a press counter or score multiple hits.
- Pause/focus loss/disconnect clears held inputs correctly; reattach recovers controller detection.
- For the v0.2 gameplay milestone, arrows must **scroll automatically** from the game clock even if no mat input occurs.
- For the v0.2 gameplay milestone, incoming mat events judge notes, update score/combo, and never depend on touchscreen lane presses.
- Restart restores the initial note sequence and timing. Pause/resume does not skip notes.

Record failed/passed outcomes from **real** tablet observation. Do not label tests as passed based only on source inspection.

## Git workflow and local data

```bash
git status --short
git diff --check
git diff -- .gitignore README.md AGENTS.md docs/
git check-ignore -v patterns/index.json stepmania-pcapp/.git/config .agents/servers/mobile-mcp.json
```

The 34 MB local chart archive, roughly 937 MB separate desktop source checkout, private per-machine tool configuration, and `pad_mapping.json` are ignored by the Android repository on purpose. Keep them available on the development computer. To add a chart to the Android app later, deliberately place a reviewed, supported, rights-cleared subset in `app/src/main/assets/patterns/` and commit those app assets only.
