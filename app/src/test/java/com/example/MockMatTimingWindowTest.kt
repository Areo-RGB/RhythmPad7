package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.input.ControllerManager
import com.example.core.input.SharedPreferencesMappingStore
import com.example.core.model.GameInputEvent
import com.example.core.model.InputSource
import com.example.core.model.Judgment
import com.example.core.model.NoteState
import com.example.core.state.RhythmGameViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

/**
 * Timing-window setting (StepMania TimingWindowScale) checked end to end with a simulated mat:
 * every note is stepped on with the same timing error, then judgments and score are compared.
 */
@RunWith(RobolectricTestRunner::class)
class MockMatTimingWindowTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()

  @After fun resetScale() { Judgment.timingWindowScale = 1.0f }

  private data class Outcome(val counts: Map<Judgment, Int>, val percentDp: Double, val combo: Int)

  /** Plays the practice round with every tap [errorMs] off (positive = late) at timing scale [scale]. */
  private fun play(scale: Float, errorMs: Int): Outcome {
    Judgment.timingWindowScale = scale
    val vm = RhythmGameViewModel(
      ControllerManager(context, SharedPreferencesMappingStore(context)),
      prefs = context.getSharedPreferences("mock_mat_tw", Context.MODE_PRIVATE).also { it.edit().clear().commit() }
    )
    vm.setAutoSyncEnabled(false) // keep the error fixed
    vm.startNewRound()
    val notes = vm.roundState.value.notes.sortedBy { it.hitTimeMs }
    for (note in notes) {
      val at = note.hitTimeMs + errorMs
      if (at > vm.roundState.value.currentGameTimeMs) vm.updateRoundTimeMs(at)
      vm.processInputEvent(GameInputEvent(note.lane, true, at, InputSource.CONTROLLER, "MOCK"))
      vm.processInputEvent(GameInputEvent(note.lane, false, at + 40, InputSource.CONTROLLER, "MOCK"))
    }
    vm.updateRoundTimeMs(notes.last().hitTimeMs + 2_000)
    val r = vm.roundState.value
    val counts = Judgment.entries.associateWith { r.judgmentCount(it) }
    val pct = if (r.possibleDancePoints == 0) 0.0 else 100.0 * r.score / r.possibleDancePoints
    return Outcome(counts, pct, r.maxCombo)
  }

  private fun Outcome.dominant(): Judgment = counts.maxBy { it.value }.key

  @Test
  fun judgmentsFollowTheScaledWindows() {
    // |error| -> expected judgment per scale (windows at x1: 22.5 / 45 / 90 / 135 / 180 ms)
    val cases = listOf(
      Triple(1.0f, 30, Judgment.W2),   // 30 <= 45
      Triple(2.0f, 30, Judgment.W1),   // 30 <= 45 (22.5*2)
      Triple(0.5f, 30, Judgment.W3),   // 22.5 < 30 <= 45 (90*0.5)
      Triple(1.0f, 100, Judgment.W4),  // 90 < 100 <= 135
      Triple(1.5f, 100, Judgment.W3),  // 100 <= 135 (90*1.5)
      Triple(1.0f, -60, Judgment.W3),  // early steps are judged the same way
      Triple(1.0f, 200, Judgment.MISS),// outside 180 ms
      Triple(1.25f, 200, Judgment.W5)  // inside 225 ms
    )
    val report = StringBuilder("\n scale | error | result      | score   | max combo\n")
    for ((scale, err, expected) in cases) {
      val o = play(scale, err)
      report.append(String.format(Locale.US, " x%.2f | %+4d  | %-11s | %6.2f%% | %d%n",
        scale, err, o.dominant().displayName, o.percentDp, o.combo))
      assertEquals("x$scale, ${err}ms", expected, o.dominant())
    }
    println(report)
  }

  @Test
  fun widerWindowNeverLowersTheScore() {
    val scores = listOf(0.5f, 1.0f, 1.5f, 2.0f).map { play(it, 40).percentDp }
    println("40 ms late at x0.5/x1/x1.5/x2 -> " + scores.joinToString { String.format(Locale.US, "%.2f%%", it) })
    assertTrue(scores.zipWithNext().all { (a, b) -> b >= a })
    assertTrue(scores.last() > scores.first())
  }
}
