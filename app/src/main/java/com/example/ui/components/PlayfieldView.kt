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
import com.example.core.gameplay.GameplayRoundState
import com.example.core.gameplay.PracticePatternGenerator
import com.example.core.model.GameLane
import com.example.core.model.Judgment
import com.example.core.model.NoteState
import com.example.core.model.PracticeNote
import com.example.core.state.GameInputState
import com.example.ui.canvas.ArrowDrawing

/**
 * 4-lane vertical rhythm game playfield (LEFT | DOWN | UP | RIGHT)
 * rendered using Compose Canvas.
 * Renders fixed arrow receptors near the top, scrolling beat/measure lines,
 * upward-scrolling practice notes, and hit burst animations.
 */
@Composable
fun PlayfieldView(
  inputState: GameInputState,
  roundState: GameplayRoundState,
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
      // Receptors placed at 18% from the top
      val receptorY = canvasHeight * 0.18f
      // Arrow receptor & note diameter based on lane width
      val arrowSize = (laneWidth * 0.76f).coerceAtMost(140f)
      val currentGameTimeMs = roundState.currentGameTimeMs
      val travelDurationMs = roundState.travelDurationMs

      // 1. Draw each lane track and pressed laser beam
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

      // 2. Draw scrolling beat and 4-beat measure lines across all 4 lanes
      for (beatIndex in 0..68) {
        val beatTimeMs = PracticePatternGenerator.beatToTimeMs(
          beat = beatIndex.toDouble(),
          beatZeroTimeMs = roundState.beatZeroTimeMs,
          bpm = roundState.bpm
        )
        val lineY = PracticeNote.calculateNoteY(
          noteHitTimeMs = beatTimeMs,
          currentGameTimeMs = currentGameTimeMs,
          receptorY = receptorY,
          playfieldHeight = canvasHeight,
          travelDurationMs = travelDurationMs
        )
        if (lineY in 0f..canvasHeight) {
          val isMeasureBar = (beatIndex % 4 == 0)
          drawLine(
            color = if (isMeasureBar) Color(0x4094A3B8) else Color(0x1C64748B),
            start = Offset(0f, lineY),
            end = Offset(canvasWidth, lineY),
            strokeWidth = if (isMeasureBar) 2.2f else 1f
          )
        }
      }

      // 3. Subtle horizontal hit-line indicator across all 4 lanes
      drawLine(
        color = Color(0x44FFFFFF),
        start = Offset(0f, receptorY),
        end = Offset(canvasWidth, receptorY),
        strokeWidth = 1.5f
      )

      // 4. Draw fixed target receptors on top of tracks
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

      // 5. Draw scrolling notes (PENDING and MISSED) and HIT bursts
      val visibleMinY = -arrowSize
      val visibleMaxY = canvasHeight + arrowSize
      val hitBurstDurationMs = 220L

      for (note in roundState.notes) {
        val laneIndex = note.lane.ordinal
        val laneCenterX = (laneIndex * laneWidth) + (laneWidth / 2f)

        when (note.state) {
          NoteState.PENDING -> {
            val noteY = note.calculateY(
              currentGameTimeMs = currentGameTimeMs,
              receptorY = receptorY,
              playfieldHeight = canvasHeight,
              travelDurationMs = travelDurationMs
            )
            if (noteY in visibleMinY..visibleMaxY) {
              ArrowDrawing.drawScrollingNote(
                drawScope = this,
                lane = note.lane,
                center = Offset(laneCenterX, noteY),
                size = arrowSize,
                isMissed = false
              )
            }
          }

          NoteState.MISSED -> {
            val noteY = note.calculateY(
              currentGameTimeMs = currentGameTimeMs,
              receptorY = receptorY,
              playfieldHeight = canvasHeight,
              travelDurationMs = travelDurationMs
            )
            if (noteY in visibleMinY..visibleMaxY) {
              ArrowDrawing.drawScrollingNote(
                drawScope = this,
                lane = note.lane,
                center = Offset(laneCenterX, noteY),
                size = arrowSize,
                isMissed = true
              )
            }
          }

          NoteState.HIT -> {
            val judgedAt = note.judgedAtMs ?: continue
            val elapsedSinceHit = currentGameTimeMs - judgedAt
            if (elapsedSinceHit in 0L..hitBurstDurationMs) {
              val progress = elapsedSinceHit.toFloat() / hitBurstDurationMs.toFloat()
              ArrowDrawing.drawHitBurst(
                drawScope = this,
                lane = note.lane,
                center = Offset(laneCenterX, receptorY),
                size = arrowSize,
                judgment = note.judgment ?: Judgment.GOOD,
                progress = progress
              )
            }
          }
        }
      }
    }
  }
}
