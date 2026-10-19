# RhythmPad7

A **native Kotlin / Jetpack Compose four-lane rhythm game**, developed specifically for the **Xiaomi Pad 7** and a physical USB dance mat. This is a new Android implementation inspired by StepMania gameplay concepts; it is **not** a port of the desktop StepMania engine.

- Android application ID: `com.rhythmpad7.app`
- Target: **Xiaomi Pad 7 only** (landscape, 3200 × 2136, 3:2)
- Main input: **Microntek USB Joystick**, VID `0079` / PID `0006`
- Technology: Kotlin, Jetpack Compose, Material 3, Compose Canvas, Android input APIs
- Project repository: https://github.com/Areo-RGB/RhythmPad7
- Hardware reference: https://github.com/Areo-RGB/dancepad

## Current status

The checked-in Android project reports **v0.1.1** as of 2026-10-19. It includes a home screen, four-lane receptor view, controller selection, Microntek mapping defaults, saved remapping, and basic input state/debug UI. The controller is recognized on the Xiaomi Pad 7.

**v0.1.1 is an input foundation, not yet a playable scrolling rhythm game.** Work on v0.2 (clock, scrolling notes, judgments, and physical-mat-only gameplay) is being generated separately in Google AI Studio. This README must be updated after that code is actually imported; do not describe planned functionality as shipped.

See [development and verification](docs/DEVELOPMENT.md), [extracted chart data](docs/CHARTS.md), and [agent guidance](AGENTS.md).

## Project layout

```text
RhythmPad7/
├── app/                   Native Android application (tracked)
│   └── src/main/java/com/example/
├── gradle/                Gradle version catalog and wrapper properties (tracked)
├── build.gradle.kts       Project Gradle configuration
├── settings.gradle.kts
├── README.md              Project overview
├── AGENTS.md              Instructions for coding assistants
├── ANDROID_MAPPING.md     Verified reference mapping from Dance Pad 1.3
├── docs/
│   ├── DEVELOPMENT.md     Build, test, and hardware instructions
│   └── CHARTS.md          Extracted pattern schema and import plan
├── patterns/              LOCAL extracted charts and SSC originals (ignored)
├── stepmania-pcapp/       LOCAL separate desktop StepMania Git checkout (ignored)
├── pad_mapping.json       LOCAL reference/controller mapping (ignored)
└── .agents/               LOCAL agent-tool settings (ignored)
```

**Important:** `patterns/` and `stepmania-pcapp/` are separate local references, not part of the Android Gradle project. Keep their contents in place. Do not add a nested desktop Git repository or full extracted chart archive to the Android Git history.

## Physical controller mapping

The four directional panels on the tested hardware generate *digital* Android button events:

| Dance-mat panel | Android key event |
| --- | --- |
| LEFT | `KeyEvent.KEYCODE_BUTTON_1` |
| DOWN | `KeyEvent.KEYCODE_BUTTON_2` |
| UP | `KeyEvent.KEYCODE_BUTTON_3` |
| RIGHT | `KeyEvent.KEYCODE_BUTTON_4` |

The center panel is positive `MotionEvent.AXIS_Y` above approximately 0.5 and **is not a fifth gameplay lane**. Keep configurable remapping for intentional user changes. Refer to [ANDROID_MAPPING.md](ANDROID_MAPPING.md) for additional panels and how this mapping was validated.

### Xiaomi Pad 7 USB hub workaround

The tested setup is **mat → USB hub → USB-C data/OTG adapter → Xiaomi Pad 7**. Direct mat-to-adapter connection failed USB enumeration on the tested tablet, while the hub connection worked.

Enable **Settings → More connection options → OTG** before connecting; on this HyperOS build the toggle may turn itself off after inactivity. If detection fails, disconnect the devices, re-enable OTG, and reconnect through the known-good hub. This is a hardware-specific workaround, not a software driver or a universal Android rule.

The tablet runs Android 16 / HyperOS OS3.0.304.0.WOZEUXM in the documented working setup.

## Build and test

Google AI Studio **Android Build mode** is used for iterative native project generation and preview. Its environment cannot inspect this computer's local `patterns/` or `stepmania-pcapp/` folders unless data is explicitly imported into the project.

To build locally, install a compatible JDK, Android SDK **platform 36.1** (the exact compile SDK configured in `app/build.gradle.kts`), and a compatible Gradle distribution. The checked-in `gradle/wrapper/gradle-wrapper.properties` specifies **Gradle 9.3.1**, but at present this Git checkout does **not** include the `gradlew` scripts or wrapper JAR. Add a complete official wrapper before using `./gradlew` as a reproducible build command.

With Gradle 9.3.1 available from your environment:

```bash
gradle :app:assembleDebug
gradle :app:testDebugUnitTest
```

The current Gradle configuration also expects a local `debug.keystore`, which is intentionally ignored by Git. Follow [DEVELOPMENT.md](docs/DEVELOPMENT.md) for the prerequisites and wireless ADB verification commands. **No successful local build is claimed by this documentation alone.**

## Chart data

Local `patterns/` contains **35 songs, 251 chart JSON files**, including **175 four-lane dance-single charts** and **70 beginner/easy four-lane charts**. Chart metadata and note-event timing are extracted from SSC files; the directory contains **no music or artwork**. Some charts have advanced events, and ten warp charts lack precomputed `time_ms` values.

Treat these as source material, not ready-to-score assets. See [CHARTS.md](docs/CHARTS.md) before copying a small, supported selection into `app/src/main/assets/`. Check the rights and licensing of any third-party material before redistribution.

## Development priorities

1. Keep the current Microntek USB input foundation functioning.
2. Make v0.2 **move notes automatically using a monotonic gameplay clock**, with real mat presses, scoring, pause/resume, and no simulated touchscreen judgments.
3. Validate v0.2 on the Xiaomi Pad 7 before building more features.
4. Integrate a curated subset of exported four-lane JSON charts.
5. Add audio-clock synchronization, later followed by `.sm` / `.ssc` support and advanced note types.

Every milestone must leave the Android application buildable. **Never expand device support beyond the Xiaomi Pad 7** without an explicit change in project requirements.
