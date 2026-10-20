package com.example.ui.components

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val assetBitmapCache = HashMap<String, ImageBitmap>()

/** Decodes an image from `assets/` off the main thread (cached); null while loading or missing. */
@Composable
fun rememberAssetBitmap(path: String?): State<ImageBitmap?> {
  val context = LocalContext.current
  val state = remember(path) { mutableStateOf(path?.let { assetBitmapCache[it] }) }
  LaunchedEffect(path) {
    if (path == null || state.value != null) return@LaunchedEffect
    val bitmap = withContext(Dispatchers.IO) {
      runCatching {
        context.assets.open(path).use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
      }.getOrNull()
    }
    if (bitmap != null) {
      assetBitmapCache[path] = bitmap
      state.value = bitmap
    }
  }
  return state
}
