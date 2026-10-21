package com.example.core.model

/**
 * Local player profile, like StepMania's `Profile` (display name + personal high scores).
 * [GUEST] plays without a profile; its results go to the machine score file, as in StepMania.
 */
data class PlayerProfile(val id: String, val displayName: String) {
  val initial: String get() = displayName.take(1).uppercase()
  val isGuest: Boolean get() = id == GUEST.id

  /** Score file name for this profile. */
  val scoreFileName: String get() = if (isGuest) "scores.json" else "scores-$id.json"

  companion object {
    val GUEST = PlayerProfile("guest", "Guest")
    val FINLEY = PlayerProfile("finley", "Finley")
    val ALL = listOf(FINLEY, GUEST)
    fun byId(id: String?): PlayerProfile = ALL.firstOrNull { it.id == id } ?: FINLEY
  }
}
