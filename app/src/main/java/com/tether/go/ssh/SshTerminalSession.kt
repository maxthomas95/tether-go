package com.tether.go.ssh

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.connectbot.sshlib.AuthResult
import org.connectbot.sshlib.ConnectResult
import org.connectbot.sshlib.SshClient
import org.connectbot.sshlib.SshSession
import org.connectbot.terminal.TerminalDimensions

private const val SSH_TERMINAL_TYPE = "xterm-256color"
private const val DEFAULT_SSH_PORT = 22
private const val TAG = "TetherSsh"

data class SshConnectionTarget(
  val host: String,
  val port: Int = DEFAULT_SSH_PORT,
  val username: String,
) {
  val displayName: String
    get() = "$username@$host:$port"
}

sealed interface SshAuthMaterial {
  data class Password(val password: String) : SshAuthMaterial
  data class PrivateKey(
    val privateKeyData: String,
    val passphrase: String? = null,
  ) : SshAuthMaterial
}

data class SshConnectionRequest(
  val target: SshConnectionTarget,
  val auth: SshAuthMaterial,
)

enum class SshTerminalPhase {
  Disconnected,
  Connecting,
  Authenticating,
  OpeningPty,
  Connected,
}

data class SshTerminalState(
  val phase: SshTerminalPhase = SshTerminalPhase.Disconnected,
  val targetLabel: String = "",
  val message: String = "Disconnected",
  val hostKey: SshHostKeySummary? = null,
  val hostKeyPrompt: SshHostKeyPrompt? = null,
  val error: String? = null,
) {
  val isBusy: Boolean
    get() = phase == SshTerminalPhase.Connecting ||
      phase == SshTerminalPhase.Authenticating ||
      phase == SshTerminalPhase.OpeningPty

  val isConnected: Boolean
    get() = phase == SshTerminalPhase.Connected
}

class SshTerminalSession(
  private val scope: CoroutineScope,
  private val hostStore: SshHostStore,
) {
  private val _state = MutableStateFlow(SshTerminalState())
  val state: StateFlow<SshTerminalState> = _state.asStateFlow()

  @Volatile
  private var activeClient: SshClient? = null

  @Volatile
  private var activeSession: SshSession? = null

  @Volatile
  private var attemptCounter = 0

  @Volatile
  private var pendingHostKeyPrompt: PendingHostKeyPrompt? = null

  private var connectJob: Job? = null
  private var lastRemoteSize: Pair<Int, Int>? = null

  fun connect(
    request: SshConnectionRequest,
    terminalSize: TerminalDimensions,
    output: (ByteArray) -> Unit,
  ) {
    disconnect(message = "Starting new SSH connection")
    val attemptId = nextAttemptId()
    val normalizedSize = terminalSize.normalized()
    lastRemoteSize = normalizedSize.rows to normalizedSize.columns

    Log.i(TAG, "Connecting to ${request.target.displayName}")
    updateState(attemptId) {
      SshTerminalState(
        phase = SshTerminalPhase.Connecting,
        targetLabel = request.target.displayName,
        message = "Connecting to ${request.target.host}:${request.target.port}",
      )
    }

    connectJob = scope.launch(Dispatchers.IO) {
      var client: SshClient? = null
      var session: SshSession? = null
      var hostKeyFailureMessage: String? = null

      try {
        val endpoint = SshHostEndpoint(
          host = request.target.host,
          port = request.target.port,
        )
        val hostKeyVerifier = KnownHostsVerifier(
          endpoint = endpoint,
          hostStore = hostStore,
          onHostKeyAccepted = { hostKey ->
            updateState(attemptId) { current ->
              current.copy(
                hostKey = hostKey,
                hostKeyPrompt = null,
                error = null,
              )
            }
          },
          onHostKeyMismatch = { mismatch ->
            hostKeyFailureMessage = mismatch.toUserMessage()
            updateState(attemptId) { current ->
              current.copy(
                hostKey = mismatch.presented,
                hostKeyPrompt = null,
                message = "Host key mismatch",
                error = hostKeyFailureMessage,
              )
            }
          },
          confirmUnknownHostKey = { prompt ->
            val response = CompletableDeferred<Boolean>()
            pendingHostKeyPrompt = PendingHostKeyPrompt(
              id = prompt.id,
              response = response,
            )
            updateState(attemptId) { current ->
              current.copy(
                hostKey = SshHostKeySummary(
                  type = prompt.type,
                  sha256Fingerprint = prompt.sha256Fingerprint,
                ),
                hostKeyPrompt = prompt,
                message = "Confirm host key fingerprint",
                error = null,
              )
            }

            val accepted = response.await()
            if (!accepted) {
              hostKeyFailureMessage = "Host key was not accepted for ${prompt.endpoint.displayName}"
            }
            updateState(attemptId) { current ->
              if (current.hostKeyPrompt?.id == prompt.id) {
                current.copy(
                  hostKeyPrompt = null,
                  message = if (accepted) "Host key accepted" else "Host key rejected",
                )
              } else {
                current
              }
            }
            pendingHostKeyPrompt = null
            accepted
          },
        )

        client = SshClient(
          host = request.target.host,
          port = request.target.port,
          hostKeyVerifier = hostKeyVerifier,
        )
        activeClient = client

        when (val result = client.connect()) {
          ConnectResult.Success -> Unit
          else -> throw SshTerminalConnectException(hostKeyFailureMessage ?: result.toUserMessage())
        }

        updateState(attemptId) { current ->
          current.copy(
            phase = SshTerminalPhase.Authenticating,
            message = "Authenticating ${request.target.username}",
            error = null,
          )
        }

        when (val result = authenticate(client, request)) {
          AuthResult.Success -> Unit
          else -> throw SshTerminalConnectException(result.toUserMessage())
        }

        updateState(attemptId) { current ->
          current.copy(
            phase = SshTerminalPhase.OpeningPty,
            message = "Requesting $SSH_TERMINAL_TYPE PTY",
            error = null,
          )
        }

        session = client.openSession()
          ?: throw SshTerminalConnectException("SSH session channel could not be opened")

        val ptyAccepted = session.requestPty(
          terminalType = SSH_TERMINAL_TYPE,
          widthChars = normalizedSize.columns,
          heightRows = normalizedSize.rows,
          widthPixels = 0,
          heightPixels = 0,
        )
        if (!ptyAccepted) {
          throw SshTerminalConnectException("Remote host rejected the PTY request")
        }

        val shellAccepted = session.requestShell()
        if (!shellAccepted) {
          throw SshTerminalConnectException("Remote host rejected the shell request")
        }
        activeSession = session

        updateState(attemptId) { current ->
          current.copy(
            phase = SshTerminalPhase.Connected,
            message = "$SSH_TERMINAL_TYPE shell connected",
            error = null,
          )
        }

        val stdoutJob = this.launchStreamReader(session.stdout, output)
        val stderrJob = this.launchStreamReader(session.stderr, output)
        val disconnectJob = launch {
          client.disconnectedFlow.collect { cause ->
            val message = cause?.message?.let { "SSH transport dropped: $it" }
              ?: "SSH transport closed"
            Log.w(TAG, "$message (${request.target.displayName})", cause)
            updateState(attemptId) { current ->
              current.copy(
                phase = SshTerminalPhase.Disconnected,
                message = message,
                error = cause?.message,
              )
            }
          }
        }

        joinAll(stdoutJob, stderrJob)
        disconnectJob.cancel()
      } catch (error: CancellationException) {
        throw error
      } catch (error: Throwable) {
        val detail = error.message ?: error::class.java.simpleName
        Log.w(TAG, "SSH connection to ${request.target.displayName} failed: $detail", error)
        updateState(attemptId) { current ->
          current.copy(
            phase = SshTerminalPhase.Disconnected,
            message = "SSH connection failed",
            error = detail,
          )
        }
      } finally {
        if (activeSession === session) activeSession = null
        if (activeClient === client) activeClient = null
        withContext(NonCancellable) {
          session?.close()
          client?.disconnect()
        }
        updateState(attemptId) { current ->
          if (current.phase == SshTerminalPhase.Connected || current.isBusy) {
            current.copy(
              phase = SshTerminalPhase.Disconnected,
              message = "SSH session closed",
            )
          } else {
            current
          }
        }
      }
    }
  }

  fun sendInput(data: ByteArray) {
    if (data.isEmpty()) return
    val session = activeSession ?: return

    scope.launch(Dispatchers.IO) {
      runCatching {
        session.write(data)
      }.onFailure { error ->
        _state.update { current ->
          current.copy(
            phase = SshTerminalPhase.Disconnected,
            message = "SSH input write failed",
            error = error.message ?: error::class.java.simpleName,
          )
        }
      }
    }
  }

  fun resize(size: TerminalDimensions) {
    val normalizedSize = size.normalized()
    val remoteSize = normalizedSize.rows to normalizedSize.columns
    if (remoteSize == lastRemoteSize) return
    lastRemoteSize = remoteSize

    val session = activeSession ?: return
    scope.launch(Dispatchers.IO) {
      runCatching {
        session.resizeTerminal(
          widthChars = normalizedSize.columns,
          heightRows = normalizedSize.rows,
          widthPixels = 0,
          heightPixels = 0,
        )
      }
    }
  }

  fun disconnect(message: String = "Disconnected") {
    attemptCounter += 1
    pendingHostKeyPrompt?.response?.complete(false)
    pendingHostKeyPrompt = null
    connectJob?.cancel()
    connectJob = null

    val session = activeSession
    val client = activeClient
    activeSession = null
    activeClient = null
    lastRemoteSize = null

    scope.launch(Dispatchers.IO) {
      session?.close()
      client?.disconnect()
    }

    _state.value = SshTerminalState(message = message)
  }

  fun respondToHostKeyPrompt(promptId: Long, accepted: Boolean) {
    val pendingPrompt = pendingHostKeyPrompt ?: return
    if (pendingPrompt.id != promptId) return

    pendingHostKeyPrompt = null
    pendingPrompt.response.complete(accepted)
  }

  private fun nextAttemptId(): Int {
    attemptCounter += 1
    return attemptCounter
  }

  private fun updateState(
    attemptId: Int,
    transform: (SshTerminalState) -> SshTerminalState,
  ) {
    if (attemptCounter == attemptId) {
      _state.update(transform)
    }
  }

  private fun CoroutineScope.launchStreamReader(
    stream: ReceiveChannel<ByteArray>,
    output: (ByteArray) -> Unit,
  ): Job = launch(Dispatchers.IO) {
    for (chunk in stream) {
      if (chunk.isNotEmpty()) {
        withContext(Dispatchers.Main.immediate) {
          output(chunk)
        }
      }
    }
  }

  private suspend fun authenticate(
    client: SshClient,
    request: SshConnectionRequest,
  ): AuthResult = when (val auth = request.auth) {
    is SshAuthMaterial.Password -> client.authenticatePassword(
      username = request.target.username,
      password = auth.password,
    )
    is SshAuthMaterial.PrivateKey -> client.authenticatePublicKey(
      username = request.target.username,
      privateKeyData = auth.privateKeyData,
      passphrase = auth.passphrase,
    )
  }
}

fun parseSshPort(value: String): Int? =
  value.trim().toIntOrNull()?.takeIf { it in 1..65535 }

private fun TerminalDimensions.normalized(): TerminalDimensions =
  TerminalDimensions(
    rows = rows.coerceAtLeast(1),
    columns = columns.coerceAtLeast(1),
  )

private fun ConnectResult.toUserMessage(): String = when (this) {
  ConnectResult.Success -> "Connected"
  is ConnectResult.HostKeyRejected -> "Host key rejected: ${sshSha256Fingerprint(key.encoded)}"
  is ConnectResult.AlgorithmMismatch -> "SSH algorithm mismatch: $message"
  is ConnectResult.ProtocolError -> cause?.message ?: message
  is ConnectResult.TransportError -> cause.message ?: cause::class.java.simpleName
}

private fun AuthResult.toUserMessage(): String = when (this) {
  AuthResult.Success -> "Authenticated"
  is AuthResult.Failure -> "Authentication failed; server allows ${allowedMethods.sorted().joinToString()}"
  is AuthResult.Error -> cause?.message ?: message
}

private fun SshHostKeyMismatch.toUserMessage(): String =
  "Host key mismatch for ${endpoint.displayName}. Expected ${expected.sha256Fingerprint}, " +
    "got ${presented.sha256Fingerprint}. Delete the saved host before trusting a changed server key."

private class SshTerminalConnectException(message: String) : Exception(message)

private data class PendingHostKeyPrompt(
  val id: Long,
  val response: CompletableDeferred<Boolean>,
)
