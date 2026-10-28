package com.tether.go.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tether.go.cli.CliToolId
import com.tether.go.cli.CliToolRegistry
import com.tether.go.cli.GuidedLaunchFlags
import com.tether.go.cli.GuidedLaunchSelection
import com.tether.go.settings.LaunchDefaultsStore
import com.tether.go.ui.theme.LocalTetherTheme

@Composable
fun LaunchDefaultsEditor(store: LaunchDefaultsStore) {
  val theme = LocalTetherTheme.current
  var tool by remember { mutableStateOf(CliToolId.CLAUDE) }
  // Ignore old callbacks after a tool switch until its new draft is composed.
  val editorTool = tool
  var loaded by remember(store, tool) { mutableStateOf(runCatching { store.load(tool) }) }
  var selection by remember(store, tool) { mutableStateOf(loaded.getOrDefault(GuidedLaunchSelection())) }
  var status by remember(store, tool) { mutableStateOf<String?>(null) }
  var actionError by remember(store, tool) { mutableStateOf<String?>(null) }
  val validation = runCatching { GuidedLaunchFlags.normalize(tool, selection) }

  SectionHeader("CLI launch defaults")
  Text(
    "Phone-local choices for future New session forms only. Existing sessions keep their launch settings. Save before changing tool or leaving; unsaved edits are discarded.",
    color = theme.textMuted, style = MaterialTheme.typography.bodySmall,
  )
  Row(
    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    GuidedLaunchFlags.supportedTools.forEach { candidate ->
      SelectableChip(CliToolRegistry.byId(candidate).displayName, candidate == tool, { tool = candidate })
    }
  }
  GuidedLaunchFields(editorTool, selection) {
    if (tool == editorTool) { selection = it; status = null; actionError = null }
  }
  val error = actionError ?: loaded.exceptionOrNull()?.message ?: validation.exceptionOrNull()?.message
  error?.let { Text(it, color = theme.statusDead, style = MaterialTheme.typography.bodySmall) }
  status?.let { Text(it, color = theme.textMuted, style = MaterialTheme.typography.bodySmall) }
  Row(
    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    FilledTonalButton(
      enabled = loaded.isSuccess && validation.isSuccess,
      onClick = save@{
        if (tool != editorTool) return@save
        runCatching { store.save(editorTool, selection) }
          .onSuccess {
            selection = it
            loaded = Result.success(it)
            actionError = null
            status = if (it == GuidedLaunchSelection()) "Defaults cleared for this CLI." else "Defaults saved for future sessions."
          }
          .onFailure { actionError = it.message ?: "Defaults could not be saved."; status = null }
      },
    ) { Text("Save") }
    TextButton(onClick = clear@{
      if (tool != editorTool) return@clear
      runCatching { store.clear(editorTool) }
        .onSuccess {
          selection = GuidedLaunchSelection()
          loaded = Result.success(selection)
          actionError = null
          status = "Defaults cleared for this CLI."
        }
        .onFailure { actionError = it.message ?: "Defaults could not be cleared."; status = null }
    }) { Text("Clear defaults") }
    TextButton(onClick = cancel@{
      if (tool != editorTool) return@cancel
      loaded = runCatching { store.load(editorTool) }
      selection = loaded.getOrDefault(GuidedLaunchSelection())
      actionError = null
      status = if (loaded.isSuccess) "Unsaved edits discarded." else null
    }) { Text("Cancel edits") }
  }
}
