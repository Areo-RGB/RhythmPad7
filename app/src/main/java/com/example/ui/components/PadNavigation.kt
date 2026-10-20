package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.example.core.model.MenuInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Delivers dance-mat menu presses to [onInput] while [enabled] (latest lambda is always used). */
@Composable
fun PadNavigation(
  inputs: Flow<MenuInput>,
  enabled: Boolean = true,
  onInput: (MenuInput) -> Unit
) {
  val handler by rememberUpdatedState(onInput)
  LaunchedEffect(inputs, enabled) {
    if (!enabled) return@LaunchedEffect
    inputs.collect { handler(it) }
  }
}

/** Default for previews/tests: no pad. */
val NoPadInputs: Flow<MenuInput> = emptyFlow()
