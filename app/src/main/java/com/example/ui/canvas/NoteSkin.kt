package com.example.ui.canvas

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.R
import com.example.core.model.GameLane
import com.example.core.model.Judgment
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Bitmaps of the StepMania `dance/default` noteskin
 * (`stepmania-pcapp/NoteSkins/dance/default`).
 *
 * All source sprites point DOWN; [NoteSkinDrawing.rotationFor] rotates them per lane.
 */
@Immutable
class NoteSkin(
  val arrows: ImageBitmap,
  val receptor: ImageBitmap,
  val receptorFlash: ImageBitmap,
  val glow: ImageBitmap
) {
  /** `_arrow 1x8`: one 128 px frame per quantization (4th, 8th, 12th, 16th, 24th, 32nd, 48th, 64th). */
  val arrowFrameSize: Int get() = arrows.width
}

@Composable
fun rememberNoteSkin(): NoteSkin {
  val arrows = ImageBitmap.imageResource(R.drawable.sm_note_arrows)
  val receptor = ImageBitmap.imageResource(R.drawable.sm_receptor)
  val flash = ImageBitmap.imageResource(R.drawable.sm_receptor_flash)
  val glow = ImageBitmap.imageResource(R.drawable.sm_tap_glow)
  return remember(arrows, receptor, flash, glow) { NoteSkin(arrows, receptor, flash, glow) }
}

object NoteSkinDrawing {

  /** Explosion: full brightness for 100 ms, then a 500 ms decelerating fade (GhostArrowBright W1Command). */
  const val EXPLOSION_DURATION_MS = 600L
  private const val EXPLOSION_HOLD_MS = 100L

  fun rotationFor(lane: GameLane): Float = when (lane) {
    GameLane.LEFT -> 90f
    GameLane.DOWN -> 0f
    GameLane.UP -> 180f
    GameLane.RIGHT -> 270f
  }

  /** StepMania note quantization → noteskin frame index (red 4th, blue 8th, green 12th, yellow 16th, ...). */
  fun quantizationFrame(beat: Double): Int {
    val frac = beat - kotlin.math.floor(beat)
    fun isMultiple(divisions: Int): Boolean {
      val scaled = frac * divisions
      return abs(scaled - scaled.roundToInt()) < 0.01
    }
    return when {
      isMultiple(1) -> 0
      isMultiple(2) -> 1
      isMultiple(3) -> 2
      isMultiple(4) -> 3
      isMultiple(6) -> 4
      isMultiple(8) -> 5
      isMultiple(12) -> 6
      else -> 7
    }
  }

  /** Receptor beat pulse (ReceptorArrow: diffuseramp .8 → 1, effecttiming .2/0/.8/0, offset .05). */
  fun receptorBrightness(beatPhase: Float): Float {
    val p = ((beatPhase + 0.05f) % 1f + 1f) % 1f
    val ramp = if (p < 0.2f) p / 0.2f else 1f - (p - 0.2f) / 0.8f
    return 0.8f + 0.2f * ramp
  }

  private fun DrawScope.drawSprite(
    image: ImageBitmap,
    center: Offset,
    size: Float,
    srcOffset: IntOffset = IntOffset.Zero,
    srcSize: IntSize = IntSize(image.width, image.height),
    alpha: Float = 1f,
    colorFilter: ColorFilter? = null,
    blendMode: BlendMode = BlendMode.SrcOver
  ) {
    val half = size / 2f
    drawImage(
      image = image,
      srcOffset = srcOffset,
      srcSize = srcSize,
      dstOffset = IntOffset((center.x - half).roundToInt(), (center.y - half).roundToInt()),
      dstSize = IntSize(size.roundToInt(), size.roundToInt()),
      alpha = alpha.coerceIn(0f, 1f),
      colorFilter = colorFilter,
      blendMode = blendMode,
      filterQuality = FilterQuality.High
    )
  }

  fun drawReceptor(
    drawScope: DrawScope,
    skin: NoteSkin,
    lane: GameLane,
    center: Offset,
    size: Float,
    beatPhase: Float,
    pressAmount: Float,
    flashAlpha: Float
  ) {
    val brightness = receptorBrightness(beatPhase)
    val zoom = 1f - 0.15f * pressAmount
    drawScope.rotate(rotationFor(lane), pivot = center) {
      scale(zoom, pivot = center) {
        drawSprite(
          image = skin.receptor,
          center = center,
          size = size,
          colorFilter = ColorFilter.tint(Color(brightness, brightness, brightness), BlendMode.Modulate)
        )
      }
      if (flashAlpha > 0.01f) {
        val flashZoom = 1f + 0.2f * (1f - pressAmount) * (1f - flashAlpha)
        scale(flashZoom * 148f / 128f, pivot = center) {
          drawSprite(
            image = skin.receptorFlash,
            center = center,
            size = size,
            alpha = flashAlpha,
            blendMode = BlendMode.Plus
          )
        }
      }
    }
  }

  fun drawNote(
    drawScope: DrawScope,
    skin: NoteSkin,
    lane: GameLane,
    center: Offset,
    size: Float,
    quantFrame: Int,
    isMissed: Boolean
  ) {
    val frame = skin.arrowFrameSize
    drawScope.rotate(rotationFor(lane), pivot = center) {
      drawSprite(
        image = skin.arrows,
        center = center,
        size = size,
        srcOffset = IntOffset(0, frame * quantFrame.coerceIn(0, 7)),
        srcSize = IntSize(frame, frame),
        alpha = if (isMissed) 0.35f else 1f,
        colorFilter = if (isMissed) {
          ColorFilter.tint(Color(0xFF6A6A6A), BlendMode.Modulate)
        } else null
      )
    }
  }

  fun drawExplosion(
    drawScope: DrawScope,
    skin: NoteSkin,
    lane: GameLane,
    center: Offset,
    size: Float,
    judgment: Judgment,
    elapsedMs: Long
  ) {
    if (elapsedMs !in 0L..EXPLOSION_DURATION_MS) return
    val alpha = if (elapsedMs <= EXPLOSION_HOLD_MS) {
      1f
    } else {
      val t = (elapsedMs - EXPLOSION_HOLD_MS).toFloat() / (EXPLOSION_DURATION_MS - EXPLOSION_HOLD_MS)
      val decel = 1f - (1f - t) * (1f - t)
      1f - decel
    }
    val tint = when (judgment) {
      Judgment.EXCELLENT -> Color(1f, 1f, 1f)
      Judgment.GOOD -> Color(0.6f, 1f, 0.8f)
      Judgment.MISS -> return
    }
    drawScope.rotate(rotationFor(lane), pivot = center) {
      drawSprite(
        image = skin.glow,
        center = center,
        size = size * (1f + 0.08f * (1f - alpha)),
        alpha = alpha,
        colorFilter = ColorFilter.tint(tint, BlendMode.Modulate),
        blendMode = BlendMode.Plus
      )
    }
  }
}
