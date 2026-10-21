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
import com.example.core.model.Judgment
import com.example.core.state.RhythmGameViewModel
import com.example.ui.screens.ControllerSettingsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.GameplayScreen
import com.example.ui.components.NoPadInputs
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MusicSelectScreen
import com.example.ui.screens.MusicSelectState
import com.example.ui.theme.RhythmPadTheme

enum class AppScreen {
  HOME,
  MUSIC_SELECT,
  GAMEPLAY,
  SETTINGS,
  OPTIONS
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
  val context = androidx.compose.ui.platform.LocalContext.current
  val timingPrefs = remember { context.getSharedPreferences("rhythmpad_settings", android.content.Context.MODE_PRIVATE) }
  var timingScale by remember {
    Judgment.timingWindowScale = timingPrefs.getFloat("timing_window_scale", 1.0f)
    androidx.compose.runtime.mutableFloatStateOf(Judgment.timingWindowScale)
  }

  val inputState by viewModel.inputState.collectAsStateWithLifecycle()
  val roundState by viewModel.roundState.collectAsStateWithLifecycle()
  val controllerInfo by viewModel.controllerInfo.collectAsStateWithLifecycle()
  val inputOffsetMs by viewModel.inputOffsetMs.collectAsStateWithLifecycle()
  val autoSyncStatus by viewModel.autoSyncStatus.collectAsStateWithLifecycle()
  val profile by viewModel.profile.collectAsStateWithLifecycle()
  val currentMapping by viewModel.currentMapping.collectAsStateWithLifecycle()
  val capturingLane by viewModel.capturingLane.collectAsStateWithLifecycle()
  val isDebugVisible by viewModel.isDebugOverlayVisible.collectAsStateWithLifecycle()
  val isPaused by viewModel.isPaused.collectAsStateWithLifecycle()
  val songs by viewModel.songs.collectAsStateWithLifecycle()
  val songsLoading by viewModel.songsLoading.collectAsStateWithLifecycle()
  val bestScores by viewModel.bestScores.collectAsStateWithLifecycle()
  val lastSubmitResult by viewModel.lastResult.collectAsStateWithLifecycle()
  val musicSelectState = remember { MusicSelectState() }
  val scrollMod by viewModel.scrollMod.collectAsStateWithLifecycle()

  AnimatedContent(
    targetState = currentScreen,
    transitionSpec = { fadeIn() togetherWith fadeOut() },
    label = "screen_transition"
  ) { screen ->
    when (screen) {
      AppScreen.HOME -> {
        HomeScreen(
          menuInputs = if (screen == currentScreen) viewModel.menuInputs else NoPadInputs,
          controllerInfo = controllerInfo,
          inputState = inputState,
          onStartGame = {
            currentScreen = AppScreen.MUSIC_SELECT
          },
          timingScale = timingScale,
          onTimingScaleChange = { scale ->
            Judgment.timingWindowScale = scale
            timingScale = Judgment.timingWindowScale
            timingPrefs.edit().putFloat("timing_window_scale", timingScale).apply()
          },
          onScanControllers = {
            viewModel.scanControllers()
          },
          onOpenSettings = {
            currentScreen = AppScreen.OPTIONS
          },
          profile = profile,
          bestGradeTier = bestScores.values.minOfOrNull { it.gradeTier },
          chartsPlayed = bestScores.size,
          bestPercent = bestScores.values.maxOfOrNull { it.percentDp },
          onSelectProfile = { viewModel.selectProfile(it) },
          inputOffsetMs = inputOffsetMs,
          onInputOffsetChange = { viewModel.setInputOffsetMs(it) },
          autoSyncEnabled = autoSyncStatus.enabled,
          onToggleAutoSync = { viewModel.setAutoSyncEnabled(!autoSyncStatus.enabled) }
        )
      }

      AppScreen.MUSIC_SELECT -> {
        MusicSelectScreen(
          playerName = profile.displayName,
          menuInputs = if (screen == currentScreen) viewModel.menuInputs else NoPadInputs,
          songs = songs,
          isLoading = songsLoading,
          bestScores = bestScores,
          onStartSong = { song, chart ->
            viewModel.stopPreview()
            viewModel.startSongRound(song, chart)
            currentScreen = AppScreen.GAMEPLAY
          },
          onStartPractice = {
            viewModel.stopPreview()
            viewModel.startNewRound()
            currentScreen = AppScreen.GAMEPLAY
          },
          onPreview = { song -> viewModel.previewSong(song) },
          onNavigateBack = {
            viewModel.stopPreview()
            currentScreen = AppScreen.HOME
          },
          state = musicSelectState,
          scrollMod = scrollMod,
          onScrollModChange = { viewModel.setScrollMod(it) }
        )
      }

      AppScreen.GAMEPLAY -> {
        GameplayScreen(
          menuInputs = if (screen == currentScreen) viewModel.menuInputs else NoPadInputs,
          inputState = inputState,
          roundState = roundState,
          controllerInfo = controllerInfo,
          isDebugOverlayVisible = isDebugVisible,
          isPaused = isPaused,
          lastSubmitResult = lastSubmitResult,
          onSyncRoundClock = {
            viewModel.syncRoundClock()
          },
          onToggleDebug = {
            viewModel.toggleDebugOverlay()
          },
          onTogglePause = {
            viewModel.togglePause()
          },
          onResume = {
            viewModel.resumeGame()
          },
          onRestartRound = {
            viewModel.restartRound()
          },
          onNavigateBack = {
            viewModel.stopRound()
            currentScreen = AppScreen.MUSIC_SELECT
          }
        )
      }

      AppScreen.OPTIONS -> {
        SettingsScreen(
          menuInputs = if (screen == currentScreen) viewModel.menuInputs else NoPadInputs,
          controllerInfo = controllerInfo,
          inputState = inputState,
          inputOffsetMs = inputOffsetMs,
          onInputOffsetChange = { viewModel.setInputOffsetMs(it) },
          autoSyncStatus = autoSyncStatus,
          onToggleAutoSync = { viewModel.setAutoSyncEnabled(!autoSyncStatus.enabled) },
          onOpenControllerSettings = { currentScreen = AppScreen.SETTINGS },
          onNavigateBack = { currentScreen = AppScreen.HOME },
          playerName = profile.displayName
        )
      }

      AppScreen.SETTINGS -> {
        ControllerSettingsScreen(
          menuInputs = if (screen == currentScreen) viewModel.menuInputs else NoPadInputs,
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
            currentScreen = AppScreen.OPTIONS
          }
        )
      }
    }
  }
}
