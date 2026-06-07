package com.tether.go.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tether.go.ui.branding.TetherLogo
import com.tether.go.ui.theme.LocalTetherTheme

/**
 * Two-pane "desktop" shell used on wide windows (>= 840dp — unfolded foldables
 * and tablets): a fixed-width [sidebar] (the session list) on the left, a 1dp
 * divider, then the [detail] pane filling the rest.
 *
 * Purely structural — it owns no app state; the caller supplies both panes as
 * slots. On compact widths the app keeps its single-column back stack and this
 * composable is never entered.
 */
@Composable
fun ExpandedLayout(
  sidebar: @Composable () -> Unit,
  detail: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  sidebarWidth: Dp = 320.dp,
) {
  val theme = LocalTetherTheme.current
  Row(modifier = modifier.fillMaxSize()) {
    Box(
      modifier = Modifier
        .width(sidebarWidth)
        .fillMaxHeight()
        .background(theme.bgPrimary),
    ) {
      sidebar()
    }
    Box(
      modifier = Modifier
        .width(1.dp)
        .fillMaxHeight()
        .background(theme.border),
    )
    Box(
      modifier = Modifier
        .weight(1f)
        .fillMaxHeight()
        .background(theme.bgPrimary),
    ) {
      detail()
    }
  }
}

/** Placeholder shown in the detail pane when no session is selected. */
@Composable
fun EmptyDetailPane(modifier: Modifier = Modifier) {
  val theme = LocalTetherTheme.current
  Column(
    modifier = modifier
      .fillMaxSize()
      .background(theme.bgPrimary)
      .padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    TetherLogo(size = 72.dp)
    Spacer(Modifier.height(20.dp))
    Text(
      text = "Select a session",
      color = theme.textPrimary,
      style = MaterialTheme.typography.titleMedium,
    )
    Spacer(Modifier.height(8.dp))
    Text(
      text = "Pick a session from the list, or start a new one.",
      color = theme.textMuted,
      style = MaterialTheme.typography.bodyMedium,
      textAlign = TextAlign.Center,
    )
  }
}
