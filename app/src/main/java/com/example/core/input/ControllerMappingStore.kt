package com.example.core.input

import android.content.Context
import android.content.SharedPreferences
import android.view.KeyEvent
import com.example.core.model.ControllerMapping
import com.example.core.model.GameLane
import com.example.core.model.InputBinding

interface ControllerMappingStore {
  fun loadMapping(): ControllerMapping
  fun saveMapping(mapping: ControllerMapping)
  fun resetToDefaults(): ControllerMapping
}

class SharedPreferencesMappingStore(context: Context) : ControllerMappingStore {
  private val prefs: SharedPreferences =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  override fun loadMapping(): ControllerMapping {
    val defaults = ControllerMapping.createMicrontekDefaults()
    val leftRaw = prefs.getString(KEY_LEFT, null)
    val downRaw = prefs.getString(KEY_DOWN, null)
    val upRaw = prefs.getString(KEY_UP, null)
    val rightRaw = prefs.getString(KEY_RIGHT, null)
    val version = prefs.getInt(KEY_VERSION, 0)

    if (leftRaw == null || downRaw == null || upRaw == null || rightRaw == null) {
      saveMapping(defaults)
      return defaults
    }

    // Migration logic: Check if stored mapping is the v0.1.0 generic default
    // (which used DPAD_LEFT or AXIS_HAT instead of the confirmed BUTTON_1..4).
    if (version < 2) {
      val isOldGenericDefault = (leftRaw.contains("KEYCODE_DPAD_LEFT") || leftRaw.contains("KEYCODE_BUTTON_X")) &&
          !leftRaw.contains("KEYCODE_BUTTON_1")

      if (isOldGenericDefault) {
        // Safely migrate old generic defaults to confirmed Microntek defaults
        saveMapping(defaults)
        return defaults
      } else {
        // User had explicit custom mappings; mark version migrated without overwriting
        prefs.edit().putInt(KEY_VERSION, 2).apply()
      }
    }

    val leftBindings = deserializeList(leftRaw).ifEmpty { defaults.leftBindings }
    val downBindings = deserializeList(downRaw).ifEmpty { defaults.downBindings }
    val upBindings = deserializeList(upRaw).ifEmpty { defaults.upBindings }
    val rightBindings = deserializeList(rightRaw).ifEmpty { defaults.rightBindings }

    return ControllerMapping(
      leftBindings = leftBindings,
      downBindings = downBindings,
      upBindings = upBindings,
      rightBindings = rightBindings
    )
  }

  override fun saveMapping(mapping: ControllerMapping) {
    prefs.edit()
      .putString(KEY_LEFT, serializeList(mapping.leftBindings))
      .putString(KEY_DOWN, serializeList(mapping.downBindings))
      .putString(KEY_UP, serializeList(mapping.upBindings))
      .putString(KEY_RIGHT, serializeList(mapping.rightBindings))
      .putInt(KEY_VERSION, 2)
      .apply()
  }

  override fun resetToDefaults(): ControllerMapping {
    prefs.edit().clear().apply()
    val defaults = ControllerMapping.createMicrontekDefaults()
    saveMapping(defaults)
    return defaults
  }

  private fun serializeList(bindings: List<InputBinding>): String {
    return bindings.joinToString(";") { it.serialize() }
  }

  private fun deserializeList(raw: String): List<InputBinding> {
    if (raw.isBlank()) return emptyList()
    return raw.split(";").mapNotNull { InputBinding.deserialize(it) }
  }

  companion object {
    private const val PREFS_NAME = "rhythmpad7_controller_mappings"
    private const val KEY_LEFT = "mapping_lane_left"
    private const val KEY_DOWN = "mapping_lane_down"
    private const val KEY_UP = "mapping_lane_up"
    private const val KEY_RIGHT = "mapping_lane_right"
    private const val KEY_VERSION = "mapping_profile_version"
  }
}
