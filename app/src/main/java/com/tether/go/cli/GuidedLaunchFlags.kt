package com.tether.go.cli

/** Bounded native CLI choices, not an app launch profile or model catalog. */
data class GuidedLaunchSelection(
  val model: String = "",
  val agent: String = "",
  val nativeProfile: String = "",
  val reasoningEffort: String = "",
)

/** Resolves guided intent into the existing persisted shell-flag fragments. */
object GuidedLaunchFlags {
  private const val MAX_VALUE_LENGTH = 128
  private val SAFE_VALUE = Regex("^[A-Za-z0-9._:@/+~\\[\\]-]+$")
  private val SAFE_CODEX_VALUE = Regex("^[A-Za-z0-9._:@/+~-]+$")
  private enum class Field(val label: String) {
    Model("Model"), Agent("Agent"), Profile("Native profile"), Reasoning("Reasoning effort"),
  }
  private data class ValueOption(val field: Field? = null, val config: Boolean = false)

  val supportedTools: List<CliToolId> = listOf(CliToolId.CLAUDE, CliToolId.OPENCODE, CliToolId.CODEX)

  /** Shared validation for saved defaults and per-session controls; no manual flags are read. */
  fun normalize(tool: CliToolId, selection: GuidedLaunchSelection): GuidedLaunchSelection {
    val normalized = GuidedLaunchSelection(
      selection.model.trim(), selection.agent.trim(), selection.nativeProfile.trim(), selection.reasoningEffort.trim(),
    )
    val active = activeFields(normalized)
    val supported = when (tool) {
      CliToolId.CLAUDE -> setOf(Field.Model)
      CliToolId.OPENCODE -> setOf(Field.Model, Field.Agent)
      CliToolId.CODEX -> setOf(Field.Model, Field.Profile, Field.Reasoning)
      CliToolId.COPILOT, CliToolId.CUSTOM -> emptySet()
    }
    active.forEach { (field, value) ->
      require(field in supported) { "${field.label} is not supported for ${CliToolRegistry.byId(tool).displayName}. Clear the guided field." }
      val allowed = if (tool == CliToolId.CODEX) SAFE_CODEX_VALUE else SAFE_VALUE
      require(value.length <= MAX_VALUE_LENGTH && !value.startsWith('-') && allowed.matches(value)) {
        "${field.label} must be one identifier of at most $MAX_VALUE_LENGTH characters, without whitespace, quotes, or shell operators, and cannot start with '-'."
      }
    }
    return normalized
  }

  private fun activeFields(selection: GuidedLaunchSelection): Map<Field, String> = linkedMapOf(
    Field.Model to selection.model, Field.Agent to selection.agent,
    Field.Profile to selection.nativeProfile, Field.Reasoning to selection.reasoningEffort,
  ).filterValues { it.isNotEmpty() }

  fun resolve(tool: CliToolId, rawFlags: List<String>, selection: GuidedLaunchSelection): List<String> {
    val normalized = normalize(tool, selection)
    val active = activeFields(normalized)
    // Do not even tokenize the manual path. Clearing controls restores the
    // caller's exact fragments, whitespace, quotes and advanced shell syntax.
    if (active.isEmpty()) return rawFlags
    val manual = PosixLiteralArguments.parse(rawFlags.joinToString(" "))
    checkManualOptions(tool, manual, active.keys)
    val generated = buildList {
      normalized.model.takeIf { it.isNotEmpty() }?.let { add("--model=$it") }
      normalized.agent.takeIf { it.isNotEmpty() }?.let { add("--agent=$it") }
      normalized.nativeProfile.takeIf { it.isNotEmpty() }?.let { add("--profile=$it") }
      normalized.reasoningEffort.takeIf { it.isNotEmpty() }?.let {
        add("-c")
        // The quotes belong to the native TOML value, inside one argv token.
        add("model_reasoning_effort=\"$it\"")
      }
    }
    return (manual + generated).map(ShellQuote::quoteArg)
  }

  private fun valueOptions(tool: CliToolId): Map<String, ValueOption> = buildMap {
    put("--model", ValueOption(Field.Model))
    when (tool) {
      CliToolId.CLAUDE -> put("--permission-mode", ValueOption())
      CliToolId.OPENCODE -> {
        put("-m", ValueOption(Field.Model))
        put("--agent", ValueOption(Field.Agent))
        put("--session", ValueOption())
        put("-s", ValueOption())
      }
      CliToolId.CODEX -> {
        put("-m", ValueOption(Field.Model))
        put("--profile", ValueOption(Field.Profile))
        put("-p", ValueOption(Field.Profile))
        put("--config", ValueOption(config = true))
        put("-c", ValueOption(config = true))
        // These native value options must not expose their payloads as flags.
        listOf("--sandbox", "-s", "--ask-for-approval", "-a", "--cd", "-C", "--image", "-i")
          .forEach { put(it, ValueOption()) }
      }
      CliToolId.COPILOT, CliToolId.CUSTOM -> Unit // No active guided fields.
    }
  }

  private fun checkManualOptions(tool: CliToolId, args: List<String>, active: Set<Field>) {
    val options = valueOptions(tool)
    val shortSelectors = options.filterValues { it.field != null || it.config }.keys
      .filter { it.length == 2 }.map { it[1] }.toSet()
    var index = 0
    while (index < args.size) {
      val arg = args[index]
      require(arg != "--") { "Remove the manual '--' marker or clear the guided launch fields." }
      val match = options.entries.firstOrNull { (name, _) ->
        arg == name || arg.startsWith("$name=") ||
          (name.length == 2 && arg.startsWith(name) && arg.length > 2)
      }
      if (match != null) {
        val (name, option) = match
        option.field?.let { requireNoOverlap(it, active) }
        val value = if (arg == name) args.getOrNull(++index) else arg.substring(name.length).removePrefix("=")
        require(value != null && value.isNotBlank() && !value.startsWith('-')) {
          "Manual $name needs an unambiguous nonempty value. Fix it or clear the guided launch fields."
        }
        if (option.config) checkConfig(value, active)
      } else if (arg.startsWith('-') && !arg.startsWith("--")) {
        // A group such as -vm could hide -m or leave it missing a value. Never
        // guess which letters are switches versus an unknown option's payload.
        require(arg.drop(1).none { it in shortSelectors }) {
          "Manual arguments contain an ambiguous short option. Use separate options or clear the guided launch fields."
        }
      }
      index++
    }
  }

  private fun checkConfig(value: String, active: Set<Field>) {
    val rawKey = value.substringBefore('=').trim()
    // Support literal TOML quoted keys too, without interpreting TOML values.
    // Escaped keys are ambiguous without a full TOML parser; fail closed only
    // when a guided config field could overlap. Unrelated payloads stay intact.
    if (Field.Model in active || Field.Reasoning in active) {
      require('\\' !in rawKey) {
        "Manual config key is ambiguous. Use an unescaped key or clear the guided launch fields."
      }
    }
    val key = rawKey.removeSurrounding("\"").removeSurrounding("'")
    when (key) {
      "model" -> requireNoOverlap(Field.Model, active)
      "model_reasoning_effort" -> requireNoOverlap(Field.Reasoning, active)
    }
  }

  private fun requireNoOverlap(field: Field, active: Set<Field>) {
    require(field !in active) {
      "Manual arguments contain a ${field.label.lowercase()} selector. Remove the manual selector or clear the guided ${field.label} field."
    }
  }
}
