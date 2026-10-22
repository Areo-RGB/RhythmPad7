package com.example.core.state

import android.content.Context
import android.content.SharedPreferences
import android.content.res.AssetManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.audio.ExoSongAudio
import com.example.core.audio.SongAudio
import com.example.core.chart.Chart
import com.example.core.chart.Song
import com.example.core.chart.SongLibrary
import com.example.core.gameplay.AutoSync
import com.example.core.gameplay.GameplayClock
import com.example.core.gameplay.GameplayRoundState
import com.example.core.gameplay.ScrollMod
import com.example.core.gameplay.EndlessGenerator
import com.example.core.gameplay.PracticePatternGenerator
import com.example.core.gameplay.RoundMeta
import com.example.core.input.ControllerManager
import com.example.core.input.SharedPreferencesMappingStore
import com.example.core.model.ControllerInfo
import com.example.core.model.ControllerMapping
import com.example.core.model.GameInputEvent
import com.example.core.model.GameLane
import com.example.core.model.MenuInput
import com.example.core.model.PlayerProfile
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.example.core.model.RawInputEvent
import com.example.core.scores.ScoreRecord
import com.example.core.scores.ScoreStore
import com.example.core.scores.SubmitResult
import com.example.core.scores.chartCheckForRound
import com.example.core.scores.chartKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Main ViewModel coordinating physical dance-mat input state, monotonic [GameplayClock],
 * scrolling rhythm gameplay round state, controller mappings, and debug telemetry HUD.
 * Managed via Android ViewModel lifecycle so onCleared() cleanly unregisters hardware listeners.
 */
class RhythmGameViewModel(
  val controllerManager: ControllerManager,
  val gameplayClock: GameplayClock = GameplayClock(),
  private val songAudio: SongAudio? = null,
  private val assets: AssetManager? = null,
  /** Extra simfile folders on device storage (`adb push` songs here), scanned after the bundled ones. */
  private val songsDirs: List<java.io.File> = emptyList(),
  private val prefs: SharedPreferences? = null,
  private var scoreStore: ScoreStore? = null,
  private val scoreStoreForProfile: ((PlayerProfile) -> ScoreStore)? = null
) : ViewModel() {

  // Active player profile (StepMania ScreenSelectProfile); results are saved under it
  private val _profile = MutableStateFlow(PlayerProfile.byId(prefs?.getString(PREF_PROFILE, null)))
  val profile: StateFlow<PlayerProfile> = _profile.asStateFlow()

  init {
    scoreStoreForProfile?.let { scoreStore = it(_profile.value) }
  }

  fun selectProfile(profile: PlayerProfile) {
    if (profile == _profile.value) return
    _profile.value = profile
    prefs?.edit()?.putString(PREF_PROFILE, profile.id)?.apply()
    _lastResult.value = null
    scoreStoreForProfile?.let { factory ->
      viewModelScope.launch {
        val store = withContext(Dispatchers.IO) { factory(profile) }
        if (_profile.value == profile) {
          scoreStore = store
          _bestScores.value = store.scores
        }
      }
    }
  }


  // High score tracking per chart and practice pattern
  private val _bestScores = MutableStateFlow<Map<String, ScoreRecord>>(scoreStore?.scores.orEmpty())
  val bestScores: StateFlow<Map<String, ScoreRecord>> = _bestScores.asStateFlow()

  private val _lastResult = MutableStateFlow<SubmitResult?>(null)
  val lastResult: StateFlow<SubmitResult?> = _lastResult.asStateFlow()

  // Scroll speed modifier (StepMania XMod / MMod / CMod), persisted across launches
  private val _scrollMod = MutableStateFlow(
    ScrollMod.parse(prefs?.getString(PREF_SCROLL_MOD, null)) ?: ScrollMod.DEFAULT
  )
  val scrollMod: StateFlow<ScrollMod> = _scrollMod.asStateFlow()

  // Holds/rolls on or off (StepMania NoHolds): off turns every hold head into a plain tap, persisted
  private val _holdsEnabled = MutableStateFlow(prefs?.getBoolean(PREF_HOLDS_ENABLED, true) ?: true)
  val holdsEnabled: StateFlow<Boolean> = _holdsEnabled.asStateFlow()

  fun setHoldsEnabled(enabled: Boolean) {
    _holdsEnabled.value = enabled
    prefs?.edit()?.putBoolean(PREF_HOLDS_ENABLED, enabled)?.apply()
    val round = _roundState.value
    if (!round.isRunning || round.isCompleted) return
    when {
      !enabled -> _roundState.update { stripHolds(it) }
      // Endless: regenerate the measures that are not on screen yet
      round.endlessLevel != null -> setEndlessLevel(round.endlessLevel)
      // Songs: restore hold tails on notes that are still pending, from the chart
      else -> currentSelection?.let { (song, chart) ->
        val source = GameplayRoundState.createSongRound(song, chart).notes.associateBy { it.id }
        _roundState.update { r ->
          r.copy(notes = r.notes.map { note ->
            val orig = source[note.id]
            if (note.isPending && orig != null && orig.isHold) {
              note.copy(holdEndMs = orig.holdEndMs, holdEndBeat = orig.holdEndBeat, isRoll = orig.isRoll)
            } else note
          })
        }
      }
    }
  }

  /** Pending holds/rolls become taps (already-judged notes keep their result). */
  private fun stripHolds(round: GameplayRoundState): GameplayRoundState {
    if (round.notes.none { it.isPending && it.isHold }) return round
    return round.copy(notes = round.notes.map {
      if (it.isPending && it.isHold) it.copy(holdEndMs = null, holdEndBeat = null, isRoll = false) else it
    })
  }

  fun setScrollMod(mod: ScrollMod) {
    _scrollMod.value = mod
    // Apply live to the round in progress (mid-song speed change from the gameplay HUD)
    _roundState.update { it.copy(scrollMod = mod) }
    prefs?.edit()?.putString(PREF_SCROLL_MOD, mod.serialize())?.apply()
  }

  // Global input offset (ms, positive = player/mat registers late), persisted.
  // Subtracted from every step's timestamp before judging, like StepMania's GlobalOffsetSeconds.
  private val _inputOffsetMs = MutableStateFlow(prefs?.getInt(PREF_INPUT_OFFSET, 0) ?: 0)
  val inputOffsetMs: StateFlow<Int> = _inputOffsetMs.asStateFlow()

  fun setInputOffsetMs(value: Int) {
    val clamped = value.coerceIn(-MAX_INPUT_OFFSET_MS, MAX_INPUT_OFFSET_MS)
    _inputOffsetMs.value = clamped
    prefs?.edit()?.putInt(PREF_INPUT_OFFSET, clamped)?.apply()
  }

  /** Auto-sync (StepMania AutosyncMachine): tunes [inputOffsetMs] from consistent steps. */
  data class AutoSyncStatus(
    val enabled: Boolean = true,
    val collected: Int = 0,
    val total: Int = AutoSync.SAMPLE_COUNT,
    val lastMessage: String? = null,
    val messageSeq: Int = 0
  )

  private val autoSync = AutoSync()
  private val _autoSyncStatus = MutableStateFlow(AutoSyncStatus())
  val autoSyncStatus: StateFlow<AutoSyncStatus> = _autoSyncStatus.asStateFlow()

  fun setAutoSyncEnabled(enabled: Boolean) {
    autoSync.reset()
    _autoSyncStatus.value = _autoSyncStatus.value.copy(enabled = enabled, collected = 0, lastMessage = null)
  }

  private fun feedAutoSync(errorMs: Long) {
    val status = _autoSyncStatus.value
    if (!status.enabled) return
    when (val result = autoSync.addSample(errorMs.toDouble())) {
      null -> _autoSyncStatus.value = status.copy(collected = autoSync.collected)
      is AutoSync.Result.Applied -> {
        val correction = Math.round(result.correctionMs).toInt()
        setInputOffsetMs(_inputOffsetMs.value + correction)
        _autoSyncStatus.value = status.copy(
          collected = 0,
          lastMessage = "Auto-sync: offset ${if (correction >= 0) "+" else ""}$correction ms → ${_inputOffsetMs.value} ms",
          messageSeq = status.messageSeq + 1
        )
      }
      is AutoSync.Result.Rejected -> _autoSyncStatus.value = status.copy(
        collected = 0,
        lastMessage = "Auto-sync: steps too uneven (±${result.stdDevMs.toInt()} ms), not applied",
        messageSeq = status.messageSeq + 1
      )
    }
  }

  // Song library loaded from assets/songs (StepMania .ssc / .sm)
  private val _songs = MutableStateFlow<List<Song>>(emptyList())
  val songs: StateFlow<List<Song>> = _songs.asStateFlow()

  private val _songsLoading = MutableStateFlow(assets != null)
  val songsLoading: StateFlow<Boolean> = _songsLoading.asStateFlow()

  /** The song + chart of the current round (null = built-in practice pattern). */
  private var currentSelection: Pair<Song, Chart>? = null

  // Audio ↔ GameplayClock lock state
  private var audioStarted = false
  private var lastAudioSyncUptimeMs = 0L

  // Live playfield input state (pressed lanes, press counters, active holders, event history)
  private val _inputState = MutableStateFlow(GameInputState())
  val inputState: StateFlow<GameInputState> = _inputState.asStateFlow()

  // Scrolling practice chart & judgment round state
  private val _roundState = MutableStateFlow(GameplayRoundState.createNewRound(isRunning = false))
  val roundState: StateFlow<GameplayRoundState> = _roundState.asStateFlow()

  // Hardware controller status
  val controllerInfo: StateFlow<ControllerInfo> = controllerManager.controllerInfo

  // Directional controller mappings
  val currentMapping: StateFlow<ControllerMapping> = controllerManager.currentMapping

  // Capture mode for mapping UI
  val capturingLane: StateFlow<GameLane?> = controllerManager.capturingLane

  // Raw telemetry stream for settings
  val rawInputEvents: SharedFlow<RawInputEvent> = controllerManager.rawInputEvents

  // UI state toggles
  private val _isDebugOverlayVisible = MutableStateFlow(false)
  val isDebugOverlayVisible: StateFlow<Boolean> = _isDebugOverlayVisible.asStateFlow()

  private val _isPaused = MutableStateFlow(false)
  val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

  /** Pad navigation: fresh arrow presses and menu buttons, consumed by whichever screen is shown. */
  private val _menuInputs = MutableSharedFlow<MenuInput>(extraBufferCapacity = 16)
  val menuInputs: SharedFlow<MenuInput> = _menuInputs.asSharedFlow()

  init {
    viewModelScope.launch {
      controllerManager.menuButtons.collect { _menuInputs.tryEmit(MenuInput.Button(it)) }
    }
    reloadSongs()
    viewModelScope.launch {
      controllerManager.gameInputEvents.collect { event ->
        processInputEvent(event)
      }
    }
  }

  /** Scans bundled songs plus [songsDirs]; called again once storage access is granted. */
  fun reloadSongs() {
    val assets = assets ?: return
    run {
      viewModelScope.launch {
        val loaded = withContext(Dispatchers.IO) {
          val external = songsDirs.flatMap { dir ->
            runCatching { dir.mkdirs(); SongLibrary.loadFromDirectory(dir) }
              .onFailure { Log.e(TAG, "Failed to scan ${dir.path}", it) }.getOrNull().orEmpty()
          }
          (SongLibrary.loadFromAssets(assets) + external).sortedBy { it.title.lowercase() }
        }
        _songs.value = loaded
        _songsLoading.value = false
        scoreStore?.let { store ->
          val validChecks = mutableMapOf<String, String>()
          validChecks[com.example.core.scores.PRACTICE_CHART_KEY] = com.example.core.scores.practiceChartCheck()
          for (song in loaded) {
            for (chart in song.charts) {
              validChecks[chartKey(song, chart)] = com.example.core.scores.chartCheck(chart)
            }
          }
          val pruned = withContext(Dispatchers.IO) { store.pruneInvalidRecords(validChecks) }
          _bestScores.value = pruned
        }
      }
    }
  }

  /**
   * Unified physical controller input processing pipeline.
   * Converts the hardware [android.view.KeyEvent.getEventTime] (stored in [GameInputEvent.timestamp])
   * into authoritative [GameplayClock] coordinates rather than using the last rendered frame time.
   * While paused, gameplay input transitions and note judgments are strictly rejected.
   */
  fun processInputEvent(event: GameInputEvent) {
    // Arrow presses also drive menus (the pause menu included), so emit before the pause gate
    if (event.isPressed && !_inputState.value.isLanePressed(event.lane)) {
      _menuInputs.tryEmit(MenuInput.Arrow(event.lane))
    }
    if (_isPaused.value) {
      return
    }

    val previousPressed = _inputState.value.isLanePressed(event.lane)
    val updatedInputState = _inputState.value.applyEvent(event)
    _inputState.value = updatedInputState
    val nowPressed = updatedInputState.isLanePressed(event.lane)

    // Trigger note judgment only on a fresh lane press transition during an active round
    if (!previousPressed && nowPressed && event.isPressed) {
      val currentRound = _roundState.value
      if (currentRound.isRunning && !currentRound.isPaused && !currentRound.isCompleted) {
        val hitGameTimeMs = if (gameplayClock.isStarted) {
          gameplayClock.toGameplayTimeMs(event.timestamp)
        } else {
          currentRound.currentGameTimeMs
        }
        val judged = currentRound.onLanePressed(
          lane = event.lane,
          inputGameTimeMs = hitGameTimeMs - _inputOffsetMs.value
        )
        _roundState.value = judged
        if (judged.judgmentSequence != currentRound.judgmentSequence) {
          judged.lastJudgment
            ?.takeIf { it.judgment != com.example.core.model.Judgment.MISS }
            ?.let { feedAutoSync(it.timingErrorMs) }
        }
      }
    }
  }

  /**
   * Starts or restarts a fresh scrolling practice round from 0 ms anchored to [GameplayClock].
   */
  fun startNewRound() {
    currentSelection = null
    beginRound(GameplayRoundState.createNewRound(isRunning = true, scrollMod = _scrollMod.value))
  }

  /**
   * Starts [chart] of [song] with its music locked to the [GameplayClock].
   */
  fun startSongRound(song: Song, chart: Chart) {
    currentSelection = song to chart
    val round = GameplayRoundState.createSongRound(song, chart, isRunning = true, scrollMod = _scrollMod.value)
    beginRound(if (_holdsEnabled.value) round else stripHolds(round))
  }

  private val endlessGenerator: EndlessGenerator? by lazy {
    try {
      assets?.open("endless/phrases.json")?.bufferedReader()?.use { EndlessGenerator.parse(it.readText()) }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to load endless phrases", e)
      null
    }
  }

  /** Endless mode: random measures from the pattern library until the player quits. */
  fun startEndlessRound(level: Int = _endlessLevel.value) {
    currentSelection = null
    val song = pickEndlessSong()
    val round = if (song != null) {
      endlessSongRound(song, level, GameplayRoundState(
        bpm = EndlessGenerator.BPM,
        beatZeroTimeMs = PracticePatternGenerator.DEFAULT_BEAT_ZERO_MS,
        currentGameTimeMs = 0L,
        isRunning = true,
        notes = emptyList(),
        scrollMod = _scrollMod.value,
        endlessLevel = level
      ), musicStartMs = ENDLESS_FIRST_SONG_START_MS)
    } else {
      // No constant-tempo song in the library: silent 130 BPM grid
      endlessSong = null
      endlessFirstMeasure = ENDLESS_LEAD_IN_MEASURES
      endlessLastMeasure = Int.MAX_VALUE
      val bpm = EndlessGenerator.BPM
      GameplayRoundState(
        bpm = bpm,
        beatZeroTimeMs = PracticePatternGenerator.DEFAULT_BEAT_ZERO_MS,
        currentGameTimeMs = 0L,
        isRunning = true,
        notes = emptyList(),
        scrollMod = _scrollMod.value,
        readBpm = bpm.toDouble(),
        endlessLevel = level,
        // Empty lead-in measures so no arrows scroll in during the countdown
        endlessNextMeasure = ENDLESS_LEAD_IN_MEASURES,
        meta = endlessMeta(level)
      )
    }
    beginRound(topUpEndless(round))
  }

  // Endless music: the current song and the measure range its steps may use
  private var endlessSong: Song? = null
  private var endlessFirstMeasure = 0
  private var endlessLastMeasure = Int.MAX_VALUE
  private var endlessSongEndMs = Long.MAX_VALUE

  /** Random constant-tempo song from the library (bundled + Songs folder), not the one just played. */
  private fun pickEndlessSong(): Song? {
    val candidates = _songs.value.filter { it.musicPath != null && it.songTiming.isConstantTempo && it.charts.isNotEmpty() }
    return (candidates.filter { it.folder != endlessSong?.folder }.ifEmpty { candidates }).randomOrNull()
  }

  /**
   * Switches [round] onto [song]: its music starts at [musicStartMs] and its beat grid times the
   * generated measures, from the song's first chart step to its last (intro/outro stay empty).
   */
  private fun endlessSongRound(song: Song, level: Int, round: GameplayRoundState, musicStartMs: Long): GameplayRoundState {
    val timing = song.songTiming
    val steps = song.charts.flatMap { it.steps }
    val firstStep = steps.minOfOrNull { it.timeSeconds } ?: 0.0
    val lastStep = steps.maxOfOrNull { it.endTimeSeconds ?: it.timeSeconds } ?: 0.0
    val bpb = EndlessGenerator.BEATS_PER_MEASURE
    endlessSong = song
    endlessFirstMeasure = kotlin.math.ceil(timing.beatAtTime(firstStep) / bpb - 1e-6).toInt().coerceAtLeast(0)
    endlessLastMeasure = (timing.beatAtTime(lastStep) / bpb).toInt() - 1
    endlessSongEndMs = musicStartMs + ((lastStep + ENDLESS_SONG_TAIL_S) * 1000).toLong()
    return round.copy(
      bpm = timing.bpmValues.first().roundToInt(),
      musicStartMs = musicStartMs,
      timing = timing,
      readBpm = song.readBpm,
      endlessNextMeasure = endlessFirstMeasure,
      meta = endlessMeta(level)
    )
  }

  /** At the end of the current song's steps, the next song starts after a short gap. */
  private fun advanceEndlessSong(round: GameplayRoundState): GameplayRoundState {
    if (endlessSong == null || round.currentGameTimeMs < endlessSongEndMs) return round
    val next = pickEndlessSong() ?: return round
    songAudio?.stop()
    audioStarted = false
    next.musicPath?.let { songAudio?.prepare(it) }
    // Beats restart at 0 on the new grid. The old song's notes (all judged) stay for scoring,
    // moved far back in beats so the X/M-mod renderer keeps them off screen.
    val retired = round.notes.map { n ->
      n.copy(beat = n.beat - ENDLESS_RETIRED_BEAT_SHIFT, holdEndBeat = n.holdEndBeat?.minus(ENDLESS_RETIRED_BEAT_SHIFT))
    }
    return endlessSongRound(next, round.endlessLevel ?: 0, round.copy(notes = retired),
      musicStartMs = round.currentGameTimeMs + ENDLESS_SONG_GAP_MS)
  }

  // Endless tech filter: bits of EndlessGenerator.TECH the player has switched off, persisted
  private val _endlessBlockedTech = MutableStateFlow(prefs?.getInt(PREF_ENDLESS_BLOCKED_TECH, 0) ?: 0)
  val endlessBlockedTech: StateFlow<Int> = _endlessBlockedTech.asStateFlow()

  /** Phrase counts per tech type at each endless level (null until the pattern library loads). */
  val endlessTechCounts: List<IntArray>? by lazy {
    endlessGenerator?.let { gen -> List(gen.levelCount) { gen.techCounts(it) } }
  }

  fun toggleEndlessTech(index: Int) {
    val blocked = _endlessBlockedTech.value xor (1 shl index)
    _endlessBlockedTech.value = blocked
    prefs?.edit()?.putInt(PREF_ENDLESS_BLOCKED_TECH, blocked)?.apply()
    _roundState.value.endlessLevel?.let { setEndlessLevel(it) }
  }

  private val _endlessLevel = MutableStateFlow(prefs?.getInt(PREF_ENDLESS_LEVEL, 0) ?: 0)

  /** Changes endless difficulty; measures not yet near the screen are regenerated at the new level. */
  fun setEndlessLevel(level: Int) {
    val gen = endlessGenerator ?: return
    val lvl = level.coerceIn(0, gen.levelCount - 1)
    _endlessLevel.value = lvl
    prefs?.edit()?.putInt(PREF_ENDLESS_LEVEL, lvl)?.apply()
    _roundState.update { round ->
      if (!round.isEndless) return@update round
      // Keep measures that start within ENDLESS_KEEP_MS (already scrolling into view)
      val keepUntil = round.currentGameTimeMs + ENDLESS_KEEP_MS
      var firstDropped = round.endlessNextMeasure
      while (firstDropped > endlessFirstMeasure &&
        round.gameTimeAtBeat(((firstDropped - 1) * EndlessGenerator.BEATS_PER_MEASURE).toDouble()) > keepUntil
      ) firstDropped--
      val cutBeat = (firstDropped * EndlessGenerator.BEATS_PER_MEASURE).toDouble()
      topUpEndless(
        round.copy(
          notes = round.notes.filter { it.beat < cutBeat },
          endlessLevel = lvl,
          endlessNextMeasure = firstDropped,
          meta = endlessMeta(lvl)
        )
      )
    }
  }

  private fun endlessMeta(level: Int) = RoundMeta(
    title = endlessSong?.let { "Endless · ${it.title}" } ?: "Endless",
    artist = endlessSong?.artist ?: "ITGmania pattern library",
    backgroundPath = endlessSong?.backgroundPath,
    musicPath = endlessSong?.musicPath,
    difficultyName = EndlessGenerator.LEVEL_NAMES.getOrElse(level) { "Level ${level + 1}" },
    difficultyIndex = level,
    meter = level + 1,
    bpmLabel = endlessSong?.displayBpm ?: "${EndlessGenerator.BPM}"
  )

  /** Appends measures until notes reach ENDLESS_LOOKAHEAD_MS past the current time. */
  private fun topUpEndless(round: GameplayRoundState): GameplayRoundState {
    val gen = endlessGenerator ?: return round
    val level = round.endlessLevel ?: return round
    var measure = round.endlessNextMeasure
    val added = mutableListOf<com.example.core.model.PracticeNote>()
    var nextId = (round.notes.maxOfOrNull { it.id } ?: 0) + 1
    while (measure <= endlessLastMeasure &&
      round.gameTimeAtBeat((measure * EndlessGenerator.BEATS_PER_MEASURE).toDouble()) <
      round.currentGameTimeMs + ENDLESS_LOOKAHEAD_MS
    ) {
      val notes = gen.measureNotes(measure, level, nextId, _endlessBlockedTech.value) { round.gameTimeAtBeat(it) }
      nextId += notes.size
      added += notes
      measure++
    }
    if (measure == round.endlessNextMeasure) return round
    val newNotes = if (_holdsEnabled.value) added else added.map { it.copy(holdEndMs = null, holdEndBeat = null) }
    return round.copy(notes = round.notes + newNotes, endlessNextMeasure = measure)
  }

  private fun beginRound(round: GameplayRoundState) {
    clearPressedStates()
    _inputState.value = _inputState.value.resetAll()
    _isPaused.value = false
    _lastResult.value = null
    songAudio?.stop()
    audioStarted = false
    round.meta.musicPath?.let { songAudio?.prepare(it) }
    gameplayClock.start()
    _roundState.value = round
  }

  /**
   * Restarts the current round (practice or song) from the beginning.
   */
  fun restartRound() {
    val selection = currentSelection
    val endless = _roundState.value.endlessLevel
    if (endless != null) {
      startEndlessRound(endless)
    } else if (selection != null) {
      startSongRound(selection.first, selection.second)
    } else {
      startNewRound()
    }
  }

  /** Loops the song's SAMPLESTART/SAMPLELENGTH preview on the music wheel. */
  fun previewSong(song: Song?) {
    val audio = songAudio ?: return
    // Never let the wheel preview touch a gameplay song (the select screen fades out after start)
    if (_roundState.value.isRunning) return
    val path = song?.musicPath
    if (path == null) {
      audio.stopPreview()
      return
    }
    audio.playPreview(
      assetPath = path,
      startMs = (song.sampleStartSeconds * 1000).toLong(),
      lengthMs = (song.sampleLengthSeconds * 1000).toLong()
    )
  }

  fun stopPreview() {
    songAudio?.stopPreview()
  }

  /**
   * Starts the music when the clock reaches [GameplayRoundState.musicStartMs] and then keeps the
   * clock locked to the audible music position. Corrections move the clock anchor
   * ([GameplayClock.nudge]); elapsed time is still derived from monotonic uptime.
   */
  private fun syncAudio(round: GameplayRoundState) {
    val audio = songAudio ?: return
    val musicStart = round.musicStartMs ?: return
    val elapsed = gameplayClock.elapsedGameplayMs()
    val songPos = elapsed - musicStart
    if (!audioStarted) {
      if (songPos >= 0L) {
        audio.playFrom(songPos)
        audioStarted = true
        lastAudioSyncUptimeMs = gameplayClock.nowUptimeMs()
      }
      return
    }
    if (!audio.isPlaying) return
    val now = gameplayClock.nowUptimeMs()
    if (now - lastAudioSyncUptimeMs < AUDIO_SYNC_INTERVAL_MS) return
    lastAudioSyncUptimeMs = now
    val audioPos = audio.positionMs()
    if (audioPos < AUDIO_SYNC_MIN_POSITION_MS) return
    val drift = songPos - audioPos // > 0: clock ahead of the music
    if (abs(drift) >= AUDIO_SYNC_DEADBAND_MS) Log.d(TAG, "drift=${drift}ms audio=${audioPos}ms")
    when {
      abs(drift) >= AUDIO_SYNC_SNAP_MS -> gameplayClock.nudge(drift)
      abs(drift) >= AUDIO_SYNC_DEADBAND_MS -> gameplayClock.nudge(drift / 2)
    }
  }

  /**
   * Synchronizes the round state directly to the authoritative monotonic [GameplayClock].
   * Called on each display frame in GameplayScreen without accumulating frame deltas.
   */
  fun syncRoundClock() {
    val current = _roundState.value
    if (!current.isRunning || current.isPaused || current.isCompleted) return
    if (current.isSongRound) syncAudio(current)
    val elapsedMs = gameplayClock.elapsedGameplayMs()
    // Pressed lanes feed StepMania-style hold/roll life
    val heldLanes = GameLane.entries.filterTo(mutableSetOf()) { _inputState.value.isLanePressed(it) }
    val updated = topUpEndless(advanceEndlessSong(current.advanceTime(elapsedMs, heldLanes)))
    _roundState.value = updated
    if (updated.isCompleted) {
      gameplayClock.stop()
      songAudio?.stop()
      audioStarted = false

      val song = currentSelection?.first
      val chart = currentSelection?.second
      val record = ScoreRecord(
        key = chartKey(song, chart),
        chartCheck = chartCheckForRound(song, chart),
        percentDp = updated.percentDp,
        gradeTier = updated.gradeTier,
        score = updated.score,
        maxCombo = updated.maxCombo,
        w1Count = updated.w1Count,
        w2Count = updated.w2Count,
        w3Count = updated.w3Count,
        w4Count = updated.w4Count,
        w5Count = updated.w5Count,
        missCount = updated.missCount,
        heldCount = updated.heldCount,
        letGoCount = updated.letGoCount,
        fullCombo = updated.fullComboKind,
        scrollMod = updated.scrollMod.serialize(),
        dateIso = java.time.LocalDate.now().toString(),
        playCount = 1
      )
      scoreStore?.let { store ->
        try {
          val result = store.submit(record)
          _bestScores.value = store.scores
          _lastResult.value = result
        } catch (e: Exception) {
          Log.e(TAG, "Failed to persist score record; not publishing unpersisted result", e)
        }
      }
    }
  }

  /**
   * Advances the scrolling gameplay clock by [deltaMs] milliseconds (for deterministic tests).
   */
  fun advanceRoundBy(deltaMs: Long) {
    if (deltaMs <= 0L) return
    val current = _roundState.value
    if (!current.isRunning || current.isPaused || current.isCompleted) return
    gameplayClock.anchorZeroForManualStep()
    val updated = current.advanceTime(current.currentGameTimeMs + deltaMs)
    _roundState.value = updated
  }

  /**
   * Sets the round clock to an explicit [gameTimeMs] timestamp (for deterministic tests).
   */
  fun updateRoundTimeMs(gameTimeMs: Long) {
    val current = _roundState.value
    if (!current.isRunning || current.isPaused || current.isCompleted) return
    gameplayClock.anchorZeroForManualStep()
    _roundState.value = current.advanceTime(gameTimeMs)
  }

  fun toggleDebugOverlay() {
    _isDebugOverlayVisible.value = !_isDebugOverlayVisible.value
  }

  fun togglePause() {
    val currentRound = _roundState.value
    if (currentRound.isCompleted) return

    if (_isPaused.value) {
      resumeGame()
    } else {
      pauseGame()
    }
  }

  fun pauseGame() {
    val currentRound = _roundState.value
    if (currentRound.isCompleted || _isPaused.value) return
    gameplayClock.pause()
    val frozenTimeMs = if (gameplayClock.isStarted && gameplayClock.startUptimeMs > 0L) {
      gameplayClock.elapsedGameplayMs()
    } else {
      currentRound.currentGameTimeMs
    }
    _isPaused.value = true
    if (audioStarted) songAudio?.pause()
    _roundState.value = currentRound.advanceTime(frozenTimeMs).copy(isPaused = true)
    clearPressedStates()
  }

  fun resumeGame() {
    if (!_isPaused.value) return
    gameplayClock.resume()
    _isPaused.value = false
    if (audioStarted) {
      songAudio?.resume()
      lastAudioSyncUptimeMs = gameplayClock.nowUptimeMs()
    }
    val currentRound = _roundState.value
    if (!currentRound.isCompleted) {
      _roundState.value = currentRound.copy(isPaused = false, isRunning = true)
    }
  }

  fun stopRound() {
    gameplayClock.stop()
    songAudio?.stop()
    audioStarted = false
    _isPaused.value = false
    _lastResult.value = null
    clearPressedStates()
    _roundState.value = _roundState.value.copy(isRunning = false, isPaused = false)
  }

  fun clearPressedStates() {
    controllerManager.clearAllPressedStates()
    _inputState.value = _inputState.value.clearPressedStates()
  }

  fun resetCounters() {
    _inputState.value = _inputState.value.resetAll()
  }

  fun onActivityPaused() {
    clearPressedStates()
    if (_roundState.value.isRunning && !_roundState.value.isCompleted) {
      pauseGame()
    }
  }

  fun onActivityResumed() {
    controllerManager.scanForControllers()
  }

  // Controller mapping delegates
  fun startCapture(lane: GameLane) {
    controllerManager.startCapture(lane)
  }

  fun cancelCapture() {
    controllerManager.cancelCapture()
  }

  fun resetMappings() {
    controllerManager.resetMappingsToDefaults()
  }

  fun scanControllers() {
    controllerManager.scanForControllers()
  }

  override fun onCleared() {
    super.onCleared()
    controllerManager.release()
    songAudio?.release()
  }

  companion object {
    private const val TAG = "AudioSync"
    private const val PREF_SCROLL_MOD = "scroll_mod"
    private const val PREF_ENDLESS_LEVEL = "endless_level"
    private const val PREF_HOLDS_ENABLED = "holds_enabled"
    private const val PREF_ENDLESS_BLOCKED_TECH = "endless_blocked_tech"
    /** Endless notes are generated this far ahead of the clock. */
    private const val ENDLESS_LOOKAHEAD_MS = 6_000L
    /** On a level change, measures starting within this window stay (past the top of every speed preset). */
    private const val ENDLESS_KEEP_MS = 3_000L
    /** Two measures at 130 BPM ≈ 3.7 s after GO before the first endless arrow. */
    private const val ENDLESS_LEAD_IN_MEASURES = 2
    /** First endless song starts right after the 3-2-1-GO countdown. */
    private const val ENDLESS_FIRST_SONG_START_MS = 3_000L
    /** Silence between endless songs. */
    private const val ENDLESS_SONG_GAP_MS = 2_000L
    /** Music keeps playing this long after the last chart step before the next song. */
    private const val ENDLESS_SONG_TAIL_S = 3.0
    private const val ENDLESS_RETIRED_BEAT_SHIFT = 1_000_000.0
    private const val PREF_PROFILE = "player_profile"
    private const val PREF_INPUT_OFFSET = "input_offset_ms"
    const val MAX_INPUT_OFFSET_MS = 250
    const val AUDIO_SYNC_INTERVAL_MS = 250L
    const val AUDIO_SYNC_MIN_POSITION_MS = 150L
    const val AUDIO_SYNC_DEADBAND_MS = 8L
    const val AUDIO_SYNC_SNAP_MS = 40L
  }

  class Factory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      val appContext = context.applicationContext
      val store = SharedPreferencesMappingStore(appContext)
      val manager = ControllerManager(appContext, store)
      return RhythmGameViewModel(
        controllerManager = manager,
        songAudio = ExoSongAudio(appContext),
        assets = appContext.assets,
        songsDirs = listOf(
          // Shared folder (needs "All files access"): adb push or a file manager
          java.io.File(android.os.Environment.getExternalStorageDirectory(), "RhythmPad7/Songs"),
          java.io.File(appContext.filesDir, "Songs")
        ),
        prefs = appContext.getSharedPreferences("rhythmpad7_settings", Context.MODE_PRIVATE),
        scoreStoreForProfile = { profile -> ScoreStore(java.io.File(appContext.filesDir, profile.scoreFileName)) }
      ) as T
    }
  }
}
