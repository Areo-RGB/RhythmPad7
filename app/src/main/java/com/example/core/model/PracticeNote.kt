package com.example.core.model

/**
 * Judgment lifecycle state of an individual note on the playfield.
 */
enum class NoteState {
  PENDING,
  HIT,
  MISSED
}

/**
 * Represents a scheduled arrow note in a chart or practice pattern.
 * Designed to be compatible with future StepMania .sm / .ssc chart loaders.
 */
data class PracticeNote(
  val id: Int,
  val lane: GameLane,
  val beat: Double,
  val hitTimeMs: Long,
  val state: NoteState = NoteState.PENDING,
  val judgment: Judgment? = null,
  val timingErrorMs: Long? = null,
  val judgedAtMs: Long? = null
) {
  val isJudged: Boolean
    get() = state != NoteState.PENDING

  val isPending: Boolean
    get() = state == NoteState.PENDING

  /**
   * Computes the vertical Y coordinate of this note at [currentGameTimeMs].
   *
   * Formula:
   *   noteY = receptorY + (hitTimeMs - currentGameTimeMs) * pixelsPerMillisecond
   *
   * - Before [hitTimeMs], `noteY > receptorY` (below the receptors).
   * - As [currentGameTimeMs] increases, `noteY` decreases (moves upward toward `receptorY`).
   * - At `currentGameTimeMs == hitTimeMs`, `noteY == receptorY` (exact alignment).
   */
  fun calculateY(
    currentGameTimeMs: Long,
    receptorY: Float,
    playfieldHeight: Float,
    travelDurationMs: Long = DEFAULT_TRAVEL_DURATION_MS
  ): Float {
    return calculateNoteY(
      noteHitTimeMs = hitTimeMs,
      currentGameTimeMs = currentGameTimeMs,
      receptorY = receptorY,
      playfieldHeight = playfieldHeight,
      travelDurationMs = travelDurationMs
    )
  }

  companion object {
    const val DEFAULT_TRAVEL_DURATION_MS = 2800L

    fun calculateNoteY(
      noteHitTimeMs: Long,
      currentGameTimeMs: Long,
      receptorY: Float,
      playfieldHeight: Float,
      travelDurationMs: Long = DEFAULT_TRAVEL_DURATION_MS
    ): Float {
      val travelDistancePx = (playfieldHeight - receptorY).coerceAtLeast(1f)
      val pixelsPerMillisecond = travelDistancePx / travelDurationMs.toFloat()
      return receptorY + (noteHitTimeMs - currentGameTimeMs) * pixelsPerMillisecond
    }
  }
}
