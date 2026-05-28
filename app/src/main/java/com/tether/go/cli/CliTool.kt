package com.tether.go.cli

/**
 * CLI tool registry, ported from the desktop Tether shared registry
 * (`src/shared/cli-tools.ts`). Tether Go launches the same agent CLIs with the
 * same flag vocabulary so a session created on the phone behaves like one
 * created on the desktop.
 */
enum class CliToolId(val id: String) {
  CLAUDE("claude"),
  CODEX("codex"),
  COPILOT("copilot"),
  OPENCODE("opencode"),
  CUSTOM("custom");

  companion object {
    fun fromId(id: String?): CliToolId = entries.firstOrNull { it.id == id } ?: CLAUDE
  }
}

data class CliFlag(val flag: String, val label: String)

data class CliToolDef(
  val id: CliToolId,
  val displayName: String,
  val binaryName: String,
  val supportsSessionResume: Boolean,
  val commonFlags: List<CliFlag>,
)

object CliToolRegistry {
  val claude = CliToolDef(
    id = CliToolId.CLAUDE,
    displayName = "Claude Code",
    binaryName = "claude",
    supportsSessionResume = true,
    commonFlags = listOf(
      CliFlag("--dangerously-skip-permissions", "Skip permission prompts"),
      CliFlag("--permission-mode plan", "Plan mode (no edits)"),
      CliFlag("--bare", "Minimal mode (skip hooks/plugins)"),
      CliFlag("--verbose", "Verbose output"),
    ),
  )

  val codex = CliToolDef(
    id = CliToolId.CODEX,
    displayName = "Codex CLI",
    binaryName = "codex",
    supportsSessionResume = true,
    commonFlags = listOf(
      CliFlag("--full-auto", "Full auto mode"),
      CliFlag("--search", "Enable web search"),
      CliFlag("--no-alt-screen", "Disable alternate screen"),
      CliFlag("--dangerously-bypass-approvals-and-sandbox", "Bypass approvals and sandbox"),
    ),
  )

  val copilot = CliToolDef(
    id = CliToolId.COPILOT,
    displayName = "GitHub Copilot CLI",
    binaryName = "copilot",
    supportsSessionResume = true,
    commonFlags = listOf(
      CliFlag("--yolo", "Allow all tools, paths, and URLs"),
      CliFlag("--plan", "Plan mode (no execution)"),
      CliFlag("--autopilot", "Autopilot continuation"),
      CliFlag("--allow-all-tools", "Allow all tools without prompting"),
      CliFlag("--no-banner", "Hide startup banner"),
    ),
  )

  val opencode = CliToolDef(
    id = CliToolId.OPENCODE,
    displayName = "OpenCode",
    binaryName = "opencode",
    supportsSessionResume = true,
    commonFlags = listOf(
      CliFlag("--continue", "Continue last session"),
      CliFlag("--pure", "Run without external plugins"),
      CliFlag("--print-logs", "Print logs to stderr"),
    ),
  )

  val custom = CliToolDef(
    id = CliToolId.CUSTOM,
    displayName = "Custom",
    binaryName = "",
    supportsSessionResume = false,
    commonFlags = emptyList(),
  )

  val all: List<CliToolDef> = listOf(claude, codex, copilot, opencode, custom)

  fun byId(id: CliToolId): CliToolDef = all.first { it.id == id }

  /** Resolve the binary to launch; Custom falls back to its user-entered binary. */
  fun binaryFor(id: CliToolId, customBinary: String?): String =
    if (id == CliToolId.CUSTOM) {
      customBinary?.trim()?.takeIf { it.isNotEmpty() } ?: "claude"
    } else {
      byId(id).binaryName
    }
}
