package com.example.core.chart

/**
 * Kotlin port of StepMania's `MsdFile` (src/MsdFile.cpp): tokenizes `#TAG:param:param;` files
 * (.sm / .ssc) into values, each a list of params where params[0] is the tag name.
 *
 * Mirrors the original quirks: `//` comments are dropped, a `#` that is the first non-blank
 * character of a line ends a value that is missing its `;`, and `\` escapes the next character.
 */
object MsdFile {

  fun parse(text: String, unescape: Boolean = true): List<List<String>> {
    val values = mutableListOf<MutableList<String>>()
    var readingValue = false
    val processed = StringBuilder()
    var hasParam = false
    var i = 0
    val len = text.length

    fun addParam() {
      values.lastOrNull()?.add(processed.toString())
      processed.setLength(0)
      hasParam = false
    }

    while (i < len) {
      val c = text[i]
      if (c == '/' && i + 1 < len && text[i + 1] == '/') {
        while (i < len && text[i] != '\n') i++
        continue
      }

      if (readingValue && c == '#') {
        // Missing ';' recovery: only when '#' is the first non-blank char on its line.
        var j = processed.length
        var firstChar = true
        while (j > 0 && processed[j - 1] != '\r' && processed[j - 1] != '\n') {
          if (processed[j - 1] == ' ' || processed[j - 1] == '\t') {
            j--
            continue
          }
          firstChar = false
          break
        }
        if (!firstChar) {
          processed.append(c)
          i++
          continue
        }
        processed.setLength(j)
        while (processed.isNotEmpty() && processed.last().let { it == '\r' || it == '\n' || it == ' ' || it == '\t' }) {
          processed.setLength(processed.length - 1)
        }
        addParam()
        readingValue = false
      }

      if (!readingValue && c == '#') {
        values.add(mutableListOf())
        readingValue = true
      }

      if (!readingValue) {
        i += if (unescape && c == '\\') 2 else 1
        continue
      }

      if (hasParam && (c == ':' || c == ';')) addParam()

      if (c == '#' || c == ':') {
        i++
        processed.setLength(0)
        hasParam = true
        continue
      }

      if (c == ';') {
        readingValue = false
        i++
        continue
      }

      if (c == '\\' && unescape) {
        i++
      }
      if (i < len) {
        processed.append(text[i])
        i++
      }
    }

    // Final value missing its ';'
    if (readingValue && hasParam) addParam()

    return values
  }
}
