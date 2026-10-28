package com.tether.go.cli

data class ResolvedSessionLaunch(val profile: LaunchProfile, val command: String)

/** Immutable form snapshot. Editing it never writes defaults or changes an existing session. */
data class SessionLaunchState(
  val tool: CliToolId,
  val selection: GuidedLaunchSelection = GuidedLaunchSelection(),
  val defaultsError: String? = null,
  val guidedResume: Boolean = false,
  val resumeId: String = "",
) {
  /** Failed default loads must be acknowledged before a preview or session can be built. */
  fun resolveFlags(rawFlags: List<String>): List<String> {
    check(defaultsError == null) { defaultsError.orEmpty() }
    return GuidedLaunchFlags.resolve(tool, rawFlags, selection)
  }

  /** Preview and Start share resolution; Start must call this again on its current form snapshot. */
  fun resolveLaunch(
    rawFlags: List<String>,
    customBinary: String? = null,
    workingDir: String? = null,
    env: Map<String, String> = emptyMap(),
  ): ResolvedSessionLaunch {
    require(tool != CliToolId.CUSTOM || !customBinary.isNullOrBlank()) { "Enter a custom binary before starting this session." }
    val profile = LaunchProfile(
      cliTool = tool,
      customBinary = customBinary?.takeIf { it.isNotBlank() },
      workingDir = workingDir?.takeIf { it.isNotBlank() },
      flags = resolveFlags(rawFlags),
      env = env,
      resumeToolSessionId = if (guidedResume) resumeId else null,
    )
    return ResolvedSessionLaunch(profile, LaunchCommandBuilder.build(profile))
  }

  /** A tool switch intentionally resets resume intent but leaves unrelated form inputs alone. */
  fun changeTool(tool: CliToolId, defaults: Result<GuidedLaunchSelection>): SessionLaunchState =
    SessionLaunchState(tool).restoreDefaults(defaults)

  /** Restore affects only guided choices, not an explicit resume ID or manual arguments. */
  fun restoreDefaults(defaults: Result<GuidedLaunchSelection>): SessionLaunchState = defaults.fold(
    onSuccess = { copy(selection = it, defaultsError = null) },
    onFailure = { copy(selection = GuidedLaunchSelection(), defaultsError =
      "Saved defaults could not be loaded. Retry restoring them, clear them in Settings, or explicitly use no defaults for this session.") },
  )

  /** Explicit acknowledgment is required before bypassing a failed defaults load. */
  fun withoutDefaults(): SessionLaunchState = copy(selection = GuidedLaunchSelection(), defaultsError = null)
}
