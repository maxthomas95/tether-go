package com.tether.go.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tether.go.ssh.SecureStorageException
import com.tether.go.ssh.SshPrivateKeyImportException
import com.tether.go.ssh.SshPrivateKeyMetadata
import com.tether.go.ssh.SshPrivateKeyStore
import com.tether.go.ui.theme.LocalTetherTheme
import com.tether.go.ui.theme.MonoLabel

enum class SshAuthMode { Password, PrivateKey }

/**
 * Password / private-key picker shared by the New Session form and the host
 * editor. Key material is never shown; only imported key labels.
 */
@Composable
fun SshAuthSection(
  mode: SshAuthMode,
  onModeChange: (SshAuthMode) -> Unit,
  password: String,
  onPasswordChange: (String) -> Unit,
  keys: List<SshPrivateKeyMetadata>,
  selectedKeyId: String?,
  onSelectKey: (String) -> Unit,
  onImportClick: () -> Unit,
  keyError: String?,
  passwordLabel: String = "Password",
) {
  val theme = LocalTetherTheme.current
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    SelectableChip("Password", mode == SshAuthMode.Password, { onModeChange(SshAuthMode.Password) })
    SelectableChip("Private key", mode == SshAuthMode.PrivateKey, { onModeChange(SshAuthMode.PrivateKey) })
  }
  if (mode == SshAuthMode.Password) {
    TetherTextField(password, onPasswordChange, passwordLabel, isPassword = true, modifier = Modifier.fillMaxWidth())
  } else {
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      SelectableChip("+ Import key", false, onImportClick)
      keys.forEach { key ->
        SelectableChip(key.label, key.id == selectedKeyId, { onSelectKey(key.id) })
      }
    }
    keyError?.let { Text(it, color = theme.statusDead, style = MaterialTheme.typography.labelMedium) }
  }
}

/** A checkbox with a label and optional muted detail line. */
@Composable
fun CheckboxRow(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  label: String,
  detail: String? = null,
  detailMonospace: Boolean = false,
) {
  val theme = LocalTetherTheme.current
  Row(
    modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Checkbox(
      checked = checked,
      onCheckedChange = onCheckedChange,
      colors = CheckboxDefaults.colors(checkedColor = theme.accent, uncheckedColor = theme.border),
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(label, color = theme.textPrimary, style = MaterialTheme.typography.bodyMedium)
      if (detail != null) {
        Text(
          detail,
          color = theme.textMuted,
          style = if (detailMonospace) MonoLabel else MaterialTheme.typography.labelSmall,
        )
      }
    }
  }
}

/**
 * Hosts the private-key import dialog and its error state; calls [onImported]
 * with the stored key's metadata on success.
 */
@Composable
fun PrivateKeyImportFlow(
  privateKeyStore: SshPrivateKeyStore,
  onDismiss: () -> Unit,
  onImported: (SshPrivateKeyMetadata) -> Unit,
) {
  var error by remember { mutableStateOf<String?>(null) }
  PrivateKeyImportDialog(
    error = error,
    onDismiss = onDismiss,
    onImport = { label, pem, passphrase ->
      runCatching { privateKeyStore.importPrivateKey(label, pem, passphrase) }
        .onSuccess(onImported)
        .onFailure { err ->
          error = when (err) {
            is SshPrivateKeyImportException -> err.message
            is SecureStorageException -> "Private key storage could not be updated"
            else -> "Private key could not be imported"
          }
        }
    },
  )
}
