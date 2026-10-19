package com.example.core.model

import android.view.KeyEvent
import android.view.MotionEvent

/**
 * Representation of an input trigger (button keycode or joystick/hat axis direction).
 */
sealed interface InputBinding {
  val description: String

  data class Key(val keyCode: Int, val label: String = KeyEvent.keyCodeToString(keyCode)) : InputBinding {
    override val description: String = label.removePrefix("KEYCODE_")

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is Key) return false
      return keyCode == other.keyCode
    }

    override fun hashCode(): Int = keyCode
  }

  data class Axis(
    val axis: Int,
    val direction: Int, // -1 for negative, +1 for positive
    val label: String = MotionEvent.axisToString(axis)
  ) : InputBinding {
    override val description: String =
      "${label.removePrefix("AXIS_")} ${if (direction < 0) "(-)" else "(+)"}"

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is Axis) return false
      return axis == other.axis && direction == other.direction
    }

    override fun hashCode(): Int = 31 * axis + direction
  }

  fun serialize(): String {
    return when (this) {
      is Key -> "KEY:$keyCode:$label"
      is Axis -> "AXIS:$axis:$direction:$label"
    }
  }

  companion object {
    fun deserialize(value: String): InputBinding? {
      val parts = value.split(":")
      if (parts.isEmpty()) return null
      return try {
        when (parts[0]) {
          "KEY" -> {
            val keyCode = parts[1].toInt()
            val label = parts.getOrNull(2) ?: KeyEvent.keyCodeToString(keyCode)
            Key(keyCode, label)
          }
          "AXIS" -> {
            val axis = parts[1].toInt()
            val dir = parts[2].toInt()
            val label = parts.getOrNull(3) ?: MotionEvent.axisToString(axis)
            Axis(axis, dir, label)
          }
          else -> null
        }
      } catch (e: Exception) {
        null
      }
    }
  }
}

/**
 * Holds active bindings for each lane.
 * Multiple bindings per lane can be supported (e.g. D-Pad Left AND Button X AND Keyboard Left Arrow).
 */
data class ControllerMapping(
  val leftBindings: List<InputBinding>,
  val downBindings: List<InputBinding>,
  val upBindings: List<InputBinding>,
  val rightBindings: List<InputBinding>
) {
  fun bindingsFor(lane: GameLane): List<InputBinding> = when (lane) {
    GameLane.LEFT -> leftBindings
    GameLane.DOWN -> downBindings
    GameLane.UP -> upBindings
    GameLane.RIGHT -> rightBindings
  }

  fun withBindingReplaced(lane: GameLane, newBinding: InputBinding): ControllerMapping {
    // Replace primary binding or set single primary binding
    val updated = listOf(newBinding)
    return when (lane) {
      GameLane.LEFT -> copy(leftBindings = updated)
      GameLane.DOWN -> copy(downBindings = updated)
      GameLane.UP -> copy(upBindings = updated)
      GameLane.RIGHT -> copy(rightBindings = updated)
    }
  }

  fun withBindingAdded(lane: GameLane, newBinding: InputBinding): ControllerMapping {
    val current = bindingsFor(lane).filterNot { it == newBinding }
    val updated = current + newBinding
    return when (lane) {
      GameLane.LEFT -> copy(leftBindings = updated)
      GameLane.DOWN -> copy(downBindings = updated)
      GameLane.UP -> copy(upBindings = updated)
      GameLane.RIGHT -> copy(rightBindings = updated)
    }
  }

  fun findLaneFor(binding: InputBinding): GameLane? {
    if (leftBindings.contains(binding)) return GameLane.LEFT
    if (downBindings.contains(binding)) return GameLane.DOWN
    if (upBindings.contains(binding)) return GameLane.UP
    if (rightBindings.contains(binding)) return GameLane.RIGHT
    return null
  }

  companion object {
    /**
     * Sensible default mappings covering standard Android gamepads, D-pads,
     * generic USB dance mats (VID 0x0079 PID 0x0006), and physical keyboard arrows.
     */
    fun createDefaults(): ControllerMapping {
      return ControllerMapping(
        leftBindings = listOf(
          InputBinding.Key(KeyEvent.KEYCODE_DPAD_LEFT, "KEYCODE_DPAD_LEFT"),
          InputBinding.Axis(MotionEvent.AXIS_HAT_X, -1, "AXIS_HAT_X"),
          InputBinding.Axis(MotionEvent.AXIS_X, -1, "AXIS_X"),
          InputBinding.Key(KeyEvent.KEYCODE_BUTTON_X, "KEYCODE_BUTTON_X"),
          InputBinding.Key(KeyEvent.KEYCODE_D, "KEYCODE_D") // Keyboard fallback
        ),
        downBindings = listOf(
          InputBinding.Key(KeyEvent.KEYCODE_DPAD_DOWN, "KEYCODE_DPAD_DOWN"),
          InputBinding.Axis(MotionEvent.AXIS_HAT_Y, 1, "AXIS_HAT_Y"),
          InputBinding.Axis(MotionEvent.AXIS_Y, 1, "AXIS_Y"),
          InputBinding.Key(KeyEvent.KEYCODE_BUTTON_A, "KEYCODE_BUTTON_A"),
          InputBinding.Key(KeyEvent.KEYCODE_F, "KEYCODE_F") // Keyboard fallback
        ),
        upBindings = listOf(
          InputBinding.Key(KeyEvent.KEYCODE_DPAD_UP, "KEYCODE_DPAD_UP"),
          InputBinding.Axis(MotionEvent.AXIS_HAT_Y, -1, "AXIS_HAT_Y"),
          InputBinding.Axis(MotionEvent.AXIS_Y, -1, "AXIS_Y"),
          InputBinding.Key(KeyEvent.KEYCODE_BUTTON_Y, "KEYCODE_BUTTON_Y"),
          InputBinding.Key(KeyEvent.KEYCODE_J, "KEYCODE_J") // Keyboard fallback
        ),
        rightBindings = listOf(
          InputBinding.Key(KeyEvent.KEYCODE_DPAD_RIGHT, "KEYCODE_DPAD_RIGHT"),
          InputBinding.Axis(MotionEvent.AXIS_HAT_X, 1, "AXIS_HAT_X"),
          InputBinding.Axis(MotionEvent.AXIS_X, 1, "AXIS_X"),
          InputBinding.Key(KeyEvent.KEYCODE_BUTTON_B, "KEYCODE_BUTTON_B"),
          InputBinding.Key(KeyEvent.KEYCODE_K, "KEYCODE_K") // Keyboard fallback
        )
      )
    }
  }
}
