package com.example.core.gameplay

import com.example.core.model.GameLane
import com.example.core.model.PracticeNote
import kotlin.math.roundToLong

/**
 * Pure Kotlin generator that creates a deterministic, repeatable 4-lane beginner
 * practice chart using musical beat positions.
 *
 * At 120 BPM:
 * - 1 beat = 500 ms
 * - Beat 0 occurs at [beatZeroTimeMs] (default 3,000 ms after round start, right after countdown)
 * - Beat 4 occurs at [beatZeroTimeMs] + 2,000 ms
 */
object PracticePatternGenerator {

  const val DEFAULT_BPM = 120
  const val DEFAULT_BEAT_ZERO_MS = 3000L

  fun beatDurationMs(bpm: Int = DEFAULT_BPM): Double {
    require(bpm > 0) { "BPM must be positive" }
    return 60_000.0 / bpm.toDouble()
  }

  fun beatToTimeMs(
    beat: Double,
    beatZeroTimeMs: Long = DEFAULT_BEAT_ZERO_MS,
    bpm: Int = DEFAULT_BPM
  ): Long {
    val msPerBeat = beatDurationMs(bpm)
    return beatZeroTimeMs + (beat * msPerBeat).roundToLong()
  }

  /**
   * Generates a deterministic 68-beat practice pattern (77 total notes, ~34s active chart + 3s lead-in)
   * mixing natural single-step footwork with occasional 2-panel jumps.
   */
  fun generatePracticeNotes(
    beatZeroTimeMs: Long = DEFAULT_BEAT_ZERO_MS,
    bpm: Int = DEFAULT_BPM
  ): List<PracticeNote> {
    val beatRows: List<Pair<Double, List<GameLane>>> = listOf(
      // Phrase 1 (Beats 0..7): Basic walk around the pad + Left/Right jump
      0.0 to listOf(GameLane.LEFT),
      1.0 to listOf(GameLane.DOWN),
      2.0 to listOf(GameLane.UP),
      3.0 to listOf(GameLane.RIGHT),
      4.0 to listOf(GameLane.LEFT),
      5.0 to listOf(GameLane.RIGHT),
      6.0 to listOf(GameLane.DOWN),
      7.0 to listOf(GameLane.LEFT, GameLane.RIGHT),

      // Phrase 2 (Beats 8..15): Alternating steps + Down/Up jump
      8.0 to listOf(GameLane.UP),
      9.0 to listOf(GameLane.DOWN),
      10.0 to listOf(GameLane.LEFT),
      11.0 to listOf(GameLane.RIGHT),
      12.0 to listOf(GameLane.UP),
      13.0 to listOf(GameLane.LEFT),
      14.0 to listOf(GameLane.RIGHT),
      15.0 to listOf(GameLane.DOWN, GameLane.UP),

      // Phrase 3 (Beats 16..23): Diamond step sequence + Left/Up jump
      16.0 to listOf(GameLane.LEFT),
      17.0 to listOf(GameLane.UP),
      18.0 to listOf(GameLane.DOWN),
      19.0 to listOf(GameLane.RIGHT),
      20.0 to listOf(GameLane.LEFT),
      21.0 to listOf(GameLane.DOWN),
      22.0 to listOf(GameLane.UP),
      23.0 to listOf(GameLane.LEFT, GameLane.UP),

      // Phrase 4 (Beats 24..31): Reverse diamond + Down/Right jump
      24.0 to listOf(GameLane.RIGHT),
      25.0 to listOf(GameLane.DOWN),
      26.0 to listOf(GameLane.UP),
      27.0 to listOf(GameLane.LEFT),
      28.0 to listOf(GameLane.RIGHT),
      29.0 to listOf(GameLane.UP),
      30.0 to listOf(GameLane.DOWN),
      31.0 to listOf(GameLane.DOWN, GameLane.RIGHT),

      // Phrase 5 (Beats 32..39): Lateral & vertical crossovers + Left/Right jump
      32.0 to listOf(GameLane.LEFT),
      33.0 to listOf(GameLane.RIGHT),
      34.0 to listOf(GameLane.UP),
      35.0 to listOf(GameLane.DOWN),
      36.0 to listOf(GameLane.LEFT),
      37.0 to listOf(GameLane.RIGHT),
      38.0 to listOf(GameLane.UP),
      39.0 to listOf(GameLane.LEFT, GameLane.RIGHT),

      // Phrase 6 (Beats 40..47): Inside-out steps + Down/Up jump
      40.0 to listOf(GameLane.DOWN),
      41.0 to listOf(GameLane.LEFT),
      42.0 to listOf(GameLane.RIGHT),
      43.0 to listOf(GameLane.UP),
      44.0 to listOf(GameLane.DOWN),
      45.0 to listOf(GameLane.RIGHT),
      46.0 to listOf(GameLane.LEFT),
      47.0 to listOf(GameLane.DOWN, GameLane.UP),

      // Phrase 7 (Beats 48..55): Counter-clockwise sweep + Left/Down jump
      48.0 to listOf(GameLane.UP),
      49.0 to listOf(GameLane.RIGHT),
      50.0 to listOf(GameLane.DOWN),
      51.0 to listOf(GameLane.LEFT),
      52.0 to listOf(GameLane.UP),
      53.0 to listOf(GameLane.LEFT),
      54.0 to listOf(GameLane.DOWN),
      55.0 to listOf(GameLane.LEFT, GameLane.DOWN),

      // Phrase 8 (Beats 56..63): Clockwise sweep + Up/Right jump
      56.0 to listOf(GameLane.RIGHT),
      57.0 to listOf(GameLane.UP),
      58.0 to listOf(GameLane.LEFT),
      59.0 to listOf(GameLane.DOWN),
      60.0 to listOf(GameLane.RIGHT),
      61.0 to listOf(GameLane.DOWN),
      62.0 to listOf(GameLane.UP),
      63.0 to listOf(GameLane.UP, GameLane.RIGHT),

      // Finale (Beats 64..67): Closing run + Final jump
      64.0 to listOf(GameLane.LEFT),
      65.0 to listOf(GameLane.DOWN),
      66.0 to listOf(GameLane.UP),
      67.0 to listOf(GameLane.LEFT, GameLane.RIGHT)
    )

    val notes = mutableListOf<PracticeNote>()
    var nextId = 1

    for ((beat, lanes) in beatRows) {
      val hitTimeMs = beatToTimeMs(beat = beat, beatZeroTimeMs = beatZeroTimeMs, bpm = bpm)
      for (lane in lanes) {
        notes.add(
          PracticeNote(
            id = nextId++,
            lane = lane,
            beat = beat,
            hitTimeMs = hitTimeMs
          )
        )
      }
    }

    return notes
  }
}
