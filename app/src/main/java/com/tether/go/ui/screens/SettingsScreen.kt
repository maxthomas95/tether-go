package com.tether.go.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tether.go.settings.SettingsStore
import com.tether.go.ui.branding.TetherLogo
import com.tether.go.ui.components.SectionHeader
import com.tether.go.ui.components.TetherTopBar
import com.tether.go.ui.theme.LocalTetherTheme
import com.tether.go.ui.theme.TetherThemes

/**
 * Settings: theme picker (full desktop palette set, Mocha default), terminal
 * font size, and an About card. Theme + font changes are applied live.
 */
@Composable
fun SettingsScreen(
  currentThemeName: String,
  onThemeChange: (String) -> Unit,
  fontSize: Int,
  onFontSizeChange: (Int) -> Unit,
  appVersion: String,
  onBack: () -> Unit,
) {
  val theme = LocalTetherTheme.current

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(theme.bgPrimary),
  ) {
    TetherTopBar(title = "Settings", onBack = onBack)

    Column(
      modifier = Modifier
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      SectionHeader("Theme")
      TetherThemes.all.forEach { candidate ->
        val selected = candidate.name == currentThemeName
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) theme.accent.copy(alpha = 0.15f) else theme.bgSidebar, RoundedCornerShape(10.dp))
            .border(1.dp, if (selected) theme.accent else theme.border, RoundedCornerShape(10.dp))
            .clickable { onThemeChange(candidate.name) }
            .padding(horizontal = 12.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Swatch(candidate.bgPrimary)
          Swatch(candidate.accent)
          Swatch(candidate.statusRunning)
          Text(
            candidate.label,
            color = theme.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
          )
          if (selected) {
            Text("✓", color = theme.accent, fontWeight = FontWeight.Bold)
          }
        }
      }

      SectionHeader("Terminal")
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(theme.bgSidebar, RoundedCornerShape(10.dp))
          .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Font size", color = theme.textPrimary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        FilledTonalButton(
          onClick = { onFontSizeChange((fontSize - 1).coerceAtLeast(SettingsStore.MIN_FONT_SIZE)) },
          shape = RoundedCornerShape(8.dp),
        ) { Text("−") }
        Text(
          "$fontSize",
          color = theme.textPrimary,
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.width(40.dp).padding(horizontal = 8.dp),
        )
        FilledTonalButton(
          onClick = { onFontSizeChange((fontSize + 1).coerceAtMost(SettingsStore.MAX_FONT_SIZE)) },
          shape = RoundedCornerShape(8.dp),
        ) { Text("+") }
      }

      SectionHeader("About")
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(theme.bgSidebar, RoundedCornerShape(10.dp))
          .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        TetherLogo(size = 56.dp)
        Spacer(Modifier.height(10.dp))
        Text("Tether Go $appVersion", color = theme.textPrimary, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text("Dumb pipe, smart shell.", color = theme.textMuted, style = MaterialTheme.typography.bodySmall)
      }
      Spacer(Modifier.height(16.dp))
    }
  }
}

@Composable
private fun Swatch(color: androidx.compose.ui.graphics.Color) {
  Box(
    modifier = Modifier
      .size(16.dp)
      .clip(CircleShape)
      .background(color, CircleShape),
  )
}
