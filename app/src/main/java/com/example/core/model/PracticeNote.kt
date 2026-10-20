package com.example.core.model

/**
 * Judgment lifecycle state of an individual note on the playfield.
 */
enum class NoteState {
  PENDING,
  HIT,
  MISSED
}

/** StepMania HoldNoteScore for a hold/roll: still running, OK (Held) or NG (LetGo / missed head). */
enum class HoldResult {
  ACTIVE,
  HELD,
  LET_GO
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
  val judgedAtMs: Long? = null,
  /** Tail time for holds/rolls (null = tap). */
  val holdEndMs: Long? = null,
  val holdEndBeat: Double? = null,
  val isRoll: Boolean = false,
  /** Set once the head is judged; null for taps and unjudged heads. */
  val holdResult: HoldResult? = null,
  /** StepMania HoldResult.fLife: 1 = safe, 0 = let go. */
  val holdLife: Float = 1f,
  /** Gameplay time the hold was finished as OK/NG (drives the OK/NG flash). */
  val holdJudgedAtMs: Long? = null
) {
  val isHold: Boolean
    get() = holdEndMs != null

  /** Last moment this note matters on the field (tail for holds). */
  val endTimeMs: Long
    get() = holdEndMs ?: hitTimeMs

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
