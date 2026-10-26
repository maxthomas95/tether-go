package com.tether.go.ssh

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.connectbot.sshlib.AuthResult
import org.connectbot.sshlib.ConnectResult
import org.connectbot.sshlib.SshClient

private const val TAG = "TetherSsh"
private const val DEFAULT_EXEC_TIMEOUT_MS = 30_000L
private const val DEFAULT_MAX_OUTPUT_BYTES = 4 * 1024 * 1024
private const val MAX_STDERR_BYTES = 16 * 1024

/** Output of a non-interactive command run over an SSH exec channel. */
data class SshExecResult(
  val stdout: String,
  val stderr: String,
  /** True when stdout hit the byte cap and the rest was discarded. */
  val truncated: Boolean,
)

class SshCommandException(message: String) : Exception(message)

/**
 * Runs one non-interactive command on a host over its own short-lived SSH
 * connection: connect, verify the host key against the TOFU store, authenticate,
 * exec, collect output, close. Used for read-only side channels such as listing
 * Claude transcripts; it never touches a session's terminal stream.
 *
 * Unknown host keys are confirmed through [confirmUnknownHostKey], so the
 * caller can show the same trust prompt as a terminal connect. Mismatched keys
 * fail closed.
 */
class SshCommandRunner(
  private val hostStore: SshHostStore,
) {
  suspend fun run(
    target: SshConnectionTarget,
    auth: SshAuthMaterial,
    command: String,
    confirmUnknownHostKey: suspend (SshHostKeyPrompt) -> Boolean,
    maxOutputBytes: Int = DEFAULT_MAX_OUTPUT_BYTES,
    timeoutMillis: Long = DEFAULT_EXEC_TIMEOUT_MS,
  ): SshExecResult = withContext(Dispatchers.IO) {
    var hostKeyFailure: String? = null
    val client = SshClient(
      host = target.host,
      port = target.port,
      hostKeyVerifier = KnownHostsVerifier(
        endpoint = SshHostEndpoint(target.host, target.port),
        hostStore = hostStore,
        onHostKeyMismatch = { mismatch -> hostKeyFailure = mismatch.toUserMessage() },
        confirmUnknownHostKey = { prompt ->
          val accepted = confirmUnknownHostKey(prompt)
          if (!accepted) hostKeyFailure = "Host key was not accepted for ${prompt.endpoint.displayName}"
          accepted
        },
      ),
    )
    try {
      val connected = client.connect()
      if (connected != ConnectResult.Success) {
        Log.w(TAG, "exec connect to ${target.displayName} returned $connected")
        throw SshCommandException(hostKeyFailure ?: connected.toUserMessage())
      }
      val authResult = client.authenticate(target.username, auth)
      if (authResult != AuthResult.Success) throw SshCommandException(authResult.toUserMessage())
      withTimeout(timeoutMillis) { client.exec(command, maxOutputBytes) }
        ?: throw SshCommandException("Remote host rejected the command")
    } finally {
      withContext(NonCancellable) { client.disconnect() }
    }
  }
}

/**
 * Run [command] on a new exec channel of an already-authenticated [SshClient].
 * Returns null if the channel could not be opened or the exec was refused.
 * Stdout is capped at [maxOutputBytes]; stderr is kept short for diagnostics.
 */
internal suspend fun SshClient.exec(command: String, maxOutputBytes: Int): SshExecResult? {
  val session = openSession() ?: return null
  return try {
    if (!session.requestExec(command)) return null
    coroutineScope {
      val stderr = async { session.stderr.readCapped(MAX_STDERR_BYTES) }
      val (stdout, truncated) = session.stdout.readCapped(maxOutputBytes)
      // Stop waiting on the remote once we've stopped reading its output.
      if (truncated) stderr.cancel()
      SshExecResult(
        stdout = stdout.toString(Charsets.UTF_8),
        stderr = if (truncated) "" else stderr.await().first.toString(Charsets.UTF_8),
        truncated = truncated,
      )
    }
  } finally {
    session.close()
  }
}

/** Drain a channel up to [limit] bytes; the flag reports whether it overflowed. */
private suspend fun ReceiveChannel<ByteArray>.readCapped(limit: Int): Pair<ByteArray, Boolean> {
  val buffer = java.io.ByteArrayOutputStream()
  for (chunk in this) {
    val room = limit - buffer.size()
    if (chunk.size > room) {
      buffer.write(chunk, 0, room)
      return buffer.toByteArray() to true
    }
    buffer.write(chunk)
  }
  return buffer.toByteArray() to false
}
