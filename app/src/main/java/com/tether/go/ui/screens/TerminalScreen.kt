package com.tether.go.ui.screens

import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tether.go.session.SessionManager
import com.tether.go.session.SessionStatus
import com.tether.go.session.TerminalAppearance
import com.tether.go.session.toSessionStatus
import com.tether.go.ssh.SshTerminalPhase
import com.tether.go.ssh.SshTerminalState
import com.tether.go.ui.components.HostKeyPromptDialog
import com.tether.go.ui.components.StatusDot
import com.tether.go.ui.components.TetherTextField
import com.tether.go.ui.components.VoiceInputButton
import com.tether.go.ui.components.TetherTopBar
import com.tether.go.ui.components.TopBarTextAction
import com.tether.go.ui.theme.LocalTetherTheme
import org.connectbot.terminal.Terminal
import org.connectbot.terminal.TerminalDimensions
import org.connectbot.terminal.TerminalEmulator
import org.connectbot.terminal.VTermKey

private const val VTERM_MOD_CTRL = 4

/**
 * Full-screen terminal view for a phone-owned session: a top bar with the
 * session label, host-key/status line, and disconnect, the themed `termlib`
 * terminal (the same persistent emulator the session owns), and a mobile quick
 * bar. The dumb-pipe invariant holds — keystrokes and quick-bar keys go to the
 * remote PTY, raw output renders, nothing is parsed.
 */
@Composable
fun TerminalScreen(
  sessionId: String,
  manager: SessionManager,
  fontSize: Int,
  onBack: () -> Unit,
  onEnsureForeground: () -> Unit = {},
) {
  val theme = LocalTetherTheme.current
  val session = manager.sessionMetadata(sessionId)
  val sshState by manager.sshStateFor(sessionId).collectAsState()
  val terminal = manager.terminalFor(sessionId)
  var showIme by remember { mutableStateOf(false) }

  // This is the session the user is viewing → suppress its own pings while open.
  DisposableEffect(sessionId) {
    manager.setActiveSession(sessionId)
    onDispose { manager.setActiveSession(null) }
  }

  val appearance = TerminalAppearance(
    foreground = theme.terminalFg,
    background = theme.terminalBg,
    ansiPalette = theme.ansiPalette(),
  )

  // Keep the terminal palette in sync with the active theme.
  LaunchedEffect(terminal, theme.name) {
    terminal?.applyColorScheme(
      ansiColors = theme.ansiPalette(),
      defaultForeground = theme.terminalFg.toArgb(),
      defaultBackground = theme.terminalBg.toArgb(),
    )
  }

  val status = sshState.toSessionStatus()
  val connected = status == SessionStatus.Running || status == SessionStatus.Connecting

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(theme.terminalBg)
      .imePadding(),
  ) {
    TetherTopBar(
      title = session?.label ?: "Session",
      subtitle = subtitleFor(sshState, session?.endpointLabel),
      onBack = onBack,
      actions = {
        StatusDot(status = status, modifier = Modifier.padding(end = 6.dp))
        if (connected) {
          TopBarTextAction(label = "Disconnect", onClick = { manager.disconnect(sessionId) }, tint = theme.statusDead)
        }
      },
    )

    Box(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .background(theme.terminalBg),
    ) {
      if (terminal != null) {
        Terminal(
          terminalEmulator = terminal,
          modifier = Modifier.fillMaxSize(),
          typeface = Typeface.MONOSPACE,
          initialFontSize = fontSize.sp,
          minFontSize = 7.sp,
          maxFontSize = 24.sp,
          backgroundColor = theme.terminalBg,
          foregroundColor = theme.terminalFg,
          selectionBackgroundColor = theme.accent,
          selectionForegroundColor = theme.terminalBg,
          keyboardEnabled = true,
          showSoftKeyboard = showIme,
          onTerminalTap = { showIme = true },
          onImeVisibilityChanged = { showIme = it },
          forcedSize = null,
        )
      } else {
        DisconnectedPanel(
          message = sshState.error ?: "Not connected",
          canReconnect = manager.canReconnectSilently(sessionId),
          onReconnect = { onEnsureForeground(); manager.reconnect(sessionId, appearance, DEFAULT_SIZE) },
          onConnectWithPassword = { password ->
            onEnsureForeground(); manager.connectWithPassword(sessionId, password, appearance, DEFAULT_SIZE)
          },
        )
      }
    }

    if (terminal != null) {
      if (!connected) {
        ReconnectBar(
          canReconnect = manager.canReconnectSilently(sessionId),
          onReconnect = { onEnsureForeground(); manager.reconnect(sessionId, appearance, DEFAULT_SIZE) },
          onConnectWithPassword = { password ->
            onEnsureForeground(); manager.connectWithPassword(sessionId, password, appearance, DEFAULT_SIZE)
          },
        )
      }
      TerminalQuickBar(
        terminal = terminal,
        showIme = showIme,
        onToggleIme = { showIme = !showIme },
      )
    }
  }

  sshState.hostKeyPrompt?.let { prompt ->
    HostKeyPromptDialog(
      prompt = prompt,
      onAccept = { manager.respondToHostKeyPrompt(sessionId, prompt.id, true) },
      onReject = { manager.respondToHostKeyPrompt(sessionId, prompt.id, false) },
    )
  }
}

private val DEFAULT_SIZE = TerminalDimensions(rows = 32, columns = 96)

private fun subtitleFor(state: SshTerminalState, endpoint: String?): String {
  val hostKey = state.hostKey
  return when {
    state.phase == SshTerminalPhase.Connected && hostKey != null -> "${endpoint ?: ""} · ${hostKey.sha256Fingerprint}"
    state.message.isNotBlank() && state.phase != SshTerminalPhase.Connected -> state.message
    else -> endpoint ?: state.message
  }
}

@Composable
private fun DisconnectedPanel(
  message: String,
  canReconnect: Boolean,
  onReconnect: () -> Unit,
  onConnectWithPassword: (String) -> Unit,
) {
  val theme = LocalTetherTheme.current
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text(message, color = theme.textMuted, style = MaterialTheme.typography.bodyMedium)
    Spacer8()
    if (canReconnect) {
      Button(
        onClick = onReconnect,
        colors = ButtonDefaults.buttonColors(containerColor = theme.accent, contentColor = theme.btnPrimaryText),
      ) { Text("Reconnect", fontWeight = FontWeight.SemiBold) }
    } else {
      PasswordReconnect(onConnectWithPassword)
    }
  }
}

@Composable
private fun ReconnectBar(
  canReconnect: Boolean,
  onReconnect: () -> Unit,
  onConnectWithPassword: (String) -> Unit,
) {
  val theme = LocalTetherTheme.current
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(theme.bgHeader)
      .padding(horizontal = 12.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text("Disconnected", color = theme.textMuted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
    if (canReconnect) {
      FilledTonalButton(onClick = onReconnect) { Text("Reconnect") }
    } else {
      PasswordReconnect(onConnectWithPassword)
    }
  }
}

@Composable
private fun PasswordReconnect(onConnectWithPassword: (String) -> Unit) {
  var password by remember { mutableStateOf("") }
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    TetherTextField(password, { password = it }, "Password", isPassword = true, modifier = Modifier.sizeIn(maxWidth = 200.dp))
    FilledTonalButton(enabled = password.isNotEmpty(), onClick = { onConnectWithPassword(password) }) { Text("Connect") }
  }
}

@Composable
private fun TerminalQuickBar(
  terminal: TerminalEmulator,
  showIme: Boolean,
  onToggleIme: () -> Unit,
) {
  val theme = LocalTetherTheme.current
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(theme.bgHeader)
      .padding(horizontal = 8.dp, vertical = 8.dp)
      .horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    QuickKey("⌨", selected = showIme, onClick = onToggleIme)
    VoiceInputButton(onTranscript = { text -> terminal.typeText(text) })
    QuickKey("Esc") { terminal.dispatchKey(0, VTermKey.ESCAPE) }
    QuickKey("Tab") { terminal.dispatchKey(0, VTermKey.TAB) }
    QuickKey("Enter") { terminal.dispatchKey(0, VTermKey.ENTER) }
    QuickKey("↑") { terminal.dispatchKey(0, VTermKey.UP) }
    QuickKey("↓") { terminal.dispatchKey(0, VTermKey.DOWN) }
    QuickKey("←") { terminal.dispatchKey(0, VTermKey.LEFT) }
    QuickKey("→") { terminal.dispatchKey(0, VTermKey.RIGHT) }
    QuickKey("^C") { terminal.dispatchCharacter(VTERM_MOD_CTRL, 'c'.code) }
    QuickKey("^D") { terminal.dispatchCharacter(VTERM_MOD_CTRL, 'd'.code) }
    QuickKey("/") { terminal.dispatchCharacter(0, '/'.code) }
    QuickKey("Y") { terminal.dispatchCharacter(0, 'y'.code) }
    QuickKey("N") { terminal.dispatchCharacter(0, 'n'.code) }
  }
}

@Composable
private fun QuickKey(
  label: String,
  selected: Boolean = false,
  onClick: () -> Unit,
) {
  val theme = LocalTetherTheme.current
  FilledTonalButton(
    onClick = onClick,
    modifier = Modifier
      .height(40.dp)
      .sizeIn(minWidth = 48.dp),
    shape = RoundedCornerShape(8.dp),
    colors = ButtonDefaults.filledTonalButtonColors(
      containerColor = if (selected) theme.accent.copy(alpha = 0.2f) else theme.bgActive,
      contentColor = if (selected) theme.accent else theme.textPrimary,
    ),
    contentPadding = ButtonDefaults.TextButtonContentPadding,
  ) {
    Text(label, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Clip)
  }
}

/** Type [text] into the PTY one code point at a time (routes to SSH stdin). */
private fun TerminalEmulator.typeText(text: String) {
  var index = 0
  while (index < text.length) {
    val codePoint = text.codePointAt(index)
    dispatchCharacter(0, codePoint)
    index += Character.charCount(codePoint)
  }
}

@Composable
private fun Spacer8() {
  androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
}
