package com.tether.go.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tether.go.ssh.SshHostRecord
import com.tether.go.ssh.SshHostStore
import com.tether.go.ssh.SshPasswordStore
import com.tether.go.ssh.SshPrivateKeyStore
import com.tether.go.ssh.parseSshPort
import com.tether.go.ui.components.CheckboxRow
import com.tether.go.ui.components.PrivateKeyImportFlow
import com.tether.go.ui.components.SectionHeader
import com.tether.go.ui.components.SshAuthMode
import com.tether.go.ui.components.SshAuthSection
import com.tether.go.ui.components.TetherTextField
import com.tether.go.ui.components.TetherTopBar
import com.tether.go.ui.theme.LocalTetherTheme
import java.util.UUID

/**
 * Add or edit a saved SSH host: nickname, endpoint, and how to authenticate.
 * A password is only kept when "Remember" is checked, and then only
 * Keystore-encrypted. Saving hands the host id to [onSaved] so the caller can
 * go straight to the host's chats.
 */
@Composable
fun HostEditorScreen(
  hostId: String?,
  hostStore: SshHostStore,
  privateKeyStore: SshPrivateKeyStore,
  passwordStore: SshPasswordStore,
  onCancel: () -> Unit,
  onSaved: (String) -> Unit,
) {
  val theme = LocalTetherTheme.current
  val existing = remember(hostId) { hostStore.loadHosts().firstOrNull { it.id == hostId } }
  val hadPassword = remember(hostId) { hostId != null && passwordStore.hasPassword(hostId) }

  var keys by remember {
    mutableStateOf(runCatching { privateKeyStore.loadPrivateKeys() }.getOrDefault(emptyList()))
  }
  var label by remember { mutableStateOf(existing?.label.orEmpty()) }
  var host by remember { mutableStateOf(existing?.host.orEmpty()) }
  var port by remember { mutableStateOf((existing?.port ?: 22).toString()) }
  var username by remember { mutableStateOf(existing?.username.orEmpty()) }
  var authMode by remember {
    mutableStateOf(if (existing?.privateKeyId != null) SshAuthMode.PrivateKey else SshAuthMode.Password)
  }
  var selectedKeyId by remember { mutableStateOf(existing?.privateKeyId) }
  var password by remember { mutableStateOf("") }
  var rememberPassword by remember { mutableStateOf(true) }
  var forgetPassword by remember { mutableStateOf(false) }
  var showImport by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }

  val parsedPort = parseSshPort(port)
  val canSave = host.isNotBlank() && username.isNotBlank() && parsedPort != null &&
    (authMode == SshAuthMode.Password || selectedKeyId != null)

  fun save() {
    val validPort = parsedPort ?: return
    val now = System.currentTimeMillis()
    val record = SshHostRecord(
      id = existing?.id ?: UUID.randomUUID().toString(),
      host = host.trim(),
      port = validPort,
      username = username.trim(),
      createdAtMillis = existing?.createdAtMillis ?: now,
      updatedAtMillis = now,
      privateKeyId = if (authMode == SshAuthMode.PrivateKey) selectedKeyId else null,
      label = label.trim().takeIf { it.isNotEmpty() },
    )
    val stored = runCatching {
      when {
        authMode == SshAuthMode.PrivateKey || forgetPassword -> passwordStore.deletePassword(record.id)
        password.isNotEmpty() && rememberPassword -> passwordStore.savePassword(record.id, password)
        password.isNotEmpty() -> passwordStore.deletePassword(record.id)
      }
    }
    if (stored.isFailure) {
      error = "Password storage could not be updated"
      return
    }
    hostStore.upsertHost(record)
    onSaved(record.id)
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(theme.bgPrimary)
      .imePadding(),
  ) {
    TetherTopBar(
      title = if (existing == null) "Add host" else "Edit host",
      subtitle = existing?.displayName,
      onBack = onCancel,
    )

    Column(
      modifier = Modifier
        .weight(1f)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      SectionHeader("Host")
      TetherTextField(label, { label = it }, "Name", modifier = Modifier.fillMaxWidth(), placeholder = "e.g. claude-vm")
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TetherTextField(host, { host = it }, "Host or IP", modifier = Modifier.weight(2f), placeholder = "10.0.10.65")
        TetherTextField(
          port, { port = it }, "Port",
          modifier = Modifier.weight(1f),
          isError = parsedPort == null,
          keyboardType = KeyboardType.Number,
        )
      }
      TetherTextField(username, { username = it }, "Username", modifier = Modifier.fillMaxWidth())

      SectionHeader("Sign in with")
      SshAuthSection(
        mode = authMode,
        onModeChange = { authMode = it },
        password = password,
        onPasswordChange = { password = it; forgetPassword = false },
        keys = keys,
        selectedKeyId = selectedKeyId,
        onSelectKey = { selectedKeyId = it },
        onImportClick = { showImport = true },
        keyError = null,
        passwordLabel = if (hadPassword && !forgetPassword) "Password (saved — type to replace)" else "Password",
      )
      if (authMode == SshAuthMode.Password) {
        CheckboxRow(
          checked = rememberPassword,
          onCheckedChange = { rememberPassword = it },
          label = "Remember password on this phone",
          detail = "Encrypted with the Android Keystore. Otherwise you'll be asked each time.",
        )
        if (hadPassword && !forgetPassword) {
          Text(
            "Forget saved password",
            color = theme.statusDead,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
              .padding(vertical = 4.dp)
              .clickable { forgetPassword = true; password = "" },
          )
        }
      }
      Text(
        "The host key is checked on first connect: you'll be asked to trust its fingerprint.",
        color = theme.textMuted,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(top = 8.dp),
      )
      error?.let { Text(it, color = theme.statusDead, style = MaterialTheme.typography.labelMedium) }
    }

    Button(
      onClick = ::save,
      enabled = canSave,
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
      Text(if (existing == null) "Save & browse chats" else "Save", fontWeight = FontWeight.SemiBold)
    }
  }

  if (showImport) {
    PrivateKeyImportFlow(
      privateKeyStore = privateKeyStore,
      onDismiss = { showImport = false },
      onImported = { metadata ->
        keys = runCatching { privateKeyStore.loadPrivateKeys() }.getOrDefault(keys)
        selectedKeyId = metadata.id
        authMode = SshAuthMode.PrivateKey
        showImport = false
      },
    )
  }
}
