package com.tether.go.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tether.go.cli.CliToolId
import com.tether.go.cli.GuidedLaunchFlags
import com.tether.go.cli.GuidedLaunchSelection
import com.tether.go.ui.theme.LocalTetherTheme

/** Controlled inputs shared by Settings and New Session; no storage or autosave. */
@Composable
fun GuidedLaunchFields(
  tool: CliToolId,
  selection: GuidedLaunchSelection,
  onChange: (GuidedLaunchSelection) -> Unit,
) {
  if (tool !in GuidedLaunchFlags.supportedTools) return
  val theme = LocalTetherTheme.current
  TetherTextField(
    selection.model, { onChange(selection.copy(model = it)) }, "Model",
    modifier = Modifier.fillMaxWidth(), placeholder = "CLI default (leave empty)",
  )
  if (tool == CliToolId.OPENCODE) {
    TetherTextField(
      selection.agent, { onChange(selection.copy(agent = it)) }, "Agent",
      modifier = Modifier.fillMaxWidth(), placeholder = "e.g. build",
    )
  }
  if (tool == CliToolId.CODEX) {
    TetherTextField(
      selection.nativeProfile, { onChange(selection.copy(nativeProfile = it)) }, "Native profile",
      modifier = Modifier.fillMaxWidth(), placeholder = "e.g. work",
    )
    Text(
      "References an existing Codex profile on the remote host, not a saved Tether Go launch profile.",
      color = theme.textMuted, style = MaterialTheme.typography.bodySmall,
    )
    TetherTextField(
      selection.reasoningEffort, { onChange(selection.copy(reasoningEffort = it)) }, "Reasoning effort",
      modifier = Modifier.fillMaxWidth(), placeholder = "e.g. low, medium, high, xhigh",
    )
  }
  Text(
    "Enter identifiers supported by your remote CLI; availability is not checked. Empty fields leave manual flags and native defaults unchanged. Do not enter secrets.",
    color = theme.textMuted, style = MaterialTheme.typography.bodySmall,
  )
}
