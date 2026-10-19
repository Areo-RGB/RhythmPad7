package com.example.core.input

import android.content.Context
import android.hardware.input.InputManager
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.example.core.model.ControllerInfo
import com.example.core.model.ControllerMapping
import com.example.core.model.GameInputEvent
import com.example.core.model.GameLane
import com.example.core.model.InputBinding
import com.example.core.model.InputSource
import com.example.core.model.RawInputEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Discovers controllers, handles raw hardware input (KeyEvents, MotionEvents),
 * maps buttons/axes to gameplay lanes, tracks connection state, and emits
 * normalized GameInputEvents.
 */
class ControllerManager(
  private val context: Context,
  private val mappingStore: ControllerMappingStore
) : InputManager.InputDeviceListener {

  private val inputManager = context.getSystemService(Context.INPUT_SERVICE) as? InputManager

  // State: Connected controller info
  private val _controllerInfo = MutableStateFlow(ControllerInfo.DISCONNECTED)
  val controllerInfo: StateFlow<ControllerInfo> = _controllerInfo.asStateFlow()

  // State: Active mappings
  private val _currentMapping = MutableStateFlow(mappingStore.loadMapping())
  val currentMapping: StateFlow<ControllerMapping> = _currentMapping.asStateFlow()

  // Stream of normalized gameplay input events
  private val _gameInputEvents = MutableSharedFlow<GameInputEvent>(extraBufferCapacity = 64)
  val gameInputEvents: SharedFlow<GameInputEvent> = _gameInputEvents.asSharedFlow()

  // Stream of raw input events for debugging and mapping telemetry
  private val _rawInputEvents = MutableSharedFlow<RawInputEvent>(extraBufferCapacity = 64)
  val rawInputEvents: SharedFlow<RawInputEvent> = _rawInputEvents.asSharedFlow()

  // Tracks currently held keycodes to eliminate duplicate ACTION_DOWN repeats
  private val pressedKeyCodes = mutableSetOf<Int>()

  // Axis hysteresis tracker
  private val axisTracker = AxisTracker(pressThreshold = 0.50f, releaseThreshold = 0.25f)

  // Mapping capture state: when non-null, the next valid input will bind to this lane
  private val _capturingLane = MutableStateFlow<GameLane?>(null)
  val capturingLane: StateFlow<GameLane?> = _capturingLane.asStateFlow()

  init {
    inputManager?.registerInputDeviceListener(this, null)
    scanForControllers()
  }

  fun scanForControllers() {
    val deviceIds = InputDevice.getDeviceIds()
    var foundController: ControllerInfo? = null

    for (id in deviceIds) {
      val device = InputDevice.getDevice(id) ?: continue
      val sources = device.sources
      val isGamepad = (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
      val isJoystick = (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
      val isDpad = (sources and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD

      if (isGamepad || isJoystick || isDpad || (!device.isVirtual && (device.vendorId != 0 || device.productId != 0))) {
        val isDanceMat = (device.vendorId == ControllerInfo.DANCE_MAT_VENDOR_ID &&
            device.productId == ControllerInfo.DANCE_MAT_PRODUCT_ID) ||
            device.name.contains("dance", ignoreCase = true) ||
            device.name.contains("mat", ignoreCase = true)

        foundController = ControllerInfo(
          isConnected = true,
          deviceId = device.id,
          name = device.name ?: "Generic Game Controller",
          vendorId = device.vendorId,
          productId = device.productId,
          descriptor = device.descriptor ?: "",
          isDanceMat = isDanceMat
        )
        break
      }
    }

    _controllerInfo.value = foundController ?: ControllerInfo.DISCONNECTED
  }

  // --- InputManager.InputDeviceListener ---

  override fun onInputDeviceAdded(deviceId: Int) {
    scanForControllers()
  }

  override fun onInputDeviceRemoved(deviceId: Int) {
    if (_controllerInfo.value.deviceId == deviceId) {
      clearAllPressedStates(InputSource.CONTROLLER)
      scanForControllers()
    }
  }

  override fun onInputDeviceChanged(deviceId: Int) {
    scanForControllers()
  }

  // --- Raw Android Event Dispatching ---

  /**
   * Processes a KeyEvent from MainActivity.
   * Returns true if the key was consumed by gameplay or capture mode.
   */
  fun dispatchKeyEvent(event: KeyEvent): Boolean {
    val keyCode = event.keyCode
    val action = event.action

    // Emit raw event for live telemetry
    val rawAction = if (action == KeyEvent.ACTION_DOWN) "DOWN" else "UP"
    _rawInputEvents.tryEmit(
      RawInputEvent(
        type = "KEY",
        name = KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_"),
        code = keyCode,
        action = rawAction,
        value = if (action == KeyEvent.ACTION_DOWN) 1f else 0f
      )
    )

    // Handle mapping capture mode first
    val currentCaptureLane = _capturingLane.value
    if (currentCaptureLane != null && action == KeyEvent.ACTION_DOWN) {
      val newBinding = InputBinding.Key(keyCode, KeyEvent.keyCodeToString(keyCode))
      applyNewBinding(currentCaptureLane, newBinding)
      _capturingLane.value = null
      return true
    }

    // Determine monotonic timestamp
    val timestamp = if (event.eventTime > 0) event.eventTime else SystemClock.uptimeMillis()

    when (action) {
      KeyEvent.ACTION_DOWN -> {
        // Strict duplicate suppression: ignore auto-repeated key-down events
        if (event.repeatCount > 0 || pressedKeyCodes.contains(keyCode)) {
          return isMappedKeyCode(keyCode)
        }
        pressedKeyCodes.add(keyCode)

        val targetLane = findLaneForKey(keyCode)
        if (targetLane != null) {
          _gameInputEvents.tryEmit(
            GameInputEvent(
              lane = targetLane,
              isPressed = true,
              timestamp = timestamp,
              source = InputSource.CONTROLLER
            )
          )
          return true
        }
      }

      KeyEvent.ACTION_UP -> {
        pressedKeyCodes.remove(keyCode)
        val targetLane = findLaneForKey(keyCode)
        if (targetLane != null) {
          _gameInputEvents.tryEmit(
            GameInputEvent(
              lane = targetLane,
              isPressed = false,
              timestamp = timestamp,
              source = InputSource.CONTROLLER
            )
          )
          return true
        }
      }
    }

    return false
  }

  /**
   * Processes a MotionEvent (joystick or hat axes) from MainActivity.
   * Returns true if axis input was consumed.
   */
  fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
    if ((event.source and InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK &&
      (event.source and InputDevice.SOURCE_GAMEPAD) != InputDevice.SOURCE_GAMEPAD &&
      (event.source and InputDevice.SOURCE_DPAD) != InputDevice.SOURCE_DPAD
    ) {
      return false
    }

    val timestamp = if (event.eventTime > 0) event.eventTime else SystemClock.uptimeMillis()
    var handled = false

    // Common axes used by D-pads and dance mats
    val axesToCheck = intArrayOf(
      MotionEvent.AXIS_HAT_X,
      MotionEvent.AXIS_HAT_Y,
      MotionEvent.AXIS_X,
      MotionEvent.AXIS_Y
    )

    for (axis in axesToCheck) {
      val value = event.getAxisValue(axis)

      // Emit raw telemetry for significant movements
      if (Math.abs(value) > 0.15f) {
        _rawInputEvents.tryEmit(
          RawInputEvent(
            type = "AXIS",
            name = MotionEvent.axisToString(axis).removePrefix("AXIS_"),
            code = axis,
            action = if (value > 0) "POS" else "NEG",
            value = value
          )
        )
      }

      // Check capture mode
      val currentCaptureLane = _capturingLane.value
      if (currentCaptureLane != null && Math.abs(value) >= 0.70f) {
        val dir = if (value > 0) 1 else -1
        val newBinding = InputBinding.Axis(axis, dir, MotionEvent.axisToString(axis))
        applyNewBinding(currentCaptureLane, newBinding)
        _capturingLane.value = null
        return true
      }

      // Process axis with hysteresis
      axisTracker.processAxis(axis, value) { checkedAxis, direction, isPressed ->
        val binding = InputBinding.Axis(checkedAxis, direction)
        val targetLane = _currentMapping.value.findLaneFor(binding)
        if (targetLane != null) {
          _gameInputEvents.tryEmit(
            GameInputEvent(
              lane = targetLane,
              isPressed = isPressed,
              timestamp = timestamp,
              source = InputSource.CONTROLLER
            )
          )
          handled = true
        }
      }
    }

    return handled
  }

  // --- Mapping Management ---

  fun startCapture(lane: GameLane) {
    _capturingLane.value = lane
  }

  fun cancelCapture() {
    _capturingLane.value = null
  }

  fun applyNewBinding(lane: GameLane, binding: InputBinding) {
    val updated = _currentMapping.value.withBindingReplaced(lane, binding)
    _currentMapping.value = updated
    mappingStore.saveMapping(updated)
  }

  fun resetMappingsToDefaults(): ControllerMapping {
    val reset = mappingStore.resetToDefaults()
    _currentMapping.value = reset
    return reset
  }

  // --- State Cleanup & Focus Loss ---

  /**
   * Clears any active input states (keys, axes) to prevent stuck receptor highlights
   * when the Activity loses focus, is paused, or a controller is disconnected.
   */
  fun clearAllPressedStates(source: InputSource = InputSource.CONTROLLER) {
    val timestamp = SystemClock.uptimeMillis()

    // Release all active keys
    if (pressedKeyCodes.isNotEmpty()) {
      for (keyCode in pressedKeyCodes.toList()) {
        val lane = findLaneForKey(keyCode)
        if (lane != null) {
          _gameInputEvents.tryEmit(
            GameInputEvent(
              lane = lane,
              isPressed = false,
              timestamp = timestamp,
              source = source
            )
          )
        }
      }
      pressedKeyCodes.clear()
    }

    // Release all active axes
    axisTracker.reset { axis, direction ->
      val binding = InputBinding.Axis(axis, direction)
      val lane = _currentMapping.value.findLaneFor(binding)
      if (lane != null) {
        _gameInputEvents.tryEmit(
          GameInputEvent(
            lane = lane,
            isPressed = false,
            timestamp = timestamp,
            source = source
          )
        )
      }
    }
  }

  fun release() {
    inputManager?.unregisterInputDeviceListener(this)
    clearAllPressedStates()
  }

  // --- Helpers ---

  private fun isMappedKeyCode(keyCode: Int): Boolean {
    val binding = InputBinding.Key(keyCode)
    return _currentMapping.value.findLaneFor(binding) != null
  }

  private fun findLaneForKey(keyCode: Int): GameLane? {
    val binding = InputBinding.Key(keyCode)
    return _currentMapping.value.findLaneFor(binding)
  }
}
