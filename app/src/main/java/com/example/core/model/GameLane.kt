package com.example.core.model

import androidx.compose.ui.graphics.Color

/**
 * The four lanes of RhythmPad7 in canonical StepMania/DDR order:
 * LEFT | DOWN | UP | RIGHT
 */
enum class GameLane(val displayName: String, val defaultColor: Color) {
  LEFT(displayName = "LEFT", defaultColor = Color(0xFFFF1744)),    // Crimson Red / Pink
  DOWN(displayName = "DOWN", defaultColor = Color(0xFF00E5FF)),    // Electric Cyan
  UP(displayName = "UP", defaultColor = Color(0xFF00E676)),        // Neon Emerald Green
  RIGHT(displayName = "RIGHT", defaultColor = Color(0xFFFF6D00));  // Vivid Amber / Orange

  val index: Int
    get() = ordinal
}
