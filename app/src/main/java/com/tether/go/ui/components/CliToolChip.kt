package com.tether.go.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tether.go.cli.CliToolId
import com.tether.go.cli.CliToolRegistry
import com.tether.go.ui.theme.LocalTetherTheme

/**
 * The bordered CLI-tool chip from desktop Tether's session rows. Each tool gets
 * a distinct hue drawn from the active theme's ANSI palette so Claude / Codex /
 * Copilot / OpenCode read apart at a glance.
 */
@Composable
fun CliToolChip(
  cliTool: CliToolId,
  modifier: Modifier = Modifier,
  customBinary: String? = null,
) {
  val theme = LocalTetherTheme.current
  val color: Color = when (cliTool) {
    CliToolId.CLAUDE -> theme.ansi[2]   // green
    CliToolId.CODEX -> theme.ansi[4]    // blue
    CliToolId.COPILOT -> theme.ansi[6]  // cyan
    CliToolId.OPENCODE -> theme.ansi[5] // magenta
    CliToolId.CUSTOM -> theme.textMuted
  }
  val label = if (cliTool == CliToolId.CUSTOM) {
    customBinary?.trim()?.takeIf { it.isNotEmpty() } ?: CliToolRegistry.custom.displayName
  } else {
    CliToolRegistry.byId(cliTool).displayName
  }

  Text(
    text = label,
    color = color,
    style = MaterialTheme.typography.labelSmall,
    fontWeight = FontWeight.Medium,
    modifier = modifier
      .border(BorderStroke(1.dp, color.copy(alpha = 0.6f)), RoundedCornerShape(4.dp))
      .padding(horizontal = 6.dp, vertical = 2.dp),
  )
}
