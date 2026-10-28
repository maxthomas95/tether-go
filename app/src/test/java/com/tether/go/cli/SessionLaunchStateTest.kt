package com.tether.go.cli

import com.tether.go.settings.PreferenceBackedLaunchDefaultsStore
import com.tether.go.ssh.StringPreferenceStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionLaunchStateTest {
  @Test fun initialSnapshotUsesSavedDefaultsWithoutResumeIntent() {
    val state = SessionLaunchState(CliToolId.CLAUDE).restoreDefaults(Result.success(GuidedLaunchSelection(model = "sonnet")))
    assertEquals(CliToolId.CLAUDE, state.tool)
    assertEquals("sonnet", state.selection.model)
    assertFalse(state.guidedResume)
    assertEquals("", state.resumeId)
    assertNull(state.defaultsError)
  }

  @Test fun toolChangesResetResumeAndReplaceRatherThanMergeGuidedFields() {
    var state = SessionLaunchState(CliToolId.CODEX, GuidedLaunchSelection(nativeProfile = "work", reasoningEffort = "high"),
      guidedResume = true, resumeId = "codex-id")
    state = state.changeTool(CliToolId.OPENCODE, Result.success(GuidedLaunchSelection(agent = "build")))
    assertEquals(CliToolId.OPENCODE, state.tool)
    assertEquals(GuidedLaunchSelection(agent = "build"), state.selection)
    assertFalse(state.guidedResume)
    assertEquals("", state.resumeId)
    CliToolId.entries.forEach { tool ->
      state = state.copy(guidedResume = true, resumeId = "previous-id").changeTool(tool, Result.success(GuidedLaunchSelection()))
      assertEquals(tool, state.tool)
      assertEquals(GuidedLaunchSelection(), state.selection)
      assertFalse(state.guidedResume)
      assertEquals("", state.resumeId)
    }
  }

  @Test fun failedLoadRequiresExplicitBypassAndDoesNotExposeStoredData() {
    val state = SessionLaunchState(CliToolId.CLAUDE).restoreDefaults(Result.failure(IllegalStateException("private stored data")))
    assertNotNull(state.defaultsError)
    assertFalse(state.defaultsError!!.contains("private stored data"))
    assertEquals(GuidedLaunchSelection(), state.selection)
    // Typing a replacement field does not silently acknowledge the read failure.
    assertNotNull(state.copy(selection = GuidedLaunchSelection(model = "typed")).defaultsError)
    assertThrows(IllegalStateException::class.java) { state.resolveFlags(emptyList()) }
    val manual = state.withoutDefaults()
    assertEquals(emptyList<String>(), manual.resolveFlags(emptyList()))
    assertNull(manual.defaultsError)
    assertEquals(GuidedLaunchSelection(), manual.selection)
  }

  @Test fun clearAndRestoreAffectOnlyGuidedFieldsAndPreserveExplicitResumeId() {
    val state = SessionLaunchState(CliToolId.CODEX, GuidedLaunchSelection(model = "edited"),
      guidedResume = true, resumeId = "explicit-id")
    val manual = state.withoutDefaults()
    assertEquals(state.copy(selection = GuidedLaunchSelection()), manual)
    val restored = manual.restoreDefaults(Result.success(GuidedLaunchSelection(model = "saved")))
    assertEquals(state.copy(selection = GuidedLaunchSelection(model = "saved")), restored)
    val failed = restored.restoreDefaults(Result.failure(IllegalStateException()))
    assertTrue(failed.guidedResume)
    assertEquals("explicit-id", failed.resumeId)
    assertNotNull(failed.defaultsError)
    assertEquals(manual, failed.withoutDefaults())
  }

  @Test fun switchingAfterAnErrorUsesOnlyTheNewToolsLoadResult() {
    val failed = SessionLaunchState(CliToolId.CLAUDE).restoreDefaults(Result.failure(IllegalStateException()))
      .copy(guidedResume = true, resumeId = "old")
    val good = failed.changeTool(CliToolId.CODEX, Result.success(GuidedLaunchSelection(nativeProfile = "work")))
    assertNull(good.defaultsError)
    assertEquals(GuidedLaunchSelection(nativeProfile = "work"), good.selection)
    val bad = good.changeTool(CliToolId.OPENCODE, Result.failure(IllegalStateException()))
    assertNotNull(bad.defaultsError)
    assertEquals(GuidedLaunchSelection(), bad.selection)
    assertFalse(bad.guidedResume)
    assertEquals("", bad.resumeId)
  }

  @Test fun storedDefaultsAreSnapshotsAndPerSessionEditsNeverWriteThem() {
    val backing = MapStore()
    val store = PreferenceBackedLaunchDefaultsStore(backing)
    val first = GuidedLaunchSelection(model = "first")
    store.save(CliToolId.CLAUDE, first)
    val state = SessionLaunchState(CliToolId.CLAUDE).restoreDefaults(runCatching { store.load(CliToolId.CLAUDE) })
    val edited = state.copy(selection = GuidedLaunchSelection(model = "edited"))
    assertEquals(first, store.load(CliToolId.CLAUDE))
    store.save(CliToolId.CLAUDE, GuidedLaunchSelection(model = "later"))
    assertEquals(first, state.selection)
    assertEquals("edited", edited.selection.model)
    assertEquals("later", state.restoreDefaults(runCatching { store.load(CliToolId.CLAUDE) }).selection.model)
    store.clear(CliToolId.CLAUDE)
    assertEquals(first, state.selection)
    assertEquals(GuidedLaunchSelection(), state.changeTool(CliToolId.CLAUDE, runCatching { store.load(CliToolId.CLAUDE) }).selection)
  }

  @Test fun missingAndExplicitlyClearedDefaultsPreserveManualFragmentsByIdentity() {
    val store = PreferenceBackedLaunchDefaultsStore(MapStore())
    val raw = listOf(" --model \"\$MODEL\" ", "'quotes' && printf raw")
    CliToolId.entries.forEach { tool ->
      assertSame(raw, GuidedLaunchFlags.resolve(tool, raw, store.load(tool)))
      val state = SessionLaunchState(tool, GuidedLaunchSelection(model = "previous")).withoutDefaults()
      assertSame(raw, state.resolveFlags(raw))
    }
  }

  @Test fun savedModelsDetectManualConflictsWithoutMutatingArgumentsOrResumeId() {
    val store = PreferenceBackedLaunchDefaultsStore(MapStore())
    GuidedLaunchFlags.supportedTools.forEach { tool ->
      store.save(tool, GuidedLaunchSelection(model = "saved"))
      val state = SessionLaunchState(tool).restoreDefaults(runCatching { store.load(tool) })
        .copy(guidedResume = true, resumeId = "explicit-id")
      val raw = listOf("--model manual")
      assertThrows(IllegalArgumentException::class.java) { state.resolveFlags(raw) }
      assertEquals(listOf("--model manual"), raw)
      assertEquals("saved", store.load(tool).model)
      assertEquals("explicit-id", state.resumeId)
      val manual = state.withoutDefaults()
      assertSame(raw, manual.resolveFlags(raw))
      val command = LaunchCommandBuilder.build(LaunchProfile(tool, flags = raw, resumeToolSessionId = manual.resumeId))
      assertTrue(command.contains("explicit-id"))
      assertTrue(command.contains("manual"))
    }
  }

  @Test fun savedDefaultsComposeWithExplicitResumeAndUnrelatedManualArguments() {
    val cases = listOf(
      CliToolId.CLAUDE to GuidedLaunchSelection(model = "sonnet[1m]"),
      CliToolId.OPENCODE to GuidedLaunchSelection(model = "provider/model", agent = "build"),
      CliToolId.CODEX to GuidedLaunchSelection(model = "resume", nativeProfile = "fork", reasoningEffort = "xhigh"),
    )
    val store = PreferenceBackedLaunchDefaultsStore(MapStore())
    cases.forEach { (tool, selection) ->
      store.save(tool, selection)
      val state = SessionLaunchState(tool).restoreDefaults(runCatching { store.load(tool) })
        .copy(guidedResume = true, resumeId = "explicit-id")
      val raw = listOf("'literal with spaces' '' '\$HOME;*'")
      val expected = GuidedLaunchFlags.resolve(tool, raw, selection)
      val flags = state.resolveFlags(raw)
      assertEquals(expected, flags)
      assertEquals(LaunchCommandBuilder.build(LaunchProfile(tool, flags = expected, resumeToolSessionId = "explicit-id")),
        LaunchCommandBuilder.build(LaunchProfile(tool, flags = flags, resumeToolSessionId = state.resumeId)))
    }
  }

  @Test fun previouslyValidPreviewCannotBypassALaterFailedRestore() {
    var state = SessionLaunchState(CliToolId.CLAUDE, GuidedLaunchSelection(model = "saved"))
    val preview = state.resolveLaunch(emptyList())
    assertTrue(preview.command.contains("saved"))
    state = state.restoreDefaults(Result.failure(IllegalStateException("unreadable")))
    assertThrows(IllegalStateException::class.java) { state.resolveLaunch(emptyList()) }
    val manual = state.withoutDefaults().resolveLaunch(listOf("--model manual"))
    assertEquals(listOf("--model manual"), manual.profile.flags)
  }

  @Test fun freshLaunchUsesCurrentToolChoicesAndInputsInsteadOfEarlierPreview() {
    var state = SessionLaunchState(CliToolId.CLAUDE, GuidedLaunchSelection(model = "old"),
      guidedResume = true, resumeId = "old-id")
    val oldPreview = state.resolveLaunch(emptyList(), workingDir = "/old", env = mapOf("MODE" to "old"))
    state = state.changeTool(CliToolId.CODEX, Result.success(GuidedLaunchSelection(nativeProfile = "work")))
      .copy(guidedResume = true, resumeId = "new-id")
    val current = state.resolveLaunch(listOf("--search"), workingDir = "/new", env = mapOf("MODE" to "new"))
    assertEquals(CliToolId.CODEX, current.profile.cliTool)
    assertEquals("/new", current.profile.workingDir)
    assertEquals(mapOf("MODE" to "new"), current.profile.env)
    assertEquals("new-id", current.profile.resumeToolSessionId)
    assertEquals(listOf("'--search'", "'--profile=work'"), current.profile.flags)
    assertEquals(LaunchCommandBuilder.build(current.profile), current.command)
    assertEquals(CliToolId.CLAUDE, oldPreview.profile.cliTool)
    assertEquals("old-id", oldPreview.profile.resumeToolSessionId)
    assertEquals("/old", oldPreview.profile.workingDir)
  }

  @Test fun freshLaunchRevalidatesNewManualConflictsIdsAndEnvironment() {
    val state = SessionLaunchState(CliToolId.CODEX, GuidedLaunchSelection(model = "saved"),
      guidedResume = true, resumeId = "valid-id")
    state.resolveLaunch(emptyList())
    assertThrows(IllegalArgumentException::class.java) { state.resolveLaunch(listOf("--model manual")) }
    assertThrows(IllegalArgumentException::class.java) { state.copy(resumeId = "").resolveLaunch(emptyList()) }
    assertThrows(IllegalArgumentException::class.java) { state.resolveLaunch(emptyList(), env = mapOf("BAD NAME" to "value")) }
    val customState = state.changeTool(CliToolId.CUSTOM, Result.success(GuidedLaunchSelection()))
    listOf(null, "", " ").forEach { binary ->
      assertThrows(IllegalArgumentException::class.java) { customState.resolveLaunch(emptyList(), customBinary = binary) }
    }
    val custom = customState.resolveLaunch(listOf("--manual"), customBinary = "my-agent", workingDir = " ")
    assertEquals("my-agent", custom.profile.customBinary)
    assertNull(custom.profile.workingDir)
    assertNull(custom.profile.resumeToolSessionId)
    assertEquals("my-agent --manual", custom.command)
  }

  private class MapStore : StringPreferenceStore {
    private val values = mutableMapOf<String, String>()
    override fun getString(key: String): String? = values[key]
    override fun putString(key: String, value: String) { values[key] = value }
    override fun removeString(key: String) { values.remove(key) }
  }
}
