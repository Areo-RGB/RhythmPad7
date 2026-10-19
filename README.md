# RhythmPad7 — v0.2.1

Native Android rhythm game built with **Kotlin** and **Jetpack Compose** exclusively for the **Xiaomi Pad 7** paired with the physical **Microntek USB Dance Mat (`0x0079:0x0006`)**.

---

## Hardware Target & Confirmed Controller Mapping

- **Target Device**: Xiaomi Pad 7 (Android 16 / Xiaomi HyperOS 3, Landscape)
- **Physical Dance Mat**: Microntek USB Joystick
  - **Vendor ID**: `0x0079`
  - **Product ID**: `0x0006`
  - **Connection**: Dance Mat → USB Hub → USB-C OTG Adapter → Xiaomi Pad 7

### Confirmed Android Input Mapping

| Physical Dance-Mat Panel | Android Input | Gameplay Role |
|---|---|---|
| **LEFT** | `KeyEvent.KEYCODE_BUTTON_1` (`188`) | Left Lane |
| **DOWN** | `KeyEvent.KEYCODE_BUTTON_2` (`189`) | Down Lane |
| **UP** | `KeyEvent.KEYCODE_BUTTON_3` (`190`) | Up Lane |
| **RIGHT** | `KeyEvent.KEYCODE_BUTTON_4` (`191`) | Right Lane |
| **SQUARE** | `KeyEvent.KEYCODE_BUTTON_5` (`192`) | Auxiliary / Remappable |
| **TRIANGLE** | `KeyEvent.KEYCODE_BUTTON_6` (`193`) | Auxiliary / Remappable |
| **CROSS** | `KeyEvent.KEYCODE_BUTTON_7` (`194`) | Auxiliary / Remappable |
| **CIRCLE** | `KeyEvent.KEYCODE_BUTTON_8` (`195`) | Auxiliary / Remappable |
| **SELECT** | `KeyEvent.KEYCODE_BUTTON_9` (`196`) | Auxiliary / Remappable |
| **START** | `KeyEvent.KEYCODE_BUTTON_10` (`197`) | Auxiliary / Remappable |
| **CENTER** | Positive `MotionEvent.AXIS_Y > 0.5` | Diagnostic Telemetry Only |

---

## What's New in v0.2.1

### Phase 1 — Monotonic `GameplayClock`
- Replaced frame-by-frame integer millisecond accumulation with an authoritative `GameplayClock` anchored to `SystemClock.uptimeMillis()`.
- Calculates elapsed gameplay time directly from clock anchors (`currentUptimeMs - startUptimeMs - accumulatedPauseDurationMs`).
- Eliminates cumulative integer truncation drift at **60 Hz, 90 Hz, 120 Hz, and 144 Hz** refresh rates.
- Prevents frame stalls from permanently slowing down note scrolling.
- Preserves exact millisecond offsets across Pause and Resume transitions.

### Phase 2 — Hardware `KeyEvent.eventTime` Hit Accuracy
- Converts real hardware `KeyEvent.eventTime` timestamps into `GameplayClock` coordinates inside `RhythmGameViewModel.processInputEvent()`.
- Guarantees that delayed rendering frames cannot arbitrarily change or miss the timing judgment of an already-received physical dance-mat press.
- Maintains **EXCELLENT** (`±70 ms`) and **GOOD** (`±150 ms`) hit windows, independent simultaneous jump hits, and duplicate `ACTION_DOWN` suppression while held.

### Phase 3 — Centered 560–640 dp 4-Lane Note Highway
- Fixed the Compose width modifier order so the 4-lane note highway is constrained to **560–640 dp** centered on the Xiaomi Pad 7 landscape display (`PlayfieldGeometry`).
- All four lanes (`LEFT`, `DOWN`, `UP`, `RIGHT`) have equal width, clear vertical lane separators, high-contrast dark highway tracks, and outer side rails.
- Stationary receptors and upward-scrolling notes share identical `PlayfieldGeometry.laneCenterX` coordinates and large standing-readable arrow geometry.

### Phase 4 — Three-Zone Landscape Gameplay UI
- Replaced overlapping full-width overlays with a dedicated three-column landscape layout:
  - **Left Zone**: Back button, practice chart title, BPM, note progress bar, round phase/time status, physical USB mat status, live `L / D / U / R` panel counters, and optional Debug Telemetry HUD.
  - **Center Zone**: Unobstructed 4-lane note highway, target receptors, scrolling notes, hit bursts, countdown (`3, 2, 1, GO!`), timing judgment banner (`EXCELLENT`, `GOOD`, `MISS` + ms offset), and live combo counter.
  - **Right Zone**: Touch controls (`Restart`, `Debug HUD`, `Pause`), live 7-digit Score, Accuracy %, Max Combo, and Judgment Breakdown (`EXCELLENT`, `GOOD`, `MISS`).

---

## Building & Testing

```bash
# Run unit and Robolectric test suite (25 tests)
gradle :app:testDebugUnitTest

# Assemble debug APK (automatically copies app-debug.apk to project root)
gradle assembleDebug
```
