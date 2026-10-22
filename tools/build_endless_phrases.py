#!/usr/bin/env python3
"""Cut patterns/charts dance-single charts into 1-measure (4-beat) phrases for Endless mode.

Output: app/src/main/assets/endless/phrases.json
  {"levels": [[phrase, ...] x5]}  level 0=Beginner .. 4=Challenge
  phrase = [techMask, [[beatOffset, laneMask, holdBeats], ...]]  (holdBeats 0 = tap)
techMask bits follow stepparity.TECH (crossover, footswitch, sideswitch, jack, bracket,
doublestep), found by ITGmania's StepParity/TechCounts run over the whole chart re-timed at
Endless BPM. Mines, fakes, lifts and notes in warp/fake regions are skipped.
"""
import json, pathlib, sys
from stepparity import Analyzer, Layout, TECH, build_rows, tech_per_row

ENDLESS_BPM = 130
LAYOUT = Layout()

ROOT = pathlib.Path(__file__).resolve().parent.parent
LEVEL = {"Beginner": 0, "Easy": 1, "Medium": 2, "Hard": 3, "Challenge": 4}

levels = [set() for _ in LEVEL]
for path in sorted((ROOT / "patterns/charts").glob("*.json")):
    chart = json.loads(path.read_text())
    if chart["step_type"] != "dance-single" or chart["difficulty"] not in LEVEL:
        continue
    measures = {}
    events = [e for e in chart["events"]
              if e["kind"] in ("tap", "hold_start", "roll_start") and not e["in_fake_region"] and not e["in_warp_region"]]
    events.sort(key=lambda e: (e["beat"], e["lane"]))
    if not events:
        continue
    # Foot placement over the whole chart, then tech per measure
    spb = 60.0 / ENDLESS_BPM
    rows = build_rows([(e["beat"], e["beat"] * spb, e["lane"],
                        (e["end_beat"] - e["beat"]) if e.get("end_beat") is not None else 0) for e in events])
    tech = {}
    if Analyzer(LAYOUT).analyze(rows):
        for row, names in zip(rows, tech_per_row(rows, LAYOUT)):
            m = int(row.beat // 4)
            tech[m] = tech.get(m, 0) | sum(1 << TECH.index(n) for n in names)
    else:
        # ITGmania gives up on these too (row with no valid foot placement); tech unknown, skip chart
        print("parity failed, skipped:", path.name, file=sys.stderr)
        continue
    for e in events:
        m, off = divmod(e["beat"], 4.0)
        hold = 0.0
        if e.get("end_beat") is not None and e["end_beat"] - e["beat"] + off <= 4.0:
            hold = round(e["end_beat"] - e["beat"], 4)
        rows = measures.setdefault(int(m), {})
        key = (round(off, 4), hold)
        rows[key] = rows.get(key, 0) | (1 << e["lane"])
    for m, rows in measures.items():
        phrase = tuple(sorted((off, mask, hold) for (off, hold), mask in rows.items()))
        if phrase:
            levels[LEVEL[chart["difficulty"]]].add((tech.get(m, 0), phrase))

out = {"tech": list(TECH),
       "levels": [sorted([t, [list(r) for r in p]] for t, p in lvl) for lvl in levels]}
dest = ROOT / "app/src/main/assets/endless/phrases.json"
dest.write_text(json.dumps(out, separators=(",", ":")))
for i, lvl in enumerate(out["levels"]):
    counts = {n: sum(1 for t, _ in lvl if t & (1 << b)) for b, n in enumerate(TECH)}
    print(f"level {i}: {len(lvl)} phrases, clean {sum(1 for t, _ in lvl if t == 0)}, {counts}", file=sys.stderr)
print(dest, [len(l) for l in out["levels"]], f"{dest.stat().st_size // 1024} KiB", file=sys.stderr)
