package com.tether.go.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tether.go.ui.branding.TetherLogo
import com.tether.go.ui.theme.LocalTetherTheme

/**
 * Shared Tether top bar. Shows either the Tether mark (home) or a back affordance
 * (sub-screens), the screen title with an optional monospace subtitle, and a
 * trailing slot for actions.
 */
@Composable
fun TetherTopBar(
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  onBack: (() -> Unit)? = null,
  showLogo: Boolean = false,
  actions: @Composable RowScope.() -> Unit = {},
) {
  val theme = LocalTetherTheme.current
  Row(
    modifier = modifier
      .fillMaxWidth()
      .heightIn(min = 52.dp)
      .padding(horizontal = 8.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    if (onBack != null) {
      Text(
        text = "‹",
        color = theme.textPrimary,
        fontSize = 28.sp,
        modifier = Modifier
          .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
          .clickable(onClick = onBack)
          .padding(horizontal = 10.dp, vertical = 2.dp),
      )
    }
    if (showLogo) {
      TetherLogo(size = 30.dp, modifier = Modifier.padding(start = 4.dp).size(30.dp))
    }
    Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
      Text(
        text = title,
        color = theme.textPrimary,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (subtitle != null) {
        Text(
          text = subtitle,
          color = theme.textMuted,
          style = MaterialTheme.typography.labelMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    actions()
  }
}

/** A compact, theme-tinted text action used in top bars (avoids icon deps). */
@Composable
fun TopBarTextAction(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  tint: Color? = null,
) {
  val theme = LocalTetherTheme.current
  Text(
    text = label,
    color = tint ?: theme.textSecondary,
    style = MaterialTheme.typography.labelLarge,
    modifier = modifier
      .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 10.dp, vertical = 8.dp),
  )
}
