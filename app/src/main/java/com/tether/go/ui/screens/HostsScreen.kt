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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tether.go.ssh.SshHostStore
import com.tether.go.ssh.SshPasswordStore
import com.tether.go.ssh.SshPrivateKeyStore
import com.tether.go.ui.components.PrivateKeyImportFlow
import com.tether.go.ui.components.SectionHeader
import com.tether.go.ui.components.TetherTopBar
import com.tether.go.ui.theme.LocalTetherTheme
import com.tether.go.ui.theme.MonoLabel

/**
 * Manage saved SSH hosts and imported private keys. Tapping a host opens its
 * Claude chats. Hosts hold only endpoint metadata plus an optional key
 * reference; key material and saved passwords live encrypted in
 * Keystore-backed storage and are never shown here.
 */
@Composable
fun HostsScreen(
  hostStore: SshHostStore,
  privateKeyStore: SshPrivateKeyStore,
  passwordStore: SshPasswordStore,
  onBack: () -> Unit,
  onAddHost: () -> Unit,
  onEditHost: (String) -> Unit,
  onOpenHost: (String) -> Unit,
) {
  val theme = LocalTetherTheme.current
  var hosts by remember { mutableStateOf(hostStore.loadHosts()) }
  var keys by remember {
    mutableStateOf(runCatching { privateKeyStore.loadPrivateKeys() }.getOrDefault(emptyList()))
  }
  var showImport by remember { mutableStateOf(false) }
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
      Button(
        onClick = onAddHost,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = theme.accent, contentColor = theme.btnPrimaryText),
      ) {
        Text("+  Add host", fontWeight = FontWeight.SemiBold)
      }

      SectionHeader("Saved hosts")
      if (hosts.isEmpty()) {
        Text(
          "No saved hosts yet. Add the machine you run Claude Code on to browse and resume its chats.",
          color = theme.textMuted,
          style = MaterialTheme.typography.bodyMedium,
        )
      } else {
        hosts.forEach { host ->
          val keyLabel = host.privateKeyId?.let { id -> keys.firstOrNull { it.id == id }?.label }
          val authLabel = when {
            keyLabel != null -> "key: $keyLabel"
            passwordStore.hasPassword(host.id) -> "saved password"
            else -> "password (asks each time)"
          }
          RowCard(
            title = host.title,
            subtitle = "${host.displayName} · $authLabel",
            onClick = { onOpenHost(host.id) },
            actions = listOf(
              CardAction("Edit") { onEditHost(host.id) },
              CardAction("Delete", destructive = true) {
                runCatching { passwordStore.deletePassword(host.id) }
                hosts = hostStore.deleteHost(host.id)
              },
            ),
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
            actions = listOf(
              CardAction("Delete", destructive = true) {
                runCatching { keys = privateKeyStore.deletePrivateKey(key.id) }
                  .onFailure { storageError = "Private key storage could not be updated" }
              },
            ),
          )
        }
      }
      Text(
        "+ Import private key",
        color = theme.accent,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
          .padding(vertical = 8.dp)
          .clickable { showImport = true },
      )
    }
  }

  if (showImport) {
    PrivateKeyImportFlow(
      privateKeyStore = privateKeyStore,
      onDismiss = { showImport = false },
      onImported = {
        keys = runCatching { privateKeyStore.loadPrivateKeys() }.getOrDefault(keys)
        showImport = false
      },
    )
  }
}

private class CardAction(val label: String, val destructive: Boolean = false, val onClick: () -> Unit)

@Composable
private fun RowCard(
  title: String,
  subtitle: String,
  actions: List<CardAction>,
  onClick: (() -> Unit)? = null,
) {
  val theme = LocalTetherTheme.current
  val shape = RoundedCornerShape(10.dp)
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(shape)
      .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
      .background(theme.bgSidebar, shape)
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
      Text(subtitle, color = theme.textMuted, style = MaterialTheme.typography.labelSmall, maxLines = 2)
    }
    actions.forEach { action ->
      Text(
        action.label,
        color = if (action.destructive) theme.statusDead else theme.textSecondary,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
          .clip(RoundedCornerShape(8.dp))
          .clickable(onClick = action.onClick)
          .padding(horizontal = 8.dp, vertical = 4.dp),
      )
    }
  }
}
