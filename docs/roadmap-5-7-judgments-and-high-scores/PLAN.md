# Implementation plan: StepMania judgments (#7) and high scores (#5)

Two features from the RhythmPad7 roadmap, written so they can be built from this folder plus the repository alone. The StepMania code they copy is in [`stepmania-source/`](stepmania-source/); the PC source tree itself (`stepmania-pcapp/`) is not in git.

**Order: #7 first, then #5.** High scores store percentages and grades; storing them under the old scoring would make old and new records incomparable. Estimate: about one day each.

## Context: how the app scores today (v0.3.x)

| Piece | Where | Today |
| --- | --- | --- |
| Judgments | `app/src/main/java/com/example/core/model/Judgment.kt` | 3 values: `EXCELLENT` ±70 ms (100 pts), `GOOD` ±150 ms (60 pts), `MISS` >150 ms. `evaluateHit(signedErrorMs)` returns `EXCELLENT`/`GOOD`/null. Constants `EXCELLENT_WINDOW_MS`, `GOOD_WINDOW_MS`, `MISS_THRESHOLD_MS`. |
| Round state | `core/gameplay/GameplayRoundState.kt` | Immutable data class. Counts `excellentCount`, `goodCount`, `missCount`, `heldCount`, `letGoCount`; `score`; `combo`/`maxCombo`. `accuracyPercent` = (100·Exc + 60·Good + 100·Held) ÷ (100·judged taps+holds). `advanceTime(t, heldLanes)` auto-misses notes > `MISS_THRESHOLD_MS` late and runs hold/roll life. `onLanePressed(lane, t)` finds the earliest note within `GOOD_WINDOW_MS`, judges it, then **recomputes** score/counts/combo from the note list. `HOLD_POINTS` = 100. |
| Notes | `core/model/PracticeNote.kt` | Per note: `state` (PENDING/HIT/MISSED), `judgment`, `timingErrorMs`, hold fields (`holdResult` ACTIVE/HELD/LET_GO, `holdLife`, …). |
| Judgment display | `ui/screens/GameplayScreen.kt` → `JudgmentAndCombo` (~line 586) | Text "EXCELLENT!!" / "GOOD" / "MISS..." in bold italic with zoom/fade; early/late ms line; combo ≥ 4 colored by full-combo state. |
| HUD and results | `GameplayScreen.kt`: right-zone `JUDGMENTS` panel (~line 342), `ScoreFrame` (~680), `JudgmentLine` (~731), `gradeDrawable` (~830), `EvaluationOverlay` (~858) | Rows EXCELLENT/GOOD/MISS (+ OK/NG when the chart has holds). Grade picks `R.drawable.sm_grade_tierNN` from `accuracyPercent` with StepMania's 17 thresholds; Tier01 when every tap is EXCELLENT and no NG. |
| Explosions | `ui/canvas/NoteSkin.kt` `drawExplosion` (~line 289) | Tint by judgment (EXCELLENT white, GOOD green). |
| Other users of the old names | `ui/components/PlayfieldView.kt`, `ui/components/SmWidgets.kt` (`LaneArrowIcon`), `ui/components/DebugOverlayView.kt` | Reference `Judgment.EXCELLENT/GOOD` and the counts. |
| Song/chart identity | `core/chart/Simfile.kt` (`Song.folder` e.g. `songs/springtime`, `Chart.difficulty`, `Chart.steps`) and `GameplayRoundState.meta` (`RoundMeta`: title, difficultyName, …) | Practice pattern rounds have `musicStartMs == null`. |
| Round completion | `core/state/RhythmGameViewModel.kt` `syncRoundClock()` (~line 265, `if (updated.isCompleted)`) | Stops clock and audio. Forfeit = `stopRound()` from the pause menu. |
| Select Music | `ui/screens/MusicSelectScreen.kt`: `WheelItem` (~532), `DifficultyRow` (~432), `SongInfoPane` | Shows difficulty pips, step counts. |
| Tests | `app/src/test/java/com/example/RhythmPadInputTest.kt`, `SimfileParserTest.kt`, `PadNavigationTest.kt` | RhythmPadInputTest asserts the 70/150 ms windows and EXCELLENT/GOOD counts (lines ~285–340, ~484, ~512). |

Build and test: `./gradlew :app:assembleDebug` and `./gradlew :app:testDebugUnitTest` (JDK 21; `debug.keystore` copied from `~/.android/debug.keystore`). Read `AGENTS.md` first: monotonic clock and hardware-timestamp judging must stay as they are.

---

## Part 1 — #7 StepMania judgment windows

Goal: judgments, combo, percentage, grade and judgment graphics behave exactly like StepMania's `dance` game with default preferences.

### Rules to implement

From [`judgment-windows-and-scoring.md`](stepmania-source/judgment-windows-and-scoring.md) and [`grading.md`](stepmania-source/grading.md):

| Judgment | Window (±) | Dance points | Grade points | Combo | Theme color |
| --- | --- | --- | --- | --- | --- |
| W1 Flawless | 22.5 ms | 3 | 2 | continues | `#A0DBF1` |
| W2 Perfect | 45 ms | 2 | 2 | continues | `#F1E4A2` |
| W3 Great | 90 ms | 1 | 1 | continues | `#ABE39B` |
| W4 Good | 135 ms | 0 | 0 | breaks | `#86ACD6` |
| W5 Bad | 180 ms | 0 | −4 | breaks | `#958CD6` |
| Miss | > 180 ms | 0 | −8 | breaks | `#F97E7E` |
| Hold OK (Held) | — | 3 | 6 | — | — |
| Hold NG (LetGo) | — | 0 | 0 | — | — |

- A step judges the earliest unjudged note in its lane whose |error| ≤ 180 ms; the first window that contains the error wins (`Player.cpp` lines 2312–2322).
- A note not stepped on becomes Miss once it is more than 180 ms late.
- **Jumps score as one row.** A row (all arrows on the same beat) is scored once, when every arrow in it is judged: any Miss in the row makes the row a Miss, otherwise the row takes the judgment of the arrow with the largest timing offset (`LastTapNoteScoreTrack`). Judgment counts, dance points and grade points are per row. The app judges per arrow today, so keep per-arrow judging for feedback but compute totals by grouping notes by beat.
- Combo: a row judged W1–W3 adds the number of arrows in it (a jump adds 2); a row judged W4, W5 or Miss resets it to 0 (`ComboContinue`/`ComboMaintain` = W3, `ComboIsPerRow` = false for `dance`).
- DP% = actual dance points ÷ possible, where possible = (rows × 3) + (holds and rolls × 3). Show with 2 decimals, truncated not rounded (`MakePercentScore`).
- Grade: percent of grade points (actual ÷ possible, possible = rows × 2 + holds and rolls × 6) mapped through the default theme's 17 `GradePercentTier` values; then `GradeTier02IsAllW2s` applies: all taps W1 and no NG → Tier01 (AAAA); all W2-or-better and no NG → Tier02 (AAA); otherwise the grade can be no better than Tier03 (`GetGrade`, `FullComboOfScore`).
- Full combo: no Miss/W4/W5 and no NG. Its color names the worst judgment: all W1 = Flawless FC, W1/W2 = Perfect FC, up to W3 = Great FC.

### Steps

1. **`Judgment.kt`** — replace the enum with `W1, W2, W3, W4, W5, MISS`, each with `displayName` ("Flawless", "Perfect", "Great", "Good", "Bad", "Miss"), `windowMs` (Double: 22.5, 45, 90, 135, 180), `dancePoints`, `gradePoints`, `continuesCombo`, `color`. `evaluateHit(signedErrorMs)` walks W1→W5 and returns null beyond 180 ms. Keep a single `MISS_THRESHOLD_MS = 180`. Add `HOLD_HELD_DANCE_POINTS = 3`, `HOLD_HELD_GRADE_POINTS = 6`.
2. **`GameplayRoundState.kt`**
   - Replace `excellentCount`/`goodCount` with a per-judgment **row** count (e.g. `judgmentCounts: Map<Judgment, Int>` or six Int fields); keep `missCount` as the Miss entry.
   - Add a pure function `scoreTotals(notes)` that groups notes by `beat`, skips rows with any PENDING note, applies the row rule above and returns counts, dance points, grade points, combo and max combo. Call it from both `onLanePressed` and `advanceTime` instead of the current hand-kept counters, so late-frame reclaimed notes stay exact.
   - `advanceTime`: auto-miss uses the new 180 ms threshold.
   - `onLanePressed`: candidate filter uses 180 ms; totals come from `scoreTotals` (`score` becomes actual dance points).
   - Hold scoring: `HOLD_POINTS` becomes 3 dance points (`advanceTime` and the recompute loop).
   - Add `possibleDancePoints` (rows × 3 + holds × 3, from the whole chart), `percentDp` (truncated to 2 decimals), `actualGradePoints`, `possibleGradePoints`, `gradeTier` (1–17, using the rules above), `fullComboKind` (FLAWLESS/PERFECT/GREAT/null). Remove `accuracyPercent` or make it return `percentDp`.
3. **Judgment art** — copy [`stepmania-source/assets/judgment-normal-2x6.png`](stepmania-source/assets/judgment-normal-2x6.png) to `app/src/main/res/drawable-nodpi/sm_judgment_normal.png`. In `JudgmentAndCombo` draw the sprite instead of text: row = judgment index (W1=0 … Miss=5), column 0 when early (error < 0) and 1 when late (`judgment-graphics.md`, `iFrame*2 (+1 if not early)`). Animation: zoom 0.9 → 0.75 over 50 ms, hold 0.8 s (W1) or 0.5 s (others), then 100 ms to zoomY 0.5 / zoomX 2 / alpha 0 (`JudgmentW*Command` in the default theme); W5 vibrates; Miss slides down 40 px. Keep the early/late ms line under it.
4. **HUD and results** — `JUDGMENTS` panel and `EvaluationOverlay` list Flawless, Perfect, Great, Good, Bad, Miss (+ OK/NG when the chart has holds). `ScoreFrame` shows DP% (2 decimals) instead of accuracy; score number = dance points. `gradeDrawable` uses `gradeTier`. Full-combo chip text/color from `fullComboKind`. Update `DebugOverlayView` counts line.
5. **Explosions and icons** — `NoteSkin.drawExplosion` tint: W1/W2 bright white (`GhostArrowBright W1/W2Command`), W3 `(0.6,1,0.8)`, W4 `(0.6,1,1)`, W5 `(1,0.6,1)`, Miss none. Update `PlayfieldView` and `SmWidgets.LaneArrowIcon` references (`Judgment.EXCELLENT` → `Judgment.W1`).
6. **Tests**
   - Update `RhythmPadInputTest` expectations from the 70/150 ms windows to the new ones (e.g. an on-time press = W1; a 100 ms error = W4; 170 ms = W5; 200 ms = no judgment, later Miss).
   - New cases: each window edge (22.5/45/90/135/180 ms, both signs); a jump with W1 + W3 counts as one W3 row (offset rule) and adds 2 combo; a jump with one Miss is a Miss row; combo continues through W3 and resets on W4; DP% with a mix of taps, jumps and holds; grade Tier01/Tier02/Tier03 cap; full-combo kind.

Acceptance: all unit tests pass; on the tablet a song shows Flawless…Miss graphics, the results list six judgments, DP% and grade agree with the rules table for a recorded run.

Risk: ±22.5 ms Flawless is tight on a tablet. Roadmap #2 (global offset + calibration) should land with or before this so on-time steps register as W1.

---

## Part 2 — #5 High scores per chart

Goal: keep your best result per song + difficulty and show it like StepMania's profile scores. One player only (no names, no machine/profile split).

### What a record holds

| Field | Example | Notes |
| --- | --- | --- |
| key | `songs/springtime#HARD` | `Song.folder` + `#` + `Difficulty.name`; `practice` for the practice pattern |
| chartCheck | `675:9f3a1c2e` | note count + 8-hex hash of all step times and lanes; a different check means the chart changed, so the record is dropped |
| percentDp | 93.41 | primary ranking |
| gradeTier | 5 | wheel icon |
| score, maxCombo | 1842, 211 | results |
| counts | W1…W5, Miss, OK, NG | results comparison |
| fullCombo | GREAT / PERFECT / FLAWLESS / null | wheel badge |
| scrollMod, dateIso | `M120`, `2026-10-20` | info |
| playCount | 7 | incremented on every completed play, best or not |

Comparison follows StepMania's `HighScore::operator<` with its default `PercentageScoring = true` ([`high-scores.md`](stepmania-source/high-scores.md)): higher DP% wins; on equal DP%, the better grade wins (so AAAA beats AAA at 100%); otherwise the existing record stays.

### Steps

1. **`core/scores/ScoreStore.kt`** — load/save `context.filesDir/scores.json` with Android's built-in `org.json` (no new dependency). `submit(record): SubmitResult(isNewBest, previousBest)` (replace only when the new record compares higher); atomic write via temp file + rename; a corrupt or missing file loads as empty. Pure Kotlin `ScoreRecord` data class plus `toJson`/`fromJson` so it can be unit tested.
2. **Chart identity** — helper `chartKey(song, chart)` and `chartCheck(chart)` (in `core/scores`). For practice: key `practice`, check from `PracticePatternGenerator` notes.
3. **`RhythmGameViewModel`** — hold a `ScoreStore` (constructor parameter, default null for existing tests; create it in `Factory`). When `syncRoundClock` sees `updated.isCompleted`, build the record from `GameplayRoundState` (needs the current song/chart: `currentSelection` already exists) and submit it. Expose `bestScores: StateFlow<Map<String, ScoreRecord>>` and `lastResult: StateFlow<SubmitResult?>`. Forfeits (`stopRound` before completion) are not saved.
4. **Select Music** — `WheelItem`: small grade image (`sm_grade_tierNN`, ~36 dp tall) of the song's best record on the selected difficulty, plus a full-combo ring color. `DifficultyRow`: best DP% replaces "N steps" when a record exists. `SongInfoPane`: play count.
5. **Results** — `EvaluationOverlay`: "NEW RECORD" chip when `isNewBest`; previous best DP% beside the new one.
6. **Tests** (`app/src/test/.../ScoreStoreTest.kt`) — JSON round-trip; better/worse replacement and the equal-DP% grade tie-break; play count increments on a worse run; chart-check mismatch drops the old record; corrupt file → empty store.

Acceptance: finishing a song twice keeps the better run and shows NEW RECORD only when beaten; records survive an app restart; the wheel and difficulty list show the stored grade and %.

Open question: practice-pattern runs are saved under `practice` in this plan. Drop that if unwanted.
