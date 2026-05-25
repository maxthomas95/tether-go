package com.tether.go.ssh

import kotlinx.coroutines.runBlocking
import org.connectbot.sshlib.PublicKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class KnownHostsVerifierTest {
  @Test
  fun fingerprintUsesSshSha256FormatWithoutPadding() {
    assertEquals(
      "SHA256:n2SnR+G5fxMfq7a0Rylsm28CAeefs8U1bmx36JtqgGo",
      sshSha256Fingerprint(TEST_KEY.encoded),
    )
  }

  @Test
  fun storePersistsHostRecordsAndPinnedKnownHostKeys() {
    val backingStore = mutableMapOf<String, String>()
    val store = PreferenceBackedSshHostStore(MapStringPreferenceStore(backingStore))
    val endpoint = SshHostEndpoint(host = "Example.COM", port = 2222)
    val hostRecord = SshHostRecord(
      id = "host-1",
      host = endpoint.host,
      port = endpoint.port,
      username = "max",
      createdAtMillis = 100,
      updatedAtMillis = 200,
    )
    val pinnedHostKey = PinnedHostKey(
      endpoint = endpoint,
      keyType = TEST_KEY.type,
      publicKeyBase64 = publicKeyBlobBase64(TEST_KEY.encoded),
      sha256Fingerprint = sshSha256Fingerprint(TEST_KEY.encoded),
      acceptedAtMillis = 300,
    )

    store.upsertHost(hostRecord)
    store.pinHostKey(pinnedHostKey)

    val restoredStore = PreferenceBackedSshHostStore(MapStringPreferenceStore(backingStore))
    assertEquals(listOf(hostRecord), restoredStore.loadHosts())
    assertEquals(
      pinnedHostKey,
      restoredStore.findPinnedHostKey(SshHostEndpoint(host = "example.com", port = 2222)),
    )
  }

  @Test
  fun firstConnectAcceptancePromptsAndPinsHostKey() = runBlocking {
    val store = PreferenceBackedSshHostStore(MapStringPreferenceStore())
    val endpoint = SshHostEndpoint(host = "example.com", port = 22)
    var acceptedSummary: SshHostKeySummary? = null
    var prompt: SshHostKeyPrompt? = null
    val verifier = KnownHostsVerifier(
      endpoint = endpoint,
      hostStore = store,
      clock = FIXED_CLOCK,
      onHostKeyAccepted = { acceptedSummary = it },
      confirmUnknownHostKey = {
        prompt = it
        true
      },
    )

    assertTrue(verifier.verify(TEST_KEY))

    assertEquals(endpoint, prompt?.endpoint)
    assertEquals(TEST_KEY.type, prompt?.type)
    assertEquals(sshSha256Fingerprint(TEST_KEY.encoded), prompt?.sha256Fingerprint)
    assertEquals(TEST_KEY.type, acceptedSummary?.type)
    assertEquals(
      PinnedHostKey(
        endpoint = endpoint,
        keyType = TEST_KEY.type,
        publicKeyBase64 = publicKeyBlobBase64(TEST_KEY.encoded),
        sha256Fingerprint = sshSha256Fingerprint(TEST_KEY.encoded),
        acceptedAtMillis = FIXED_CLOCK.millis(),
      ),
      store.findPinnedHostKey(endpoint),
    )
  }

  @Test
  fun verifierRejectsPinnedHostKeyMismatchWithoutPrompting() = runBlocking {
    val store = PreferenceBackedSshHostStore(MapStringPreferenceStore())
    val endpoint = SshHostEndpoint(host = "example.com", port = 22)
    store.pinHostKey(
      PinnedHostKey(
        endpoint = endpoint,
        keyType = TEST_KEY.type,
        publicKeyBase64 = publicKeyBlobBase64(TEST_KEY.encoded),
        sha256Fingerprint = sshSha256Fingerprint(TEST_KEY.encoded),
        acceptedAtMillis = 100,
      ),
    )
    var prompted = false
    var mismatch: SshHostKeyMismatch? = null
    val verifier = KnownHostsVerifier(
      endpoint = endpoint,
      hostStore = store,
      onHostKeyMismatch = { mismatch = it },
      confirmUnknownHostKey = {
        prompted = true
        true
      },
    )

    assertFalse(verifier.verify(CHANGED_TEST_KEY))

    assertFalse(prompted)
    assertNotNull(mismatch)
    assertEquals(
      sshSha256Fingerprint(TEST_KEY.encoded),
      mismatch?.expected?.sha256Fingerprint,
    )
    assertEquals(
      sshSha256Fingerprint(CHANGED_TEST_KEY.encoded),
      mismatch?.presented?.sha256Fingerprint,
    )
  }

  @Test
  fun parseSshPortAcceptsOnlyTcpPortRange() {
    assertEquals(22, parseSshPort("22"))
    assertEquals(65535, parseSshPort("65535"))
    assertEquals(null, parseSshPort("0"))
    assertEquals(null, parseSshPort("65536"))
    assertEquals(null, parseSshPort("not-a-port"))
  }

  private class MapStringPreferenceStore(
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

  private companion object {
    val FIXED_CLOCK: Clock = Clock.fixed(Instant.ofEpochMilli(300), ZoneOffset.UTC)
    val TEST_KEY = PublicKey(
      type = "ssh-ed25519",
      encoded = byteArrayOf(1, 2, 3, 4),
    )
    val CHANGED_TEST_KEY = PublicKey(
      type = "ssh-ed25519",
      encoded = byteArrayOf(4, 3, 2, 1),
    )
  }
}
