# Extracted dance-chart data

## What is available

The local `patterns/` folder is an **SSC-derived export**, originally generated for the separate Dance Pad project, and is intentionally ignored by the Android Git repository. No files were moved or removed when organizing RhythmPad7.

The data README reports:
- **35 songs**
- **251 charts**, **150,488 note events**
- **175 dance-single charts** with four lanes
- **70 beginner/easy four-lane charts**
- **241 charts** with calculated millisecond event times
- **10 warp charts** without precomputed `time_ms`

Chart JSON files occupy roughly 31 MB; the full local archive including SSC originals is roughly 34 MB. **There is no audio or artwork in this export.**

## Source files

```text
patterns/
├── README.md
├── index.json
├── beginner-and-easy.json
├── charts/                   One compact JSON file per chart
└── originals/                Original SSC files for provenance
    ├── installed/
    └── repository/
```

`index.json` contains catalog metadata and chart filenames; `beginner-and-easy.json` is a smaller discovery index. Each `charts/*.json` file holds its metadata, timing specification, and an `events` array.

A typical dance-single event is conceptually:

```json
{
  "beat": 40.0,
  "beat_fraction": "40",
  "lane": 0,
  "direction": "left",
  "kind": "tap",
  "symbol": "1",
  "time_ms": 13324.333,
  "in_fake_region": false,
  "in_warp_region": false
}
```

Read the actual file's full schema rather than assuming only these fields exist. The `lane` value is indexed against that chart's `lanes` array:

| Lane index | Dance direction | Microntek button |
| --- | --- | --- |
| 0 | Left | BUTTON_1 |
| 1 | Down | BUTTON_2 |
| 2 | Up | BUTTON_3 |
| 3 | Right | BUTTON_4 |

**Critical timing detail:** `time_ms` is relative to the **original music/audio timeline**, including SSC offsets. It is **not** an elapsed time measured from the Android app's Start button. Later audio playback will need a defined chart-to-song clock origin and calibration.

## Note kinds and complex timing

The extractor preserves kinds including:
- `tap` and tap symbol `1`
- `hold_start` / `hold_end` (symbols `2` / `3`)
- `roll_start` (symbol `4`)
- mines, lifts, fakes, keysounds and attacks

Hold heads can have `end_beat` and `end_time_ms`. Simultaneous lane events at the same beat represent jumps. Chart timing also preserves BPM segments, stops, delays, warps and song/chart offset scope.

**Do not silently treat holds, mines, fakes, rolls, or lifts as ordinary taps.** A tap-only import must explicitly filter unsupported kinds and document the resulting behavior. Do not invent timestamps when `time_ms` is null. Advanced timing and unsupported note kinds should block a chart from normal scoring until implemented.

## Safe first-import strategy

Before integrating many charts, first implement a correct moving-note engine and judgment clock (v0.2), then add a focused JSON loader for a handful of supported charts.

A local data inspection identified **nine** Beginner/Easy four-arrow charts containing *only tap events*, valid `time_ms` and no stops/delays/warps. Example candidates:

| Chart | Difficulty | Events |
| --- | --- | ---: |
| Fantastic World | Beginner | 57 |
| DNA | Beginner | 57 |
| BOSSY | Beginner | 85 |
| Roadman | Beginner | 92 |
| Dysangel | Beginner | 104 |

The absence of advanced note types in these particular exports does not provide audio. They are candidates for **silent note-sequence testing**, not automatically synchronized playable songs.

Proposed chart-import gate for each selected file:
1. Require `step_type == "dance-single"` and lanes `["left","down","up","right"]`.
2. Require `milliseconds_available == true`, no missing numeric `time_ms` on scored events, and no unresolved warnings.
3. Accept only the note kinds supported by the current engine; start with `tap`.
4. Preserve simultaneous note timestamps and fractional precision.
5. Keep original title, artist, difficulty, meter, chart ID, and source attribution.
6. Convert audio-relative times to your local practice-clock timeline explicitly; do not infer song playback that does not exist.
7. Copy only the approved subset into `app/src/main/assets/patterns/`, and add parser/clock/judgment tests.

## Local data inspection

From the repository root:

```bash
python3 - <<'PY'
import json
from pathlib import Path
root = Path("patterns")
entries = json.loads((root / "index.json").read_text())["entries"]
print("all charts:", len(entries))
print("dance-single charts:", sum(e["step_type"] == "dance-single" for e in entries))
for chart in entries:
    if chart["step_type"] == "dance-single" and chart["difficulty"] == "Beginner":
        print(chart["title"], chart["file"])
PY
```

Do not bulk-copy `patterns/` into the Android project. Review third-party licensing and redistribution rights before committing exported charts or any associated music/artwork.
