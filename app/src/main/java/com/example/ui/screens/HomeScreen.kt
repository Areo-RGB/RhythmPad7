package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.model.ControllerInfo
import com.example.core.model.GameLane
import com.example.core.state.GameInputState
import com.example.ui.components.LaneArrowIcon
import com.example.ui.components.SmBackground
import com.example.ui.components.SmChip
import com.example.ui.components.SmFooter
import com.example.ui.components.SmMenuItem
import com.example.ui.components.SmPanel
import com.example.ui.components.smShadow
import com.example.ui.theme.OpenSans
import com.example.ui.theme.SmCharcoal
import com.example.ui.theme.SmEasyGreen
import com.example.ui.theme.SmP2Red
import com.example.ui.theme.SmPlum
import com.example.ui.theme.SmPlumDark
import com.example.ui.theme.SmTextMuted
import com.example.ui.theme.SmTextSecondary

/**
 * StepMania-style title menu for RhythmPad7: animated orange background with tunnels,
 * logo, plum wheel-item menu choices, and a live dance-mat test panel.
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
  val logoIntro = remember { Animatable(0f) }
  val menuIntro = remember { Animatable(0f) }
  LaunchedEffect(Unit) { logoIntro.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
  LaunchedEffect(Unit) {
    kotlinx.coroutines.delay(250)
    menuIntro.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
  }

  Box(modifier = modifier.fillMaxSize()) {
    SmBackground(showTunnels = true)

    Column(modifier = Modifier.fillMaxSize()) {
      // Top info strip (ScreenTitleMenu VersionInfo / NetworkStatus corner text)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 56.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        SmChip(text = "dance • single", color = SmPlum)
        Spacer(modifier = Modifier.width(10.dp))
        Text(
          text = "1 practice chart loaded",
          style = TextStyle(
            fontFamily = OpenSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = Color.White
          ).smShadow(1.5f)
        )
        Spacer(modifier = Modifier.weight(1f))
        ConnectionBadge(isConnected = controllerInfo.isConnected)
      }

      Row(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Left: logo + live input panel
        Column(
          modifier = Modifier
            .weight(1.1f)
            .fillMaxHeight()
            .padding(start = 64.dp, end = 24.dp),
          verticalArrangement = Arrangement.Center
        ) {
          Logo(
            modifier = Modifier.graphicsLayer {
              val s = 0.6f + 0.4f * logoIntro.value
              scaleX = s
              scaleY = s
              alpha = logoIntro.value
              transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
            }
          )

          Spacer(modifier = Modifier.height(30.dp))

          InputTestPanel(
            controllerInfo = controllerInfo,
            inputState = inputState,
            modifier = Modifier
              .width(520.dp)
              .graphicsLayer { alpha = menuIntro.value }
          )
        }

        // Right: title-menu choices sliding in from the right edge
        Column(
          modifier = Modifier
            .weight(0.9f)
            .fillMaxHeight()
            .graphicsLayer {
              translationX = (1f - menuIntro.value) * size.width
            },
          verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
          horizontalAlignment = Alignment.End
        ) {
          SmMenuItem(
            text = "GAME START",
            caption = "StepMania Pad Foundation #1 • 4-panel practice",
            icon = Icons.Default.PlayArrow,
            highlighted = true,
            onClick = onStartGame,
            modifier = Modifier.testTag("home_button_start_game")
          )
          SmMenuItem(
            text = "CONTROLLER SETTINGS",
            caption = "Map dance-mat panels & view raw input",
            icon = Icons.Default.Settings,
            onClick = onOpenSettings,
            modifier = Modifier.testTag("home_button_controller_settings")
          )
          SmMenuItem(
            text = "RESCAN DEVICES",
            caption = "Search for the USB dance mat again",
            icon = Icons.Default.Refresh,
            onClick = onScanControllers,
            modifier = Modifier.testTag("home_button_rescan")
          )
        }
      }

      SmFooter(
        text = "Step on the pad to test your panels  •  Tap GAME START to play",
        trailing = "RhythmPad7 v0.2.1"
      )
    }
  }
}

@Composable
private fun Logo(modifier: Modifier = Modifier) {
  val transition = rememberInfiniteTransition(label = "logo_glow")
  val glow by transition.animateFloat(
    initialValue = 0.0f,
    targetValue = 0.35f,
    animationSpec = infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
    label = "logo_glow_alpha"
  )

  Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
    Image(
      painter = painterResource(R.drawable.sm_logo_arrow),
      contentDescription = null,
      modifier = Modifier.size(104.dp)
    )
    Spacer(modifier = Modifier.width(14.dp))
    Column {
      Box {
        val logoStyle = TextStyle(
          fontFamily = OpenSans,
          fontWeight = FontWeight.ExtraBold,
          fontSize = 64.sp,
          letterSpacing = (-1).sp
        )
        // Dark outline pass
        Text(
          text = "RhythmPad7",
          style = logoStyle.copy(color = Color(0xFF2B0B15)).smShadow(4f, Color(0x99000000)),
          maxLines = 1,
          softWrap = false
        )
        Text(
          text = "RhythmPad7",
          style = logoStyle.copy(
            brush = Brush.verticalGradient(listOf(Color(0xFFB13C61), SmPlum, SmPlumDark))
          ),
          maxLines = 1,
          softWrap = false,
          modifier = Modifier.graphicsLayer { translationX = -2.dp.toPx(); translationY = -2.dp.toPx() }
        )
        Text(
          text = "RhythmPad7",
          style = logoStyle.copy(color = Color.White.copy(alpha = glow)),
          maxLines = 1,
          softWrap = false,
          modifier = Modifier.graphicsLayer { translationX = -2.dp.toPx(); translationY = -2.dp.toPx() }
        )
      }
      Text(
        text = "DANCE-MAT RHYTHM GAME",
        style = TextStyle(
          fontFamily = OpenSans,
          fontWeight = FontWeight.Bold,
          fontSize = 16.sp,
          letterSpacing = 4.sp,
          color = Color.White
        ).smShadow(2f),
        modifier = Modifier.padding(start = 4.dp)
      )
    }
  }
}

@Composable
private fun ConnectionBadge(isConnected: Boolean) {
  Row(
    modifier = Modifier
      .clip(CircleShape)
      .background(SmCharcoal.copy(alpha = 0.85f))
      .padding(horizontal = 14.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(10.dp)
        .clip(CircleShape)
        .background(if (isConnected) SmEasyGreen else SmP2Red)
    )
    Spacer(modifier = Modifier.width(8.dp))
    Text(
      text = if (isConnected) "DANCE MAT READY" else "NO DANCE MAT",
      style = TextStyle(
        fontFamily = OpenSans,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        letterSpacing = 1.sp,
        color = Color.White
      )
    )
  }
}

@Composable
private fun InputTestPanel(
  controllerInfo: ControllerInfo,
  inputState: GameInputState,
  modifier: Modifier = Modifier
) {
  SmPanel(
    modifier = modifier,
    title = "INPUT STATUS",
    titleTrailing = {
      Text(
        text = if (controllerInfo.isConnected) "CONNECTED" else "DISCONNECTED",
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
      text = if (controllerInfo.isConnected) controllerInfo.name.replace(Regex("\\s+"), " ") else "No USB controller connected",
      style = TextStyle(
        fontFamily = OpenSans,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        color = Color.White
      ),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
    Text(
      text = if (controllerInfo.isConnected) {
        "VID ${controllerInfo.hexVendorId} • PID ${controllerInfo.hexProductId} • device ${controllerInfo.deviceId}"
      } else {
        "Connect the Microntek USB dance mat (0079:0006) via USB-C OTG"
      },
      style = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        color = SmTextMuted
      ),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )

    Spacer(modifier = Modifier.height(14.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceEvenly
    ) {
      GameLane.entries.forEach { lane ->
        val isPressed = inputState.isLanePressed(lane)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          LaneArrowIcon(lane = lane, pressed = isPressed, size = 64.dp)
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = lane.displayName,
            style = TextStyle(
              fontFamily = OpenSans,
              fontWeight = FontWeight.Bold,
              fontSize = 11.sp,
              letterSpacing = 1.sp,
              color = if (isPressed) Color.White else SmTextSecondary
            )
          )
          Text(
            text = "${inputState.getPressCount(lane)}",
            style = TextStyle(
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp,
              color = SmTextMuted
            )
          )
        }
      }
    }
  }
}
