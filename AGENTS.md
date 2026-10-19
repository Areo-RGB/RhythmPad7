# AGENTS.md — RhythmPad7 project instructions

This file applies to the entire repository, unless a more specific `AGENTS.md` exists in a subdirectory. It is intended for coding agents, including AI Studio / Gemini and local agents.

## Mission and non-negotiable scope

Build a **standalone, native Android four-lane rhythm game** inspired by StepMania, exclusively for the **Xiaomi Pad 7**. Do not optimize for other tablets, Android phones, desktop StepMania, or web. Keep landscape play on the 3:2 display usable from standing distance.

Use the existing Kotlin + Jetpack Compose application. **Do not port the desktop C++ engine, replace the project with a new scaffold, or introduce React/Unity/Flutter/NDK.**

The checked-in version on 2026-10-19 is **v0.1.1**. A v0.2 gameplay change may be under development outside this checkout. **Inspect the actual branch, code, and `git status` before claiming which version is implemented.**

## Critical hardware facts — do not guess

Tested hardware: Xiaomi Pad 7, Android 16 / HyperOS 3, USB dance mat named **Microntek USB Joystick**, vendor `0x0079`, product `0x0006`.

Tested electrical path: **dance mat → USB hub → USB-C OTG adapter → tablet**. Direct adapter-only connection did not reliably enumerate this device. OTG must be enabled in HyperOS.

Confirmed Android directional KeyEvents:

| Lane | Android button |
| --- | --- |
| LEFT | `KeyEvent.KEYCODE_BUTTON_1` |
| DOWN | `KeyEvent.KEYCODE_BUTTON_2` |
| UP | `KeyEvent.KEYCODE_BUTTON_3` |
| RIGHT | `KeyEvent.KEYCODE_BUTTON_4` |

These are **four independent digital key events**, not directional axes. The mat's center panel is positive `AXIS_Y` and **must not** activate a directional lane by default.

Keep the Microntek VID/PID preference, device-ID filtering, remapping persistence, simultaneous inputs, button release, and key-repeat suppression. Do not grab USB HID interfaces or require root/privileged permissions when Android's standard input subsystem already handles the device.

Source of truth for the hardware mapping: [ANDROID_MAPPING.md](ANDROID_MAPPING.md) and the independent hardware reference at https://github.com/Areo-RGB/dancepad.

## Gameplay and architectural expectations

- Current input: `MainActivity` → `ControllerManager` → `GameInputEvent` → `RhythmGameViewModel` / game state → Compose UI.
- Keep input/state/gameplay logic separate from Canvas rendering and navigation.
- Track active input identities independently. A release from one source/binding must not cancel a different active holder.
- Use `KeyEvent.eventTime` and `SystemClock.uptimeMillis()` consistently for local gameplay timing. These timestamps are not physical foot-contact times.
- Scrolling notes must be positioned from their scheduled hit times and an independent monotonic game clock; **never advance notes by a fixed amount per frame or require controller input to make the screen move**.
- Preserve pause/resume time continuity, deterministic restart, and on-focus-loss input cleanup.
- Gameplay judgments must come from **real dance-mat controls**, not touchscreen lane simulation. Touch remains valid for menus, navigation, settings, Pause/Resume/Restart.
- Never present a static placeholder score/BPM/judgment as if it were real gameplay.
- Do not add networking, accounts, a desktop input relay, or unrelated device compatibility.

## Source layout

- `app/src/main/java/com/example/`: current Kotlin source package; **application ID stays `com.rhythmpad7.app`** unless explicitly requested.
- `app/src/test/`: unit and Robolectric tests.
- `gradle/libs.versions.toml` and Gradle Kotlin DSL: existing build configuration; retain AI Studio-compatible signing/build support unless intentionally migrating.
- `docs/DEVELOPMENT.md`: local build, wireless ADB, test workflow.
- `docs/CHARTS.md`: exported SSC/JSON semantics and import constraints.
- `patterns/`: **ignored local dataset**, not a Gradle module or automatic input asset directory.
- `stepmania-pcapp/`: **ignored separate desktop upstream Git clone**, not part of this project or an Android dependency.
- `.agents/`: ignored local tooling settings; **never commit private connection configs**.
- `pad_mapping.json`: ignored local mapping file; the Android app uses its own private preferences.

Do not delete, move, bulk-reformat, or commit ignored local reference directories. Keep the Android repository lightweight.

## Exported chart-data safety

The local `patterns/` archive includes SSC-derived four-arrow charts with events and `time_ms`. Times are relative to the original **song audio timeline**, incorporating offsets; they are not automatically gameplay-clock times.

For an early chart import, use **only** eligible `dance-single` records with four lanes, valid numeric `time_ms`, and note kinds the engine actually supports. Do not flatten holds, rolls, mines, fakes, or lifts into taps. Do not invent timestamps for charts with warps and missing `time_ms`. Simultaneous notes share a scheduled hit time.

Do not assume all 251 exports are ready for gameplay, or that music is included. Obtain appropriate rights before distributing any imported third-party assets. Only select, documented app-ready files should be intentionally added under `app/src/main/assets/` after the feature exists.

## Work habits

1. Inspect `git status` and read the existing implementation before modifying files.
2. Work in small, incremental, buildable steps. Do not regenerate the entire project.
3. Preserve input mappings, project identity, stored preferences, and lifecycle behavior.
4. Add focused deterministic tests for input edges, timing windows, pause/resume, and chart parsing as features land.
5. Build and run relevant tests using **actual available tools**; distinguish compilation, emulator tests, and physical mat tests in reports.
6. If build tools, a debug signing key, or Android SDK platform are missing, state the blocker rather than inventing a successful build.
7. Keep `README.md`, `docs/` and version/status descriptions current when code changes.
8. Never commit secrets, keystores, local device reports, large local reference clones, or exported chart archives by accident.
9. Never commit/push without explicit authorization.

For Google AI Studio prompts: Gemini does **not** automatically see the user's local filesystem; embed required technical mappings or explicitly provide any needed assets. Do not ask Gemini to fetch the desktop reference directory from a Linux path.

## Done means

A milestone is done only when the intended features are implemented, the Android project builds in an available environment, tests are reported without guessing, and the existing confirmed USB-mat input behavior is preserved. A working physical-device claim requires an actual Xiaomi Pad 7 test.
