package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.ControllerInfo
import com.example.core.model.GameLane
import com.example.core.state.GameInputState
import com.example.ui.components.DebugOverlayView
import com.example.ui.components.PlayfieldView
import com.example.ui.components.SimulatedInputControls

/**
 * 4-lane rhythm gameplay screen for RhythmPad7.
 * Includes playfield with programmatic canvas receptors, reserved areas for future
 * score/combo/judgments, pause control, debug telemetry HUD, and on-screen simulated input pad.
 */
@Composable
fun GameplayScreen(
  inputState: GameInputState,
  controllerInfo: ControllerInfo,
  isDebugOverlayVisible: Boolean,
  isSimulatedInputVisible: Boolean,
  isPaused: Boolean,
  onSimulatedInput: (GameLane, Boolean) -> Unit,
  onToggleDebug: () -> Unit,
  onToggleSimulatedInput: () -> Unit,
  onTogglePause: () -> Unit,
  onResume: () -> Unit,
  onResetCounters: () -> Unit,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  BackHandler(onBack = onNavigateBack)

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color(0xFF04070D))
  ) {
    // 1. Core 4-Lane Canvas Playfield
    PlayfieldView(
      inputState = inputState,
      modifier = Modifier.fillMaxSize()
    )

    // 2. Reserved Stage HUD Overlay (Future Song Info, Score, Combo, Judgment)
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
      // Left Reserved Zone: Song Info & Stage Info
      Column(
        modifier = Modifier
          .align(Alignment.TopStart)
          .padding(top = 64.dp)
      ) {
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x990F172A))
            .border(1.dp, Color(0x40334155), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
          Column {
            Text(
              text = "STAGE 1 • PRACTICE",
              style = MaterialTheme.typography.labelSmall.copy(
                color = Color(0xFF38BDF8),
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
              )
            )
            Text(
              text = "RhythmPad7 Engine Test",
              style = MaterialTheme.typography.titleSmall.copy(
                color = Color(0xFFF1F5F9),
                fontWeight = FontWeight.Bold
              )
            )
            Text(
              text = "140 BPM (v0.2 ready)",
              style = MaterialTheme.typography.bodySmall.copy(
                color = Color(0xFF94A3B8),
                fontFamily = FontFamily.Monospace
              )
            )
          }
        }
      }

      // Right Reserved Zone: Future Score & Target Judgment Placeholder
      Column(
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(top = 64.dp),
        horizontalAlignment = Alignment.End
      ) {
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x990F172A))
            .border(1.dp, Color(0x40334155), RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
          Column(horizontalAlignment = Alignment.End) {
            Text(
              text = "SCORE",
              style = MaterialTheme.typography.labelSmall.copy(
                color = Color(0xFF64748B),
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
              )
            )
            Text(
              text = "000,000,000",
              style = MaterialTheme.typography.titleMedium.copy(
                color = Color(0xFF38BDF8),
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
              )
            )
          }
        }
      }

      // Center Playfield Reserved Zone: Future Combo / Judgment Text Area
      Column(
        modifier = Modifier
          .align(Alignment.Center)
          .padding(top = 100.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        if (inputState.isAnyLanePressed) {
          Text(
            text = "PERFECT",
            style = MaterialTheme.typography.headlineMedium.copy(
              fontWeight = FontWeight.Black,
              letterSpacing = 2.sp,
              color = Color(0xFF00E5FF)
            )
          )
        }
      }
    }

    // 3. Top Navigation & Tool Bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .align(Alignment.TopCenter)
        .background(Color(0xBB0A0F1D))
        .padding(horizontal = 20.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Left: Back button & Title
      Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(
          onClick = onNavigateBack,
          modifier = Modifier
            .size(44.dp)
            .testTag("gameplay_button_back"),
          colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = Color(0xFF1E293B),
            contentColor = Color(0xFFF1F5F9)
          )
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back to Home"
          )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
          Text(
            text = "RhythmPad7",
            style = MaterialTheme.typography.titleMedium.copy(
              fontWeight = FontWeight.Black,
              color = Color(0xFFF1F5F9)
            )
          )
          Text(
            text = if (controllerInfo.isConnected) "USB: ${controllerInfo.name}" else "Simulated Input Mode",
            style = MaterialTheme.typography.bodySmall.copy(
              color = if (controllerInfo.isConnected) Color(0xFF34D399) else Color(0xFF94A3B8)
            )
          )
        }
      }

      // Right: Tool Controls (Pause, Toggle Debug, Toggle Simulated Input, Reset Counters)
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Reset counters button
        FilledTonalIconButton(
          onClick = onResetCounters,
          modifier = Modifier.size(44.dp),
          colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = Color(0xFF1E293B),
            contentColor = Color(0xFF94A3B8)
          )
        ) {
          Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = "Reset Counters",
            modifier = Modifier.size(20.dp)
          )
        }

        // Simulated Input Toggle Button
        FilledTonalIconButton(
          onClick = onToggleSimulatedInput,
          modifier = Modifier
            .size(44.dp)
            .testTag("gameplay_button_toggle_sim_input"),
          colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = if (isSimulatedInputVisible) Color(0xFF0284C7) else Color(0xFF1E293B),
            contentColor = if (isSimulatedInputVisible) Color.White else Color(0xFF94A3B8)
          )
        ) {
          Icon(
            imageVector = Icons.Default.TouchApp,
            contentDescription = "Toggle Simulated Pad",
            modifier = Modifier.size(20.dp)
          )
        }

        // Debug Overlay Toggle Button
        FilledTonalIconButton(
          onClick = onToggleDebug,
          modifier = Modifier
            .size(44.dp)
            .testTag("gameplay_button_toggle_debug"),
          colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = if (isDebugOverlayVisible) Color(0xFF0D9488) else Color(0xFF1E293B),
            contentColor = if (isDebugOverlayVisible) Color.White else Color(0xFF94A3B8)
          )
        ) {
          Icon(
            imageVector = Icons.Default.BugReport,
            contentDescription = "Toggle Debug HUD",
            modifier = Modifier.size(20.dp)
          )
        }

        // Pause Button
        FilledTonalIconButton(
          onClick = onTogglePause,
          modifier = Modifier
            .size(44.dp)
            .testTag("gameplay_button_pause"),
          colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = Color(0xFF1E293B),
            contentColor = Color(0xFFF1F5F9)
          )
        ) {
          Icon(
            imageVector = Icons.Default.Pause,
            contentDescription = "Pause"
          )
        }
      }
    }

    // 4. Floating Debug Telemetry HUD (When Toggled)
    AnimatedVisibility(
      visible = isDebugOverlayVisible,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier
        .align(Alignment.CenterStart)
        .padding(start = 20.dp, top = 60.dp, bottom = 120.dp)
    ) {
      DebugOverlayView(
        inputState = inputState,
        controllerInfo = controllerInfo
      )
    }

    // 5. On-Screen Simulated Touch Input Controls (Bottom Area)
    AnimatedVisibility(
      visible = isSimulatedInputVisible,
      enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
      exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
      modifier = Modifier.align(Alignment.BottomCenter)
    ) {
      SimulatedInputControls(
        inputState = inputState,
        onInput = onSimulatedInput
      )
    }

    // 6. Pause Dialog / Overlay
    if (isPaused) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color(0xD904070D))
          .testTag("pause_overlay"),
        contentAlignment = Alignment.Center
      ) {
        Surface(
          shape = RoundedCornerShape(24.dp),
          color = Color(0xFF0F172A),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
          modifier = Modifier.width(420.dp)
        ) {
          Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Text(
              text = "GAME PAUSED",
              style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = Color(0xFFF1F5F9)
              )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "All input states cleared. Choose an action to continue.",
              style = MaterialTheme.typography.bodyMedium.copy(
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center
              )
            )

            Spacer(modifier = Modifier.height(28.dp))

            Button(
              onClick = onResume,
              modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("pause_button_resume"),
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF00E5FF),
                contentColor = Color(0xFF04060A)
              )
            ) {
              Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Resume")
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "RESUME",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black)
              )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
              onClick = onNavigateBack,
              modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("pause_button_quit"),
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1E293B),
                contentColor = Color(0xFFF1F5F9)
              )
            ) {
              Text(
                text = "RETURN TO TITLE",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
              )
            }
          }
        }
      }
    }
  }
}
