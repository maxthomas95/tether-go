package com.tether.go.session

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.tether.go.cli.CliToolId
import com.tether.go.cli.LaunchCommandBuilder
import com.tether.go.ssh.SshAuthMaterial
import com.tether.go.ssh.SshConnectionRequest
import com.tether.go.ssh.SshConnectionTarget
import com.tether.go.ssh.SshHostKeyPrompt
import com.tether.go.ssh.SshHostStore
import com.tether.go.ssh.SshPrivateKeyStore
import com.tether.go.ssh.SshTerminalPhase
import com.tether.go.ssh.SshTerminalSession
import com.tether.go.ssh.SshTerminalState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.connectbot.terminal.TerminalDimensions
import org.connectbot.terminal.TerminalEmulator
import org.connectbot.terminal.TerminalEmulatorFactory
import java.util.UUID

/** A row in the session list: durable metadata plus live runtime status. */
data class SessionUiModel(
  val session: Session,
  val status: SessionStatus,
  val statusMessage: String,
  val hostKeyPrompt: SshHostKeyPrompt?,
)

/**
 * Terminal colors + initial geometry used to construct a session's emulator.
 * A plain class (not a data class): instances are always rebuilt from the
 * active theme and never compared, so value equality is neither needed nor
 * meaningful for the [ansiPalette] array member.
 */
class TerminalAppearance(
  val foreground: Color,
  val background: Color,
  val ansiPalette: IntArray,
  val initialRows: Int = 32,
  val initialCols: Int = 96,
)

/** Everything the New Session flow gathers before a session exists. */
data class SessionDraft(
  val label: String,
  val hostId: String?,
  val host: String,
  val port: Int,
  val username: String,
  val cliTool: CliToolId,
  val customBinary: String?,
  val workingDir: String?,
  val flags: List<String>,
  val env: Map<String, String>,
  val privateKeyId: String?,
)

/**
 * Owns every phone-owned session for the lifetime of the app process. Each
 * session gets its own [SshTerminalSession] transport and a persistent
 * `termlib` [TerminalEmulator] so its scrollback survives navigating away from
 * and back to the terminal. Sessions stay connected while the app is open and
 * are torn down when the manager's [scope] is cancelled (app intentionally
 * closed), matching the v0.1 phone-owned lifecycle.
 */
class SessionManager(
  private val scope: CoroutineScope,
  private val hostStore: SshHostStore,
  private val privateKeyStore: SshPrivateKeyStore,
  private val store: SessionStore,
  private val clock: () -> Long = { System.currentTimeMillis() },
) {
  private class Runtime(
    var session: Session,
    val sshSession: SshTerminalSession,
  ) {
    var terminal: TerminalEmulator? = null
    var launchSent: Boolean = false
    var collectJob: Job? = null
  }

  private val runtimes = LinkedHashMap<String, Runtime>()
  private val states = HashMap<String, SshTerminalState>()

  private val _sessions = MutableStateFlow<List<SessionUiModel>>(emptyList())
  val sessions: StateFlow<List<SessionUiModel>> = _sessions.asStateFlow()

  private val emptySshState: StateFlow<SshTerminalState> =
    MutableStateFlow(SshTerminalState()).asStateFlow()

  init {
    store.loadSessions().forEach { session ->
      addRuntime(session)
    }
    recompute()
  }

  fun sshStateFor(sessionId: String): StateFlow<SshTerminalState> =
    runtimes[sessionId]?.sshSession?.state ?: emptySshState

  fun terminalFor(sessionId: String): TerminalEmulator? = runtimes[sessionId]?.terminal

  fun sessionMetadata(sessionId: String): Session? = runtimes[sessionId]?.session

  /** Create, persist, and immediately connect + launch a new session. */
  fun createSession(
    draft: SessionDraft,
    auth: SshAuthMaterial,
    appearance: TerminalAppearance,
    terminalSize: TerminalDimensions,
  ): String {
    val now = clock()
    val session = Session(
      id = UUID.randomUUID().toString(),
      label = draft.label.trim().ifEmpty { defaultLabel(draft) },
      hostId = draft.hostId,
      host = draft.host,
      port = draft.port,
      username = draft.username,
      cliTool = draft.cliTool,
      customBinary = draft.customBinary,
      workingDir = draft.workingDir,
      flags = draft.flags,
      env = draft.env,
      privateKeyId = draft.privateKeyId,
      createdAtMillis = now,
      updatedAtMillis = now,
    )
    val runtime = addRuntime(session)
    store.upsertSession(session)
    connectRuntime(runtime, auth, appearance, terminalSize)
    recompute()
    return session.id
  }

  /** Reconnect an existing session using stored (private-key) auth if available. */
  fun reconnect(
    sessionId: String,
    appearance: TerminalAppearance,
    terminalSize: TerminalDimensions,
  ): Boolean {
    val runtime = runtimes[sessionId] ?: return false
    val auth = resolveStoredAuth(runtime.session) ?: return false
    connectRuntime(runtime, auth, appearance, terminalSize)
    return true
  }

  /** Whether a session can reconnect without re-prompting for a password. */
  fun canReconnectSilently(sessionId: String): Boolean =
    runtimes[sessionId]?.session?.let { resolveStoredAuth(it) != null } ?: false

  fun connectWithPassword(
    sessionId: String,
    password: String,
    appearance: TerminalAppearance,
    terminalSize: TerminalDimensions,
  ) {
    val runtime = runtimes[sessionId] ?: return
    connectRuntime(runtime, SshAuthMaterial.Password(password), appearance, terminalSize)
  }

  fun disconnect(sessionId: String) {
    runtimes[sessionId]?.sshSession?.disconnect(message = "Disconnected")
  }

  fun removeSession(sessionId: String) {
    val runtime = runtimes.remove(sessionId) ?: return
    runtime.collectJob?.cancel()
    runtime.sshSession.disconnect(message = "Session removed")
    states.remove(sessionId)
    store.deleteSession(sessionId)
    recompute()
  }

  fun rename(sessionId: String, label: String) {
    val runtime = runtimes[sessionId] ?: return
    val trimmed = label.trim().ifEmpty { return }
    runtime.session = runtime.session.copy(label = trimmed, updatedAtMillis = clock())
    store.upsertSession(runtime.session)
    recompute()
  }

  fun respondToHostKeyPrompt(sessionId: String, promptId: Long, accepted: Boolean) {
    runtimes[sessionId]?.sshSession?.respondToHostKeyPrompt(promptId, accepted)
  }

  fun resize(sessionId: String, size: TerminalDimensions) {
    runtimes[sessionId]?.sshSession?.resize(size)
  }

  // ── internals ──────────────────────────────────────────────────────────

  private fun addRuntime(session: Session): Runtime {
    val runtime = Runtime(session, SshTerminalSession(scope, hostStore))
    runtimes[session.id] = runtime
    runtime.collectJob = scope.launch {
      runtime.sshSession.state.collect { state ->
        states[session.id] = state
        if (state.phase == SshTerminalPhase.Connected && !runtime.launchSent) {
          runtime.launchSent = true
          val command = LaunchCommandBuilder.build(runtime.session.launchProfile())
          if (command.isNotBlank()) {
            runtime.sshSession.sendInput((command + "\n").toByteArray(Charsets.UTF_8))
          }
        }
        if (state.phase == SshTerminalPhase.Disconnected) {
          runtime.launchSent = false
        }
        recompute()
      }
    }
    return runtime
  }

  private fun connectRuntime(
    runtime: Runtime,
    auth: SshAuthMaterial,
    appearance: TerminalAppearance,
    terminalSize: TerminalDimensions,
  ) {
    val terminal = ensureTerminal(runtime, appearance)
    runtime.launchSent = false
    runtime.sshSession.connect(
      request = SshConnectionRequest(
        target = SshConnectionTarget(
          host = runtime.session.host,
          port = runtime.session.port,
          username = runtime.session.username,
        ),
        auth = auth,
      ),
      terminalSize = terminalSize,
      output = terminal::writeInput,
    )
  }

  private fun ensureTerminal(runtime: Runtime, appearance: TerminalAppearance): TerminalEmulator {
    runtime.terminal?.let { return it }
    val terminal = TerminalEmulatorFactory.create(
      initialRows = appearance.initialRows,
      initialCols = appearance.initialCols,
      defaultForeground = appearance.foreground,
      defaultBackground = appearance.background,
      onKeyboardInput = { data -> runtime.sshSession.sendInput(data) },
      onResize = { size -> runtime.sshSession.resize(size) },
    )
    terminal.applyColorScheme(
      ansiColors = appearance.ansiPalette,
      defaultForeground = appearance.foreground.toArgb(),
      defaultBackground = appearance.background.toArgb(),
    )
    runtime.terminal = terminal
    return terminal
  }

  private fun resolveStoredAuth(session: Session): SshAuthMaterial? {
    val keyId = session.privateKeyId ?: return null
    val material = runCatching { privateKeyStore.loadPrivateKeyMaterial(keyId) }.getOrNull() ?: return null
    return SshAuthMaterial.PrivateKey(
      privateKeyData = material.privateKeyData,
      passphrase = material.passphrase,
    )
  }

  private fun recompute() {
    _sessions.value = runtimes.values.map { runtime ->
      val state = states[runtime.session.id] ?: SshTerminalState()
      SessionUiModel(
        session = runtime.session,
        status = state.toSessionStatus(),
        statusMessage = state.message,
        hostKeyPrompt = state.hostKeyPrompt,
      )
    }
  }

  private fun defaultLabel(draft: SessionDraft): String {
    val dir = draft.workingDir?.trim()?.takeIf { it.isNotEmpty() }?.substringAfterLast('/')
    return dir ?: draft.host
  }
}

internal fun SshTerminalState.toSessionStatus(): SessionStatus = when (phase) {
  SshTerminalPhase.Disconnected -> if (error != null) SessionStatus.Error else SessionStatus.Disconnected
  SshTerminalPhase.Connecting,
  SshTerminalPhase.Authenticating,
  SshTerminalPhase.OpeningPty,
  -> SessionStatus.Connecting
  SshTerminalPhase.Connected -> SessionStatus.Running
}
