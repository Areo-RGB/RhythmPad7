# StepMania source reference

The StepMania 5 PC source lives in `stepmania-pcapp/`, which is git-ignored, so other machines and LLM sessions working from this repository cannot see it. These files hold verbatim excerpts of the parts RhythmPad7 ports or plans to port, each tagged with its original path and line numbers.

| File | Covers |
| --- | --- |
| [ported-timing-scrolling-holds.md](ported-timing-scrolling-holds.md) | MsdFile tokenizer, TimingData beat↔time, SCROLLS/SPEEDS, `GetYOffset`, hold/roll life — the originals of `core/chart` and `core/gameplay` |

Planned work keeps its own self-contained folder with plan + excerpts, e.g. [`../roadmap-5-7-judgments-and-high-scores/`](../roadmap-5-7-judgments-and-high-scores/) (judgment windows, grading, judgment graphics, high scores).

Notes for whoever implements from these:

- `TapNoteScore` order in StepMania is None < HitMine < AvoidMine < CheckpointMiss < Miss < W5 < W4 < W3 < W2 < W1 < CheckpointHit; comparisons like `tns >= TNS_W3` mean "W3 or better".
- `Grade` runs the other way: `Grade_Tier01` (AAAA) is the best, larger values are worse.
- RhythmPad7 plays the `dance` game in normal (non-Oni) mode with default preferences (`TimingWindowScale` 1, `TimingWindowAdd` 0), not Beginner-merciful scoring.
- Values in `Themes/default/metrics.ini` override `Themes/_fallback/metrics.ini`.

Source: StepMania 5 source tree as checked out locally in `stepmania-pcapp/`. License: MIT-style, full text in [LICENSE-StepMania.txt](LICENSE-StepMania.txt).
