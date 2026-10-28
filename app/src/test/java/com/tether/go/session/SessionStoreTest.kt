package com.tether.go.session

import com.tether.go.cli.CliToolId
import com.tether.go.cli.GuidedLaunchFlags
import com.tether.go.cli.GuidedLaunchSelection
import com.tether.go.cli.LaunchCommandBuilder
import com.tether.go.ssh.StringPreferenceStore
import com.tether.go.settings.PreferenceBackedLaunchDefaultsStore
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStoreTest {
  private fun sampleSession(
    id: String = "s1",
    updatedAt: Long = 200,
  ) = Session(
    id = id,
    label = "tether",
    hostId = "h1",
    host = "vm-1",
    port = 2222,
    username = "max",
    cliTool = CliToolId.CLAUDE,
    customBinary = null,
    workingDir = "/repo/tether",
    flags = listOf("--verbose", "--permission-mode plan"),
    env = mapOf("FOO" to "bar", "TOKEN" to "a=b=c"),
    privateKeyId = "key-1",
    createdAtMillis = 100,
    updatedAtMillis = updatedAt,
  )

  @Test
  fun roundTripsSessionThroughStorage() {
    val backing = mutableMapOf<String, String>()
    val session = sampleSession()
    PreferenceBackedSessionStore(MapStore(backing)).upsertSession(session)

    val restored = PreferenceBackedSessionStore(MapStore(backing)).loadSessions()
    assertEquals(listOf(session), restored)
  }

  @Test
  fun roundTripsMutedAndUnmutedSessionsIndependently() {
    val backing = mutableMapOf<String, String>()
    val store = PreferenceBackedSessionStore(MapStore(backing))
    val muted = sampleSession(id = "muted", updatedAt = 300).copy(notificationsMuted = true)
    val unmuted = sampleSession(id = "unmuted")
    store.upsertSession(muted)
    store.upsertSession(unmuted)

    val restored = PreferenceBackedSessionStore(MapStore(backing)).loadSessions()
    assertEquals(listOf(muted, unmuted), restored)
    assertTrue(restored[0].notificationsMuted)
    assertFalse(restored[1].notificationsMuted)
  }

  @Test
  fun loadsLegacyFourteenFieldRecordAsUnmutedAndPreservesItOnUpgrade() {
    val session = sampleSession()
    // Explicit legacy schema fixture: the old encoder had no mute field.
    val fields = listOf(
      session.id, session.label, session.hostId.orEmpty(), session.host,
      session.port.toString(), session.username, session.cliTool.id,
      session.customBinary.orEmpty(), session.workingDir.orEmpty(),
      session.flags.joinToString("\n"),
      session.env.entries.joinToString("\n") { "${it.key}=${it.value}" },
      session.privateKeyId.orEmpty(), session.createdAtMillis.toString(),
      session.updatedAtMillis.toString(),
    )
    val legacyRecord = fields.joinToString("\t") {
      Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray(Charsets.UTF_8))
    }
    val backing = mutableMapOf("session_records_v1" to legacyRecord)
    val store = PreferenceBackedSessionStore(MapStore(backing))

    val restored = store.loadSessions().single()
    assertEquals(session, restored)
    assertFalse(restored.notificationsMuted)
    store.upsertSession(restored.copy(notificationsMuted = true))
    assertEquals(16, backing.getValue("session_records_v1").split('\t').size)
    assertEquals(session.copy(notificationsMuted = true), store.loadSessions().single())
  }

  @Test
  fun rejectsMalformedMuteFieldWithoutDroppingOtherRecords() {
    val backing = mutableMapOf<String, String>()
    val store = PreferenceBackedSessionStore(MapStore(backing))
    store.upsertSession(sampleSession())
    val validRecord = backing.getValue("session_records_v1")
    val invalidBoolean = Base64.getUrlEncoder().withoutPadding()
      .encodeToString("invalid".toByteArray(Charsets.UTF_8))
    val invalidFields = validRecord.split('\t').toMutableList().also { it[14] = invalidBoolean }
    backing["session_records_v1"] = invalidFields.joinToString("\t") + "\n" + validRecord

    assertEquals(listOf(sampleSession()), store.loadSessions())
  }

  @Test
  fun loadsFifteenFieldRecordsWithStoredMuteAndNoResumeId() {
    val backing = mutableMapOf<String, String>()
    val store = PreferenceBackedSessionStore(MapStore(backing))
    val session = sampleSession().copy(notificationsMuted = true)
    store.upsertSession(session)
    backing["session_records_v1"] = backing.getValue("session_records_v1").split('\t').take(15).joinToString("\t")
    assertEquals(session, store.loadSessions().single())
    assertNull(store.loadSessions().single().resumeToolSessionId)
    store.upsertSession(store.loadSessions().single().copy(label = "Renamed"))
    assertEquals(16, backing.getValue("session_records_v1").split('\t').size)
    assertEquals(session.copy(label = "Renamed"), store.loadSessions().single())
  }

  @Test
  fun sixteenFieldsPreserveNativeIdFlagsEnvironmentMuteAndTimestamps() {
    val backing = mutableMapOf<String, String>()
    val store = PreferenceBackedSessionStore(MapStore(backing))
    val session = sampleSession().copy(resumeToolSessionId = "native-'id", notificationsMuted = true)
    store.upsertSession(session)
    assertEquals(16, backing.getValue("session_records_v1").split('\t').size)
    assertEquals(session, store.loadSessions().single())
    assertEquals("native-'id", store.loadSessions().single().launchProfile().resumeToolSessionId)
    store.upsertSession(session.copy(label = "Renamed", notificationsMuted = false))
    assertEquals(session.copy(label = "Renamed", notificationsMuted = false), store.loadSessions().single())
  }

  @Test
  fun resolvedGuidedFlagsRoundTripInExistingSchemaAndReproduceLaunchCommands() {
    val cases = listOf(
      CliToolId.CLAUDE to GuidedLaunchSelection(model = "sonnet[1m]"),
      CliToolId.OPENCODE to GuidedLaunchSelection(model = "provider/model", agent = "build"),
      CliToolId.CODEX to GuidedLaunchSelection(model = "resume", nativeProfile = "fork", reasoningEffort = "xhigh"),
    )
    cases.forEach { (tool, selection) -> listOf(null, "explicit-id").forEach { resumeId ->
      val backing = mutableMapOf<String, String>()
      val flags = GuidedLaunchFlags.resolve(tool, listOf("'literal with spaces' '' '\$HOME;*'"), selection)
      val session = sampleSession().copy(cliTool = tool, flags = flags, resumeToolSessionId = resumeId)
      val command = LaunchCommandBuilder.build(session.launchProfile())
      PreferenceBackedSessionStore(MapStore(backing)).upsertSession(session)
      val restored = PreferenceBackedSessionStore(MapStore(backing)).loadSessions().single()
      assertEquals(16, backing.getValue("session_records_v1").split('\t').size)
      assertEquals(session, restored)
      assertEquals(flags, restored.flags)
      assertEquals(command, LaunchCommandBuilder.build(restored.launchProfile()))
    } }
  }

  @Test
  fun laterGlobalDefaultEditsAndClearsNeverChangeSavedSessionFlagsOrReconnectCommand() {
    val cases = listOf(
      CliToolId.CLAUDE to GuidedLaunchSelection(model = "sonnet[1m]"),
      CliToolId.OPENCODE to GuidedLaunchSelection(model = "provider/model", agent = "build"),
      CliToolId.CODEX to GuidedLaunchSelection(model = "model", nativeProfile = "work", reasoningEffort = "high"),
    )
    cases.forEach { (tool, selection) -> listOf(null, "explicit-id").forEach { resumeId ->
      val defaultsBacking = mutableMapOf<String, String>()
      val defaults = PreferenceBackedLaunchDefaultsStore(MapStore(defaultsBacking))
      defaults.save(tool, selection)
      val snapshot = defaults.load(tool)
      val flags = GuidedLaunchFlags.resolve(tool, listOf("'literal with spaces'"), snapshot)
      val session = sampleSession().copy(cliTool = tool, flags = flags, resumeToolSessionId = resumeId)
      val command = LaunchCommandBuilder.build(session.launchProfile())
      val sessionBacking = mutableMapOf<String, String>()
      val sessions = PreferenceBackedSessionStore(MapStore(sessionBacking))
      sessions.upsertSession(session)
      val originalRecord = sessionBacking.toMap()
      defaults.save(tool, GuidedLaunchSelection(model = "later-model"))
      assertEquals(selection, snapshot)
      assertEquals(flags, session.flags)
      assertEquals(originalRecord, sessionBacking)
      assertEquals(command, LaunchCommandBuilder.build(sessions.loadSessions().single().launchProfile()))
      defaults.clear(tool)
      val restored = PreferenceBackedSessionStore(MapStore(sessionBacking)).loadSessions().single()
      assertEquals(session, restored)
      assertEquals(16, sessionBacking.getValue("session_records_v1").split('\t').size)
      assertEquals(command, LaunchCommandBuilder.build(restored.launchProfile()))
    } }
  }

  @Test
  fun nonemptyInvalidIdsSurviveStorageSoLaunchFailsClosed() {
    listOf(" ", "bad\nvalue", "bad\u0000value", "-id").forEach { id ->
      val store = PreferenceBackedSessionStore(MapStore())
      val session = sampleSession().copy(resumeToolSessionId = id)
      store.upsertSession(session)
      assertEquals(session, store.loadSessions().single())
    }
  }

  @Test
  fun preferenceOnlyUpdatePreservesEqualTimestampSessionOrder() {
    val backing = mutableMapOf<String, String>()
    val store = PreferenceBackedSessionStore(MapStore(backing))
    val first = sampleSession(id = "first")
    val second = sampleSession(id = "second")
    store.upsertSession(first)
    store.upsertSession(second)

    store.upsertSession(first.copy(notificationsMuted = true))

    val restored = PreferenceBackedSessionStore(MapStore(backing)).loadSessions()
    assertEquals(listOf(first.copy(notificationsMuted = true), second), restored)
  }

  @Test
  fun sortsByUpdatedDescendingAndDeletes() {
    val backing = mutableMapOf<String, String>()
    val store = PreferenceBackedSessionStore(MapStore(backing))
    val older = sampleSession(id = "a", updatedAt = 100)
    val newer = sampleSession(id = "b", updatedAt = 300)
    store.upsertSession(older)
    val afterUpsert = store.upsertSession(newer)
    assertEquals(listOf("b", "a"), afterUpsert.map { it.id })

    val afterDelete = store.deleteSession("b")
    assertEquals(listOf("a"), afterDelete.map { it.id })
  }

  private class MapStore(
    private val values: MutableMap<String, String> = mutableMapOf(),
  ) : StringPreferenceStore {
    override fun getString(key: String): String? = values[key]
    override fun putString(key: String, value: String) {
      values[key] = value
    }
    override fun removeString(key: String) {
      values.remove(key)
    }
  }
}
