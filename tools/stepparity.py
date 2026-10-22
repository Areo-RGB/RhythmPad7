"""Python port of ITGmania's StepParity (foot assignment) and TechCounts for dance-single.

Source: ITGmania src/StepParityDatastructs.*, StepParityGenerator.*, StepParityCost.*, TechCounts.cpp
(c) 2023 Michael Votaw, MIT-style license (see those files). Mines/fakes are not modelled: the
Endless phrase pool drops them before analysis.
"""
import math

INVALID = -1
NONE, LH, LT, RH, RT = 0, 1, 2, 3, 4
FEET = (LH, LT, RH, RT)
FOOT_MASKS = (0, 1, 2, 4, 8)
OTHER_PART = (NONE, LT, LH, RT, RH)

# StepParityCost.h
DOUBLESTEP, BRACKETJACK, JACK, SLOW_BRACKET, TWISTED_FOOT = 850, 20, 30, 300, 100000
BRACKETTAP, HOLDSWITCH, FOOTSWITCH, FACING, DISTANCE, SPIN, SIDESWITCH = 400, 55, 325, 2, 6, 1000, 130
JACK_THRESHOLD, SLOW_BRACKET_THRESHOLD = 0.1, 0.15
SLOW_FOOTSWITCH_THRESHOLD, SLOW_FOOTSWITCH_IGNORE = 0.2, 0.4

# TechCounts.cpp
JACK_CUTOFF, FOOTSWITCH_CUTOFF, DOUBLESTEP_CUTOFF = 0.176, 0.3, 0.235
TECH = ("crossover", "footswitch", "sideswitch", "jack", "bracket", "doublestep")


class Layout:
  """Steps.cpp dance-single: Left (0,1), Down (1,0), Up (1,2), Right (2,1)."""

  def __init__(self):
    self.cols = [(0, 1), (1, 0), (1, 2), (2, 1)]
    self.up, self.down, self.side = [2], [1], [0, 3]
    n = self.n = len(self.cols)
    self.dist = {}
    self.fx = {}
    self.fy = {}
    for l in range(n):
      for r in range(n):
        dx = self.cols[l][0] - self.cols[r][0]
        dy = self.cols[l][1] - self.cols[r][1]
        d = math.sqrt(dx * dx + dy * dy)
        self.dist[l, r] = d
        if d == 0:
          self.fx[l, r] = self.fy[l, r] = 0.0
        else:
          self.fx[l, r] = max(0.0, _facing(self._diff(l, r, 0)))
          self.fy[l, r] = max(0.0, _facing(self._diff(l, r, 1)))
    self.permute = {0: []}
    for mask in range(1 << n):
      if bin(mask).count("1") > 4:
        continue
      p = self._permute(mask, [NONE] * n, 0)
      if p:
        self.permute[mask] = p

  def _diff(self, l, r, axis):
    if l == r:
      return 0.0
    dx = self.cols[r][0] - self.cols[l][0]
    dy = self.cols[r][1] - self.cols[l][1]
    v = (dx, dy)[axis] / math.sqrt(dx * dx + dy * dy)
    neg = v <= 0
    v = v ** 4
    return -v if neg else v

  def bracket_ok(self, a, b):
    # C++ compares in float32, where sqrt(2)^2 rounds to exactly 2; double gives 2.0000000000000004
    return self.dist[a, b] ** 2 <= 2 + 1e-6

  def distance(self, a, b):
    return 0.0 if a == INVALID or b == INVALID else self.dist[a, b]

  def xpen(self, a, b):
    return 0.0 if a == INVALID or b == INVALID else self.fx[a, b]

  def ypen(self, a, b):
    return 0.0 if a == INVALID or b == INVALID else self.fy[a, b]

  def avg(self, a, b):
    if a == INVALID and b == INVALID:
      return (0, 0)
    if a == INVALID:
      return self.cols[b]
    if b == INVALID:
      return self.cols[a]
    return ((self.cols[a][0] + self.cols[b][0]) / 2, (self.cols[a][1] + self.cols[b][1]) / 2)

  def _permute(self, mask, cols, c):
    if c >= len(cols):
      where = {f: INVALID for f in FEET}
      for i, f in enumerate(cols):
        if f != NONE:
          where[f] = i
      if (where[LH] == INVALID and where[LT] != INVALID) or (where[RH] == INVALID and where[RT] != INVALID):
        return []
      if where[LH] != INVALID and where[LT] != INVALID and not self.bracket_ok(where[LH], where[LT]):
        return []
      if where[RH] != INVALID and where[RT] != INVALID and not self.bracket_ok(where[RH], where[RT]):
        return []
      return [tuple(cols)]
    if mask & (1 << c):
      out = []
      for f in FEET:
        if f in cols:
          continue
        nc = list(cols)
        nc[c] = f
        out += self._permute(mask, nc, c + 1)
      return out
    return self._permute(mask, cols, c + 1)


def _facing(v):
  return (-min(v, 0.0)) ** 1.8 * 100


class State:
  __slots__ = ("combined", "moved_mask", "holding_mask", "combined_mask", "where", "hitting", "moved", "holding")

  def __init__(self, n):
    self.combined = [NONE] * n
    self.moved_mask = self.holding_mask = self.combined_mask = 0
    self.where = [INVALID] * 5
    self.hitting = [INVALID] * 5
    self.moved = [False] * 5
    self.holding = [False] * 5

  def key(self):
    return self.combined_mask | (self.moved_mask << 30) | (self.holding_mask << 46)


class Row:
  def __init__(self, n, beat, second, notes, holds):
    self.n, self.beat, self.second = n, beat, second
    self.notes = notes  # per column: None or (beat, hold_length)
    self.holds = holds  # per column: None or (beat, hold_length) of a hold started earlier
    self.note_mask = sum(1 << c for c in range(n) if notes[c] is not None)
    self.hold_mask = sum(1 << c for c in range(n) if holds[c] is not None)
    self.columns = [NONE] * n
    self.where = [INVALID] * 5
    self.note_count = 0

  def set_feet(self, st):
    for c in range(self.n):
      if self.notes[c] is not None:
        self.columns[c] = st.combined[c]
        self.where[st.combined[c]] = c
        self.note_count += 1


def build_rows(notes, n=4):
  """notes: [(beat, second, column, hold_length or 0)] sorted by second. Mirrors CreateRows."""
  rows = []
  active = [None] * n
  cur, cur_sec, cur_beat = None, None, None

  def flush():
    holds = [None] * n
    for c in range(n):
      h = active[c]
      holds[c] = h if h is not None and h[2] < cur_sec else None
    rows.append(Row(n, cur_beat, cur_sec, [(x[0], x[1]) if x else None for x in cur],
                    [(h[0], h[1]) if h else None for h in holds]))

  for beat, sec, col, hold in notes:
    if cur_sec != sec:
      if cur_sec is not None:
        flush()
      cur_sec, cur_beat = sec, beat
      cur = [None] * n
      for c in range(n):
        if active[c] is not None and beat > active[c][0] + active[c][1]:
          active[c] = None
    cur[col] = (beat, hold if hold > 0 else -1)
    if hold > 0:
      active[col] = (beat, hold, sec)
  if cur_sec is not None:
    flush()
  return rows


class Analyzer:
  def __init__(self, layout):
    self.L = layout

  # ---- StepParityGenerator ----
  def analyze(self, rows):
    L, n = self.L, self.L.n
    cache = {}
    start = {"state": State(n), "second": rows[0].second - 1, "cost": 0.0, "prev": None, "row": -1}
    prev_nodes = [start]
    for i, row in enumerate(rows):
      perms = L.permute.get(row.note_mask | row.hold_mask) or L.permute.get(row.note_mask) or []
      state_map = {}
      result = []
      for init in prev_nodes:
        elapsed = row.second - init["second"]
        for cols in perms:
          rs = self._result_state(init["state"], row, cols, cache)
          cost = self._cost(init["state"], rs, row, rows[i - 1] if i > 0 else None, cols, elapsed)
          k = rs.key()
          total = init["cost"] + cost
          ex = state_map.get(k)
          if ex is not None:
            if total < ex["cost"]:
              ex["cost"], ex["prev"] = total, init
          else:
            node = {"state": rs, "second": row.second, "cost": total, "prev": init, "row": i}
            state_map[k] = node
            result.append(node)
      prev_nodes = result
    if not prev_nodes:
      return False
    best = min(prev_nodes, key=lambda x: x["cost"])
    path = []
    while best is not start and best is not None:
      path.append(best)
      best = best["prev"]
    path.reverse()
    if len(path) != len(rows):
      return False
    for row, node in zip(rows, path):
      row.set_feet(node["state"])
    return True

  def _result_state(self, init, row, cols, cache):
    n = self.L.n
    rs = State(n)
    for i, f in enumerate(cols):
      if f == NONE:
        continue
      rs.hitting[f] = i
      if row.holds[i] is None or init.combined[i] != f:
        rs.moved[f] = True
    for i, f in enumerate(cols):
      if f == NONE:
        continue
      if row.holds[i] is not None:
        rs.holding[f] = True
      bit = 1 << i
      if row.hold_mask & bit:
        rs.holding_mask |= FOOT_MASKS[f]
      if not (row.hold_mask & bit) or init.combined[i] != f:
        rs.moved_mask |= FOOT_MASKS[f]
    for i in range(n):
      if cols[i] != NONE:
        rs.combined[i] = cols[i]
        continue
      p = init.combined[i]
      if p in (LH, RH):
        if not rs.moved[p]:
          rs.combined[i] = p
      elif p == LT:
        if not rs.moved[LT] and not rs.moved[LH]:
          rs.combined[i] = p
      elif p == RT:
        if not rs.moved[RT] and not rs.moved[RH]:
          rs.combined[i] = p
    for i in range(n):
      if rs.combined[i] != NONE:
        rs.where[rs.combined[i]] = i
      rs.combined_mask |= rs.combined[i] << (i * 3)
    k = rs.key()
    if k in cache:
      return cache[k]
    cache[k] = rs
    return rs

  # ---- StepParityCost ----
  def _cost(self, ini, res, row, prev_row, cols, elapsed):
    L = self.L
    lh, lt, rh, rt = res.hitting[LH], res.hitting[LT], res.hitting[RH], res.hitting[RT]
    moved_l = res.moved[LH] or res.moved[LT]
    moved_r = res.moved[RH] or res.moved[RT]
    ini_l = (ini.moved[LH] and not ini.holding[LH]) or (ini.moved[LT] and not ini.holding[LT])
    ini_r = (ini.moved[RH] and not ini.holding[RH]) or (ini.moved[RT] and not ini.holding[RT])
    did_jump = ini_l and ini_r
    jack_l = (not did_jump and moved_l and ini_l and (
      (lh > INVALID and ini.combined[lh] == LH and not res.holding[LH]) or
      (lt > INVALID and ini.combined[lt] == LT and not res.holding[LT])))
    jack_r = (not did_jump and moved_r and ini_r and (
      (rh > INVALID and ini.combined[rh] == RH and not res.holding[RH]) or
      (rt > INVALID and ini.combined[rt] == RT and not res.holding[RT])))
    cost = 0.0

    # hold switch
    if row.hold_mask:
      for c in range(row.n):
        if row.holds[c] is None:
          continue
        rc, ic = res.combined[c], ini.combined[c]
        if (rc in (LH, LT) and ic not in (LH, LT)) or (rc in (RH, RT) and ic not in (RH, RT)):
          pf = ini.where[rc]
          cost += HOLDSWITCH * (1 if pf == INVALID else L.distance(c, pf))
      # bracket tap
      for heel, toe, a, b in ((lh, lt, LH, LT), (rh, rt, RH, RT)):
        if heel != INVALID and toe != INVALID:
          jp = 1 / elapsed if (ini.moved[a] or ini.moved[b]) else 1
          if row.holds[heel] is not None and row.holds[toe] is None:
            cost += BRACKETTAP * jp
          if row.holds[toe] is not None and row.holds[heel] is None:
            cost += BRACKETTAP * jp

    one_foot = moved_l != moved_r
    if one_foot and res.holding_mask == 0 and not did_jump:
      # bracket jack
      if jack_l and res.moved[LH] and res.moved[LT]:
        cost += BRACKETJACK
      if jack_r and res.moved[RH] and res.moved[RT]:
        cost += BRACKETJACK
      # doublestep
      ds = (moved_l and not jack_l and ini_l) or (moved_r and not jack_r and ini_r)
      if prev_row is not None:
        for h in prev_row.holds:
          if h is None:
            continue
          end = h[0] + h[1]
          if prev_row.beat < end < row.beat or end >= row.beat:
            ds = False
      if ds:
        cost += DOUBLESTEP

    # slow bracket
    if elapsed > SLOW_BRACKET_THRESHOLD and one_foot and sum(x is not None for x in row.notes) >= 2:
      cost += (elapsed - SLOW_BRACKET_THRESHOLD) * SLOW_BRACKET

    # twisted foot
    lpos, rpos = L.avg(lh, lt), L.avg(rh, rt)
    crossed = rpos[0] < lpos[0]
    rback = rh != INVALID and rt != INVALID and L.cols[rt][1] < L.cols[rh][1]
    lback = lh != INVALID and lt != INVALID and L.cols[lt][1] < L.cols[lh][1]
    if not crossed and (rback or lback):
      cost += TWISTED_FOOT

    # facing
    elh, elt, erh, ert = res.where[LH], res.where[LT], res.where[RH], res.where[RT]
    if elt == INVALID:
      elt = elh
    if ert == INVALID:
      ert = erh
    cost += (L.xpen(elh, erh) + L.xpen(elt, ert) + L.ypen(elh, elt) + L.ypen(erh, ert)) * FACING

    # spin
    plp = L.avg(ini.where[LH], ini.where[LT])
    prp = L.avg(ini.where[RH], ini.where[RT])
    lp, rp = L.avg(elh, elt), L.avg(erh, ert)
    if rp[0] < lp[0] and prp[0] < plp[0]:
      if (rp[1] < lp[1] and prp[1] > plp[1]) or (rp[1] > lp[1] and prp[1] < plp[1]):
        cost += SPIN

    # footswitch
    if SLOW_FOOTSWITCH_THRESHOLD <= elapsed < SLOW_FOOTSWITCH_IGNORE:
      ts = elapsed - SLOW_FOOTSWITCH_THRESHOLD
      for i in range(row.n):
        ic = ini.combined[i]
        if ic == NONE or cols[i] == NONE:
          continue
        if ic != cols[i] and ic != OTHER_PART[cols[i]]:
          cost += (ts / (SLOW_FOOTSWITCH_THRESHOLD + ts)) * FOOTSWITCH
          break

    # sideswitch
    for c in L.side:
      ic = ini.combined[c]
      if ic != cols[c] and cols[c] != NONE and ic != NONE and not res.moved[ic]:
        cost += SIDESWITCH

    # jack
    if elapsed < JACK_THRESHOLD and one_foot and (jack_l or jack_r):
      cost += (1 / (JACK_THRESHOLD - elapsed) - 1 / JACK_THRESHOLD) * JACK

    # big movements quickly
    for f in FEET:
      if not (res.moved_mask & FOOT_MASKS[f]):
        continue
      ip = ini.where[f]
      if ip == INVALID:
        continue
      other = res.hitting[OTHER_PART[f]]
      bracketing = other != INVALID
      if bracketing and other == ip:
        continue
      d = L.distance(ip, res.hitting[f]) * DISTANCE / elapsed
      cost += d * 0.2 if bracketing else d
    return cost


def tech_per_row(rows, L):
  """TechCounts::CalculateTechCountsFromRows; returns a set of TECH names per row."""
  out = []
  for i, cur in enumerate(rows):
    tech = set()
    if cur.note_count >= 2:
      if cur.where[LH] != INVALID and cur.where[LT] != INVALID:
        tech.add("bracket")
      if cur.where[RH] != INVALID and cur.where[RT] != INVALID:
        tech.add("bracket")
    if i == 0:
      out.append(tech)
      continue
    prev = rows[i - 1]
    el = cur.second - prev.second
    if cur.note_count == 1 and prev.note_count == 1:
      for f in FEET:
        if cur.where[f] == INVALID or prev.where[f] == INVALID:
          continue
        if prev.where[f] == cur.where[f]:
          if el < JACK_CUTOFF:
            tech.add("jack")
        elif el < DOUBLESTEP_CUTOFF:
          tech.add("doublestep")

    def fsw(c):
      a, b = cur.columns[c], prev.columns[c]
      return a != NONE and b != NONE and b != a and OTHER_PART[b] != a and el < FOOTSWITCH_CUTOFF

    if any(fsw(c) for c in L.up + L.down):
      tech.add("footswitch")
    if any(fsw(c) for c in L.side):
      tech.add("sideswitch")

    lh, lt, rh, rt = cur.where[LH], cur.where[LT], cur.where[RH], cur.where[RT]
    plh, plt, prh, prt = prev.where[LH], prev.where[LT], prev.where[RH], prev.where[RT]
    if rh != INVALID and plh != INVALID and prh == INVALID:
      if L.avg(rh, rt)[0] < L.avg(plh, plt)[0]:
        if i > 1:
          pprh = rows[i - 2].where[RH]
          if pprh != INVALID and pprh != rh:
            tech.add("crossover")
        else:
          tech.add("crossover")
    elif lh != INVALID and prh != INVALID and plh == INVALID:
      if L.avg(prh, prt)[0] < L.avg(lh, lt)[0]:
        if i > 1:
          pplh = rows[i - 2].where[LH]
          if pplh != INVALID and pplh != lh:
            tech.add("crossover")
        else:
          tech.add("crossover")
    out.append(tech)
  return out
