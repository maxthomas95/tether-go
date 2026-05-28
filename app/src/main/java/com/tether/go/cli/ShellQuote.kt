package com.tether.go.cli

/**
 * POSIX shell quoting, ported from the desktop Tether helper
 * (`src/shared/shell-quote.ts`). Tether Go always crosses into a remote POSIX
 * shell, so only the POSIX side is needed. These keep working directories and
 * environment assignments safe when the launch command is typed into the PTY.
 */
object ShellQuote {
  private const val SINGLE_QUOTE_ESCAPE = "'\\''"
  private val ENV_NAME_RE = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
  private val NUL_CHAR: Char = Char(0)

  private fun assertNoNul(value: String, label: String) {
    require(!value.contains(NUL_CHAR)) { "$label cannot contain NUL bytes" }
  }

  fun quoteArg(value: String): String {
    assertNoNul(value, "POSIX shell argument")
    val out = StringBuilder("'")
    for (ch in value) {
      if (ch == '\'') out.append(SINGLE_QUOTE_ESCAPE) else out.append(ch)
    }
    return out.append("'").toString()
  }

  fun quoteEnvAssignment(name: String, value: String): String {
    assertNoNul(name, "Environment variable name")
    assertNoNul(value, "Environment variable value")
    require(ENV_NAME_RE.matches(name)) {
      "Invalid environment variable name: $name; names must match $ENV_NAME_RE and cannot contain \"=\""
    }
    return quoteArg("$name=$value")
  }

  /** Quote a path but keep a leading `~` (or `~/`) expandable by the shell. */
  fun quotePathPreservingHome(value: String): String = when {
    value == "~" -> "~"
    value.startsWith("~/") -> "~/" + quoteArg(value.substring(2))
    else -> quoteArg(value)
  }
}
