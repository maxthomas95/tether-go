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
  val resumeToolSessionId: String? = null,
)

object LaunchCommandBuilder {
  /**
   * Build the single shell command line Tether Go types into the remote PTY.
   *
   * This is deliberately just string assembly: Tether Go produces the same
   * command a user would type by hand (`cd <dir> && env 'FOO=bar' claude --flags`)
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

    val assignments = profile.env.entries
      .filter { it.key.isNotBlank() }
      .map { (name, value) -> ShellQuote.quoteEnvAssignment(name.trim(), value) }
    if (assignments.isNotEmpty()) {
      parts += "env"
      parts += assignments
    }

    parts += CliToolRegistry.binaryFor(profile.cliTool, profile.customBinary)

    val resumeId = profile.resumeToolSessionId
    if (resumeId == null) {
      // Default / manual deliberately preserves the existing raw shell flags.
      profile.flags.map { it.trim() }.filter { it.isNotEmpty() }.forEach { parts += it }
    } else {
      val manualArgs = PosixLiteralArguments.parse(profile.flags.joinToString(" "))
      parts += NativeResume.buildArgs(profile.cliTool, manualArgs, resumeId).map(ShellQuote::quoteArg)
    }

    return parts.joinToString(" ")
  }
}
