package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.ControllerInfo
import com.example.core.model.ControllerMapping
import com.example.core.model.GameLane
import com.example.core.model.RawInputEvent
import com.example.core.state.GameInputState
import kotlinx.coroutines.flow.SharedFlow

/**
 * Controller configuration and raw input telemetry screen.
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
  modifier: Modifier = Modifier
) {
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

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color(0xFF090E17))
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(24.dp)
    ) {
      // Top Navigation Bar
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          FilledTonalIconButton(
            onClick = onNavigateBack,
            modifier = Modifier
              .size(44.dp)
              .testTag("settings_button_back"),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
              containerColor = Color(0xFF1E293B),
              contentColor = Color(0xFFF1F5F9)
            )
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back"
            )
          }

          Spacer(modifier = Modifier.width(14.dp))

          Column {
            Text(
              text = "CONTROLLER SETTINGS",
              style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = Color(0xFFF1F5F9)
              )
            )
            Text(
              text = "USB Dance Mat (0079:0006 Priority) & Direct Mapping",
              style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
            )
          }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          // Scan Hardware Button
          OutlinedButton(
            onClick = onScanControllers,
            modifier = Modifier.height(42.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8))
          ) {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = "Scan",
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("SCAN DEVICES", fontWeight = FontWeight.Bold, fontSize = 12.sp)
          }

          // Reset to Defaults Button
          OutlinedButton(
            onClick = onResetDefaults,
            modifier = Modifier
              .height(42.dp)
              .testTag("settings_button_reset_defaults"),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF87171))
          ) {
            Icon(
              imageVector = Icons.Default.Restore,
              contentDescription = "Reset",
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("RESTORE DEFAULTS", fontWeight = FontWeight.Bold, fontSize = 12.sp)
          }
        }
      }

      Spacer(modifier = Modifier.height(18.dp))

      // Main Landscape 2-Column Split: Mappings (Left) & Telemetry / Hardware (Right)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
      ) {
        // Left Column: 4 Direction Mapping Cards with Inline Listening
        Column(
          modifier = Modifier
            .weight(1.3f)
            .fillMaxHeight(),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Text(
            text = "DIRECTIONAL MAPPINGS",
            style = MaterialTheme.typography.labelMedium.copy(
              fontWeight = FontWeight.Bold,
              color = Color(0xFF94A3B8),
              letterSpacing = 1.sp
            )
          )

          GameLane.entries.forEach { lane ->
            val bindings = currentMapping.bindingsFor(lane)
            val isPressed = inputState.isLanePressed(lane)
            val isListening = capturingLane == lane

            MappingLaneCard(
              lane = lane,
              bindingsText = bindings.joinToString(" • ") { it.description }.ifEmpty { "Unassigned" },
              isPressed = isPressed,
              isListening = isListening,
              onRemap = { onStartCapture(lane) },
              onCancel = onCancelCapture
            )
          }
        }

        // Right Column: Hardware Details & Live Telemetry Feed
        Column(
          modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          // Hardware Card
          Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF131A26),
            border = BorderStroke(1.dp, Color(0xFF1E293B))
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(
                  imageVector = if (controllerInfo.isConnected) Icons.Default.SportsEsports else Icons.Default.Gamepad,
                  contentDescription = null,
                  tint = if (controllerInfo.isConnected) Color(0xFF34D399) else Color(0xFF64748B)
                )
                Text(
                  text = "TARGET DANCE MAT",
                  style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8)
                  )
                )
              }

              Spacer(modifier = Modifier.height(8.dp))

              Text(
                text = controllerInfo.name,
                style = MaterialTheme.typography.titleMedium.copy(
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFFF1F5F9)
                )
              )

              if (controllerInfo.isConnected) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = "Vendor ID: ${controllerInfo.hexVendorId} | Product ID: ${controllerInfo.hexProductId}",
                  style = MaterialTheme.typography.bodySmall.copy(
                    color = Color(0xFF38BDF8),
                    fontFamily = FontFamily.Monospace
                  )
                )
                if (controllerInfo.isDanceMat) {
                  Text(
                    text = "★ USB Dance Mat Matched (0079:0006 Priority)",
                    style = MaterialTheme.typography.labelSmall.copy(
                      color = Color(0xFF10B981),
                      fontWeight = FontWeight.Bold
                    )
                  )
                }
                Text(
                  text = "Device ID: ${controllerInfo.deviceId} (Locked • Unrelated inputs rejected)",
                  style = MaterialTheme.typography.labelSmall.copy(
                    color = Color(0xFF64748B),
                    fontFamily = FontFamily.Monospace
                  )
                )
              } else {
                Text(
                  text = "No controller detected. Plug in your USB dance mat or gamepad.",
                  style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF64748B))
                )
              }
            }
          }

          // Live Raw Event Monitor
          Surface(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF0F1522),
            border = BorderStroke(1.dp, Color(0xFF1E293B))
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Text(
                text = "LIVE RAW INPUT TELEMETRY",
                style = MaterialTheme.typography.labelMedium.copy(
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF94A3B8),
                  letterSpacing = 0.5.sp
                )
              )
              Spacer(modifier = Modifier.height(8.dp))

              if (recentRawEvents.isEmpty()) {
                Box(
                  modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = "Press any controller button or D-pad direction to test raw signals...",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF475569))
                  )
                }
              } else {
                LazyColumn(
                  modifier = Modifier.fillMaxSize(),
                  verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                  items(recentRawEvents) { raw ->
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF182232), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Text(
                        text = "${raw.type}: ${raw.name} (${raw.action})",
                        style = MaterialTheme.typography.labelSmall.copy(
                          fontFamily = FontFamily.Monospace,
                          fontWeight = FontWeight.Bold,
                          color = if (raw.action == "DOWN" || raw.value != 0f) Color(0xFF38BDF8) else Color(0xFF94A3B8)
                        )
                      )
                      Text(
                        text = "code=${raw.code}",
                        style = MaterialTheme.typography.labelSmall.copy(
                          fontFamily = FontFamily.Monospace,
                          color = Color(0xFF64748B)
                        )
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun MappingLaneCard(
  lane: GameLane,
  bindingsText: String,
  isPressed: Boolean,
  isListening: Boolean,
  onRemap: () -> Unit,
  onCancel: () -> Unit
) {
  val laneColor = lane.defaultColor

  val cardBackground = when {
    isListening -> Color(0xFF1E1B4B)
    isPressed -> laneColor.copy(alpha = 0.15f)
    else -> Color(0xFF131A26)
  }

  val cardBorder = when {
    isListening -> BorderStroke(2.dp, Color(0xFFA855F7))
    isPressed -> BorderStroke(2.dp, laneColor)
    else -> BorderStroke(1.dp, Color(0xFF1E293B))
  }

  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(14.dp),
    color = cardBackground,
    border = cardBorder
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        // Direction Badge
        Box(
          modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isPressed || isListening) laneColor else Color(0xFF1E293B))
            .border(1.dp, laneColor, RoundedCornerShape(10.dp)),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = lane.name.take(1),
            style = MaterialTheme.typography.titleSmall.copy(
              color = if (isPressed || isListening) Color.White else laneColor,
              fontWeight = FontWeight.Black
            )
          )
        }

        Column {
          Text(
            text = lane.displayName,
            style = MaterialTheme.typography.titleSmall.copy(
              fontWeight = FontWeight.Bold,
              color = Color(0xFFF1F5F9)
            )
          )
          Text(
            text = if (isListening) "Listening on dance mat..." else bindingsText,
            style = MaterialTheme.typography.bodySmall.copy(
              color = if (isListening) Color(0xFFC084FC) else Color(0xFF94A3B8),
              fontFamily = FontFamily.Monospace,
              fontWeight = if (isListening) FontWeight.Bold else FontWeight.Normal
            )
          )
        }
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        if (isPressed && !isListening) {
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(6.dp))
              .background(laneColor)
              .padding(horizontal = 8.dp, vertical = 4.dp)
          ) {
            Text(
              text = "ACTIVE",
              style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Black,
                color = Color.White
              )
            )
          }
        }

        if (isListening) {
          OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.height(36.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8))
          ) {
            Text("CANCEL", fontWeight = FontWeight.Bold, fontSize = 11.sp)
          }
        } else {
          Button(
            onClick = onRemap,
            modifier = Modifier.testTag("mapping_button_remap_${lane.name.lowercase()}"),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = Color(0xFF1E293B),
              contentColor = Color(0xFF38BDF8)
            )
          ) {
            Text("REMAP", fontWeight = FontWeight.Bold, fontSize = 12.sp)
          }
        }
      }
    }
  }
}
