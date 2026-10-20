package com.example.core.gameplay

import java.util.Locale

/**
 * StepMania scroll-speed modifiers (PlayerOptions m_fScrollSpeed / m_fMaxScrollBPM / m_fTimeSpacing):
 * - [X]: beat spacing × multiplier — arrows follow BPM changes, stops, SCROLLS and SPEEDS.
 * - [M]: like X, with the multiplier chosen so the song's read BPM scrolls at [bpm].
 * - [C]: constant time spacing at [bpm]; ignores tempo changes and chart scroll effects.
 */
sealed class ScrollMod {
  abstract val label: String

  data class X(val multiplier: Double) : ScrollMod() {
    override val label: String
      get() = if (multiplier % 1.0 == 0.0) "${multiplier.toInt()}x" else String.format(Locale.US, "%.2gx", multiplier)
  }

  data class M(val bpm: Int) : ScrollMod() {
    override val label: String get() = "M$bpm"
  }

  data class C(val bpm: Int) : ScrollMod() {
    override val label: String get() = "C$bpm"
  }

  /** Beat-spacing multiplier for X/M mods; [readBpm] is the song's highest displayed BPM. */
  fun beatMultiplier(readBpm: Double): Double = when (this) {
    is X -> multiplier
    is M -> bpm / readBpm.coerceAtLeast(1.0)
    is C -> 1.0
  }

  fun serialize(): String = when (this) {
    is X -> "x$multiplier"
    is M -> "m$bpm"
    is C -> "c$bpm"
  }

  companion object {
    /**
     * Beats between the receptors and the bottom of the note field at 1x, matching StepMania's
     * 64 px ARROW_SPACING over its ~365 px default note-field travel.
     */
    const val BEATS_ON_SCREEN_AT_1X = 5.7

    /** M120 ≈ 2.85 s of arrows on screen for every song, the beginner-friendly feel RhythmPad7 had. */
    val DEFAULT: ScrollMod = M(120)

    val PRESETS: List<ScrollMod> = listOf(
      M(120), M(180), M(250), M(350), M(450),
      X(1.0), X(1.5), X(2.0), X(3.0),
      C(150), C(250), C(350), C(450)
    )

    fun parse(raw: String?): ScrollMod? {
      if (raw.isNullOrBlank()) return null
      val value = raw.substring(1)
      return when (raw.first().lowercaseChar()) {
        'x' -> value.toDoubleOrNull()?.takeIf { it > 0 }?.let { X(it) }
        'm' -> value.toIntOrNull()?.takeIf { it > 0 }?.let { M(it) }
        'c' -> value.toIntOrNull()?.takeIf { it > 0 }?.let { C(it) }
        else -> null
      }
    }
  }
}
