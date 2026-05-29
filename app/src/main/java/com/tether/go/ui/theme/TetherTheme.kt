package com.tether.go.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * Tether design tokens, ported 1:1 from the desktop Tether renderer theme
 * registry (`src/renderer/styles/themes.ts`) so the mobile client shares the
 * exact house palette and Catppuccin continuity.
 *
 * Each theme carries the product chrome tokens (backgrounds, text, accent,
 * status colors) plus a terminal palette (background/foreground/cursor and a
 * 16-entry ANSI table) used to color the ConnectBot `termlib` renderer. Keeping
 * both in one place means the terminal stream and the surrounding product UI
 * stay visually unified, which is the whole point of "feels like Tether".
 */
data class TetherTheme(
  val name: String,
  val label: String,
  val isDark: Boolean,
  // Product chrome
  val bgPrimary: Color,
  val bgSidebar: Color,
  val bgHeader: Color,
  val bgHover: Color,
  val bgActive: Color,
  val textPrimary: Color,
  val textSecondary: Color,
  val textMuted: Color,
  val border: Color,
  val accent: Color,
  val statusRunning: Color,
  val statusWaiting: Color,
  val statusIdle: Color,
  val statusDead: Color,
  val btnPrimaryText: Color,
  // Terminal
  val terminalBg: Color,
  val terminalFg: Color,
  val terminalCursor: Color,
  val terminalSelectionBg: Color,
  val ansi: List<Color>,
) {
  /** 16-entry ARGB palette for `TerminalEmulator.applyColorScheme`. */
  fun ansiPalette(): IntArray = IntArray(16) { index -> ansi[index].toArgb() }
}

/** Parse a `#rrggbb` or `#rrggbbaa` CSS hex string into a Compose [Color]. */
private fun hex(value: String): Color {
  val h = value.removePrefix("#")
  return when (h.length) {
    6 -> Color(
      red = h.substring(0, 2).toInt(16),
      green = h.substring(2, 4).toInt(16),
      blue = h.substring(4, 6).toInt(16),
    )
    8 -> Color(
      red = h.substring(0, 2).toInt(16),
      green = h.substring(2, 4).toInt(16),
      blue = h.substring(4, 6).toInt(16),
      alpha = h.substring(6, 8).toInt(16),
    )
    else -> error("Unsupported hex color: $value")
  }
}

private fun ansi(
  black: String, red: String, green: String, yellow: String,
  blue: String, magenta: String, cyan: String, white: String,
  brightBlack: String, brightRed: String, brightGreen: String, brightYellow: String,
  brightBlue: String, brightMagenta: String, brightCyan: String, brightWhite: String,
): List<Color> = listOf(
  hex(black), hex(red), hex(green), hex(yellow),
  hex(blue), hex(magenta), hex(cyan), hex(white),
  hex(brightBlack), hex(brightRed), hex(brightGreen), hex(brightYellow),
  hex(brightBlue), hex(brightMagenta), hex(brightCyan), hex(brightWhite),
)

object TetherThemes {
  // ── Catppuccin Mocha (default, matches desktop) ──────────────────────
  val Mocha = TetherTheme(
    name = "mocha",
    label = "Catppuccin Mocha",
    isDark = true,
    bgPrimary = hex("#1e1e2e"),
    bgSidebar = hex("#181825"),
    bgHeader = hex("#313244"),
    bgHover = hex("#45475a"),
    bgActive = hex("#585b70"),
    textPrimary = hex("#cdd6f4"),
    textSecondary = hex("#bac2de"),
    textMuted = hex("#6c7086"),
    border = hex("#585b70"),
    accent = hex("#b4befe"),
    statusRunning = hex("#a6e3a1"),
    statusWaiting = hex("#f9e2af"),
    statusIdle = hex("#7f849c"),
    statusDead = hex("#f38ba8"),
    btnPrimaryText = hex("#1e1e2e"),
    terminalBg = hex("#1e1e2e"),
    terminalFg = hex("#cdd6f4"),
    terminalCursor = hex("#f5e0dc"),
    terminalSelectionBg = hex("#585b704d"),
    ansi = ansi(
      "#45475a", "#f38ba8", "#a6e3a1", "#f9e2af", "#89b4fa", "#f5c2e7", "#94e2d5", "#bac2de",
      "#585b70", "#f38ba8", "#a6e3a1", "#f9e2af", "#89b4fa", "#f5c2e7", "#94e2d5", "#a6adc8",
    ),
  )

  // ── Catppuccin Macchiato ─────────────────────────────────────────────
  val Macchiato = TetherTheme(
    name = "macchiato",
    label = "Catppuccin Macchiato",
    isDark = true,
    bgPrimary = hex("#24273a"),
    bgSidebar = hex("#1e2030"),
    bgHeader = hex("#363a4f"),
    bgHover = hex("#494d64"),
    bgActive = hex("#5b6078"),
    textPrimary = hex("#cad3f5"),
    textSecondary = hex("#b8c0e0"),
    textMuted = hex("#6e738d"),
    border = hex("#5b6078"),
    accent = hex("#b7bdf8"),
    statusRunning = hex("#a6da95"),
    statusWaiting = hex("#eed49f"),
    statusIdle = hex("#8087a2"),
    statusDead = hex("#ed8796"),
    btnPrimaryText = hex("#24273a"),
    terminalBg = hex("#24273a"),
    terminalFg = hex("#cad3f5"),
    terminalCursor = hex("#f4dbd6"),
    terminalSelectionBg = hex("#5b60784d"),
    ansi = ansi(
      "#494d64", "#ed8796", "#a6da95", "#eed49f", "#8aadf4", "#f5bde6", "#8bd5ca", "#b8c0e0",
      "#5b6078", "#ed8796", "#a6da95", "#eed49f", "#8aadf4", "#f5bde6", "#8bd5ca", "#a5adcb",
    ),
  )

  // ── Catppuccin Frappé ─────────────────────────────────────────────────
  val Frappe = TetherTheme(
    name = "frappe",
    label = "Catppuccin Frappé",
    isDark = true,
    bgPrimary = hex("#303446"),
    bgSidebar = hex("#292c3c"),
    bgHeader = hex("#414559"),
    bgHover = hex("#51576d"),
    bgActive = hex("#626880"),
    textPrimary = hex("#c6d0f5"),
    textSecondary = hex("#b5bfe2"),
    textMuted = hex("#737994"),
    border = hex("#626880"),
    accent = hex("#babbf1"),
    statusRunning = hex("#a6d189"),
    statusWaiting = hex("#e5c890"),
    statusIdle = hex("#838ba7"),
    statusDead = hex("#e78284"),
    btnPrimaryText = hex("#303446"),
    terminalBg = hex("#303446"),
    terminalFg = hex("#c6d0f5"),
    terminalCursor = hex("#f2d5cf"),
    terminalSelectionBg = hex("#6268804d"),
    ansi = ansi(
      "#51576d", "#e78284", "#a6d189", "#e5c890", "#8caaee", "#f4b8e4", "#81c8be", "#b5bfe2",
      "#626880", "#e78284", "#a6d189", "#e5c890", "#8caaee", "#f4b8e4", "#81c8be", "#a5adce",
    ),
  )

  // ── Catppuccin Latte (light) ──────────────────────────────────────────
  val Latte = TetherTheme(
    name = "latte",
    label = "Catppuccin Latte",
    isDark = false,
    bgPrimary = hex("#eff1f5"),
    bgSidebar = hex("#e6e9ef"),
    bgHeader = hex("#ccd0da"),
    bgHover = hex("#bcc0cc"),
    bgActive = hex("#acb0be"),
    textPrimary = hex("#4c4f69"),
    textSecondary = hex("#5c5f77"),
    textMuted = hex("#9ca0b0"),
    border = hex("#acb0be"),
    accent = hex("#7287fd"),
    statusRunning = hex("#40a02b"),
    statusWaiting = hex("#df8e1d"),
    statusIdle = hex("#8c8fa1"),
    statusDead = hex("#d20f39"),
    btnPrimaryText = hex("#eff1f5"),
    terminalBg = hex("#eff1f5"),
    terminalFg = hex("#4c4f69"),
    terminalCursor = hex("#dc8a78"),
    terminalSelectionBg = hex("#acb0be80"),
    ansi = ansi(
      "#5c5f77", "#d20f39", "#40a02b", "#df8e1d", "#1e66f5", "#ea76cb", "#179299", "#acb0be",
      "#6c6f85", "#d20f39", "#40a02b", "#df8e1d", "#1e66f5", "#ea76cb", "#179299", "#bcc0cc",
    ),
  )

  // ── Tether (Default Dark) — neutral house theme ───────────────────────
  val TetherDark = TetherTheme(
    name = "default-dark",
    label = "Tether (Default Dark)",
    isDark = true,
    bgPrimary = hex("#1e1e1e"),
    bgSidebar = hex("#252526"),
    bgHeader = hex("#2d2d2d"),
    bgHover = hex("#2a2d2e"),
    bgActive = hex("#37373d"),
    textPrimary = hex("#cccccc"),
    textSecondary = hex("#858585"),
    textMuted = hex("#5a5a5a"),
    border = hex("#3c3c3c"),
    accent = hex("#4fc1e9"),
    statusRunning = hex("#22c55e"),
    statusWaiting = hex("#eab308"),
    statusIdle = hex("#6b7280"),
    statusDead = hex("#ef4444"),
    btnPrimaryText = hex("#000000"),
    terminalBg = hex("#1e1e1e"),
    terminalFg = hex("#cccccc"),
    terminalCursor = hex("#cccccc"),
    terminalSelectionBg = hex("#264f78"),
    // Desktop's default-dark xterm has no ANSI table; use the VS Code Dark+ set.
    ansi = ansi(
      "#000000", "#cd3131", "#0dbc79", "#e5e510", "#2472c8", "#bc3fbc", "#11a8cd", "#e5e5e5",
      "#666666", "#f14c4c", "#23d18b", "#f5f543", "#3b8eea", "#d670d6", "#29b8db", "#ffffff",
    ),
  )

  // ── Tether Light ──────────────────────────────────────────────────────
  val TetherLight = TetherTheme(
    name = "tether-light",
    label = "Tether Light",
    isDark = false,
    bgPrimary = hex("#ffffff"),
    bgSidebar = hex("#f3f3f3"),
    bgHeader = hex("#ececec"),
    bgHover = hex("#e8e8e8"),
    bgActive = hex("#d6d6d6"),
    textPrimary = hex("#1f1f1f"),
    textSecondary = hex("#616161"),
    textMuted = hex("#8c8c8c"),
    border = hex("#d4d4d4"),
    accent = hex("#0078d4"),
    statusRunning = hex("#16a34a"),
    statusWaiting = hex("#d97706"),
    statusIdle = hex("#9ca3af"),
    statusDead = hex("#dc2626"),
    btnPrimaryText = hex("#ffffff"),
    terminalBg = hex("#ffffff"),
    terminalFg = hex("#1f1f1f"),
    terminalCursor = hex("#1f1f1f"),
    terminalSelectionBg = hex("#add6ff"),
    ansi = ansi(
      "#000000", "#cd3131", "#00bc00", "#949800", "#0451a5", "#bc05bc", "#0598bc", "#555555",
      "#666666", "#cd3131", "#14ce14", "#b5ba00", "#0451a5", "#bc05bc", "#0598bc", "#a5a5a5",
    ),
  )

  // ── Brass (warm identity palette) ─────────────────────────────────────
  val Brass = TetherTheme(
    name = "tether",
    label = "Brass",
    isDark = true,
    bgPrimary = hex("#1f1c18"),
    bgSidebar = hex("#1a1714"),
    bgHeader = hex("#2c2723"),
    bgHover = hex("#3a342d"),
    bgActive = hex("#4a4239"),
    textPrimary = hex("#ece4d4"),
    textSecondary = hex("#c9bfae"),
    textMuted = hex("#807565"),
    border = hex("#3a342d"),
    accent = hex("#c68a5c"),
    statusRunning = hex("#22c55e"),
    statusWaiting = hex("#eab308"),
    statusIdle = hex("#6b7280"),
    statusDead = hex("#ef4444"),
    btnPrimaryText = hex("#1f1c18"),
    terminalBg = hex("#1f1c18"),
    terminalFg = hex("#ece4d4"),
    terminalCursor = hex("#c68a5c"),
    terminalSelectionBg = hex("#c68a5c4d"),
    ansi = ansi(
      "#3a342d", "#e87b6e", "#a8c97a", "#e0bd6d", "#7da7c2", "#d29ab4", "#7ec0bd", "#c9bfae",
      "#4a4239", "#f0908a", "#bcd991", "#ecc985", "#95b9d1", "#dcaec3", "#9ad2c5", "#ece4d4",
    ),
  )

  /** Display order used by the theme picker; Mocha first to match desktop. */
  val all: List<TetherTheme> = listOf(
    Mocha, Macchiato, Frappe, Latte, TetherDark, TetherLight, Brass,
  )

  val default: TetherTheme = Mocha

  fun byName(name: String?): TetherTheme =
    all.firstOrNull { it.name == name } ?: default
}

/** Current Tether theme, provided at the app root via [TetherGoTheme]. */
val LocalTetherTheme = staticCompositionLocalOf { TetherThemes.default }
