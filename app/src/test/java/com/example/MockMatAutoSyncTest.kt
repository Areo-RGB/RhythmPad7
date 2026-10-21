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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.abs
import kotlin.random.Random

/**
 * End-to-end auto-sync check with a simulated dance mat: a "player" steps on every note of the
 * practice chart with a fixed lag plus random jitter, through the real ViewModel input path.
 */
@RunWith(RobolectricTestRunner::class)
class MockMatAutoSyncTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()

  private class PlayResult(val errors: List<Long>, val misses: Int)

  /** Plays the whole practice round; returns the judged timing errors (after offset). */
  private fun RhythmGameViewModel.playRound(lagMs: Int, jitterMs: Int, seed: Int): PlayResult {
    startNewRound()
    val rnd = Random(seed)
    val taps = roundState.value.notes.sortedBy { it.hitTimeMs }
    for (note in taps) {
      val pressAt = note.hitTimeMs + lagMs + rnd.nextInt(-jitterMs, jitterMs + 1)
      if (pressAt > roundState.value.currentGameTimeMs) updateRoundTimeMs(pressAt)
      processInputEvent(GameInputEvent(note.lane, true, pressAt, InputSource.CONTROLLER, "MOCK"))
      processInputEvent(GameInputEvent(note.lane, false, pressAt + 40, InputSource.CONTROLLER, "MOCK"))
    }
    updateRoundTimeMs(taps.last().hitTimeMs + 2_000)
    val notes = roundState.value.notes
    return PlayResult(
      errors = notes.filter { it.state == NoteState.HIT }.map { it.timingErrorMs ?: 0L },
      misses = notes.count { it.state == NoteState.MISSED || it.judgment == Judgment.MISS }
    )
  }

  private fun newViewModel() = RhythmGameViewModel(
    ControllerManager(context, SharedPreferencesMappingStore(context)),
    prefs = context.getSharedPreferences("mock_mat_test", Context.MODE_PRIVATE).also { it.edit().clear().commit() }
  )

  @Test
  fun consistentLateMatIsCorrectedByAutoSync() {
    val vm = newViewModel()
    assertTrue("auto-sync on by default", vm.autoSyncStatus.value.enabled)

    val first = vm.playRound(lagMs = 45, jitterMs = 8, seed = 1)
    val second = vm.playRound(lagMs = 45, jitterMs = 8, seed = 2)

    val offset = vm.inputOffsetMs.value
    println("mat lag 45±8 ms -> offset $offset ms; round1 mean ${first.errors.average()} ms, round2 mean ${second.errors.average()} ms, notes ${first.errors.size}")
    assertTrue("offset should converge near 45 ms, was $offset", abs(offset - 45) <= 6)
    assertTrue("second round should be centered, mean ${second.errors.average()}", abs(second.errors.average()) < abs(first.errors.average()))
  }

  @Test
  fun sloppyStepsDoNotMoveTheOffset() {
    val vm = newViewModel()
    vm.playRound(lagMs = 0, jitterMs = 90, seed = 3)
    println("jitter ±90 ms -> offset ${vm.inputOffsetMs.value} ms, last: ${vm.autoSyncStatus.value.lastMessage}")
    assertEquals(0, vm.inputOffsetMs.value)
  }

  @Test
  fun earlyMatGivesNegativeOffset() {
    val vm = newViewModel()
    vm.playRound(lagMs = -30, jitterMs = 6, seed = 4)
    println("mat lag -30±6 ms -> offset ${vm.inputOffsetMs.value} ms")
    assertTrue("offset should be near -30, was ${vm.inputOffsetMs.value}", abs(vm.inputOffsetMs.value + 30) <= 6)
  }
}
