package com.example.core.gameplay

import com.example.core.model.GameLane
import com.example.core.model.PracticeNote
import org.json.JSONObject
import kotlin.random.Random

/**
 * One 4-beat measure cut from an ITGmania chart: rows of (beat offset, lane bitmask, hold beats).
 * [techMask] bits index [EndlessGenerator.TECH] (ITGmania StepParity/TechCounts, see tools/stepparity.py).
 */
data class EndlessPhrase(val techMask: Int, val rows: List<Row>) {
  data class Row(val beatOffset: Double, val laneMask: Int, val holdBeats: Double)
}

/**
 * Endless mode: streams random measures (timed by the caller's beat grid, a song's or [BPM]) from `assets/endless/phrases.json` (built by
 * `tools/build_endless_phrases.py` from `patterns/charts`) at a constant [BPM].
 * Levels follow the source chart difficulty: 0 = Beginner .. 4 = Challenge.
 */
class EndlessGenerator(private val levels: List<List<EndlessPhrase>>, private val random: Random = Random.Default) {

  val levelCount: Int get() = levels.size

  /** Phrases at [level] per tech type (drives which tech toggles are worth showing). */
  fun techCounts(level: Int): IntArray {
    val counts = IntArray(TECH.size)
    levels.getOrNull(level)?.forEach { p -> TECH.indices.forEach { if (p.techMask and (1 shl it) != 0) counts[it]++ } }
    return counts
  }

  /**
   * Notes for [measure] (absolute index) at [level], ids starting at [firstId]. Phrases containing
   * any tech in [blockedTech] are skipped (falls back to tech-free phrases if nothing is left).
   */
  fun measureNotes(measure: Int, level: Int, firstId: Int, blockedTech: Int = 0, timeAtBeat: (Double) -> Long): List<PracticeNote> {
    val all = levels[level.coerceIn(0, levels.lastIndex)]
    val pool = all.filter { it.techMask and blockedTech == 0 }.ifEmpty { all.filter { it.techMask == 0 } }
    if (pool.isEmpty()) return emptyList()
    val phrase = pool[random.nextInt(pool.size)]
    var id = firstId
    return phrase.rows.flatMap { row ->
      val beat = measure * BEATS_PER_MEASURE + row.beatOffset
      GameLane.entries.filter { row.laneMask and (1 shl it.ordinal) != 0 }.map { lane ->
        val endBeat = if (row.holdBeats > 0) beat + row.holdBeats else null
        PracticeNote(
          id = id++,
          lane = lane,
          beat = beat,
          hitTimeMs = timeAtBeat(beat),
          holdEndMs = endBeat?.let(timeAtBeat),
          holdEndBeat = endBeat
        )
      }
    }
  }

  companion object {
    const val BPM = 130
    const val BEATS_PER_MEASURE = 4
    val LEVEL_NAMES = listOf("Beginner", "Easy", "Medium", "Hard", "Challenge")

    /** ITGmania TechCounts categories tagged on phrases, in techMask bit order. */
    val TECH = listOf("Crossovers", "Footswitches", "Sideswitches", "Jacks", "Brackets", "Doublesteps")

    fun parse(json: String, random: Random = Random.Default): EndlessGenerator {
      val levels = JSONObject(json).getJSONArray("levels")
      return EndlessGenerator(
        List(levels.length()) { l ->
          val phrases = levels.getJSONArray(l)
          List(phrases.length()) { p ->
            val phrase = phrases.getJSONArray(p)
            val rows = phrase.getJSONArray(1)
            EndlessPhrase(phrase.getInt(0), List(rows.length()) { r ->
              val row = rows.getJSONArray(r)
              EndlessPhrase.Row(row.getDouble(0), row.getInt(1), row.getDouble(2))
            })
          }
        },
        random
      )
    }
  }
}
