package com.tether.go.ssh

import org.connectbot.sshlib.HostKeyVerifier
import org.connectbot.sshlib.PublicKey
import java.security.MessageDigest
import java.time.Clock
import java.util.Base64

data class SshHostKeySummary(
  val type: String,
  val sha256Fingerprint: String,
)

data class SshHostKeyPrompt(
  val id: Long,
  val endpoint: SshHostEndpoint,
  val type: String,
  val sha256Fingerprint: String,
)

data class SshHostKeyMismatch(
  val endpoint: SshHostEndpoint,
  val expected: PinnedHostKey,
  val presented: SshHostKeySummary,
)

class KnownHostsVerifier(
  private val endpoint: SshHostEndpoint,
  private val hostStore: SshHostStore,
  private val clock: Clock = Clock.systemUTC(),
  private val onHostKeyAccepted: (SshHostKeySummary) -> Unit = {},
  private val onHostKeyMismatch: (SshHostKeyMismatch) -> Unit = {},
  private val confirmUnknownHostKey: suspend (SshHostKeyPrompt) -> Boolean,
) : HostKeyVerifier {
  override suspend fun verify(key: PublicKey): Boolean {
    val summary = key.toHostKeySummary()
    val publicKeyBase64 = publicKeyBlobBase64(key.encoded)
    val pinnedKey = hostStore.findPinnedHostKey(endpoint)

    if (pinnedKey == null) {
      val accepted = confirmUnknownHostKey(
        SshHostKeyPrompt(
          id = nextHostKeyPromptId(),
          endpoint = endpoint,
          type = summary.type,
          sha256Fingerprint = summary.sha256Fingerprint,
        ),
      )
      if (!accepted) return false

      hostStore.pinHostKey(
        PinnedHostKey(
          endpoint = endpoint,
          keyType = key.type,
          publicKeyBase64 = publicKeyBase64,
          sha256Fingerprint = summary.sha256Fingerprint,
          acceptedAtMillis = clock.millis(),
        ),
      )
      onHostKeyAccepted(summary)
      return true
    }

    if (pinnedKey.matches(key.type, publicKeyBase64)) {
      onHostKeyAccepted(summary)
      return true
    }

    onHostKeyMismatch(
      SshHostKeyMismatch(
        endpoint = endpoint,
        expected = pinnedKey,
        presented = summary,
      ),
    )
    return false
  }
}

internal fun PublicKey.toHostKeySummary(): SshHostKeySummary =
  SshHostKeySummary(
    type = type,
    sha256Fingerprint = sshSha256Fingerprint(encoded),
  )

internal fun sshSha256Fingerprint(publicKeyBlob: ByteArray): String {
  val digest = MessageDigest.getInstance("SHA-256").digest(publicKeyBlob)
  return "SHA256:${Base64.getEncoder().withoutPadding().encodeToString(digest)}"
}

private val hostKeyPromptId = java.util.concurrent.atomic.AtomicLong(0)

private fun nextHostKeyPromptId(): Long =
  hostKeyPromptId.incrementAndGet()
