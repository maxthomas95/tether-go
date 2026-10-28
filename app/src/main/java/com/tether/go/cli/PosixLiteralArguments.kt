package com.tether.go.cli

/**
 * A deliberately small POSIX word reader for guided launch arguments, not a
 * shell evaluator. Quotes and literal escapes are supported; anything requiring
 * shell expansion or execution must stay in Default / manual mode.
 */
object PosixLiteralArguments {
  private const val MANUAL_HINT = "Use Default / manual for shell syntax."
  private enum class Quote { None, Single, Double }

  fun parse(source: String): List<String> {
    require(source.none { it.isISOControl() && it != '\t' }) {
      "Arguments cannot contain control characters. $MANUAL_HINT"
    }
    val result = mutableListOf<String>()
    val word = StringBuilder()
    var started = false
    var quote = Quote.None
    var index = 0
    while (index < source.length) {
      val ch = source[index]
      when (quote) {
        Quote.Single -> if (ch == '\'') quote = Quote.None else word.append(ch)
        Quote.Double -> when (ch) {
          '"' -> quote = Quote.None
          '$', '`' -> throw IllegalArgumentException("Shell expansion is not supported in guided resume. $MANUAL_HINT")
          '\\' -> {
            require(index + 1 < source.length) { "Incomplete argument escape. $MANUAL_HINT" }
            val next = source[++index]
            // POSIX double quotes retain backslashes before ordinary characters.
            if (next !in "\$`\"\\") word.append('\\')
            word.append(next)
          }
          else -> word.append(ch)
        }
        Quote.None -> when {
          ch == ' ' || ch == '\t' -> if (started) {
            result += word.toString()
            word.clear()
            started = false
          }
          ch == '\'' -> { quote = Quote.Single; started = true }
          ch == '"' -> { quote = Quote.Double; started = true }
          ch == '\\' -> {
            require(index + 1 < source.length) { "Incomplete argument escape. $MANUAL_HINT" }
            word.append(source[++index])
            started = true
          }
          ch in "\$`;|&<>()*?[]{}~#" || ch.isWhitespace() ->
            throw IllegalArgumentException("Shell syntax is not supported in guided resume. $MANUAL_HINT")
          else -> { word.append(ch); started = true }
        }
      }
      index++
    }
    require(quote == Quote.None) { "Unclosed argument quote. $MANUAL_HINT" }
    if (started) result += word.toString()
    // Commands are typed into an interactive PTY, not passed directly to
    // execve: even a quoted/escaped tab could trigger shell line editing.
    require(result.none { arg -> arg.any { it.isISOControl() } }) {
      "Guided arguments cannot contain literal control characters. $MANUAL_HINT"
    }
    return result
  }
}
