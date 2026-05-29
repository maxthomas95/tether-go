package com.tether.go.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tether.go.ssh.SshHostKeyPrompt
import com.tether.go.ui.theme.LocalTetherTheme

/**
 * Host-key trust-on-first-use prompt. The fingerprint is shown in monospace and
 * the user must explicitly accept before authentication proceeds; rejecting
 * fails the connection closed.
 */
@Composable
fun HostKeyPromptDialog(
  prompt: SshHostKeyPrompt,
  onAccept: () -> Unit,
  onReject: () -> Unit,
) {
  val theme = LocalTetherTheme.current
  AlertDialog(
    onDismissRequest = {},
    title = { Text("Trust SSH host key?") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(prompt.endpoint.displayName, color = theme.textPrimary)
        Text(
          text = "${prompt.type}\n${prompt.sha256Fingerprint}",
          color = theme.accent,
          fontFamily = FontFamily.Monospace,
          fontSize = 12.sp,
        )
        Text(
          text = "Accept only if this fingerprint matches the server you intended to reach.",
          color = theme.textMuted,
          style = MaterialTheme.typography.bodySmall,
        )
      }
    },
    confirmButton = { TextButton(onClick = onAccept) { Text("Accept") } },
    dismissButton = { TextButton(onClick = onReject) { Text("Reject") } },
  )
}

/**
 * Import an existing PEM private key into Keystore-encrypted storage. Key bytes
 * and passphrases never leave the encrypted store and are never logged.
 */
@Composable
fun PrivateKeyImportDialog(
  error: String?,
  onDismiss: () -> Unit,
  onImport: (label: String, privateKeyData: String, passphrase: String?) -> Unit,
) {
  val theme = LocalTetherTheme.current
  var label by remember { mutableStateOf("") }
  var privateKeyData by remember { mutableStateOf("") }
  var passphrase by remember { mutableStateOf("") }
  val canImport = label.isNotBlank() && privateKeyData.isNotBlank()

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Import SSH private key") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TetherTextField(
          value = label,
          onValueChange = { label = it },
          label = "Label",
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = privateKeyData,
          onValueChange = { privateKeyData = it },
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp, max = 200.dp),
          label = { Text("Private key (PEM)") },
          textStyle = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
          ),
          colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = theme.textPrimary,
            unfocusedTextColor = theme.textPrimary,
            focusedBorderColor = theme.accent,
            unfocusedBorderColor = theme.border,
            cursorColor = theme.accent,
            focusedLabelColor = theme.accent,
            unfocusedLabelColor = theme.textMuted,
          ),
        )
        TetherTextField(
          value = passphrase,
          onValueChange = { passphrase = it },
          label = "Passphrase (optional)",
          isPassword = true,
          modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
          Text(
            text = error,
            color = theme.statusDead,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    },
    confirmButton = {
      TextButton(
        enabled = canImport,
        onClick = { onImport(label, privateKeyData, passphrase.takeIf { it.isNotEmpty() }) },
      ) { Text("Import") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
