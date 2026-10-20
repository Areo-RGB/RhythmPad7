package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.ControllerInfo
import com.example.core.model.ControllerMapping
import com.example.core.model.GameLane
import com.example.core.model.MenuInput
import com.example.ui.components.NoPadInputs
import com.example.ui.components.PadNavigation
import kotlinx.coroutines.flow.Flow
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.example.core.model.RawInputEvent
import com.example.core.state.GameInputState
import com.example.ui.components.ChamferShape
import com.example.ui.components.LaneArrowIcon
import com.example.ui.components.SmBackground
import com.example.ui.components.SmButton
import com.example.ui.components.SmButtonStyle
import com.example.ui.components.SmChip
import com.example.ui.components.SmFooter
import com.example.ui.components.SmHeader
import com.example.ui.components.SmPanel
import com.example.ui.components.smShadow
import com.example.ui.theme.OpenSans
import com.example.ui.theme.SmBeginnerMint
import com.example.ui.theme.SmCharcoal
import com.example.ui.theme.SmEasyGreen
import com.example.ui.theme.SmGold
import com.example.ui.theme.SmPlum
import com.example.ui.theme.SmPlumLight
import com.example.ui.theme.SmTextMuted
import com.example.ui.theme.SmTextSecondary
import kotlinx.coroutines.flow.SharedFlow

/**
 * Controller configuration and raw input telemetry screen (ScreenMapControllers style).
 * Directly identifies and prioritizes USB dance mats (VID 0x0079 PID 0x0006)
 * and uses non-intrusive inline capture rather than a modal wizard.
 */
@Composable
fun ControllerSettingsScreen(
  controllerInfo: ControllerInfo,
  currentMapping: ControllerMapping,
  capturingLane: GameLane?,
  inputState: GameInputState,
  rawInputEvents: SharedFlow<RawInputEvent>,
  onStartCapture: (GameLane) -> Unit,
  onCancelCapture: () -> Unit,
  onResetDefaults: () -> Unit,
  onScanControllers: () -> Unit,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
  menuInputs: Flow<MenuInput> = NoPadInputs
) {
  // Pad control: ↑/↓ choose a direction row, Start/Circle remap it, Select/Cross back
  var cursor by remember { mutableIntStateOf(0) }
  PadNavigation(menuInputs) { input ->
    when {
      input == MenuInput.Arrow(GameLane.UP) -> cursor = (cursor + 3) % 4
      input == MenuInput.Arrow(GameLane.DOWN) -> cursor = (cursor + 1) % 4
      input.isConfirm -> onStartCapture(GameLane.entries[cursor])
      input.isBack -> {
        onCancelCapture()
        onNavigateBack()
      }
    }
  }

  BackHandler(onBack = {
    onCancelCapture()
    onNavigateBack()
  })

  // Local buffer of recent raw hardware events
  val recentRawEvents = remember { mutableStateListOf<RawInputEvent>() }

  LaunchedEffect(rawInputEvents) {
    rawInputEvents.collect { event ->
      recentRawEvents.add(0, event)
      if (recentRawEvents.size > 20) {
        recentRawEvents.removeLast()
      }
    }
  }

  Box(modifier = modifier.fillMaxSize()) {
    SmBackground()

    Column(modifier = Modifier.fillMaxSize()) {
      SmHeader(
        title = "Controller Settings",
        subtitle = "USB dance mat (0079:0006 priority) & panel mapping",
        icon = Icons.AutoMirrored.Filled.ArrowBack,
        onIconClick = {
          onCancelCapture()
          onNavigateBack()
        },
        iconTestTag = "settings_button_back"
      ) {
        SmButton(
          text = "SCAN DEVICES",
          icon = Icons.Default.Refresh,
          style = SmButtonStyle.Dark,
          height = 46.dp,
          fontSize = 14,
          onClick = onScanControllers
        )
        SmButton(
          text = "RESTORE DEFAULTS",
          icon = Icons.Default.Restore,
          style = SmButtonStyle.Dark,
          height = 46.dp,
          fontSize = 14,
          onClick = onResetDefaults,
          modifier = Modifier.testTag("settings_button_reset_defaults")
        )
      }

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
          .padding(horizontal = 56.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp)
      ) {
        // Left Column: 4 direction option rows with inline listening
        SmPanel(
          modifier = Modifier
            .weight(1.3f)
            .fillMaxHeight(),
          title = "DIRECTIONAL MAPPINGS",
          contentPadding = PaddingValues(14.dp)
        ) {
          Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
          ) {
            GameLane.entries.forEach { lane ->
              val bindings = currentMapping.bindingsFor(lane)
              MappingLaneRow(
                lane = lane,
                bindingsText = bindings.joinToString(" • ") { it.description }.ifEmpty { "Unassigned" },
                isPressed = inputState.isLanePressed(lane),
                isListening = capturingLane == lane,
                isFocused = cursor == lane.ordinal,
                onRemap = {
                  cursor = lane.ordinal
                  onStartCapture(lane)
                },
                onCancel = onCancelCapture
              )
            }
          }
        }

        // Right Column: Hardware details & live telemetry feed
        Column(
          modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          SmPanel(
            modifier = Modifier.fillMaxWidth(),
            title = "TARGET DANCE MAT",
            titleTrailing = {
              Text(
                text = if (controllerInfo.isConnected) "CONNECTED" else "NOT FOUND",
                style = TextStyle(
                  fontFamily = OpenSans,
                  fontWeight = FontWeight.Bold,
                  fontSize = 12.sp,
                  letterSpacing = 1.sp,
                  color = if (controllerInfo.isConnected) SmEasyGreen else Color(0xFFFFB4B4)
                )
              )
            }
          ) {
            Text(
              text = controllerInfo.name.replace(Regex("\\s+"), " "),
              style = TextStyle(
                fontFamily = OpenSans,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.White
              ),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            if (controllerInfo.isConnected) {
              Text(
                text = "VID ${controllerInfo.hexVendorId} • PID ${controllerInfo.hexProductId} • device ${controllerInfo.deviceId}",
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = SmTextSecondary)
              )
              Spacer(modifier = Modifier.height(8.dp))
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (controllerInfo.isDanceMat) {
                  SmChip(text = "DANCE MAT MATCHED", color = SmEasyGreen.copy(alpha = 0.85f), textColor = SmCharcoal)
                }
                SmChip(text = "OTHER INPUTS LOCKED", color = SmPlum)
              }
            } else {
              Text(
                text = "No controller detected. Plug in your USB dance mat or gamepad.",
                style = TextStyle(fontFamily = OpenSans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = SmTextMuted)
              )
            }
          }

          SmPanel(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f),
            title = "LIVE RAW INPUT",
            contentPadding = PaddingValues(12.dp)
          ) {
            if (recentRawEvents.isEmpty()) {
              Box(
                modifier = Modifier
                  .fillMaxSize()
                  .padding(16.dp),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = "Step on a panel or press any controller button…",
                  style = TextStyle(fontFamily = OpenSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = SmTextMuted)
                )
              }
            } else {
              LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                items(recentRawEvents) { raw ->
                  val active = raw.action == "DOWN" || raw.value != 0f
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .clip(ChamferShape(6.dp, pointLeft = false))
                      .background(if (active) SmPlum.copy(alpha = 0.55f) else Color(0x22FFFFFF))
                      .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text(
                      text = "${raw.type}: ${raw.name} (${raw.action})",
                      style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (active) Color.White else SmTextSecondary
                      ),
                      modifier = Modifier.weight(1f)
                    )
                    Text(
                      text = "code=${raw.code}",
                      style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = SmTextMuted)
                    )
                  }
                }
              }
            }
          }
        }
      }

      SmFooter(
        text = "Pad: ↑ ↓ choose  •  START remap, then step the panel  •  SELECT back",
        trailing = "RhythmPad7 v0.3.0"
      )
    }
  }
}

/** Option-row style mapping entry: noteskin arrow, lane name, binding value and REMAP action. */
@Composable
private fun MappingLaneRow(
  lane: GameLane,
  bindingsText: String,
  isPressed: Boolean,
  isListening: Boolean,
  isFocused: Boolean,
  onRemap: () -> Unit,
  onCancel: () -> Unit
) {
  val transition = rememberInfiniteTransition(label = "listen_pulse")
  val pulse by transition.animateFloat(
    initialValue = 0.4f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
    label = "listen_pulse_alpha"
  )
  val shape = ChamferShape(18.dp, pointLeft = true, pointRight = false)

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(84.dp)
      .clip(shape)
      .background(
        when {
          isListening -> Brush.horizontalGradient(listOf(SmPlumLight, SmPlum.copy(alpha = 0.6f)))
          isPressed -> Brush.horizontalGradient(listOf(SmPlum, SmPlum.copy(alpha = 0.35f)))
          else -> Brush.horizontalGradient(listOf(Color(0x33FFFFFF), Color(0x0FFFFFFF)))
        }
      )
      .border(
        width = if (isListening || isFocused) 2.dp else 1.dp,
        color = when {
          isListening -> Color.White.copy(alpha = pulse)
          isFocused -> Color.White
          else -> Color(0x33FFFFFF)
        },
        shape = shape
      )
      .padding(start = 26.dp, end = 14.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    LaneArrowIcon(lane = lane, pressed = isPressed || isListening, size = 60.dp)

    Spacer(modifier = Modifier.width(18.dp))

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = lane.displayName,
        style = TextStyle(
          fontFamily = OpenSans,
          fontWeight = FontWeight.ExtraBold,
          fontSize = 20.sp,
          letterSpacing = 1.sp,
          color = Color.White
        ).smShadow(1.5f)
      )
      Text(
        text = if (isListening) "Listening… step on the dance mat" else bindingsText,
        style = TextStyle(
          fontFamily = FontFamily.Monospace,
          fontWeight = if (isListening) FontWeight.Bold else FontWeight.Normal,
          fontSize = 13.sp,
          color = if (isListening) SmGold else SmTextSecondary
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }

    if (isPressed && !isListening) {
      SmChip(text = "ACTIVE", color = SmBeginnerMint, textColor = SmCharcoal)
      Spacer(modifier = Modifier.width(10.dp))
    }

    if (isListening) {
      SmButton(
        text = "CANCEL",
        style = SmButtonStyle.Dark,
        height = 44.dp,
        fontSize = 13,
        onClick = onCancel
      )
    } else {
      SmButton(
        text = "REMAP",
        style = SmButtonStyle.Plum,
        height = 44.dp,
        fontSize = 13,
        onClick = onRemap,
        modifier = Modifier.testTag("mapping_button_remap_${lane.name.lowercase()}")
      )
    }
  }
}
