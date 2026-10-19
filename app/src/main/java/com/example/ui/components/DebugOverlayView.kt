package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.core.model.GameLane
import com.example.core.state.GameInputState

/**
 * Floating telemetry HUD showing live hardware controller status, active lane states,
 * press counts, and recent input event history.
 * Makes it effortless to verify BUTTON_1..4 to LEFT, DOWN, UP, RIGHT mappings.
 */
@Composable
fun DebugOverlayView(
  inputState: GameInputState,
  controllerInfo: ControllerInfo,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .width(380.dp)
      .fillMaxHeight(0.90f)
      .clip(RoundedCornerShape(16.dp))
      .background(Color(0xF0070B14))
      .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
      .padding(14.dp)
      .testTag("debug_overlay_hud")
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      // Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "ENGINE DEBUG HUD",
          style = MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            color = Color(0xFF38BDF8)
          )
        )
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (controllerInfo.isConnected) Color(0xFF065F46) else Color(0xFF374151))
            .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
          Text(
            text = if (controllerInfo.isConnected) "USB READY" else "SIM ONLY",
            style = MaterialTheme.typography.labelSmall.copy(
              color = if (controllerInfo.isConnected) Color(0xFF34D399) else Color(0xFF9CA3AF),
              fontWeight = FontWeight.Bold
            )
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))
      HorizontalDivider(color = Color(0xFF1E293B))
      Spacer(modifier = Modifier.height(8.dp))

      // Controller & Source Status
      DebugSectionTitle("CONTROLLER HARDWARE")
      Text(
        text = if (controllerInfo.isConnected) controllerInfo.formattedDetails else "No physical USB controller connected",
        style = MaterialTheme.typography.bodySmall.copy(
          color = if (controllerInfo.isConnected) Color(0xFFE2E8F0) else Color(0xFF94A3B8),
          fontFamily = FontFamily.Monospace
        )
      )

      if (controllerInfo.isMicrontek) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "★ Microntek USB Joystick Confirmed (VID 0079 PID 0006)",
          style = MaterialTheme.typography.labelSmall.copy(
            color = Color(0xFF10B981),
            fontWeight = FontWeight.Bold
          )
        )
      }

      // Profile Status Badge
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = if (controllerInfo.isMicrontekProfileActive) {
          "PROFILE: Confirmed Microntek (BTN 1..4)"
        } else {
          "PROFILE: Custom Mapping"
        },
        style = MaterialTheme.typography.labelSmall.copy(
          color = if (controllerInfo.isMicrontekProfileActive) Color(0xFF34D399) else Color(0xFF38BDF8),
          fontWeight = FontWeight.SemiBold
        )
      )

      // Confirmed button mapping cheat sheet
      Spacer(modifier = Modifier.height(6.dp))
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(Color(0xFF131A26), RoundedCornerShape(6.dp))
          .padding(horizontal = 8.dp, vertical = 4.dp)
      ) {
        Text(
          text = "BTN_1→LEFT • BTN_2→DOWN • BTN_3→UP • BTN_4→RIGHT",
          style = MaterialTheme.typography.labelSmall.copy(
            color = Color(0xFF94A3B8),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp
          )
        )
      }

      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = "ACTIVE SOURCE: ${inputState.activeSource}",
        style = MaterialTheme.typography.labelMedium.copy(
          color = Color(0xFFA5B4FC),
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold
        )
      )

      Spacer(modifier = Modifier.height(8.dp))
      HorizontalDivider(color = Color(0xFF1E293B))
      Spacer(modifier = Modifier.height(8.dp))

      // Live Lane States & Counters
      DebugSectionTitle("LANE STATES & COUNTERS")
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        GameLane.entries.forEach { lane ->
          val isPressed = inputState.isLanePressed(lane)
          val count = inputState.getPressCount(lane)
          val laneColor = lane.defaultColor
          val holders = inputState.getActiveHoldersFor(lane)

          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
          ) {
            Box(
              modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (isPressed) laneColor else Color(0xFF1E293B))
                .border(1.dp, if (isPressed) Color.White else Color(0xFF475569), CircleShape),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = lane.name.take(1),
                style = MaterialTheme.typography.labelSmall.copy(
                  color = if (isPressed) Color.White else Color(0xFF94A3B8),
                  fontWeight = FontWeight.Black
                )
              )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
              text = "$count",
              style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = FontFamily.Monospace,
                color = if (isPressed) laneColor else Color(0xFFCBD5E1),
                fontWeight = FontWeight.Bold
              )
            )
            if (holders.isNotEmpty()) {
              Text(
                text = "${holders.size} held",
                style = MaterialTheme.typography.labelSmall.copy(
                  fontSize = 9.sp,
                  color = Color(0xFF38BDF8)
                )
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))
      HorizontalDivider(color = Color(0xFF1E293B))
      Spacer(modifier = Modifier.height(8.dp))

      // Recent Event History (Bounded)
      DebugSectionTitle("RECENT EVENT TELEMETRY")
      if (inputState.recentEvents.isEmpty()) {
        Text(
          text = "Awaiting input events...",
          style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF64748B))
        )
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          items(inputState.recentEvents.take(10)) { event ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .background(
                  if (event.isPressed) event.lane.defaultColor.copy(alpha = 0.15f)
                  else Color(0x331E293B),
                  RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 6.dp, vertical = 3.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "${event.lane.name} • ${event.actionName} (${event.inputId})",
                style = MaterialTheme.typography.labelSmall.copy(
                  color = if (event.isPressed) event.lane.defaultColor else Color(0xFF94A3B8),
                  fontFamily = FontFamily.Monospace,
                  fontWeight = FontWeight.Bold
                )
              )
              Text(
                text = "${event.source.name} | ${event.timestamp % 100000}ms",
                style = MaterialTheme.typography.labelSmall.copy(
                  color = Color(0xFF64748B),
                  fontFamily = FontFamily.Monospace
                )
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun DebugSectionTitle(title: String) {
  Text(
    text = title,
    style = MaterialTheme.typography.labelSmall.copy(
      fontWeight = FontWeight.Bold,
      color = Color(0xFF94A3B8),
      letterSpacing = 0.5.sp
    ),
    modifier = Modifier.padding(bottom = 2.dp)
  )
}
