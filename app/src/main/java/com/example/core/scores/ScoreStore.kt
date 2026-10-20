package com.example.core.scores

import android.util.AtomicFile
import android.util.Log
import com.example.core.chart.Chart
import com.example.core.chart.Song
import com.example.core.chart.StepKind
import com.example.core.gameplay.FullComboKind
import com.example.core.gameplay.PracticePatternGenerator
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.zip.CRC32

/**
 * High score record for a single chart or practice pattern.
 *
 * Comparison follows StepMania's `HighScore::operator<` with `PercentageScoring = true`:
 * higher DP% wins; on equal DP%, the better grade wins (tier 1 / AAAA beats tier 2 / AAA).
 */
data class ScoreRecord(
  val key: String,
  val chartCheck: String,
  val percentDp: Double,
  val gradeTier: Int,
  val score: Int,
  val maxCombo: Int,
  val w1Count: Int,
  val w2Count: Int,
  val w3Count: Int,
  val w4Count: Int,
  val w5Count: Int,
  val missCount: Int,
  val heldCount: Int,
  val letGoCount: Int,
  val fullCombo: FullComboKind?,
  val scrollMod: String,
  val dateIso: String,
  val playCount: Int = 1
) {
  /**
   * Returns true if this record compares higher than [other].
   */
  fun isBetterThan(other: ScoreRecord): Boolean {
    if (percentDp != other.percentDp) {
      return percentDp > other.percentDp
    }
    // Lower tier number is a better grade: tier 1 (AAAA) beats tier 2 (AAA)
    return gradeTier < other.gradeTier
  }

  fun toJson(): JSONObject = JSONObject().apply {
    put("key", key)
    put("chartCheck", chartCheck)
    put("percentDp", percentDp)
    put("gradeTier", gradeTier)
    put("score", score)
    put("maxCombo", maxCombo)
    put("w1Count", w1Count)
    put("w2Count", w2Count)
    put("w3Count", w3Count)
    put("w4Count", w4Count)
    put("w5Count", w5Count)
    put("missCount", missCount)
    put("heldCount", heldCount)
    put("letGoCount", letGoCount)
    if (fullCombo != null) {
      put("fullCombo", fullCombo.name)
    } else {
      put("fullCombo", JSONObject.NULL)
    }
    put("scrollMod", scrollMod)
    put("dateIso", dateIso)
    put("playCount", playCount)
  }

  companion object {
    fun fromJson(json: JSONObject): ScoreRecord {
      val fullComboStr = if (json.isNull("fullCombo") || !json.has("fullCombo")) null else json.getString("fullCombo")
      val fc = fullComboStr?.let {
        try {
          FullComboKind.valueOf(it)
        } catch (_: IllegalArgumentException) {
          null
        }
      }
      return ScoreRecord(
        key = json.getString("key"),
        chartCheck = json.getString("chartCheck"),
        percentDp = json.getDouble("percentDp"),
        gradeTier = json.getInt("gradeTier"),
        score = json.getInt("score"),
        maxCombo = json.getInt("maxCombo"),
        w1Count = json.optInt("w1Count", 0),
        w2Count = json.optInt("w2Count", 0),
        w3Count = json.optInt("w3Count", 0),
        w4Count = json.optInt("w4Count", 0),
        w5Count = json.optInt("w5Count", 0),
        missCount = json.optInt("missCount", 0),
        heldCount = json.optInt("heldCount", 0),
        letGoCount = json.optInt("letGoCount", 0),
        fullCombo = fc,
        scrollMod = json.optString("scrollMod", ""),
        dateIso = json.optString("dateIso", ""),
        playCount = json.optInt("playCount", 1)
      )
    }
  }
}

data class SubmitResult(
  val isNewBest: Boolean,
  val previousBest: ScoreRecord?
)

/** Key for practice pattern runs. */
const val PRACTICE_CHART_KEY = "practice"

/**
 * Builds the canonical storage key for a chart: `folder#CHART_ID` or `practice`.
 */
fun chartKey(song: Song?, chart: Chart?): String {
  if (song == null || chart == null) return PRACTICE_CHART_KEY
  val chartIdentifier = chart.chartId.ifEmpty {
    if (chart.description.isNotBlank()) "${chart.difficulty.name}#${chart.description}"
    else chart.difficulty.name
  }
  return "${song.folder}#$chartIdentifier"
}

private fun updateCrc(crc: CRC32, timeMs: Long, laneOrdinal: Int, kindOrdinal: Int, endTimeMs: Long) {
  val buffer = ByteBuffer.allocate(18).order(ByteOrder.LITTLE_ENDIAN)
  buffer.putLong(timeMs)
  buffer.put(laneOrdinal.toByte())
  buffer.put(kindOrdinal.toByte())
  buffer.putLong(endTimeMs)
  crc.update(buffer.array(), 0, 18)
}

/**
 * Computes the chartCheck string `noteCount:8-hex` from complete step timestamps,
 * lane, note type, and hold/roll end times using deterministic binary encoding.
 */
fun chartCheck(chart: Chart): String {
  val crc = CRC32()
  for (step in chart.steps) {
    val ms = (step.timeSeconds * 1000.0).toLong()
    val endMs = step.endTimeSeconds?.let { (it * 1000.0).toLong() } ?: 0L
    updateCrc(crc, ms, step.lane.ordinal, step.kind.ordinal, endMs)
  }
  return String.format(Locale.US, "%d:%08x", chart.noteCount, crc.value)
}

/**
 * Computes the chartCheck string for the built-in practice pattern using
 * complete timestamps, lane, kind, and hold end time.
 */
fun practiceChartCheck(): String {
  val notes = PracticePatternGenerator.generatePracticeNotes()
  val crc = CRC32()
  for (note in notes) {
    val kindOrdinal = when {
      note.isRoll -> StepKind.ROLL.ordinal
      note.isHold -> StepKind.HOLD.ordinal
      else -> StepKind.TAP.ordinal
    }
    updateCrc(crc, note.hitTimeMs, note.lane.ordinal, kindOrdinal, note.holdEndMs ?: 0L)
  }
  return String.format(Locale.US, "%d:%08x", notes.size, crc.value)
}

fun chartCheckForRound(song: Song?, chart: Chart?): String =
  if (song != null && chart != null) chartCheck(chart) else practiceChartCheck()

/**
 * Persistent high-score store saving best results to a JSON file.
 * Uses [AtomicFile] for atomic writes and handles corrupt/missing files safely.
 */
class ScoreStore(private val storageFile: File) {

  private val atomicFile = AtomicFile(storageFile)
  private val _scores = mutableMapOf<String, ScoreRecord>()
  val scores: Map<String, ScoreRecord>
    get() = synchronized(this) {
      _scores.toMap()
    }

  init {
    load()
  }

  fun getRecord(key: String): ScoreRecord? = synchronized(this) {
    _scores[key]
  }

  /**
   * Submits a newly completed run.
   * If the record is the first run, replaces a modified chart, or beats the previous best,
   * it becomes the new best record. In all cases, playCount is incremented.
   *
   * Only mutates in-memory state after persistence to [storageFile] succeeds via [AtomicFile].
   * Propagates [IOException] on failure.
   */
  @Throws(IOException::class)
  fun submit(record: ScoreRecord): SubmitResult = synchronized(this) {
    val existing = _scores[record.key]
    val candidate = _scores.toMutableMap()
    val isNewBest: Boolean
    val previousBest: ScoreRecord?

    // Chart check mismatch: chart changed, drop old record
    if (existing == null || existing.chartCheck != record.chartCheck) {
      val newBest = record.copy(playCount = 1)
      candidate[record.key] = newBest
      isNewBest = true
      previousBest = null
    } else {
      val updatedPlayCount = existing.playCount + 1
      if (record.isBetterThan(existing)) {
        val newBest = record.copy(playCount = updatedPlayCount)
        candidate[record.key] = newBest
        isNewBest = true
        previousBest = existing
      } else {
        // Existing best preserved; play count incremented
        val updatedExisting = existing.copy(playCount = updatedPlayCount)
        candidate[record.key] = updatedExisting
        isNewBest = false
        previousBest = existing
      }
    }

    // Persist to disk FIRST. If save throws IOException, in-memory _scores remains intact.
    save(candidate)

    _scores.clear()
    _scores.putAll(candidate)
    return SubmitResult(isNewBest = isNewBest, previousBest = previousBest)
  }

  /**
   * Validates stored records against [validChecks] (map of chartKey -> expectedChartCheck).
   * Any record whose chartCheck does not match the expected checksum is pruned immediately.
   * Returns the pruned scores map.
   */
  fun pruneInvalidRecords(validChecks: Map<String, String>): Map<String, ScoreRecord> = synchronized(this) {
    val invalidKeys = _scores.entries.filter { (key, record) ->
      val expected = validChecks[key]
      expected != null && record.chartCheck != expected
    }.map { it.key }

    if (invalidKeys.isNotEmpty()) {
      val candidate = _scores.toMutableMap()
      invalidKeys.forEach { candidate.remove(it) }
      try {
        save(candidate)
        _scores.clear()
        _scores.putAll(candidate)
      } catch (e: IOException) {
        Log.e(TAG, "Failed to persist pruned score file", e)
      }
    }
    return _scores.toMap()
  }

  private fun load() = synchronized(this) {
    _scores.clear()
    if (!storageFile.exists()) return
    try {
      val bytes = atomicFile.readFully()
      if (bytes.isEmpty()) return
      val text = String(bytes, Charsets.UTF_8)
      if (text.isBlank()) return
      val root = JSONObject(text)
      val keys = root.keys()
      while (keys.hasNext()) {
        val key = keys.next()
        val recordJson = root.optJSONObject(key) ?: continue
        try {
          val record = ScoreRecord.fromJson(recordJson)
          _scores[key] = record
        } catch (e: JSONException) {
          Log.w(TAG, "Skipping invalid score record for key: $key", e)
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to load score file; starting empty", e)
      _scores.clear()
    }
  }

  @Throws(IOException::class)
  private fun save(candidate: Map<String, ScoreRecord>) {
    val root = JSONObject()
    for ((key, record) in candidate) {
      root.put(key, record.toJson())
    }
    val text = root.toString(2)
    storageFile.parentFile?.mkdirs()
    val stream = atomicFile.startWrite()
    try {
      stream.write(text.toByteArray(Charsets.UTF_8))
      atomicFile.finishWrite(stream)
    } catch (e: Exception) {
      atomicFile.failWrite(stream)
      if (e is IOException) throw e else throw IOException(e)
    }
  }

  companion object {
    private const val TAG = "ScoreStore"
  }
}
