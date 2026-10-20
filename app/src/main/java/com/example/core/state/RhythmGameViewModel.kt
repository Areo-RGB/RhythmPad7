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
import com.example.core.gameplay.GameplayClock
import com.example.core.gameplay.GameplayRoundState
import com.example.core.gameplay.ScrollMod
import com.example.core.input.ControllerManager
import com.example.core.input.SharedPreferencesMappingStore
import com.example.core.model.ControllerInfo
import com.example.core.model.ControllerMapping
import com.example.core.model.GameInputEvent
import com.example.core.model.GameLane
import com.example.core.model.MenuInput
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.example.core.model.RawInputEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
  private val prefs: SharedPreferences? = null
) : ViewModel() {

  // Scroll speed modifier (StepMania XMod / MMod / CMod), persisted across launches
  private val _scrollMod = MutableStateFlow(
    ScrollMod.parse(prefs?.getString(PREF_SCROLL_MOD, null)) ?: ScrollMod.DEFAULT
  )
  val scrollMod: StateFlow<ScrollMod> = _scrollMod.asStateFlow()

  fun setScrollMod(mod: ScrollMod) {
    _scrollMod.value = mod
    prefs?.edit()?.putString(PREF_SCROLL_MOD, mod.serialize())?.apply()
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
    if (assets != null) {
      viewModelScope.launch {
        _songs.value = withContext(Dispatchers.IO) { SongLibrary.loadFromAssets(assets) }
        _songsLoading.value = false
      }
    }
    viewModelScope.launch {
      controllerManager.gameInputEvents.collect { event ->
        processInputEvent(event)
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
        _roundState.value = currentRound.onLanePressed(
          lane = event.lane,
          inputGameTimeMs = hitGameTimeMs
        )
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
    beginRound(GameplayRoundState.createSongRound(song, chart, isRunning = true, scrollMod = _scrollMod.value))
  }

  private fun beginRound(round: GameplayRoundState) {
    clearPressedStates()
    _inputState.value = _inputState.value.resetAll()
    _isPaused.value = false
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
    if (selection != null) {
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
    val updated = current.advanceTime(elapsedMs, heldLanes)
    _roundState.value = updated
    if (updated.isCompleted) {
      gameplayClock.stop()
      songAudio?.stop()
      audioStarted = false
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
        prefs = appContext.getSharedPreferences("rhythmpad7_settings", Context.MODE_PRIVATE)
      ) as T
    }
  }
}
