package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.model.GameLane
import com.example.core.state.GameInputState
import com.example.ui.canvas.ArrowDrawing

/**
 * 4-lane vertical rhythm game playfield (LEFT | DOWN | UP | RIGHT)
 * rendered using Compose Canvas. Receptors sit at the top of the playfield
 * with room below for future upward-scrolling note charts.
 */
@Composable
fun PlayfieldView(
  inputState: GameInputState,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          colors = listOf(
            Color(0xFF070A0F),
            Color(0xFF0F1522),
            Color(0xFF090D15)
          )
        )
      ),
    contentAlignment = Alignment.Center
  ) {
    // Stage container constrained to optimal rhythm-game stage width on landscape tablet
    Canvas(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 680.dp)
        .testTag("gameplay_canvas_playfield")
    ) {
      val canvasWidth = size.width
      val canvasHeight = size.height

      // 4 lanes equal width
      val laneWidth = canvasWidth / 4f
      // Receptors placed at ~18% from the top
      val receptorY = canvasHeight * 0.18f
      // Arrow receptor diameter based on lane width (leaving breathing room between lanes)
      val arrowSize = (laneWidth * 0.76f).coerceAtMost(140f)

      // Draw each lane track and laser beam
      GameLane.entries.forEach { lane ->
        val laneIndex = lane.ordinal
        val laneLeft = laneIndex * laneWidth
        val isPressed = inputState.isLanePressed(lane)

        ArrowDrawing.drawLaneTrack(
          drawScope = this,
          lane = lane,
          laneLeft = laneLeft,
          laneWidth = laneWidth,
          laneHeight = canvasHeight,
          receptorY = receptorY,
          isPressed = isPressed
        )
      }

      // Draw receptors on top of tracks
      GameLane.entries.forEach { lane ->
        val laneIndex = lane.ordinal
        val laneCenterX = (laneIndex * laneWidth) + (laneWidth / 2f)
        val isPressed = inputState.isLanePressed(lane)

        ArrowDrawing.drawReceptor(
          drawScope = this,
          lane = lane,
          center = Offset(laneCenterX, receptorY),
          size = arrowSize,
          isPressed = isPressed
        )
      }

      // Subtle horizontal hit-line indicator across all 4 lanes
      drawLine(
        color = Color(0x33FFFFFF),
        start = Offset(0f, receptorY),
        end = Offset(canvasWidth, receptorY),
        strokeWidth = 1f
      )
    }
  }
}
