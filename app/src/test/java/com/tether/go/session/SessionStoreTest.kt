package com.tether.go.session

import com.tether.go.cli.CliToolId
import com.tether.go.ssh.StringPreferenceStore
import org.junit.Assert.assertEquals
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
