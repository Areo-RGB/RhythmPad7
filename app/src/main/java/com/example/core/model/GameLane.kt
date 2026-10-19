package com.example.core.model

import androidx.compose.ui.graphics.Color

/**
 * The four lanes of RhythmPad7 in canonical StepMania/DDR order:
 * LEFT | DOWN | UP | RIGHT
 */
enum class GameLane(val displayName: String, val defaultColor: Color) {
  // Colors match the dance/default noteskin frames used for each lane's UI icon
  LEFT(displayName = "LEFT", defaultColor = Color(0xFFA35BFF)),    // 24th purple
  DOWN(displayName = "DOWN", defaultColor = Color(0xFF3D8BFF)),    // 8th blue
  UP(displayName = "UP", defaultColor = Color(0xFF6BDB2A)),        // 12th green
  RIGHT(displayName = "RIGHT", defaultColor = Color(0xFFFF4A3D));  // 4th red

  val index: Int
    get() = ordinal
}
