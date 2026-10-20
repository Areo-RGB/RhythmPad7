# AGENTS.md — RhythmPad7 Engineering & Architecture Guide

## Project Overview
- **Application**: `RhythmPad7` (`com.rhythmpad7.app`)
- **Current Version**: `0.3.0` (`versionCode = 4`)
- **Target Hardware**: Xiaomi Pad 7 (Landscape) + Microntek USB Dance Mat (`VID 0x0079`, `PID 0x0006`)

---

## Core Architectural Invariants (DO NOT BREAK)

1. **Physical USB Dance Mat Priority & Device ID Filtering (`ControllerManager.kt`)**
   - Always prioritize USB Vendor ID `0x0079` and Product ID `0x0006` (`Microntek USB Joystick`).
   - Default directional mappings must remain:
     - `LEFT` → `KeyEvent.KEYCODE_BUTTON_1`
     - `DOWN` → `KeyEvent.KEYCODE_BUTTON_2`
     - `UP` → `KeyEvent.KEYCODE_BUTTON_3`
     - `RIGHT` → `KeyEvent.KEYCODE_BUTTON_4`
   - Reject events from unrelated device IDs when the target controller is connected, and never intercept Android system keys (`BACK`, `VOLUME_*`, `HOME`, `POWER`).
   - Do not reintroduce touchscreen simulated arrow buttons into gameplay.

2. **Monotonic Timing (`GameplayClock.kt`)**
   - Never accumulate per-frame integer `deltaMs` in `GameplayScreen.kt` (which causes truncation drift at 60/90/120/144 Hz and permanent slowdown on frame stalls).
   - Always derive elapsed chart time from `GameplayClock.elapsedGameplayMs()` anchored to `SystemClock.uptimeMillis()`.

3. **Hardware Input Timestamp Judgments (`RhythmGameViewModel.kt` & `GameplayRoundState.kt`)**
   - Always convert `GameInputEvent.timestamp` (sourced from `KeyEvent.eventTime`) into gameplay coordinates via `gameplayClock.toGameplayTimeMs(event.timestamp)`.
   - Never use the last rendered frame timestamp (`currentRound.currentGameTimeMs`) to judge physical button presses.

4. **Three-Zone Landscape Layout & Centered Note Highway (`GameplayScreen.kt` & `PlayfieldView.kt`)**
   - Keep the 4-lane note highway centered and constrained to `560–640 dp` (`PlayfieldGeometry`) on landscape tablets.
   - Keep `widthIn(...)` **before** `fillMaxWidth()` / `fillMaxHeight()` on the playfield `Canvas` so Compose does not stretch the 4 lanes across the entire screen width.
   - Keep the Left HUD Zone and Right Score/Controls Zone strictly outside the Center Note Highway so song and score panels never cover the arrow receptors.

5. **Song Charts & Audio Lock (`core/chart/`, `core/audio/`, `RhythmGameViewModel.syncAudio`)**
   - Songs live in `app/src/main/assets/songs/<folder>/` (`.ssc` preferred, `.sm` fallback). `MsdFile` and `TimingData` are ports of StepMania's `MsdFile.cpp` / `TimingData.cpp`; keep stop/delay/warp ordering identical and covered by `SimfileParserTest`.
   - Note hit times are `musicStartMs + TimingData.timeAtBeat(beat) * 1000`. The music starts when the clock reaches `musicStartMs`; afterwards the clock is locked to ExoPlayer's position only via `GameplayClock.nudge()` (anchor shift), never by accumulating deltas.
   - Arrow positions use StepMania beat spacing (`GameplayRoundState.scrollUnits`: SCROLLS via `displayedBeat`, SPEEDS via `displayedSpeedPercent`, × X/M multiplier); only C-mods use time spacing. Judgments always use `hitTimeMs`, never screen position.
   - The music-select preview must never stop or replace gameplay audio (`SongAudio.stopPreview()` only stops previews; `previewSong` is ignored while a round runs).

6. **Root APK Sync (`app/build.gradle.kts` & `copy_debug_apk.sh`)**
   - `assembleDebug` automatically copies the generated APK to `/app-debug.apk` in the project root. Preserve this Gradle hook across edits.
