package com.example.core.state

import com.example.core.model.GameInputEvent
import com.example.core.model.GameLane

/**
 * Immutable state representing current playfield lane states, press counters,
 * and recent input event telemetry.
 */
data class GameInputState(
  val lanePressed: Map<GameLane, Boolean> = GameLane.entries.associateWith { false },
  val pressCounts: Map<GameLane, Int> = GameLane.entries.associateWith { 0 },
  val lastInputTimestamps: Map<GameLane, Long> = GameLane.entries.associateWith { 0L },
  val recentEvents: List<GameInputEvent> = emptyList(),
  val activeSource: String = "IDLE"
) {
  val isAnyLanePressed: Boolean
    get() = lanePressed.values.any { it }

  fun isLanePressed(lane: GameLane): Boolean = lanePressed[lane] ?: false

  fun getPressCount(lane: GameLane): Int = pressCounts[lane] ?: 0

  /**
   * Applies a new GameInputEvent to produce the next state:
   * - Updates pressed state for the specific lane
   * - Increments press count only on transition to pressed
   * - Updates timestamp
   * - Appends to bounded event history (max 25 entries)
   */
  fun applyEvent(event: GameInputEvent, maxHistory: Int = 25): GameInputState {
    val currentlyPressed = lanePressed[event.lane] ?: false
    val newPressCount = if (event.isPressed && !currentlyPressed) {
      (pressCounts[event.lane] ?: 0) + 1
    } else {
      pressCounts[event.lane] ?: 0
    }

    val updatedLanes = lanePressed.toMutableMap().apply { put(event.lane, event.isPressed) }
    val updatedCounts = pressCounts.toMutableMap().apply { put(event.lane, newPressCount) }
    val updatedTimestamps = lastInputTimestamps.toMutableMap().apply { put(event.lane, event.timestamp) }

    val updatedRecent = (listOf(event) + recentEvents).take(maxHistory)

    return copy(
      lanePressed = updatedLanes,
      pressCounts = updatedCounts,
      lastInputTimestamps = updatedTimestamps,
      recentEvents = updatedRecent,
      activeSource = event.source.name
    )
  }

  /**
   * Clears all pressed states (e.g. on focus loss, pause, or disconnect)
   * while keeping press counts intact.
   */
  fun clearPressedStates(): GameInputState {
    return copy(
      lanePressed = GameLane.entries.associateWith { false },
      activeSource = "IDLE"
    )
  }

  /**
   * Complete reset of both pressed states and counters.
   */
  fun resetAll(): GameInputState {
    return GameInputState()
  }
}
