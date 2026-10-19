package com.example.core.model

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * Rhythm timing judgments awarded for approaching notes.
 */
enum class Judgment(
  val displayName: String,
  val points: Int,
  val windowMs: Long,
  val color: Color
) {
  EXCELLENT(
    displayName = "EXCELLENT",
    points = 100,
    windowMs = 70L,
    color = Color(0xFFA0DBF1) // StepMania JudgmentLine_W1
  ),
  GOOD(
    displayName = "GOOD",
    points = 60,
    windowMs = 150L,
    color = Color(0xFFABE39B) // StepMania JudgmentLine_W3
  ),
  MISS(
    displayName = "MISS",
    points = 0,
    windowMs = 150L,
    color = Color(0xFFF97E7E) // StepMania JudgmentLine_Miss
  );

  companion object {
    const val EXCELLENT_WINDOW_MS = 70L
    const val GOOD_WINDOW_MS = 150L
    const val MISS_THRESHOLD_MS = 150L

    /**
     * Evaluates a signed timing error (inputTimeMs - scheduledHitTimeMs).
     * Returns EXCELLENT within ±70ms, GOOD within ±150ms, or null if outside hit windows.
     */
    fun evaluateHit(signedErrorMs: Long): Judgment? {
      val absError = abs(signedErrorMs)
      return when {
        absError <= EXCELLENT_WINDOW_MS -> EXCELLENT
        absError <= GOOD_WINDOW_MS -> GOOD
        else -> null
      }
    }
  }
}
