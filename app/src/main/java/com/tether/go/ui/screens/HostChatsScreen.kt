package com.tether.go.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.tether.go.chats.ClaudeChatCatalog
import com.tether.go.chats.RemoteChat
import com.tether.go.chats.matches
import com.tether.go.chats.pathLeaf
import com.tether.go.chats.relativeTimeLabel
import com.tether.go.chats.shortenHomePath
import com.tether.go.cli.CliToolId
import com.tether.go.cli.CliToolRegistry
import com.tether.go.session.SessionDraft
import com.tether.go.session.SessionStatus
import com.tether.go.session.SessionUiModel
import com.tether.go.ssh.SshAuthMaterial
import com.tether.go.ssh.SshCommandException
import com.tether.go.ssh.SshCommandRunner
import com.tether.go.ssh.SshConnectionTarget
import com.tether.go.ssh.SshHostKeyPrompt
import com.tether.go.ssh.SshHostRecord
import com.tether.go.ssh.SshHostStore
import com.tether.go.ssh.SshPasswordStore
import com.tether.go.ssh.SshStoredAuthResolver
import com.tether.go.ui.components.CheckboxRow
import com.tether.go.ui.components.HostKeyPromptDialog
import com.tether.go.ui.components.SelectableChip
import com.tether.go.ui.components.StatusDot
import com.tether.go.ui.components.TetherTextField
import com.tether.go.ui.components.TetherTopBar
import com.tether.go.ui.components.TopBarTextAction
import com.tether.go.ui.theme.LocalTetherTheme
import com.tether.go.ui.theme.MonoLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface ChatsLoad {
  data object Loading : ChatsLoad
  data object NeedsPassword : ChatsLoad
  data class Failed(val message: String) : ChatsLoad
  data class Loaded(val chats: List<RemoteChat>, val truncated: Boolean) : ChatsLoad
}

private class HostKeyRequest(val prompt: SshHostKeyPrompt, val response: CompletableDeferred<Boolean>)

/**
 * A saved host's Claude Code conversations, newest first and grouped by
 * project. Tapping a chat opens a phone-owned terminal that runs
 * `cd <project> && claude --resume <id>`; "New chat" starts a fresh Claude in
 * that project. The list comes from a read-only exec side channel
 * ([ClaudeChatCatalog]); terminal output is never parsed.
 */
@Composable
fun HostChatsScreen(
  hostId: String,
  hostStore: SshHostStore,
  passwordStore: SshPasswordStore,
  authResolver: SshStoredAuthResolver,
  commandRunner: SshCommandRunner,
  sessions: List<SessionUiModel>,
  launchFlags: Set<String>,
  onLaunchFlagsChange: (Set<String>) -> Unit,
  onBack: () -> Unit,
  onEditHost: () -> Unit,
  onNewSession: () -> Unit,
  onOpenSession: (String) -> Unit,
  onStart: (SessionDraft, SshAuthMaterial) -> Unit,
) {
  val theme = LocalTetherTheme.current
  val host = remember(hostId) { hostStore.loadHosts().firstOrNull { it.id == hostId } }
  if (host == null) {
    Column(Modifier.fillMaxSize().background(theme.bgPrimary)) {
      TetherTopBar(title = "Host not found", onBack = onBack)
    }
    return
  }

  var auth by remember { mutableStateOf<SshAuthMaterial?>(null) }
  var load by remember { mutableStateOf<ChatsLoad>(ChatsLoad.Loading) }
  var reloadKey by remember { mutableIntStateOf(0) }
  var hostKeyRequest by remember { mutableStateOf<HostKeyRequest?>(null) }
  var passwordError by remember { mutableStateOf<String?>(null) }
  var showPasswordDialog by remember { mutableStateOf(false) }
  var query by remember { mutableStateOf("") }

  LaunchedEffect(hostId, reloadKey) {
    load = ChatsLoad.Loading
    val resolved = auth ?: withContext(Dispatchers.IO) { authResolver.resolve(host.id, host.privateKeyId) }
    if (resolved == null) {
      load = ChatsLoad.NeedsPassword
      showPasswordDialog = true
      return@LaunchedEffect
    }
    auth = resolved
    load = try {
      val result = commandRunner.run(
        target = SshConnectionTarget(host.host, host.port, host.username),
        auth = resolved,
        command = ClaudeChatCatalog.listCommand(),
        confirmUnknownHostKey = { prompt ->
          val response = CompletableDeferred<Boolean>()
          withContext(Dispatchers.Main) { hostKeyRequest = HostKeyRequest(prompt, response) }
          try {
            response.await()
          } finally {
            withContext(Dispatchers.Main) { hostKeyRequest = null }
          }
        },
      )
      ChatsLoad.Loaded(ClaudeChatCatalog.parse(result.stdout), result.truncated)
    } catch (error: CancellationException) {
      throw error
    } catch (error: Exception) {
      val message = (error as? SshCommandException)?.message ?: error.message ?: error::class.java.simpleName
      if (resolved is SshAuthMaterial.Password && message.startsWith("Authentication failed")) {
        // Wrong or stale saved password: ask again rather than dead-ending.
        auth = null
        passwordError = "That password was rejected"
        showPasswordDialog = true
        ChatsLoad.NeedsPassword
      } else {
        ChatsLoad.Failed(message)
      }
    }
  }

  fun startClaude(chat: RemoteChat?, workingDir: String) {
    val credentials = auth ?: return
    onStart(
      SessionDraft(
        label = chat?.displayTitle?.take(48) ?: pathLeaf(workingDir),
        hostId = host.id,
        host = host.host,
        port = host.port,
        username = host.username,
        cliTool = CliToolId.CLAUDE,
        customBinary = null,
        workingDir = workingDir,
        flags = launchFlags.toList(),
        env = emptyMap(),
        privateKeyId = host.privateKeyId,
        toolSessionId = chat?.sessionId,
        resumeToolSession = chat != null,
      ),
      credentials,
    )
  }

  // Chats already open as phone sessions: tapping one returns to it.
  val openByChatId = sessions
    .filter { it.session.hostId == host.id && it.session.toolSessionId != null }
    .associateBy { it.session.toolSessionId!! }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(theme.bgPrimary),
  ) {
    TetherTopBar(
      title = host.title,
      subtitle = "Claude chats · ${host.displayName}",
      onBack = onBack,
      actions = {
        TopBarTextAction(label = "Refresh", onClick = { reloadKey += 1 })
        TopBarTextAction(label = "Edit", onClick = onEditHost)
      },
    )

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      item(key = "controls") {
        ChatsControls(
          query = query,
          onQueryChange = { query = it },
          launchFlags = launchFlags,
          onLaunchFlagsChange = onLaunchFlagsChange,
          onNewSession = onNewSession,
        )
      }

      when (val state = load) {
        ChatsLoad.Loading -> item(key = "loading") { LoadingCard(host) }
        ChatsLoad.NeedsPassword -> item(key = "password") {
          MessageCard(
            title = "Password needed",
            body = "Enter the SSH password for ${host.displayName} to list its Claude chats.",
            actionLabel = "Enter password",
            onAction = { showPasswordDialog = true },
          )
        }
        is ChatsLoad.Failed -> item(key = "failed") {
          MessageCard(
            title = "Couldn't list chats",
            body = state.message,
            isError = true,
            actionLabel = "Retry",
            onAction = { reloadKey += 1 },
          )
        }
        is ChatsLoad.Loaded -> {
          val visible = state.chats.filter { it.matches(query) }
          if (state.chats.isEmpty()) {
            item(key = "empty") {
              MessageCard(
                title = "No Claude chats here yet",
                body = "Tether Go reads ~/.claude/projects on this host. Start a session and your chats will show up here.",
                actionLabel = "New session",
                onAction = onNewSession,
              )
            }
          } else if (visible.isEmpty()) {
            item(key = "no-match") {
              Text("No chats match \"$query\".", color = theme.textMuted, style = MaterialTheme.typography.bodyMedium)
            }
          }
          val now = System.currentTimeMillis() / 1000
          visible.groupBy { it.workingDir }.forEach { (dir, chats) ->
            item(key = "project-$dir") {
              ProjectHeader(
                path = shortenHomePath(dir, host.username),
                onNewChat = { startClaude(chat = null, workingDir = dir) },
              )
            }
            items(chats, key = { it.sessionId }) { chat ->
              val open = openByChatId[chat.sessionId]
              ChatCard(
                chat = chat,
                nowEpochSeconds = now,
                openStatus = open?.status,
                onClick = {
                  if (open != null) onOpenSession(open.session.id) else startClaude(chat, chat.workingDir)
                },
              )
            }
          }
          if (state.truncated || state.chats.size >= ClaudeChatCatalog.DEFAULT_LIMIT) {
            item(key = "limit") {
              Text(
                "Showing the ${state.chats.size} most recent chats.",
                color = theme.textMuted,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp),
              )
            }
          }
        }
      }
    }
  }

  hostKeyRequest?.let { request ->
    HostKeyPromptDialog(
      prompt = request.prompt,
      onAccept = { request.response.complete(true) },
      onReject = { request.response.complete(false) },
    )
  }

  if (showPasswordDialog) {
    PasswordDialog(
      host = host,
      error = passwordError,
      onDismiss = { showPasswordDialog = false },
      onSubmit = { password, remember ->
        if (remember) runCatching { passwordStore.savePassword(host.id, password) }
        auth = SshAuthMaterial.Password(password)
        passwordError = null
        showPasswordDialog = false
        reloadKey += 1
      },
    )
  }
}

@Composable
private fun ChatsControls(
  query: String,
  onQueryChange: (String) -> Unit,
  launchFlags: Set<String>,
  onLaunchFlagsChange: (Set<String>) -> Unit,
  onNewSession: () -> Unit,
) {
  val theme = LocalTetherTheme.current
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Button(
      onClick = onNewSession,
      modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
      shape = RoundedCornerShape(10.dp),
      colors = ButtonDefaults.buttonColors(containerColor = theme.accent, contentColor = theme.btnPrimaryText),
    ) {
      Text("+  New session on this host", fontWeight = FontWeight.SemiBold)
    }
    TetherTextField(query, onQueryChange, "Search chats", modifier = Modifier.fillMaxWidth())
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text("Launch with", color = theme.textMuted, style = MaterialTheme.typography.labelMedium)
      CliToolRegistry.claude.commonFlags.forEach { flag ->
        val selected = flag.flag in launchFlags
        SelectableChip(
          label = flag.chipLabel,
          selected = selected,
          onClick = { onLaunchFlagsChange(if (selected) launchFlags - flag.flag else launchFlags + flag.flag) },
        )
      }
    }
  }
}

@Composable
private fun ProjectHeader(path: String, onNewChat: () -> Unit) {
  val theme = LocalTetherTheme.current
  Row(
    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        pathLeaf(path).uppercase(),
        color = theme.textSecondary,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(path, color = theme.textMuted, style = MonoLabel.copy(fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    TopBarTextAction(label = "+ New chat", onClick = onNewChat, tint = theme.accent)
  }
}

@Composable
private fun ChatCard(
  chat: RemoteChat,
  nowEpochSeconds: Long,
  openStatus: SessionStatus?,
  onClick: () -> Unit,
) {
  val theme = LocalTetherTheme.current
  val shape = RoundedCornerShape(12.dp)
  val preview = (chat.lastPrompt ?: chat.firstPrompt)?.takeIf { it != chat.displayTitle }
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(shape)
      .clickable(onClick = onClick)
      .background(theme.bgSidebar, shape)
      .then(if (openStatus != null) Modifier.border(1.dp, theme.accent.copy(alpha = 0.6f), shape) else Modifier)
      .padding(horizontal = 12.dp, vertical = 10.dp),
  ) {
    Text(
      chat.displayTitle,
      color = theme.textPrimary,
      style = MaterialTheme.typography.titleSmall,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
    if (preview != null) {
      Text(
        "› $preview",
        color = theme.textMuted,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 2.dp),
      )
    }
    Row(
      modifier = Modifier.padding(top = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        relativeTimeLabel(chat.modifiedEpochSeconds, nowEpochSeconds),
        color = theme.textSecondary,
        style = MaterialTheme.typography.labelSmall,
      )
      Text(chat.sessionId.take(8), color = theme.textMuted, style = MonoLabel.copy(fontSize = 11.sp))
      Spacer(Modifier.weight(1f))
      if (openStatus != null) {
        StatusDot(status = openStatus)
        Text("on phone", color = theme.accent, style = MaterialTheme.typography.labelSmall)
      } else {
        Text("Resume ›", color = theme.accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
      }
    }
  }
}

@Composable
private fun LoadingCard(host: SshHostRecord) {
  val theme = LocalTetherTheme.current
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 16.dp)
      .background(theme.bgSidebar, RoundedCornerShape(12.dp))
      .padding(16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = theme.accent)
    Spacer(Modifier.width(12.dp))
    Text(
      "Reading Claude chats on ${host.title}…",
      color = theme.textSecondary,
      style = MaterialTheme.typography.bodyMedium,
    )
  }
}

@Composable
private fun MessageCard(
  title: String,
  body: String,
  actionLabel: String,
  onAction: () -> Unit,
  isError: Boolean = false,
) {
  val theme = LocalTetherTheme.current
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 16.dp)
      .background(theme.bgSidebar, RoundedCornerShape(12.dp))
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Text(title, color = if (isError) theme.statusDead else theme.textPrimary, style = MaterialTheme.typography.titleSmall)
    Text(body, color = theme.textMuted, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(4.dp))
    FilledTonalButton(onClick = onAction, shape = RoundedCornerShape(8.dp)) { Text(actionLabel) }
  }
}

@Composable
private fun PasswordDialog(
  host: SshHostRecord,
  error: String?,
  onDismiss: () -> Unit,
  onSubmit: (password: String, remember: Boolean) -> Unit,
) {
  val theme = LocalTetherTheme.current
  var password by remember { mutableStateOf("") }
  var rememberPassword by remember { mutableStateOf(true) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("SSH password") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(host.displayName, color = theme.textSecondary, style = MonoLabel)
        TetherTextField(password, { password = it }, "Password", isPassword = true, modifier = Modifier.fillMaxWidth())
        CheckboxRow(
          checked = rememberPassword,
          onCheckedChange = { rememberPassword = it },
          label = "Remember on this phone",
        )
        error?.let { Text(it, color = theme.statusDead, style = MaterialTheme.typography.labelMedium) }
      }
    },
    confirmButton = {
      TextButton(enabled = password.isNotEmpty(), onClick = { onSubmit(password, rememberPassword) }) { Text("Connect") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
