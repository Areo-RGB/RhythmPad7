package com.example.core.model

/**
 * Normalized gameplay input event consumed by the rhythm engine.
 * [inputId] uniquely identifies the specific physical button, axis deflection,
 * or simulated control that triggered the event, enabling independent tracking
 * of simultaneous and overlapping inputs for the same lane.
 */
data class GameInputEvent(
  val lane: GameLane,
  val isPressed: Boolean,
  val timestamp: Long,
  val source: InputSource,
  val inputId: String = "${source.name}_${lane.name}"
) {
  val actionName: String
    get() = if (isPressed) "PRESSED" else "RELEASED"
}
