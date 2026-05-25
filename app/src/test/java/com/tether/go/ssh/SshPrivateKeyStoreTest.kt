package com.tether.go.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64

class SshPrivateKeyStoreTest {
  @Test
  fun encryptedStringPreferenceStoreDoesNotPersistPlaintextValues() {
    val backingStore = mutableMapOf<String, String>()
    val store = EncryptedStringPreferenceStore(
      backingStore = MapStringPreferenceStore(backingStore),
      cipher = testCipher(),
    )
    val secret = "$SAMPLE_PRIVATE_KEY\nphrase=fixture-value"

    store.putString("secret", secret)

    val storedValue = backingStore["secret"]
    assertNotNull(storedValue)
    assertFalse(storedValue!!.contains("BEGIN OPENSSH PRIVATE KEY"))
    assertFalse(storedValue.contains("fixture-value"))
    assertEquals(secret, store.getString("secret"))
  }

  @Test
  fun encryptedStringPreferenceStoreReportsCorruptValuesWithoutPlaintext() {
    val backingStore = mutableMapOf("secret" to "not-an-encrypted-value")
    val store = EncryptedStringPreferenceStore(
      backingStore = MapStringPreferenceStore(backingStore),
      cipher = testCipher(),
    )

    val error = assertThrows(SecureStorageException::class.java) {
      store.getString("secret")
    }

    assertEquals("Encrypted preference value could not be read", error.message)
    assertFalse(error.message.orEmpty().contains("not-an-encrypted-value"))
  }

  @Test
  fun importPrivateKeyStoresMetadataAndEncryptedMaterial() {
    val backingStore = mutableMapOf<String, String>()
    val store = privateKeyStore(backingStore)

    val metadata = store.importPrivateKey(
      label = "  Prod deploy key  ",
      privateKeyData = SAMPLE_PRIVATE_KEY,
      passphrase = "fixture-value",
    )

    assertEquals(
      SshPrivateKeyMetadata(
        id = "key-1",
        label = "Prod deploy key",
        keyFormat = "OPENSSH PRIVATE KEY",
        hasPassphrase = true,
        createdAtMillis = FIXED_CLOCK.millis(),
        updatedAtMillis = FIXED_CLOCK.millis(),
      ),
      metadata,
    )
    assertEquals(listOf(metadata), store.loadPrivateKeys())
    assertEquals(
      SshPrivateKeyMaterial(
        id = "key-1",
        privateKeyData = SAMPLE_PRIVATE_KEY,
        passphrase = "fixture-value",
      ),
      store.loadPrivateKeyMaterial("key-1"),
    )

    val rawStoredValues = backingStore.values.joinToString(separator = "\n")
    assertFalse(rawStoredValues.contains("BEGIN OPENSSH PRIVATE KEY"))
    assertFalse(rawStoredValues.contains("fixture-value"))
  }

  @Test
  fun importPrivateKeyRejectsInvalidPemInput() {
    val store = privateKeyStore()

    assertThrows(SshPrivateKeyImportException::class.java) {
      store.importPrivateKey(
        label = "bad",
        privateKeyData = "not a key",
        passphrase = null,
      )
    }
    assertThrows(SshPrivateKeyImportException::class.java) {
      store.importPrivateKey(
        label = "bad",
        privateKeyData = """
          -----BEGIN OPENSSH PRIVATE KEY-----
          %%%%
          -----END OPENSSH PRIVATE KEY-----
        """.trimIndent(),
        passphrase = null,
      )
    }
  }

  @Test
  fun deletePrivateKeyRemovesMaterialAndMetadata() {
    val backingStore = mutableMapOf<String, String>()
    val store = privateKeyStore(backingStore)
    store.importPrivateKey(
      label = "prod",
      privateKeyData = SAMPLE_PRIVATE_KEY,
      passphrase = null,
    )

    assertTrue(store.deletePrivateKey("key-1").isEmpty())

    assertTrue(store.loadPrivateKeys().isEmpty())
    assertNull(store.loadPrivateKeyMaterial("key-1"))
  }

  @Test
  fun hostRecordsPersistOnlyPrivateKeySelectionReference() {
    val backingStore = mutableMapOf<String, String>()
    val hostStore = PreferenceBackedSshHostStore(MapStringPreferenceStore(backingStore))
    val hostRecord = SshHostRecord(
      id = "host-1",
      host = "example.com",
      port = 22,
      username = "max",
      createdAtMillis = 100,
      updatedAtMillis = 200,
      privateKeyId = "key-1",
    )

    hostStore.upsertHost(hostRecord)

    assertEquals(listOf(hostRecord), hostStore.loadHosts())
    val rawHostValues = backingStore.values.joinToString(separator = "\n")
    assertFalse(rawHostValues.contains("BEGIN OPENSSH PRIVATE KEY"))
    assertFalse(rawHostValues.contains("passphrase"))
  }

  @Test
  fun corruptedPrivateKeyMetadataFailsClosed() {
    val backingStore = mutableMapOf<String, String>()
    val store = privateKeyStore(backingStore)
    store.importPrivateKey(
      label = "prod",
      privateKeyData = SAMPLE_PRIVATE_KEY,
      passphrase = null,
    )
    backingStore["private_key_metadata_v1"] = "corrupt"

    assertThrows(SecureStorageException::class.java) {
      store.loadPrivateKeys()
    }
  }

  private fun privateKeyStore(
    backingStore: MutableMap<String, String> = mutableMapOf(),
  ): PreferenceBackedSshPrivateKeyStore =
    PreferenceBackedSshPrivateKeyStore(
      preferences = EncryptedStringPreferenceStore(
        backingStore = MapStringPreferenceStore(backingStore),
        cipher = testCipher(),
      ),
      clock = FIXED_CLOCK,
      idGenerator = { "key-1" },
    )

  private fun testCipher(): StringCipher =
    TestStringCipher()

  private class TestStringCipher : StringCipher {
    override fun encrypt(plaintext: String): String {
      val reversed = plaintext.reversed().toByteArray(StandardCharsets.UTF_8)
      return "test:${Base64.getUrlEncoder().withoutPadding().encodeToString(reversed)}"
    }

    override fun decrypt(ciphertext: String): String {
      require(ciphertext.startsWith("test:")) { "Unsupported test cipher format" }
      val encoded = ciphertext.removePrefix("test:")
      val decoded = Base64.getUrlDecoder().decode(encoded)
      return String(decoded, StandardCharsets.UTF_8).reversed()
    }
  }

  private class MapStringPreferenceStore(
    private val values: MutableMap<String, String>,
  ) : StringPreferenceStore {
    override fun getString(key: String): String? = values[key]

    override fun putString(key: String, value: String) {
      values[key] = value
    }

    override fun removeString(key: String) {
      values.remove(key)
    }
  }

  private companion object {
    val FIXED_CLOCK: Clock = Clock.fixed(Instant.ofEpochMilli(1_800), ZoneOffset.UTC)
    val SAMPLE_PRIVATE_KEY = """
      -----BEGIN OPENSSH PRIVATE KEY-----
      AQIDBA==
      -----END OPENSSH PRIVATE KEY-----
    """.trimIndent()
  }
}
