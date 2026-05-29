package com.tether.go.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tether.go.session.SessionStatus
import com.tether.go.session.SessionUiModel
import com.tether.go.ui.branding.TetherLogo
import com.tether.go.ui.components.CliToolChip
import com.tether.go.ui.components.StatusDot
import com.tether.go.ui.components.TetherTopBar
import com.tether.go.ui.components.TopBarTextAction
import com.tether.go.ui.theme.LocalTetherTheme
import com.tether.go.ui.theme.MonoLabel

/**
 * Home screen: the phone-owned session list. Mirrors the desktop sidebar as a
 * mobile-first list — sessions grouped by host, each with a status dot, label,
 * CLI-tool chip, working directory, and per-session actions.
 */
@Composable
fun SessionListScreen(
  sessions: List<SessionUiModel>,
  onOpenSession: (String) -> Unit,
  onNewSession: () -> Unit,
  onOpenHosts: () -> Unit,
  onOpenSettings: () -> Unit,
  onRename: (String, String) -> Unit,
  onDisconnect: (String) -> Unit,
  onReconnect: (String) -> Unit,
  onRemove: (String) -> Unit,
  canReconnect: (String) -> Boolean,
) {
  val theme = LocalTetherTheme.current
  var renameTarget by remember { mutableStateOf<SessionUiModel?>(null) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(theme.bgPrimary),
  ) {
    TetherTopBar(
      title = "Tether Go",
      showLogo = true,
      actions = {
        TopBarTextAction(label = "Hosts", onClick = onOpenHosts)
        TopBarTextAction(label = "Settings", onClick = onOpenSettings)
      },
    )

    Button(
      onClick = onNewSession,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp),
      shape = RoundedCornerShape(10.dp),
      colors = ButtonDefaults.buttonColors(
        containerColor = theme.accent,
        contentColor = theme.btnPrimaryText,
      ),
    ) {
      Text(text = "+  New session", fontWeight = FontWeight.SemiBold)
    }

    if (sessions.isEmpty()) {
      EmptySessions(onNewSession = onNewSession)
    } else {
      val groups = sessions.groupBy { it.session.host }
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        groups.forEach { (host, hostSessions) ->
          item(key = "header-$host") {
            SessionGroupHeader(
              host = host,
              activeCount = hostSessions.count {
                it.status == SessionStatus.Running || it.status == SessionStatus.Connecting
              },
              total = hostSessions.size,
            )
          }
          items(hostSessions, key = { it.session.id }) { model ->
            SessionCard(
              model = model,
              canReconnect = canReconnect(model.session.id),
              onOpen = { onOpenSession(model.session.id) },
              onRename = { renameTarget = model },
              onDisconnect = { onDisconnect(model.session.id) },
              onReconnect = { onReconnect(model.session.id) },
              onRemove = { onRemove(model.session.id) },
            )
          }
        }
      }
    }
  }

  renameTarget?.let { target ->
    RenameDialog(
      currentLabel = target.session.label,
      onDismiss = { renameTarget = null },
      onConfirm = { newLabel ->
        onRename(target.session.id, newLabel)
        renameTarget = null
      },
    )
  }
}

@Composable
private fun SessionGroupHeader(host: String, activeCount: Int, total: Int) {
  val theme = LocalTetherTheme.current
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 8.dp, bottom = 2.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = host.uppercase(),
      color = theme.textSecondary,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.SemiBold,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
    Text(
      text = if (activeCount > 0) "$activeCount active" else "$total saved",
      color = theme.textMuted,
      style = MaterialTheme.typography.labelSmall,
    )
  }
}

@Composable
private fun SessionCard(
  model: SessionUiModel,
  canReconnect: Boolean,
  onOpen: () -> Unit,
  onRename: () -> Unit,
  onDisconnect: () -> Unit,
  onReconnect: () -> Unit,
  onRemove: () -> Unit,
) {
  val theme = LocalTetherTheme.current
  val session = model.session

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onOpen)
      .background(theme.bgSidebar, RoundedCornerShape(12.dp))
      .padding(horizontal = 12.dp, vertical = 12.dp),
    verticalAlignment = Alignment.Top,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    StatusDot(status = model.status, modifier = Modifier.padding(top = 5.dp))

    Column(modifier = Modifier.weight(1f)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = session.label,
          color = theme.textPrimary,
          style = MaterialTheme.typography.titleSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(8.dp))
        CliToolChip(cliTool = session.cliTool, customBinary = session.customBinary)
      }
      Spacer(Modifier.height(3.dp))
      Text(
        text = session.workingDir?.takeIf { it.isNotBlank() } ?: "~",
        color = theme.textSecondary,
        style = MonoLabel,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = session.endpointLabel,
        color = theme.textMuted,
        style = MonoLabel.copy(fontSize = 11.sp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (model.status != SessionStatus.Running) {
        Text(
          text = model.statusMessage,
          color = if (model.status == SessionStatus.Error) theme.statusDead else theme.textMuted,
          style = MaterialTheme.typography.labelSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(top = 2.dp),
        )
      }
    }

    SessionCardMenu(
      status = model.status,
      canReconnect = canReconnect,
      onRename = onRename,
      onDisconnect = onDisconnect,
      onReconnect = onReconnect,
      onRemove = onRemove,
    )
  }
}

@Composable
private fun SessionCardMenu(
  status: SessionStatus,
  canReconnect: Boolean,
  onRename: () -> Unit,
  onDisconnect: () -> Unit,
  onReconnect: () -> Unit,
  onRemove: () -> Unit,
) {
  val theme = LocalTetherTheme.current
  var expanded by remember { mutableStateOf(false) }
  val connected = status == SessionStatus.Running ||
    status == SessionStatus.Connecting ||
    status == SessionStatus.Waiting

  Box {
    Text(
      text = "⋮",
      color = theme.textMuted,
      fontSize = 20.sp,
      modifier = Modifier
        .clip(RoundedCornerShape(8.dp))
        .clickable { expanded = true }
        .padding(horizontal = 8.dp, vertical = 2.dp),
    )
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      DropdownMenuItem(
        text = { Text("Rename") },
        onClick = { expanded = false; onRename() },
      )
      if (connected) {
        DropdownMenuItem(
          text = { Text("Disconnect") },
          onClick = { expanded = false; onDisconnect() },
        )
      } else if (canReconnect) {
        DropdownMenuItem(
          text = { Text("Reconnect") },
          onClick = { expanded = false; onReconnect() },
        )
      }
      DropdownMenuItem(
        text = { Text("Remove", color = theme.statusDead) },
        onClick = { expanded = false; onRemove() },
      )
    }
  }
}

@Composable
private fun RenameDialog(
  currentLabel: String,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
) {
  val theme = LocalTetherTheme.current
  var label by remember { mutableStateOf(currentLabel) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Rename session") },
    text = {
      OutlinedTextField(
        value = label,
        onValueChange = { label = it },
        singleLine = true,
        label = { Text("Label") },
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = theme.accent,
          cursorColor = theme.accent,
          focusedLabelColor = theme.accent,
        ),
      )
    },
    confirmButton = {
      TextButton(enabled = label.isNotBlank(), onClick = { onConfirm(label) }) { Text("Save") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
private fun EmptySessions(onNewSession: () -> Unit) {
  val theme = LocalTetherTheme.current
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    TetherLogo(size = 72.dp)
    Spacer(Modifier.height(20.dp))
    Text(
      text = "No sessions yet",
      color = theme.textPrimary,
      style = MaterialTheme.typography.titleMedium,
    )
    Spacer(Modifier.height(8.dp))
    Text(
      text = "Start an agent CLI on a reachable SSH host and Tether Go will keep it on your phone.",
      color = theme.textMuted,
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(20.dp))
    Button(
      onClick = onNewSession,
      colors = ButtonDefaults.buttonColors(
        containerColor = theme.accent,
        contentColor = theme.btnPrimaryText,
      ),
    ) {
      Text("+  New session", fontWeight = FontWeight.SemiBold)
    }
  }
}
