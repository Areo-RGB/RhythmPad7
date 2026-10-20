package com.example.core.audio

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Song music playback used by the gameplay clock sync and the music-select preview.
 * All calls must happen on the main thread (ExoPlayer's application looper).
 */
interface SongAudio {
  /** Loads [assetPath] (relative to `assets/`) paused at position 0. */
  fun prepare(assetPath: String)

  /** Seeks to [positionMs] and starts playing. */
  fun playFrom(positionMs: Long)
  fun pause()
  fun resume()
  fun stop()

  val isPlaying: Boolean

  /** Current audible music position in ms (ExoPlayer compensates for output latency). */
  fun positionMs(): Long

  /** Loops [lengthMs] of [assetPath] starting at [startMs] (ScreenSelectMusic sample). */
  fun playPreview(assetPath: String, startMs: Long, lengthMs: Long)

  /** Stops playback only if the preview (not a gameplay song) is playing. */
  fun stopPreview()
  fun release()
}

class ExoSongAudio(context: Context) : SongAudio {
  private val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build()
  private var preparedPath: String? = null
  private var isPreview = false

  private fun assetUri(path: String): Uri =
    Uri.parse("asset:///" + path.split('/').joinToString("/") { Uri.encode(it) })

  override fun prepare(assetPath: String) {
    isPreview = false
    player.repeatMode = Player.REPEAT_MODE_OFF
    player.playWhenReady = false
    player.setMediaItem(MediaItem.fromUri(assetUri(assetPath)))
    player.volume = 1f
    player.prepare()
    player.seekTo(0L)
    preparedPath = assetPath
  }

  override fun playFrom(positionMs: Long) {
    player.seekTo(positionMs.coerceAtLeast(0L))
    player.play()
  }

  override fun pause() {
    player.pause()
  }

  override fun resume() {
    player.play()
  }

  override fun stop() {
    player.stop()
    player.clearMediaItems()
    preparedPath = null
    isPreview = false
  }

  override val isPlaying: Boolean
    get() = player.isPlaying

  override fun positionMs(): Long = player.currentPosition

  override fun playPreview(assetPath: String, startMs: Long, lengthMs: Long) {
    isPreview = true
    val item = MediaItem.Builder()
      .setUri(assetUri(assetPath))
      .setClippingConfiguration(
        MediaItem.ClippingConfiguration.Builder()
          .setStartPositionMs(startMs.coerceAtLeast(0L))
          .setEndPositionMs(startMs.coerceAtLeast(0L) + lengthMs.coerceAtLeast(1_000L))
          .build()
      )
      .build()
    player.setMediaItem(item)
    player.repeatMode = Player.REPEAT_MODE_ONE
    player.volume = 0.8f
    player.prepare()
    player.play()
    preparedPath = null
  }

  override fun stopPreview() {
    if (isPreview) stop()
  }

  override fun release() {
    player.release()
  }
}
