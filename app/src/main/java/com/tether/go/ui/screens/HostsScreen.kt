package com.tether.go.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tether.go.ssh.SecureStorageException
import com.tether.go.ssh.SshHostStore
import com.tether.go.ssh.SshPrivateKeyImportException
import com.tether.go.ssh.SshPrivateKeyStore
import com.tether.go.ui.components.PrivateKeyImportDialog
import com.tether.go.ui.components.SectionHeader
import com.tether.go.ui.components.TetherTopBar
import com.tether.go.ui.theme.LocalTetherTheme
import com.tether.go.ui.theme.MonoLabel

/**
 * Manage saved SSH hosts and imported private keys. Hosts hold only endpoint
 * metadata plus an optional key reference; key material lives encrypted in
 * Keystore-backed storage and is never shown here.
 */
@Composable
fun HostsScreen(
  hostStore: SshHostStore,
  privateKeyStore: SshPrivateKeyStore,
  onBack: () -> Unit,
) {
  val theme = LocalTetherTheme.current
  var hosts by remember { mutableStateOf(hostStore.loadHosts()) }
  var keys by remember {
    mutableStateOf(runCatching { privateKeyStore.loadPrivateKeys() }.getOrDefault(emptyList()))
  }
  var showImport by remember { mutableStateOf(false) }
  var importError by remember { mutableStateOf<String?>(null) }
  var storageError by remember { mutableStateOf<String?>(null) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(theme.bgPrimary),
  ) {
    TetherTopBar(title = "Hosts & keys", onBack = onBack)

    Column(
      modifier = Modifier
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      SectionHeader("Saved hosts")
      if (hosts.isEmpty()) {
        Text("No saved hosts yet.", color = theme.textMuted, style = MaterialTheme.typography.bodyMedium)
      } else {
        hosts.forEach { host ->
          val keyLabel = host.privateKeyId?.let { id -> keys.firstOrNull { it.id == id }?.label }
          RowCard(
            title = host.displayName,
            subtitle = keyLabel?.let { "key: $it" } ?: "password auth",
            onDelete = {
              hosts = hostStore.deleteHost(host.id)
            },
          )
        }
      }

      SectionHeader("Private keys")
      storageError?.let { Text(it, color = theme.statusDead, style = MaterialTheme.typography.labelMedium) }
      if (keys.isEmpty()) {
        Text("No imported keys.", color = theme.textMuted, style = MaterialTheme.typography.bodyMedium)
      } else {
        keys.forEach { key ->
          RowCard(
            title = key.label,
            subtitle = key.keyFormat + if (key.hasPassphrase) " · passphrase" else "",
            onDelete = {
              runCatching { keys = privateKeyStore.deletePrivateKey(key.id) }
                .onFailure { storageError = "Private key storage could not be updated" }
            },
          )
        }
      }
      Text(
        "+ Import private key",
        color = theme.accent,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
          .padding(vertical = 8.dp)
          .clickable { importError = null; showImport = true },
      )
    }
  }

  if (showImport) {
    PrivateKeyImportDialog(
      error = importError,
      onDismiss = { showImport = false; importError = null },
      onImport = { label, pem, passphrase ->
        runCatching { privateKeyStore.importPrivateKey(label, pem, passphrase) }
          .onSuccess {
            keys = runCatching { privateKeyStore.loadPrivateKeys() }.getOrDefault(keys)
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

@Composable
private fun RowCard(
  title: String,
  subtitle: String,
  onDelete: () -> Unit,
) {
  val theme = LocalTetherTheme.current
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(theme.bgSidebar, RoundedCornerShape(10.dp))
      .padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        title,
        color = theme.textPrimary,
        style = MonoLabel,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(subtitle, color = theme.textMuted, style = MaterialTheme.typography.labelSmall)
    }
    Text(
      "Delete",
      color = theme.statusDead,
      style = MaterialTheme.typography.labelLarge,
      modifier = Modifier
        .clickable(onClick = onDelete)
        .padding(horizontal = 8.dp, vertical = 4.dp),
    )
  }
}
