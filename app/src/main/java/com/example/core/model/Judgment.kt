package com.example.core.model

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * StepMania `dance` mode tap judgments (`TapNoteScore_W1` .. `TapNoteScore_Miss`)
 * with default timing windows (`TimingWindowScale = 1.0`, `TimingWindowAdd = 0`),
 * dance point weights (`PercentScoreWeight*`), grade weights (`GradeWeight*`),
 * combo continuation rules (`ComboContinue` / `ComboMaintain` = W3), and theme colors.
 */
enum class Judgment(
  val displayName: String,
  val windowMs: Double,
  val dancePoints: Int,
  val gradePoints: Int,
  val continuesCombo: Boolean,
  val color: Color
) {
  W1(
    displayName = "Flawless",
    windowMs = 22.5,
    dancePoints = 3,
    gradePoints = 2,
    continuesCombo = true,
    color = Color(0xFFA0DBF1) // StepMania JudgmentLine_W1
  ),
  W2(
    displayName = "Perfect",
    windowMs = 45.0,
    dancePoints = 2,
    gradePoints = 2,
    continuesCombo = true,
    color = Color(0xFFF1E4A2) // StepMania JudgmentLine_W2
  ),
  W3(
    displayName = "Great",
    windowMs = 90.0,
    dancePoints = 1,
    gradePoints = 1,
    continuesCombo = true,
    color = Color(0xFFABE39B) // StepMania JudgmentLine_W3
  ),
  W4(
    displayName = "Good",
    windowMs = 135.0,
    dancePoints = 0,
    gradePoints = 0,
    continuesCombo = false,
    color = Color(0xFF86ACD6) // StepMania JudgmentLine_W4
  ),
  W5(
    displayName = "Bad",
    windowMs = 180.0,
    dancePoints = 0,
    gradePoints = -4,
    continuesCombo = false,
    color = Color(0xFF958CD6) // StepMania JudgmentLine_W5
  ),
  MISS(
    displayName = "Miss",
    windowMs = 180.0,
    dancePoints = 0,
    gradePoints = -8,
    continuesCombo = false,
    color = Color(0xFFF97E7E) // StepMania JudgmentLine_Miss
  );

  companion object {
    const val MISS_THRESHOLD_MS = 180L
    const val HOLD_HELD_DANCE_POINTS = 3
    const val HOLD_HELD_GRADE_POINTS = 6

    private val HIT_WINDOWS = listOf(W1, W2, W3, W4, W5)

    /** StepMania `TimingWindowScale`: multiplies every hit window (and the miss threshold). */
    const val MIN_TIMING_SCALE = 0.5f
    const val MAX_TIMING_SCALE = 2.0f
    const val TIMING_SCALE_STEP = 0.25f

    @Volatile
    var timingWindowScale: Float = 1.0f
      set(value) { field = value.coerceIn(MIN_TIMING_SCALE, MAX_TIMING_SCALE) }

    /** Current late-miss threshold in ms, scaled by [timingWindowScale]. */
    val missThresholdMs: Long get() = missThresholdMsFor(timingWindowScale)

    fun missThresholdMsFor(scale: Float): Long = (MISS_THRESHOLD_MS * scale).toLong()

    /**
     * Evaluates a signed timing error (inputTimeMs - scheduledHitTimeMs) against W1→W5 windows.
     * The first window containing |signedErrorMs| wins (StepMania `Player::Step`);
     * returns null if outside ±180 ms.
     */
    fun evaluateHit(signedErrorMs: Double): Judgment? {
      val absError = abs(signedErrorMs)
      val scale = timingWindowScale
      return HIT_WINDOWS.firstOrNull { absError <= it.windowMs * scale }
    }

    fun evaluateHit(signedErrorMs: Long): Judgment? = evaluateHit(signedErrorMs.toDouble())
  }
}
