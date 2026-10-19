package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RhythmDarkColorScheme = darkColorScheme(
  primary = RhythmCyan,
  onPrimary = Color(0xFF04070D),
  primaryContainer = Color(0xFF083344),
  onPrimaryContainer = RhythmCyan,
  secondary = RhythmGreen,
  onSecondary = Color(0xFF04070D),
  tertiary = RhythmRed,
  onTertiary = Color.White,
  background = RhythmBackground,
  onBackground = RhythmTextPrimary,
  surface = RhythmSurface,
  onSurface = RhythmTextPrimary,
  surfaceVariant = RhythmSurfaceVariant,
  onSurfaceVariant = RhythmTextSecondary,
  outline = RhythmOutline
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
