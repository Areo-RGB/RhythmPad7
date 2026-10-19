package com.example.core.input

/**
 * Handles threshold and hysteresis tracking for analog and D-pad axes
 * (e.g. AXIS_X, AXIS_Y, AXIS_HAT_X, AXIS_HAT_Y).
 *
 * Prevents jitter around the threshold by requiring the value to drop
 * below the release threshold before a release event is emitted.
 */
class AxisTracker(
  val pressThreshold: Float = 0.50f,
  val releaseThreshold: Float = 0.25f
) {
  init {
    require(pressThreshold > releaseThreshold) {
      "Press threshold must be greater than release threshold for hysteresis"
    }
  }

  // Key: Pair(axisId, direction) -> isCurrentlyPressed
  private val activeStates = mutableMapOf<Pair<Int, Int>, Boolean>()

  /**
   * Processes an axis value update.
   *
   * @param axis The MotionEvent axis code (e.g. AXIS_X, AXIS_HAT_Y)
   * @param value The current axis value between -1.0f and +1.0f
   * @param onStateChanged Callback invoked when an axis direction transitions
   *                       (axis, direction, isPressed)
   */
  fun processAxis(
    axis: Int,
    value: Float,
    onStateChanged: (axis: Int, direction: Int, isPressed: Boolean) -> Unit
  ) {
    // Check negative direction (-1)
    val negKey = Pair(axis, -1)
    val negPressed = activeStates[negKey] ?: false
    if (!negPressed && value <= -pressThreshold) {
      activeStates[negKey] = true
      onStateChanged(axis, -1, true)
    } else if (negPressed && value >= -releaseThreshold) {
      activeStates[negKey] = false
      onStateChanged(axis, -1, false)
    }

    // Check positive direction (+1)
    val posKey = Pair(axis, 1)
    val posPressed = activeStates[posKey] ?: false
    if (!posPressed && value >= pressThreshold) {
      activeStates[posKey] = true
      onStateChanged(axis, 1, true)
    } else if (posPressed && value <= releaseThreshold) {
      activeStates[posKey] = false
      onStateChanged(axis, 1, false)
    }
  }

  fun isPressed(axis: Int, direction: Int): Boolean {
    return activeStates[Pair(axis, direction)] ?: false
  }

  /**
   * Clears all active axis states and emits release for any that were pressed.
   */
  fun reset(onRelease: ((axis: Int, direction: Int) -> Unit)? = null) {
    activeStates.forEach { (key, isPressed) ->
      if (isPressed) {
        onRelease?.invoke(key.first, key.second)
      }
    }
    activeStates.clear()
  }
}
