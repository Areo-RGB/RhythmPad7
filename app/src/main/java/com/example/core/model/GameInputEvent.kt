package com.example.core.model

/**
 * Normalized gameplay input event consumed by the rhythm engine.
 */
data class GameInputEvent(
  val lane: GameLane,
  val isPressed: Boolean,
  val timestamp: Long,
  val source: InputSource
) {
  val actionName: String
    get() = if (isPressed) "PRESSED" else "RELEASED"
}
