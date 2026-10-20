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
import com.example.core.model.MenuButton
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
 * Discovers controllers, prioritizes the confirmed Microntek USB Joystick (VID 0x0079 PID 0x0006),
 * enforces strict device ID filtering so unrelated keyboards/devices cannot interfere,
 * protects Android system keys (Back, Volume) from capture or interception,
 * and emits normalized GameInputEvents for digital BUTTON_1..4 dance mat panels.
 */
class ControllerManager(
  private val context: Context,
  private val mappingStore: ControllerMappingStore
) : InputManager.InputDeviceListener {

  private val inputManager = context.getSystemService(Context.INPUT_SERVICE) as? InputManager

  // State: Connected controller info (strictly prioritizes Microntek dance mat)
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

  // Mat menu buttons (Start/Select/Circle/Cross/Square/Triangle), fresh presses only
  private val _menuButtons = MutableSharedFlow<MenuButton>(extraBufferCapacity = 16)
  val menuButtons: SharedFlow<MenuButton> = _menuButtons.asSharedFlow()

  // Tracks currently held keycodes to eliminate duplicate ACTION_DOWN repeats
  private val pressedKeyCodes = mutableSetOf<Int>()

  // Mapping capture state: when non-null, the next valid input on the target controller will bind to this lane
  private val _capturingLane = MutableStateFlow<GameLane?>(null)
  val capturingLane: StateFlow<GameLane?> = _capturingLane.asStateFlow()

  init {
    inputManager?.registerInputDeviceListener(this, null)
    scanForControllers()
  }

  /**
   * Scans all connected input devices and prioritizes the confirmed Microntek USB Joystick
   * (Vendor ID 0x0079, Product ID 0x0006) over generic gamepads or keyboards.
   */
  fun scanForControllers() {
    val deviceIds = InputDevice.getDeviceIds()
    var bestController: ControllerInfo? = null
    var bestScore = -1

    for (id in deviceIds) {
      val device = InputDevice.getDevice(id) ?: continue
      val sources = device.sources
      val isGamepad = (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
      val isJoystick = (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
      val isDpad = (sources and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD

      val isGenericController = isGamepad || isJoystick || isDpad
      val hasVendorProduct = !device.isVirtual && (device.vendorId != 0 || device.productId != 0)

      if (!isGenericController && !hasVendorProduct) continue

      val isExactMicrontek = (device.vendorId == ControllerInfo.DANCE_MAT_VENDOR_ID &&
          device.productId == ControllerInfo.DANCE_MAT_PRODUCT_ID)
      val hasMicrontekName = device.name?.contains("microntek", ignoreCase = true) == true
      val hasDanceName = device.name?.contains("dance", ignoreCase = true) == true ||
          device.name?.contains("mat", ignoreCase = true) == true

      // Ranking priorities:
      // 100: Exact Microntek USB Joystick (0079:0006)
      // 80: Name matches "microntek", "dance" or "mat"
      // 50: Dedicated Gamepad
      // 40: Dedicated Joystick
      // 20: D-pad device
      var score = 10
      if (isExactMicrontek) score = 100
      else if (hasMicrontekName || hasDanceName) score = 80
      else if (isGamepad) score = 50
      else if (isJoystick) score = 40
      else if (isDpad) score = 20

      if (score > bestScore) {
        bestScore = score
        val isMat = isExactMicrontek || hasMicrontekName || hasDanceName
        bestController = ControllerInfo(
          isConnected = true,
          deviceId = device.id,
          name = device.name ?: ControllerInfo.CONFIRMED_CONTROLLER_NAME,
          vendorId = device.vendorId,
          productId = device.productId,
          descriptor = device.descriptor ?: "",
          isDanceMat = isMat,
          isMicrontekProfileActive = _currentMapping.value.isMicrontekProfileActive()
        )
      }
    }

    _controllerInfo.value = bestController ?: ControllerInfo.DISCONNECTED
  }

  // --- InputManager.InputDeviceListener ---

  override fun onInputDeviceAdded(deviceId: Int) {
    scanForControllers()
  }

  override fun onInputDeviceRemoved(deviceId: Int) {
    if (_controllerInfo.value.deviceId == deviceId) {
      clearAllPressedStates(InputSource.CONTROLLER)
      _controllerInfo.value = ControllerInfo.DISCONNECTED
      scanForControllers()
    }
  }

  override fun onInputDeviceChanged(deviceId: Int) {
    scanForControllers()
  }

  // --- Raw Android Event Dispatching & Device ID Filtering ---

  /**
   * Processes a KeyEvent from MainActivity.
   * Restricts processing strictly to the designated controller device ID
   * and ignores system keys (Back, Volume, Home).
   */
  fun dispatchKeyEvent(event: KeyEvent): Boolean {
    val keyCode = event.keyCode
    val action = event.action

    // Never intercept or consume Android system keys (Back, Volume, Home, Power)
    if (isSystemKey(keyCode)) {
      return false
    }

    val active = _controllerInfo.value

    // Device ID restriction:
    if (active.isConnected) {
      // Reject any event from a different device ID
      if (event.deviceId != active.deviceId) {
        return false
      }
    } else {
      // When no controller is connected, reject non-controller hardware (e.g. general keyboards)
      // while allowing virtual or test runners (where deviceId <= 0 or device is virtual)
      val dev = event.device
      if (dev != null && !dev.isVirtual && event.deviceId > 0) {
        val sources = dev.sources
        val isControllerSource = (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
            (sources and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD
        if (!isControllerSource) {
          return false
        }
      }
    }

    // Annotate physical button names for clear telemetry feedback
    val rawAction = if (action == KeyEvent.ACTION_DOWN) "DOWN" else "UP"
    val friendlyName = getFriendlyButtonName(keyCode)

    _rawInputEvents.tryEmit(
      RawInputEvent(
        type = "BUTTON",
        name = friendlyName,
        code = keyCode,
        action = rawAction,
        value = if (action == KeyEvent.ACTION_DOWN) 1f else 0f
      )
    )

    // Handle mapping capture mode (reject system keys already handled above)
    val currentCaptureLane = _capturingLane.value
    if (currentCaptureLane != null && action == KeyEvent.ACTION_DOWN) {
      val newBinding = InputBinding.Key(keyCode, KeyEvent.keyCodeToString(keyCode))
      applyNewBinding(currentCaptureLane, newBinding)
      _capturingLane.value = null
      return true
    }

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
              source = InputSource.CONTROLLER,
              inputId = "KEY_$keyCode"
            )
          )
          return true
        }
        val menuButton = MenuButton.fromKeyCode(keyCode)
        if (menuButton != null) {
          _menuButtons.tryEmit(menuButton)
          return true
        }
      }

      KeyEvent.ACTION_UP -> {
        pressedKeyCodes.remove(keyCode)
        if (findLaneForKey(keyCode) == null && MenuButton.fromKeyCode(keyCode) != null) return true
        val targetLane = findLaneForKey(keyCode)
        if (targetLane != null) {
          _gameInputEvents.tryEmit(
            GameInputEvent(
              lane = targetLane,
              isPressed = false,
              timestamp = timestamp,
              source = InputSource.CONTROLLER,
              inputId = "KEY_$keyCode"
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
   * Note: The 4 gameplay arrows are independent digital buttons (BUTTON_1..4).
   * The CENTER sensor reports on positive AXIS_Y (> 0.5) and is monitored
   * for telemetry without triggering gameplay arrows.
   */
  fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
    val active = _controllerInfo.value

    if (active.isConnected) {
      if (event.deviceId != active.deviceId) {
        return false
      }
    } else {
      val dev = event.device
      if (dev != null && !dev.isVirtual && event.deviceId > 0) {
        val sources = dev.sources
        val isControllerSource = (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
            (sources and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD
        if (!isControllerSource) {
          return false
        }
      }
    }

    // Monitor CENTER panel sensor on positive AXIS_Y (> 0.5)
    val axisY = event.getAxisValue(MotionEvent.AXIS_Y)
    if (axisY > 0.5f) {
      _rawInputEvents.tryEmit(
        RawInputEvent(
          type = "SENSOR",
          name = "CENTER (AXIS_Y > 0.5)",
          code = MotionEvent.AXIS_Y,
          action = "ACTIVE",
          value = axisY
        )
      )
    }

    return false
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
    updateMicrontekProfileStatus()
  }

  fun resetMappingsToDefaults(): ControllerMapping {
    val reset = mappingStore.resetToDefaults()
    _currentMapping.value = reset
    updateMicrontekProfileStatus()
    return reset
  }

  private fun updateMicrontekProfileStatus() {
    val current = _controllerInfo.value
    if (current.isConnected) {
      _controllerInfo.value = current.copy(
        isMicrontekProfileActive = _currentMapping.value.isMicrontekProfileActive()
      )
    }
  }

  // --- State Cleanup & Focus Loss ---

  fun clearAllPressedStates(source: InputSource = InputSource.CONTROLLER) {
    val timestamp = SystemClock.uptimeMillis()

    if (pressedKeyCodes.isNotEmpty()) {
      for (keyCode in pressedKeyCodes.toList()) {
        val lane = findLaneForKey(keyCode)
        if (lane != null) {
          _gameInputEvents.tryEmit(
            GameInputEvent(
              lane = lane,
              isPressed = false,
              timestamp = timestamp,
              source = source,
              inputId = "KEY_$keyCode"
            )
          )
        }
      }
      pressedKeyCodes.clear()
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

  /**
   * System keys that should never be intercepted as controller inputs or captured during remapping.
   */
  private fun isSystemKey(keyCode: Int): Boolean {
    return when (keyCode) {
      KeyEvent.KEYCODE_BACK,
      KeyEvent.KEYCODE_VOLUME_UP,
      KeyEvent.KEYCODE_VOLUME_DOWN,
      KeyEvent.KEYCODE_VOLUME_MUTE,
      KeyEvent.KEYCODE_HOME,
      KeyEvent.KEYCODE_APP_SWITCH,
      KeyEvent.KEYCODE_POWER,
      KeyEvent.KEYCODE_MENU,
      KeyEvent.KEYCODE_NOTIFICATION -> true
      else -> false
    }
  }

  /**
   * Human-friendly label for physical dance mat panels confirmed on Microntek joystick.
   */
  private fun getFriendlyButtonName(keyCode: Int): String {
    return when (keyCode) {
      KeyEvent.KEYCODE_BUTTON_1 -> "BUTTON_1 (LEFT)"
      KeyEvent.KEYCODE_BUTTON_2 -> "BUTTON_2 (DOWN)"
      KeyEvent.KEYCODE_BUTTON_3 -> "BUTTON_3 (UP)"
      KeyEvent.KEYCODE_BUTTON_4 -> "BUTTON_4 (RIGHT)"
      KeyEvent.KEYCODE_BUTTON_5 -> "BUTTON_5 (SQUARE)"
      KeyEvent.KEYCODE_BUTTON_6 -> "BUTTON_6 (TRIANGLE)"
      KeyEvent.KEYCODE_BUTTON_7 -> "BUTTON_7 (CROSS)"
      KeyEvent.KEYCODE_BUTTON_8 -> "BUTTON_8 (CIRCLE)"
      KeyEvent.KEYCODE_BUTTON_9 -> "BUTTON_9 (SELECT)"
      KeyEvent.KEYCODE_BUTTON_10 -> "BUTTON_10 (START)"
      else -> KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
    }
  }
}
