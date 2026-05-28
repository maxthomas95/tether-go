package com.tether.go.cli

/**
 * A launch profile describes how to start an agent CLI inside a remote PTY:
 * which tool, where, with which flags and environment. It mirrors the desktop
 * session-creation concepts (working directory, CLI tool, flags, env vars).
 */
data class LaunchProfile(
  val cliTool: CliToolId,
  val customBinary: String? = null,
  val workingDir: String? = null,
  val flags: List<String> = emptyList(),
  val env: Map<String, String> = emptyMap(),
)

object LaunchCommandBuilder {
  /**
   * Build the single shell command line Tether Go types into the remote PTY.
   *
   * This is deliberately just string assembly: Tether Go produces the same
   * command a user would type by hand (`cd <dir> && FOO=bar claude --flags`)
   * and writes it to stdin. It never wraps the CLI in a parser or intercepts
   * its output — the dumb-pipe invariant is preserved.
   *
   * The returned command has no trailing newline; the caller submits it.
   */
  fun build(profile: LaunchProfile): String {
    val parts = mutableListOf<String>()

    profile.workingDir?.trim()?.takeIf { it.isNotEmpty() }?.let { dir ->
      parts += "cd ${ShellQuote.quotePathPreservingHome(dir)} &&"
    }

    profile.env.entries
      .filter { it.key.isNotBlank() }
      .forEach { (name, value) ->
        parts += ShellQuote.quoteEnvAssignment(name.trim(), value)
      }

    parts += CliToolRegistry.binaryFor(profile.cliTool, profile.customBinary)

    // CLI flags are passed through verbatim (a flag may carry its own value,
    // e.g. "--permission-mode plan"), matching how desktop stores cliArgs.
    profile.flags
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .forEach { parts += it }

    return parts.joinToString(" ")
  }
}
