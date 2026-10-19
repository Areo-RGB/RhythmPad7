package com.example.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.core.model.GameLane
import com.example.core.model.Judgment

/**
 * Geometric vector paths and rendering routines for DDR/StepMania style arrow receptors,
 * scrolling note arrows, hit bursts, and lane visual feedback using Jetpack Compose Canvas.
 */
object ArrowDrawing {

  /**
   * Builds a bold, standing-readable arrow path pointing UP, centered at (centerX, centerY)
   * with given radius. Can be rotated to point LEFT (270°), RIGHT (90°), or DOWN (180°).
   */
  fun createUpArrowPath(centerX: Float, centerY: Float, radius: Float): Path {
    val path = Path()
    // Canonical StepMania arrow geometry normalized with high-visibility wing & tail proportions
    val topTip = Offset(centerX, centerY - radius * 0.96f)
    val rightWing = Offset(centerX + radius * 0.94f, centerY + radius * 0.04f)
    val rightInner = Offset(centerX + radius * 0.44f, centerY - radius * 0.04f)
    val rightBottom = Offset(centerX + radius * 0.44f, centerY + radius * 0.92f)
    val bottomNotch = Offset(centerX, centerY + radius * 0.56f)
    val leftBottom = Offset(centerX - radius * 0.44f, centerY + radius * 0.92f)
    val leftInner = Offset(centerX - radius * 0.44f, centerY - radius * 0.04f)
    val leftWing = Offset(centerX - radius * 0.94f, centerY + radius * 0.04f)

    path.moveTo(topTip.x, topTip.y)
    path.lineTo(rightWing.x, rightWing.y)
    path.lineTo(rightInner.x, rightInner.y)
    path.lineTo(rightBottom.x, rightBottom.y)
    path.lineTo(bottomNotch.x, bottomNotch.y)
    path.lineTo(leftBottom.x, leftBottom.y)
    path.lineTo(leftInner.x, leftInner.y)
    path.lineTo(leftWing.x, leftWing.y)
    path.close()
    return path
  }

  fun getRotationForLane(lane: GameLane): Float {
    return when (lane) {
      GameLane.LEFT -> 270f
      GameLane.DOWN -> 180f
      GameLane.UP -> 0f
      GameLane.RIGHT -> 90f
    }
  }

  /**
   * Draws a fixed target arrow receptor at the top of the lane with idle and pressed visual feedback.
   */
  fun drawReceptor(
    drawScope: DrawScope,
    lane: GameLane,
    center: Offset,
    size: Float,
    isPressed: Boolean
  ) {
    val rotation = getRotationForLane(lane)
    val arrowRadius = size * 0.46f
    val arrowPath = createUpArrowPath(center.x, center.y, arrowRadius)
    val laneColor = lane.defaultColor

    drawScope.rotate(degrees = rotation, pivot = center) {
      if (isPressed) {
        // 1. Outer Glow Aura
        val glowRadius = arrowRadius * 1.24f
        val glowPath = createUpArrowPath(center.x, center.y, glowRadius)
        drawPath(
          path = glowPath,
          color = laneColor.copy(alpha = 0.38f),
          style = Fill
        )

        // 2. Main Filled Neon Arrow
        drawPath(
          path = arrowPath,
          color = laneColor,
          style = Fill
        )

        // 3. Inner White Core Flash for authentic hit feedback
        val coreRadius = arrowRadius * 0.65f
        val corePath = createUpArrowPath(center.x, center.y, coreRadius)
        drawPath(
          path = corePath,
          color = Color.White.copy(alpha = 0.88f),
          style = Fill
        )

        // 4. Sharp crisp white border
        drawPath(
          path = arrowPath,
          color = Color.White,
          style = Stroke(width = size * 0.045f)
        )
      } else {
        // Idle/Unpressed Receptor State (High-contrast target silhouette readable from standing distance)
        // 1. Semi-translucent dark glass backdrop
        drawPath(
          path = arrowPath,
          color = laneColor.copy(alpha = 0.18f),
          style = Fill
        )

        // 2. Colored inner rim stroke
        drawPath(
          path = arrowPath,
          color = laneColor.copy(alpha = 0.62f),
          style = Stroke(width = size * 0.058f)
        )

        // 3. Crisp outer metallic outline
        drawPath(
          path = arrowPath,
          color = Color(0xFFCBD5E1),
          style = Stroke(width = size * 0.026f)
        )

        // 4. Center alignment dot
        drawCircle(
          color = laneColor.copy(alpha = 0.72f),
          radius = arrowRadius * 0.13f,
          center = center
        )
      }
    }
  }

  /**
   * Draws an upward-scrolling note arrow in the given lane.
   * Distinct from the stationary hollow receptor by its solid vibrant neon fill,
   * bright inner core, and crisp white rim.
   */
  fun drawScrollingNote(
    drawScope: DrawScope,
    lane: GameLane,
    center: Offset,
    size: Float,
    isMissed: Boolean = false
  ) {
    val rotation = getRotationForLane(lane)
    val arrowRadius = size * 0.46f
    val arrowPath = createUpArrowPath(center.x, center.y, arrowRadius)
    val baseColor = if (isMissed) Color(0xFFEF4444) else lane.defaultColor
    val alphaMultiplier = if (isMissed) 0.38f else 1.0f

    drawScope.rotate(degrees = rotation, pivot = center) {
      // 1. Subtle outer bloom around approaching note
      if (!isMissed) {
        val haloPath = createUpArrowPath(center.x, center.y, arrowRadius * 1.15f)
        drawPath(
          path = haloPath,
          color = baseColor.copy(alpha = 0.28f),
          style = Fill
        )
      }

      // 2. Solid high-contrast note fill
      drawPath(
        path = arrowPath,
        color = baseColor.copy(alpha = 0.96f * alphaMultiplier),
        style = Fill
      )

      // 3. Inner bright bevel highlight
      val innerPath = createUpArrowPath(center.x, center.y, arrowRadius * 0.64f)
      drawPath(
        path = innerPath,
        color = Color.White.copy(alpha = 0.60f * alphaMultiplier),
        style = Fill
      )

      // 4. Crisp outer white border
      drawPath(
        path = arrowPath,
        color = Color.White.copy(alpha = 0.96f * alphaMultiplier),
        style = Stroke(width = size * 0.046f)
      )

      // 5. Inner dark contrast contour line
      drawPath(
        path = innerPath,
        color = Color(0xFF060911).copy(alpha = 0.60f * alphaMultiplier),
        style = Stroke(width = size * 0.022f)
      )
    }
  }

  /**
   * Draws an expanding hit burst / flash ring on the receptor when a note is judged EXCELLENT or GOOD.
   * [progress] is normalized in 0f..1f over the burst duration (~220ms).
   */
  fun drawHitBurst(
    drawScope: DrawScope,
    lane: GameLane,
    center: Offset,
    size: Float,
    judgment: Judgment,
    progress: Float
  ) {
    val clamped = progress.coerceIn(0f, 1f)
    val alpha = (1f - clamped).coerceIn(0f, 1f)
    val scale = 1.0f + (clamped * 0.55f)
    val burstColor = judgment.color
    val rotation = getRotationForLane(lane)
    val arrowRadius = size * 0.46f * scale
    val burstPath = createUpArrowPath(center.x, center.y, arrowRadius)

    drawScope.rotate(degrees = rotation, pivot = center) {
      drawPath(
        path = burstPath,
        color = burstColor.copy(alpha = 0.45f * alpha),
        style = Fill
      )
      drawPath(
        path = burstPath,
        color = Color.White.copy(alpha = 0.92f * alpha),
        style = Stroke(width = size * 0.055f * (1f - clamped * 0.5f))
      )
    }

    drawScope.drawCircle(
      color = burstColor.copy(alpha = 0.55f * alpha),
      radius = size * 0.54f * scale,
      center = center,
      style = Stroke(width = 4.5f * alpha)
    )
  }

  /**
   * Draws the vertical lane track, center guide line, clear lane separators,
   * and the laser light beam when the lane is pressed.
   */
  fun drawLaneTrack(
    drawScope: DrawScope,
    lane: GameLane,
    laneLeft: Float,
    laneWidth: Float,
    laneHeight: Float,
    receptorY: Float,
    isPressed: Boolean
  ) {
    val laneColor = lane.defaultColor
    val laneCenterX = laneLeft + (laneWidth / 2f)

    // Deep dark highway lane background (alternating subtle tint for clear column distinction)
    val laneTint = if (lane.ordinal % 2 == 0) Color(0xFF050811) else Color(0xFF070C18)
    drawScope.drawRect(
      color = laneTint,
      topLeft = Offset(laneLeft, 0f),
      size = Size(laneWidth, laneHeight)
    )

    // Subtle center-of-lane guide line
    drawScope.drawLine(
      color = laneColor.copy(alpha = 0.10f),
      start = Offset(laneCenterX, receptorY),
      end = Offset(laneCenterX, laneHeight),
      strokeWidth = 1f
    )

    // Clear vertical lane separator lines
    drawScope.drawLine(
      color = Color(0x77334155),
      start = Offset(laneLeft, 0f),
      end = Offset(laneLeft, laneHeight),
      strokeWidth = 2f
    )
    drawScope.drawLine(
      color = Color(0x77334155),
      start = Offset(laneLeft + laneWidth, 0f),
      end = Offset(laneLeft + laneWidth, laneHeight),
      strokeWidth = 2f
    )

    // Pressed Laser Beam
    if (isPressed) {
      val beamBrush = Brush.verticalGradient(
        colors = listOf(
          laneColor.copy(alpha = 0.45f),
          laneColor.copy(alpha = 0.22f),
          laneColor.copy(alpha = 0.06f),
          Color.Transparent
        ),
        startY = receptorY,
        endY = laneHeight
      )
      drawScope.drawRect(
        brush = beamBrush,
        topLeft = Offset(laneLeft + 2f, receptorY - 36f),
        size = Size(laneWidth - 4f, laneHeight - receptorY + 36f)
      )

      // Accent border glow on active lane boundaries
      drawScope.drawLine(
        color = laneColor.copy(alpha = 0.78f),
        start = Offset(laneLeft, receptorY - 36f),
        end = Offset(laneLeft, laneHeight),
        strokeWidth = 3f
      )
      drawScope.drawLine(
        color = laneColor.copy(alpha = 0.78f),
        start = Offset(laneLeft + laneWidth, receptorY - 36f),
        end = Offset(laneLeft + laneWidth, laneHeight),
        strokeWidth = 3f
      )
    }
  }
}
