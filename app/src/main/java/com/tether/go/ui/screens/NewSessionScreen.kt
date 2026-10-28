package com.tether.go.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tether.go.cli.CliToolId
import com.tether.go.cli.CliToolRegistry
import com.tether.go.cli.GuidedLaunchFlags
import com.tether.go.cli.NativeResume
import com.tether.go.cli.SessionLaunchState
import com.tether.go.session.SessionDraft
import com.tether.go.settings.LaunchDefaultsStore
import com.tether.go.ssh.SecureStorageException
import com.tether.go.ssh.SshAuthMaterial
import com.tether.go.ssh.SshHostRecord
import com.tether.go.ssh.SshHostStore
import com.tether.go.ssh.SshPrivateKeyImportException
import com.tether.go.ssh.SshPrivateKeyStore
import com.tether.go.ssh.parseSshPort
import com.tether.go.ui.components.GuidedLaunchFields
import com.tether.go.ui.components.PrivateKeyImportDialog
import com.tether.go.ui.components.SectionHeader
import com.tether.go.ui.components.SelectableChip
import com.tether.go.ui.components.TetherTextField
import com.tether.go.ui.components.TetherTopBar
import com.tether.go.ui.theme.LocalTetherTheme
import java.util.UUID

private enum class AuthMode { Password, PrivateKey }

/**
 * New Session flow: pick or enter an SSH host, choose auth, the CLI tool, a
 * working directory, common launch flags, environment variables, and a label.
 * Builds a [SessionDraft] + [SshAuthMaterial] and hands them to [onStart].
 */
@Composable
fun NewSessionScreen(
  launchDefaultsStore: LaunchDefaultsStore,
  hostStore: SshHostStore,
  privateKeyStore: SshPrivateKeyStore,
  onCancel: () -> Unit,
  onStart: (SessionDraft, SshAuthMaterial) -> String?,
) {
  val theme = LocalTetherTheme.current

  var savedHosts by remember { mutableStateOf(hostStore.loadHosts()) }
  var savedKeys by remember {
    mutableStateOf(runCatching { privateKeyStore.loadPrivateKeys() }.getOrDefault(emptyList()))
  }

  var selectedHostId by remember { mutableStateOf<String?>(null) }
  var host by remember { mutableStateOf("") }
  var port by remember { mutableStateOf("22") }
  var username by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var authMode by remember { mutableStateOf(AuthMode.Password) }
  var selectedKeyId by remember { mutableStateOf<String?>(null) }

  var launchState by remember(launchDefaultsStore) {
    mutableStateOf(SessionLaunchState(CliToolId.CLAUDE).restoreDefaults(
      runCatching { launchDefaultsStore.load(CliToolId.CLAUDE) },
    ))
  }
  val cliTool = launchState.tool
  val guidedResume = launchState.guidedResume
  val resumeId = launchState.resumeId
  val guidedSelection = launchState.selection
  var customBinary by remember { mutableStateOf("") }
  var workingDir by remember { mutableStateOf("") }
  var selectedFlags by remember { mutableStateOf(setOf<String>()) }
  var extraArgs by remember { mutableStateOf("") }
  var envRows by remember { mutableStateOf(listOf<Pair<String, String>>()) }
  var label by remember { mutableStateOf("") }

  var showImport by remember { mutableStateOf(false) }
  var importError by remember { mutableStateOf<String?>(null) }
  var keyError by remember { mutableStateOf<String?>(null) }
  var starting by remember { mutableStateOf(false) }

  val portValid = parseSshPort(port) != null
  // Keep manual input separate: clearing controls restores it without rewriting.
  val rawFlags = selectedFlags.toList() + listOf(extraArgs).filter { it.isNotBlank() }
  val env = envRows.filter { it.first.isNotBlank() }.associate { it.first.trim() to it.second }
  fun resolveCurrentLaunch() = launchState.resolveLaunch(
    rawFlags = selectedFlags.toList() + listOf(extraArgs).filter { it.isNotBlank() },
    customBinary = customBinary,
    workingDir = workingDir,
    env = envRows.filter { it.first.isNotBlank() }.associate { it.first.trim() to it.second },
  )
  val preview = runCatching { resolveCurrentLaunch().command }
  val validationError = preview.exceptionOrNull()?.message
  val resumeIdInvalid = guidedResume && runCatching { NativeResume.validateSessionId(resumeId) }.isFailure
  var startError by remember(launchState, rawFlags, customBinary, workingDir, env) {
    mutableStateOf<String?>(null)
  }
  val authReady = when (authMode) {
    AuthMode.Password -> password.isNotEmpty()
    AuthMode.PrivateKey -> selectedKeyId != null
  }
  val canStart = host.isNotBlank() && username.isNotBlank() && portValid && authReady &&
    (cliTool != CliToolId.CUSTOM || customBinary.isNotBlank()) && preview.isSuccess && !starting

  fun applyHost(record: SshHostRecord) {
    selectedHostId = record.id
    host = record.host
    port = record.port.toString()
    username = record.username
    val keyId = record.privateKeyId
    if (keyId != null && savedKeys.any { it.id == keyId }) {
      selectedKeyId = keyId
      authMode = AuthMode.PrivateKey
    }
  }

  fun ensureHostSaved(parsedPort: Int): String {
    val existing = savedHosts.firstOrNull { it.id == selectedHostId }
      ?: savedHosts.firstOrNull {
        it.host.equals(host.trim(), ignoreCase = true) && it.port == parsedPort && it.username == username.trim()
      }
    val now = System.currentTimeMillis()
    val record = SshHostRecord(
      id = existing?.id ?: UUID.randomUUID().toString(),
      host = host.trim(),
      port = parsedPort,
      username = username.trim(),
      createdAtMillis = existing?.createdAtMillis ?: now,
      updatedAtMillis = now,
      privateKeyId = if (authMode == AuthMode.PrivateKey) selectedKeyId else existing?.privateKeyId,
    )
    savedHosts = hostStore.upsertHost(record)
    selectedHostId = record.id
    return record.id
  }

  fun start() {
    // Event callbacks can run before recomposition. Never launch a cached preview.
    if (starting || host.isBlank() || username.isBlank()) return
    val parsedPort = parseSshPort(port) ?: return
    if (authMode == AuthMode.Password && password.isEmpty()) return
    if (authMode == AuthMode.PrivateKey && selectedKeyId == null) return
    starting = true
    var launched = false
    try {
      // Validate the current launch snapshot before key reads, host saves or SSH.
      val launchProfile = runCatching { resolveCurrentLaunch().profile }.getOrElse {
        startError = it.message ?: "Session could not be launched"
        return
      }
      val auth = when (authMode) {
        AuthMode.Password -> SshAuthMaterial.Password(password)
        AuthMode.PrivateKey -> {
          val keyId = selectedKeyId ?: return
          val material = runCatching { privateKeyStore.loadPrivateKeyMaterial(keyId) }.getOrElse {
            keyError = "Private key storage could not be read"
            return
          } ?: run {
            keyError = "Selected private key is unavailable"
            return
          }
          SshAuthMaterial.PrivateKey(material.privateKeyData, material.passphrase)
        }
      }
      val hostId = ensureHostSaved(parsedPort)
      startError = onStart(
        SessionDraft(
          label = label,
          hostId = hostId,
          host = host.trim(),
          port = parsedPort,
          username = username.trim(),
          cliTool = launchProfile.cliTool,
          customBinary = launchProfile.customBinary,
          workingDir = launchProfile.workingDir,
          flags = launchProfile.flags,
          env = launchProfile.env,
          privateKeyId = if (authMode == AuthMode.PrivateKey) selectedKeyId else null,
          resumeToolSessionId = launchProfile.resumeToolSessionId,
        ),
        auth,
      )
      launched = startError == null
    } finally {
      if (!launched) starting = false
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(theme.bgPrimary)
      .imePadding(),
  ) {
    TetherTopBar(title = "New session", onBack = onCancel)

    Column(
      modifier = Modifier
        .weight(1f)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      // ── Connection ───────────────────────────────────────────────
      SectionHeader("Connection")
      if (savedHosts.isNotEmpty()) {
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          savedHosts.forEach { record ->
            SelectableChip(
              label = record.displayName,
              selected = record.id == selectedHostId,
              onClick = { applyHost(record) },
            )
          }
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TetherTextField(host, { host = it; selectedHostId = null }, "Host", modifier = Modifier.weight(2f))
        TetherTextField(port, { port = it }, "Port", modifier = Modifier.weight(1f), isError = !portValid, keyboardType = KeyboardType.Number)
      }
      TetherTextField(username, { username = it }, "Username", modifier = Modifier.fillMaxWidth())

      // ── Auth ─────────────────────────────────────────────────────
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectableChip("Password", authMode == AuthMode.Password, { authMode = AuthMode.Password })
        SelectableChip("Private key", authMode == AuthMode.PrivateKey, { authMode = AuthMode.PrivateKey })
      }
      if (authMode == AuthMode.Password) {
        TetherTextField(password, { password = it }, "Password", isPassword = true, modifier = Modifier.fillMaxWidth())
      } else {
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          SelectableChip("+ Import key", false, { importError = null; showImport = true })
          savedKeys.forEach { key ->
            SelectableChip(key.label, key.id == selectedKeyId, { selectedKeyId = key.id; keyError = null })
          }
        }
        keyError?.let { Text(it, color = theme.statusDead, style = MaterialTheme.typography.labelMedium) }
      }

      // ── Launch ───────────────────────────────────────────────────
      SectionHeader("CLI tool")
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        CliToolRegistry.all.forEach { def ->
          SelectableChip(def.displayName, def.id == cliTool, {
            if (launchState.tool != def.id) {
              launchState = launchState.changeTool(def.id, runCatching { launchDefaultsStore.load(def.id) })
              selectedFlags = emptySet()
            }
          })
        }
      }
      if (cliTool == CliToolId.CUSTOM) {
        TetherTextField(customBinary, { customBinary = it }, "Custom binary", modifier = Modifier.fillMaxWidth(), placeholder = "e.g. my-agent")
      }
      SectionHeader("Session launch")
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectableChip("Default / manual", !guidedResume, { if (launchState.tool == cliTool) launchState = launchState.copy(guidedResume = false) })
        SelectableChip(
          "Resume by ID", guidedResume, { if (launchState.tool == cliTool) launchState = launchState.copy(guidedResume = true) },
          enabled = CliToolRegistry.byId(cliTool).supportsSessionResume,
        )
      }
      if (guidedResume) {
        TetherTextField(
          resumeId, { if (launchState.tool == cliTool) launchState = launchState.copy(resumeId = it) }, "Full native session ID",
          modifier = Modifier.fillMaxWidth(), isError = resumeIdInvalid,
        )
        Text(
          "Uses this CLI's native resume command on the selected host. Tether Go does not look up history or verify that the ID exists.",
          color = theme.textMuted, style = MaterialTheme.typography.bodySmall,
        )
      }
      if (cliTool in GuidedLaunchFlags.supportedTools) {
        SectionHeader("Guided launch settings")
        Text(
          "Starts with this CLI's saved phone-local defaults. Edits here affect only this session.",
          color = theme.textMuted, style = MaterialTheme.typography.bodySmall,
        )
        GuidedLaunchFields(cliTool, guidedSelection) {
          if (launchState.tool == cliTool) launchState = launchState.copy(selection = it)
        }
        validationError?.let {
          Text(it, color = theme.statusDead, style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = {
          if (launchState.tool == cliTool) launchState = launchState.withoutDefaults()
        }) {
          Text("Use no defaults for this session")
        }
        TextButton(onClick = {
          if (launchState.tool == cliTool) {
            launchState = launchState.restoreDefaults(runCatching { launchDefaultsStore.load(cliTool) })
          }
        }) { Text("Restore saved defaults") }
        Text(
          "These actions change guided fields only. Extra arguments and the explicit resume ID stay unchanged.",
          color = theme.textMuted, style = MaterialTheme.typography.bodySmall,
        )
      }
      TetherTextField(workingDir, { workingDir = it }, "Working directory", modifier = Modifier.fillMaxWidth(), placeholder = "~ or /repo/project")

      val toolFlags = CliToolRegistry.byId(cliTool).commonFlags
      if (toolFlags.isNotEmpty()) {
        SectionHeader("Flags")
        toolFlags.forEach { flag ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Checkbox(
              checked = selectedFlags.contains(flag.flag),
              onCheckedChange = { checked ->
                if (launchState.tool == cliTool) {
                  selectedFlags = if (checked) selectedFlags + flag.flag else selectedFlags - flag.flag
                }
              },
              colors = CheckboxDefaults.colors(checkedColor = theme.accent, uncheckedColor = theme.border),
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(flag.label, color = theme.textPrimary, style = MaterialTheme.typography.bodyMedium)
              Text(flag.flag, color = theme.textMuted, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
          }
        }
      }
      TetherTextField(extraArgs, { extraArgs = it }, "Extra arguments", modifier = Modifier.fillMaxWidth(), placeholder = "--model opus")

      // ── Environment ──────────────────────────────────────────────
      SectionHeader("Environment variables")
      envRows.forEachIndexed { index, (k, v) ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
          TetherTextField(k, { nv -> envRows = envRows.toMutableList().also { it[index] = nv to v } }, "KEY", modifier = Modifier.weight(1f))
          TetherTextField(v, { nv -> envRows = envRows.toMutableList().also { it[index] = k to nv } }, "value", modifier = Modifier.weight(1f))
          Text("✕", color = theme.textMuted, fontSize = 18.sp, modifier = Modifier
            .padding(4.dp)
            .clickable { envRows = envRows.toMutableList().also { it.removeAt(index) } })
        }
      }
      Text(
        "+ Add variable",
        color = theme.accent,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
          .padding(vertical = 4.dp)
          .clickable { envRows = envRows + ("" to "") },
      )

      // ── Label + preview ──────────────────────────────────────────
      SectionHeader("Label")
      TetherTextField(label, { label = it }, "Session label", modifier = Modifier.fillMaxWidth(), placeholder = "Optional")

      SectionHeader("Launch command")
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(theme.terminalBg, RoundedCornerShape(8.dp))
          .padding(12.dp),
      ) {
        Text(
          preview.getOrNull() ?: "Fix the launch configuration to see a preview.",
          color = theme.terminalFg, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
        )
      }
      (validationError ?: startError)?.let { error ->
        Text(error, color = theme.statusDead, style = MaterialTheme.typography.bodySmall)
      }
      Spacer(Modifier.height(12.dp))
    }

    Button(
      onClick = { start() },
      enabled = canStart,
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      shape = RoundedCornerShape(10.dp),
      colors = ButtonDefaults.buttonColors(
        containerColor = theme.accent,
        contentColor = theme.btnPrimaryText,
        disabledContainerColor = theme.bgHeader,
        disabledContentColor = theme.textMuted,
      ),
    ) {
      Text("Start session", fontWeight = FontWeight.SemiBold)
    }
  }

  if (showImport) {
    PrivateKeyImportDialog(
      error = importError,
      onDismiss = { showImport = false; importError = null },
      onImport = { lbl, pem, pass ->
        runCatching { privateKeyStore.importPrivateKey(lbl, pem, pass) }
          .onSuccess { metadata ->
            savedKeys = runCatching { privateKeyStore.loadPrivateKeys() }.getOrDefault(savedKeys)
            selectedKeyId = metadata.id
            authMode = AuthMode.PrivateKey
            showImport = false
            importError = null
          }
          .onFailure { err ->
            importError = when (err) {
              is SshPrivateKeyImportException -> err.message
              is SecureStorageException -> "Private key storage could not be updated"
              else -> "Private key could not be imported"
            }
          }
      },
    )
  }
}
