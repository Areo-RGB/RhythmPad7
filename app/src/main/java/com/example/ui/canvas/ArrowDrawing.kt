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

/**
 * Geometric vector paths and rendering routines for DDR/StepMania style arrow receptors
 * and lane visual feedback using Jetpack Compose Canvas.
 */
object ArrowDrawing {

  /**
   * Builds an arrow path pointing UP, centered at (centerX, centerY) with given radius.
   * Can be rotated to point LEFT (-90°), RIGHT (+90°), or DOWN (180°).
   */
  fun createUpArrowPath(centerX: Float, centerY: Float, radius: Float): Path {
    val path = Path()
    // Canonical StepMania arrow geometry normalized with center notch
    val topTip = Offset(centerX, centerY - radius * 0.95f)
    val rightWing = Offset(centerX + radius * 0.92f, centerY + radius * 0.05f)
    val rightInner = Offset(centerX + radius * 0.40f, centerY - radius * 0.05f)
    val rightBottom = Offset(centerX + radius * 0.40f, centerY + radius * 0.90f)
    val bottomNotch = Offset(centerX, centerY + radius * 0.52f)
    val leftBottom = Offset(centerX - radius * 0.40f, centerY + radius * 0.90f)
    val leftInner = Offset(centerX - radius * 0.40f, centerY - radius * 0.05f)
    val leftWing = Offset(centerX - radius * 0.92f, centerY + radius * 0.05f)

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
   * Draws a complete arrow receptor with idle and pressed visual feedback.
   */
  fun drawReceptor(
    drawScope: DrawScope,
    lane: GameLane,
    center: Offset,
    size: Float,
    isPressed: Boolean
  ) {
    val rotation = getRotationForLane(lane)
    val arrowRadius = size * 0.44f
    val arrowPath = createUpArrowPath(center.x, center.y, arrowRadius)
    val laneColor = lane.defaultColor

    drawScope.rotate(degrees = rotation, pivot = center) {
      if (isPressed) {
        // 1. Outer Glow Aura
        val glowRadius = arrowRadius * 1.25f
        val glowPath = createUpArrowPath(center.x, center.y, glowRadius)
        drawPath(
          path = glowPath,
          color = laneColor.copy(alpha = 0.35f),
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
          color = Color.White.copy(alpha = 0.85f),
          style = Fill
        )

        // 4. Sharp crisp white border
        drawPath(
          path = arrowPath,
          color = Color.White,
          style = Stroke(width = size * 0.04f)
        )
      } else {
        // Idle/Unpressed Receptor State

        // 1. Semi-translucent dark glass backdrop
        drawPath(
          path = arrowPath,
          color = laneColor.copy(alpha = 0.14f),
          style = Fill
        )

        // 2. Secondary accent stroke
        drawPath(
          path = arrowPath,
          color = laneColor.copy(alpha = 0.45f),
          style = Stroke(width = size * 0.05f)
        )

        // 3. Crisp outer silhouette outline
        drawPath(
          path = arrowPath,
          color = Color(0xFF94A3B8), // Slate 400
          style = Stroke(width = size * 0.025f)
        )

        // 4. Center directional dot
        drawCircle(
          color = laneColor.copy(alpha = 0.6f),
          radius = arrowRadius * 0.12f,
          center = center
        )
      }
    }
  }

  /**
   * Draws the vertical lane background and the laser light beam when the lane is pressed.
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

    // Subtle dark lane background
    drawScope.drawRect(
      color = Color(0x22111827),
      topLeft = Offset(laneLeft, 0f),
      size = Size(laneWidth, laneHeight)
    )

    // Lane separator lines
    drawScope.drawLine(
      color = Color(0x44334155),
      start = Offset(laneLeft, 0f),
      end = Offset(laneLeft, laneHeight),
      strokeWidth = 1.5f
    )
    drawScope.drawLine(
      color = Color(0x44334155),
      start = Offset(laneLeft + laneWidth, 0f),
      end = Offset(laneLeft + laneWidth, laneHeight),
      strokeWidth = 1.5f
    )

    // Pressed Laser Beam
    if (isPressed) {
      // Downward fading beam from receptor to bottom of screen
      val beamBrush = Brush.verticalGradient(
        colors = listOf(
          laneColor.copy(alpha = 0.40f),
          laneColor.copy(alpha = 0.20f),
          laneColor.copy(alpha = 0.05f),
          Color.Transparent
        ),
        startY = receptorY,
        endY = laneHeight
      )
      drawScope.drawRect(
        brush = beamBrush,
        topLeft = Offset(laneLeft + 2f, receptorY - 30f),
        size = Size(laneWidth - 4f, laneHeight - receptorY + 30f)
      )

      // Accent border glow on lane boundaries
      drawScope.drawLine(
        color = laneColor.copy(alpha = 0.7f),
        start = Offset(laneLeft, receptorY - 30f),
        end = Offset(laneLeft, laneHeight),
        strokeWidth = 3f
      )
      drawScope.drawLine(
        color = laneColor.copy(alpha = 0.7f),
        start = Offset(laneLeft + laneWidth, receptorY - 30f),
        end = Offset(laneLeft + laneWidth, laneHeight),
        strokeWidth = 3f
      )
    }
  }
}
