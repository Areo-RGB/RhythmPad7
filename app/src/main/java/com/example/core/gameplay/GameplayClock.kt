package com.example.core.gameplay

import android.os.SystemClock

/**
 * Authoritative monotonic gameplay clock for RhythmPad7 v0.3.0.
 *
 * Uses Android's monotonic uptime ([SystemClock.uptimeMillis]) as the single source of truth
 * and derives elapsed gameplay time directly from clock anchors rather than accumulating
 * truncated integer frame deltas.
 *
 * Key guarantees:
 * - Zero cumulative rounding error at 60 Hz, 90 Hz, 120 Hz, and 144 Hz refresh rates.
 * - Frame stalls never permanently slow down note scrolling or desynchronize the chart.
 * - Pause and resume preserve exact millisecond offsets.
 * - Physical dance-mat [android.view.KeyEvent.getEventTime] timestamps convert directly
 *   into gameplay-clock coordinates so delayed rendering frames cannot alter hit judgments.
 */
class GameplayClock(
  private val uptimeMillisProvider: () -> Long = { SystemClock.uptimeMillis() }
) {
  var startUptimeMs: Long = 0L
    private set

  var accumulatedPauseDurationMs: Long = 0L
    private set

  var pausedAtUptimeMs: Long? = null
    private set

  var isStarted: Boolean = false
    private set

  val isPaused: Boolean
    get() = pausedAtUptimeMs != null

  /**
   * Returns the current monotonic uptime in milliseconds from the underlying time source.
   */
  fun nowUptimeMs(): Long = uptimeMillisProvider()

  /**
   * Starts or restarts the gameplay clock at [atUptimeMs], anchoring gameplay time 0 ms.
   */
  fun start(atUptimeMs: Long = uptimeMillisProvider()) {
    startUptimeMs = atUptimeMs
    accumulatedPauseDurationMs = 0L
    pausedAtUptimeMs = null
    isStarted = true
  }

  /**
   * Freezes the gameplay clock at [atUptimeMs].
   */
  fun pause(atUptimeMs: Long = uptimeMillisProvider()) {
    if (!isStarted || pausedAtUptimeMs != null) return
    pausedAtUptimeMs = atUptimeMs.coerceAtLeast(startUptimeMs + accumulatedPauseDurationMs)
  }

  /**
   * Resumes the gameplay clock at [atUptimeMs], adding the exact paused interval
   * to [accumulatedPauseDurationMs].
   */
  fun resume(atUptimeMs: Long = uptimeMillisProvider()) {
    if (!isStarted) return
    val pausedAt = pausedAtUptimeMs ?: return
    val pauseDuration = (atUptimeMs - pausedAt).coerceAtLeast(0L)
    accumulatedPauseDurationMs += pauseDuration
    pausedAtUptimeMs = null
  }

  /**
   * Stops the gameplay clock.
   */
  fun stop() {
    isStarted = false
    pausedAtUptimeMs = null
  }

  /**
   * Computes elapsed gameplay time in milliseconds directly from the clock anchors at [atUptimeMs].
   */
  fun elapsedGameplayMs(atUptimeMs: Long = uptimeMillisProvider()): Long {
    if (!isStarted) return 0L
    val effectiveUptime = pausedAtUptimeMs ?: atUptimeMs
    return (effectiveUptime - startUptimeMs - accumulatedPauseDurationMs).coerceAtLeast(0L)
  }

  /**
   * Converts a physical input event timestamp ([android.view.KeyEvent.getEventTime], in Android
   * uptime milliseconds) into gameplay-clock coordinates.
   *
   * If [eventUptimeMs] is <= 0L, falls back to [elapsedGameplayMs] at [nowUptimeMs].
   */
  fun toGameplayTimeMs(
    eventUptimeMs: Long,
    nowUptimeMs: Long = uptimeMillisProvider()
  ): Long {
    if (!isStarted) return 0L
    if (eventUptimeMs <= 0L) {
      return elapsedGameplayMs(nowUptimeMs)
    }
    val effectiveUptime = pausedAtUptimeMs ?: eventUptimeMs
    return (effectiveUptime - startUptimeMs - accumulatedPauseDurationMs).coerceAtLeast(0L)
  }

  /**
   * Moves the anchor by [deltaMs] so elapsed gameplay time drops by [deltaMs]
   * (positive = clock was ahead of the music). Used to lock the clock to audio playback;
   * elapsed time is still derived from monotonic uptime anchors, never accumulated.
   */
  fun nudge(deltaMs: Long) {
    if (!isStarted || deltaMs == 0L) return
    startUptimeMs += deltaMs
  }

  /**
   * Anchors the clock so that direct test timestamps (relative to 0 ms) map 1:1 with gameplay time.
   */
  fun anchorZeroForManualStep() {
    if (!isStarted) {
      isStarted = true
    }
    startUptimeMs = 0L
    accumulatedPauseDurationMs = 0L
  }
}
