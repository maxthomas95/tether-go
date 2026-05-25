package com.tether.go.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
  primary = Color(0xFF1769AA),
  onPrimary = Color(0xFFFFFFFF),
  background = Color(0xFFF8FAFC),
  onBackground = Color(0xFF17212B),
  surface = Color(0xFFFFFFFF),
  onSurface = Color(0xFF17212B),
)

private val DarkColorScheme = darkColorScheme(
  primary = Color(0xFF7DD3FC),
  onPrimary = Color(0xFF06202F),
  background = Color(0xFF101820),
  onBackground = Color(0xFFE6EDF3),
  surface = Color(0xFF16222C),
  onSurface = Color(0xFFE6EDF3),
)

@Composable
fun TetherGoTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
    typography = Typography(),
    content = content,
  )
}
