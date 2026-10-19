package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RhythmDarkColorScheme = darkColorScheme(
  primary = SmPlum,
  onPrimary = Color.White,
  primaryContainer = SmPlumDark,
  onPrimaryContainer = Color.White,
  secondary = SmGold,
  onSecondary = SmCharcoal,
  tertiary = SmStageOrange,
  onTertiary = Color.White,
  background = SmCharcoal,
  onBackground = SmTextPrimary,
  surface = SmCharcoal,
  onSurface = SmTextPrimary,
  surfaceVariant = SmCharcoalLight,
  onSurfaceVariant = SmTextSecondary,
  outline = SmCharcoalEdge
)

@Composable
fun RhythmPadTheme(
  content: @Composable () -> Unit
) {
  MaterialTheme(
    colorScheme = RhythmDarkColorScheme,
    typography = Typography,
    content = content
  )
}
