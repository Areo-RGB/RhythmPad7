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
import androidx.compose.material.icons.filled.Build
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
import com.example.core.model.Judgment
import com.example.core.model.MenuInput
import com.example.core.model.PlayerProfile
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.example.ui.components.NoPadInputs
import com.example.ui.components.PadNavigation
import kotlinx.coroutines.flow.Flow
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
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
import com.example.ui.theme.SmP1Blue
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
  timingScale: Float,
  onTimingScaleChange: (Float) -> Unit,
  onScanControllers: () -> Unit,
  onOpenSettings: () -> Unit = {},
  modifier: Modifier = Modifier,
  menuInputs: Flow<MenuInput> = NoPadInputs,
  inputOffsetMs: Int = 0,
  onInputOffsetChange: (Int) -> Unit = {},
  profile: PlayerProfile = PlayerProfile.GUEST,
  onSelectProfile: (PlayerProfile) -> Unit = {},
  /** Best grade tier across the profile's saved scores (1 = AAAA .. 17 = D), null if none. */
  bestGradeTier: Int? = null,
  /** Number of charts with a saved score for this profile. */
  chartsPlayed: Int = 0,
  /** Best DP% across the profile's saved scores. */
  bestPercent: Double? = null,
  autoSyncEnabled: Boolean = false,
  onToggleAutoSync: () -> Unit = {}
) {
  // Title-menu cursor (dance mat: ↑/↓ move, Start/Circle choose)
  var cursor by remember { mutableIntStateOf(0) }
  val step = Judgment.TIMING_SCALE_STEP
  fun stepTiming(dir: Int) {
    val next = timingScale + dir * step
    // Tapping/confirming past the max wraps back to the minimum
    onTimingScaleChange(
      if (next > Judgment.MAX_TIMING_SCALE + 0.001f) Judgment.MIN_TIMING_SCALE
      else next.coerceIn(Judgment.MIN_TIMING_SCALE, Judgment.MAX_TIMING_SCALE)
    )
  }
  val actions = listOf(onStartGame, { stepTiming(1) }, onOpenSettings, onScanControllers)
  PadNavigation(menuInputs) { input ->
    when {
      cursor == 1 && input == MenuInput.Arrow(GameLane.LEFT) -> stepTiming(-1)
      cursor == 1 && input == MenuInput.Arrow(GameLane.RIGHT) -> stepTiming(1)
      input == MenuInput.Arrow(GameLane.UP) -> cursor = (cursor + actions.size - 1) % actions.size
      input == MenuInput.Arrow(GameLane.DOWN) -> cursor = (cursor + 1) % actions.size
      input.isConfirm -> actions[cursor]()
    }
  }

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
        if (!PROMINENT_PLAYER_CARD) {
          ProfileSelector(
            profile = profile,
            bestGradeTier = bestGradeTier,
            onClick = { onSelectProfile(nextProfile(profile)) }
          )
          Spacer(modifier = Modifier.width(12.dp))
        }
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

          if (PROMINENT_PLAYER_CARD) {
            PlayerPanel(
              profile = profile,
              bestGradeTier = bestGradeTier,
              chartsPlayed = chartsPlayed,
              bestPercent = bestPercent,
              onSwitch = { onSelectProfile(nextProfile(profile)) },
              modifier = Modifier
                .width(520.dp)
                .graphicsLayer { alpha = menuIntro.value }
            )
          } else {
            InputTestPanel(
              controllerInfo = controllerInfo,
              inputState = inputState,
              modifier = Modifier
                .width(520.dp)
                .graphicsLayer { alpha = menuIntro.value }
            )
          }
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
            highlighted = cursor == 0,
            onClick = onStartGame,
            modifier = Modifier.testTag("home_button_start_game")
          )
          SmMenuItem(
            text = "TIMING WINDOW  ×${"%.2f".format(timingScale)}",
            caption = "± ${(Judgment.W1.windowMs * timingScale).let { "%.1f".format(it) }} ms Flawless • ± ${Judgment.missThresholdMsFor(timingScale)} ms max  •  ← → adjust",
            icon = Icons.Default.Settings,
            highlighted = cursor == 1,
            onClick = { stepTiming(1) },
            modifier = Modifier.testTag("home_button_timing_window")
          )
          SmMenuItem(
            text = "SETTINGS",
            caption = "Input status, sync offset & controller mapping",
            icon = Icons.Default.Build,
            highlighted = cursor == 2,
            onClick = onOpenSettings,
            modifier = Modifier.testTag("home_button_settings")
          )
          SmMenuItem(
            text = "RESCAN DEVICES",
            caption = "Search for the USB dance mat again",
            icon = Icons.Default.Refresh,
            highlighted = cursor == 3,
            onClick = onScanControllers,
            modifier = Modifier.testTag("home_button_rescan")
          )
        }
      }

      SmFooter(
        text = "Pad: ↑ ↓ choose  •  START to select  •  or tap a menu item",
        trailing = "RhythmPad7 v0.3.0",
        playerName = profile.displayName
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
internal fun InputTestPanel(
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


/**
 * StepMania ScreenSelectProfile-style player card: "PLAYER 1" label + profile name + best grade.
 * Tap to cycle through profiles; results are saved under the active one.
 */
@Composable
private fun ProfileSelector(profile: PlayerProfile, bestGradeTier: Int?, onClick: () -> Unit) {
  val shape = com.example.ui.components.ChamferShape(12.dp)
  val labelStyle = TextStyle(
    fontFamily = OpenSans,
    fontWeight = FontWeight.Bold,
    fontSize = 9.sp,
    letterSpacing = 1.5.sp,
    color = Color.White.copy(alpha = 0.7f)
  )
  Row(
    modifier = Modifier
      .height(52.dp)
      .clip(shape)
      .background(Brush.verticalGradient(listOf(SmPlum, SmPlumDark)))
      .border(1.dp, Color(0x66FFFFFF), shape)
      .clickable(onClick = onClick)
      // Extra side padding so content clears the 12 dp chamfer points
      .padding(horizontal = 24.dp)
      .testTag("home_profile_selector"),
    verticalAlignment = Alignment.CenterVertically
  ) {
    // Both columns share the same label row + fixed-height value row, so labels and values line up
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(text = "PLAYER 1", style = labelStyle)
      Box(modifier = Modifier.height(24.dp), contentAlignment = Alignment.CenterStart) {
        Text(
          text = profile.displayName.uppercase(),
          style = TextStyle(fontFamily = OpenSans, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, letterSpacing = 1.sp, color = Color.White).smShadow(1.5f),
          maxLines = 1
        )
      }
    }
    if (bestGradeTier != null) {
      Spacer(modifier = Modifier.width(16.dp))
      Box(
        modifier = Modifier
          .width(1.dp)
          .height(30.dp)
          .background(Color(0x40FFFFFF))
      )
      Spacer(modifier = Modifier.width(16.dp))
      Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text(text = "BEST", style = labelStyle)
        Box(modifier = Modifier.height(24.dp), contentAlignment = Alignment.Center) {
          Image(
            painter = painterResource(gradeDrawableForTier(bestGradeTier)),
            contentDescription = "Best grade",
            modifier = Modifier.height(22.dp)
          )
        }
      }
    }
  }
}


/** true = large PLAYER 1 panel under the logo (input status lives in Settings); false = compact card in the top bar. */
private const val PROMINENT_PLAYER_CARD = true

private fun nextProfile(current: PlayerProfile): PlayerProfile {
  val all = PlayerProfile.ALL
  return all[(all.indexOf(current) + 1) % all.size]
}

/**
 * Large StepMania-style player panel (same SmPanel frame and width as INPUT STATUS):
 * profile name, best grade, best score and charts played. Tap anywhere to switch profile.
 */
@Composable
private fun PlayerPanel(
  profile: PlayerProfile,
  bestGradeTier: Int?,
  chartsPlayed: Int,
  bestPercent: Double?,
  onSwitch: () -> Unit,
  modifier: Modifier = Modifier
) {
  val labelStyle = TextStyle(
    fontFamily = OpenSans,
    fontWeight = FontWeight.Bold,
    fontSize = 11.sp,
    letterSpacing = 1.5.sp,
    color = Color.White.copy(alpha = 0.65f)
  )
  SmPanel(
    modifier = modifier
      .clickable(onClick = onSwitch)
      .testTag("home_player_panel"),
    title = "PLAYER 1",
    // Plum body to match the title-menu items
    titleColor = Color(0xFFB13C61),
    background = Brush.verticalGradient(listOf(Color(0xE6882D47), Color(0xE64A1827))),
    titleTrailing = {
      Text(
        text = "TAP TO SWITCH  ⇄",
        style = TextStyle(
          fontFamily = OpenSans,
          fontWeight = FontWeight.Bold,
          fontSize = 12.sp,
          letterSpacing = 1.sp,
          color = Color.White.copy(alpha = 0.75f)
        )
      )
    }
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = profile.displayName.uppercase(),
          style = TextStyle(
            fontFamily = OpenSans,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 40.sp,
            letterSpacing = 2.sp,
            color = Color.White
          ).smShadow(2f),
          maxLines = 1
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
          Column {
            Text(text = "BEST SCORE", style = labelStyle)
            Text(
              text = bestPercent?.let { String.format(java.util.Locale.US, "%.2f%%", it) } ?: "—",
              style = TextStyle(fontFamily = OpenSans, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
            )
          }
          Column {
            Text(text = "CHARTS PLAYED", style = labelStyle)
            Text(
              text = "$chartsPlayed",
              style = TextStyle(fontFamily = OpenSans, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
            )
          }
        }
      }
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "BEST GRADE", style = labelStyle)
        Spacer(modifier = Modifier.height(4.dp))
        Box(modifier = Modifier.height(64.dp), contentAlignment = Alignment.Center) {
          if (bestGradeTier != null) {
            Image(
              painter = painterResource(gradeDrawableForTier(bestGradeTier)),
              contentDescription = "Best grade",
              modifier = Modifier.height(60.dp)
            )
          } else {
            Text(
              text = "—",
              style = TextStyle(fontFamily = OpenSans, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = SmTextMuted)
            )
          }
        }
      }
    }
  }
}

