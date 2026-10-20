package com.example

import com.example.core.gameplay.FullComboKind
import com.example.core.scores.PRACTICE_CHART_KEY
import com.example.core.scores.ScoreRecord
import com.example.core.scores.ScoreStore
import com.example.core.scores.chartKey
import com.example.core.scores.practiceChartCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

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
}
