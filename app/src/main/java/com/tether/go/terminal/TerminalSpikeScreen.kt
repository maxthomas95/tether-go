package com.tether.go.terminal

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tether.go.ssh.AndroidSshPrivateKeyStore
import com.tether.go.ssh.SecureStorageException
import com.tether.go.ssh.SshAuthMaterial
import com.tether.go.ssh.SshConnectionRequest
import com.tether.go.ssh.SshConnectionTarget
import com.tether.go.ssh.SshHostKeyPrompt
import com.tether.go.ssh.SshHostRecord
import com.tether.go.ssh.SshPrivateKeyImportException
import com.tether.go.ssh.SshPrivateKeyMetadata
import com.tether.go.ssh.SshTerminalSession
import com.tether.go.ssh.SshTerminalState
import com.tether.go.ssh.SharedPreferencesSshHostStore
import com.tether.go.ssh.parseSshPort
import org.connectbot.terminal.Terminal
import org.connectbot.terminal.TerminalDimensions
import org.connectbot.terminal.TerminalEmulator
import org.connectbot.terminal.TerminalEmulatorFactory
import org.connectbot.terminal.VTermKey
import java.util.UUID

private const val VTERM_MOD_CTRL = 4

private val AppBackground = Color(0xFF0B0D10)
private val TerminalBackground = Color(0xFF05080B)
private val TerminalForeground = Color(0xFFD9E7E2)
private val PanelBackground = Color(0xFF151A1F)
private val AccentCyan = Color(0xFF50D5E8)
private val AccentGreen = Color(0xFF9BE564)
private val AccentAmber = Color(0xFFFFC857)
private val AccentRed = Color(0xFFFF7A70)
private val MutedText = Color(0xFFA7B4AE)

private val TerminalAnsiPalette = intArrayOf(
  0xFF05080B.toInt(),
  0xFFE65F5C.toInt(),
  0xFF7AD66D.toInt(),
  0xFFEACB65.toInt(),
  0xFF58A6FF.toInt(),
  0xFFD07AF2.toInt(),
  0xFF50D5E8.toInt(),
  0xFFD9E7E2.toInt(),
  0xFF52605D.toInt(),
  0xFFFF7A70.toInt(),
  0xFF9BE564.toInt(),
  0xFFFFE08A.toInt(),
  0xFF8AB4FF.toInt(),
  0xFFE09BFF.toInt(),
  0xFF81F7E5.toInt(),
  0xFFFFFFFF.toInt(),
)

private enum class SshAuthMode {
  Password,
  PrivateKey,
}

@Composable
fun TerminalSpikeScreen() {
  val inputBuffer = remember { TerminalInputBuffer() }
  val coroutineScope = rememberCoroutineScope()
  val appContext = LocalContext.current.applicationContext
  val hostStore = remember(appContext) { SharedPreferencesSshHostStore(appContext) }
  val privateKeyStore = remember(appContext) { AndroidSshPrivateKeyStore(appContext) }
  val sshSession = remember(coroutineScope, hostStore) {
    SshTerminalSession(
      scope = coroutineScope,
      hostStore = hostStore,
    )
  }
  val connectionState by sshSession.state.collectAsState()

  var terminalSize by remember { mutableStateOf(TerminalDimensions(rows = 32, columns = 96)) }
  var showIme by remember { mutableStateOf(false) }
  var forcedSize by remember { mutableStateOf<Pair<Int, Int>?>(null) }
  var savedHosts by remember(hostStore) { mutableStateOf(hostStore.loadHosts()) }
  var selectedHostId by remember { mutableStateOf<String?>(null) }
  var privateKeyError by remember { mutableStateOf<String?>(null) }
  var savedPrivateKeys by remember(privateKeyStore) {
    mutableStateOf(
      runCatching { privateKeyStore.loadPrivateKeys() }
        .getOrElse {
          privateKeyError = "Private key storage could not be read"
          emptyList()
        },
    )
  }
  var selectedPrivateKeyId by remember { mutableStateOf<String?>(null) }
  var authMode by remember { mutableStateOf(SshAuthMode.Password) }
  var showPrivateKeyImport by remember { mutableStateOf(false) }
  var host by remember { mutableStateOf("") }
  var port by remember { mutableStateOf("22") }
  var username by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }

  val terminal = remember {
    TerminalEmulatorFactory.create(
      initialRows = terminalSize.rows,
      initialCols = terminalSize.columns,
      defaultForeground = TerminalForeground,
      defaultBackground = TerminalBackground,
      onKeyboardInput = { data ->
        inputBuffer.append(data)
        sshSession.sendInput(data)
      },
      onResize = {
        terminalSize = it
        sshSession.resize(it)
      },
    )
  }

  DisposableEffect(sshSession) {
    terminal.applyColorScheme(
      ansiColors = TerminalAnsiPalette,
      defaultForeground = TerminalForeground.toArgb(),
      defaultBackground = TerminalBackground.toArgb(),
    )

    onDispose {
      sshSession.disconnect()
    }
  }

  val inputSnapshot = inputBuffer.snapshot
  val selectedHost = savedHosts.firstOrNull { it.id == selectedHostId }

  fun persistCurrentHost(parsedPort: Int): SshHostRecord? {
    val trimmedHost = host.trim()
    val trimmedUsername = username.trim()
    if (trimmedHost.isBlank() || trimmedUsername.isBlank()) return null

    val now = System.currentTimeMillis()
    val existingHost = selectedHost ?: savedHosts.firstOrNull {
      it.host.equals(trimmedHost, ignoreCase = true) &&
        it.port == parsedPort &&
        it.username == trimmedUsername
    }
    val record = SshHostRecord(
      id = existingHost?.id ?: UUID.randomUUID().toString(),
      host = trimmedHost,
      port = parsedPort,
      username = trimmedUsername,
      createdAtMillis = existingHost?.createdAtMillis ?: now,
      updatedAtMillis = now,
      privateKeyId = selectedPrivateKeyId.takeIf { authMode == SshAuthMode.PrivateKey },
    )
    savedHosts = hostStore.upsertHost(record)
    selectedHostId = record.id
    return record
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(AppBackground)
      .systemBarsPadding(),
  ) {
    SshConnectionPanel(
      host = host,
      port = port,
      username = username,
      password = password,
      savedHosts = savedHosts,
      selectedHostId = selectedHostId,
      savedPrivateKeys = savedPrivateKeys,
      selectedPrivateKeyId = selectedPrivateKeyId,
      authMode = authMode,
      privateKeyError = privateKeyError,
      terminalSize = terminalSize,
      inputSnapshot = inputSnapshot,
      connectionState = connectionState,
      onHostChange = { host = it },
      onPortChange = { port = it },
      onUsernameChange = { username = it },
      onPasswordChange = { password = it },
      onAuthModeChange = {
        authMode = it
        privateKeyError = null
      },
      onImportPrivateKey = {
        privateKeyError = null
        showPrivateKeyImport = true
      },
      onSelectPrivateKey = {
        selectedPrivateKeyId = it
        authMode = SshAuthMode.PrivateKey
        privateKeyError = null
      },
      onDeletePrivateKey = {
        selectedPrivateKeyId?.let { keyId ->
          runCatching {
            savedPrivateKeys = privateKeyStore.deletePrivateKey(keyId)
          }.onSuccess {
            selectedPrivateKeyId = null
            privateKeyError = null
          }.onFailure {
            privateKeyError = "Private key storage could not be updated"
          }
        }
      },
      onSaveHost = {
        parseSshPort(port)?.let { persistCurrentHost(it) }
      },
      onSelectHost = { record ->
        selectedHostId = record.id
        host = record.host
        port = record.port.toString()
        username = record.username
        val savedKeyId = record.privateKeyId
        if (savedKeyId != null && savedPrivateKeys.any { it.id == savedKeyId }) {
          selectedPrivateKeyId = savedKeyId
          authMode = SshAuthMode.PrivateKey
          privateKeyError = null
        } else {
          selectedPrivateKeyId = null
          authMode = SshAuthMode.Password
          privateKeyError = if (savedKeyId == null) null else "Saved private key is unavailable"
        }
      },
      onDeleteHost = {
        selectedHostId?.let { hostId ->
          savedHosts = hostStore.deleteHost(hostId)
          selectedHostId = null
        }
      },
      onConnect = {
        val parsedPort = parseSshPort(port) ?: return@SshConnectionPanel
        val auth = when (authMode) {
          SshAuthMode.Password -> SshAuthMaterial.Password(password)
          SshAuthMode.PrivateKey -> {
            val keyId = selectedPrivateKeyId ?: return@SshConnectionPanel
            val keyMaterial = runCatching { privateKeyStore.loadPrivateKeyMaterial(keyId) }
              .getOrElse {
                privateKeyError = "Private key storage could not be read"
                return@SshConnectionPanel
              }
            if (keyMaterial == null) {
              privateKeyError = "Selected private key is unavailable"
              return@SshConnectionPanel
            }
            SshAuthMaterial.PrivateKey(
              privateKeyData = keyMaterial.privateKeyData,
              passphrase = keyMaterial.passphrase,
            )
          }
        }
        privateKeyError = null
        persistCurrentHost(parsedPort)
        sshSession.connect(
          request = SshConnectionRequest(
            target = SshConnectionTarget(
              host = host.trim(),
              port = parsedPort,
              username = username.trim(),
            ),
            auth = auth,
          ),
          terminalSize = terminalSize,
          output = terminal::writeInput,
        )
      },
      onDisconnect = {
        sshSession.disconnect()
      },
    )

    if (showPrivateKeyImport) {
      PrivateKeyImportDialog(
        error = privateKeyError,
        onDismiss = {
          privateKeyError = null
          showPrivateKeyImport = false
        },
        onImport = { label, privateKeyData, passphrase ->
          runCatching {
            privateKeyStore.importPrivateKey(
              label = label,
              privateKeyData = privateKeyData,
              passphrase = passphrase,
            )
          }.onSuccess { metadata ->
            val refreshedKeys = runCatching { privateKeyStore.loadPrivateKeys() }
              .getOrElse {
                privateKeyError = "Private key storage could not be read"
                return@onSuccess
              }
            savedPrivateKeys = refreshedKeys
            selectedPrivateKeyId = metadata.id
            authMode = SshAuthMode.PrivateKey
            privateKeyError = null
            showPrivateKeyImport = false
          }.onFailure { error ->
            privateKeyError = when (error) {
              is SshPrivateKeyImportException -> error.message
              is SecureStorageException -> "Private key storage could not be updated"
              else -> "Private key could not be imported"
            }
          }
        },
      )
    }

    connectionState.hostKeyPrompt?.let { prompt ->
      HostKeyPromptDialog(
        prompt = prompt,
        onAccept = { sshSession.respondToHostKeyPrompt(prompt.id, accepted = true) },
        onReject = { sshSession.respondToHostKeyPrompt(prompt.id, accepted = false) },
      )
    }

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .background(TerminalBackground),
    ) {
      Terminal(
        terminalEmulator = terminal,
        modifier = Modifier.fillMaxSize(),
        typeface = Typeface.MONOSPACE,
        initialFontSize = 12.sp,
        minFontSize = 7.sp,
        maxFontSize = 22.sp,
        backgroundColor = TerminalBackground,
        foregroundColor = TerminalForeground,
        selectionBackgroundColor = AccentCyan,
        selectionForegroundColor = TerminalBackground,
        keyboardEnabled = true,
        showSoftKeyboard = showIme,
        onTerminalTap = { showIme = true },
        onImeVisibilityChanged = { showIme = it },
        forcedSize = forcedSize,
      )
    }

    InputBufferStrip(
      inputSnapshot = inputSnapshot,
      onClearInput = inputBuffer::clear,
    )

    TerminalQuickBar(
      terminal = terminal,
      showIme = showIme,
      forcedSize = forcedSize,
      onShowImeChange = { showIme = it },
      onForcedSizeChange = { forcedSize = it },
    )
  }
}

@Composable
private fun SshConnectionPanel(
  host: String,
  port: String,
  username: String,
  password: String,
  savedHosts: List<SshHostRecord>,
  selectedHostId: String?,
  savedPrivateKeys: List<SshPrivateKeyMetadata>,
  selectedPrivateKeyId: String?,
  authMode: SshAuthMode,
  privateKeyError: String?,
  terminalSize: TerminalDimensions,
  inputSnapshot: TerminalInputSnapshot,
  connectionState: SshTerminalState,
  onHostChange: (String) -> Unit,
  onPortChange: (String) -> Unit,
  onUsernameChange: (String) -> Unit,
  onPasswordChange: (String) -> Unit,
  onAuthModeChange: (SshAuthMode) -> Unit,
  onImportPrivateKey: () -> Unit,
  onSelectPrivateKey: (String) -> Unit,
  onDeletePrivateKey: () -> Unit,
  onSaveHost: () -> Unit,
  onSelectHost: (SshHostRecord) -> Unit,
  onDeleteHost: () -> Unit,
  onConnect: () -> Unit,
  onDisconnect: () -> Unit,
) {
  val portInvalid = port.isNotBlank() && parseSshPort(port) == null
  val hasAuthMaterial = when (authMode) {
    SshAuthMode.Password -> password.isNotEmpty()
    SshAuthMode.PrivateKey -> selectedPrivateKeyId != null
  }
  val canConnect = host.isNotBlank() &&
    username.isNotBlank() &&
    hasAuthMaterial &&
    !portInvalid &&
    !connectionState.isConnected &&
    !connectionState.isBusy
  val fieldsEnabled = !connectionState.isBusy && !connectionState.isConnected
  val canSaveHost = host.isNotBlank() && username.isNotBlank() && !portInvalid && fieldsEnabled
  val canDeleteHost = selectedHostId != null && fieldsEnabled

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(PanelBackground)
      .padding(horizontal = 12.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(1.dp),
      ) {
        Text(
          text = "Tether Go",
          color = TerminalForeground,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "${terminalSize.columns}x${terminalSize.rows} | stdin ${inputSnapshot.totalBytes} B",
          color = MutedText,
          style = MaterialTheme.typography.labelMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      ConnectionActionButton(
        connectionState = connectionState,
        canConnect = canConnect,
        onConnect = onConnect,
        onDisconnect = onDisconnect,
      )
    }

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      SpikeTextField(
        value = host,
        onValueChange = onHostChange,
        label = "Host",
        enabled = fieldsEnabled,
        modifier = Modifier.width(190.dp),
      )
      SpikeTextField(
        value = port,
        onValueChange = onPortChange,
        label = "Port",
        enabled = fieldsEnabled,
        isError = portInvalid,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(88.dp),
      )
      SpikeTextField(
        value = username,
        onValueChange = onUsernameChange,
        label = "User",
        enabled = fieldsEnabled,
        modifier = Modifier.width(150.dp),
      )
      SpikeTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = "Password",
        enabled = fieldsEnabled && authMode == SshAuthMode.Password,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.width(170.dp),
      )
    }

    PrivateKeyControls(
      savedPrivateKeys = savedPrivateKeys,
      selectedPrivateKeyId = selectedPrivateKeyId,
      authMode = authMode,
      fieldsEnabled = fieldsEnabled,
      onAuthModeChange = onAuthModeChange,
      onImportPrivateKey = onImportPrivateKey,
      onSelectPrivateKey = onSelectPrivateKey,
      onDeletePrivateKey = onDeletePrivateKey,
    )

    HostRecordControls(
      savedHosts = savedHosts,
      selectedHostId = selectedHostId,
      canSaveHost = canSaveHost,
      canDeleteHost = canDeleteHost,
      onSaveHost = onSaveHost,
      onSelectHost = onSelectHost,
      onDeleteHost = onDeleteHost,
    )

    TerminalStatusLine(
      connectionState = connectionState,
      privateKeyError = privateKeyError,
    )
  }
}

@Composable
private fun HostRecordControls(
  savedHosts: List<SshHostRecord>,
  selectedHostId: String?,
  canSaveHost: Boolean,
  canDeleteHost: Boolean,
  onSaveHost: () -> Unit,
  onSelectHost: (SshHostRecord) -> Unit,
  onDeleteHost: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    FilledTonalButton(
      onClick = onSaveHost,
      enabled = canSaveHost,
      modifier = Modifier.height(34.dp),
      shape = RoundedCornerShape(6.dp),
      colors = ButtonDefaults.filledTonalButtonColors(
        containerColor = Color(0xFF1D3A3F),
        contentColor = AccentCyan,
        disabledContainerColor = Color(0xFF20262C),
        disabledContentColor = Color(0xFF64706C),
      ),
      contentPadding = ButtonDefaults.TextButtonContentPadding,
    ) {
      Text(
        text = if (selectedHostId == null) "Save host" else "Update host",
        fontSize = 12.sp,
      )
    }

    FilledTonalButton(
      onClick = onDeleteHost,
      enabled = canDeleteHost,
      modifier = Modifier.height(34.dp),
      shape = RoundedCornerShape(6.dp),
      colors = ButtonDefaults.filledTonalButtonColors(
        containerColor = Color(0xFF33201F),
        contentColor = AccentRed,
        disabledContainerColor = Color(0xFF20262C),
        disabledContentColor = Color(0xFF64706C),
      ),
      contentPadding = ButtonDefaults.TextButtonContentPadding,
    ) {
      Text(text = "Delete", fontSize = 12.sp)
    }

    savedHosts.forEach { record ->
      val selected = record.id == selectedHostId
      FilledTonalButton(
        onClick = { onSelectHost(record) },
        modifier = Modifier
          .height(34.dp)
          .sizeIn(minWidth = 112.dp),
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
          containerColor = if (selected) Color(0xFF253B2A) else Color(0xFF20262C),
          contentColor = if (selected) AccentGreen else TerminalForeground,
        ),
        contentPadding = ButtonDefaults.TextButtonContentPadding,
      ) {
        Text(
          text = record.displayName,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          fontSize = 12.sp,
        )
      }
    }
  }
}

@Composable
private fun PrivateKeyControls(
  savedPrivateKeys: List<SshPrivateKeyMetadata>,
  selectedPrivateKeyId: String?,
  authMode: SshAuthMode,
  fieldsEnabled: Boolean,
  onAuthModeChange: (SshAuthMode) -> Unit,
  onImportPrivateKey: () -> Unit,
  onSelectPrivateKey: (String) -> Unit,
  onDeletePrivateKey: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    AuthModeButton(
      label = "Password",
      selected = authMode == SshAuthMode.Password,
      enabled = fieldsEnabled,
      onClick = { onAuthModeChange(SshAuthMode.Password) },
    )
    AuthModeButton(
      label = "Private key",
      selected = authMode == SshAuthMode.PrivateKey,
      enabled = fieldsEnabled,
      onClick = { onAuthModeChange(SshAuthMode.PrivateKey) },
    )
    FilledTonalButton(
      onClick = onImportPrivateKey,
      enabled = fieldsEnabled,
      modifier = Modifier.height(34.dp),
      shape = RoundedCornerShape(6.dp),
      colors = ButtonDefaults.filledTonalButtonColors(
        containerColor = Color(0xFF1D3A3F),
        contentColor = AccentCyan,
        disabledContainerColor = Color(0xFF20262C),
        disabledContentColor = Color(0xFF64706C),
      ),
      contentPadding = ButtonDefaults.TextButtonContentPadding,
    ) {
      Text(text = "Import key", fontSize = 12.sp)
    }
    FilledTonalButton(
      onClick = onDeletePrivateKey,
      enabled = fieldsEnabled && selectedPrivateKeyId != null,
      modifier = Modifier.height(34.dp),
      shape = RoundedCornerShape(6.dp),
      colors = ButtonDefaults.filledTonalButtonColors(
        containerColor = Color(0xFF33201F),
        contentColor = AccentRed,
        disabledContainerColor = Color(0xFF20262C),
        disabledContentColor = Color(0xFF64706C),
      ),
      contentPadding = ButtonDefaults.TextButtonContentPadding,
    ) {
      Text(text = "Delete key", fontSize = 12.sp)
    }

    savedPrivateKeys.forEach { metadata ->
      val selected = metadata.id == selectedPrivateKeyId
      FilledTonalButton(
        onClick = { onSelectPrivateKey(metadata.id) },
        enabled = fieldsEnabled,
        modifier = Modifier
          .height(34.dp)
          .sizeIn(minWidth = 112.dp),
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
          containerColor = if (selected) Color(0xFF253B2A) else Color(0xFF20262C),
          contentColor = if (selected) AccentGreen else TerminalForeground,
          disabledContainerColor = Color(0xFF20262C),
          disabledContentColor = Color(0xFF64706C),
        ),
        contentPadding = ButtonDefaults.TextButtonContentPadding,
      ) {
        Text(
          text = metadata.label,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          fontSize = 12.sp,
        )
      }
    }
  }
}

@Composable
private fun AuthModeButton(
  label: String,
  selected: Boolean,
  enabled: Boolean,
  onClick: () -> Unit,
) {
  FilledTonalButton(
    onClick = onClick,
    enabled = enabled,
    modifier = Modifier.height(34.dp),
    shape = RoundedCornerShape(6.dp),
    colors = ButtonDefaults.filledTonalButtonColors(
      containerColor = if (selected) Color(0xFF253B2A) else Color(0xFF20262C),
      contentColor = if (selected) AccentGreen else TerminalForeground,
      disabledContainerColor = Color(0xFF20262C),
      disabledContentColor = Color(0xFF64706C),
    ),
    contentPadding = ButtonDefaults.TextButtonContentPadding,
  ) {
    Text(text = label, fontSize = 12.sp)
  }
}

@Composable
private fun PrivateKeyImportDialog(
  error: String?,
  onDismiss: () -> Unit,
  onImport: (label: String, privateKeyData: String, passphrase: String?) -> Unit,
) {
  var label by remember { mutableStateOf("") }
  var privateKeyData by remember { mutableStateOf("") }
  var passphrase by remember { mutableStateOf("") }
  val canImport = label.isNotBlank() && privateKeyData.isNotBlank()

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(text = "Import SSH Private Key")
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SpikeTextField(
          value = label,
          onValueChange = { label = it },
          label = "Label",
          enabled = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = privateKeyData,
          onValueChange = { privateKeyData = it },
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 130.dp, max = 220.dp),
          label = {
            Text(text = "Private key")
          },
          textStyle = MaterialTheme.typography.bodySmall.copy(
            color = TerminalForeground,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
          ),
          colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TerminalForeground,
            unfocusedTextColor = TerminalForeground,
            focusedBorderColor = AccentCyan,
            unfocusedBorderColor = Color(0xFF3A4249),
            cursorColor = AccentCyan,
            focusedLabelColor = AccentCyan,
            unfocusedLabelColor = MutedText,
          ),
        )
        SpikeTextField(
          value = passphrase,
          onValueChange = { passphrase = it },
          label = "Passphrase",
          enabled = true,
          visualTransformation = PasswordVisualTransformation(),
          modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
          Text(
            text = error,
            color = AccentRed,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    },
    confirmButton = {
      TextButton(
        enabled = canImport,
        onClick = {
          onImport(
            label,
            privateKeyData,
            passphrase.takeIf { it.isNotEmpty() },
          )
        },
      ) {
        Text(text = "Import")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(text = "Cancel")
      }
    },
  )
}

@Composable
private fun HostKeyPromptDialog(
  prompt: SshHostKeyPrompt,
  onAccept: () -> Unit,
  onReject: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = {},
    title = {
      Text(text = "Trust SSH Host Key")
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = prompt.endpoint.displayName)
        Text(text = "${prompt.type} ${prompt.sha256Fingerprint}")
        Text(text = "Accept only if this fingerprint matches the server you intended to reach.")
      }
    },
    confirmButton = {
      TextButton(onClick = onAccept) {
        Text(text = "Accept")
      }
    },
    dismissButton = {
      TextButton(onClick = onReject) {
        Text(text = "Reject")
      }
    },
  )
}

@Composable
private fun SpikeTextField(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  enabled: Boolean,
  modifier: Modifier = Modifier,
  isError: Boolean = false,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  visualTransformation: PasswordVisualTransformation? = null,
) {
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier.height(58.dp),
    enabled = enabled,
    singleLine = true,
    isError = isError,
    label = {
      Text(
        text = label,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    },
    keyboardOptions = keyboardOptions,
    visualTransformation = visualTransformation ?: androidx.compose.ui.text.input.VisualTransformation.None,
    textStyle = MaterialTheme.typography.bodyMedium.copy(
      color = TerminalForeground,
      fontSize = 14.sp,
    ),
    colors = OutlinedTextFieldDefaults.colors(
      focusedTextColor = TerminalForeground,
      unfocusedTextColor = TerminalForeground,
      disabledTextColor = MutedText,
      focusedBorderColor = AccentCyan,
      unfocusedBorderColor = Color(0xFF3A4249),
      disabledBorderColor = Color(0xFF293038),
      cursorColor = AccentCyan,
      focusedLabelColor = AccentCyan,
      unfocusedLabelColor = MutedText,
      disabledLabelColor = Color(0xFF64706C),
      errorBorderColor = AccentRed,
      errorLabelColor = AccentRed,
    ),
  )
}

@Composable
private fun ConnectionActionButton(
  connectionState: SshTerminalState,
  canConnect: Boolean,
  onConnect: () -> Unit,
  onDisconnect: () -> Unit,
) {
  val label = when {
    connectionState.isConnected -> "Disconnect"
    connectionState.isBusy -> "Cancel"
    else -> "Connect"
  }
  val enabled = connectionState.isBusy || connectionState.isConnected || canConnect

  FilledTonalButton(
    onClick = {
      if (connectionState.isConnected || connectionState.isBusy) {
        onDisconnect()
      } else {
        onConnect()
      }
    },
    enabled = enabled,
    modifier = Modifier
      .height(40.dp)
      .sizeIn(minWidth = 96.dp),
    shape = RoundedCornerShape(6.dp),
    colors = ButtonDefaults.filledTonalButtonColors(
      containerColor = if (connectionState.isConnected) Color(0xFF33201F) else Color(0xFF1D3A3F),
      contentColor = if (connectionState.isConnected) AccentRed else AccentCyan,
      disabledContainerColor = Color(0xFF20262C),
      disabledContentColor = Color(0xFF64706C),
    ),
  ) {
    Text(text = label, maxLines = 1, fontSize = 13.sp)
  }
}

@Composable
private fun TerminalStatusLine(
  connectionState: SshTerminalState,
  privateKeyError: String?,
) {
  val statusColor = when {
    privateKeyError != null -> AccentRed
    connectionState.error != null -> AccentRed
    connectionState.isConnected -> AccentGreen
    connectionState.isBusy -> AccentAmber
    else -> MutedText
  }
  val hostKey = connectionState.hostKey
  val text = when {
    hostKey != null -> "${connectionState.message} | ${hostKey.type} ${hostKey.sha256Fingerprint}"
    connectionState.targetLabel.isNotBlank() -> "${connectionState.targetLabel} | ${connectionState.message}"
    else -> connectionState.message
  }

  Text(
    text = privateKeyError ?: connectionState.error ?: text,
    modifier = Modifier.fillMaxWidth(),
    color = statusColor,
    style = MaterialTheme.typography.labelMedium,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
  )
}

@Composable
private fun InputBufferStrip(
  inputSnapshot: TerminalInputSnapshot,
  onClearInput: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(Color(0xFF101316))
      .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      text = "stdin",
      color = AccentCyan,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.SemiBold,
    )
    Text(
      text = inputSnapshot.preview.ifEmpty { "-" },
      modifier = Modifier.weight(1f),
      color = TerminalForeground,
      fontFamily = FontFamily.Monospace,
      fontSize = 12.sp,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    TextButton(
      onClick = onClearInput,
      modifier = Modifier.height(34.dp),
      colors = ButtonDefaults.textButtonColors(contentColor = MutedText),
    ) {
      Text(text = "Clear", fontSize = 12.sp, maxLines = 1)
    }
  }
}

@Composable
private fun TerminalQuickBar(
  terminal: TerminalEmulator,
  showIme: Boolean,
  forcedSize: Pair<Int, Int>?,
  onShowImeChange: (Boolean) -> Unit,
  onForcedSizeChange: (Pair<Int, Int>?) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(PanelBackground)
      .padding(horizontal = 10.dp, vertical = 8.dp)
      .horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    QuickBarButton(
      label = if (showIme) "Hide IME" else "IME",
      selected = showIme,
      onClick = { onShowImeChange(!showIme) },
    )
    QuickBarButton(
      label = "Auto",
      selected = forcedSize == null,
      onClick = { onForcedSizeChange(null) },
    )
    QuickBarButton(
      label = "80x24",
      selected = forcedSize == Pair(24, 80),
      onClick = { onForcedSizeChange(Pair(24, 80)) },
    )
    QuickBarButton(
      label = "132x40",
      selected = forcedSize == Pair(40, 132),
      onClick = { onForcedSizeChange(Pair(40, 132)) },
    )
    QuickBarButton(label = "Esc") { terminal.dispatchKey(0, VTermKey.ESCAPE) }
    QuickBarButton(label = "Tab") { terminal.dispatchKey(0, VTermKey.TAB) }
    QuickBarButton(label = "Enter") { terminal.dispatchKey(0, VTermKey.ENTER) }
    QuickBarButton(label = "Up") { terminal.dispatchKey(0, VTermKey.UP) }
    QuickBarButton(label = "Down") { terminal.dispatchKey(0, VTermKey.DOWN) }
    QuickBarButton(label = "Left") { terminal.dispatchKey(0, VTermKey.LEFT) }
    QuickBarButton(label = "Right") { terminal.dispatchKey(0, VTermKey.RIGHT) }
    QuickBarButton(label = "^C") { terminal.dispatchCharacter(VTERM_MOD_CTRL, 'c'.code) }
    QuickBarButton(label = "^D") { terminal.dispatchCharacter(VTERM_MOD_CTRL, 'd'.code) }
    QuickBarButton(label = "claude") { terminal.sendText("claude\n") }
    QuickBarButton(label = "codex") { terminal.sendText("codex\n") }
    QuickBarButton(label = "resume") { terminal.sendText("resume\n") }
  }
}

@Composable
private fun QuickBarButton(
  label: String,
  selected: Boolean = false,
  onClick: () -> Unit,
) {
  FilledTonalButton(
    onClick = onClick,
    modifier = Modifier
      .height(40.dp)
      .sizeIn(minWidth = 52.dp),
    shape = RoundedCornerShape(6.dp),
    colors = ButtonDefaults.filledTonalButtonColors(
      containerColor = if (selected) Color(0xFF1D3A3F) else Color(0xFF20262C),
      contentColor = if (selected) AccentCyan else TerminalForeground,
    ),
    contentPadding = ButtonDefaults.TextButtonContentPadding,
  ) {
    Text(
      text = label,
      maxLines = 1,
      overflow = TextOverflow.Clip,
      fontSize = 13.sp,
    )
  }
}

private fun TerminalEmulator.sendText(text: String) {
  text.forEachCodePoint { codePoint ->
    if (codePoint == '\n'.code || codePoint == '\r'.code) {
      dispatchKey(0, VTermKey.ENTER)
    } else {
      dispatchCharacter(0, codePoint)
    }
  }
}

private inline fun String.forEachCodePoint(action: (Int) -> Unit) {
  var index = 0
  while (index < length) {
    val codePoint = codePointAt(index)
    action(codePoint)
    index += Character.charCount(codePoint)
  }
}
