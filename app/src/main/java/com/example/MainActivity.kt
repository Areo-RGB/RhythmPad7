package com.example

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.state.RhythmGameViewModel
import com.example.ui.screens.ControllerSettingsScreen
import com.example.ui.screens.GameplayScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.RhythmPadTheme

enum class AppScreen {
  HOME,
  GAMEPLAY,
  SETTINGS
}

class MainActivity : ComponentActivity() {

  // Standard Android ViewModel lifecycle ownership via viewModels delegate and Factory
  private val viewModel: RhythmGameViewModel by viewModels {
    RhythmGameViewModel.Factory(applicationContext)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    hideSystemBars()

    setContent {
      RhythmPadTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
          RhythmAppContent(viewModel = viewModel)
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    hideSystemBars()
    viewModel.onActivityResumed()
  }

  override fun onPause() {
    super.onPause()
    viewModel.onActivityPaused()
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) {
      hideSystemBars()
    } else {
      viewModel.onActivityPaused()
    }
  }

  // --- Hardware Controller Event Forwarding ---

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    // Intercept controller key events and restrict to target dance mat
    if (viewModel.controllerManager.dispatchKeyEvent(event)) {
      return true
    }
    return super.dispatchKeyEvent(event)
  }

  override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
    // Intercept joystick/dance-mat axis events
    if (viewModel.controllerManager.dispatchGenericMotionEvent(event)) {
      return true
    }
    return super.dispatchGenericMotionEvent(event)
  }

  private fun hideSystemBars() {
    val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
    windowInsetsController.systemBarsBehavior =
      WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
  }
}

@Composable
fun RhythmAppContent(viewModel: RhythmGameViewModel) {
  var currentScreen by remember { mutableStateOf(AppScreen.HOME) }

  val inputState by viewModel.inputState.collectAsStateWithLifecycle()
  val controllerInfo by viewModel.controllerInfo.collectAsStateWithLifecycle()
  val currentMapping by viewModel.currentMapping.collectAsStateWithLifecycle()
  val capturingLane by viewModel.capturingLane.collectAsStateWithLifecycle()
  val isDebugVisible by viewModel.isDebugOverlayVisible.collectAsStateWithLifecycle()
  val isSimVisible by viewModel.isSimulatedInputVisible.collectAsStateWithLifecycle()
  val isPaused by viewModel.isPaused.collectAsStateWithLifecycle()

  AnimatedContent(
    targetState = currentScreen,
    transitionSpec = { fadeIn() togetherWith fadeOut() },
    label = "screen_transition"
  ) { screen ->
    when (screen) {
      AppScreen.HOME -> {
        HomeScreen(
          controllerInfo = controllerInfo,
          inputState = inputState,
          onStartGame = {
            viewModel.resumeGame()
            currentScreen = AppScreen.GAMEPLAY
          },
          onOpenSettings = {
            currentScreen = AppScreen.SETTINGS
          },
          onScanControllers = {
            viewModel.scanControllers()
          }
        )
      }

      AppScreen.GAMEPLAY -> {
        GameplayScreen(
          inputState = inputState,
          controllerInfo = controllerInfo,
          isDebugOverlayVisible = isDebugVisible,
          isSimulatedInputVisible = isSimVisible,
          isPaused = isPaused,
          onSimulatedInput = { lane, isPressed ->
            viewModel.onSimulatedInput(lane, isPressed)
          },
          onToggleDebug = {
            viewModel.toggleDebugOverlay()
          },
          onToggleSimulatedInput = {
            viewModel.toggleSimulatedInput()
          },
          onTogglePause = {
            viewModel.togglePause()
          },
          onResume = {
            viewModel.resumeGame()
          },
          onResetCounters = {
            viewModel.resetCounters()
          },
          onNavigateBack = {
            viewModel.clearPressedStates()
            currentScreen = AppScreen.HOME
          }
        )
      }

      AppScreen.SETTINGS -> {
        ControllerSettingsScreen(
          controllerInfo = controllerInfo,
          currentMapping = currentMapping,
          capturingLane = capturingLane,
          inputState = inputState,
          rawInputEvents = viewModel.rawInputEvents,
          onStartCapture = { lane ->
            viewModel.startCapture(lane)
          },
          onCancelCapture = {
            viewModel.cancelCapture()
          },
          onResetDefaults = {
            viewModel.resetMappings()
          },
          onScanControllers = {
            viewModel.scanControllers()
          },
          onNavigateBack = {
            viewModel.cancelCapture()
            currentScreen = AppScreen.HOME
          }
        )
      }
    }
  }
}
