# Testing RhythmPad7

Three layers, from fastest to closest-to-real:

| Layer | Runs on | Covers | Command |
|---|---|---|---|
| Unit tests (incl. virtual mat) | PC (JVM + Robolectric) | Parsing, judgments, scoring, debounce, auto-sync, timing window | `./gradlew :app:testDebugUnitTest` |
| Maestro UI flows | Tablet over adb | Navigation, settings, profiles, persistence, a full round, screenshots | `cd .maestro && maestro --device <serial> test .` |
| Real dance mat | Tablet + Microntek mat | Feel, real input latency, mat chatter | Play a song |

## Virtual mat (unit tests)

Maestro and `adb shell input` cannot act as the dance mat: they inject keys through
Android's virtual keyboard (rejected by `ControllerManager`'s device filter) and cannot
press with millisecond timing. Gameplay timing is therefore tested on the JVM with a
simulated mat that steps on every note of the practice chart through the real
`RhythmGameViewModel.processInputEvent` path, with a chosen lag and jitter.

- `MockMatAutoSyncTest` — auto-sync converges on the mat's lag:
  45 ±8 ms late → offset +44 ms after one round (round-2 mean error ≈ 0 ms);
  30 ±6 ms early → −29 ms; sloppy ±90 ms → offset stays 0 ("too uneven, not applied").
- `MockMatTimingWindowTest` — timing-window scale changes judgments and score as StepMania's
  `TimingWindowScale` does (e.g. 30 ms late: Perfect at ×1, Flawless at ×2, Great at ×0.5;
  200 ms late: Miss at ×1, Bad at ×1.25), and a wider window never lowers the score.
- `DebounceAutoSyncTest` — `InputDebouncer` (chatter swallowed, quick taps kept, keys independent)
  and `AutoSync` batch math.

The virtual mat feeds `processInputEvent` directly, so it bypasses `ControllerManager`'s
debounce; debounce is covered by `DebounceAutoSyncTest`.

Tests that change `Judgment.timingWindowScale` (global) must reset it to `1.0` in `@After`.

## Maestro flows (`.maestro/`)

Compose `testTag`s are exposed as resource IDs (`testTagsAsResourceId` in `MainActivity`),
so flows select by `id:` (e.g. `home_button_settings`). Prefer ids over on-screen text.

| Flow | Checks |
|---|---|
| `smoke` | Every screen opens and back works: Home → Settings → Controller Settings → back → Song select. Run after every install. |
| `profile` | Switch to Guest, restart, still Guest, switch back to Finley; footer follows. |
| `timing-window` | Steps to ×2.00, wraps to ×0.50, survives a restart; restores ×1.00. Regexes accept `1,00` (German locale) and `1.00`. |
| `settings` | Reset offset to 0, toggle auto-sync off/on, Controller Settings round trip. |
| `speed-control` | In-game speed stepper clamps at M120 and C450 (no wrap), is saved across a restart; leaves M180. |
| `endless` | Endless plays a library song ("Endless · <song>", EVENT STAGE) and generates notes; difficulty clamps at BEGINNER/CHALLENGE and is saved; Crossovers chip toggles and is saved; Jacks chip hidden at Beginner (no jacks in that pool); HOLDS switch flips; back goes Home. Leaves MEDIUM, all chips on, holds off. |
| `song-folder` | Needs the 5 Club Fantastic songs in `/sdcard/RhythmPad7/Songs` (`tools/push_songs.sh`): song select shows 8 songs incl. BACK UP/DNA/Fantastic World, no `128.000000` BPM, BACK UP starts with its background. |
| `full-round` | Plays the practice chart with no input **as Guest** (so Finley's scores are never touched), checks the results screen, switches back to Finley. ~1.5 min. |
| `screenshots` | Captures home, settings, controller settings, song select into `screenshots/` (git-ignored) for design review. |

`subflows/ensure-guest.yaml` / `ensure-finley.yaml` are helpers; `config.yaml` sets the run order.
Every flow must leave the app on **Finley** with timing ×1.00 and auto-sync ON.

Run all: `cd .maestro && maestro --device 4c637b9e test .` (tablet serial from `adb devices`).
One flow: `maestro --device 4c637b9e test .maestro/smoke.yaml`.

Without the Maestro driver (see quirk below): `tools/run_flows.sh .maestro/endless.yaml ...` runs flows
through the `phone` CLI and force-stops the app over adb before each flow. `phone` cannot kill the app
mid-flow, so `subflows/to-home.yaml` backs out of gameplay after every `launchApp`; restart
persistence checks are therefore weaker there (in-memory state survives).

### Xiaomi tablet quirk

Maestro installs a helper app (`dev.mobile.maestro`) on each run. HyperOS blocks adb installs
unless **Developer options → Install via USB** is enabled (needs a Xiaomi account and SIM/network
check); otherwise runs fail with `INSTALL_FAILED_USER_RESTRICTED` / "Device server died".
Approve the on-screen install prompt or enable that option.

### Not testable with Maestro
- Gameplay judgments, auto-sync, debounce — need mat-timed input (use the virtual mat tests).
- The results of a *good* round — Maestro cannot hit notes.
