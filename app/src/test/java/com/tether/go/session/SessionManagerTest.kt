package com.tether.go.session

import com.tether.go.cli.CliToolId
import com.tether.go.ssh.PreferenceBackedSshHostStore
import com.tether.go.ssh.PreferenceBackedSshPrivateKeyStore
import com.tether.go.ssh.StringPreferenceStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
    val session = sampleSession("first").copy(notificationsMuted = true)
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
    val session = sampleSession("first").copy(notificationsMuted = true)
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
    val session = sampleSession("first").copy(notificationsMuted = true)
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
  }

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
