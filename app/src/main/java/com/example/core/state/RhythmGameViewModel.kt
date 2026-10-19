package com.example.core.state

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.input.ControllerManager
import com.example.core.model.ControllerInfo
import com.example.core.model.ControllerMapping
import com.example.core.model.GameInputEvent
import com.example.core.model.GameLane
import com.example.core.model.InputSource
import com.example.core.model.RawInputEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Main ViewModel coordinating the gameplay input state, controller management,
 * simulated inputs, and debugging UI toggles.
 */
class RhythmGameViewModel(
  private val controllerManager: ControllerManager
) : ViewModel() {

  // Live playfield state (pressed lanes, press counters, event history)
  private val _inputState = MutableStateFlow(GameInputState())
  val inputState: StateFlow<GameInputState> = _inputState.asStateFlow()

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

  private val _isSimulatedInputVisible = MutableStateFlow(true) // Visible by default for easy testing
  val isSimulatedInputVisible: StateFlow<Boolean> = _isSimulatedInputVisible.asStateFlow()

  private val _isPaused = MutableStateFlow(false)
  val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

  init {
    // Collect normalized controller events and apply them to the gameplay input state
    viewModelScope.launch {
      controllerManager.gameInputEvents.collect { event ->
        processInputEvent(event)
      }
    }
  }

  /**
   * Unified input processing pipeline used by both physical controllers and simulated inputs.
   */
  fun processInputEvent(event: GameInputEvent) {
    _inputState.value = _inputState.value.applyEvent(event)
  }

  /**
   * Dispatches simulated input from on-screen controls into the unified input pipeline.
   */
  fun onSimulatedInput(lane: GameLane, isPressed: Boolean) {
    val event = GameInputEvent(
      lane = lane,
      isPressed = isPressed,
      timestamp = SystemClock.uptimeMillis(),
      source = InputSource.SIMULATED
    )
    processInputEvent(event)
  }

  fun toggleDebugOverlay() {
    _isDebugOverlayVisible.value = !_isDebugOverlayVisible.value
  }

  fun toggleSimulatedInput() {
    _isSimulatedInputVisible.value = !_isSimulatedInputVisible.value
  }

  fun togglePause() {
    _isPaused.value = !_isPaused.value
    if (_isPaused.value) {
      clearPressedStates()
    }
  }

  fun resumeGame() {
    _isPaused.value = false
  }

  fun clearPressedStates() {
    controllerManager.clearAllPressedStates()
    _inputState.value = _inputState.value.clearPressedStates()
  }

  fun resetCounters() {
    _inputState.value = _inputState.value.resetAll()
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
}
