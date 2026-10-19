package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
 * Landscape Tablet Home Screen for RhythmPad7 v0.1.0.
 */
@Composable
fun HomeScreen(
  controllerInfo: ControllerInfo,
  inputState: GameInputState,
  onStartGame: () -> Unit,
  onOpenSettings: () -> Unit,
  onScanControllers: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(
        Brush.radialGradient(
          colors = listOf(
            Color(0xFF131C2E),
            Color(0xFF090E17),
            Color(0xFF04060A)
          ),
          radius = 1600f
        )
      )
      .padding(32.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxSize(),
      horizontalArrangement = Arrangement.spacedBy(36.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Left Column: Branding, Title, Version, Action Buttons
      Column(
        modifier = Modifier
          .weight(1.2f)
          .fillMaxHeight(),
        verticalArrangement = Arrangement.Center
      ) {
        // Version Badge & Platform Tag
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(6.dp))
              .background(Color(0xFF1E293B))
              .border(1.dp, Color(0xFF334155), RoundedCornerShape(6.dp))
              .padding(horizontal = 10.dp, vertical = 4.dp)
          ) {
            Text(
              text = "v0.2.0 • STEP 2",
              style = MaterialTheme.typography.labelSmall.copy(
                color = Color(0xFF38BDF8),
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
              )
            )
          }

          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(6.dp))
              .background(Color(0x3310B981))
              .border(1.dp, Color(0xFF10B981), RoundedCornerShape(6.dp))
              .padding(horizontal = 10.dp, vertical = 4.dp)
          ) {
            Text(
              text = "SCROLLING GAMEPLAY + USB DANCE MAT",
              style = MaterialTheme.typography.labelSmall.copy(
                color = Color(0xFF34D399),
                fontWeight = FontWeight.Bold
              )
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Main Title
        Text(
          text = "RhythmPad7",
          style = MaterialTheme.typography.displayLarge.copy(
            fontSize = 58.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-1).sp,
            brush = Brush.horizontalGradient(
              listOf(
                Color(0xFFFFFFFF),
                Color(0xFF00E5FF),
                Color(0xFF00E676)
              )
            )
          )
        )

        Text(
          text = "Dance-Mat Rhythm Game Engine for Xiaomi Pad 7",
          style = MaterialTheme.typography.titleMedium.copy(
            color = Color(0xFF94A3B8),
            fontWeight = FontWeight.Medium
          )
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Big Primary Action: START GAME
        Button(
          onClick = onStartGame,
          modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .shadow(16.dp, RoundedCornerShape(16.dp), spotColor = Color(0xFF00E5FF))
            .testTag("home_button_start_game"),
          shape = RoundedCornerShape(16.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF00E5FF),
            contentColor = Color(0xFF04060A)
          )
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
          ) {
            Icon(
              imageVector = Icons.Default.PlayArrow,
              contentDescription = "Start Game",
              modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "START GAME",
              style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp
              )
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Secondary Action: CONTROLLER SETTINGS
        OutlinedButton(
          onClick = onOpenSettings,
          modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag("home_button_controller_settings"),
          shape = RoundedCornerShape(16.dp),
          colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Color(0xFFF1F5F9)
          ),
          border = BorderStroke(
            width = 1.5.dp,
            brush = Brush.horizontalGradient(
              listOf(Color(0xFF38BDF8), Color(0xFF334155))
            )
          )
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
          ) {
            Icon(
              imageVector = Icons.Default.Settings,
              contentDescription = "Controller Settings",
              modifier = Modifier.size(22.dp),
              tint = Color(0xFF38BDF8)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "CONTROLLER SETTINGS",
              style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
              )
            )
          }
        }
      }

      // Right Column: Controller Status Panel & Live Lane Preview
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight(),
        verticalArrangement = Arrangement.Center
      ) {
        // Controller Status Card
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(20.dp)),
          color = Color(0xCC0E1524),
          tonalElevation = 4.dp
        ) {
          Column(modifier = Modifier.padding(20.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = if (controllerInfo.isConnected) Icons.Default.SportsEsports else Icons.Default.Gamepad,
                  contentDescription = "Controller Icon",
                  tint = if (controllerInfo.isConnected) Color(0xFF34D399) else Color(0xFF64748B),
                  modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "INPUT STATUS",
                  style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    letterSpacing = 1.sp
                  )
                )
              }

              IconButton(
                onClick = onScanControllers,
                modifier = Modifier.size(32.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Refresh,
                  contentDescription = "Scan Controllers",
                  tint = Color(0xFF38BDF8),
                  modifier = Modifier.size(18.dp)
                )
              }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Controller state indicator
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(10.dp)
                  .clip(CircleShape)
                  .background(if (controllerInfo.isConnected) Color(0xFF10B981) else Color(0xFFEF4444))
              )
              Text(
                text = if (controllerInfo.isConnected) "Controller Connected" else "No USB Controller Connected",
                style = MaterialTheme.typography.titleSmall.copy(
                  fontWeight = FontWeight.SemiBold,
                  color = if (controllerInfo.isConnected) Color(0xFFF1F5F9) else Color(0xFF94A3B8)
                )
              )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
              text = if (controllerInfo.isConnected) {
                controllerInfo.formattedDetails
              } else {
                "Connect your physical Microntek USB Dance Mat (VID 0x0079 / PID 0x0006) via USB-C OTG."
              },
              style = MaterialTheme.typography.bodySmall.copy(
                color = Color(0xFF64748B),
                fontFamily = if (controllerInfo.isConnected) FontFamily.Monospace else FontFamily.Default
              )
            )
          }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Quick Input Test Pad
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(20.dp)),
          color = Color(0xCC0E1524)
        ) {
          Column(modifier = Modifier.padding(20.dp)) {
            Text(
              text = "LIVE INPUT TEST",
              style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color(0xFF94A3B8),
                letterSpacing = 1.sp
              )
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              GameLane.entries.forEach { lane ->
                val isPressed = inputState.isLanePressed(lane)
                val count = inputState.getPressCount(lane)
                val laneColor = lane.defaultColor

                Box(
                  modifier = Modifier
                    .weight(1f)
                    .height(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                      if (isPressed) laneColor.copy(alpha = 0.85f) else Color(0xFF1E293B)
                    )
                    .border(
                      width = if (isPressed) 2.dp else 1.dp,
                      color = if (isPressed) Color.White else Color(0xFF334155),
                      shape = RoundedCornerShape(12.dp)
                    ),
                  contentAlignment = Alignment.Center
                ) {
                  Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                      text = lane.displayName,
                      style = MaterialTheme.typography.labelMedium.copy(
                        color = if (isPressed) Color.White else laneColor,
                        fontWeight = FontWeight.Black
                      )
                    )
                    Text(
                      text = "$count",
                      style = MaterialTheme.typography.labelSmall.copy(
                        color = if (isPressed) Color.White else Color(0xFF64748B),
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
    }
  }
}
