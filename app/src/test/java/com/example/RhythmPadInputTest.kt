package com.example

import android.content.Context
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import com.example.core.gameplay.GameplayClock
import com.example.core.gameplay.GameplayRoundState
import com.example.core.gameplay.PracticePatternGenerator
import com.example.core.input.ControllerManager
import com.example.core.input.SharedPreferencesMappingStore
import com.example.core.model.GameInputEvent
import com.example.core.model.GameLane
import com.example.core.model.InputBinding
import com.example.core.model.InputSource
import com.example.core.model.Judgment
import com.example.core.model.NoteState
import com.example.core.model.PracticeNote
import com.example.core.state.GameInputState
import com.example.core.state.RhythmGameViewModel
import com.example.ui.components.PlayfieldGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.roundToLong

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RhythmPadInputTest {

  private lateinit var context: Context

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
  }

  // --- 1. BUTTON_1 -> LEFT ---
  @Test
  fun testButton1ActivatesLeft() {
    val store = SharedPreferencesMappingStore(context)
    val mapping = store.loadMapping()
    val binding = InputBinding.Key(KeyEvent.KEYCODE_BUTTON_1)
    assertEquals(GameLane.LEFT, mapping.findLaneFor(binding))

    var state = GameInputState()
    val event = GameInputEvent(GameLane.LEFT, true, 1000L, InputSource.CONTROLLER, "KEY_188")
    state = state.applyEvent(event)
    assertTrue("BUTTON_1 must activate LEFT lane", state.isLanePressed(GameLane.LEFT))
  }

  // --- 2. BUTTON_2 -> DOWN ---
  @Test
  fun testButton2ActivatesDown() {
    val store = SharedPreferencesMappingStore(context)
    val mapping = store.loadMapping()
    val binding = InputBinding.Key(KeyEvent.KEYCODE_BUTTON_2)
    assertEquals(GameLane.DOWN, mapping.findLaneFor(binding))

    var state = GameInputState()
    val event = GameInputEvent(GameLane.DOWN, true, 1000L, InputSource.CONTROLLER, "KEY_189")
    state = state.applyEvent(event)
    assertTrue("BUTTON_2 must activate DOWN lane", state.isLanePressed(GameLane.DOWN))
  }

  // --- 3. BUTTON_3 -> UP ---
  @Test
  fun testButton3ActivatesUp() {
    val store = SharedPreferencesMappingStore(context)
    val mapping = store.loadMapping()
    val binding = InputBinding.Key(KeyEvent.KEYCODE_BUTTON_3)
    assertEquals(GameLane.UP, mapping.findLaneFor(binding))

    var state = GameInputState()
    val event = GameInputEvent(GameLane.UP, true, 1000L, InputSource.CONTROLLER, "KEY_190")
    state = state.applyEvent(event)
    assertTrue("BUTTON_3 must activate UP lane", state.isLanePressed(GameLane.UP))
  }

  // --- 4. BUTTON_4 -> RIGHT ---
  @Test
  fun testButton4ActivatesRight() {
    val store = SharedPreferencesMappingStore(context)
    val mapping = store.loadMapping()
    val binding = InputBinding.Key(KeyEvent.KEYCODE_BUTTON_4)
    assertEquals(GameLane.RIGHT, mapping.findLaneFor(binding))

    var state = GameInputState()
    val event = GameInputEvent(GameLane.RIGHT, true, 1000L, InputSource.CONTROLLER, "KEY_191")
    state = state.applyEvent(event)
    assertTrue("BUTTON_4 must activate RIGHT lane", state.isLanePressed(GameLane.RIGHT))
  }

  // --- 5. BUTTON_1 does not activate unrelated lanes ---
  @Test
  fun testButton1DoesNotActivateUnrelatedLanes() {
    var state = GameInputState()
    val event = GameInputEvent(GameLane.LEFT, true, 1000L, InputSource.CONTROLLER, "KEY_188")
    state = state.applyEvent(event)

    assertTrue(state.isLanePressed(GameLane.LEFT))
    assertFalse("DOWN must remain unpressed", state.isLanePressed(GameLane.DOWN))
    assertFalse("UP must remain unpressed", state.isLanePressed(GameLane.UP))
    assertFalse("RIGHT must remain unpressed", state.isLanePressed(GameLane.RIGHT))
  }

  // --- 6. Repeated DOWN is ignored ---
  @Test
  fun testRepeatedDownIsIgnored() {
    var state = GameInputState()
    val event1 = GameInputEvent(GameLane.LEFT, true, 100L, InputSource.CONTROLLER, "KEY_188")
    val event2 = GameInputEvent(GameLane.LEFT, true, 150L, InputSource.CONTROLLER, "KEY_188")

    state = state.applyEvent(event1)
    assertEquals(1, state.getPressCount(GameLane.LEFT))

    // Second repeated DOWN while held
    state = state.applyEvent(event2)
    assertEquals("Counter must not increment on repeated DOWN", 1, state.getPressCount(GameLane.LEFT))
    assertTrue(state.isLanePressed(GameLane.LEFT))
  }

  // --- 7. LEFT and RIGHT remain independently pressed ---
  @Test
  fun testLeftAndRightRemainIndependentlyPressed() {
    var state = GameInputState()
    state = state.applyEvent(GameInputEvent(GameLane.LEFT, true, 100L, InputSource.CONTROLLER, "BTN_1"))
    state = state.applyEvent(GameInputEvent(GameLane.RIGHT, true, 100L, InputSource.CONTROLLER, "BTN_4"))

    assertTrue("LEFT must be pressed", state.isLanePressed(GameLane.LEFT))
    assertTrue("RIGHT must be pressed", state.isLanePressed(GameLane.RIGHT))
    assertFalse("DOWN must be unpressed", state.isLanePressed(GameLane.DOWN))
    assertFalse("UP must be unpressed", state.isLanePressed(GameLane.UP))
  }

  // --- 8. Releasing one held direction does not release another ---
  @Test
  fun testReleasingOneHeldDirectionDoesNotReleaseAnother() {
    var state = GameInputState()
    state = state.applyEvent(GameInputEvent(GameLane.LEFT, true, 100L, InputSource.CONTROLLER, "BTN_1"))
    state = state.applyEvent(GameInputEvent(GameLane.DOWN, true, 105L, InputSource.CONTROLLER, "BTN_2"))

    // Release LEFT
    state = state.applyEvent(GameInputEvent(GameLane.LEFT, false, 150L, InputSource.CONTROLLER, "BTN_1"))

    assertFalse("LEFT must be released", state.isLanePressed(GameLane.LEFT))
    assertTrue("DOWN must stay pressed independently", state.isLanePressed(GameLane.DOWN))
  }

  // --- 9. A second active binding cannot be incorrectly released by the first ---
  @Test
  fun testSecondActiveBindingCannotBeIncorrectlyReleasedByFirst() {
    var state = GameInputState()

    // 1. Primary controller binding presses LEFT
    state = state.applyEvent(GameInputEvent(GameLane.LEFT, true, 100L, InputSource.CONTROLLER, "KEY_BTN1"))
    assertTrue(state.isLanePressed(GameLane.LEFT))
    assertEquals(1, state.getPressCount(GameLane.LEFT))

    // 2. Secondary controller binding also holds LEFT
    state = state.applyEvent(GameInputEvent(GameLane.LEFT, true, 120L, InputSource.CONTROLLER, "KEY_DPAD_LEFT"))
    assertTrue(state.isLanePressed(GameLane.LEFT))
    assertEquals(1, state.getPressCount(GameLane.LEFT))

    // 3. First binding releases, but secondary binding is STILL held
    state = state.applyEvent(GameInputEvent(GameLane.LEFT, false, 150L, InputSource.CONTROLLER, "KEY_BTN1"))
    assertTrue("Lane must stay pressed because second binding is still held!", state.isLanePressed(GameLane.LEFT))

    // 4. Secondary binding releases
    state = state.applyEvent(GameInputEvent(GameLane.LEFT, false, 200L, InputSource.CONTROLLER, "KEY_DPAD_LEFT"))
    assertFalse("Lane must release once all bindings release", state.isLanePressed(GameLane.LEFT))
  }

  // --- 10. Unrelated devices & system keys cannot trigger the selected Microntek profile ---
  @Test
  fun testUnrelatedDevicesCannotTriggerSelectedMicrontekProfile() {
    val store = SharedPreferencesMappingStore(context)
    val manager = ControllerManager(context, store)

    val backKey = KeyEvent(0L, 100L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0)
    assertFalse("Back key must never be intercepted as controller event", manager.dispatchKeyEvent(backKey))

    val volumeKey = KeyEvent(0L, 100L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, 0)
    assertFalse("Volume key must never be intercepted as controller event", manager.dispatchKeyEvent(volumeKey))
  }

  // --- 11. Disconnect clears active input ---
  @Test
  fun testDisconnectClearsActiveInput() {
    var state = GameInputState()
    state = state.applyEvent(GameInputEvent(GameLane.LEFT, true, 100L, InputSource.CONTROLLER, "BTN_1"))
    state = state.applyEvent(GameInputEvent(GameLane.UP, true, 105L, InputSource.CONTROLLER, "BTN_3"))

    assertTrue(state.isAnyLanePressed)

    val cleared = state.clearPressedStates()
    assertFalse("All lanes must be clear on disconnect", cleared.isAnyLanePressed)
    assertEquals(1, cleared.getPressCount(GameLane.LEFT))
    assertEquals(1, cleared.getPressCount(GameLane.UP))
  }

  // --- 12. Default mappings load correctly ---
  @Test
  fun testDefaultMappingsLoadCorrectly() {
    val store = SharedPreferencesMappingStore(context)
    val mapping = store.loadMapping()

    assertTrue("Default LEFT must contain BUTTON_1",
      mapping.leftBindings.any { it is InputBinding.Key && it.keyCode == KeyEvent.KEYCODE_BUTTON_1 })
    assertTrue("Default DOWN must contain BUTTON_2",
      mapping.downBindings.any { it is InputBinding.Key && it.keyCode == KeyEvent.KEYCODE_BUTTON_2 })
    assertTrue("Default UP must contain BUTTON_3",
      mapping.upBindings.any { it is InputBinding.Key && it.keyCode == KeyEvent.KEYCODE_BUTTON_3 })
    assertTrue("Default RIGHT must contain BUTTON_4",
      mapping.rightBindings.any { it is InputBinding.Key && it.keyCode == KeyEvent.KEYCODE_BUTTON_4 })
    assertTrue("Microntek profile must be active by default", mapping.isMicrontekProfileActive())
  }

  // --- 13. Custom mappings persist ---
  @Test
  fun testCustomMappingsPersist() {
    val store = SharedPreferencesMappingStore(context)
    val customBinding = InputBinding.Key(KeyEvent.KEYCODE_BUTTON_5, "KEYCODE_BUTTON_5")
    val current = store.loadMapping()
    val modified = current.withBindingReplaced(GameLane.LEFT, customBinding)
    store.saveMapping(modified)

    val reloaded = store.loadMapping()
    assertEquals(listOf(customBinding), reloaded.leftBindings)
    assertEquals(GameLane.LEFT, reloaded.findLaneFor(customBinding))
  }

  // --- 14. Reset Defaults restores Microntek mappings ---
  @Test
  fun testResetDefaultsRestoresMicrontekMappings() {
    val store = SharedPreferencesMappingStore(context)
    val customBinding = InputBinding.Key(KeyEvent.KEYCODE_BUTTON_9, "KEYCODE_BUTTON_9")
    val modified = store.loadMapping().withBindingReplaced(GameLane.LEFT, customBinding)
    store.saveMapping(modified)

    val restored = store.resetToDefaults()
    assertTrue("Reset must restore BUTTON_1 to LEFT",
      restored.leftBindings.any { it is InputBinding.Key && it.keyCode == KeyEvent.KEYCODE_BUTTON_1 })
    assertTrue("Reset must restore Microntek profile", restored.isMicrontekProfileActive())
  }

  // --- 15. Beat-to-time conversion at 120 BPM ---
  @Test
  fun testBeatToTimeConversionAt120Bpm() {
    assertEquals(500.0, PracticePatternGenerator.beatDurationMs(120), 0.001)
    assertEquals(3000L, PracticePatternGenerator.beatToTimeMs(beat = 0.0, beatZeroTimeMs = 3000L, bpm = 120))
    assertEquals(3500L, PracticePatternGenerator.beatToTimeMs(beat = 1.0, beatZeroTimeMs = 3000L, bpm = 120))
    assertEquals(5000L, PracticePatternGenerator.beatToTimeMs(beat = 4.0, beatZeroTimeMs = 3000L, bpm = 120))
  }

  // --- 16. Note scrolling Y position math ---
  @Test
  fun testNoteScrollingYPositionMath() {
    val receptorY = 200f
    val playfieldHeight = 1200f
    val note = PracticeNote(id = 1, lane = GameLane.LEFT, beat = 0.0, hitTimeMs = 3000L)

    // Before hitTimeMs (at 1000ms), note is below receptor (Y > receptorY)
    val yAt1000 = note.calculateY(1000L, receptorY, playfieldHeight, 2800L)
    assertTrue("Before hit time, noteY must be below receptorY", yAt1000 > receptorY)

    // At 2000ms, note moves upward (yAt2000 < yAt1000)
    val yAt2000 = note.calculateY(2000L, receptorY, playfieldHeight, 2800L)
    assertTrue("Note must scroll upward as time increases", yAt2000 < yAt1000)
    assertTrue("At 2000ms, note is still below receptorY", yAt2000 > receptorY)

    // At exact hitTimeMs (3000ms), noteY == receptorY
    val yAtHit = note.calculateY(3000L, receptorY, playfieldHeight, 2800L)
    assertEquals("At exact hit time, noteY must align with receptorY", receptorY, yAtHit, 0.01f)
  }

  // --- 17. EXCELLENT, GOOD, and MISS judgment windows, score, and combo ---
  @Test
  fun testJudgmentWindowsScoreAndCombo() {
    var round = GameplayRoundState.createNewRound(bpm = 120, beatZeroTimeMs = 3000L)
    // First note: Beat 0.0 (3000ms) -> LEFT
    // Second note: Beat 1.0 (3500ms) -> DOWN
    // Third note: Beat 2.0 (4000ms) -> UP

    // 1. Hit first note at 3040ms (+40ms within ±70ms EXCELLENT window)
    round = round.advanceTime(3040L)
    round = round.onLanePressed(GameLane.LEFT, 3040L)
    assertEquals(1, round.excellentCount)
    assertEquals(100, round.score)
    assertEquals(1, round.combo)
    assertEquals(1, round.maxCombo)
    assertEquals(Judgment.EXCELLENT, round.lastJudgment?.judgment)

    // 2. Hit second note at 3610ms (+110ms within ±150ms GOOD window)
    round = round.advanceTime(3610L)
    round = round.onLanePressed(GameLane.DOWN, 3610L)
    assertEquals(1, round.goodCount)
    assertEquals(160, round.score)
    assertEquals(2, round.combo)
    assertEquals(2, round.maxCombo)
    assertEquals(Judgment.GOOD, round.lastJudgment?.judgment)

    // 3. Let third note (4000ms UP) pass beyond +150ms without pressing -> auto-MISS at 4200ms
    round = round.advanceTime(4200L)
    assertEquals(1, round.missCount)
    assertEquals(160, round.score)
    assertEquals("Combo must reset to 0 on MISS", 0, round.combo)
    assertEquals("Max combo must be preserved", 2, round.maxCombo)
    assertEquals(Judgment.MISS, round.lastJudgment?.judgment)
  }

  // --- 18. Single note cannot be judged twice & distant notes are not hit early ---
  @Test
  fun testNoteJudgedOnceAndDistantNotesProtected() {
    var round = GameplayRoundState.createNewRound(bpm = 120, beatZeroTimeMs = 3000L)

    // Press LEFT way too early at 1000ms (first LEFT note is at 3000ms)
    round = round.advanceTime(1000L)
    round = round.onLanePressed(GameLane.LEFT, 1000L)
    assertEquals("Distant note must not be judged at 1000ms", 0, round.judgedNotesCount)
    assertEquals(NoteState.PENDING, round.notes.first().state)

    // Now advance to 3000ms and hit first LEFT note
    round = round.advanceTime(3000L)
    round = round.onLanePressed(GameLane.LEFT, 3000L)
    assertEquals(1, round.excellentCount)
    assertEquals(NoteState.HIT, round.notes.first().state)

    // Press LEFT again at 3020ms — should NOT judge the already-hit note or the next LEFT note (at 5000ms)
    round = round.advanceTime(3020L)
    round = round.onLanePressed(GameLane.LEFT, 3020L)
    assertEquals("Repeated press must not double-score note", 1, round.excellentCount)
    assertEquals(100, round.score)
  }

  // --- 19. Simultaneous 2-arrow jump (Beat 7 = 6500ms LEFT + RIGHT) ---
  @Test
  fun testSimultaneousTwoArrowJumpJudgesBothLanes() {
    val store = SharedPreferencesMappingStore(context)
    val manager = ControllerManager(context, store)
    val viewModel = RhythmGameViewModel(manager)
    viewModel.startNewRound()

    // Beat 7.0 at 120 BPM (beatZero = 3000ms) is at 3000 + 7 * 500 = 6500ms (LEFT + RIGHT jump)
    viewModel.updateRoundTimeMs(6500L)

    viewModel.processInputEvent(
      GameInputEvent(GameLane.LEFT, true, 6500L, InputSource.CONTROLLER, "KEY_188")
    )
    viewModel.processInputEvent(
      GameInputEvent(GameLane.RIGHT, true, 6500L, InputSource.CONTROLLER, "KEY_191")
    )

    val jumpNotes = viewModel.roundState.value.notes.filter { it.beat == 7.0 }
    assertEquals(2, jumpNotes.size)
    assertTrue("Both jump notes must be judged HIT", jumpNotes.all { it.state == NoteState.HIT })
    assertEquals(2, viewModel.roundState.value.combo)
    assertEquals(200, viewModel.roundState.value.score)
  }

  // --- 20. Pause freezes game clock and rejects controller input ---
  @Test
  fun testPauseFreezesClockAndRejectsInput() {
    val store = SharedPreferencesMappingStore(context)
    val manager = ControllerManager(context, store)
    val viewModel = RhythmGameViewModel(manager)
    viewModel.startNewRound()

    viewModel.updateRoundTimeMs(3000L)
    viewModel.togglePause()
    assertTrue(viewModel.isPaused.value)

    // Attempt to advance time and send physical press while paused
    viewModel.advanceRoundBy(500L)
    assertEquals("Clock must remain frozen at 3000ms while paused", 3000L, viewModel.roundState.value.currentGameTimeMs)

    viewModel.processInputEvent(
      GameInputEvent(GameLane.LEFT, true, 3000L, InputSource.CONTROLLER, "KEY_188")
    )
    assertEquals("No note may be judged while paused", 0, viewModel.roundState.value.score)

    // Resume and verify input works again
    viewModel.resumeGame()
    assertFalse(viewModel.isPaused.value)
    viewModel.processInputEvent(
      GameInputEvent(GameLane.LEFT, true, 3000L, InputSource.CONTROLLER, "KEY_188")
    )
    assertEquals(100, viewModel.roundState.value.score)
  }

  // --- 21. Phase 1: Monotonic GameplayClock eliminates refresh-rate rounding drift at 60/90/120/144 Hz ---
  @Test
  fun testGameplayClockEliminatesRefreshRateRoundingDrift() {
    val refreshRatesHz = listOf(60, 90, 120, 144)

    for (hz in refreshRatesHz) {
      var currentUptimeMs = 20_000L
      val clock = GameplayClock { currentUptimeMs }
      clock.start(atUptimeMs = 20_000L)

      val totalSeconds = 10
      val totalFrames = hz * totalSeconds
      val framePeriodNanos = 1_000_000_000.0 / hz.toDouble()

      for (frame in 1..totalFrames) {
        val elapsedMsFromStart = ((frame * framePeriodNanos) / 1_000_000.0).roundToLong()
        currentUptimeMs = 20_000L + elapsedMsFromStart
        clock.elapsedGameplayMs()
      }

      assertEquals(
        "At ${hz}Hz, 10s of frames must equal exactly 10000ms with zero cumulative truncation drift",
        10_000L,
        clock.elapsedGameplayMs()
      )
    }
  }

  // --- 22. Phase 1: GameplayClock pause/resume offset accuracy and frame-stall resilience ---
  @Test
  fun testGameplayClockPauseResumePreservesExactOffsetsAndSurvivesFrameStalls() {
    var fakeUptimeMs = 10_000L
    val clock = GameplayClock { fakeUptimeMs }
    val store = SharedPreferencesMappingStore(context)
    val manager = ControllerManager(context, store)
    val viewModel = RhythmGameViewModel(manager, clock)

    viewModel.startNewRound()
    assertEquals(10_000L, clock.startUptimeMs)

    // Advance 2500ms
    fakeUptimeMs = 12_500L
    viewModel.syncRoundClock()
    assertEquals(2500L, viewModel.roundState.value.currentGameTimeMs)

    // Pause at 12_500L for 4321ms
    viewModel.pauseGame()
    fakeUptimeMs = 16_821L
    assertEquals("Gameplay time must stay frozen at 2500ms while paused", 2500L, clock.elapsedGameplayMs())

    // Resume at 16_821L
    viewModel.resumeGame()
    assertEquals(4321L, clock.accumulatedPauseDurationMs)

    // Simulate a 450ms frame stall (16_821L -> 17_271L)
    fakeUptimeMs = 17_271L
    viewModel.syncRoundClock()
    assertEquals(
      "Frame stall of 450ms must advance chart to exact 2950ms without clamping slowdown",
      2950L,
      viewModel.roundState.value.currentGameTimeMs
    )
  }

  // --- 23. Phase 2: KeyEvent.eventTime is used for hit judgment instead of last rendered frame time ---
  @Test
  fun testPhysicalKeyEventEventTimeUsedInsteadOfDelayedFrameTime() {
    var fakeUptimeMs = 50_000L
    val clock = GameplayClock { fakeUptimeMs }
    val store = SharedPreferencesMappingStore(context)
    store.resetToDefaults()
    val manager = ControllerManager(context, store)
    val viewModel = RhythmGameViewModel(manager, clock)

    viewModel.startNewRound()

    // Last rendered frame occurs at 52_810L -> 2810ms gameplay time (190ms early for the 3000ms note,
    // which is outside the ±150ms hit window if frame time were mistakenly used!)
    fakeUptimeMs = 52_810L
    viewModel.syncRoundClock()
    assertEquals(2810L, viewModel.roundState.value.currentGameTimeMs)

    // Physical dance-mat press arrives with authoritative KeyEvent.eventTime = 53_018L
    // (3018ms gameplay time -> +18ms from 3000ms note = EXCELLENT!)
    fakeUptimeMs = 53_018L
    val matDownEvent = KeyEvent(53_018L, 53_018L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_1, 0)
    assertTrue(manager.dispatchKeyEvent(matDownEvent))

    val stateAfterHit = viewModel.roundState.value
    assertEquals("Note must be judged EXCELLENT using KeyEvent.eventTime", 1, stateAfterHit.excellentCount)
    assertEquals(100, stateAfterHit.score)
    assertEquals(18L, stateAfterHit.lastJudgment?.timingErrorMs)
  }

  // --- 24. Phase 2: Delayed frame rendered after KeyEvent.eventTime still awards accurate hit ---
  @Test
  fun testDelayedFrameRenderedAfterKeyEventTimeReclaimsNoteJudgment() {
    var fakeUptimeMs = 50_000L
    val clock = GameplayClock { fakeUptimeMs }
    val store = SharedPreferencesMappingStore(context)
    store.resetToDefaults()
    val manager = ControllerManager(context, store)
    val viewModel = RhythmGameViewModel(manager, clock)

    viewModel.startNewRound()

    // Frame stalls and renders late at 53_190L (3190ms gameplay time, auto-missing the 3000ms note)
    fakeUptimeMs = 53_190L
    viewModel.syncRoundClock()
    assertEquals(1, viewModel.roundState.value.missCount)

    // Buffered physical KeyEvent whose kernel eventTime was 53_025L (3025ms -> +25ms EXCELLENT) is processed
    val matDownEvent = KeyEvent(53_025L, 53_025L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_1, 0)
    assertTrue(manager.dispatchKeyEvent(matDownEvent))

    val state = viewModel.roundState.value
    assertEquals("False auto-miss from delayed frame must be cleared", 0, state.missCount)
    assertEquals("On-time physical press must be judged EXCELLENT", 1, state.excellentCount)
    assertEquals(1, state.combo)
    assertEquals(100, state.score)
    assertEquals(25L, state.lastJudgment?.timingErrorMs)
  }

  // --- 25. Phase 3: Centered note highway width and equal lane center alignment ---
  @Test
  fun testPlayfieldGeometryCenteredHighwayAndEqualLaneCenters() {
    // Xiaomi Pad 7 landscape width (~1200 dp)
    val highwayWidthDp = PlayfieldGeometry.calculateHighwayWidthDp(1200f)
    assertTrue(
      "Highway width ($highwayWidthDp dp) must be within 560..640 dp on landscape tablet",
      highwayWidthDp in PlayfieldGeometry.MIN_HIGHWAY_WIDTH_DP..PlayfieldGeometry.MAX_HIGHWAY_WIDTH_DP
    )

    val highwayWidthPx = 600f
    val laneWidthPx = PlayfieldGeometry.laneWidth(highwayWidthPx)
    assertEquals(150f, laneWidthPx, 0.001f)

    assertEquals(75f, PlayfieldGeometry.laneCenterX(0, laneWidthPx), 0.001f)
    assertEquals(225f, PlayfieldGeometry.laneCenterX(1, laneWidthPx), 0.001f)
    assertEquals(375f, PlayfieldGeometry.laneCenterX(2, laneWidthPx), 0.001f)
    assertEquals(525f, PlayfieldGeometry.laneCenterX(3, laneWidthPx), 0.001f)
  }
}
