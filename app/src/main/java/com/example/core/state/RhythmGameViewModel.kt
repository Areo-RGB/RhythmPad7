package com.example.core.state

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.gameplay.GameplayRoundState
import com.example.core.input.ControllerManager
import com.example.core.input.SharedPreferencesMappingStore
import com.example.core.model.ControllerInfo
import com.example.core.model.ControllerMapping
import com.example.core.model.GameInputEvent
import com.example.core.model.GameLane
import com.example.core.model.RawInputEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Main ViewModel coordinating physical dance-mat input state, scrolling rhythm gameplay round state,
 * controller mappings, and debug telemetry HUD.
 * Managed via Android ViewModel lifecycle so onCleared() cleanly unregisters hardware listeners.
 */
class RhythmGameViewModel(
  val controllerManager: ControllerManager
) : ViewModel() {

  // Live playfield input state (pressed lanes, press counters, active holders, event history)
  private val _inputState = MutableStateFlow(GameInputState())
  val inputState: StateFlow<GameInputState> = _inputState.asStateFlow()

  // Scrolling practice chart & judgment round state
  private val _roundState = MutableStateFlow(GameplayRoundState.createNewRound(isRunning = false))
  val roundState: StateFlow<GameplayRoundState> = _roundState.asStateFlow()

  // Hardware controller status
  val controllerInfo: StateFlow<ControllerInfo> = controllerManager.controllerInfo

  // Directional controller mappings
  val currentMapping: StateFlow<ControllerMapping> = controllerManager.currentMapping

  // Capture mode for mapping UI
  val capturingLane: StateFlow<GameLane?> = controllerManager.capturingLane

  // Raw telemetry stream for settings
  val rawInputEvents: SharedFlow<RawInputEvent> = controllerManager.rawInputEvents

  // UI state toggles
  private val _isDebugOverlayVisible = MutableStateFlow(false)
  val isDebugOverlayVisible: StateFlow<Boolean> = _isDebugOverlayVisible.asStateFlow()

  private val _isPaused = MutableStateFlow(false)
  val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

  init {
    viewModelScope.launch {
      controllerManager.gameInputEvents.collect { event ->
        processInputEvent(event)
      }
    }
  }

  /**
   * Unified physical controller input processing pipeline.
   * While paused, gameplay input transitions and note judgments are strictly rejected.
   */
  fun processInputEvent(event: GameInputEvent) {
    if (_isPaused.value) {
      return
    }

    val previousPressed = _inputState.value.isLanePressed(event.lane)
    val updatedInputState = _inputState.value.applyEvent(event)
    _inputState.value = updatedInputState
    val nowPressed = updatedInputState.isLanePressed(event.lane)

    // Trigger note judgment only on a fresh lane press transition during an active round
    if (!previousPressed && nowPressed && event.isPressed) {
      val currentRound = _roundState.value
      if (currentRound.isRunning && !currentRound.isPaused && !currentRound.isCompleted) {
        _roundState.value = currentRound.onLanePressed(
          lane = event.lane,
          inputGameTimeMs = currentRound.currentGameTimeMs
        )
      }
    }
  }

  /**
   * Starts or restarts a fresh scrolling practice round from 0 ms.
   */
  fun startNewRound() {
    clearPressedStates()
    _inputState.value = _inputState.value.resetAll()
    _isPaused.value = false
    _roundState.value = GameplayRoundState.createNewRound(isRunning = true)
  }

  /**
   * Restarts the practice round from the beginning.
   */
  fun restartRound() {
    startNewRound()
  }

  /**
   * Advances the scrolling gameplay clock by [deltaMs] milliseconds.
   */
  fun advanceRoundBy(deltaMs: Long) {
    if (deltaMs <= 0L) return
    val current = _roundState.value
    if (!current.isRunning || current.isPaused || current.isCompleted) return
    _roundState.value = current.advanceTime(current.currentGameTimeMs + deltaMs)
  }

  /**
   * Sets the round clock to an explicit [gameTimeMs] timestamp (useful for frame clock or tests).
   */
  fun updateRoundTimeMs(gameTimeMs: Long) {
    val current = _roundState.value
    if (!current.isRunning || current.isPaused || current.isCompleted) return
    _roundState.value = current.advanceTime(gameTimeMs)
  }

  fun toggleDebugOverlay() {
    _isDebugOverlayVisible.value = !_isDebugOverlayVisible.value
  }

  fun togglePause() {
    val currentRound = _roundState.value
    if (currentRound.isCompleted) return

    val willPause = !_isPaused.value
    _isPaused.value = willPause
    _roundState.value = currentRound.copy(isPaused = willPause)
    if (willPause) {
      clearPressedStates()
    }
  }

  fun pauseGame() {
    val currentRound = _roundState.value
    if (currentRound.isCompleted || _isPaused.value) return
    _isPaused.value = true
    _roundState.value = currentRound.copy(isPaused = true)
    clearPressedStates()
  }

  fun resumeGame() {
    _isPaused.value = false
    val currentRound = _roundState.value
    if (!currentRound.isCompleted) {
      _roundState.value = currentRound.copy(isPaused = false, isRunning = true)
    }
  }

  fun stopRound() {
    _isPaused.value = false
    clearPressedStates()
    _roundState.value = _roundState.value.copy(isRunning = false, isPaused = false)
  }

  fun clearPressedStates() {
    controllerManager.clearAllPressedStates()
    _inputState.value = _inputState.value.clearPressedStates()
  }

  fun resetCounters() {
    _inputState.value = _inputState.value.resetAll()
  }

  fun onActivityPaused() {
    clearPressedStates()
    if (_roundState.value.isRunning && !_roundState.value.isCompleted) {
      pauseGame()
    }
  }

  fun onActivityResumed() {
    controllerManager.scanForControllers()
  }

  // Controller mapping delegates
  fun startCapture(lane: GameLane) {
    controllerManager.startCapture(lane)
  }

  fun cancelCapture() {
    controllerManager.cancelCapture()
  }

  fun resetMappings() {
    controllerManager.resetMappingsToDefaults()
  }

  fun scanControllers() {
    controllerManager.scanForControllers()
  }

  override fun onCleared() {
    super.onCleared()
    controllerManager.release()
  }

  class Factory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      val appContext = context.applicationContext
      val store = SharedPreferencesMappingStore(appContext)
      val manager = ControllerManager(appContext, store)
      return RhythmGameViewModel(manager) as T
    }
  }
}
