package com.tether.go.session

import androidx.compose.ui.graphics.Color
import com.tether.go.cli.LaunchCommandBuilder
import com.tether.go.cli.CliToolId
import com.tether.go.ssh.SshAuthMaterial
import org.connectbot.terminal.TerminalDimensions
import com.tether.go.ssh.PreferenceBackedSshHostStore
import com.tether.go.ssh.PreferenceBackedSshPrivateKeyStore
import com.tether.go.ssh.StringPreferenceStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionManagerTest {
  @Test
  fun mutingPersistsAndPublishesOnlyTheTargetSessionWithoutChangingItsRuntime() = runTest {
    val first = sampleSession("first")
    val second = sampleSession("second")
    val store = RecordingSessionStore(listOf(first, second))
    val manager = SessionManager(
      backgroundScope,
      PreferenceBackedSshHostStore(MapStore()),
      PreferenceBackedSshPrivateKeyStore(MapStore()),
      store,
      clock = { 300L },
    )
    val notifier = RecordingNotifier()
    manager.notifier = notifier
    val sshState = manager.sshStateFor(first.id)

    manager.setMuted(first.id, true)

    val expected = first.copy(notificationsMuted = true)
    assertTrue(manager.isMuted(first.id))
    assertFalse(manager.isMuted(second.id))
    assertEquals(expected, manager.sessionMetadata(first.id))
    assertEquals(second, manager.sessionMetadata(second.id))
    assertEquals(listOf(expected, second), manager.sessions.value.map { it.session })
    assertEquals(listOf(expected, second), store.loadSessions())
    assertEquals(listOf(first.id), notifier.canceled)
    assertSame(sshState, manager.sshStateFor(first.id))
    assertEquals(SessionStatus.Disconnected, manager.sessions.value.first().status)
    assertEquals(0, manager.connectedCount())
    assertEquals(emptyList<Session>(), notifier.notified)
  }

  @Test
  fun restoredMuteCanBeUnmutedAndRemainsUnmutedAfterAnotherManagerLoads() = runTest {
    val session = sampleSession("first").copy(notificationsMuted = true, resumeToolSessionId = "native-id")
    val store = RecordingSessionStore(listOf(session))
    val hostStore = PreferenceBackedSshHostStore(MapStore())
    val privateKeyStore = PreferenceBackedSshPrivateKeyStore(MapStore())
    val manager = SessionManager(backgroundScope, hostStore, privateKeyStore, store, clock = { 400L })
    val notifier = RecordingNotifier()
    manager.notifier = notifier
    assertTrue(manager.isMuted(session.id))
    assertTrue(manager.sessions.value.single().session.notificationsMuted)

    manager.setMuted(session.id, false)

    assertFalse(manager.isMuted(session.id))
    assertFalse(manager.sessions.value.single().session.notificationsMuted)
    assertEquals(session.updatedAtMillis, manager.sessionMetadata(session.id)?.updatedAtMillis)
    assertEquals(emptyList<String>(), notifier.canceled)
    assertEquals(emptyList<Session>(), notifier.notified)
    val restored = SessionManager(backgroundScope, hostStore, privateKeyStore, store)
    assertFalse(restored.isMuted(session.id))
  }

  @Test
  fun repeatedMuteClearsAnAlertWithoutRewritingMetadataAndUnknownIdsAreIgnored() = runTest {
    val session = sampleSession("first").copy(notificationsMuted = true, resumeToolSessionId = "native-id")
    val store = RecordingSessionStore(listOf(session))
    val manager = SessionManager(
      backgroundScope,
      PreferenceBackedSshHostStore(MapStore()),
      PreferenceBackedSshPrivateKeyStore(MapStore()),
      store,
    )
    val notifier = RecordingNotifier()
    manager.notifier = notifier

    manager.setMuted(session.id, true)
    manager.setMuted("missing", true)
    manager.setMuted("missing", false)

    assertEquals(listOf(session.id), notifier.canceled)
    assertEquals(0, store.upserts)
    assertEquals(session, manager.sessionMetadata(session.id))
    assertFalse(manager.isMuted("missing"))
    assertEquals(listOf(session), manager.sessions.value.map { it.session })
  }

  @Test
  fun storageFailureDoesNotChangeRuntimeOrPublishedPreference() = runTest {
    val session = sampleSession("first")
    val store = RecordingSessionStore(listOf(session))
    val manager = SessionManager(
      backgroundScope,
      PreferenceBackedSshHostStore(MapStore()),
      PreferenceBackedSshPrivateKeyStore(MapStore()),
      store,
    )
    val notifier = RecordingNotifier()
    manager.notifier = notifier
    store.failUpserts = true

    assertThrows(IllegalStateException::class.java) { manager.setMuted(session.id, true) }

    assertFalse(manager.isMuted(session.id))
    assertEquals(session, manager.sessionMetadata(session.id))
    assertEquals(listOf(session), manager.sessions.value.map { it.session })
    assertEquals(listOf(session), store.loadSessions())
    assertEquals(emptyList<String>(), notifier.canceled)
  }

  @Test
  fun renamingPreservesTheSessionsPersistedMutePreference() = runTest {
    val session = sampleSession("first").copy(notificationsMuted = true, resumeToolSessionId = "native-id")
    val store = RecordingSessionStore(listOf(session))
    val manager = SessionManager(
      backgroundScope,
      PreferenceBackedSshHostStore(MapStore()),
      PreferenceBackedSshPrivateKeyStore(MapStore()),
      store,
    )

    manager.rename(session.id, "Renamed")

    assertTrue(manager.isMuted(session.id))
    assertTrue(store.loadSessions().single().notificationsMuted)
    assertEquals("Renamed", manager.sessions.value.single().session.label)
    assertEquals("native-id", manager.sessionMetadata(session.id)?.resumeToolSessionId)
    assertEquals("native-id", store.loadSessions().single().resumeToolSessionId)
  }

  @Test
  fun invalidCreationDoesNotPersistAllocateTerminalOrOpenTransport() = runTest {
    val store = RecordingSessionStore(emptyList())
    val manager = SessionManager(backgroundScope, PreferenceBackedSshHostStore(MapStore()),
      PreferenceBackedSshPrivateKeyStore(MapStore()), store)
    val sample = sampleSession("draft")
    val draft = SessionDraft(sample.label, sample.hostId, sample.host, sample.port, sample.username,
      sample.cliTool, sample.customBinary, sample.workingDir, sample.flags, sample.env, sample.privateKeyId,
      resumeToolSessionId = "")
    listOf(
      draft,
      draft.copy(resumeToolSessionId = "bad\nvalue"),
      draft.copy(resumeToolSessionId = "id", flags = listOf("--continue")),
      draft.copy(resumeToolSessionId = "id", flags = listOf("$(whoami)")),
      draft.copy(resumeToolSessionId = "id", cliTool = CliToolId.CUSTOM),
      draft.copy(resumeToolSessionId = null, env = mapOf("BAD NAME" to "value")),
    ).forEach { invalid ->
      val result = manager.createSession(invalid, SshAuthMaterial.Password("unused"), appearance(), size)
      assertTrue(result.isFailure)
      assertTrue(result.exceptionOrNull() is IllegalArgumentException)
      assertEquals(emptyList<SessionUiModel>(), manager.sessions.value)
      assertEquals(0, store.upserts)
      assertEquals(0, manager.connectedCount())
    }
  }

  @Test
  fun invalidPersistedReconnectPublishesErrorWithoutOpeningTransportOrChangingIntent() = runTest {
    listOf(
      sampleSession("blank").copy(resumeToolSessionId = " "),
      sampleSession("custom").copy(cliTool = CliToolId.CUSTOM, resumeToolSessionId = "id"),
      sampleSession("selector").copy(resumeToolSessionId = "id", flags = listOf("'--resume' old")),
      sampleSession("shell").copy(resumeToolSessionId = "id", flags = listOf("--model $" + "MODEL")),
      sampleSession("env").copy(env = mapOf("BAD NAME" to "value")),
    ).forEach { session ->
      val store = RecordingSessionStore(listOf(session))
      val manager = SessionManager(backgroundScope, PreferenceBackedSshHostStore(MapStore()),
        PreferenceBackedSshPrivateKeyStore(MapStore()), store)
      assertFalse(manager.reconnect(session.id, appearance(), size))
      // Password reconnect must follow the same preflight, not bypass it.
      manager.connectWithPassword(session.id, "unused", appearance(), size)
      testScheduler.runCurrent()
      assertEquals(SessionStatus.Error, manager.sessions.value.single().status)
      assertTrue(manager.sshStateFor(session.id).value.error.orEmpty().startsWith("Cannot launch session:"))
      assertNull(manager.terminalFor(session.id))
      assertEquals(0, manager.connectedCount())
      assertEquals(0, store.upserts)
      assertEquals(session, manager.sessionMetadata(session.id))
      assertEquals(session, store.loadSessions().single())
    }
  }

  @Test
  fun restoredResumeRetainsExplicitIdThroughReconnectAttemptRenameAndMute() = runTest {
    CliToolId.entries.filter { it != CliToolId.CUSTOM }.forEach { tool ->
      val session = sampleSession(tool.id).copy(cliTool = tool, resumeToolSessionId = "native-id")
      val store = RecordingSessionStore(listOf(session))
      val manager = SessionManager(backgroundScope, PreferenceBackedSshHostStore(MapStore()),
        PreferenceBackedSshPrivateKeyStore(MapStore()), store, clock = { 300L })
      // No stored credential in this fixture: preflight succeeds, auth stops it.
      assertFalse(manager.reconnect(session.id, appearance(), size))
      assertNull(manager.sshStateFor(session.id).value.error)
      manager.rename(session.id, "Renamed")
      manager.setMuted(session.id, true)
      val restored = store.loadSessions().single()
      assertEquals(session.copy(label = "Renamed", updatedAtMillis = 300, notificationsMuted = true), restored)
      val command = LaunchCommandBuilder.build(restored.launchProfile())
      assertTrue(command.contains("'native-id'"))
      assertFalse(command.contains("--last") || command.contains("--continue") || command.contains("--session-id"))
    }
  }

  private fun appearance() = TerminalAppearance(Color.White, Color.Black, IntArray(16))
  private val size = TerminalDimensions(rows = 24, columns = 80)

  private fun sampleSession(id: String) = Session(
    id = id,
    label = "Session $id",
    hostId = null,
    host = "example.test",
    port = 22,
    username = "user",
    cliTool = CliToolId.CLAUDE,
    customBinary = null,
    workingDir = null,
    flags = emptyList(),
    env = emptyMap(),
    privateKeyId = null,
    createdAtMillis = 100,
    updatedAtMillis = 200,
  )

  private class RecordingSessionStore(initial: List<Session>) : SessionStore {
    private val sessions = initial.associateByTo(linkedMapOf()) { it.id }
    var upserts: Int = 0
      private set
    var failUpserts: Boolean = false

    override fun loadSessions(): List<Session> = sessions.values.toList()

    override fun upsertSession(session: Session): List<Session> {
      check(!failUpserts) { "Storage unavailable" }
      upserts += 1
      sessions[session.id] = session
      return loadSessions()
    }

    override fun deleteSession(sessionId: String): List<Session> {
      sessions.remove(sessionId)
      return loadSessions()
    }
  }

  private class RecordingNotifier : SessionNotifier {
    val canceled = mutableListOf<String>()
    val notified = mutableListOf<Session>()

    override fun notifyWaiting(session: Session, isBell: Boolean) {
      notified += session
    }

    override fun cancel(sessionId: String) {
      canceled += sessionId
    }
  }

  private class MapStore : StringPreferenceStore {
    private val values = mutableMapOf<String, String>()
    override fun getString(key: String): String? = values[key]
    override fun putString(key: String, value: String) {
      values[key] = value
    }
    override fun removeString(key: String) {
      values.remove(key)
    }
  }
}
