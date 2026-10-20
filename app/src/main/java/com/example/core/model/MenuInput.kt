package com.example.core.model

/** Non-arrow buttons on the Microntek mat (see ANDROID_MAPPING.md: B5–B10). */
enum class MenuButton(val keyCode: Int) {
  SQUARE(android.view.KeyEvent.KEYCODE_BUTTON_5),
  TRIANGLE(android.view.KeyEvent.KEYCODE_BUTTON_6),
  CROSS(android.view.KeyEvent.KEYCODE_BUTTON_7),
  CIRCLE(android.view.KeyEvent.KEYCODE_BUTTON_8),
  SELECT(android.view.KeyEvent.KEYCODE_BUTTON_9),
  START(android.view.KeyEvent.KEYCODE_BUTTON_10);

  /** StepMania convention on a pad: Start (or Circle) confirms, Select (or Cross) goes back. */
  val isConfirm: Boolean get() = this == START || this == CIRCLE
  val isBack: Boolean get() = this == SELECT || this == CROSS

  companion object {
    fun fromKeyCode(keyCode: Int): MenuButton? = entries.firstOrNull { it.keyCode == keyCode }
  }
}

/** A fresh press used for menu navigation: an arrow panel or a menu button. */
sealed class MenuInput {
  data class Arrow(val lane: GameLane) : MenuInput()
  data class Button(val button: MenuButton) : MenuInput()

  val isConfirm: Boolean get() = this is Button && button.isConfirm
  val isBack: Boolean get() = this is Button && button.isBack
}
