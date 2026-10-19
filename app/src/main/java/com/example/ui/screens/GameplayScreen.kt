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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.gameplay.GameplayRoundState
import com.example.core.model.ControllerInfo
import com.example.core.model.Judgment
import com.example.core.state.GameInputState
import com.example.ui.components.DebugOverlayView
import com.example.ui.components.PlayfieldView
import java.util.Locale
import kotlin.math.abs

/**
 * 4-lane rhythm gameplay screen for RhythmPad7 v0.2.0.
 * Features real upward-scrolling practice notes synchronized to a monotonic frame clock,
 * physical dance-mat timing judgments (EXCELLENT, GOOD, MISS), live score & combo HUD,
 * pause/resume/restart controls, and a round-complete summary modal.
 * Touchscreen arrow input has been removed so only the physical USB dance mat triggers arrows.
 */
@Composable
fun GameplayScreen(
  inputState: GameInputState,
  roundState: GameplayRoundState,
  controllerInfo: ControllerInfo,
  isDebugOverlayVisible: Boolean,
  isPaused: Boolean,
  onAdvanceRoundBy: (Long) -> Unit,
  onToggleDebug: () -> Unit,
  onTogglePause: () -> Unit,
  onResume: () -> Unit,
  onRestartRound: () -> Unit,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  BackHandler(onBack = onNavigateBack)

  // Real-time monotonic frame loop driving note scrolling and auto-miss evaluation
  LaunchedEffect(roundState.isRunning, roundState.isPaused, roundState.isCompleted) {
    if (roundState.isRunning && !roundState.isPaused && !roundState.isCompleted) {
      var lastFrameNanos = -1L
      while (true) {
        withFrameNanos { frameNanos ->
          if (lastFrameNanos >= 0L) {
            val deltaMs = ((frameNanos - lastFrameNanos) / 1_000_000L).coerceIn(0L, 100L)
            if (deltaMs > 0L) {
              onAdvanceRoundBy(deltaMs)
            }
          }
          lastFrameNanos = frameNanos
        }
      }
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color(0xFF04070D))
  ) {
    // 1. Core 4-Lane Canvas Playfield with Scrolling Notes & Fixed Receptors
    PlayfieldView(
      inputState = inputState,
      roundState = roundState,
      modifier = Modifier.fillMaxSize()
    )

    // 2. Live Stage HUD Overlay (Song Info, Score, Combo, Judgment, Countdown)
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
      // Left Zone: Practice Pattern Info & Progress
      Column(
        modifier = Modifier
          .align(Alignment.TopStart)
          .padding(top = 64.dp)
          .width(260.dp)
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xCC0F172A))
            .border(1.dp, Color(0x40334155), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
          Column {
            Text(
              text = "STAGE 1 • PRACTICE CHART",
              style = MaterialTheme.typography.labelSmall.copy(
                color = Color(0xFF38BDF8),
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
              )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = "StepMania Pad Foundation #1",
              style = MaterialTheme.typography.titleSmall.copy(
                color = Color(0xFFF1F5F9),
                fontWeight = FontWeight.Bold
              )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = "${roundState.bpm} BPM • ${roundState.judgedNotesCount}/${roundState.totalNotes} NOTES",
              style = MaterialTheme.typography.bodySmall.copy(
                color = Color(0xFF94A3B8),
                fontFamily = FontFamily.Monospace
              )
            )
            Spacer(modifier = Modifier.height(8.dp))
            val progress = if (roundState.totalNotes > 0) {
              roundState.judgedNotesCount.toFloat() / roundState.totalNotes.toFloat()
            } else 0f
            LinearProgressIndicator(
              progress = { progress.coerceIn(0f, 1f) },
              modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
              color = Color(0xFF00E5FF),
              trackColor = Color(0xFF1E293B)
            )
          }
        }
      }

      // Right Zone: Live Score, Accuracy, Max Combo & Judgment Breakdown
      Column(
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(top = 64.dp)
          .width(260.dp),
        horizontalAlignment = Alignment.End
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xCC0F172A))
            .border(1.dp, Color(0x40334155), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("gameplay_hud_score_panel")
        ) {
          Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth()) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "SCORE",
                style = MaterialTheme.typography.labelSmall.copy(
                  color = Color(0xFF64748B),
                  fontWeight = FontWeight.Black,
                  letterSpacing = 1.sp
                )
              )
              Text(
                text = "${roundState.accuracyPercent}% • MAX ${roundState.maxCombo}x",
                style = MaterialTheme.typography.labelSmall.copy(
                  color = Color(0xFF94A3B8),
                  fontFamily = FontFamily.Monospace,
                  fontWeight = FontWeight.Bold
                )
              )
            }

            Text(
              text = String.format(Locale.US, "%07d", roundState.score),
              style = MaterialTheme.typography.headlineSmall.copy(
                color = Color(0xFF00E5FF),
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
              ),
              modifier = Modifier.testTag("gameplay_text_score")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Judgment breakdown pills
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              JudgmentCountChip(
                label = "EXC",
                count = roundState.excellentCount,
                color = Judgment.EXCELLENT.color
              )
              JudgmentCountChip(
                label = "GOOD",
                count = roundState.goodCount,
                color = Judgment.GOOD.color
              )
              JudgmentCountChip(
                label = "MISS",
                count = roundState.missCount,
                color = Judgment.MISS.color
              )
            }
          }
        }
      }

      // Center Playfield Zone: Countdown, Judgment Banner & Live Combo Counter
      Column(
        modifier = Modifier
          .align(Alignment.Center)
          .padding(bottom = 40.dp),
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

      // Bottom Non-Blocking USB Dance Mat Connection Banner (when unplugged)
      if (!controllerInfo.isConnected) {
        Box(
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xDD1E1B4B))
            .border(1.dp, Color(0xFF6366F1), RoundedCornerShape(12.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .testTag("gameplay_banner_connect_usb")
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Icon(
              imageVector = Icons.Default.UsbOff,
              contentDescription = "USB Disconnected",
              tint = Color(0xFFA5B4FC),
              modifier = Modifier.size(20.dp)
            )
            Text(
              text = "Connect Physical USB Dance Mat (Microntek 0079:0006) via OTG to hit notes",
              style = MaterialTheme.typography.bodySmall.copy(
                color = Color(0xFFE0E7FF),
                fontWeight = FontWeight.SemiBold
              )
            )
          }
        }
      }
    }

    // 3. Top Navigation & Tool Bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .align(Alignment.TopCenter)
        .background(Color(0xBB0A0F1D))
        .padding(horizontal = 20.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Left: Back button & Title
      Row(verticalAlignment = Alignment.CenterVertically) {
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

        Spacer(modifier = Modifier.width(12.dp))

        Column {
          Text(
            text = "RhythmPad7 • v0.2.0",
            style = MaterialTheme.typography.titleMedium.copy(
              fontWeight = FontWeight.Black,
              color = Color(0xFFF1F5F9)
            )
          )
          Text(
            text = if (controllerInfo.isConnected) {
              "USB Dance Mat: ${controllerInfo.name}"
            } else {
              "Awaiting Physical USB Dance Mat (0079:0006)"
            },
            style = MaterialTheme.typography.bodySmall.copy(
              color = if (controllerInfo.isConnected) Color(0xFF34D399) else Color(0xFFFBBF24)
            )
          )
        }
      }

      // Right: Tool Controls (Restart Round, Toggle Debug HUD, Pause)
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
    }

    // 4. Floating Debug Telemetry HUD (When Toggled)
    AnimatedVisibility(
      visible = isDebugOverlayVisible,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier
        .align(Alignment.CenterStart)
        .padding(start = 20.dp, top = 60.dp, bottom = 40.dp)
    ) {
      DebugOverlayView(
        inputState = inputState,
        roundState = roundState,
        controllerInfo = controllerInfo
      )
    }

    // 5. Pause Dialog / Overlay
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
              text = "Chart clock frozen at ${roundState.currentGameTimeMs} ms. Dance-mat inputs are paused.",
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

    // 6. Round Complete Summary Modal
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

            // Breakdown row
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
private fun JudgmentCountChip(
  label: String,
  count: Int,
  color: Color
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    modifier = Modifier
      .background(Color(0xFF1E293B), RoundedCornerShape(6.dp))
      .padding(horizontal = 8.dp, vertical = 4.dp)
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall.copy(
        color = color,
        fontWeight = FontWeight.Black,
        fontSize = 10.sp
      )
    )
    Text(
      text = "$count",
      style = MaterialTheme.typography.labelSmall.copy(
        color = Color.White,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold
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
