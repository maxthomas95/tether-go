package com.tether.go.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Root Tether Go theme. Derives a Material3 [androidx.compose.material3.ColorScheme]
 * from the selected [TetherTheme] tokens and also publishes the full token set
 * through [LocalTetherTheme] so custom components (status dots, CLI chips,
 * terminal palette) can read Tether-specific colors that Material3 does not model.
 */
@Composable
fun TetherGoTheme(
  theme: TetherTheme = TetherThemes.default,
  content: @Composable () -> Unit,
) {
  // Start from the Material baseline for light/dark, then map Tether tokens
  // onto it in a single place (avoids duplicating the assignment block).
  val base = if (theme.isDark) darkColorScheme() else lightColorScheme()
  val colorScheme = base.copy(
    primary = theme.accent,
    onPrimary = theme.btnPrimaryText,
    secondary = theme.accent,
    onSecondary = theme.btnPrimaryText,
    background = theme.bgPrimary,
    onBackground = theme.textPrimary,
    surface = theme.bgSidebar,
    onSurface = theme.textPrimary,
    surfaceVariant = theme.bgHeader,
    onSurfaceVariant = theme.textSecondary,
    surfaceContainer = theme.bgHeader,
    surfaceContainerHigh = theme.bgHover,
    outline = theme.border,
    outlineVariant = theme.border,
    error = theme.statusDead,
  )

  CompositionLocalProvider(LocalTetherTheme provides theme) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = TetherTypography,
      content = content,
    )
  }
}
