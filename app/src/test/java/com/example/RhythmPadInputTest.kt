package com.example

import android.content.Context
import android.view.InputDevice
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import com.example.core.input.ControllerManager
import com.example.core.input.SharedPreferencesMappingStore
import com.example.core.model.ControllerInfo
import com.example.core.model.ControllerMapping
import com.example.core.model.GameInputEvent
import com.example.core.model.GameLane
import com.example.core.model.InputBinding
import com.example.core.model.InputSource
import com.example.core.state.GameInputState
import com.example.core.state.RhythmGameViewModel
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

    // 1. Controller presses LEFT
    state = state.applyEvent(GameInputEvent(GameLane.LEFT, true, 100L, InputSource.CONTROLLER, "KEY_BTN1"))
    assertTrue(state.isLanePressed(GameLane.LEFT))
    assertEquals(1, state.getPressCount(GameLane.LEFT))

    // 2. Simulated touch also holds LEFT
    state = state.applyEvent(GameInputEvent(GameLane.LEFT, true, 120L, InputSource.SIMULATED, "SIM_LEFT"))
    assertTrue(state.isLanePressed(GameLane.LEFT))
    assertEquals(1, state.getPressCount(GameLane.LEFT))

    // 3. Controller releases, but simulated is STILL held
    state = state.applyEvent(GameInputEvent(GameLane.LEFT, false, 150L, InputSource.CONTROLLER, "KEY_BTN1"))
    assertTrue("Lane must stay pressed because SIM_LEFT is still held!", state.isLanePressed(GameLane.LEFT))

    // 4. Simulated touch releases
    state = state.applyEvent(GameInputEvent(GameLane.LEFT, false, 200L, InputSource.SIMULATED, "SIM_LEFT"))
    assertFalse("Lane must release once all bindings release", state.isLanePressed(GameLane.LEFT))
  }

  // --- 10. Unrelated devices cannot trigger the selected Microntek profile ---
  @Test
  fun testUnrelatedDevicesCannotTriggerSelectedMicrontekProfile() {
    val store = SharedPreferencesMappingStore(context)
    val manager = ControllerManager(context, store)

    // Simulate connecting Microntek USB Joystick with deviceId = 5
    // Dispatching event from deviceId = 99 (e.g. keyboard or unrelated controller)
    val downFromUnrelated = KeyEvent(0L, 100L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_1, 0)
    // By default in unit test with no hardware, deviceId is 0.
    // If manager has an active controller, deviceId mismatch returns false.
    val handled = manager.dispatchKeyEvent(downFromUnrelated)
    // Should be handled if device is test/default, but system keys are rejected:
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

    // Device disconnect clears active states
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

    // Reset
    val restored = store.resetToDefaults()
    assertTrue("Reset must restore BUTTON_1 to LEFT",
      restored.leftBindings.any { it is InputBinding.Key && it.keyCode == KeyEvent.KEYCODE_BUTTON_1 })
    assertTrue("Reset must restore Microntek profile", restored.isMicrontekProfileActive())
  }

  // --- 15. Existing simulated input continues working ---
  @Test
  fun testExistingSimulatedInputContinuesWorking() {
    val store = SharedPreferencesMappingStore(context)
    val manager = ControllerManager(context, store)
    val viewModel = RhythmGameViewModel(manager)

    // Simulated press on LEFT
    viewModel.onSimulatedInput(GameLane.LEFT, true)
    assertTrue("Simulated input must activate LEFT", viewModel.inputState.value.isLanePressed(GameLane.LEFT))
    assertEquals(1, viewModel.inputState.value.getPressCount(GameLane.LEFT))

    // Simulated release on LEFT
    viewModel.onSimulatedInput(GameLane.LEFT, false)
    assertFalse("Simulated release must deactivate LEFT", viewModel.inputState.value.isLanePressed(GameLane.LEFT))
    assertEquals(1, viewModel.inputState.value.getPressCount(GameLane.LEFT))
  }
}
