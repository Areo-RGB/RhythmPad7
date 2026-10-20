package com.example.core.chart

import kotlin.math.roundToInt

/**
 * Kotlin port of StepMania's `TimingData` beat ↔ time conversion (src/TimingData.cpp,
 * `GetElapsedTimeInternal` / `GetBeatInternal`).
 *
 * Supports BPM changes, stops, delays and warps. Times are in seconds relative to the start
 * of the music file; beat 0 sits at `-offset` seconds exactly like StepMania.
 */
class TimingData(
  val offsetSeconds: Double,
  bpms: List<Pair<Double, Double>>,
  stops: List<Pair<Double, Double>> = emptyList(),
  delays: List<Pair<Double, Double>> = emptyList(),
  warps: List<Pair<Double, Double>> = emptyList()
) {
  private class Segment(val row: Int, val value: Double)

  private val bpmSegs = bpms.map { Segment(beatToRow(it.first), it.second) }
    .filter { it.value > 0.0 }
    .sortedBy { it.row }
    .ifEmpty { listOf(Segment(0, 120.0)) }
  private val stopSegs = stops.filter { it.second > 0.0 }.map { Segment(beatToRow(it.first), it.second) }.sortedBy { it.row }
  private val delaySegs = delays.filter { it.second > 0.0 }.map { Segment(beatToRow(it.first), it.second) }.sortedBy { it.row }
  private val warpSegs = warps.filter { it.second > 0.0 }.map { Segment(beatToRow(it.first), it.second) }.sortedBy { it.row }

  val bpmValues: List<Double> get() = bpmSegs.map { it.value }
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

    /** Parses "beat=value,beat=value" lists (BPMS, STOPS, DELAYS, WARPS). */
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
