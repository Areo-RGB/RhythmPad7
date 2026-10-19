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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.UsbOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.gameplay.GameplayRoundState
import com.example.core.model.ControllerInfo
import com.example.core.model.GameLane
import com.example.core.model.Judgment
import com.example.core.state.GameInputState
import com.example.ui.components.DebugOverlayView
import com.example.ui.components.PlayfieldGeometry
import com.example.ui.components.PlayfieldView
import java.util.Locale
import kotlin.math.abs

/**
 * Three-zone landscape rhythm gameplay screen for RhythmPad7 v0.2.1.
 *
 * - Phase 1: Uses monotonic [com.example.core.gameplay.GameplayClock] synchronization
 *   on each frame rather than integer millisecond delta accumulation.
 * - Phase 3: Keeps a single centered 560–640 dp note highway with four equal-width lanes
 *   and unobstructed receptors.
 * - Phase 4: Separates the UI into three dedicated landscape columns:
 *   - Left Zone: Back button, practice name, BPM, progress bar, round status, and USB mat status.
 *   - Center Zone: Unobstructed 4-lane note highway, receptors, scrolling notes, countdown,
 *     judgment banner, and combo counter.
 *   - Right Zone: Touch controls (Restart, Debug HUD, Pause), live score, accuracy, max combo,
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
      .background(Color(0xFF080D1A))
  ) {
    val highwayWidthDp = PlayfieldGeometry.calculateHighwayWidthDp(maxWidth.value).dp

    // Three-Zone Landscape Layout: [ Left HUD Wing | Centered 4-Lane Highway | Right Score & Controls Wing ]
    Row(
      modifier = Modifier
        .fillMaxSize()
        .testTag("gameplay_three_zone_row"),
      verticalAlignment = Alignment.Top
    ) {
      // =========================================================================
      // LEFT ZONE: Navigation, Practice Chart Info, Progress, Status & Mat Telemetry
      // =========================================================================
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .padding(horizontal = 14.dp, vertical = 14.dp)
          .verticalScroll(rememberScrollState())
          .testTag("gameplay_zone_left"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        // 1. Top-Left Header: Back Button + App Version Badge
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          FilledTonalIconButton(
            onClick = onNavigateBack,
            modifier = Modifier
              .size(48.dp)
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

          Column {
            Text(
              text = "RhythmPad7",
              style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Black,
                color = Color(0xFFF8FAFC)
              ),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = "v0.2.1 • XIAOMI PAD 7",
              style = MaterialTheme.typography.labelSmall.copy(
                color = Color(0xFF38BDF8),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
              )
            )
          }
        }

        // 2. Practice Chart Info & Progress Card
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0F172A))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(14.dp))
            .padding(14.dp)
            .testTag("gameplay_hud_left_panel")
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "PRACTICE PATTERN",
              style = MaterialTheme.typography.labelSmall.copy(
                color = Color(0xFF38BDF8),
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
              )
            )
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF1E293B))
                .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
              Text(
                text = roundState.phase.name,
                style = MaterialTheme.typography.labelSmall.copy(
                  color = Color(0xFFE2E8F0),
                  fontFamily = FontFamily.Monospace,
                  fontWeight = FontWeight.Bold,
                  fontSize = 10.sp
                )
              )
            }
          }

          Spacer(modifier = Modifier.height(6.dp))

          Text(
            text = "StepMania Pad Foundation #1",
            style = MaterialTheme.typography.titleSmall.copy(
              color = Color(0xFFF1F5F9),
              fontWeight = FontWeight.Bold
            )
          )

          Spacer(modifier = Modifier.height(4.dp))

          Text(
            text = "${roundState.bpm} BPM  •  ${roundState.judgedNotesCount} / ${roundState.totalNotes} NOTES",
            style = MaterialTheme.typography.bodySmall.copy(
              color = Color(0xFF94A3B8),
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.SemiBold
            )
          )

          Spacer(modifier = Modifier.height(10.dp))

          val progress = if (roundState.totalNotes > 0) {
            roundState.judgedNotesCount.toFloat() / roundState.totalNotes.toFloat()
          } else 0f

          LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
              .fillMaxWidth()
              .height(7.dp)
              .clip(RoundedCornerShape(4.dp)),
            color = Color(0xFF00E5FF),
            trackColor = Color(0xFF1E293B)
          )

          Spacer(modifier = Modifier.height(8.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            val seconds = roundState.currentGameTimeMs / 1000.0
            Text(
              text = String.format(Locale.US, "TIME %.2fs", seconds),
              style = MaterialTheme.typography.labelSmall.copy(
                color = Color(0xFF64748B),
                fontFamily = FontFamily.Monospace
              )
            )
            Text(
              text = "${roundState.pendingNotesCount} LEFT",
              style = MaterialTheme.typography.labelSmall.copy(
                color = Color(0xFF64748B),
                fontFamily = FontFamily.Monospace
              )
            )
          }
        }

        // 3. Physical Controller Status & Secondary Info Card
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (controllerInfo.isConnected) Color(0xFF0F172A) else Color(0xFF1A1635))
            .border(
              1.dp,
              if (controllerInfo.isConnected) Color(0xFF1E293B) else Color(0xFF6366F1),
              RoundedCornerShape(14.dp)
            )
            .padding(14.dp)
            .let { mod ->
              if (!controllerInfo.isConnected) mod.testTag("gameplay_banner_connect_usb") else mod
            }
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = if (controllerInfo.isConnected) Icons.Default.CheckCircle else Icons.Default.UsbOff,
              contentDescription = if (controllerInfo.isConnected) "USB Connected" else "USB Disconnected",
              tint = if (controllerInfo.isConnected) Color(0xFF34D399) else Color(0xFFA5B4FC),
              modifier = Modifier.size(18.dp)
            )
            Text(
              text = if (controllerInfo.isConnected) "USB DANCE MAT ACTIVE" else "CONNECT USB DANCE MAT",
              style = MaterialTheme.typography.labelSmall.copy(
                color = if (controllerInfo.isConnected) Color(0xFF34D399) else Color(0xFFA5B4FC),
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp
              )
            )
          }

          Spacer(modifier = Modifier.height(4.dp))

          Text(
            text = if (controllerInfo.isConnected) {
              controllerInfo.formattedDetails
            } else {
              "Microntek USB Joystick (0079:0006) via OTG"
            },
            style = MaterialTheme.typography.bodySmall.copy(
              color = Color(0xFFCBD5E1),
              fontSize = 11.sp
            )
          )

          Spacer(modifier = Modifier.height(10.dp))

          // Secondary Info: Live 4-Panel Mat State Pills (L / D / U / R)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            GameLane.entries.forEach { lane ->
              val isPressed = inputState.isLanePressed(lane)
              Box(
                modifier = Modifier
                  .weight(1f)
                  .clip(RoundedCornerShape(8.dp))
                  .background(
                    if (isPressed) lane.defaultColor.copy(alpha = 0.28f) else Color(0xFF1E293B)
                  )
                  .border(
                    1.dp,
                    if (isPressed) lane.defaultColor else Color(0xFF334155),
                    RoundedCornerShape(8.dp)
                  )
                  .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
              ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  Text(
                    text = lane.displayName.take(1),
                    style = MaterialTheme.typography.labelSmall.copy(
                      color = if (isPressed) Color.White else lane.defaultColor,
                      fontWeight = FontWeight.Black
                    )
                  )
                  Text(
                    text = "${inputState.getPressCount(lane)}",
                    style = MaterialTheme.typography.labelSmall.copy(
                      color = Color(0xFF94A3B8),
                      fontFamily = FontFamily.Monospace,
                      fontSize = 10.sp
                    )
                  )
                }
              }
            }
          }
        }

        // 4. Optional Debug Telemetry Panel (Housed inside Left Zone so highway is never blocked)
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
      // CENTER ZONE: Unobstructed 4-Lane Note Highway, Receptors, Notes & Feedback
      // =========================================================================
      Box(
        modifier = Modifier
          .width(highwayWidthDp)
          .fillMaxHeight()
          .testTag("gameplay_zone_center"),
        contentAlignment = Alignment.Center
      ) {
        // 1. Centered 4-Lane Canvas Highway
        PlayfieldView(
          inputState = inputState,
          roundState = roundState,
          modifier = Modifier.fillMaxSize()
        )

        // 2. Center-Highway Countdown, Judgment Banner & Live Combo Counter
        Column(
          modifier = Modifier
            .align(Alignment.Center)
            .padding(top = 48.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          val countdown = roundState.countdownText
          if (countdown != null) {
            Text(
              text = countdown,
              style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 68.sp,
                fontWeight = FontWeight.Black,
                color = if (countdown == "GO!") Color(0xFF00E676) else Color(0xFF00E5FF),
                letterSpacing = 2.sp
              ),
              modifier = Modifier.testTag("gameplay_text_countdown")
            )
          }

          val lastJudgment = roundState.lastJudgment
          val showJudgment = lastJudgment != null &&
            (roundState.currentGameTimeMs - lastJudgment.triggeredAtGameTimeMs) <= 950L

          if (showJudgment && lastJudgment != null) {
            Text(
              text = lastJudgment.judgment.displayName,
              style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 38.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.5.sp,
                color = lastJudgment.judgment.color
              ),
              modifier = Modifier.testTag("gameplay_text_judgment")
            )

            if (lastJudgment.judgment != Judgment.MISS) {
              val err = lastJudgment.timingErrorMs
              val offsetLabel = when {
                abs(err) <= 10L -> "PERFECT (${err}ms)"
                err < 0L -> "EARLY (${err}ms)"
                else -> "LATE (+${err}ms)"
              }
              Text(
                text = offsetLabel,
                style = MaterialTheme.typography.labelMedium.copy(
                  fontFamily = FontFamily.Monospace,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFFE2E8F0)
                )
              )
            }
          }

          if (roundState.combo > 0) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "${roundState.combo} COMBO",
              style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
                color = Color(0xFFFFD700)
              ),
              modifier = Modifier.testTag("gameplay_text_combo")
            )
          }
        }
      }

      // =========================================================================
      // RIGHT ZONE: Touch Controls, Score, Accuracy, Max Combo & Judgment Breakdown
      // =========================================================================
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .padding(horizontal = 14.dp, vertical = 14.dp)
          .verticalScroll(rememberScrollState())
          .testTag("gameplay_zone_right"),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        // 1. Top-Right Touch Action Controls (Restart, Debug HUD, Pause)
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Restart Round Button
          FilledTonalIconButton(
            onClick = onRestartRound,
            modifier = Modifier
              .size(48.dp)
              .testTag("gameplay_button_restart"),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
              containerColor = Color(0xFF1E293B),
              contentColor = Color(0xFF38BDF8)
            )
          ) {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = "Restart Practice Round",
              modifier = Modifier.size(22.dp)
            )
          }

          // Debug Overlay Toggle Button
          FilledTonalIconButton(
            onClick = onToggleDebug,
            modifier = Modifier
              .size(48.dp)
              .testTag("gameplay_button_toggle_debug"),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
              containerColor = if (isDebugOverlayVisible) Color(0xFF0D9488) else Color(0xFF1E293B),
              contentColor = if (isDebugOverlayVisible) Color.White else Color(0xFF94A3B8)
            )
          ) {
            Icon(
              imageVector = Icons.Default.BugReport,
              contentDescription = "Toggle Debug HUD",
              modifier = Modifier.size(22.dp)
            )
          }

          // Pause Button
          FilledTonalIconButton(
            onClick = onTogglePause,
            modifier = Modifier
              .size(48.dp)
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

        // 2. Score, Accuracy & Max Combo Panel
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0F172A))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(14.dp))
            .padding(14.dp)
            .testTag("gameplay_hud_score_panel"),
          horizontalAlignment = Alignment.End
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "SCORE",
              style = MaterialTheme.typography.labelSmall.copy(
                color = Color(0xFF38BDF8),
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
              )
            )
            Text(
              text = "ACCURACY ${roundState.accuracyPercent}%",
              style = MaterialTheme.typography.labelSmall.copy(
                color = Color(0xFFE2E8F0),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
              )
            )
          }

          Spacer(modifier = Modifier.height(4.dp))

          Text(
            text = String.format(Locale.US, "%07d", roundState.score),
            style = MaterialTheme.typography.headlineMedium.copy(
              color = Color(0xFF00E5FF),
              fontWeight = FontWeight.Black,
              fontFamily = FontFamily.Monospace
            ),
            modifier = Modifier.testTag("gameplay_text_score")
          )

          Spacer(modifier = Modifier.height(8.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "COMBO ${roundState.combo}x",
              style = MaterialTheme.typography.bodySmall.copy(
                color = Color(0xFFFFD700),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
              )
            )
            Text(
              text = "MAX ${roundState.maxCombo}x",
              style = MaterialTheme.typography.bodySmall.copy(
                color = Color(0xFF94A3B8),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
              )
            )
          }
        }

        // 3. Judgment Breakdown Card (EXCELLENT / GOOD / MISS)
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0F172A))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(14.dp))
            .padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(
            text = "JUDGMENT BREAKDOWN",
            style = MaterialTheme.typography.labelSmall.copy(
              color = Color(0xFF94A3B8),
              fontWeight = FontWeight.Black,
              letterSpacing = 0.8.sp
            )
          )

          JudgmentBreakdownRow(
            label = "EXCELLENT",
            windowText = "±70ms",
            count = roundState.excellentCount,
            color = Judgment.EXCELLENT.color
          )
          JudgmentBreakdownRow(
            label = "GOOD",
            windowText = "±150ms",
            count = roundState.goodCount,
            color = Judgment.GOOD.color
          )
          JudgmentBreakdownRow(
            label = "MISS",
            windowText = ">150ms",
            count = roundState.missCount,
            color = Judgment.MISS.color
          )
        }
      }
    }

    // =========================================================================
    // MODAL OVERLAYS: Pause Dialog & Round Complete Summary
    // =========================================================================
    if (isPaused && !roundState.isCompleted) {
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
          border = BorderStroke(1.dp, Color(0xFF334155)),
          modifier = Modifier.width(440.dp)
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
              text = "GameplayClock frozen at ${roundState.currentGameTimeMs} ms. Dance-mat inputs are paused.",
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

            OutlinedButton(
              onClick = onRestartRound,
              modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("pause_button_restart"),
              shape = RoundedCornerShape(12.dp),
              border = BorderStroke(1.dp, Color(0xFF38BDF8)),
              colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color(0xFF38BDF8)
              )
            ) {
              Icon(imageVector = Icons.Default.Replay, contentDescription = "Restart")
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "RESTART ROUND",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
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

    if (roundState.isCompleted) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color(0xE004070D))
          .testTag("round_complete_overlay"),
        contentAlignment = Alignment.Center
      ) {
        Surface(
          shape = RoundedCornerShape(24.dp),
          color = Color(0xFF0F172A),
          border = BorderStroke(1.5.dp, Color(0xFF00E5FF)),
          modifier = Modifier.width(500.dp)
        ) {
          Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Text(
              text = "PRACTICE ROUND COMPLETE",
              style = MaterialTheme.typography.labelLarge.copy(
                color = Color(0xFF00E5FF),
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp
              )
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = String.format(Locale.US, "%07d", roundState.score),
              style = MaterialTheme.typography.displayMedium.copy(
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
              ),
              modifier = Modifier.testTag("summary_text_final_score")
            )
            Text(
              text = "ACCURACY ${roundState.accuracyPercent}%  •  MAX COMBO ${roundState.maxCombo}x",
              style = MaterialTheme.typography.titleSmall.copy(
                color = Color(0xFFFFD700),
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
              )
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
              modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E293B), RoundedCornerShape(14.dp))
                .padding(vertical = 14.dp, horizontal = 20.dp),
              horizontalArrangement = Arrangement.SpaceEvenly
            ) {
              SummaryMetricColumn(
                label = "EXCELLENT",
                count = roundState.excellentCount,
                color = Judgment.EXCELLENT.color
              )
              SummaryMetricColumn(
                label = "GOOD",
                count = roundState.goodCount,
                color = Judgment.GOOD.color
              )
              SummaryMetricColumn(
                label = "MISS",
                count = roundState.missCount,
                color = Judgment.MISS.color
              )
            }

            Spacer(modifier = Modifier.height(26.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Button(
                onClick = onRestartRound,
                modifier = Modifier
                  .weight(1f)
                  .height(52.dp)
                  .testTag("summary_button_play_again"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                  containerColor = Color(0xFF00E5FF),
                  contentColor = Color(0xFF04060A)
                )
              ) {
                Icon(imageVector = Icons.Default.Replay, contentDescription = "Play Again")
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "PLAY AGAIN",
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black)
                )
              }

              OutlinedButton(
                onClick = onNavigateBack,
                modifier = Modifier
                  .weight(1f)
                  .height(52.dp)
                  .testTag("summary_button_home"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF475569)),
                colors = ButtonDefaults.outlinedButtonColors(
                  contentColor = Color(0xFFF1F5F9)
                )
              ) {
                Text(
                  text = "HOME",
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun JudgmentBreakdownRow(
  label: String,
  windowText: String,
  count: Int,
  color: Color
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
      .padding(horizontal = 10.dp, vertical = 7.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Box(
        modifier = Modifier
          .size(8.dp)
          .clip(CircleShape)
          .background(color)
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(
          color = color,
          fontWeight = FontWeight.Black
        )
      )
      Text(
        text = windowText,
        style = MaterialTheme.typography.labelSmall.copy(
          color = Color(0xFF64748B),
          fontFamily = FontFamily.Monospace,
          fontSize = 10.sp
        )
      )
    }
    Text(
      text = "$count",
      style = MaterialTheme.typography.bodyMedium.copy(
        color = Color.White,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Black
      )
    )
  }
}

@Composable
private fun SummaryMetricColumn(
  label: String,
  count: Int,
  color: Color
) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall.copy(
        color = color,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.sp
      )
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
      text = "$count",
      style = MaterialTheme.typography.headlineSmall.copy(
        color = Color.White,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Black
      )
    )
  }
}
