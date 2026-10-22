# AGENTS.md — RhythmPad7 Engineering & Architecture Guide

## Project Overview
- **Application**: `RhythmPad7` (`com.rhythmpad7.app`)
- **Current Version**: `0.3.0` (`versionCode = 4`)
- **Target Hardware**: Xiaomi Pad 7 (Landscape) + Microntek USB Dance Mat (`VID 0x0079`, `PID 0x0006`)
- **No backwards compatibility needed**: personal app for one tablet. Don't keep old code paths, migrations for saved data/prefs, or shims for older Android versions; change formats and APIs freely.

---

## StepMania Source Reference
- `stepmania-pcapp/` (the PC StepMania 5 source) is git-ignored. Verbatim excerpts of already-ported code (MSD/TimingData, scrolling, holds) are in `docs/stepmania-reference/`; each planned feature has a self-contained folder with plan + excerpts (e.g. `docs/roadmap-5-7-judgments-and-high-scores/`). Read those before changing gameplay rules.

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
   - Holds/rolls follow StepMania `Player::UpdateHoldNotes`: hold life refills while the lane is pressed and drains over 250 ms when released; roll life drains over 500 ms and refills on each re-step; life 0 = NG, reaching the tail = OK. `RhythmGameViewModel.syncRoundClock` must keep passing the pressed lanes into `advanceTime`.
   - The music-select preview must never stop or replace gameplay audio (`SongAudio.stopPreview()` only stops previews; `previewSong` is ignored while a round runs).

6. **Root APK Sync (`app/build.gradle.kts` & `copy_debug_apk.sh`)**
   - `assembleDebug` automatically copies the generated APK to `/app-debug.apk` in the project root. Preserve this Gradle hook across edits.

7. **StepMania Judgment Windows & Scoring (`Judgment.kt`, `ScoreStore.kt`)**
   - Windows must follow StepMania `dance` defaults: W1 (±22.5 ms), W2 (±45 ms), W3 (±90 ms), W4 (±135 ms), W5 (±180 ms), Miss (>180 ms).
   - Jumps score as one row decided by the arrow with the largest timing offset (`LastTapNoteScoreTrack`), while combo adds every tap. Combo resets on W4, W5, or Miss.
   - Dance Points % (`percentDp`) is truncated to 2 decimals (`MakePercentScore`).
   - The windows are multiplied by `Judgment.timingWindowScale` (StepMania `TimingWindowScale`, ×0.5–×2.0, Home → TIMING WINDOW, saved in `rhythmpad_settings`). The defaults above are ×1.0; use `Judgment.missThresholdMs`, never the raw `MISS_THRESHOLD_MS`, in gameplay code.
   - High scores are persisted per profile (`PlayerProfile.scoreFileName`: `scores-finley.json`, Guest = `scores.json`) keyed by `folder#DIFFICULTY` (or `practice`) with `chartCheck` CRC verification. Comparison prioritizes `percentDp`, with equal-DP tie-breaks decided by better `gradeTier` (lower tier number). Chart changes drop old records.

8. **Input Offset, Auto-Sync & Debounce (`RhythmGameViewModel.kt`, `AutoSync.kt`, `InputDebouncer.kt`)**
   - Every step's game time is shifted by the saved `inputOffsetMs` before judging (positive = mat registers late). Arrows are never moved by it.
   - Auto-sync (ITGmania `AdjustSync`) is ON by default: every 24 non-Miss hits, if stddev < 30 ms, the mean error is added to the offset; otherwise nothing changes. Feed it only the hit's own `timingErrorMs`.
   - Lane key events pass through a 20 ms per-key `InputDebouncer` (ITGmania `InputDebounceTime`) in `ControllerManager`: presses report immediately, chatter releases are swallowed, delayed reports keep the raw `eventTime`.

9. **Player Profiles (`PlayerProfile.kt`)**
   - Profiles are Finley (default) and Guest (StepMania machine scores). The active profile is chosen by tapping the PLAYER 1 panel on Home, shown in the footer (`SmFooter.playerName`), and saved as `player_profile`.
   - Automated runs that finish rounds must play as Guest so Finley's scores stay real.

---

## Testing

See `docs/TESTING.md`. Unit tests (`./gradlew :app:testDebugUnitTest`) include a **virtual mat** (`MockMatAutoSyncTest`, `MockMatTimingWindowTest`) that plays the practice chart with chosen lag/jitter — use it for any change to judgments, scoring, offset or auto-sync, because Maestro and `adb input` cannot act as the mat. UI regressions: Maestro flows in `.maestro/` (`cd .maestro && maestro --device <serial> test .`); they select Compose `testTag`s by `id:` and must leave the app on Finley, timing ×1.00, auto-sync ON.

---

## Tablet Automation

`tools/tablet-agent/` contains the Mobilerun Portal client, `tablet` CLI, and `tablet-agent` MCP server. Read its `README.md` for setup and commands. Prefer current resource IDs from `tablet inspect` / MCP `inspect_screen` over stored coordinates. Check `verified` separately from `accepted`; never blindly repeat an action whose error reports `outcome_unknown:true`. `tablet doctor` is read-only; `--full` also round-trips clipboard/files and restores its temporary settings. Keep device profiles/tokens outside the repository. Gameplay inputs must still come from the physical dance mat.
