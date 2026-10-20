package com.example.ui.components

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.gameplay.GameplayRoundState
import com.example.core.model.GameLane
import com.example.core.model.Judgment
import com.example.core.model.NoteState
import com.example.core.model.PracticeNote
import com.example.core.state.GameInputState
import com.example.ui.canvas.NoteSkinDrawing
import com.example.ui.canvas.rememberNoteSkin

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
    return (laneWidthPx * 0.86f).coerceIn(72f, 440f)
  }
}

/**
 * Centered 4-lane StepMania-style note field (LEFT | DOWN | UP | RIGHT) rendered with the
 * `dance/default` noteskin bitmaps on a Compose Canvas.
 *
 * Mirrors the PC theme: a dark screen filter behind the note field, beat-pulsing grey
 * receptors near the top, quantization-colored arrows scrolling upward to the exact
 * receptor lane centers, receptor press flashes, and additive tap explosions.
 */
@Composable
fun PlayfieldView(
  inputState: GameInputState,
  roundState: GameplayRoundState,
  modifier: Modifier = Modifier
) {
  val skin = rememberNoteSkin()

  // Per-lane press animation (ReceptorArrow NoneCommand zoom + ReceptorOverlay Press/Lift flash)
  val pressAmounts = GameLane.entries.map { lane ->
    animateFloatAsState(
      targetValue = if (inputState.isLanePressed(lane)) 1f else 0f,
      animationSpec = if (inputState.isLanePressed(lane)) snap() else tween(110),
      label = "receptor_press_${lane.name}"
    )
  }
  val flashAlphas = GameLane.entries.map { lane ->
    animateFloatAsState(
      targetValue = if (inputState.isLanePressed(lane)) 0.6f else 0f,
      animationSpec = if (inputState.isLanePressed(lane)) snap() else tween(200, easing = LinearOutSlowInEasing),
      label = "receptor_flash_${lane.name}"
    )
  }

  Box(
    modifier = modifier.fillMaxSize(),
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

      val laneWidth = PlayfieldGeometry.laneWidth(canvasWidth)
      val receptorY = canvasHeight * PlayfieldGeometry.RECEPTOR_Y_FRACTION
      val arrowSize = PlayfieldGeometry.arrowSizePx(laneWidth)
      val currentGameTimeMs = roundState.currentGameTimeMs
      val travelDurationMs = roundState.travelDurationMs

      val currentBeat = roundState.beatAt(currentGameTimeMs)
      val beatPhase = (((currentBeat % 1.0) + 1.0) % 1.0).toFloat()

      // 0. ScreenFilter: dark backing plate behind the note field, softened at the edges
      drawRect(color = Color(0xD0000000), size = Size(canvasWidth, canvasHeight))
      drawRect(
        brush = Brush.horizontalGradient(
          0f to Color(0x66000000),
          0.04f to Color.Transparent,
          0.96f to Color.Transparent,
          1f to Color(0x66000000)
        ),
        size = Size(canvasWidth, canvasHeight)
      )

      // 1. Lane separators and pressed lane beams
      GameLane.entries.forEach { lane ->
        val laneLeft = lane.ordinal * laneWidth
        if (lane.ordinal > 0) {
          drawLine(
            color = Color(0x18FFFFFF),
            start = Offset(laneLeft, 0f),
            end = Offset(laneLeft, canvasHeight),
            strokeWidth = 1.5f
          )
        }
        val press = pressAmounts[lane.ordinal].value
        if (press > 0.01f) {
          drawRect(
            brush = Brush.verticalGradient(
              colors = listOf(Color(0x40FFFFFF), Color(0x10FFFFFF), Color.Transparent),
              startY = receptorY,
              endY = canvasHeight * 0.85f
            ),
            topLeft = Offset(laneLeft + laneWidth * 0.1f, receptorY),
            size = Size(laneWidth * 0.8f, canvasHeight - receptorY),
            alpha = press
          )
        }
      }

      // 2. Faint scrolling measure bars (StepMania TimingData aware)
      val firstMeasureBeat = kotlin.math.floor(currentBeat / 4.0) * 4.0
      for (m in 0..16) {
        val measureBeat = firstMeasureBeat + m * 4.0
        if (measureBeat < 0.0) continue
        val lineY = PracticeNote.calculateNoteY(
          noteHitTimeMs = roundState.gameTimeAtBeat(measureBeat),
          currentGameTimeMs = currentGameTimeMs,
          receptorY = receptorY,
          playfieldHeight = canvasHeight,
          travelDurationMs = travelDurationMs
        )
        if (lineY > canvasHeight) break
        if (lineY >= receptorY) {
          drawLine(
            color = Color(0x1FFFFFFF),
            start = Offset(0f, lineY),
            end = Offset(canvasWidth, lineY),
            strokeWidth = 2f
          )
        }
      }

      // 3. Receptors aligned to each lane's center
      GameLane.entries.forEach { lane ->
        NoteSkinDrawing.drawReceptor(
          drawScope = this,
          skin = skin,
          lane = lane,
          center = Offset(PlayfieldGeometry.laneCenterX(lane.ordinal, laneWidth), receptorY),
          size = arrowSize,
          beatPhase = beatPhase,
          pressAmount = pressAmounts[lane.ordinal].value,
          flashAlpha = flashAlphas[lane.ordinal].value
        )
      }

      // 4. Scrolling notes (pending + missed) and tap explosions at identical lane centers
      val visibleMinY = -arrowSize
      val visibleMaxY = canvasHeight + arrowSize

      for (note in roundState.notes) {
        val laneCenterX = PlayfieldGeometry.laneCenterX(note.lane.ordinal, laneWidth)

        when (note.state) {
          NoteState.PENDING, NoteState.MISSED -> {
            val noteY = note.calculateY(
              currentGameTimeMs = currentGameTimeMs,
              receptorY = receptorY,
              playfieldHeight = canvasHeight,
              travelDurationMs = travelDurationMs
            )
            if (noteY in visibleMinY..visibleMaxY) {
              NoteSkinDrawing.drawNote(
                drawScope = this,
                skin = skin,
                lane = note.lane,
                center = Offset(laneCenterX, noteY),
                size = arrowSize,
                quantFrame = NoteSkinDrawing.quantizationFrame(note.beat),
                isMissed = note.state == NoteState.MISSED
              )
            }
          }

          NoteState.HIT -> {
            val judgedAt = note.judgedAtMs ?: continue
            NoteSkinDrawing.drawExplosion(
              drawScope = this,
              skin = skin,
              lane = note.lane,
              center = Offset(laneCenterX, receptorY),
              size = arrowSize,
              judgment = note.judgment ?: Judgment.GOOD,
              elapsedMs = currentGameTimeMs - judgedAt
            )
          }
        }
      }
    }
  }
}
