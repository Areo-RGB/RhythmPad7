package com.example

import android.content.Context
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import com.example.core.input.ControllerManager
import com.example.core.input.SharedPreferencesMappingStore
import com.example.core.model.GameInputEvent
import com.example.core.model.GameLane
import com.example.core.model.InputSource
import com.example.core.model.MenuButton
import com.example.core.model.MenuInput
import com.example.core.state.RhythmGameViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PadNavigationTest {
  private val context: Context = ApplicationProvider.getApplicationContext()

  @Test
  fun menuButtonsFollowStepManiaPadConvention() {
    assertEquals(MenuButton.START, MenuButton.fromKeyCode(KeyEvent.KEYCODE_BUTTON_10))
    assertEquals(MenuButton.SELECT, MenuButton.fromKeyCode(KeyEvent.KEYCODE_BUTTON_9))
    assertTrue(MenuButton.START.isConfirm && MenuButton.CIRCLE.isConfirm)
    assertTrue(MenuButton.SELECT.isBack && MenuButton.CROSS.isBack)
    assertFalse(MenuButton.TRIANGLE.isConfirm || MenuButton.TRIANGLE.isBack)
    // Arrow panels are never menu buttons
    assertEquals(null, MenuButton.fromKeyCode(KeyEvent.KEYCODE_BUTTON_1))
  }

  @Test
  fun startKeyEmitsMenuButtonOnceWithoutRepeats() = runTest {
    val manager = ControllerManager(context, SharedPreferencesMappingStore(context))
    val received = mutableListOf<MenuButton>()
    val job = launch(UnconfinedTestDispatcher(testScheduler)) { manager.menuButtons.collect { received += it } }

    assertTrue(manager.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_10)))
    // Held key auto-repeat must not re-trigger
    manager.dispatchKeyEvent(KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_10, 1))
    assertTrue(manager.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_10)))
    // System Back is never taken
    assertFalse(manager.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)))

    assertEquals(listOf(MenuButton.START), received)
    job.cancel()
  }

  @Test
  fun arrowPressesReachMenusEvenWhilePaused() = runTest {
    val manager = ControllerManager(context, SharedPreferencesMappingStore(context))
    val viewModel = RhythmGameViewModel(manager)
    val received = mutableListOf<MenuInput>()
    val job = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.menuInputs.collect { received += it } }

    viewModel.startNewRound()
    viewModel.pauseGame()
    viewModel.processInputEvent(GameInputEvent(GameLane.DOWN, true, 100L, InputSource.CONTROLLER, "KEY_189"))
    viewModel.processInputEvent(GameInputEvent(GameLane.DOWN, false, 150L, InputSource.CONTROLLER, "KEY_189"))

    assertEquals(listOf<MenuInput>(MenuInput.Arrow(GameLane.DOWN)), received)
    job.cancel()
  }
}
