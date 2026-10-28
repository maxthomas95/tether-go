package com.tether.go.cli

/** Explicit native command helper only; it never discovers or verifies history. */
object NativeResume {
  private const val MAX_ID_LENGTH = 256

  fun buildArgs(tool: CliToolId, manualArgs: List<String>, sessionId: String): List<String> {
    require(CliToolRegistry.byId(tool).supportsSessionResume) {
      "Guided resume is not supported for Custom commands. Use Default / manual."
    }
    validateSessionId(sessionId)
    require(!hasConflicts(tool, manualArgs)) {
      "Manual arguments contain a session selector or ambiguous option. Remove it or use Default / manual."
    }
    // Match desktop src/shared/cli-tools.ts buildCliArgsForTool exactly. In
    // particular Claude must never fall back to --session-id (which can create).
    return when (tool) {
      CliToolId.CLAUDE -> manualArgs + listOf("--resume", sessionId)
      CliToolId.CODEX -> listOf("resume", sessionId) + manualArgs
      CliToolId.COPILOT -> listOf("--resume", sessionId) + manualArgs
      CliToolId.OPENCODE -> listOf("--session", sessionId) + manualArgs
      CliToolId.CUSTOM -> error("Custom resume was rejected above")
    }
  }

  /** Kept separate so unrelated argument errors do not mark the ID field. */
  fun validateSessionId(sessionId: String) {
    require(sessionId.isNotBlank()) { "Enter the full native session ID to resume." }
    require(sessionId.length <= MAX_ID_LENGTH && !sessionId.startsWith('-') &&
      sessionId.none { it.isWhitespace() || it.isISOControl() }) {
      "Session ID must be at most $MAX_ID_LENGTH characters, with no whitespace or control characters, and cannot start with '-'."
    }
  }

  private fun hasConflicts(tool: CliToolId, args: List<String>): Boolean {
    val valueOptions = when (tool) {
      CliToolId.CLAUDE -> setOf("--model", "--permission-mode")
      CliToolId.OPENCODE -> setOf("--model", "--agent", "-m")
      CliToolId.CODEX -> setOf("--model", "-m", "--profile", "-p", "--config", "-c")
      CliToolId.COPILOT -> setOf("--model")
      CliToolId.CUSTOM -> emptySet()
    }
    var index = 0
    while (index < args.size) {
      val arg = args[index]
      val option = valueOptions.firstOrNull { name ->
        arg == name || arg.startsWith("$name=") ||
          (name.length == 2 && arg.startsWith(name) && arg.length > 2)
      }
      if (option != null) {
        // Known value options consume their next literal, not a subcommand.
        // Incomplete/option-like values could swallow the managed selector.
        val value = if (arg == option) args.getOrNull(++index) ?: return true
          else arg.substring(option.length).removePrefix("=")
        if (value.isBlank() || value.startsWith('-')) return true
      } else if (conflicts(tool, arg)) {
        return true
      }
      index++
    }
    return false
  }

  private fun conflicts(tool: CliToolId, arg: String): Boolean {
    // An end-of-options marker could turn Claude's appended --resume into a
    // prompt. Inspect every literal token, including quoted/escaped selectors.
    if (arg == "--") return true
    val longOption = arg.substringBefore('=')
    val selectors = when (tool) {
      CliToolId.CLAUDE -> setOf("--resume", "--continue", "--session-id", "--fork-session", "--from-pr", "--teleport")
      CliToolId.CODEX -> setOf("--last", "--worktree", "resume", "fork")
      CliToolId.COPILOT -> setOf("--resume", "--continue", "--session-id", "--connect", "--worktree")
      CliToolId.OPENCODE -> setOf("--session", "--continue", "--fork")
      CliToolId.CUSTOM -> emptySet()
    }
    if (longOption in selectors) return true
    val shortSelectors = when (tool) {
      CliToolId.CLAUDE -> "rc"
      CliToolId.COPILOT -> "rw"
      CliToolId.OPENCODE -> "sc"
      CliToolId.CODEX, CliToolId.CUSTOM -> ""
    }
    // Deliberately conservative for grouped/attached short forms: without each
    // CLI's full option grammar, an attached value cannot be disambiguated.
    return arg.startsWith('-') && !arg.startsWith("--") &&
      arg.drop(1).any { it in shortSelectors }
  }
}
