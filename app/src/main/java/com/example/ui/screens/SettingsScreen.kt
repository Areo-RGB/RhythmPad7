package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.ControllerInfo
import com.example.core.model.GameLane
import com.example.core.model.MenuInput
import com.example.core.state.GameInputState
import com.example.core.state.RhythmGameViewModel
import com.example.ui.components.NoPadInputs
import com.example.ui.components.PadNavigation
import com.example.ui.components.SmBackground
import com.example.ui.components.SmChip
import com.example.ui.components.SmFooter
import com.example.ui.components.SmMenuItem
import com.example.ui.components.SmPanel
import com.example.ui.components.smShadow
import com.example.ui.theme.OpenSans
import com.example.ui.theme.SmEasyGreen
import com.example.ui.theme.SmPlum
import com.example.ui.theme.SmTextMuted
import kotlinx.coroutines.flow.Flow

/**
 * StepMania-style options page: live input status, sync offset / auto-sync,
 * and the entry to controller (panel) mapping.
 */
@Composable
fun SettingsScreen(
  controllerInfo: ControllerInfo,
  inputState: GameInputState,
  inputOffsetMs: Int,
  onInputOffsetChange: (Int) -> Unit,
  autoSyncStatus: RhythmGameViewModel.AutoSyncStatus,
  onToggleAutoSync: () -> Unit,
  onOpenControllerSettings: () -> Unit,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
  playerName: String? = null,
  menuInputs: Flow<MenuInput> = NoPadInputs
) {
  BackHandler(onBack = onNavigateBack)

  var cursor by remember { mutableIntStateOf(0) }
  val itemCount = 4
  PadNavigation(menuInputs) { input ->
    when {
      input == MenuInput.Arrow(GameLane.UP) -> cursor = (cursor + itemCount - 1) % itemCount
      input == MenuInput.Arrow(GameLane.DOWN) -> cursor = (cursor + 1) % itemCount
      cursor == 0 && input == MenuInput.Arrow(GameLane.LEFT) -> onInputOffsetChange(inputOffsetMs - OFFSET_STEP_MS)
      cursor == 0 && input == MenuInput.Arrow(GameLane.RIGHT) -> onInputOffsetChange(inputOffsetMs + OFFSET_STEP_MS)
      input.isBack -> onNavigateBack()
      input.isConfirm -> when (cursor) {
        0 -> onInputOffsetChange(0)
        1 -> onToggleAutoSync()
        2 -> onOpenControllerSettings()
        else -> onNavigateBack()
      }
    }
  }

  Box(modifier = modifier.fillMaxSize()) {
    SmBackground(showTunnels = false)

    Column(modifier = Modifier.fillMaxSize()) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 56.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        SmChip(text = "SETTINGS", color = SmPlum)
        Spacer(modifier = Modifier.width(10.dp))
        Text(
          text = "Input, sync & controller",
          style = TextStyle(
            fontFamily = OpenSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = Color.White
          ).smShadow(1.5f)
        )
      }

      Row(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Left: live input status + auto-sync details
        Column(
          modifier = Modifier
            .weight(1.1f)
            .fillMaxHeight()
            .padding(start = 64.dp, end = 24.dp),
          verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)
        ) {
          InputTestPanel(
            controllerInfo = controllerInfo,
            inputState = inputState,
            modifier = Modifier.width(520.dp)
          )
          AutoSyncPanel(
            status = autoSyncStatus,
            offsetMs = inputOffsetMs,
            modifier = Modifier.width(520.dp)
          )
        }

        // Right: option rows
        Column(
          modifier = Modifier
            .weight(0.9f)
            .fillMaxHeight(),
          verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
          horizontalAlignment = Alignment.End
        ) {
          SmMenuItem(
            text = "SYNC OFFSET  ${formatOffset(inputOffsetMs)}",
            caption = "← → adjust by $OFFSET_STEP_MS ms  •  START resets to 0",
            icon = Icons.Default.Sync,
            highlighted = cursor == 0,
            onClick = { onInputOffsetChange(0) },
            modifier = Modifier.testTag("settings_sync_offset")
          )
          SmMenuItem(
            text = "AUTO-SYNC  ${if (autoSyncStatus.enabled) "ON" else "OFF"}",
            caption = "Tunes the offset from every $AUTO_SYNC_BATCH consistent steps",
            icon = Icons.Default.Refresh,
            highlighted = cursor == 1,
            onClick = onToggleAutoSync,
            modifier = Modifier.testTag("settings_auto_sync")
          )
          SmMenuItem(
            text = "CONTROLLER SETTINGS",
            caption = "Map dance-mat panels & view raw input",
            icon = Icons.Default.Settings,
            highlighted = cursor == 2,
            onClick = onOpenControllerSettings,
            modifier = Modifier.testTag("settings_controller")
          )
          SmMenuItem(
            text = "BACK",
            caption = "Return to the title menu",
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            highlighted = cursor == 3,
            onClick = onNavigateBack,
            modifier = Modifier.testTag("settings_back")
          )
        }
      }

      SmFooter(
        text = "Pad: ↑ ↓ choose  •  ← → adjust  •  START select  •  SELECT back",
        trailing = "RhythmPad7 v0.3.0",
        playerName = playerName
      )
    }
  }
}

@Composable
private fun AutoSyncPanel(
  status: RhythmGameViewModel.AutoSyncStatus,
  offsetMs: Int,
  modifier: Modifier = Modifier
) {
  val labelStyle = TextStyle(
    fontFamily = OpenSans,
    fontWeight = FontWeight.Bold,
    fontSize = 11.sp,
    letterSpacing = 1.5.sp,
    color = SmTextMuted
  )
  val valueStyle = TextStyle(fontFamily = OpenSans, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
  SmPanel(
    modifier = modifier,
    title = "SYNC",
    titleTrailing = {
      Text(
        text = if (status.enabled) "AUTO-SYNC ON" else "AUTO-SYNC OFF",
        style = TextStyle(
          fontFamily = OpenSans,
          fontWeight = FontWeight.Bold,
          fontSize = 12.sp,
          letterSpacing = 1.sp,
          color = if (status.enabled) SmEasyGreen else Color(0xFFFFB4B4)
        )
      )
    }
  ) {
    Row(horizontalArrangement = Arrangement.spacedBy(36.dp)) {
      Column {
        Text(text = "OFFSET", style = labelStyle)
        Text(text = formatOffset(offsetMs), style = valueStyle)
      }
      Column {
        Text(text = "NEXT BATCH", style = labelStyle)
        Text(text = "${status.collected} / ${status.total} steps", style = valueStyle)
      }
    }
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = status.lastMessage ?: "Positive offset = the mat registers your steps late.",
      style = TextStyle(fontFamily = OpenSans, fontSize = 13.sp, color = Color.White.copy(alpha = 0.75f))
    )
  }
}

private fun formatOffset(ms: Int): String = "${if (ms > 0) "+" else ""}$ms ms"

private const val OFFSET_STEP_MS = 5
private const val AUTO_SYNC_BATCH = 24
