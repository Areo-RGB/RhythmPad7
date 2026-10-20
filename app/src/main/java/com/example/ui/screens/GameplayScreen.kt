package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.chart.Difficulty
import com.example.core.gameplay.FullComboKind
import com.example.core.gameplay.GameplayRoundState
import com.example.core.model.ControllerInfo
import com.example.core.model.GameLane
import com.example.core.model.Judgment
import com.example.core.model.MenuButton
import com.example.core.model.MenuInput
import com.example.ui.components.NoPadInputs
import com.example.ui.components.PadNavigation
import kotlinx.coroutines.flow.Flow
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.example.core.state.GameInputState
import com.example.ui.components.ChamferShape
import com.example.ui.components.DebugOverlayView
import com.example.ui.components.LaneArrowIcon
import com.example.ui.components.PlayfieldGeometry
import com.example.ui.components.PlayfieldView
import com.example.ui.components.SmBackground
import com.example.ui.components.SmButton
import com.example.ui.components.SmButtonStyle
import com.example.ui.components.SmChip
import com.example.ui.components.SmIconButton
import com.example.ui.components.SmMeterBar
import com.example.ui.components.SmPanel
import com.example.ui.components.rememberAssetBitmap
import com.example.ui.components.smShadow
import com.example.ui.theme.OpenSans
import com.example.ui.theme.SmBeginnerMint
import com.example.ui.theme.SmCharcoal
import com.example.ui.theme.SmCharcoalLight
import com.example.ui.theme.SmEasyGreen
import com.example.ui.theme.SmMaxCombo
import com.example.ui.theme.SmP1Blue
import com.example.ui.theme.SmPlum
import com.example.ui.theme.SmStageOrange
import com.example.ui.theme.SmTextMuted
import com.example.ui.theme.SmTextSecondary
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Roboto bold italic used for judgments and combo ("Combo Numbers" / Judgment Normal). */
private val JudgmentTextStyle = TextStyle(
  fontFamily = FontFamily.Default,
  fontWeight = FontWeight.Black,
  fontStyle = FontStyle.Italic
)

/** StepMania HoldJudgment "OK" yellow (JudgmentLine_W2 tone). */
private val HoldOkColor = Color(0xFFF1E4A2)

/** Tabular digits so the score does not jitter while counting. */
private val NumberStyle = TextStyle(
  fontFamily = FontFamily.Default,
  fontWeight = FontWeight.Bold,
  fontFeatureSettings = "tnum"
)

/**
 * Three-zone landscape rhythm gameplay screen styled after the StepMania 5 default theme.
 *
 * - Phase 1: Uses monotonic [com.example.core.gameplay.GameplayClock] synchronization
 *   on each frame rather than integer millisecond delta accumulation.
 * - Phase 3: Keeps a single centered 560–640 dp note field with four equal-width lanes
 *   and unobstructed receptors.
 * - Phase 4: Separates the UI into three dedicated landscape columns:
 *   - Left Zone: Back button, stage plate, song info, BPM, song meter, and USB mat status.
 *   - Center Zone: Unobstructed noteskin note field, Ready/Go countdown, judgment and combo.
 *   - Right Zone: Touch controls (Restart, Debug HUD, Pause), score frame, accuracy,
 *     and judgment breakdown.
 */
@Composable
fun GameplayScreen(
  inputState: GameInputState,
  roundState: GameplayRoundState,
  controllerInfo: ControllerInfo,
  isDebugOverlayVisible: Boolean,
  isPaused: Boolean,
  onSyncRoundClock: () -> Unit,
  onToggleDebug: () -> Unit,
  onTogglePause: () -> Unit,
  onResume: () -> Unit,
  onRestartRound: () -> Unit,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
  menuInputs: Flow<MenuInput> = NoPadInputs
) {
  BackHandler(onBack = onNavigateBack)

  // Pad control: Start pauses; the pause menu and results take arrows + Start/Select like StepMania
  var pauseCursor by remember { mutableIntStateOf(0) }
  var resultCursor by remember { mutableIntStateOf(0) }
  LaunchedEffect(isPaused) { if (isPaused) pauseCursor = 0 }
  LaunchedEffect(roundState.isCompleted) { if (roundState.isCompleted) resultCursor = 0 }
  PadNavigation(menuInputs) { input ->
    when {
      roundState.isCompleted -> when {
        input is MenuInput.Arrow -> resultCursor = 1 - resultCursor
        input.isConfirm -> if (resultCursor == 0) onRestartRound() else onNavigateBack()
        input.isBack -> onNavigateBack()
      }
      isPaused -> when {
        input == MenuInput.Arrow(GameLane.UP) -> pauseCursor = (pauseCursor + 2) % 3
        input == MenuInput.Arrow(GameLane.DOWN) -> pauseCursor = (pauseCursor + 1) % 3
        input.isConfirm -> when (pauseCursor) {
          0 -> onResume()
          1 -> onRestartRound()
          else -> onNavigateBack()
        }
        input.isBack -> onResume()
      }
      // During play only Start does anything; arrows are notes
      input == MenuInput.Button(MenuButton.START) -> onTogglePause()
    }
  }

  // Authoritative monotonic clock sync on every vsync frame (eliminates 60/90/120/144Hz rounding drift)
  LaunchedEffect(roundState.isRunning, roundState.isPaused, roundState.isCompleted) {
    if (roundState.isRunning && !roundState.isPaused && !roundState.isCompleted) {
      while (true) {
        withFrameNanos {
          onSyncRoundClock()
        }
      }
    }
  }

  BoxWithConstraints(
    modifier = modifier
      .fillMaxSize()
      .background(SmCharcoal)
  ) {
    val highwayWidthDp = PlayfieldGeometry.calculateHighwayWidthDp(maxWidth.value).dp

    val songBackground by rememberAssetBitmap(roundState.meta.backgroundPath)
    val bg = songBackground
    if (bg != null) {
      // Song background, dimmed like StepMania's BGBrightness / ScreenFilter
      Image(
        bitmap = bg,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
      )
      Box(modifier = Modifier.fillMaxSize().background(Color(0x99000000)))
    } else {
      SmBackground(animated = false, dim = 0.62f)
    }

    // Three-Zone Landscape Layout: [ Left HUD Wing | Centered 4-Lane Note Field | Right Score & Controls Wing ]
    Row(
      modifier = Modifier
        .fillMaxSize()
        .testTag("gameplay_three_zone_row"),
      verticalAlignment = Alignment.Top
    ) {
      // =========================================================================
      // LEFT ZONE: Navigation, Song Info, Song Meter & Mat Telemetry
      // =========================================================================
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .padding(horizontal = 16.dp, vertical = 16.dp)
          .verticalScroll(rememberScrollState())
          .testTag("gameplay_zone_left"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          SmIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back to Home",
            onClick = onNavigateBack,
            modifier = Modifier.testTag("gameplay_button_back")
          )
          SmChip(
            text = if (roundState.isSongRound) "EVENT STAGE" else "PRACTICE STAGE",
            color = SmStageOrange
          )
        }

        SongInfoPanel(roundState = roundState)

        MatStatusPanel(controllerInfo = controllerInfo, inputState = inputState)

        // Optional Debug Telemetry Panel (housed inside Left Zone so the note field is never blocked)
        AnimatedVisibility(
          visible = isDebugOverlayVisible,
          enter = fadeIn(),
          exit = fadeOut()
        ) {
          DebugOverlayView(
            inputState = inputState,
            roundState = roundState,
            controllerInfo = controllerInfo,
            modifier = Modifier.fillMaxWidth()
          )
        }
      }

      // =========================================================================
      // CENTER ZONE: Unobstructed Note Field, Countdown, Judgment & Combo
      // =========================================================================
      Box(
        modifier = Modifier
          .width(highwayWidthDp)
          .fillMaxHeight()
          .testTag("gameplay_zone_center"),
        contentAlignment = Alignment.Center
      ) {
        PlayfieldView(
          inputState = inputState,
          roundState = roundState,
          modifier = Modifier.fillMaxSize()
        )

        CountdownBanner(
          countdown = roundState.countdownText,
          modifier = Modifier.align(Alignment.Center)
        )

        JudgmentAndCombo(
          roundState = roundState,
          modifier = Modifier
            .align(Alignment.Center)
            .offset(y = 40.dp)
        )
      }

      // =========================================================================
      // RIGHT ZONE: Touch Controls, Score, Accuracy & Judgment Breakdown
      // =========================================================================
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .padding(horizontal = 16.dp, vertical = 16.dp)
          .verticalScroll(rememberScrollState())
          .testTag("gameplay_zone_right"),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          SmIconButton(
            icon = Icons.Default.Refresh,
            contentDescription = "Restart Practice Round",
            onClick = onRestartRound,
            modifier = Modifier.testTag("gameplay_button_restart")
          )
          SmIconButton(
            icon = Icons.Default.BugReport,
            contentDescription = "Toggle Debug HUD",
            onClick = onToggleDebug,
            active = isDebugOverlayVisible,
            modifier = Modifier.testTag("gameplay_button_toggle_debug")
          )
          SmIconButton(
            icon = Icons.Default.Pause,
            contentDescription = "Pause",
            onClick = onTogglePause,
            modifier = Modifier.testTag("gameplay_button_pause")
          )
        }

        ScoreFrame(roundState = roundState)

        SmPanel(
          modifier = Modifier.fillMaxWidth(),
          title = "JUDGMENTS",
          contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
          JudgmentLine("FLAWLESS", "±22.5ms", roundState.w1Count, Judgment.W1.color)
          JudgmentLine("PERFECT", "±45ms", roundState.w2Count, Judgment.W2.color)
          JudgmentLine("GREAT", "±90ms", roundState.w3Count, Judgment.W3.color)
          JudgmentLine("GOOD", "±135ms", roundState.w4Count, Judgment.W4.color)
          JudgmentLine("BAD", "±180ms", roundState.w5Count, Judgment.W5.color)
          JudgmentLine("MISS", ">180ms", roundState.missCount, Judgment.MISS.color)
          if (roundState.totalHolds > 0) {
            JudgmentLine("OK", "holds", roundState.heldCount, HoldOkColor)
            JudgmentLine("NG", "let go", roundState.letGoCount, Judgment.MISS.color)
          }
          Spacer(modifier = Modifier.height(4.dp))
          JudgmentLine("MAX COMBO", null, roundState.maxCombo, SmMaxCombo)
        }
      }
    }

    // =========================================================================
    // MODAL OVERLAYS: Pause Menu & Evaluation
    // =========================================================================
    if (isPaused && !roundState.isCompleted) {
      PauseOverlay(
        roundState = roundState,
        cursor = pauseCursor,
        onResume = onResume,
        onRestartRound = onRestartRound,
        onNavigateBack = onNavigateBack
      )
    }

    if (roundState.isCompleted) {
      EvaluationOverlay(
        roundState = roundState,
        cursor = resultCursor,
        onRestartRound = onRestartRound,
        onNavigateBack = onNavigateBack
      )
    }
  }
}

// ---------------------------------------------------------------------------
// Left zone
// ---------------------------------------------------------------------------

@Composable
private fun SongInfoPanel(roundState: GameplayRoundState) {
  SmPanel(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("gameplay_hud_left_panel"),
    title = "NOW PLAYING",
    titleTrailing = {
      Text(
        text = roundState.phase.name,
        style = TextStyle(
          fontFamily = OpenSans,
          fontWeight = FontWeight.Bold,
          fontSize = 11.sp,
          letterSpacing = 1.sp,
          color = Color.White.copy(alpha = 0.8f)
        )
      )
    }
  ) {
    Text(
      text = roundState.meta.title,
      style = TextStyle(
        fontFamily = OpenSans,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        color = Color.White
      ),
      maxLines = 2,
      overflow = TextOverflow.Ellipsis
    )
    Text(
      text = roundState.meta.artist,
      style = TextStyle(
        fontFamily = OpenSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        color = SmTextMuted
      )
    )

    Spacer(modifier = Modifier.height(12.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
      // BPMDisplay
      Column {
        Text(
          text = "BPM",
          style = TextStyle(
            fontFamily = OpenSans,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            color = SmTextMuted
          )
        )
        Text(
          text = roundState.meta.bpmLabel,
          style = NumberStyle.copy(fontSize = 30.sp, color = Color.White)
        )
      }
      Spacer(modifier = Modifier.weight(1f))
      // Difficulty plate (dance-single / Beginner)
      Row(
        modifier = Modifier
          .clip(ChamferShape(10.dp, pointLeft = false))
          .background(Difficulty.entries[roundState.meta.difficultyIndex].color())
          .padding(start = 12.dp, end = 18.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = roundState.meta.difficultyName.uppercase(Locale.US),
          style = TextStyle(
            fontFamily = OpenSans,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 12.sp,
            color = SmCharcoal
          )
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
          text = "${roundState.meta.meter}",
          style = NumberStyle.copy(fontSize = 20.sp, color = SmCharcoal)
        )
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    val progress = if (roundState.totalNotes > 0) {
      roundState.judgedNotesCount.toFloat() / roundState.totalNotes.toFloat()
    } else 0f

    SmMeterBar(progress = progress, modifier = Modifier.fillMaxWidth())

    Spacer(modifier = Modifier.height(6.dp))

    Row(modifier = Modifier.fillMaxWidth()) {
      Text(
        text = String.format(Locale.US, "%d:%05.2f", roundState.currentGameTimeMs / 60000, (roundState.currentGameTimeMs % 60000) / 1000.0),
        style = NumberStyle.copy(fontSize = 13.sp, color = SmTextSecondary)
      )
      Spacer(modifier = Modifier.weight(1f))
      Text(
        text = "${roundState.judgedNotesCount} / ${roundState.totalNotes} notes",
        style = NumberStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Normal, color = SmTextSecondary)
      )
    }
  }
}

@Composable
private fun MatStatusPanel(controllerInfo: ControllerInfo, inputState: GameInputState) {
  SmPanel(
    modifier = Modifier
      .fillMaxWidth()
      .let { mod -> if (!controllerInfo.isConnected) mod.testTag("gameplay_banner_connect_usb") else mod },
    title = if (controllerInfo.isConnected) "DANCE MAT ACTIVE" else "CONNECT USB DANCE MAT",
    titleColor = if (controllerInfo.isConnected) SmPlum else Color(0xFFB0302B),
    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 10.dp)
  ) {
    Text(
      text = if (controllerInfo.isConnected) {
        controllerInfo.name.replace(Regex("\\s+"), " ")
      } else {
        "Microntek USB Joystick (0079:0006) via OTG"
      },
      style = TextStyle(
        fontFamily = OpenSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        color = SmTextSecondary
      ),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceEvenly
    ) {
      GameLane.entries.forEach { lane ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          LaneArrowIcon(lane = lane, pressed = inputState.isLanePressed(lane), size = 44.dp)
          Text(
            text = "${inputState.getPressCount(lane)}",
            style = NumberStyle.copy(fontSize = 12.sp, color = SmTextMuted)
          )
        }
      }
    }
  }
}

// ---------------------------------------------------------------------------
// Center zone
// ---------------------------------------------------------------------------

@Composable
private fun CountdownBanner(countdown: String?, modifier: Modifier = Modifier) {
  if (countdown == null) return
  val isGo = countdown == "GO!"
  val pop = remember(countdown) { Animatable(0f) }
  LaunchedEffect(countdown) { pop.animateTo(1f, tween(260, easing = FastOutSlowInEasing)) }

  Column(
    modifier = modifier
      .padding(bottom = 120.dp)
      .testTag("gameplay_text_countdown"),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Image(
      painter = painterResource(if (isGo) R.drawable.sm_go else R.drawable.sm_ready),
      contentDescription = countdown,
      modifier = Modifier
        .width(380.dp)
        .height(76.dp)
        .graphicsLayer {
          val s = if (isGo) 1.25f - 0.25f * pop.value else 1f
          scaleX = s
          scaleY = s
          alpha = if (isGo) pop.value else 1f
        }
    )
    if (!isGo) {
      Text(
        text = countdown,
        style = JudgmentTextStyle.copy(
          fontSize = 72.sp,
          color = Color.White
        ).smShadow(4f),
        modifier = Modifier.graphicsLayer {
          val s = 1.5f - 0.5f * pop.value
          scaleX = s
          scaleY = s
          alpha = pop.value
        }
      )
    }
  }
}

private data class JudgmentAnimState(
  val zoomX: Float,
  val zoomY: Float,
  val alpha: Float,
  val offsetXDp: Float = 0f,
  val offsetYDp: Float = 0f,
  val glowAlpha: Float = 0f
)

private fun smoothStep(t: Float): Float {
  val u = t.coerceIn(0f, 1f)
  return u * u * (3f - 2f * u)
}

/**
 * StepMania `JudgmentW1Command` .. `JudgmentMissCommand` (`Themes/default/metrics.ini`).
 */
private fun computeJudgmentAnim(judgment: Judgment, elapsedMs: Long): JudgmentAnimState? {
  if (elapsedMs < 0L) return null
  val t = elapsedMs.toFloat()
  return when (judgment) {
    Judgment.W1 -> {
      // zoom,0.9;smooth,0.05;zoom,0.75;sleep,0.8;smooth,0.1;zoomy,0.5;zoomx,1.3;diffusealpha,0;
      // glowblink;effectperiod,0.05;effectcolor1,color("1,1,1,0");effectcolor2,color("1,1,1,0.25")
      if (elapsedMs > 950L) return null
      val glow = if ((elapsedMs / 25L) % 2L == 1L) 0.25f else 0f
      when {
        t < 50f -> {
          val z = 0.9f + (0.75f - 0.9f) * smoothStep(t / 50f)
          JudgmentAnimState(zoomX = z, zoomY = z, alpha = 1f, glowAlpha = glow)
        }
        t < 850f -> JudgmentAnimState(zoomX = 0.75f, zoomY = 0.75f, alpha = 1f, glowAlpha = glow)
        else -> {
          val u = smoothStep((t - 850f) / 100f)
          JudgmentAnimState(
            zoomX = 0.75f + (1.3f - 0.75f) * u,
            zoomY = 0.75f + (0.5f - 0.75f) * u,
            alpha = 1f - u,
            glowAlpha = glow * (1f - u)
          )
        }
      }
    }

    Judgment.W2, Judgment.W3, Judgment.W4 -> {
      // zoom,0.9;smooth,0.05;zoom,0.75;sleep,0.5;smooth,0.1;zoomy,0.5;zoomx,2;diffusealpha,0
      if (elapsedMs > 650L) return null
      when {
        t < 50f -> {
          val z = 0.9f + (0.75f - 0.9f) * smoothStep(t / 50f)
          JudgmentAnimState(zoomX = z, zoomY = z, alpha = 1f)
        }
        t < 550f -> JudgmentAnimState(zoomX = 0.75f, zoomY = 0.75f, alpha = 1f)
        else -> {
          val u = smoothStep((t - 550f) / 100f)
          JudgmentAnimState(
            zoomX = 0.75f + (2f - 0.75f) * u,
            zoomY = 0.75f + (0.5f - 0.75f) * u,
            alpha = 1f - u
          )
        }
      }
    }

    Judgment.W5 -> {
      // zoom,0.75;vibrate;effectmagnitude,1,2,2;sleep,0.5;smooth,0.1;zoomy,0.5;zoomx,2;diffusealpha,0
      if (elapsedMs > 600L) return null
      val vibX = if ((elapsedMs / 16L) % 2L == 0L) 1f else -1f
      val vibY = if ((elapsedMs / 24L) % 2L == 0L) 2f else -2f
      if (t < 500f) {
        JudgmentAnimState(zoomX = 0.75f, zoomY = 0.75f, alpha = 1f, offsetXDp = vibX, offsetYDp = vibY)
      } else {
        val u = smoothStep((t - 500f) / 100f)
        JudgmentAnimState(
          zoomX = 0.75f + (2f - 0.75f) * u,
          zoomY = 0.75f + (0.5f - 0.75f) * u,
          alpha = 1f - u,
          offsetXDp = vibX,
          offsetYDp = vibY
        )
      }
    }

    Judgment.MISS -> {
      // zoom,0.75;y,-20;smooth,0.8;y,20;sleep,0.5;smooth,0.1;zoomy,0.5;zoomx,2;diffusealpha,0
      if (elapsedMs > 1400L) return null
      when {
        t < 800f -> {
          val y = -20f + 40f * smoothStep(t / 800f)
          JudgmentAnimState(zoomX = 0.75f, zoomY = 0.75f, alpha = 1f, offsetYDp = y)
        }
        t < 1300f -> JudgmentAnimState(zoomX = 0.75f, zoomY = 0.75f, alpha = 1f, offsetYDp = 20f)
        else -> {
          val u = smoothStep((t - 1300f) / 100f)
          JudgmentAnimState(
            zoomX = 0.75f + (2f - 0.75f) * u,
            zoomY = 0.75f + (0.5f - 0.75f) * u,
            alpha = 1f - u,
            offsetYDp = 20f
          )
        }
      }
    }
  }
}

@Composable
private fun JudgmentAndCombo(roundState: GameplayRoundState, modifier: Modifier = Modifier) {
  val lastJudgment = roundState.lastJudgment
  val elapsed = if (lastJudgment != null) roundState.currentGameTimeMs - lastJudgment.triggeredAtGameTimeMs else Long.MAX_VALUE
  val anim = if (lastJudgment != null) computeJudgmentAnim(lastJudgment.judgment, elapsed) else null
  val judgmentSheet = ImageBitmap.imageResource(R.drawable.sm_judgment_normal)

  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    if (lastJudgment != null && anim != null) {
      val row = lastJudgment.judgment.ordinal.coerceIn(0, 5)
      val col = if (lastJudgment.timingErrorMs < 0L) 0 else 1
      val frameW = judgmentSheet.width / 2
      val frameH = judgmentSheet.height / 6

      Canvas(
        modifier = Modifier
          .width(316.dp)
          .height(86.dp)
          .graphicsLayer {
            scaleX = anim.zoomX
            scaleY = anim.zoomY
            translationX = anim.offsetXDp.dp.toPx()
            translationY = anim.offsetYDp.dp.toPx()
            this.alpha = anim.alpha.coerceIn(0f, 1f)
          }
          .testTag("gameplay_text_judgment")
      ) {
        val dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
        val srcOffset = IntOffset(col * frameW, row * frameH)
        val srcSize = IntSize(frameW, frameH)
        drawImage(
          image = judgmentSheet,
          srcOffset = srcOffset,
          srcSize = srcSize,
          dstOffset = IntOffset.Zero,
          dstSize = dstSize,
          filterQuality = FilterQuality.High
        )
        if (anim.glowAlpha > 0f) {
          drawImage(
            image = judgmentSheet,
            srcOffset = srcOffset,
            srcSize = srcSize,
            dstOffset = IntOffset.Zero,
            dstSize = dstSize,
            alpha = anim.glowAlpha,
            colorFilter = ColorFilter.tint(Color.White, BlendMode.SrcAtop),
            blendMode = BlendMode.Plus,
            filterQuality = FilterQuality.High
          )
        }
      }

      if (lastJudgment.judgment != Judgment.MISS) {
        val err = lastJudgment.timingErrorMs
        val offsetLabel = when {
          abs(err) <= 10L -> "ON TIME  ${err}ms"
          err < 0L -> "EARLY  ${err}ms"
          else -> "LATE  +${err}ms"
        }
        Text(
          text = offsetLabel,
          style = NumberStyle.copy(
            fontSize = 15.sp,
            color = if (err < 0L) Color(0xFF8FC8FF) else Color(0xFFFFB27A)
          ).smShadow(2f),
          modifier = Modifier.graphicsLayer { this.alpha = anim.alpha.coerceIn(0f, 1f) }
        )
      }
    }

    // Combo: StepMania shows it from 4 combo onward
    if (roundState.combo >= 4) {
      val noComboBreak = roundState.missCount == 0 &&
        roundState.w5Count == 0 &&
        roundState.w4Count == 0 &&
        roundState.letGoCount == 0
      val comboColor = when {
        noComboBreak && roundState.w2Count == 0 && roundState.w3Count == 0 -> Judgment.W1.color
        noComboBreak && roundState.w3Count == 0 -> Judgment.W2.color
        noComboBreak -> Judgment.W3.color
        else -> Color.White
      }
      Spacer(modifier = Modifier.height(8.dp))
      Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.testTag("gameplay_text_combo")
      ) {
        Text(
          text = "${roundState.combo}",
          style = JudgmentTextStyle.copy(
            fontSize = 56.sp,
            color = comboColor
          ).smShadow(4f, Color(0xE6000000))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "COMBO",
          style = JudgmentTextStyle.copy(
            fontSize = 22.sp,
            color = comboColor
          ).smShadow(2f, Color(0xE6000000)),
          modifier = Modifier.padding(bottom = 10.dp)
        )
      }
    }
  }
}

// ---------------------------------------------------------------------------
// Right zone
// ---------------------------------------------------------------------------

@Composable
private fun ScoreFrame(roundState: GameplayRoundState) {
  val shape = ChamferShape(22.dp)
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(shape)
      .background(Brush.verticalGradient(listOf(Color(0xFF3A3936), SmCharcoalLight, SmCharcoal)))
      .border(1.5.dp, Color(0x55FFFFFF), shape)
      .padding(horizontal = 28.dp, vertical = 12.dp)
      .testTag("gameplay_hud_score_panel"),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(
        modifier = Modifier
          .size(10.dp)
          .clip(ChamferShape(3.dp))
          .background(SmP1Blue)
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = "P1 SCORE",
        style = TextStyle(
          fontFamily = OpenSans,
          fontWeight = FontWeight.Bold,
          fontSize = 12.sp,
          letterSpacing = 1.5.sp,
          color = SmTextMuted
        )
      )
    }
    Text(
      text = dimLeadingZeros(roundState.score, 7),
      style = NumberStyle.copy(fontSize = 42.sp, color = Color.White).smShadow(2f),
      modifier = Modifier.testTag("gameplay_text_score")
    )
    Text(
      text = String.format(Locale.US, "%.2f%%", roundState.percentDp),
      style = NumberStyle.copy(fontSize = 22.sp, color = SmMaxCombo).smShadow(1.5f)
    )
  }
}

private fun dimLeadingZeros(value: Int, digits: Int) = buildAnnotatedString {
  val text = String.format(Locale.US, "%0${digits}d", value)
  val firstSignificant = text.indexOfFirst { it != '0' }.let { if (it < 0) text.length - 1 else it }
  withStyle(SpanStyle(color = Color.White.copy(alpha = 0.25f))) { append(text.substring(0, firstSignificant)) }
  append(text.substring(firstSignificant))
}

@Composable
private fun JudgmentLine(label: String, windowText: String?, count: Int, color: Color) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = label,
      style = JudgmentTextStyle.copy(fontSize = 17.sp, color = color).smShadow(1.5f)
    )
    if (windowText != null) {
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = windowText,
        style = NumberStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Normal, color = SmTextMuted)
      )
    }
    Spacer(modifier = Modifier.weight(1f))
    Text(
      text = dimLeadingZeros(count, 4),
      style = NumberStyle.copy(fontSize = 20.sp, color = Color.White)
    )
  }
}

// ---------------------------------------------------------------------------
// Overlays
// ---------------------------------------------------------------------------

@Composable
private fun PauseOverlay(
  roundState: GameplayRoundState,
  cursor: Int,
  onResume: () -> Unit,
  onRestartRound: () -> Unit,
  onNavigateBack: () -> Unit
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xCC000000))
      .testTag("pause_overlay"),
    contentAlignment = Alignment.Center
  ) {
    SmPanel(
      modifier = Modifier.width(460.dp),
      title = "PAUSED",
      contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp)
    ) {
      Text(
        text = String.format(Locale.US, "Song position %.2fs  •  Pad: ↑ ↓ choose, START select, SELECT continue", roundState.currentGameTimeMs / 1000.0),
        style = TextStyle(
          fontFamily = OpenSans,
          fontWeight = FontWeight.SemiBold,
          fontSize = 14.sp,
          color = SmTextSecondary,
          textAlign = TextAlign.Center
        ),
        modifier = Modifier.fillMaxWidth()
      )
      Spacer(modifier = Modifier.height(22.dp))
      SmButton(
        text = "CONTINUE",
        icon = Icons.Default.PlayArrow,
        style = SmButtonStyle.Gold,
        focused = cursor == 0,
        onClick = onResume,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("pause_button_resume")
      )
      Spacer(modifier = Modifier.height(12.dp))
      SmButton(
        text = "RESTART SONG",
        icon = Icons.Default.Replay,
        style = SmButtonStyle.Plum,
        focused = cursor == 1,
        onClick = onRestartRound,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("pause_button_restart")
      )
      Spacer(modifier = Modifier.height(12.dp))
      SmButton(
        text = "FORFEIT",
        icon = Icons.Default.Home,
        style = SmButtonStyle.Dark,
        focused = cursor == 2,
        onClick = onNavigateBack,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("pause_button_quit")
      )
    }
  }
}

/** Maps a StepMania 1..17 grade tier to its bundled `sm_grade_tierNN` drawable resource. */
fun gradeDrawableForTier(tier: Int): Int = when (tier.coerceIn(1, 17)) {
  1 -> R.drawable.sm_grade_tier01
  2 -> R.drawable.sm_grade_tier02
  3 -> R.drawable.sm_grade_tier03
  4 -> R.drawable.sm_grade_tier04
  5 -> R.drawable.sm_grade_tier05
  6 -> R.drawable.sm_grade_tier06
  7 -> R.drawable.sm_grade_tier07
  8 -> R.drawable.sm_grade_tier08
  9 -> R.drawable.sm_grade_tier09
  10 -> R.drawable.sm_grade_tier10
  11 -> R.drawable.sm_grade_tier11
  12 -> R.drawable.sm_grade_tier12
  13 -> R.drawable.sm_grade_tier13
  14 -> R.drawable.sm_grade_tier14
  15 -> R.drawable.sm_grade_tier15
  16 -> R.drawable.sm_grade_tier16
  else -> R.drawable.sm_grade_tier17
}

/** StepMania 5 default grade tiers (Themes/default/metrics.ini GradePercentTierNN). */
private fun gradeDrawable(roundState: GameplayRoundState): Int = gradeDrawableForTier(roundState.gradeTier)

@Composable
private fun EvaluationOverlay(
  roundState: GameplayRoundState,
  cursor: Int,
  onRestartRound: () -> Unit,
  onNavigateBack: () -> Unit
) {
  val intro = remember { Animatable(0f) }
  LaunchedEffect(Unit) { intro.animateTo(1f, tween(500, easing = LinearEasing)) }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xD9000000))
      .testTag("round_complete_overlay"),
    contentAlignment = Alignment.Center
  ) {
    SmPanel(
      modifier = Modifier
        .width(720.dp)
        .graphicsLayer {
          alpha = intro.value
          translationY = (1f - intro.value) * 40.dp.toPx()
        },
      title = "RESULTS  •  ${roundState.meta.title}  •  ${roundState.meta.difficultyName} ${roundState.meta.meter}",
      contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp)
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        // Grade + percentage
        Column(
          modifier = Modifier.width(260.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Image(
            painter = painterResource(gradeDrawable(roundState)),
            contentDescription = "Grade",
            modifier = Modifier
              .width(240.dp)
              .height(139.dp)
              .graphicsLayer {
                val s = 1.6f - 0.6f * intro.value
                scaleX = s
                scaleY = s
              }
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = String.format(Locale.US, "%.2f%%", roundState.percentDp),
            style = NumberStyle.copy(fontSize = 34.sp, color = Color.White).smShadow(2f)
          )
          roundState.fullComboKind?.let { fc ->
            val (fcText, fcColor) = when (fc) {
              FullComboKind.FLAWLESS -> "FLAWLESS FULL COMBO" to Judgment.W1.color
              FullComboKind.PERFECT -> "PERFECT FULL COMBO" to Judgment.W2.color
              FullComboKind.GREAT -> "GREAT FULL COMBO" to Judgment.W3.color
            }
            SmChip(
              text = fcText,
              color = fcColor,
              textColor = SmCharcoal
            )
          }
        }

        Spacer(modifier = Modifier.width(24.dp))

        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "SCORE",
            style = TextStyle(
              fontFamily = OpenSans,
              fontWeight = FontWeight.Bold,
              fontSize = 12.sp,
              letterSpacing = 1.5.sp,
              color = SmTextMuted
            )
          )
          Text(
            text = dimLeadingZeros(roundState.score, 7),
            style = NumberStyle.copy(fontSize = 44.sp, color = Color.White).smShadow(2f),
            modifier = Modifier.testTag("summary_text_final_score")
          )
          Spacer(modifier = Modifier.height(8.dp))
          JudgmentLine("FLAWLESS", null, roundState.w1Count, Judgment.W1.color)
          JudgmentLine("PERFECT", null, roundState.w2Count, Judgment.W2.color)
          JudgmentLine("GREAT", null, roundState.w3Count, Judgment.W3.color)
          JudgmentLine("GOOD", null, roundState.w4Count, Judgment.W4.color)
          JudgmentLine("BAD", null, roundState.w5Count, Judgment.W5.color)
          JudgmentLine("MISS", null, roundState.missCount, Judgment.MISS.color)
          if (roundState.totalHolds > 0) {
            JudgmentLine("OK", "holds", roundState.heldCount, HoldOkColor)
            JudgmentLine("NG", "let go", roundState.letGoCount, Judgment.MISS.color)
          }
          JudgmentLine("MAX COMBO", null, roundState.maxCombo, SmMaxCombo)
        }
      }

      Spacer(modifier = Modifier.height(22.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        SmButton(
          text = "PLAY AGAIN",
          icon = Icons.Default.Replay,
          style = SmButtonStyle.Gold,
          focused = cursor == 0,
          onClick = onRestartRound,
          modifier = Modifier
            .weight(1f)
            .testTag("summary_button_play_again")
        )
        SmButton(
          text = "SELECT MUSIC",
          icon = Icons.Default.Home,
          style = SmButtonStyle.Dark,
          focused = cursor == 1,
          onClick = onNavigateBack,
          modifier = Modifier
            .weight(1f)
            .testTag("summary_button_home")
        )
      }
    }
  }
}
