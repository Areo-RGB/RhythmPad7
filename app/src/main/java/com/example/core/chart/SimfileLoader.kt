package com.example.core.chart

import com.example.core.model.GameLane
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Loads `dance-single` charts from StepMania `.ssc` (NotesLoaderSSC.cpp) and `.sm`
 * (NotesLoaderSM.cpp) text. Only what RhythmPad7 can play is kept: taps, holds (`2`…`3`) and
 * rolls (`4`…`3`). Mines, fakes, lifts and notes inside warps are skipped.
 */
object SimfileLoader {

  /**
   * @param fileLookup resolves a filename referenced by the simfile to the name actually present
   *   in [folder] (assets may be re-encoded, e.g. `bg.png` → `bg.jpg`), or null when missing.
   */
  fun load(
    text: String,
    isSsc: Boolean,
    folder: String,
    fileLookup: (String) -> String? = { it }
  ): Song {
    val values = MsdFile.parse(text)
    val header = mutableMapOf<String, String>()
    val charts = mutableListOf<Chart>()

    var songTiming: TimingData? = null
    fun songTimingOrDefault(): TimingData {
      songTiming?.let { return it }
      return buildTiming(header).also { songTiming = it }
    }

    if (isSsc) {
      var chartTags: MutableMap<String, String>? = null
      for (value in values) {
        val tag = value.firstOrNull()?.uppercase(Locale.US) ?: continue
        val param = value.getOrNull(1) ?: ""
        when {
          tag == "NOTEDATA" -> chartTags = mutableMapOf()
          chartTags == null -> header[tag] = param
          tag == "NOTES" || tag == "NOTES2" -> {
            buildSscChart(chartTags, param, header, ::songTimingOrDefault)?.let(charts::add)
            chartTags = null
          }
          else -> chartTags[tag] = param
        }
      }
    } else {
      for (value in values) {
        val tag = value.firstOrNull()?.uppercase(Locale.US) ?: continue
        if (tag == "NOTES" || tag == "NOTES2") {
          // #NOTES:type:description:difficulty:meter:radar:data;
          if (value.size < 7) continue
          if (value[1].trim().lowercase(Locale.US) != "dance-single") continue
          val timing = songTimingOrDefault()
          charts += Chart(
            difficulty = Difficulty.parse(value[3]),
            meter = value[4].trim().toIntOrNull() ?: 1,
            description = value[2].trim(),
            timing = timing,
            steps = parseNoteData(value[6], timing)
          )
        } else {
          header[tag] = value.getOrNull(1) ?: ""
        }
      }
    }

    val timing = songTimingOrDefault()
    val bpms = timing.bpmValues
    val displayBpm = header["DISPLAYBPM"]?.trim()?.takeIf { it.isNotEmpty() && it != "*" }
      ?.replace(":", " – ")
      ?: if (bpms.min().roundToInt() == bpms.max().roundToInt()) {
        "${bpms.first().roundToInt()}"
      } else {
        "${bpms.min().roundToInt()} – ${bpms.max().roundToInt()}"
      }

    val readBpm = header["DISPLAYBPM"]?.trim()?.split(':')?.mapNotNull { it.trim().toDoubleOrNull() }
      ?.maxOrNull()?.takeIf { it > 0 } ?: timing.maxBpm

    return Song(
      folder = folder,
      title = header["TITLE"]?.trim().orEmpty().ifEmpty { folder.substringAfterLast('/') },
      subtitle = header["SUBTITLE"]?.trim().orEmpty(),
      artist = header["ARTIST"]?.trim().orEmpty(),
      musicFile = header["MUSIC"]?.trim()?.takeIf { it.isNotEmpty() }?.let(fileLookup),
      bannerFile = header["BANNER"]?.trim()?.takeIf { it.isNotEmpty() }?.let(fileLookup),
      backgroundFile = header["BACKGROUND"]?.trim()?.takeIf { it.isNotEmpty() }?.let(fileLookup),
      sampleStartSeconds = header["SAMPLESTART"]?.trim()?.toDoubleOrNull() ?: 0.0,
      sampleLengthSeconds = header["SAMPLELENGTH"]?.trim()?.toDoubleOrNull()?.takeIf { it > 0 } ?: 12.0,
      displayBpm = displayBpm,
      readBpm = readBpm,
      songTiming = timing,
      charts = assignChartIds(charts.sortedWith(compareBy({ it.difficulty.ordinal }, { it.meter })))
    )
  }

  internal fun assignChartIds(charts: List<Chart>): List<Chart> {
    val used = mutableSetOf<String>()
    val difficultyCounts = charts.groupBy { it.difficulty }
    return charts.map { chart ->
      val hasMultiple = (difficultyCounts[chart.difficulty]?.size ?: 0) > 1
      val base = when {
        !hasMultiple && chart.description.isBlank() -> chart.difficulty.name
        chart.description.isNotBlank() -> "${chart.difficulty.name}#${chart.description}"
        else -> "${chart.difficulty.name}#${chart.meter}"
      }
      var candidate = base
      var suffix = 2
      while (candidate in used) {
        candidate = "$base#$suffix"
        suffix++
      }
      used += candidate
      chart.copy(chartId = candidate)
    }
  }

  private fun buildTiming(tags: Map<String, String>): TimingData = TimingData(
    offsetSeconds = tags["OFFSET"]?.trim()?.toDoubleOrNull() ?: 0.0,
    bpms = TimingData.parsePairs(tags["BPMS"]),
    stops = TimingData.parsePairs(tags["STOPS"] ?: tags["FREEZES"]),
    delays = TimingData.parsePairs(tags["DELAYS"]),
    warps = TimingData.parsePairs(tags["WARPS"]),
    scrolls = TimingData.parsePairs(tags["SCROLLS"]),
    speeds = TimingData.parseSpeeds(tags["SPEEDS"])
  )

  private fun buildSscChart(
    tags: Map<String, String>,
    noteData: String,
    header: Map<String, String>,
    songTiming: () -> TimingData
  ): Chart? {
    if (tags["STEPSTYPE"]?.trim()?.lowercase(Locale.US) != "dance-single") return null
    // SSC "split timing": a chart that declares its own timing tags overrides the song's.
    val timingKeys = listOf("OFFSET", "BPMS", "STOPS", "DELAYS", "WARPS", "SCROLLS", "SPEEDS")
    val timing = if (timingKeys.any { tags.containsKey(it) }) {
      buildTiming(timingKeys.associateWith { key -> tags[key] ?: header[key].orEmpty() })
    } else {
      songTiming()
    }
    return Chart(
      difficulty = Difficulty.parse(tags["DIFFICULTY"].orEmpty()),
      meter = tags["METER"]?.trim()?.toIntOrNull() ?: 1,
      description = (tags["CHARTNAME"] ?: tags["DESCRIPTION"]).orEmpty().trim(),
      timing = timing,
      steps = parseNoteData(noteData, timing)
    )
  }

  /** Measures separated by ',', each with N rows of 4 columns; row r of measure m is beat 4m + 4r/N. */
  fun parseNoteData(data: String, timing: TimingData): List<ChartStep> {
    val steps = mutableListOf<ChartStep>()
    // Index into [steps] of the hold/roll head still waiting for its '3' tail, per column
    val openHolds = arrayOfNulls<Int>(4)
    val measures = data.substringBefore('&').split(',')
    measures.forEachIndexed { measureIndex, measure ->
      val rows = measure.lines()
        .map { it.substringBefore("//").trim() }
        .filter { it.isNotEmpty() }
      if (rows.isEmpty()) return@forEachIndexed
      rows.forEachIndexed { rowIndex, row ->
        val beat = measureIndex * 4.0 + rowIndex * 4.0 / rows.size
        val rounded = TimingData.rowToBeat(TimingData.beatToRow(beat))
        val warped = timing.isWarpedBeat(rounded)
        var column = 0
        var i = 0
        while (i < row.length && column < 4) {
          val ch = row[i]
          when (ch) {
            '{' -> { i = row.indexOf('}', i).let { if (it < 0) row.length else it + 1 }; continue }
            '[' -> { i = row.indexOf(']', i).let { if (it < 0) row.length else it + 1 }; continue }
          }
          when (ch) {
            '1', '2', '4' -> if (!warped) {
              steps += ChartStep(
                beat = rounded,
                timeSeconds = timing.timeAtBeat(rounded),
                lane = GameLane.entries[column],
                kind = when (ch) {
                  '2' -> StepKind.HOLD
                  '4' -> StepKind.ROLL
                  else -> StepKind.TAP
                }
              )
              if (ch != '1') openHolds[column] = steps.lastIndex
            }
            '3' -> openHolds[column]?.let { index ->
              steps[index] = steps[index].copy(endBeat = rounded, endTimeSeconds = timing.timeAtBeat(rounded))
              openHolds[column] = null
            }
          }
          column++
          i++
        }
      }
    }
    // A head without a tail plays as a plain tap
    return steps.map { if (it.kind != StepKind.TAP && it.endBeat == null) it.copy(kind = StepKind.TAP) else it }
  }
}
