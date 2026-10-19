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
 * Confirmed default profile maps the physical Microntek USB Joystick:
 * BUTTON_1 -> LEFT
 * BUTTON_2 -> DOWN
 * BUTTON_3 -> UP
 * BUTTON_4 -> RIGHT
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

  /**
   * Returns true if the current mapping has the confirmed physical Microntek button assignments.
   */
  fun isMicrontekProfileActive(): Boolean {
    val hasBtn1 = leftBindings.any { it is InputBinding.Key && it.keyCode == KeyEvent.KEYCODE_BUTTON_1 }
    val hasBtn2 = downBindings.any { it is InputBinding.Key && it.keyCode == KeyEvent.KEYCODE_BUTTON_2 }
    val hasBtn3 = upBindings.any { it is InputBinding.Key && it.keyCode == KeyEvent.KEYCODE_BUTTON_3 }
    val hasBtn4 = rightBindings.any { it is InputBinding.Key && it.keyCode == KeyEvent.KEYCODE_BUTTON_4 }
    return hasBtn1 && hasBtn2 && hasBtn3 && hasBtn4
  }

  companion object {
    /**
     * Confirmed physical dance mat profile for Microntek USB Joystick (VID 0x0079 PID 0x0006).
     * Exact digital button mappings:
     * - LEFT: KEYCODE_BUTTON_1
     * - DOWN: KEYCODE_BUTTON_2
     * - UP: KEYCODE_BUTTON_3
     * - RIGHT: KEYCODE_BUTTON_4
     */
    fun createMicrontekDefaults(): ControllerMapping {
      return ControllerMapping(
        leftBindings = listOf(
          InputBinding.Key(KeyEvent.KEYCODE_BUTTON_1, "KEYCODE_BUTTON_1")
        ),
        downBindings = listOf(
          InputBinding.Key(KeyEvent.KEYCODE_BUTTON_2, "KEYCODE_BUTTON_2")
        ),
        upBindings = listOf(
          InputBinding.Key(KeyEvent.KEYCODE_BUTTON_3, "KEYCODE_BUTTON_3")
        ),
        rightBindings = listOf(
          InputBinding.Key(KeyEvent.KEYCODE_BUTTON_4, "KEYCODE_BUTTON_4")
        )
      )
    }

    fun createDefaults(): ControllerMapping = createMicrontekDefaults()
  }
}
