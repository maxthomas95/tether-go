package com.tether.go.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SshPasswordStoreTest {
  @Test
  fun savesLoadsAndDeletesPerHost() {
    val secrets = MapStore()
    val store = PreferenceBackedSshPasswordStore(secrets, MapStore())

    assertFalse(store.hasPassword("h1"))
    store.savePassword("h1", "hunter2")
    store.savePassword("h2", "other")

    assertTrue(store.hasPassword("h1"))
    assertEquals("hunter2", store.loadPassword("h1"))
    store.deletePassword("h1")
    assertFalse(store.hasPassword("h1"))
    assertNull(store.loadPassword("h1"))
    assertEquals("other", store.loadPassword("h2"))
    assertFalse(secrets.values.containsValue("hunter2"))
  }

  @Test
  fun resolverPrefersKeyThenSavedPasswordAndToleratesStorageFailure() {
    val passwords = PreferenceBackedSshPasswordStore(MapStore(), MapStore())
    passwords.savePassword("h1", "pw")
    val keys = FakeKeyStore(mapOf("k1" to SshPrivateKeyMaterial("k1", "PEM", null)))
    val resolver = SshStoredAuthResolver(keys, passwords)

    assertEquals(SshAuthMaterial.PrivateKey("PEM", null), resolver.resolve("h1", "k1"))
    assertEquals(SshAuthMaterial.Password("pw"), resolver.resolve("h1", null))
    assertEquals(SshAuthMaterial.Password("pw"), resolver.resolve("h1", "missing-key"))
    assertNull(resolver.resolve("h2", null))
    assertNull(resolver.resolve(null, null))
    assertTrue(resolver.hasStoredAuth("h1", null))
    assertFalse(resolver.hasStoredAuth("h2", null))

    val locked = PreferenceBackedSshPasswordStore(ThrowingStore(), MapStore().apply {
      putString("saved_password_hosts_v1", "h1")
    })
    assertNull(SshStoredAuthResolver(keys, locked).resolve("h1", null))
  }

  @Test
  fun hostLabelRoundTripsAndOldRecordsStillLoad() {
    val backing = MapStore(reverse = false)
    val store = PreferenceBackedSshHostStore(backing)
    val record = SshHostRecord("h1", "10.0.10.65", 22, "max", 1, 2, label = "claude-vm")
    store.upsertHost(record)
    assertEquals(listOf(record), store.loadHosts())
    assertEquals("claude-vm", record.title)
    assertEquals("10.0.10.65", record.copy(label = null).title)

    // A 7-field record (pre-label) must still decode.
    backing.values.replaceAll { _, value -> value.substringBeforeLast('\t') }
    assertEquals(listOf(record.copy(label = null)), store.loadHosts())
  }

  /** Stands in for Keystore-backed storage; values are "encrypted" by reversal. */
  private class MapStore(private val reverse: Boolean = true) : StringPreferenceStore {
    val values: MutableMap<String, String> = mutableMapOf()
    override fun getString(key: String): String? = values[key]?.let { if (reverse) it.reversed() else it }
    override fun putString(key: String, value: String) {
      values[key] = if (reverse) value.reversed() else value
    }
    override fun removeString(key: String) {
      values.remove(key)
    }
  }

  private class ThrowingStore : StringPreferenceStore {
    override fun getString(key: String): String? = throw SecureStorageException("locked")
    override fun putString(key: String, value: String) = throw SecureStorageException("locked")
    override fun removeString(key: String) = Unit
  }

  private class FakeKeyStore(private val material: Map<String, SshPrivateKeyMaterial>) : SshPrivateKeyStore {
    override fun loadPrivateKeys(): List<SshPrivateKeyMetadata> = emptyList()
    override fun importPrivateKey(label: String, privateKeyData: String, passphrase: String?) =
      throw UnsupportedOperationException()
    override fun loadPrivateKeyMaterial(id: String): SshPrivateKeyMaterial? = material[id]
    override fun deletePrivateKey(id: String): List<SshPrivateKeyMetadata> = emptyList()
  }
}
