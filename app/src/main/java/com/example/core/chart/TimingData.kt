package com.example.core.chart

import kotlin.math.roundToInt

/** StepMania SPEEDS segment: `beat=ratio=delay=unit` (unit 0 = beats, 1 = seconds). */
data class SpeedSegment(val beat: Double, val ratio: Double, val delay: Double, val delayInSeconds: Boolean)

/**
 * Kotlin port of StepMania's `TimingData` beat ↔ time conversion (src/TimingData.cpp,
 * `GetElapsedTimeInternal` / `GetBeatInternal`) and display helpers
 * (`GetDisplayedBeat` for SCROLLS, `GetDisplayedSpeedPercent` for SPEEDS).
 *
 * Supports BPM changes, stops, delays, warps, scrolls and speeds. Times are in seconds relative to the start
 * of the music file; beat 0 sits at `-offset` seconds exactly like StepMania.
 */
class TimingData(
  val offsetSeconds: Double,
  bpms: List<Pair<Double, Double>>,
  stops: List<Pair<Double, Double>> = emptyList(),
  delays: List<Pair<Double, Double>> = emptyList(),
  warps: List<Pair<Double, Double>> = emptyList(),
  scrolls: List<Pair<Double, Double>> = emptyList(),
  speeds: List<SpeedSegment> = emptyList()
) {
  /** SCROLLS: beat → ratio, always starting with a segment at beat 0 like StepMania. */
  private val scrollSegs: List<Pair<Double, Double>> = scrolls.sortedBy { it.first }
    .let { list -> if (list.isEmpty() || list.first().first > 0.0) listOf(0.0 to 1.0) + list else list }
  private val speedSegs: List<SpeedSegment> = speeds.sortedBy { it.beat }

  val hasScrollEffects: Boolean
    get() = scrollSegs.any { it.second != 1.0 } || speedSegs.any { it.ratio != 1.0 }

  /** GetDisplayedBeat: beats scaled by SCROLLS ratios (0 ratio = arrows stand still). */
  fun displayedBeat(beat: Double): Double {
    var out = 0.0
    var i = 0
    while (i < scrollSegs.size - 1) {
      if (scrollSegs[i + 1].first > beat) break
      out += (scrollSegs[i + 1].first - scrollSegs[i].first) * scrollSegs[i].second
      i++
    }
    out += (beat - scrollSegs[i].first) * scrollSegs[i].second
    return out
  }

  private fun delayAtBeat(beat: Double): Double {
    val row = beatToRow(beat)
    return delaySegs.firstOrNull { it.row == row }?.value ?: 0.0
  }

  /** GetDisplayedSpeedPercent: SPEEDS multiplier, tweened over each segment's delay. */
  fun displayedSpeedPercent(songBeat: Double, musicSeconds: Double): Double {
    if (speedSegs.isEmpty()) return 1.0
    var index = 0
    for (i in speedSegs.indices) {
      if (speedSegs[i].beat <= songBeat) index = i else break
    }
    val seg = speedSegs[index]
    val startTime = timeAtBeat(seg.beat) - delayAtBeat(seg.beat)
    val endTime = if (seg.delayInSeconds) {
      startTime + seg.delay
    } else {
      timeAtBeat(seg.beat + seg.delay) - delayAtBeat(seg.beat + seg.delay)
    }
    val first = speedSegs[0]
    return if (index == 0 && first.delay > 0.0 && musicSeconds < startTime) {
      1.0
    } else if (endTime >= musicSeconds && (index > 0 || first.delay > 0.0)) {
      val prior = if (index == 0) 1.0 else speedSegs[index - 1].ratio
      val duration = endTime - startTime
      val used = if (duration == 0.0) 1.0 else (musicSeconds - startTime) / duration
      prior + used * -(prior - seg.ratio)
    } else {
      seg.ratio
    }
  }

  private class Segment(val row: Int, val value: Double)

  private val bpmSegs = bpms.map { Segment(beatToRow(it.first), it.second) }
    .filter { it.value > 0.0 }
    .sortedBy { it.row }
    .ifEmpty { listOf(Segment(0, 120.0)) }
  private val stopSegs = stops.filter { it.second > 0.0 }.map { Segment(beatToRow(it.first), it.second) }.sortedBy { it.row }
  private val delaySegs = delays.filter { it.second > 0.0 }.map { Segment(beatToRow(it.first), it.second) }.sortedBy { it.row }
  private val warpSegs = warps.filter { it.second > 0.0 }.map { Segment(beatToRow(it.first), it.second) }.sortedBy { it.row }

  val bpmValues: List<Double> get() = bpmSegs.map { it.value }
  val maxBpm: Double get() = bpmSegs.maxOf { it.value }
  val hasWarps: Boolean get() = warpSegs.isNotEmpty()

  private class Start {
    var bpm = 0
    var warp = 0
    var stop = 0
    var delay = 0
    var lastRow = 0
    var lastTime = 0.0
    var warpDestination = 0.0
    var isWarping = false
  }

  private enum class Event { WARP_DESTINATION, BPM_CHANGE, STOP, DELAY, STOP_DELAY, MARKER, WARP, NOT_FOUND }

  private fun bpmAtRow(row: Int): Double {
    var bpm = bpmSegs.first().value
    for (seg in bpmSegs) {
      if (seg.row > row) break
      bpm = seg.value
    }
    return bpm
  }

  private fun findEvent(start: Start, markerRow: Int?): Pair<Int, Event> {
    var eventRow = Int.MAX_VALUE
    var type = Event.NOT_FOUND
    if (start.isWarping && beatToRow(start.warpDestination) < eventRow) {
      eventRow = beatToRow(start.warpDestination); type = Event.WARP_DESTINATION
    }
    if (start.bpm < bpmSegs.size && bpmSegs[start.bpm].row < eventRow) {
      eventRow = bpmSegs[start.bpm].row; type = Event.BPM_CHANGE
    }
    if (start.delay < delaySegs.size && delaySegs[start.delay].row < eventRow) {
      eventRow = delaySegs[start.delay].row; type = Event.DELAY
    }
    if (markerRow != null && markerRow < eventRow) {
      eventRow = markerRow; type = Event.MARKER
    }
    if (start.stop < stopSegs.size && stopSegs[start.stop].row < eventRow) {
      val tmp = eventRow
      eventRow = stopSegs[start.stop].row
      type = if (tmp == eventRow) Event.STOP_DELAY else Event.STOP
    }
    if (start.warp < warpSegs.size && warpSegs[start.warp].row < eventRow) {
      eventRow = warpSegs[start.warp].row; type = Event.WARP
    }
    return eventRow to type
  }

  /** Seconds from music start at which [beat] is reached (GetElapsedTimeFromBeatNoOffset). */
  fun timeAtBeat(beat: Double): Double {
    val start = Start().apply { lastTime = -offsetSeconds }
    val markerRow = beatToRow(beat)
    var bps = bpmAtRow(0) / 60.0
    while (true) {
      val (eventRow, type) = findEvent(start, markerRow)
      if (type == Event.NOT_FOUND) break
      val toNext = if (start.isWarping) 0.0 else rowToBeat(eventRow - start.lastRow) / bps
      start.lastTime += toNext
      when (type) {
        Event.WARP_DESTINATION -> start.isWarping = false
        Event.BPM_CHANGE -> {
          bps = bpmSegs[start.bpm].value / 60.0
          start.bpm++
        }
        Event.STOP, Event.STOP_DELAY -> {
          start.lastTime += stopSegs[start.stop].value
          start.stop++
        }
        Event.DELAY -> {
          start.lastTime += delaySegs[start.delay].value
          start.delay++
        }
        Event.MARKER -> return start.lastTime
        Event.WARP -> {
          start.isWarping = true
          val ws = warpSegs[start.warp]
          val sum = ws.value + rowToBeat(ws.row)
          if (sum > start.warpDestination) start.warpDestination = sum
          start.warp++
        }
        Event.NOT_FOUND -> {}
      }
      start.lastRow = eventRow
    }
    return start.lastTime
  }

  /** Beat shown at [seconds] from music start (GetBeatAndBPSFromElapsedTimeNoOffset). */
  fun beatAtTime(seconds: Double): Double {
    val start = Start().apply { lastTime = -offsetSeconds }
    var bps = bpmAtRow(0) / 60.0
    while (true) {
      val (eventRow, type) = findEvent(start, null)
      if (type == Event.NOT_FOUND) break
      val toNext = if (start.isWarping) 0.0 else rowToBeat(eventRow - start.lastRow) / bps
      val nextTime = start.lastTime + toNext
      if (seconds < nextTime) break
      start.lastTime = nextTime
      when (type) {
        Event.WARP_DESTINATION -> start.isWarping = false
        Event.BPM_CHANGE -> {
          bps = bpmSegs[start.bpm].value / 60.0
          start.bpm++
        }
        Event.DELAY, Event.STOP_DELAY -> {
          val pause = delaySegs[start.delay].value
          if (seconds < start.lastTime + pause) return rowToBeat(delaySegs[start.delay].row)
          start.lastTime += pause
          start.delay++
          if (type == Event.STOP_DELAY) {
            val stop = stopSegs[start.stop].value
            if (seconds < start.lastTime + stop) return rowToBeat(stopSegs[start.stop].row)
            start.lastTime += stop
            start.stop++
          }
        }
        Event.STOP -> {
          val pause = stopSegs[start.stop].value
          if (seconds < start.lastTime + pause) return rowToBeat(stopSegs[start.stop].row)
          start.lastTime += pause
          start.stop++
        }
        Event.WARP -> {
          start.isWarping = true
          val ws = warpSegs[start.warp]
          val sum = ws.value + rowToBeat(ws.row)
          if (sum > start.warpDestination) start.warpDestination = sum
          start.warp++
        }
        Event.MARKER, Event.NOT_FOUND -> {}
      }
      start.lastRow = eventRow
    }
    return rowToBeat(start.lastRow) + (seconds - start.lastTime) * bps
  }

  /** True when [beat] lies inside a warp (notes there are skipped, like StepMania fakes). */
  fun isWarpedBeat(beat: Double): Boolean {
    val row = beatToRow(beat)
    return warpSegs.any { row >= it.row && row < beatToRow(rowToBeat(it.row) + it.value) }
  }

  companion object {
    const val ROWS_PER_BEAT = 48

    fun beatToRow(beat: Double): Int = (beat * ROWS_PER_BEAT).roundToInt()
    fun rowToBeat(row: Int): Double = row.toDouble() / ROWS_PER_BEAT

    /** Parses SPEEDS "beat=ratio=delay=unit" lists. */
    fun parseSpeeds(raw: String?): List<SpeedSegment> {
      if (raw.isNullOrBlank()) return emptyList()
      return raw.split(',').mapNotNull { entry ->
        val p = entry.trim().split('=').map { it.trim() }
        if (p.size < 2) return@mapNotNull null
        SpeedSegment(
          beat = p[0].toDoubleOrNull() ?: return@mapNotNull null,
          ratio = p[1].toDoubleOrNull() ?: return@mapNotNull null,
          delay = p.getOrNull(2)?.toDoubleOrNull() ?: 0.0,
          delayInSeconds = p.getOrNull(3)?.toDoubleOrNull()?.toInt() == 1
        )
      }
    }

    /** Parses "beat=value,beat=value" lists (BPMS, STOPS, DELAYS, WARPS, SCROLLS). */
    fun parsePairs(raw: String?): List<Pair<Double, Double>> {
      if (raw.isNullOrBlank()) return emptyList()
      return raw.split(',').mapNotNull { entry ->
        val parts = entry.trim().split('=')
        if (parts.size < 2) return@mapNotNull null
        val beat = parts[0].trim().toDoubleOrNull() ?: return@mapNotNull null
        val value = parts[1].trim().toDoubleOrNull() ?: return@mapNotNull null
        beat to value
      }
    }
  }
}
