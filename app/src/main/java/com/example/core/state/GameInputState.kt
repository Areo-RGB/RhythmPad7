package com.example.core.state

import com.example.core.model.GameInputEvent
import com.example.core.model.GameLane

/**
 * Immutable state representing current playfield lane states, active input holders,
 * press counters, and recent input event telemetry.
 *
 * Rather than a raw boolean per lane, active input bindings are tracked separately
 * per lane (e.g. multiple hardware buttons or simultaneous touch + controller),
 * and the pressed state of each lane is derived from whether any input is holding it.
 */
data class GameInputState(
  val activeHolders: Map<GameLane, Set<String>> = GameLane.entries.associateWith { emptySet() },
  val pressCounts: Map<GameLane, Int> = GameLane.entries.associateWith { 0 },
  val lastInputTimestamps: Map<GameLane, Long> = GameLane.entries.associateWith { 0L },
  val recentEvents: List<GameInputEvent> = emptyList(),
  val activeSource: String = "IDLE"
) {
  /**
   * Derived: A lane is pressed if and only if at least one input holder is active.
   */
  fun isLanePressed(lane: GameLane): Boolean =
    activeHolders[lane]?.isNotEmpty() == true

  val lanePressed: Map<GameLane, Boolean>
    get() = GameLane.entries.associateWith { isLanePressed(it) }

  val isAnyLanePressed: Boolean
    get() = activeHolders.values.any { it.isNotEmpty() }

  fun getPressCount(lane: GameLane): Int = pressCounts[lane] ?: 0

  fun getActiveHoldersFor(lane: GameLane): Set<String> = activeHolders[lane] ?: emptySet()

  /**
   * Applies an input event by tracking the specific inputId.
   * If a second source presses the lane, it is added to activeHolders.
   * If one source releases the lane, other active sources keep the lane pressed.
   * Press count increments ONLY on transition from unpressed (0 holders) to pressed (>= 1 holder).
   */
  fun applyEvent(event: GameInputEvent, maxHistory: Int = 25): GameInputState {
    val currentHolders = activeHolders[event.lane] ?: emptySet()
    val wasPressed = currentHolders.isNotEmpty()

    val updatedHolders = if (event.isPressed) {
      currentHolders + event.inputId
    } else {
      currentHolders - event.inputId
    }

    val isNowPressed = updatedHolders.isNotEmpty()

    // Increment press count only when the lane transitions from fully unpressed to pressed
    val newPressCount = if (!wasPressed && isNowPressed) {
      (pressCounts[event.lane] ?: 0) + 1
    } else {
      pressCounts[event.lane] ?: 0
    }

    val updatedHoldersMap = activeHolders.toMutableMap().apply {
      put(event.lane, updatedHolders)
    }
    val updatedCounts = pressCounts.toMutableMap().apply {
      put(event.lane, newPressCount)
    }
    val updatedTimestamps = lastInputTimestamps.toMutableMap().apply {
      put(event.lane, event.timestamp)
    }
    val updatedRecent = (listOf(event) + recentEvents).take(maxHistory)

    val updatedSource = if (isNowPressed) {
      event.source.name
    } else if (updatedHoldersMap.values.any { it.isNotEmpty() }) {
      activeSource
    } else {
      "IDLE"
    }

    return copy(
      activeHolders = updatedHoldersMap,
      pressCounts = updatedCounts,
      lastInputTimestamps = updatedTimestamps,
      recentEvents = updatedRecent,
      activeSource = updatedSource
    )
  }

  /**
   * Clears all active input holders (e.g. on focus loss, pause, or disconnect)
   * while keeping press statistics intact.
   */
  fun clearPressedStates(): GameInputState {
    return copy(
      activeHolders = GameLane.entries.associateWith { emptySet() },
      activeSource = "IDLE"
    )
  }

  /**
   * Complete reset of all active states and counters.
   */
  fun resetAll(): GameInputState {
    return GameInputState()
  }
}
