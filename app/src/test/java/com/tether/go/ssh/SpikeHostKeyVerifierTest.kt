package com.tether.go.ssh

import kotlinx.coroutines.runBlocking
import org.connectbot.sshlib.PublicKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpikeHostKeyVerifierTest {
  @Test
  fun verifierAcceptsFirstHostKeyAndReportsFingerprint() = runBlocking {
    var summary: SshHostKeySummary? = null
    val verifier = SpikeHostKeyVerifier { summary = it }

    assertTrue(verifier.verify(TEST_KEY))

    assertEquals("ssh-ed25519", summary?.type)
    assertTrue(summary?.sha256Fingerprint?.startsWith("SHA256:") == true)
  }

  @Test
  fun verifierRejectsChangedHostKeyWithinConnection() = runBlocking {
    val verifier = SpikeHostKeyVerifier()

    assertTrue(verifier.verify(TEST_KEY))
    assertFalse(verifier.verify(CHANGED_TEST_KEY))
  }

  @Test
  fun parseSshPortAcceptsOnlyTcpPortRange() {
    assertEquals(22, parseSshPort("22"))
    assertEquals(65535, parseSshPort("65535"))
    assertEquals(null, parseSshPort("0"))
    assertEquals(null, parseSshPort("65536"))
    assertEquals(null, parseSshPort("not-a-port"))
  }

  private companion object {
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
