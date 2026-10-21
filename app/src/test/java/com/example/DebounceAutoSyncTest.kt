package com.example

import com.example.core.gameplay.AutoSync
import com.example.core.input.InputDebouncer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DebounceAutoSyncTest {
  private val pending = mutableListOf<Pair<Long, () -> Unit>>()
  private val reports = mutableListOf<Triple<Int, Boolean, Long>>()
  private val debouncer = InputDebouncer<Int>(
    windowMs = 20,
    schedule = { at, check -> pending += at to check },
    report = { k, p, t -> reports += Triple(k, p, t) }
  )

  private fun runTimersUntil(ms: Long) {
    pending.sortBy { it.first }
    while (pending.isNotEmpty() && pending.first().first <= ms) pending.removeAt(0).second()
  }

  @Test fun pressIsReportedImmediately() {
    debouncer.onRawEvent(1, true, 1000)
    assertEquals(listOf(Triple(1, true, 1000L)), reports)
  }

  @Test fun chatterReleaseAndRepressIsSwallowed() {
    debouncer.onRawEvent(1, true, 1000)
    debouncer.onRawEvent(1, false, 1005)
    debouncer.onRawEvent(1, true, 1008)
    runTimersUntil(1100)
    assertEquals(listOf(Triple(1, true, 1000L)), reports)
  }

  @Test fun quickTapReleaseIsDelayedNotLost() {
    debouncer.onRawEvent(1, true, 1000)
    debouncer.onRawEvent(1, false, 1010)
    assertEquals(1, reports.size)
    runTimersUntil(1020)
    assertEquals(Triple(1, false, 1010L), reports.last())
  }

  @Test fun separateStepsOutsideWindowBothCount() {
    debouncer.onRawEvent(1, true, 1000)
    debouncer.onRawEvent(1, false, 1100)
    debouncer.onRawEvent(1, true, 1200)
    assertEquals(3, reports.size)
  }

  @Test fun keysAreIndependent() {
    debouncer.onRawEvent(1, true, 1000)
    debouncer.onRawEvent(2, true, 1001)
    assertEquals(2, reports.size)
  }

  @Test fun autoSyncAppliesMeanWhenConsistent() {
    val sync = AutoSync()
    var result: AutoSync.Result? = null
    repeat(24) { i -> result = sync.addSample(30.0 + if (i % 2 == 0) 5 else -5) }
    val applied = result as AutoSync.Result.Applied
    assertEquals(30.0, applied.correctionMs, 0.001)
    assertEquals(0, sync.collected)
  }

  @Test fun autoSyncRejectsScatteredSteps() {
    val sync = AutoSync()
    var result: AutoSync.Result? = null
    repeat(23) { i -> assertNull(sync.addSample(if (i % 2 == 0) 80.0 else -80.0)) }
    result = sync.addSample(80.0)
    assertTrue(result is AutoSync.Result.Rejected)
  }
}
