package com.example.core.chart

import com.example.core.model.GameLane

/** StepMania difficulty slots (Difficulty.h), in wheel order. */
enum class Difficulty(val displayName: String) {
  BEGINNER("Beginner"),
  EASY("Easy"),
  MEDIUM("Medium"),
  HARD("Hard"),
  CHALLENGE("Challenge"),
  EDIT("Edit");

  companion object {
    /** Accepts SSC names plus the legacy .sm aliases StepMania still maps (Basic, Trick, ...). */
    fun parse(raw: String): Difficulty = when (raw.trim().lowercase()) {
      "beginner" -> BEGINNER
      "easy", "basic", "light" -> EASY
      "medium", "another", "trick", "standard", "difficult" -> MEDIUM
      "hard", "ssr", "maniac", "heavy" -> HARD
      "challenge", "smaniac", "expert", "oni" -> CHALLENGE
      else -> EDIT
    }
  }
}

enum class StepKind { TAP, HOLD, ROLL }

/**
 * One playable step in seconds from music start. HOLD/ROLL steps carry their tail
 * ([endBeat] / [endTimeSeconds]); the head is judged like a tap.
 */
data class ChartStep(
  val beat: Double,
  val timeSeconds: Double,
  val lane: GameLane,
  val kind: StepKind = StepKind.TAP,
  val endBeat: Double? = null,
  val endTimeSeconds: Double? = null
)

data class Chart(
  val difficulty: Difficulty,
  val meter: Int,
  val description: String,
  val timing: TimingData,
  val steps: List<ChartStep>
) {
  val noteCount: Int get() = steps.size
  val holdCount: Int get() = steps.count { it.kind != StepKind.TAP }
}

data class Song(
  /** Asset folder, e.g. `songs/springtime`. */
  val folder: String,
  val title: String,
  val subtitle: String,
  val artist: String,
  val musicFile: String?,
  val bannerFile: String?,
  val backgroundFile: String?,
  val sampleStartSeconds: Double,
  val sampleLengthSeconds: Double,
  val displayBpm: String,
  /** Highest displayed BPM; M-mods scale scroll speed against it (PlayerState::m_fReadBPM). */
  val readBpm: Double,
  val songTiming: TimingData,
  val charts: List<Chart>
) {
  val musicPath: String? get() = musicFile?.let { "$folder/$it" }
  val bannerPath: String? get() = bannerFile?.let { "$folder/$it" }
  val backgroundPath: String? get() = backgroundFile?.let { "$folder/$it" }

  fun chart(difficulty: Difficulty): Chart? = charts.firstOrNull { it.difficulty == difficulty }
}
