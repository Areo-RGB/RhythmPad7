package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
 * Pure geometry calculations for the 4-lane note highway.
 * Ensures all 4 lanes have equal width, receptors and scrolling notes share identical
 * lane center coordinates, and the highway stays constrained to 560–640 dp on landscape tablets.
 */
object PlayfieldGeometry {
  const val MIN_HIGHWAY_WIDTH_DP = 560f
  const val MAX_HIGHWAY_WIDTH_DP = 640f
  const val RECEPTOR_Y_FRACTION = 0.16f

  /**
   * Computes the target note highway width in dp for a given landscape screen width.
   * On landscape tablets (>= 920 dp, such as Xiaomi Pad 7), constrains the highway to 560–640 dp
   * so left and right HUD panels have dedicated side zones without ever overlapping receptors.
   */
  fun calculateHighwayWidthDp(availableScreenWidthDp: Float): Float {
    return if (availableScreenWidthDp >= 920f) {
      (availableScreenWidthDp * 0.50f).coerceIn(MIN_HIGHWAY_WIDTH_DP, MAX_HIGHWAY_WIDTH_DP)
    } else {
      (availableScreenWidthDp * 0.56f).coerceIn(360f, MAX_HIGHWAY_WIDTH_DP)
    }
  }

  /**
   * Equal width of each of the 4 directional lanes (LEFT, DOWN, UP, RIGHT).
   */
  fun laneWidth(highwayWidthPx: Float): Float = highwayWidthPx / 4f

  /**
   * Authoritative X coordinate of the center of [laneIndex] (0..3).
   * Used by both fixed receptors and scrolling notes so they never drift apart.
   */
  fun laneCenterX(laneIndex: Int, laneWidthPx: Float): Float {
    return (laneIndex * laneWidthPx) + (laneWidthPx / 2f)
  }

  /**
   * Standing-readable arrow diameter scaled to the lane width.
   */
  fun arrowSizePx(laneWidthPx: Float): Float {
    return (laneWidthPx * 0.80f).coerceIn(72f, 180f)
  }
}

/**
 * Centered 4-lane vertical rhythm game note highway (LEFT | DOWN | UP | RIGHT)
 * rendered using Compose Canvas.
 *
 * Features a dark framed note highway, four equal-width lanes with clear separators,
 * unobstructed arrow receptors near the top, scrolling beat/measure bars,
 * upward-scrolling practice notes aligned to the exact receptor lane centers,
 * and hit burst animations.
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
      .background(Color(0xFF04070E)),
    contentAlignment = Alignment.Center
  ) {
    // Note: widthIn MUST precede fillMaxWidth/fillMaxHeight so Compose constrains width to 560..640.dp
    Canvas(
      modifier = Modifier
        .widthIn(
          min = PlayfieldGeometry.MIN_HIGHWAY_WIDTH_DP.dp,
          max = PlayfieldGeometry.MAX_HIGHWAY_WIDTH_DP.dp
        )
        .fillMaxWidth()
        .fillMaxHeight()
        .testTag("gameplay_canvas_playfield")
    ) {
      val canvasWidth = size.width
      val canvasHeight = size.height

      // 4 equal-width lanes
      val laneWidth = PlayfieldGeometry.laneWidth(canvasWidth)
      // Receptors placed at 16% from the top with unobstructed headroom
      val receptorY = canvasHeight * PlayfieldGeometry.RECEPTOR_Y_FRACTION
      // Large standing-readable arrow diameter
      val arrowSize = PlayfieldGeometry.arrowSizePx(laneWidth)
      val currentGameTimeMs = roundState.currentGameTimeMs
      val travelDurationMs = roundState.travelDurationMs

      // 0. Subtle receptor target band backdrop across the 4 lanes
      drawRect(
        color = Color(0x181E293B),
        topLeft = Offset(0f, (receptorY - arrowSize * 0.56f).coerceAtLeast(0f)),
        size = Size(canvasWidth, arrowSize * 1.12f)
      )

      // 1. Draw each lane track, clear separator lines, and pressed laser beam
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

      // 2. Draw scrolling beat and 4-beat measure lines across the 4-lane highway
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
            color = if (isMeasureBar) Color(0x4D94A3B8) else Color(0x2264748B),
            start = Offset(0f, lineY),
            end = Offset(canvasWidth, lineY),
            strokeWidth = if (isMeasureBar) 2.4f else 1.2f
          )
        }
      }

      // 3. Horizontal receptor alignment line across the 4 lanes
      drawLine(
        color = Color(0x55E2E8F0),
        start = Offset(0f, receptorY),
        end = Offset(canvasWidth, receptorY),
        strokeWidth = 2f
      )

      // 4. Draw fixed target receptors aligned to each lane's center
      GameLane.entries.forEach { lane ->
        val laneCenterX = PlayfieldGeometry.laneCenterX(lane.ordinal, laneWidth)
        val isPressed = inputState.isLanePressed(lane)

        ArrowDrawing.drawReceptor(
          drawScope = this,
          lane = lane,
          center = Offset(laneCenterX, receptorY),
          size = arrowSize,
          isPressed = isPressed
        )
      }

      // 5. Draw scrolling notes (PENDING and MISSED) and HIT bursts at identical lane centers
      val visibleMinY = -arrowSize
      val visibleMaxY = canvasHeight + arrowSize
      val hitBurstDurationMs = 220L

      for (note in roundState.notes) {
        val laneCenterX = PlayfieldGeometry.laneCenterX(note.lane.ordinal, laneWidth)

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

      // 6. Outer highway side rails to visually frame the 4-lane highway in the center zone
      drawLine(
        color = Color(0x8838BDF8),
        start = Offset(1.5f, 0f),
        end = Offset(1.5f, canvasHeight),
        strokeWidth = 3f
      )
      drawLine(
        color = Color(0x8838BDF8),
        start = Offset(canvasWidth - 1.5f, 0f),
        end = Offset(canvasWidth - 1.5f, canvasHeight),
        strokeWidth = 3f
      )
    }
  }
}
