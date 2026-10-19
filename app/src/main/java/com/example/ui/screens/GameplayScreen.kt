package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.gameplay.GameplayRoundState
import com.example.core.model.ControllerInfo
import com.example.core.model.GameLane
import com.example.core.model.Judgment
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

/** Roboto bold italic used for judgments and combo ("Combo Numbers" / Judgment Normal). */
private val JudgmentTextStyle = TextStyle(
  fontFamily = FontFamily.Default,
  fontWeight = FontWeight.Black,
  fontStyle = FontStyle.Italic
)

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
  modifier: Modifier = Modifier
) {
  BackHandler(onBack = onNavigateBack)

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

    SmBackground(animated = false, dim = 0.62f)

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
          SmChip(text = "PRACTICE STAGE", color = SmStageOrange)
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
          JudgmentLine("EXCELLENT", "±70ms", roundState.excellentCount, Judgment.EXCELLENT.color)
          JudgmentLine("GOOD", "±150ms", roundState.goodCount, Judgment.GOOD.color)
          JudgmentLine("MISS", ">150ms", roundState.missCount, Judgment.MISS.color)
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
        onResume = onResume,
        onRestartRound = onRestartRound,
        onNavigateBack = onNavigateBack
      )
    }

    if (roundState.isCompleted) {
      EvaluationOverlay(
        roundState = roundState,
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
      text = "StepMania Pad Foundation #1",
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
      text = "RhythmPad7 Practice",
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
          text = "${roundState.bpm}",
          style = NumberStyle.copy(fontSize = 30.sp, color = Color.White)
        )
      }
      Spacer(modifier = Modifier.weight(1f))
      // Difficulty plate (dance-single / Beginner)
      Row(
        modifier = Modifier
          .clip(ChamferShape(10.dp, pointLeft = false))
          .background(SmBeginnerMint)
          .padding(start = 12.dp, end = 18.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "BEGINNER",
          style = TextStyle(
            fontFamily = OpenSans,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 12.sp,
            color = SmCharcoal
          )
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
          text = "1",
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

@Composable
private fun JudgmentAndCombo(roundState: GameplayRoundState, modifier: Modifier = Modifier) {
  val lastJudgment = roundState.lastJudgment
  val elapsed = if (lastJudgment != null) roundState.currentGameTimeMs - lastJudgment.triggeredAtGameTimeMs else Long.MAX_VALUE
  val showJudgment = lastJudgment != null && elapsed in 0L..950L

  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    if (showJudgment && lastJudgment != null) {
      // Judgment Normal: zoom 1.3 → 1 over ~100 ms, hold, then fade
      val t = elapsed.toFloat()
      val zoom = if (t < 100f) 1.3f - 0.3f * (t / 100f) else 1f
      val alpha = if (t > 750f) 1f - (t - 750f) / 200f else 1f
      val label = when (lastJudgment.judgment) {
        Judgment.EXCELLENT -> "EXCELLENT!!"
        Judgment.GOOD -> "GOOD"
        Judgment.MISS -> "MISS..."
      }
      Text(
        text = label,
        style = JudgmentTextStyle.copy(
          fontSize = 52.sp,
          letterSpacing = 1.sp,
          brush = Brush.verticalGradient(
            listOf(Color.White, lastJudgment.judgment.color, lastJudgment.judgment.color)
          )
        ).smShadow(4f, Color(0xE6000000)),
        modifier = Modifier
          .graphicsLayer {
            scaleX = zoom
            scaleY = zoom
            this.alpha = alpha.coerceIn(0f, 1f)
          }
          .testTag("gameplay_text_judgment")
      )

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
          modifier = Modifier.graphicsLayer { this.alpha = alpha.coerceIn(0f, 1f) }
        )
      }
    }

    // Combo: StepMania shows it from 4 combo onward
    if (roundState.combo >= 4) {
      val fullCombo = roundState.missCount == 0
      val comboColor = when {
        fullCombo && roundState.goodCount == 0 -> Judgment.EXCELLENT.color
        fullCombo -> SmMaxCombo
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
      text = String.format(Locale.US, "%.1f%%", roundState.accuracyPercent),
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
        text = String.format(Locale.US, "Song position %.2fs — dance-mat input is paused.", roundState.currentGameTimeMs / 1000.0),
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
        onClick = onRestartRound,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("pause_button_restart")
      )
      Spacer(modifier = Modifier.height(12.dp))
      SmButton(
        text = "FORFEIT TO TITLE",
        icon = Icons.Default.Home,
        style = SmButtonStyle.Dark,
        onClick = onNavigateBack,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("pause_button_quit")
      )
    }
  }
}

/** StepMania 5 default grade tiers (Themes/default/metrics.ini GradePercentTierNN). */
private fun gradeDrawable(roundState: GameplayRoundState): Int {
  if (roundState.judgedNotesCount > 0 && roundState.excellentCount == roundState.judgedNotesCount) {
    return R.drawable.sm_grade_tier01
  }
  val pct = roundState.accuracyPercent / 100f
  val tiers = listOf(
    0.98f to R.drawable.sm_grade_tier03,
    0.96f to R.drawable.sm_grade_tier04,
    0.94f to R.drawable.sm_grade_tier05,
    0.92f to R.drawable.sm_grade_tier06,
    0.89f to R.drawable.sm_grade_tier07,
    0.86f to R.drawable.sm_grade_tier08,
    0.83f to R.drawable.sm_grade_tier09,
    0.80f to R.drawable.sm_grade_tier10,
    0.76f to R.drawable.sm_grade_tier11,
    0.72f to R.drawable.sm_grade_tier12,
    0.68f to R.drawable.sm_grade_tier13,
    0.64f to R.drawable.sm_grade_tier14,
    0.60f to R.drawable.sm_grade_tier15,
    0.55f to R.drawable.sm_grade_tier16
  )
  return tiers.firstOrNull { pct >= it.first }?.second ?: R.drawable.sm_grade_tier17
}

@Composable
private fun EvaluationOverlay(
  roundState: GameplayRoundState,
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
      title = "RESULTS  •  StepMania Pad Foundation #1",
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
            text = String.format(Locale.US, "%.2f%%", roundState.accuracyPercent),
            style = NumberStyle.copy(fontSize = 34.sp, color = Color.White).smShadow(2f)
          )
          if (roundState.missCount == 0 && roundState.judgedNotesCount > 0) {
            SmChip(
              text = if (roundState.goodCount == 0) "FULL EXCELLENT COMBO" else "FULL COMBO",
              color = if (roundState.goodCount == 0) Color(0xFF3A8FB7) else SmEasyGreen
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
          JudgmentLine("EXCELLENT", null, roundState.excellentCount, Judgment.EXCELLENT.color)
          JudgmentLine("GOOD", null, roundState.goodCount, Judgment.GOOD.color)
          JudgmentLine("MISS", null, roundState.missCount, Judgment.MISS.color)
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
          onClick = onRestartRound,
          modifier = Modifier
            .weight(1f)
            .testTag("summary_button_play_again")
        )
        SmButton(
          text = "TITLE MENU",
          icon = Icons.Default.Home,
          style = SmButtonStyle.Dark,
          onClick = onNavigateBack,
          modifier = Modifier
            .weight(1f)
            .testTag("summary_button_home")
        )
      }
    }
  }
}
