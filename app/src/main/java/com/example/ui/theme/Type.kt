package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

/** "Common Header" / "Common Large" font of the StepMania 5 default theme. */
val OpenSans = FontFamily(
  Font(R.font.open_sans_semibold, FontWeight.SemiBold),
  Font(R.font.open_sans_bold, FontWeight.Bold),
  Font(R.font.open_sans_extrabold, FontWeight.ExtraBold),
  Font(R.font.open_sans_extrabold, FontWeight.Black)
)

/** "Common Normal" / "Combo Numbers" font (Roboto is the Android system default). */
val Roboto = FontFamily.Default

private val base = Typography()

val Typography = Typography(
  displayLarge = base.displayLarge.copy(fontFamily = OpenSans, fontWeight = FontWeight.ExtraBold),
  displayMedium = base.displayMedium.copy(fontFamily = OpenSans, fontWeight = FontWeight.ExtraBold),
  displaySmall = base.displaySmall.copy(fontFamily = OpenSans, fontWeight = FontWeight.Bold),
  headlineLarge = base.headlineLarge.copy(fontFamily = OpenSans, fontWeight = FontWeight.Bold),
  headlineMedium = base.headlineMedium.copy(fontFamily = OpenSans, fontWeight = FontWeight.Bold),
  headlineSmall = base.headlineSmall.copy(fontFamily = OpenSans, fontWeight = FontWeight.Bold),
  titleLarge = base.titleLarge.copy(fontFamily = OpenSans, fontWeight = FontWeight.SemiBold),
  titleMedium = base.titleMedium.copy(fontFamily = OpenSans, fontWeight = FontWeight.SemiBold),
  titleSmall = base.titleSmall.copy(fontFamily = OpenSans, fontWeight = FontWeight.SemiBold),
  bodyLarge = TextStyle(
    fontFamily = Roboto,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 24.sp,
    letterSpacing = 0.5.sp
  ),
  bodyMedium = base.bodyMedium.copy(fontFamily = Roboto),
  bodySmall = base.bodySmall.copy(fontFamily = Roboto),
  labelLarge = base.labelLarge.copy(fontFamily = OpenSans, fontWeight = FontWeight.Bold),
  labelMedium = base.labelMedium.copy(fontFamily = OpenSans, fontWeight = FontWeight.Bold),
  labelSmall = base.labelSmall.copy(fontFamily = OpenSans, fontWeight = FontWeight.Bold)
)
