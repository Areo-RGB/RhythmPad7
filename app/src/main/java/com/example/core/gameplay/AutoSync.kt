package com.example.core.gameplay

import kotlin.math.sqrt

/**
 * Machine auto-sync modeled on StepMania/ITGmania `AdjustSync::AutosyncOffset`:
 * collect [sampleCount] tap timing errors; if their standard deviation is below
 * [maxStdDevMs] the player is consistent, so the mean error is the input/audio lag
 * and gets added to the global offset. Otherwise the batch is discarded.
 */
class AutoSync(
  private val sampleCount: Int = SAMPLE_COUNT,
  private val maxStdDevMs: Double = MAX_STDDEV_MS
) {
  sealed class Result {
    /** Mean error (ms, positive = late) to add to the offset. */
    data class Applied(val correctionMs: Double, val stdDevMs: Double) : Result()
    data class Rejected(val stdDevMs: Double) : Result()
  }

  private val samples = ArrayList<Double>(sampleCount)

  val collected: Int get() = samples.size

  /** Adds one signed timing error (ms); returns a result once a full batch is collected. */
  fun addSample(errorMs: Double): Result? {
    samples += errorMs
    if (samples.size < sampleCount) return null
    val mean = samples.average()
    val stdDev = sqrt(samples.sumOf { (it - mean) * (it - mean) } / samples.size)
    samples.clear()
    return if (stdDev < maxStdDevMs) Result.Applied(mean, stdDev) else Result.Rejected(stdDev)
  }

  fun reset() = samples.clear()

  companion object {
    const val SAMPLE_COUNT = 24
    const val MAX_STDDEV_MS = 30.0
  }
}
