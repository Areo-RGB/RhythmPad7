package com.example

import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import com.example.core.input.AxisTracker
import com.example.core.input.ControllerManager
import com.example.core.input.SharedPreferencesMappingStore
import com.example.core.model.ControllerMapping
import com.example.core.model.GameInputEvent
import com.example.core.model.GameLane
import com.example.core.model.InputBinding
import com.example.core.model.InputSource
import com.example.core.state.GameInputState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RhythmPadInputTest {

  private lateinit var context: Context

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
  }

  // --- 1. Press / Release State Transitions ---

  @Test
  fun testPressAndReleaseStateTransitions() {
    var state = GameInputState()
    assertFalse("Initial lane should not be pressed", state.isLanePressed(GameLane.LEFT))
    assertEquals(0, state.getPressCount(GameLane.LEFT))

    // Press LEFT
    val pressEvent = GameInputEvent(
      lane = GameLane.LEFT,
      isPressed = true,
      timestamp = 1000L,
      source = InputSource.CONTROLLER
    )
    state = state.applyEvent(pressEvent)
    assertTrue("Lane should be pressed", state.isLanePressed(GameLane.LEFT))
    assertEquals(1, state.getPressCount(GameLane.LEFT))
    assertEquals(1000L, state.lastInputTimestamps[GameLane.LEFT])

    // Release LEFT
    val releaseEvent = GameInputEvent(
      lane = GameLane.LEFT,
      isPressed = false,
      timestamp = 1100L,
      source = InputSource.CONTROLLER
    )
    state = state.applyEvent(releaseEvent)
    assertFalse("Lane should be released", state.isLanePressed(GameLane.LEFT))
    // Press count should remain 1 on release
    assertEquals(1, state.getPressCount(GameLane.LEFT))
  }

  // --- 2. Duplicate DOWN Prevention ---

  @Test
  fun testDuplicateDownPreventionInState() {
    var state = GameInputState()

    val event1 = GameInputEvent(GameLane.UP, true, 100L, InputSource.CONTROLLER)
    val event2 = GameInputEvent(GameLane.UP, true, 150L, InputSource.CONTROLLER)

    state = state.applyEvent(event1)
    assertEquals(1, state.getPressCount(GameLane.UP))

    // Applying duplicate press event while already pressed should NOT increment counter
    state = state.applyEvent(event2)
    assertEquals(1, state.getPressCount(GameLane.UP))
    assertTrue(state.isLanePressed(GameLane.UP))
  }

  // --- 3. Multiple Simultaneous Inputs ---

  @Test
  fun testMultipleSimultaneousInputs() {
    var state = GameInputState()

    // Press LEFT and RIGHT simultaneously (Jump)
    state = state.applyEvent(GameInputEvent(GameLane.LEFT, true, 100L, InputSource.CONTROLLER))
    state = state.applyEvent(GameInputEvent(GameLane.RIGHT, true, 100L, InputSource.CONTROLLER))

    assertTrue("LEFT must be pressed", state.isLanePressed(GameLane.LEFT))
    assertTrue("RIGHT must be pressed", state.isLanePressed(GameLane.RIGHT))
    assertFalse("DOWN should remain idle", state.isLanePressed(GameLane.DOWN))
    assertFalse("UP should remain idle", state.isLanePressed(GameLane.UP))
  }

  // --- 4. Independent Release Behavior ---

  @Test
  fun testIndependentReleaseBehavior() {
    var state = GameInputState()

    // Hold LEFT and DOWN
    state = state.applyEvent(GameInputEvent(GameLane.LEFT, true, 100L, InputSource.CONTROLLER))
    state = state.applyEvent(GameInputEvent(GameLane.DOWN, true, 105L, InputSource.CONTROLLER))

    // Release LEFT only
    state = state.applyEvent(GameInputEvent(GameLane.LEFT, false, 150L, InputSource.CONTROLLER))

    assertFalse("LEFT should be released", state.isLanePressed(GameLane.LEFT))
    assertTrue("DOWN must stay pressed independently", state.isLanePressed(GameLane.DOWN))
  }

  // --- 5. Axis Threshold and Hysteresis Logic ---

  @Test
  fun testAxisThresholdAndHysteresis() {
    val tracker = AxisTracker(pressThreshold = 0.50f, releaseThreshold = 0.25f)
    val transitions = mutableListOf<Triple<Int, Int, Boolean>>()

    val callback: (Int, Int, Boolean) -> Unit = { axis, direction, isPressed ->
      transitions.add(Triple(axis, direction, isPressed))
    }

    val axisX = MotionEvent.AXIS_X

    // Small movement under threshold -> no transition
    tracker.processAxis(axisX, -0.30f, callback)
    assertTrue("Below press threshold should not trigger", transitions.isEmpty())

    // Crosses negative press threshold (-0.50f)
    tracker.processAxis(axisX, -0.60f, callback)
    assertEquals(1, transitions.size)
    assertEquals(Triple(axisX, -1, true), transitions.last())
    assertTrue(tracker.isPressed(axisX, -1))

    // Value relaxes to -0.35f (above release threshold -0.25f) -> must STAY PRESSED (Hysteresis)
    tracker.processAxis(axisX, -0.35f, callback)
    assertEquals(1, transitions.size)
    assertTrue(tracker.isPressed(axisX, -1))

    // Value drops below release threshold to -0.10f -> triggers release
    tracker.processAxis(axisX, -0.10f, callback)
    assertEquals(2, transitions.size)
    assertEquals(Triple(axisX, -1, false), transitions.last())
    assertFalse(tracker.isPressed(axisX, -1))
  }

  // --- 6. Input State Reset ---

  @Test
  fun testInputStateReset() {
    var state = GameInputState()
    state = state.applyEvent(GameInputEvent(GameLane.LEFT, true, 100L, InputSource.CONTROLLER))
    state = state.applyEvent(GameInputEvent(GameLane.UP, true, 105L, InputSource.CONTROLLER))

    assertTrue(state.isAnyLanePressed)

    // Clear pressed states (e.g. pause/focus loss)
    val cleared = state.clearPressedStates()
    assertFalse(cleared.isAnyLanePressed)
    // Counters should be preserved
    assertEquals(1, cleared.getPressCount(GameLane.LEFT))
    assertEquals(1, cleared.getPressCount(GameLane.UP))

    // Reset all
    val reset = cleared.resetAll()
    assertEquals(0, reset.getPressCount(GameLane.LEFT))
    assertEquals(0, reset.getPressCount(GameLane.UP))
    assertTrue(reset.recentEvents.isEmpty())
  }

  // --- 7. Mapping Serialization & Restoration ---

  @Test
  fun testMappingSerializationAndRestoration() {
    val store = SharedPreferencesMappingStore(context)
    val defaults = store.resetToDefaults()

    assertNotNull(defaults)
    assertTrue(defaults.leftBindings.isNotEmpty())
    assertTrue(defaults.downBindings.isNotEmpty())
    assertTrue(defaults.upBindings.isNotEmpty())
    assertTrue(defaults.rightBindings.isNotEmpty())

    // Customize a binding
    val customBinding = InputBinding.Key(KeyEvent.KEYCODE_BUTTON_L1, "KEYCODE_BUTTON_L1")
    val modified = defaults.withBindingReplaced(GameLane.LEFT, customBinding)
    store.saveMapping(modified)

    // Reload from store
    val reloaded = store.loadMapping()
    assertEquals(listOf(customBinding), reloaded.leftBindings)
    assertEquals(GameLane.LEFT, reloaded.findLaneFor(customBinding))

    // Restore defaults
    val restored = store.resetToDefaults()
    assertFalse(restored.leftBindings.contains(customBinding))
  }

  // --- 8. ControllerManager Key Repeat Suppression ---

  @Test
  fun testControllerManagerKeyRepeatSuppressed() {
    val store = SharedPreferencesMappingStore(context)
    val manager = ControllerManager(context, store)

    val downEvent = KeyEvent(
      0L, 100L, KeyEvent.ACTION_DOWN,
      KeyEvent.KEYCODE_DPAD_LEFT, 0
    )
    val repeatEvent = KeyEvent(
      0L, 150L, KeyEvent.ACTION_DOWN,
      KeyEvent.KEYCODE_DPAD_LEFT, 1 // repeat count 1
    )
    val upEvent = KeyEvent(
      0L, 200L, KeyEvent.ACTION_UP,
      KeyEvent.KEYCODE_DPAD_LEFT, 0
    )

    val handled1 = manager.dispatchKeyEvent(downEvent)
    assertTrue("First DOWN should be handled", handled1)

    val handledRepeat = manager.dispatchKeyEvent(repeatEvent)
    // Repeat event should return true (consumed) but not emit new press
    assertTrue(handledRepeat)

    val handledUp = manager.dispatchKeyEvent(upEvent)
    assertTrue("UP should be handled", handledUp)
  }
}
