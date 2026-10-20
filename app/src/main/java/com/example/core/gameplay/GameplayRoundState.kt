package com.example.core.gameplay

import com.example.core.chart.Chart
import com.example.core.chart.Song
import com.example.core.chart.StepKind
import com.example.core.chart.TimingData
import com.example.core.model.GameLane
import com.example.core.model.HoldResult
import com.example.core.model.Judgment
import com.example.core.model.NoteState
import com.example.core.model.PracticeNote
import java.util.EnumMap
import kotlin.math.abs
import kotlin.math.floor
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
 * StepMania full-combo tier (no Miss/W5/W4 and no LetGo hold).
 */
enum class FullComboKind {
  FLAWLESS,
  PERFECT,
  GREAT
}

/**
 * Pure per-row and per-hold scoring totals computed from a chart's note list.
 */
data class ScoreTotals(
  val judgmentCounts: Map<Judgment, Int>,
  val heldCount: Int,
  val letGoCount: Int,
  val dancePoints: Int,
  val gradePoints: Int,
  val combo: Int,
  val maxCombo: Int
) {
  val w1Count: Int get() = judgmentCounts[Judgment.W1] ?: 0
  val w2Count: Int get() = judgmentCounts[Judgment.W2] ?: 0
  val w3Count: Int get() = judgmentCounts[Judgment.W3] ?: 0
  val w4Count: Int get() = judgmentCounts[Judgment.W4] ?: 0
  val w5Count: Int get() = judgmentCounts[Judgment.W5] ?: 0
  val missCount: Int get() = judgmentCounts[Judgment.MISS] ?: 0
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
  val actualGradePoints: Int = 0,
  val combo: Int = 0,
  val maxCombo: Int = 0,
  val judgmentCounts: Map<Judgment, Int> = emptyMap(),
  val missCount: Int = 0,
  /** Holds/rolls finished OK (StepMania HNS_Held). */
  val heldCount: Int = 0,
  /** Holds/rolls let go or with a missed head (HNS_LetGo). */
  val letGoCount: Int = 0,
  val lastJudgment: JudgmentFeedback? = null,
  val laneLastHitTimeMs: Map<GameLane, Long> = emptyMap(),
  val judgmentSequence: Int = 0,
  val meta: RoundMeta = RoundMeta(),
  val musicStartMs: Long? = null,
  val timing: TimingData? = null,
  val scrollMod: ScrollMod = ScrollMod.DEFAULT,
  /** Highest displayed BPM (M-mod reference). */
  val readBpm: Double = bpm.toDouble()
) {
  val isSongRound: Boolean
    get() = musicStartMs != null && timing != null

  val w1Count: Int get() = judgmentCounts[Judgment.W1] ?: 0
  val w2Count: Int get() = judgmentCounts[Judgment.W2] ?: 0
  val w3Count: Int get() = judgmentCounts[Judgment.W3] ?: 0
  val w4Count: Int get() = judgmentCounts[Judgment.W4] ?: 0
  val w5Count: Int get() = judgmentCounts[Judgment.W5] ?: 0

  fun judgmentCount(judgment: Judgment): Int =
    if (judgment == Judgment.MISS) (judgmentCounts[Judgment.MISS] ?: missCount)
    else (judgmentCounts[judgment] ?: 0)

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

  /**
   * StepMania ArrowEffects::GetYOffset (without accel/effect mods), in "1x beats": how far below
   * the receptors a note at [noteBeat] / [noteHitTimeMs] sits at [nowMs] (negative once passed).
   * Multiply by the note field's pixels per beat (`travel / ScrollMod.BEATS_ON_SCREEN_AT_1X`).
   *
   * X/M mods use beat spacing (`displayedBeat(note) - displayedBeat(song)`, scaled by SPEEDS), so
   * arrows follow BPM changes, stops, delays and SCROLLS. C mods use time spacing.
   */
  fun scrollUnits(noteBeat: Double, noteHitTimeMs: Long, nowMs: Long, songBeat: Double = beatAt(nowMs)): Double {
    val mod = scrollMod
    if (mod is ScrollMod.C) {
      return (noteHitTimeMs - nowMs) / 1000.0 * mod.bpm / 60.0
    }
    val t = timing
    val beats = if (t != null) {
      val musicSeconds = (nowMs - (musicStartMs ?: 0L)) / 1000.0
      (t.displayedBeat(noteBeat) - t.displayedBeat(songBeat)) * t.displayedSpeedPercent(songBeat, musicSeconds)
    } else {
      noteBeat - songBeat
    }
    return beats * mod.beatMultiplier(readBpm)
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

  val totalRows: Int
    get() = notes.mapTo(hashSetOf()) { it.beat }.size

  val judgedNotesCount: Int
    get() = notes.count { it.state != NoteState.PENDING }

  val judgedRowsCount: Int
    get() = w1Count + w2Count + w3Count + w4Count + w5Count + missCount

  val pendingNotesCount: Int
    get() = notes.count { it.state == NoteState.PENDING }

  val totalHolds: Int
    get() = notes.count { it.isHold }

  val judgedHoldsCount: Int
    get() = heldCount + letGoCount

  /** Possible dance points for the whole chart: (rows * 3) + (holds and rolls * 3). */
  val possibleDancePoints: Int
    get() = totalRows * Judgment.W1.dancePoints + totalHolds * Judgment.HOLD_HELD_DANCE_POINTS

  /** Possible grade points for the whole chart: (rows * 2) + (holds and rolls * 6). */
  val possibleGradePoints: Int
    get() = totalRows * Judgment.W1.gradePoints + totalHolds * Judgment.HOLD_HELD_GRADE_POINTS

  /**
   * StepMania DP% (`PlayerStageStats::MakePercentScore`), in 0.00..100.00,
   * truncated (not rounded) to 2 decimal places.
   */
  val percentDp: Double
    get() = makePercentScore(score, possibleDancePoints)

  val accuracyPercent: Float
    get() = percentDp.toFloat()

  /**
   * StepMania full-combo classification (`PlayerStageStats::FullComboOfScore`) once all rows
   * and holds in the chart have been judged: no Miss/W5/W4 and no NG (LetGo) hold.
   */
  val fullComboKind: FullComboKind?
    get() {
      if (notes.isEmpty()) return null
      if (notes.any { it.state == NoteState.PENDING || it.holdResult == HoldResult.ACTIVE }) return null
      if (letGoCount > 0) return null
      if (missCount > 0 || w5Count > 0 || w4Count > 0) return null
      val hitRows = w1Count + w2Count + w3Count
      if (hitRows <= 0) return null
      return when {
        w3Count > 0 -> FullComboKind.GREAT
        w2Count > 0 -> FullComboKind.PERFECT
        else -> FullComboKind.FLAWLESS
      }
    }

  /**
   * StepMania default-theme grade tier (1 = Tier01/AAAA .. 17 = Tier17/D) from
   * `PlayerStageStats::GetGrade` with `GradeTier02IsAllW2s = true`.
   */
  val gradeTier: Int
    get() {
      val fPercent = if (possibleGradePoints == 0) 0f else actualGradePoints.toFloat() / possibleGradePoints.toFloat()
      val rawTier = GRADE_PERCENT_TIERS.firstOrNull { fPercent >= it.second }?.first ?: 17
      return when (fullComboKind) {
        FullComboKind.FLAWLESS -> 1
        FullComboKind.PERFECT -> 2
        else -> maxOf(rawTier, 3)
      }
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
   * that have passed the receptor beyond the miss window (> 180ms late) as MISSED.
   *
   * Active holds/rolls follow StepMania's Player::UpdateHoldNotes: a hold's life refills while its
   * lane is in [heldLanes] and drains to 0 over [HOLD_WINDOW_MS] when released; a roll always
   * drains over [ROLL_WINDOW_MS] (re-steps refill it in [onLanePressed]). Life 0 = NG (let go),
   * reaching the tail = OK (held). [heldLanes] null = no input sample this step (no drain).
   */
  fun advanceTime(newGameTimeMs: Long, heldLanes: Set<GameLane>? = null): GameplayRoundState {
    if (isPaused || isCompleted) return this

    val clampedTimeMs = newGameTimeMs.coerceAtLeast(currentGameTimeMs)
    val deltaMs = (clampedTimeMs - currentGameTimeMs).toFloat()
    var updatedLastJudgment = lastJudgment
    var nextSeq = judgmentSequence
    var anyNoteChanged = false

    val updatedNotes = notes.map { note ->
      if (note.holdResult == HoldResult.ACTIVE) {
        val holdEnd = note.holdEndMs ?: note.hitTimeMs
        var life = note.holdLife
        if (heldLanes != null && clampedTimeMs > note.hitTimeMs) {
          life = when {
            note.isRoll -> life - deltaMs / ROLL_WINDOW_MS
            note.lane in heldLanes -> 1f
            else -> life - deltaMs / HOLD_WINDOW_MS
          }.coerceAtLeast(0f)
        }
        when {
          life <= 0f -> {
            anyNoteChanged = true
            note.copy(holdLife = 0f, holdResult = HoldResult.LET_GO, holdJudgedAtMs = clampedTimeMs)
          }
          clampedTimeMs >= holdEnd -> {
            anyNoteChanged = true
            note.copy(holdLife = 1f, holdResult = HoldResult.HELD, holdJudgedAtMs = holdEnd)
          }
          life != note.holdLife -> {
            anyNoteChanged = true
            note.copy(holdLife = life)
          }
          else -> note
        }
      } else if (note.state == NoteState.PENDING &&
        (clampedTimeMs - note.hitTimeMs) > Judgment.MISS_THRESHOLD_MS
      ) {
        anyNoteChanged = true
        nextSeq += 1
        updatedLastJudgment = JudgmentFeedback(
          judgment = Judgment.MISS,
          lane = note.lane,
          timingErrorMs = clampedTimeMs - note.hitTimeMs,
          triggeredAtGameTimeMs = clampedTimeMs,
          sequenceId = nextSeq
        )
        // A missed head kills its hold: counted as NG (SCORE_MISSED_HOLDS_AND_ROLLS)
        note.copy(
          state = NoteState.MISSED,
          judgment = Judgment.MISS,
          timingErrorMs = clampedTimeMs - note.hitTimeMs,
          judgedAtMs = clampedTimeMs,
          holdResult = if (note.isHold) HoldResult.LET_GO else null,
          holdLife = if (note.isHold) 0f else note.holdLife
        )
      } else {
        note
      }
    }

    val lastNoteTimeMs = updatedNotes.maxOfOrNull { it.endTimeMs } ?: 0L
    val allJudged = updatedNotes.all { it.state != NoteState.PENDING && it.holdResult != HoldResult.ACTIVE }
    val roundFinished = allJudged && clampedTimeMs >= (lastNoteTimeMs + POST_ROUND_BUFFER_MS)

    if (!anyNoteChanged && !roundFinished && clampedTimeMs == currentGameTimeMs) {
      return this
    }

    val totals = if (anyNoteChanged) scoreTotals(updatedNotes) else null
    return copy(
      currentGameTimeMs = clampedTimeMs,
      notes = if (anyNoteChanged) updatedNotes else notes,
      score = totals?.dancePoints ?: score,
      actualGradePoints = totals?.gradePoints ?: actualGradePoints,
      combo = totals?.combo ?: combo,
      maxCombo = totals?.maxCombo ?: maxCombo,
      judgmentCounts = totals?.judgmentCounts ?: judgmentCounts,
      missCount = totals?.missCount ?: missCount,
      heldCount = totals?.heldCount ?: heldCount,
      letGoCount = totals?.letGoCount ?: letGoCount,
      lastJudgment = updatedLastJudgment,
      judgmentSequence = nextSeq,
      isCompleted = roundFinished,
      isRunning = !roundFinished && isRunning
    )
  }

  /**
   * Evaluates a physical dance-mat press in [lane] at [inputGameTimeMs] (converted from
   * [android.view.KeyEvent.getEventTime] via [GameplayClock]).
   *
   * Matches the earliest note in [lane] within ±180ms that is either PENDING or was only
   * auto-missed by a delayed frame rendered after [inputGameTimeMs].
   * Each note can only be judged HIT once, and a single press judges at most one note in that lane.
   */
  fun onLanePressed(
    lane: GameLane,
    inputGameTimeMs: Long = currentGameTimeMs
  ): GameplayRoundState {
    if (isPaused || isCompleted) return this

    // First ensure any overdue notes prior to inputGameTimeMs are auto-missed
    val advancedState = if (inputGameTimeMs > currentGameTimeMs) {
      advanceTime(inputGameTimeMs)
    } else {
      this
    }

    // A step on a lane with an active roll refills the roll's life (StepMania Player::Step)
    val timeSyncedState = if (advancedState.notes.any { it.isRoll && it.lane == lane && it.holdResult == HoldResult.ACTIVE }) {
      advancedState.copy(notes = advancedState.notes.map { note ->
        if (note.isRoll && note.lane == lane && note.holdResult == HoldResult.ACTIVE) note.copy(holdLife = 1f) else note
      })
    } else {
      advancedState
    }

    // Find the earliest candidate note in this lane within the ±180ms W5 hit window.
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
      .filter { abs(inputGameTimeMs - it.hitTimeMs) <= Judgment.MISS_THRESHOLD_MS }
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
          judgedAtMs = inputGameTimeMs,
          // Stepping on a hold head starts the hold (also revives one auto-missed by a late frame)
          holdResult = if (note.isHold) HoldResult.ACTIVE else null,
          holdLife = 1f,
          holdJudgedAtMs = null
        )
      } else {
        note
      }
    }

    // Recompute exact score, counts, and combo from updated notes so even reclaimed late-frame notes stay exact
    val totals = scoreTotals(updatedNotes)

    val nextSeq = timeSyncedState.judgmentSequence + 1
    val effectiveTriggerTime = maxOf(timeSyncedState.currentGameTimeMs, inputGameTimeMs)

    val newHitMap = timeSyncedState.laneLastHitTimeMs.toMutableMap().apply {
      put(lane, effectiveTriggerTime)
    }

    return timeSyncedState.copy(
      notes = updatedNotes,
      score = totals.dancePoints,
      actualGradePoints = totals.gradePoints,
      combo = totals.combo,
      maxCombo = totals.maxCombo,
      judgmentCounts = totals.judgmentCounts,
      missCount = totals.missCount,
      heldCount = totals.heldCount,
      letGoCount = totals.letGoCount,
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

    /** StepMania TimingWindowSecondsHold / TimingWindowSecondsRoll defaults. */
    const val HOLD_WINDOW_MS = 250f
    const val ROLL_WINDOW_MS = 500f

    /** Dance points for a hold/roll finished OK (`PercentScoreWeightHeld = 3`). */
    const val HOLD_POINTS: Int = Judgment.HOLD_HELD_DANCE_POINTS

    /** Earliest gameplay time the first step may land on (after the Ready?/Go! countdown). */
    const val SONG_FIRST_STEP_MIN_MS = 3_600L
    const val SONG_MIN_LEAD_IN_MS = 1_000L

    /** StepMania default theme `GradePercentTier01` .. `GradePercentTier17`. */
    private val GRADE_PERCENT_TIERS = listOf(
      1 to 1.000000f,
      2 to 1.000000f,
      3 to 0.98f,
      4 to 0.96f,
      5 to 0.94f,
      6 to 0.92f,
      7 to 0.89f,
      8 to 0.86f,
      9 to 0.83f,
      10 to 0.80f,
      11 to 0.76f,
      12 to 0.72f,
      13 to 0.68f,
      14 to 0.64f,
      15 to 0.60f,
      16 to 0.55f,
      17 to -999f
    )

    /**
     * StepMania `PlayerStageStats::MakePercentScore`: returns percentage in 0.00..100.00,
     * truncated (never rounded) to 2 decimal places with the +0.000001f float-precision boost.
     */
    fun makePercentScore(actual: Int, possible: Int): Double {
      if (possible <= 0) return 0.0
      if (actual >= possible) return 100.0
      if (actual <= 0) return 0.0
      val fPercent = (actual.toFloat() / possible.toFloat()).coerceAtLeast(0f) + 0.000001f
      return floor(fPercent.toDouble() * 10000.0) / 100.0
    }

    /**
     * Groups [notes] by beat, skips rows that still have any PENDING note, and scores each
     * completed row once (`ScoreKeeperNormal::HandleTapRowScore` / `LastTapNoteScoreTrack`):
     * - Any Miss in the row makes the row a Miss.
     * - Otherwise the row takes the judgment of the arrow with the largest timing offset.
     * - Combo adds the number of arrows in the row on W1..W3 and resets to 0 on W4..Miss.
     * - Hold OK/NG counts and points are added per finished hold/roll.
     */
    fun scoreTotals(notes: List<PracticeNote>): ScoreTotals {
      val counts = EnumMap<Judgment, Int>(Judgment::class.java).apply {
        Judgment.entries.forEach { put(it, 0) }
      }
      var dancePoints = 0
      var gradePoints = 0
      var combo = 0
      var maxCombo = 0

      val rows = notes.groupBy { it.beat }.entries.sortedBy { it.key }
      for ((_, rowNotes) in rows) {
        if (rowNotes.any { it.state == NoteState.PENDING }) continue

        val rowJudgment = if (rowNotes.any { it.state == NoteState.MISSED || it.judgment == Judgment.MISS || it.judgment == null }) {
          Judgment.MISS
        } else {
          // StepMania NoteDataWithScoring::LastTapNoteScoreTrack
          var scoreTime = Long.MIN_VALUE
          var chosen = Judgment.W1
          for (note in rowNotes.sortedBy { it.lane.ordinal }) {
            val tm = note.timingErrorMs ?: continue
            if (tm < scoreTime) continue
            scoreTime = tm
            chosen = note.judgment ?: Judgment.MISS
          }
          chosen
        }

        counts[rowJudgment] = (counts[rowJudgment] ?: 0) + 1
        dancePoints += rowJudgment.dancePoints
        gradePoints += rowJudgment.gradePoints

        if (rowJudgment.continuesCombo) {
          combo += rowNotes.size
          if (combo > maxCombo) maxCombo = combo
        } else {
          combo = 0
        }
      }

      var heldCount = 0
      var letGoCount = 0
      for (note in notes) {
        when (note.holdResult) {
          HoldResult.HELD -> {
            heldCount += 1
            dancePoints += HOLD_POINTS
            gradePoints += Judgment.HOLD_HELD_GRADE_POINTS
          }
          HoldResult.LET_GO -> {
            letGoCount += 1
          }
          else -> {}
        }
      }

      return ScoreTotals(
        judgmentCounts = counts,
        heldCount = heldCount,
        letGoCount = letGoCount,
        dancePoints = dancePoints,
        gradePoints = gradePoints,
        combo = combo,
        maxCombo = maxCombo
      )
    }

    fun createSongRound(
      song: Song,
      chart: Chart,
      isRunning: Boolean = true,
      scrollMod: ScrollMod = ScrollMod.DEFAULT
    ): GameplayRoundState {
      val firstStepMs = chart.steps.minOfOrNull { (it.timeSeconds * 1000.0).roundToLong() } ?: 0L
      val musicStartMs = maxOf(SONG_MIN_LEAD_IN_MS, SONG_FIRST_STEP_MIN_MS - firstStepMs)
      val notes = chart.steps.mapIndexed { index, step ->
        PracticeNote(
          id = index + 1,
          lane = step.lane,
          beat = step.beat,
          hitTimeMs = musicStartMs + (step.timeSeconds * 1000.0).roundToLong(),
          holdEndMs = step.endTimeSeconds?.let { musicStartMs + (it * 1000.0).roundToLong() },
          holdEndBeat = step.endBeat,
          isRoll = step.kind == StepKind.ROLL
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
        timing = chart.timing,
        scrollMod = scrollMod,
        readBpm = song.readBpm
      )
    }

    fun createNewRound(
      bpm: Int = PracticePatternGenerator.DEFAULT_BPM,
      beatZeroTimeMs: Long = PracticePatternGenerator.DEFAULT_BEAT_ZERO_MS,
      isRunning: Boolean = true,
      scrollMod: ScrollMod = ScrollMod.DEFAULT
    ): GameplayRoundState {
      return GameplayRoundState(
        bpm = bpm,
        beatZeroTimeMs = beatZeroTimeMs,
        currentGameTimeMs = 0L,
        isRunning = isRunning,
        isPaused = false,
        isCompleted = false,
        scrollMod = scrollMod,
        readBpm = bpm.toDouble(),
        notes = PracticePatternGenerator.generatePracticeNotes(
          beatZeroTimeMs = beatZeroTimeMs,
          bpm = bpm
        )
      )
    }
  }
}
