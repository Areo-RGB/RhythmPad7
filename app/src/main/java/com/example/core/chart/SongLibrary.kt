package com.example.core.chart

import android.content.res.AssetManager
import android.util.Log
import java.util.Locale

/**
 * Scans `assets/songs/<folder>/` for `.ssc` (preferred, like StepMania) or `.sm` simfiles.
 */
object SongLibrary {
  private const val TAG = "SongLibrary"
  const val ROOT = "songs"

  fun loadFromAssets(assets: AssetManager): List<Song> {
    val folders = assets.list(ROOT).orEmpty().sorted()
    return folders.mapNotNull { name ->
      val folder = "$ROOT/$name"
      val files = assets.list(folder).orEmpty().toList()
      val simfile = files.firstOrNull { it.lowercase(Locale.US).endsWith(".ssc") }
        ?: files.firstOrNull { it.lowercase(Locale.US).endsWith(".sm") }
        ?: return@mapNotNull null
      runCatching {
        val text = assets.open("$folder/$simfile").bufferedReader().use { it.readText() }
        SimfileLoader.load(
          text = text,
          isSsc = simfile.lowercase(Locale.US).endsWith(".ssc"),
          folder = folder,
          fileLookup = { resolveFile(files, it) }
        )
      }.onFailure { Log.w(TAG, "Failed to load $folder/$simfile", it) }
        .getOrNull()
        ?.takeIf { it.charts.isNotEmpty() && it.musicFile != null }
    }.sortedBy { it.title.lowercase(Locale.US) }
  }

  /**
   * Scans `<root>/<folder>/` on device storage, also one pack level deep like StepMania's
   * `Songs/<Pack>/<Song>/`. Song paths are absolute, which SongAudio/AssetImage read from disk.
   */
  fun loadFromDirectory(root: java.io.File): List<Song> {
    val songDirs = root.listFiles().orEmpty().filter { it.isDirectory }.flatMap { dir ->
      if (dir.listFiles().orEmpty().any { it.isSimfile() }) listOf(dir)
      else dir.listFiles().orEmpty().filter { it.isDirectory }
    }
    return songDirs.mapNotNull { dir ->
      val files = dir.list().orEmpty().toList()
      val simfile = files.firstOrNull { it.lowercase(Locale.US).endsWith(".ssc") }
        ?: files.firstOrNull { it.lowercase(Locale.US).endsWith(".sm") }
        ?: return@mapNotNull null
      runCatching {
        SimfileLoader.load(
          text = java.io.File(dir, simfile).readText(),
          isSsc = simfile.lowercase(Locale.US).endsWith(".ssc"),
          folder = dir.absolutePath,
          fileLookup = { resolveFile(files, it) }
        )
      }.onFailure { Log.w(TAG, "Failed to load ${dir.path}/$simfile", it) }
        .getOrNull()
        ?.takeIf { it.charts.isNotEmpty() && it.musicFile != null }
    }
  }

  private fun java.io.File.isSimfile() =
    isFile && name.lowercase(Locale.US).let { it.endsWith(".ssc") || it.endsWith(".sm") }

  /** Exact match first, then same base name with any extension (assets re-encode images). */
  fun resolveFile(files: List<String>, wanted: String): String? {
    files.firstOrNull { it == wanted }?.let { return it }
    files.firstOrNull { it.equals(wanted, ignoreCase = true) }?.let { return it }
    val base = wanted.substringBeforeLast('.').lowercase(Locale.US)
    return files.firstOrNull { it.substringBeforeLast('.').lowercase(Locale.US) == base }
  }
}
