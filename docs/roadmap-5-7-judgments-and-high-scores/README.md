# Roadmap #5 and #7: StepMania judgments and high scores

Self-contained work package for RhythmPad7 (Android, Kotlin/Compose). Everything needed beyond the repository itself is in this folder.

| File | What it is |
| --- | --- |
| [PLAN.md](PLAN.md) | The implementation plan: current app context with file paths, StepMania rules as tables, step-by-step changes, tests and acceptance checks. Start here. |
| [stepmania-source/judgment-windows-and-scoring.md](stepmania-source/judgment-windows-and-scoring.md) | Verbatim StepMania code: W1–W5 windows, judging a step, per-row scoring of jumps, combo rules, dance points / DP% |
| [stepmania-source/grading.md](stepmania-source/grading.md) | Grade weights, the 17 default-theme tiers, `GetGrade`, full-combo checks |
| [stepmania-source/judgment-graphics.md](stepmania-source/judgment-graphics.md) | Judgment sprite sheet layout, frame choice, animation commands, colors |
| [stepmania-source/assets/judgment-normal-2x6.png](stepmania-source/assets/judgment-normal-2x6.png) | The judgment sprite sheet to bundle in the app |
| [stepmania-source/high-scores.md](stepmania-source/high-scores.md) | StepMania high-score fields and comparison |
| [stepmania-source/LICENSE-StepMania.txt](stepmania-source/LICENSE-StepMania.txt) | StepMania's MIT-style license for the excerpts and the image |

Reading StepMania code:

- `TapNoteScore` order: None < HitMine < AvoidMine < CheckpointMiss < Miss < W5 < W4 < W3 < W2 < W1 < CheckpointHit, so `tns >= TNS_W3` means "Great or better".
- `Grade` is the reverse: `Grade_Tier01` (AAAA) is best; a larger value is worse.
- RhythmPad7 plays the `dance` game, normal (non-Oni) mode, default preferences (`TimingWindowScale` 1, `TimingWindowAdd` 0, `MercifulBeginner` false, `PercentageScoring` true).
- `Themes/default/metrics.ini` values override `Themes/_fallback/metrics.ini`.

Other StepMania code the app already ports (MSD tokenizer, TimingData, scrolling, holds) is in [`../stepmania-reference/`](../stepmania-reference/).
