package com.example

<<<<<<< HEAD
import com.example.core.chart.Chart
import com.example.core.chart.ChartStep
import com.example.core.chart.Difficulty
import com.example.core.chart.Song
import com.example.core.chart.StepKind
import com.example.core.chart.TimingData
import com.example.core.gameplay.FullComboKind
import com.example.core.model.GameLane
import com.example.core.scores.PRACTICE_CHART_KEY
import com.example.core.scores.ScoreRecord
import com.example.core.scores.ScoreStore
import com.example.core.scores.chartCheck
=======
import com.example.core.gameplay.FullComboKind
import com.example.core.scores.PRACTICE_CHART_KEY
import com.example.core.scores.ScoreRecord
import com.example.core.scores.ScoreStore
>>>>>>> 1d8a8080040e2bcd0e69e81b883b8e67eefabfa9
import com.example.core.scores.chartKey
import com.example.core.scores.practiceChartCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
<<<<<<< HEAD
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
=======
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
>>>>>>> 1d8a8080040e2bcd0e69e81b883b8e67eefabfa9
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
<<<<<<< HEAD
import java.io.IOException
=======
>>>>>>> 1d8a8080040e2bcd0e69e81b883b8e67eefabfa9

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ScoreStoreTest {

  @get:Rule
  val tempFolder = TemporaryFolder()

  @Test
  fun jsonRoundTripPreservesAllFields() {
    val record = ScoreRecord(
      key = "songs/springtime#HARD",
      chartCheck = "450:a1b2c3d4",
      percentDp = 93.41,
      gradeTier = 5,
      score = 1250,
      maxCombo = 210,
      w1Count = 300,
      w2Count = 100,
      w3Count = 30,
      w4Count = 10,
      w5Count = 5,
      missCount = 5,
      heldCount = 20,
      letGoCount = 2,
      fullCombo = FullComboKind.GREAT,
      scrollMod = "M120",
      dateIso = "2026-10-20",
      playCount = 7
    )

    val json = record.toJson()
    val restored = ScoreRecord.fromJson(json)

    assertEquals(record.key, restored.key)
    assertEquals(record.chartCheck, restored.chartCheck)
    assertEquals(record.percentDp, restored.percentDp, 0.001)
    assertEquals(record.gradeTier, restored.gradeTier)
    assertEquals(record.score, restored.score)
    assertEquals(record.maxCombo, restored.maxCombo)
    assertEquals(record.w1Count, restored.w1Count)
    assertEquals(record.w2Count, restored.w2Count)
    assertEquals(record.w3Count, restored.w3Count)
    assertEquals(record.w4Count, restored.w4Count)
    assertEquals(record.w5Count, restored.w5Count)
    assertEquals(record.missCount, restored.missCount)
    assertEquals(record.heldCount, restored.heldCount)
    assertEquals(record.letGoCount, restored.letGoCount)
    assertEquals(record.fullCombo, restored.fullCombo)
    assertEquals(record.scrollMod, restored.scrollMod)
    assertEquals(record.dateIso, restored.dateIso)
    assertEquals(record.playCount, restored.playCount)
  }

  @Test
  fun jsonRoundTripHandlesNullFullCombo() {
    val record = ScoreRecord(
      key = "songs/test#EASY",
      chartCheck = "100:12345678",
      percentDp = 75.0,
      gradeTier = 11,
      score = 500,
      maxCombo = 25,
      w1Count = 50,
      w2Count = 30,
      w3Count = 10,
      w4Count = 5,
      w5Count = 3,
      missCount = 2,
      heldCount = 0,
      letGoCount = 0,
      fullCombo = null,
      scrollMod = "1.5x",
      dateIso = "2026-10-20",
      playCount = 1
    )

    val json = record.toJson()
    val restored = ScoreRecord.fromJson(json)
    assertNull(restored.fullCombo)
  }

  @Test
  fun betterRecordReplacesOldBest() {
    val file = File(tempFolder.root, "scores.json")
    val store = ScoreStore(file)

    val run1 = ScoreRecord(
      key = "songs/springtime#HARD",
      chartCheck = "450:a1b2c3d4",
      percentDp = 85.50,
      gradeTier = 7,
      score = 900,
      maxCombo = 100,
      w1Count = 200,
      w2Count = 150,
      w3Count = 50,
      w4Count = 20,
      w5Count = 10,
      missCount = 20,
      heldCount = 15,
      letGoCount = 5,
      fullCombo = null,
      scrollMod = "1.0x",
      dateIso = "2026-10-20"
    )

    val res1 = store.submit(run1)
    assertTrue(res1.isNewBest)
    assertNull(res1.previousBest)
    assertEquals(1, store.getRecord("songs/springtime#HARD")?.playCount)
    assertEquals(85.50, store.getRecord("songs/springtime#HARD")?.percentDp ?: 0.0, 0.001)

    // Second run is better
    val run2 = run1.copy(percentDp = 92.10, gradeTier = 4, score = 1100)
    val res2 = store.submit(run2)
    assertTrue(res2.isNewBest)
    assertNotNull(res2.previousBest)
    assertEquals(85.50, res2.previousBest?.percentDp ?: 0.0, 0.001)
    assertEquals(2, store.getRecord("songs/springtime#HARD")?.playCount)
    assertEquals(92.10, store.getRecord("songs/springtime#HARD")?.percentDp ?: 0.0, 0.001)
  }

  @Test
  fun worseRecordKeepsOldBestAndIncrementsPlayCount() {
    val file = File(tempFolder.root, "scores.json")
    val store = ScoreStore(file)

    val run1 = ScoreRecord(
      key = "songs/springtime#HARD",
      chartCheck = "450:a1b2c3d4",
      percentDp = 90.00,
      gradeTier = 5,
      score = 1000,
      maxCombo = 150,
      w1Count = 250,
      w2Count = 100,
      w3Count = 50,
      w4Count = 10,
      w5Count = 0,
      missCount = 5,
      heldCount = 20,
      letGoCount = 0,
      fullCombo = null,
      scrollMod = "1.0x",
      dateIso = "2026-10-20"
    )
    store.submit(run1)

    // Worse run: lower DP%
    val run2 = run1.copy(percentDp = 75.00, gradeTier = 11, score = 700)
    val res2 = store.submit(run2)

    assertFalse(res2.isNewBest)
    assertNotNull(res2.previousBest)
    val currentBest = store.getRecord("songs/springtime#HARD")
    assertNotNull(currentBest)
    assertEquals(90.00, currentBest?.percentDp ?: 0.0, 0.001)
    assertEquals(2, currentBest?.playCount)
  }

  @Test
  fun equalDpTieBreakBetterGradeWins() {
    val file = File(tempFolder.root, "scores.json")
    val store = ScoreStore(file)

    // 100% DP with all W2s -> Grade Tier 2 (AAA)
    val allW2 = ScoreRecord(
      key = "songs/springtime#EASY",
      chartCheck = "100:beefcafe",
      percentDp = 100.00,
      gradeTier = 2,
      score = 300,
      maxCombo = 100,
      w1Count = 0,
      w2Count = 100,
      w3Count = 0,
      w4Count = 0,
      w5Count = 0,
      missCount = 0,
      heldCount = 0,
      letGoCount = 0,
      fullCombo = FullComboKind.PERFECT,
      scrollMod = "1.0x",
      dateIso = "2026-10-20"
    )
    store.submit(allW2)
    assertEquals(2, store.getRecord("songs/springtime#EASY")?.gradeTier)

    // 100% DP with all W1s -> Grade Tier 1 (AAAA) - tier 1 beats tier 2
    val allW1 = allW2.copy(
      gradeTier = 1,
      w1Count = 100,
      w2Count = 0,
      fullCombo = FullComboKind.FLAWLESS
    )
    val res = store.submit(allW1)

    assertTrue(res.isNewBest)
    val currentBest = store.getRecord("songs/springtime#EASY")
    assertEquals(1, currentBest?.gradeTier)
    assertEquals(2, currentBest?.playCount)

    // Subsequent equal DP% run with worse grade does NOT replace best
    val run3 = allW2
    val res3 = store.submit(run3)
    assertFalse(res3.isNewBest)
    assertEquals(1, store.getRecord("songs/springtime#EASY")?.gradeTier)
    assertEquals(3, store.getRecord("songs/springtime#EASY")?.playCount)
  }

  @Test
  fun chartCheckMismatchDropsOldRecord() {
    val file = File(tempFolder.root, "scores.json")
    val store = ScoreStore(file)

    val oldChartRun = ScoreRecord(
      key = "songs/springtime#HARD",
      chartCheck = "100:11111111",
      percentDp = 98.00,
      gradeTier = 2,
      score = 1500,
      maxCombo = 100,
      w1Count = 90,
      w2Count = 10,
      w3Count = 0,
      w4Count = 0,
      w5Count = 0,
      missCount = 0,
      heldCount = 0,
      letGoCount = 0,
      fullCombo = FullComboKind.PERFECT,
      scrollMod = "1.0x",
      dateIso = "2026-10-19",
      playCount = 10
    )
    store.submit(oldChartRun)

    // Chart was edited; hash is now different
    val newChartRun = ScoreRecord(
      key = "songs/springtime#HARD",
      chartCheck = "105:22222222",
      percentDp = 80.00,
      gradeTier = 8,
      score = 800,
      maxCombo = 50,
      w1Count = 60,
      w2Count = 20,
      w3Count = 10,
      w4Count = 5,
      w5Count = 5,
      missCount = 5,
      heldCount = 0,
      letGoCount = 0,
      fullCombo = null,
      scrollMod = "1.0x",
      dateIso = "2026-10-20"
    )

    val res = store.submit(newChartRun)
    assertTrue("Mismatching chartCheck should be treated as a new record", res.isNewBest)
    assertNull("Old record for changed chart is dropped, so previousBest is null", res.previousBest)

    val best = store.getRecord("songs/springtime#HARD")
    assertNotNull(best)
    assertEquals("105:22222222", best?.chartCheck)
    assertEquals(80.00, best?.percentDp ?: 0.0, 0.001)
    assertEquals(1, best?.playCount)
  }

  @Test
  fun corruptOrMissingFileLoadsEmptyWithoutException() {
    val missingFile = File(tempFolder.root, "does_not_exist.json")
    val store1 = ScoreStore(missingFile)
    assertTrue(store1.scores.isEmpty())

    val corruptFile = File(tempFolder.root, "corrupt.json")
    corruptFile.writeText("{ this is definitely not valid json")
    val store2 = ScoreStore(corruptFile)
    assertTrue(store2.scores.isEmpty())
  }

  @Test
  fun persistenceSurvivesReload() {
    val file = File(tempFolder.root, "scores.json")
    val store1 = ScoreStore(file)

    val run = ScoreRecord(
      key = "songs/springtime#MEDIUM",
      chartCheck = "200:33333333",
      percentDp = 91.50,
      gradeTier = 5,
      score = 850,
      maxCombo = 95,
      w1Count = 150,
      w2Count = 40,
      w3Count = 10,
      w4Count = 0,
      w5Count = 0,
      missCount = 0,
      heldCount = 5,
      letGoCount = 0,
      fullCombo = FullComboKind.GREAT,
      scrollMod = "C400",
      dateIso = "2026-10-20"
    )
    store1.submit(run)

    // Second instance loading from the same file
    val store2 = ScoreStore(file)
    val restored = store2.getRecord("songs/springtime#MEDIUM")
    assertNotNull(restored)
    assertEquals(91.50, restored?.percentDp ?: 0.0, 0.001)
    assertEquals(1, restored?.playCount)
    assertEquals(FullComboKind.GREAT, restored?.fullCombo)
    assertEquals("C400", restored?.scrollMod)
  }

  @Test
  fun practicePatternKeyAndCheck() {
    assertEquals(PRACTICE_CHART_KEY, chartKey(null, null))
    val check = practiceChartCheck()
    assertTrue("Practice check format should be noteCount:8hex, got $check", check.matches(Regex("""\d+:[0-9a-f]{8}""")))

    val file = File(tempFolder.root, "scores.json")
    val store = ScoreStore(file)
    val practiceRun = ScoreRecord(
      key = PRACTICE_CHART_KEY,
      chartCheck = check,
      percentDp = 99.12,
      gradeTier = 2,
      score = 450,
      maxCombo = 77,
      w1Count = 70,
      w2Count = 7,
      w3Count = 0,
      w4Count = 0,
      w5Count = 0,
      missCount = 0,
      heldCount = 0,
      letGoCount = 0,
      fullCombo = FullComboKind.PERFECT,
      scrollMod = "1.0x",
      dateIso = "2026-10-20"
    )
    val res = store.submit(practiceRun)
    assertTrue(res.isNewBest)
    assertEquals(practiceRun.key, store.getRecord(PRACTICE_CHART_KEY)?.key)
  }
<<<<<<< HEAD

  @Test
  fun checksumDifferentiatesTimestampsWithSameLowestByte() {
    val timing = TimingData(offsetSeconds = 0.0, bpms = listOf(0.0 to 120.0))
    val chart1 = Chart(
      difficulty = Difficulty.EASY,
      meter = 1,
      description = "",
      timing = timing,
      steps = listOf(ChartStep(beat = 0.0, timeSeconds = 1.0, lane = GameLane.LEFT)) // 1000 ms
    )
    val chart2 = Chart(
      difficulty = Difficulty.EASY,
      meter = 1,
      description = "",
      timing = timing,
      steps = listOf(ChartStep(beat = 0.0, timeSeconds = 1.256, lane = GameLane.LEFT)) // 1256 ms
    )
    val check1 = chartCheck(chart1)
    val check2 = chartCheck(chart2)
    assertNotEquals("Timestamps 1000ms and 1256ms must have different checksums", check1, check2)
  }

  @Test
  fun checksumDifferentiatesNoteKindAndHoldDuration() {
    val timing = TimingData(offsetSeconds = 0.0, bpms = listOf(0.0 to 120.0))
    val tapChart = Chart(
      difficulty = Difficulty.HARD,
      meter = 5,
      description = "",
      timing = timing,
      steps = listOf(ChartStep(beat = 0.0, timeSeconds = 1.0, lane = GameLane.UP, kind = StepKind.TAP))
    )
    val holdChart1 = Chart(
      difficulty = Difficulty.HARD,
      meter = 5,
      description = "",
      timing = timing,
      steps = listOf(ChartStep(beat = 0.0, timeSeconds = 1.0, lane = GameLane.UP, kind = StepKind.HOLD, endTimeSeconds = 2.0))
    )
    val holdChart2 = Chart(
      difficulty = Difficulty.HARD,
      meter = 5,
      description = "",
      timing = timing,
      steps = listOf(ChartStep(beat = 0.0, timeSeconds = 1.0, lane = GameLane.UP, kind = StepKind.HOLD, endTimeSeconds = 3.0))
    )
    val rollChart = Chart(
      difficulty = Difficulty.HARD,
      meter = 5,
      description = "",
      timing = timing,
      steps = listOf(ChartStep(beat = 0.0, timeSeconds = 1.0, lane = GameLane.UP, kind = StepKind.ROLL, endTimeSeconds = 2.0))
    )

    val tapCheck = chartCheck(tapChart)
    val hold1Check = chartCheck(holdChart1)
    val hold2Check = chartCheck(holdChart2)
    val rollCheck = chartCheck(rollChart)

    assertNotEquals(tapCheck, hold1Check)
    assertNotEquals(hold1Check, hold2Check)
    assertNotEquals(hold1Check, rollCheck)
  }

  @Test
  fun pruneInvalidRecordsImmediatelyRemovesMismatchingChecksums() {
    val file = File(tempFolder.root, "scores.json")
    val store = ScoreStore(file)
    val oldRecord = ScoreRecord(
      key = "songs/test#HARD",
      chartCheck = "100:old_hash",
      percentDp = 95.0,
      gradeTier = 3,
      score = 1000,
      maxCombo = 100,
      w1Count = 100,
      w2Count = 0,
      w3Count = 0,
      w4Count = 0,
      w5Count = 0,
      missCount = 0,
      heldCount = 0,
      letGoCount = 0,
      fullCombo = FullComboKind.FLAWLESS,
      scrollMod = "1.0x",
      dateIso = "2026-10-20",
      playCount = 5
    )
    store.submit(oldRecord)
    assertEquals(1, store.scores.size)

    val validChecks = mapOf(
      "songs/test#HARD" to "100:new_hash",
      PRACTICE_CHART_KEY to practiceChartCheck()
    )
    val pruned = store.pruneInvalidRecords(validChecks)
    assertTrue("Mismatched record should be pruned", pruned.isEmpty())
    assertTrue(store.scores.isEmpty())

    val storeReloaded = ScoreStore(file)
    assertTrue(storeReloaded.scores.isEmpty())
  }

  @Test
  fun multipleChartsWithSameDifficultyHaveDistinctKeysAndRecords() {
    val timing = TimingData(offsetSeconds = 0.0, bpms = listOf(0.0 to 120.0))
    val edit1 = Chart(
      difficulty = Difficulty.EDIT,
      meter = 7,
      description = "Keyboard Edit",
      timing = timing,
      steps = listOf(ChartStep(beat = 0.0, timeSeconds = 0.5, lane = GameLane.LEFT)),
      chartId = "EDIT#Keyboard Edit"
    )
    val edit2 = Chart(
      difficulty = Difficulty.EDIT,
      meter = 9,
      description = "Pad Edit",
      timing = timing,
      steps = listOf(ChartStep(beat = 0.0, timeSeconds = 0.5, lane = GameLane.RIGHT)),
      chartId = "EDIT#Pad Edit"
    )
    val song = Song(
      folder = "songs/my_song",
      title = "My Song",
      subtitle = "",
      artist = "Artist",
      musicFile = null,
      bannerFile = null,
      backgroundFile = null,
      sampleStartSeconds = 0.0,
      sampleLengthSeconds = 10.0,
      displayBpm = "120",
      readBpm = 120.0,
      songTiming = timing,
      charts = listOf(edit1, edit2)
    )

    val key1 = chartKey(song, edit1)
    val key2 = chartKey(song, edit2)
    assertNotEquals("Charts with same difficulty must have distinct keys", key1, key2)
    assertEquals("songs/my_song#EDIT#Keyboard Edit", key1)
    assertEquals("songs/my_song#EDIT#Pad Edit", key2)

    val file = File(tempFolder.root, "scores.json")
    val store = ScoreStore(file)

    val rec1 = ScoreRecord(
      key = key1,
      chartCheck = chartCheck(edit1),
      percentDp = 80.0,
      gradeTier = 8,
      score = 500,
      maxCombo = 20,
      w1Count = 20,
      w2Count = 0,
      w3Count = 0,
      w4Count = 0,
      w5Count = 0,
      missCount = 0,
      heldCount = 0,
      letGoCount = 0,
      fullCombo = FullComboKind.FLAWLESS,
      scrollMod = "1.0x",
      dateIso = "2026-10-20"
    )
    val rec2 = ScoreRecord(
      key = key2,
      chartCheck = chartCheck(edit2),
      percentDp = 95.0,
      gradeTier = 3,
      score = 900,
      maxCombo = 50,
      w1Count = 50,
      w2Count = 0,
      w3Count = 0,
      w4Count = 0,
      w5Count = 0,
      missCount = 0,
      heldCount = 0,
      letGoCount = 0,
      fullCombo = FullComboKind.FLAWLESS,
      scrollMod = "1.0x",
      dateIso = "2026-10-20"
    )

    store.submit(rec1)
    store.submit(rec2)

    assertEquals(2, store.scores.size)
    assertEquals(80.0, store.getRecord(key1)?.percentDp ?: 0.0, 0.001)
    assertEquals(95.0, store.getRecord(key2)?.percentDp ?: 0.0, 0.001)
  }

  @Test
  fun failedWriteThrowsAndDoesNotMutateInMemoryState() {
    val readOnlyDir = File(tempFolder.root, "readonly_dir")
    readOnlyDir.mkdirs()
    val file = File(readOnlyDir, "scores.json")
    val store = ScoreStore(file)

    val initial = ScoreRecord(
      key = "songs/test#EASY",
      chartCheck = "50:12345678",
      percentDp = 80.0,
      gradeTier = 8,
      score = 500,
      maxCombo = 50,
      w1Count = 50,
      w2Count = 0,
      w3Count = 0,
      w4Count = 0,
      w5Count = 0,
      missCount = 0,
      heldCount = 0,
      letGoCount = 0,
      fullCombo = FullComboKind.FLAWLESS,
      scrollMod = "1.0x",
      dateIso = "2026-10-20",
      playCount = 1
    )
    store.submit(initial)
    assertEquals(1, store.getRecord("songs/test#EASY")?.playCount)

    readOnlyDir.setWritable(false)
    try {
      val betterRun = initial.copy(percentDp = 95.0, gradeTier = 3)
      try {
        store.submit(betterRun)
        fail("Expected IOException when writing to read-only directory")
      } catch (_: IOException) {
        // Expected
      }

      val current = store.getRecord("songs/test#EASY")
      assertNotNull(current)
      assertEquals(80.0, current?.percentDp ?: 0.0, 0.001)
      assertEquals(1, current?.playCount)
    } finally {
      readOnlyDir.setWritable(true)
    }
  }
=======
>>>>>>> 1d8a8080040e2bcd0e69e81b883b8e67eefabfa9
}
