package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.GameLane
import com.example.core.state.GameInputState

/**
 * On-screen simulated touch pad for tablet testing and development without a physical controller.
 * Supports multi-touch independent press and release states for all four directions.
 */
@Composable
fun SimulatedInputControls(
  inputState: GameInputState,
  onInput: (GameLane, Boolean) -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 24.dp, vertical = 12.dp),
    contentAlignment = Alignment.Center
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(110.dp)
        .background(
          color = Color(0xDD0B0F19),
          shape = RoundedCornerShape(20.dp)
        )
        .border(
          width = 1.dp,
          color = Color(0x40334155),
          shape = RoundedCornerShape(20.dp)
        )
        .padding(10.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
      verticalAlignment = Alignment.CenterVertically
    ) {
      GameLane.entries.forEach { lane ->
        val isPressed = inputState.isLanePressed(lane)
        val icon = when (lane) {
          GameLane.LEFT -> Icons.AutoMirrored.Filled.ArrowBack
          GameLane.DOWN -> Icons.Default.ArrowDownward
          GameLane.UP -> Icons.Default.ArrowUpward
          GameLane.RIGHT -> Icons.AutoMirrored.Filled.ArrowForward
        }

        SimulatedLaneButton(
          lane = lane,
          isPressed = isPressed,
          icon = icon,
          onInput = onInput,
          modifier = Modifier.weight(1f)
        )
      }
    }
  }
}

@Composable
private fun SimulatedLaneButton(
  lane: GameLane,
  isPressed: Boolean,
  icon: ImageVector,
  onInput: (GameLane, Boolean) -> Unit,
  modifier: Modifier = Modifier
) {
  val laneColor = lane.defaultColor

  val backgroundBrush = if (isPressed) {
    Brush.verticalGradient(
      colors = listOf(
        laneColor.copy(alpha = 0.90f),
        laneColor.copy(alpha = 0.60f)
      )
    )
  } else {
    Brush.verticalGradient(
      colors = listOf(
        Color(0xFF1E293B),
        Color(0xFF0F172A)
      )
    )
  }

  val borderColor = if (isPressed) Color.White else laneColor.copy(alpha = 0.5f)
  val contentColor = if (isPressed) Color.White else laneColor

  Box(
    modifier = modifier
      .height(90.dp)
      .clip(RoundedCornerShape(16.dp))
      .background(backgroundBrush)
      .border(
        width = if (isPressed) 2.5.dp else 1.5.dp,
        color = borderColor,
        shape = RoundedCornerShape(16.dp)
      )
      .scale(if (isPressed) 0.96f else 1.0f)
      .testTag("simulated_button_${lane.name.lowercase()}")
      .pointerInput(lane) {
        awaitEachGesture {
          // Wait for finger down
          awaitFirstDown(requireUnconsumed = false)
          onInput(lane, true)

          // Wait for finger release or cancellation
          waitForUpOrCancellation()
          onInput(lane, false)
        }
      },
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = "${lane.displayName} arrow",
        tint = contentColor,
        modifier = Modifier.size(32.dp)
      )
      Text(
        text = lane.displayName,
        style = MaterialTheme.typography.labelMedium.copy(
          fontWeight = FontWeight.Bold,
          letterSpacing = 1.sp
        ),
        color = contentColor
      )
    }
  }
}
