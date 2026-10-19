package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// StepMania 5 "default" theme palette (Themes/default/Scripts/02 Colors.lua)
val SmPlum = Color(0xFF882D47)            // ScreenColors.Default header bar
val SmPlumDark = Color(0xFF4A1827)        // ColorDarkTone(SmPlum)
val SmPlumLight = Color(0xFFC0587A)       // ColorLightTone(SmPlum) – header diamond
val SmStageOrange = Color(0xFFD05722)     // ScreenColors.StageInformation
val SmGold = Color(0xFFF7B035)            // LifeMeter frame gold
val SmGoldLight = Color(0xFFFFD873)
val SmMaxCombo = Color(0xFFFFC600)        // JudgmentLine_MaxCombo

// Charcoal frame tones (ScoreDisplayNormal frame / base bar #1C1C1B → #333230)
val SmCharcoal = Color(0xFF1C1C1B)
val SmCharcoalLight = Color(0xFF333230)
val SmCharcoalEdge = Color(0xFF55524D)
val SmScrim = Color(0xCC0B0A0A)

// Judgment line colors
val SmJudgeW1 = Color(0xFFA0DBF1)
val SmJudgeW2 = Color(0xFFF1E4A2)
val SmJudgeW3 = Color(0xFFABE39B)
val SmJudgeMiss = Color(0xFFF97E7E)

// Player / difficulty accents
val SmP1Blue = Color(0xFF4B82DC)
val SmP2Red = Color(0xFFDF4C47)
val SmBeginnerMint = Color(0xFF1AE0E4)
val SmEasyGreen = Color(0xFF3ACF2A)

val SmTextPrimary = Color(0xFFFFFFFF)
val SmTextSecondary = Color(0xFFD9D4CC)
val SmTextMuted = Color(0xFF9A948B)

// Legacy names kept for existing call sites
val RhythmBackground = SmCharcoal
val RhythmSurface = SmCharcoalLight
val RhythmSurfaceVariant = Color(0xFF2A2927)
val RhythmCyan = SmBeginnerMint
val RhythmGreen = SmEasyGreen
val RhythmRed = SmP2Red
val RhythmOrange = SmStageOrange
val RhythmPurple = SmPlumLight
val RhythmTextPrimary = SmTextPrimary
val RhythmTextSecondary = SmTextSecondary
val RhythmOutline = SmCharcoalEdge
