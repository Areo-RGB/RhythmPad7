package com.example.core.gameplay

import com.example.core.chart.Chart
import com.example.core.chart.Song
import com.example.core.chart.TimingData
import com.example.core.model.GameLane
import com.example.core.model.Judgment
import com.example.core.model.NoteState
import com.example.core.model.PracticeNote
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * High-level lifecycle phase of a gameplay round.
 */
enum class RoundPhase {
  COUNTDOWN,
  PLAYING,
  PAUSED,
  COMPLETED
}

/**
 * Snapshot of the most recent timing judgment for center-screen HUD display.
 */
data class JudgmentFeedback(
  val judgment: Judgment,
  val lane: GameLane,
  val timingErrorMs: Long,
  val triggeredAtGameTimeMs: Long,
  val sequenceId: Int
)

/**
 * Display metadata for the round (song info panel, evaluation header, background art).
 */
data class RoundMeta(
  val title: String = "StepMania Pad Foundation #1",
  val artist: String = "RhythmPad7 Practice",
  val difficultyName: String = "Beginner",
  val difficultyIndex: Int = 0,
  val meter: Int = 1,
  val bpmLabel: String = "${PracticePatternGenerator.DEFAULT_BPM}",
  val backgroundPath: String? = null,
  val musicPath: String? = null
)

/**
 * Immutable, deterministic state of a scrolling rhythm gameplay round.
 *
 * For song rounds, [musicStartMs] is the gameplay time at which the music file's position 0 plays
 * and [timing] maps music seconds to beats (StepMania TimingData), so
 * `noteHitTimeMs = musicStartMs + timing.timeAtBeat(beat) * 1000`.
 */
data class GameplayRoundState(
  val bpm: Int = PracticePatternGenerator.DEFAULT_BPM,
  val beatZeroTimeMs: Long = PracticePatternGenerator.DEFAULT_BEAT_ZERO_MS,
  val travelDurationMs: Long = PracticeNote.DEFAULT_TRAVEL_DURATION_MS,
  val currentGameTimeMs: Long = 0L,
  val isRunning: Boolean = false,
  val isPaused: Boolean = false,
  val isCompleted: Boolean = false,
  val notes: List<PracticeNote> = PracticePatternGenerator.generatePracticeNotes(
    beatZeroTimeMs = PracticePatternGenerator.DEFAULT_BEAT_ZERO_MS,
    bpm = PracticePatternGenerator.DEFAULT_BPM
  ),
  val score: Int = 0,
  val combo: Int = 0,
  val maxCombo: Int = 0,
  val excellentCount: Int = 0,
  val goodCount: Int = 0,
  val missCount: Int = 0,
  val lastJudgment: JudgmentFeedback? = null,
  val laneLastHitTimeMs: Map<GameLane, Long> = emptyMap(),
  val judgmentSequence: Int = 0,
  val meta: RoundMeta = RoundMeta(),
  val musicStartMs: Long? = null,
  val timing: TimingData? = null
) {
  val isSongRound: Boolean
    get() = musicStartMs != null && timing != null

  /** Beat displayed at [gameTimeMs] (drives receptor pulse and measure lines). */
  fun beatAt(gameTimeMs: Long): Double {
    val t = timing
    val start = musicStartMs
    return if (t != null && start != null) {
      t.beatAtTime((gameTimeMs - start) / 1000.0)
    } else {
      (gameTimeMs - beatZeroTimeMs) * bpm / 60_000.0
    }
  }

  /** Gameplay time at which [beat] is reached. */
  fun gameTimeAtBeat(beat: Double): Long {
    val t = timing
    val start = musicStartMs
    return if (t != null && start != null) {
      start + (t.timeAtBeat(beat) * 1000.0).roundToLong()
    } else {
      PracticePatternGenerator.beatToTimeMs(beat = beat, beatZeroTimeMs = beatZeroTimeMs, bpm = bpm)
    }
  }

  val totalNotes: Int
    get() = notes.size

  val judgedNotesCount: Int
    get() = excellentCount + goodCount + missCount

  val pendingNotesCount: Int
    get() = notes.count { it.state == NoteState.PENDING }

  /**
   * Weighted accuracy percentage (0..100).
   * EXCELLENT = 100% weight, GOOD = 60% weight, MISS = 0% weight.
   */
  val accuracyPercent: Float
    get() {
      if (judgedNotesCount == 0) return 100f
      val earnedWeight = (excellentCount * 100f) + (goodCount * 60f)
      val possibleWeight = judgedNotesCount * 100f
      return ((earnedWeight / possibleWeight) * 1000f).roundToInt() / 10f
    }

  val phase: RoundPhase
    get() = when {
      isCompleted -> RoundPhase.COMPLETED
      isPaused -> RoundPhase.PAUSED
      currentGameTimeMs < beatZeroTimeMs -> RoundPhase.COUNTDOWN
      else -> RoundPhase.PLAYING
    }

  /**
   * Countdown banner text ("3", "2", "1", "GO!", or null once active).
   */
  val countdownText: String?
    get() {
      if (isCompleted) return null
      return when {
        currentGameTimeMs < 1000L -> "3"
        currentGameTimeMs < 2000L -> "2"
        currentGameTimeMs < 3000L -> "1"
        currentGameTimeMs < 3650L -> "GO!"
        else -> null
      }
    }

  /**
   * Advances the round clock to [newGameTimeMs] and marks any pending notes
   * that have passed the receptor beyond the miss window (> 150ms late) as MISSED.
   */
  fun advanceTime(newGameTimeMs: Long): GameplayRoundState {
    if (isPaused || isCompleted) return this

    val clampedTimeMs = newGameTimeMs.coerceAtLeast(currentGameTimeMs)
    var updatedCombo = combo
    var updatedMisses = missCount
    var updatedLastJudgment = lastJudgment
    var nextSeq = judgmentSequence
    var anyNoteChanged = false

    val updatedNotes = notes.map { note ->
      if (note.state == NoteState.PENDING &&
        (clampedTimeMs - note.hitTimeMs) > Judgment.MISS_THRESHOLD_MS
      ) {
        anyNoteChanged = true
        updatedCombo = 0
        updatedMisses += 1
        nextSeq += 1
        updatedLastJudgment = JudgmentFeedback(
          judgment = Judgment.MISS,
          lane = note.lane,
          timingErrorMs = clampedTimeMs - note.hitTimeMs,
          triggeredAtGameTimeMs = clampedTimeMs,
          sequenceId = nextSeq
        )
        note.copy(
          state = NoteState.MISSED,
          judgment = Judgment.MISS,
          timingErrorMs = clampedTimeMs - note.hitTimeMs,
          judgedAtMs = clampedTimeMs
        )
      } else {
        note
      }
    }

    val lastNoteTimeMs = updatedNotes.maxOfOrNull { it.hitTimeMs } ?: 0L
    val allJudged = updatedNotes.all { it.state != NoteState.PENDING }
    val roundFinished = allJudged && clampedTimeMs >= (lastNoteTimeMs + POST_ROUND_BUFFER_MS)

    return if (!anyNoteChanged && !roundFinished && clampedTimeMs == currentGameTimeMs) {
      this
    } else {
      copy(
        currentGameTimeMs = clampedTimeMs,
        notes = if (anyNoteChanged) updatedNotes else notes,
        combo = updatedCombo,
        missCount = updatedMisses,
        lastJudgment = updatedLastJudgment,
        judgmentSequence = nextSeq,
        isCompleted = roundFinished,
        isRunning = !roundFinished && isRunning
      )
    }
  }

  /**
   * Evaluates a physical dance-mat press in [lane] at [inputGameTimeMs] (converted from
   * [android.view.KeyEvent.getEventTime] via [GameplayClock]).
   *
   * Matches the earliest note in [lane] within ±150ms that is either PENDING or was only
   * auto-missed by a delayed frame rendered after [inputGameTimeMs].
   * Each note can only be judged HIT once, and a single press judges at most one note in that lane.
   */
  fun onLanePressed(
    lane: GameLane,
    inputGameTimeMs: Long = currentGameTimeMs
  ): GameplayRoundState {
    if (isPaused || isCompleted) return this

    // First ensure any overdue notes prior to inputGameTimeMs are auto-missed
    val timeSyncedState = if (inputGameTimeMs > currentGameTimeMs) {
      advanceTime(inputGameTimeMs)
    } else {
      this
    }

    // Find the earliest candidate note in this lane within the ±150ms GOOD hit window.
    // Includes PENDING notes as well as notes that were auto-missed by a frame rendered
    // strictly after the hardware input timestamp (judgedAtMs > inputGameTimeMs).
    val candidateNote = timeSyncedState.notes
      .asSequence()
      .filter { note ->
        note.lane == lane && (
          note.state == NoteState.PENDING ||
            (note.state == NoteState.MISSED && (note.judgedAtMs ?: Long.MAX_VALUE) > inputGameTimeMs)
          )
      }
      .filter { abs(inputGameTimeMs - it.hitTimeMs) <= Judgment.GOOD_WINDOW_MS }
      .minByOrNull { it.hitTimeMs }
      ?: return timeSyncedState

    val signedErrorMs = inputGameTimeMs - candidateNote.hitTimeMs
    val judgment = Judgment.evaluateHit(signedErrorMs) ?: return timeSyncedState

    val updatedNotes = timeSyncedState.notes.map { note ->
      if (note.id == candidateNote.id) {
        note.copy(
          state = NoteState.HIT,
          judgment = judgment,
          timingErrorMs = signedErrorMs,
          judgedAtMs = inputGameTimeMs
        )
      } else {
        note
      }
    }

    // Recompute exact score, counts, and combo from updated notes so even reclaimed late-frame notes stay exact
    var runningCombo = 0
    var computedMaxCombo = timeSyncedState.maxCombo
    var computedScore = 0
    var computedExcellents = 0
    var computedGoods = 0
    var computedMisses = 0

    for (note in updatedNotes) {
      when (note.state) {
        NoteState.HIT -> {
          runningCombo += 1
          if (runningCombo > computedMaxCombo) {
            computedMaxCombo = runningCombo
          }
          when (note.judgment) {
            Judgment.EXCELLENT -> {
              computedExcellents += 1
              computedScore += Judgment.EXCELLENT.points
            }
            Judgment.GOOD -> {
              computedGoods += 1
              computedScore += Judgment.GOOD.points
            }
            else -> {}
          }
        }
        NoteState.MISSED -> {
          computedMisses += 1
          runningCombo = 0
        }
        NoteState.PENDING -> {}
      }
    }

    val nextSeq = timeSyncedState.judgmentSequence + 1
    val effectiveTriggerTime = maxOf(timeSyncedState.currentGameTimeMs, inputGameTimeMs)

    val newHitMap = timeSyncedState.laneLastHitTimeMs.toMutableMap().apply {
      put(lane, effectiveTriggerTime)
    }

    return timeSyncedState.copy(
      notes = updatedNotes,
      score = computedScore,
      combo = runningCombo,
      maxCombo = computedMaxCombo,
      excellentCount = computedExcellents,
      goodCount = computedGoods,
      missCount = computedMisses,
      lastJudgment = JudgmentFeedback(
        judgment = judgment,
        lane = lane,
        timingErrorMs = signedErrorMs,
        triggeredAtGameTimeMs = effectiveTriggerTime,
        sequenceId = nextSeq
      ),
      laneLastHitTimeMs = newHitMap,
      judgmentSequence = nextSeq
    )
  }

  companion object {
    const val POST_ROUND_BUFFER_MS = 900L

    /** Earliest gameplay time the first step may land on (after the Ready?/Go! countdown). */
    const val SONG_FIRST_STEP_MIN_MS = 3_600L
    const val SONG_MIN_LEAD_IN_MS = 1_000L

    fun createSongRound(
      song: Song,
      chart: Chart,
      isRunning: Boolean = true
    ): GameplayRoundState {
      val firstStepMs = chart.steps.minOfOrNull { (it.timeSeconds * 1000.0).roundToLong() } ?: 0L
      val musicStartMs = maxOf(SONG_MIN_LEAD_IN_MS, SONG_FIRST_STEP_MIN_MS - firstStepMs)
      val notes = chart.steps.mapIndexed { index, step ->
        PracticeNote(
          id = index + 1,
          lane = step.lane,
          beat = step.beat,
          hitTimeMs = musicStartMs + (step.timeSeconds * 1000.0).roundToLong()
        )
      }
      val firstBpm = chart.timing.bpmValues.firstOrNull() ?: 120.0
      return GameplayRoundState(
        bpm = firstBpm.roundToInt().coerceAtLeast(1),
        beatZeroTimeMs = 3_000L,
        currentGameTimeMs = 0L,
        isRunning = isRunning,
        notes = notes,
        meta = RoundMeta(
          title = song.title,
          artist = song.artist,
          difficultyName = chart.difficulty.displayName,
          difficultyIndex = chart.difficulty.ordinal,
          meter = chart.meter,
          bpmLabel = song.displayBpm,
          backgroundPath = song.backgroundPath,
          musicPath = song.musicPath
        ),
        musicStartMs = musicStartMs,
        timing = chart.timing
      )
    }

    fun createNewRound(
      bpm: Int = PracticePatternGenerator.DEFAULT_BPM,
      beatZeroTimeMs: Long = PracticePatternGenerator.DEFAULT_BEAT_ZERO_MS,
      isRunning: Boolean = true
    ): GameplayRoundState {
      return GameplayRoundState(
        bpm = bpm,
        beatZeroTimeMs = beatZeroTimeMs,
        currentGameTimeMs = 0L,
        isRunning = isRunning,
        isPaused = false,
        isCompleted = false,
        notes = PracticePatternGenerator.generatePracticeNotes(
          beatZeroTimeMs = beatZeroTimeMs,
          bpm = bpm
        )
      )
    }
  }
}
