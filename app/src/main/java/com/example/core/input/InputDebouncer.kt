package com.example.core.input

/**
 * Per-button debounce modeled on StepMania/ITGmania `InputFilter` (`InputDebounceTime`, default 20 ms).
 *
 * After a state change is reported for a button, further changes are held back for [windowMs].
 * When the window expires, the button's latest raw state is reported if it differs from the last
 * reported one. Consequences:
 * - A press is reported immediately, so taps never lose latency.
 * - A release that is followed by a re-press within the window (contact chatter) is never reported,
 *   so one stomp can't count twice and a held arrow doesn't flicker off.
 * - A real quick tap (press, release) is still reported: the release just arrives at the window's end.
 *
 * Delayed reports carry the raw event's own timestamp, so judgments stay exact.
 * Not thread-safe: call from one thread (the main looper).
 */
class InputDebouncer<K>(
  private val windowMs: Long = DEFAULT_WINDOW_MS,
  /** Schedules [check] to run at uptime [atMs]. */
  private val schedule: (atMs: Long, check: () -> Unit) -> Unit,
  /** Receives debounced state changes: (key, pressed, timestampMs). */
  private val report: (key: K, pressed: Boolean, timestampMs: Long) -> Unit
) {
  private class ButtonState(
    var rawPressed: Boolean = false,
    var rawChangedAtMs: Long = 0L,
    var reportedPressed: Boolean = false,
    var lastReportAtMs: Long = Long.MIN_VALUE / 2,
    var checkScheduled: Boolean = false
  )

  private val buttons = HashMap<K, ButtonState>()

  fun onRawEvent(key: K, pressed: Boolean, timestampMs: Long) {
    val state = buttons.getOrPut(key) { ButtonState() }
    if (state.rawPressed == pressed) return
    state.rawPressed = pressed
    state.rawChangedAtMs = timestampMs

    if (timestampMs - state.lastReportAtMs >= windowMs) {
      reportIfChanged(key, state)
    } else if (!state.checkScheduled) {
      state.checkScheduled = true
      schedule(state.lastReportAtMs + windowMs) {
        state.checkScheduled = false
        reportIfChanged(key, state)
      }
    }
  }

  /** Forgets all buttons (e.g. on focus loss) without reporting anything. */
  fun reset() = buttons.clear()

  private fun reportIfChanged(key: K, state: ButtonState) {
    if (state.rawPressed == state.reportedPressed) return
    state.reportedPressed = state.rawPressed
    state.lastReportAtMs = state.rawChangedAtMs
    report(key, state.rawPressed, state.rawChangedAtMs)
  }

  companion object {
    const val DEFAULT_WINDOW_MS = 20L
  }
}
