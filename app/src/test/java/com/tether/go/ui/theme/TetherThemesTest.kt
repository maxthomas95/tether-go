package com.tether.go.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

class TetherThemesTest {
  @Test
  fun registryKeepsExistingStorageIdsAndPickerOrderWhileAddingDesktopThemes() {
    val names = listOf(
      "mocha", "macchiato", "frappe", "latte", "default-dark", "tether-light", "tether",
      "nord", "gruvbox-dark", "gruvbox-light", "harbor",
    )
    assertEquals(names, TetherThemes.all.map { it.name })
    assertEquals(names.size, TetherThemes.all.map { it.name }.toSet().size)
    assertEquals(names.size, TetherThemes.all.map { it.label }.toSet().size)
    TetherThemes.all.forEach { theme ->
      assertSame(theme.name, theme, TetherThemes.byName(theme.name))
    }
  }

  @Test
  fun absentOrUnrecognizedPreferencesStillFallBackToMocha() {
    assertSame(TetherThemes.Mocha, TetherThemes.default)
    listOf(null, "", "unknown-theme").forEach { name ->
      assertSame(TetherThemes.Mocha, TetherThemes.byName(name))
    }
  }

  @Test
  fun nativeAnsiPalettesAreIndependentSnapshots() {
    TetherThemes.all.forEach { theme ->
      val original = theme.ansiPalette()
      val mutableCopy = theme.ansiPalette()
      assertEquals(theme.name, 16, original.size)
      assertNotSame(theme.name, original, mutableCopy)
      mutableCopy[0] = 0
      assertArrayEquals(theme.name, original, theme.ansiPalette())
      assertEquals(theme.name, original[0], theme.ansi.first().toArgb())
    }
  }

  @Test
  fun cssSelectionAlphaRemainsInTheArgbAlphaChannel() {
    assertEquals(0x4d585b70, TetherThemes.Mocha.terminalSelectionBg.toArgb())
    assertEquals(0x80acb0be.toInt(), TetherThemes.Latte.terminalSelectionBg.toArgb())
    assertEquals(0xff31535f.toInt(), TetherThemes.Harbor.terminalSelectionBg.toArgb())
  }
}

/**
 * Snapshot of all corresponding colors from desktop `src/shared/themes.ts`
 * at c5a7cdd. Keep these expectations independent of the production hex parser.
 * Tether Dark's explicit native Dark+ ANSI fallback is intentionally retained:
 * desktop does not specify that palette, instead relying on xterm defaults.
 */
@RunWith(Parameterized::class)
class TetherThemePaletteTest(private val expected: ExpectedTheme) {
  private val theme = TetherThemes.byName(expected.name)

  @Test
  fun metadataMatchesDesktop() {
    assertEquals(expected.name, theme.name)
    assertEquals(expected.label, theme.label)
    assertEquals(expected.isDark, theme.isDark)
  }

  @Test
  fun chromeColorsMatchDesktop() {
    assertColors(
      expected.chromeArgb,
      listOf(
        "bgPrimary" to theme.bgPrimary,
        "bgSidebar" to theme.bgSidebar,
        "bgHeader" to theme.bgHeader,
        "bgHover" to theme.bgHover,
        "bgActive" to theme.bgActive,
        "textPrimary" to theme.textPrimary,
        "textSecondary" to theme.textSecondary,
        "textMuted" to theme.textMuted,
        "border" to theme.border,
        "accent" to theme.accent,
        "statusRunning" to theme.statusRunning,
        "statusWaiting" to theme.statusWaiting,
        "statusIdle" to theme.statusIdle,
        "statusDead" to theme.statusDead,
        "btnPrimaryText" to theme.btnPrimaryText,
      ),
    )
  }

  @Test
  fun terminalColorsMatchDesktop() {
    assertColors(
      expected.terminalArgb,
      listOf(
        "terminalBg" to theme.terminalBg,
        "terminalFg" to theme.terminalFg,
        "terminalCursor" to theme.terminalCursor,
        "terminalSelectionBg" to theme.terminalSelectionBg,
      ),
    )
  }

  @Test
  fun nativeAnsiColorsHaveCorrectOrderAndArgbValues() {
    val palette = expected.ansiArgb.toArgbArray()
    assertEquals(16, theme.ansi.size)
    assertArrayEquals(expected.name, palette, theme.ansiPalette())
    assertArrayEquals(expected.name, palette, theme.ansi.map { it.toArgb() }.toIntArray())
  }

  private fun assertColors(expectedArgb: String, actual: List<Pair<String, Color>>) {
    val colors = expectedArgb.toArgbArray()
    assertEquals(expected.name, colors.size, actual.size)
    actual.forEachIndexed { index, (token, color) ->
      assertEquals("${expected.name}.$token", colors[index], color.toArgb())
    }
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun themes(): List<Array<Any>> = listOf(
      ExpectedTheme(
        name = "mocha", label = "Catppuccin Mocha", isDark = true,
        chromeArgb = "ff1e1e2e ff181825 ff313244 ff45475a ff585b70 ffcdd6f4 ffbac2de ffa0a4ba ff585b70 ffb4befe ffa6e3a1 fff9e2af ff7f849c fff38ba8 ff1e1e2e",
        terminalArgb = "ff1e1e2e ffcdd6f4 fff5e0dc 4d585b70",
        ansiArgb = "ff45475a fff38ba8 ffa6e3a1 fff9e2af ff89b4fa fff5c2e7 ff94e2d5 ffbac2de " +
          "ff585b70 fff38ba8 ffa6e3a1 fff9e2af ff89b4fa fff5c2e7 ff94e2d5 ffa6adc8",
      ),
      ExpectedTheme(
        name = "macchiato", label = "Catppuccin Macchiato", isDark = true,
        chromeArgb = "ff24273a ff1e2030 ff363a4f ff494d64 ff5b6078 ffcad3f5 ffb8c0e0 ffa0a6c0 ff5b6078 ffb7bdf8 ffa6da95 ffeed49f ff8087a2 ffed8796 ff24273a",
        terminalArgb = "ff24273a ffcad3f5 fff4dbd6 4d5b6078",
        ansiArgb = "ff494d64 ffed8796 ffa6da95 ffeed49f ff8aadf4 fff5bde6 ff8bd5ca ffb8c0e0 " +
          "ff5b6078 ffed8796 ffa6da95 ffeed49f ff8aadf4 fff5bde6 ff8bd5ca ffa5adcb",
      ),
      ExpectedTheme(
        name = "frappe", label = "Catppuccin Frappé", isDark = true,
        chromeArgb = "ff303446 ff292c3c ff414559 ff51576d ff626880 ffc6d0f5 ffb5bfe2 ffadb3cc ff626880 ffbabbf1 ffa6d189 ffe5c890 ff838ba7 ffe78284 ff303446",
        terminalArgb = "ff303446 ffc6d0f5 fff2d5cf 4d626880",
        ansiArgb = "ff51576d ffe78284 ffa6d189 ffe5c890 ff8caaee fff4b8e4 ff81c8be ffb5bfe2 " +
          "ff626880 ffe78284 ffa6d189 ffe5c890 ff8caaee fff4b8e4 ff81c8be ffa5adce",
      ),
      ExpectedTheme(
        name = "latte", label = "Catppuccin Latte", isDark = false,
        chromeArgb = "ffeff1f5 ffe6e9ef ffccd0da ffbcc0cc ffacb0be ff4c4f69 ff5c5f77 ff626579 ffacb0be ff7287fd ff40a02b ffdf8e1d ff8c8fa1 ffd20f39 ffeff1f5",
        terminalArgb = "ffeff1f5 ff4c4f69 ffdc8a78 80acb0be",
        ansiArgb = "ff5c5f77 ffd20f39 ff40a02b ffdf8e1d ff1e66f5 ffea76cb ff179299 ffacb0be " +
          "ff6c6f85 ffd20f39 ff40a02b ffdf8e1d ff1e66f5 ffea76cb ff179299 ffbcc0cc",
      ),
      ExpectedTheme(
        name = "default-dark", label = "Tether (Default Dark)", isDark = true,
        chromeArgb = "ff171c22 ff1e252d ff252e38 ff303b47 ff344655 ffe7eef5 ffbdcbd8 ffa7b5c4 ff3d4a58 ff62d2eb ff22c55e ffeab308 ff6b7280 ffef4444 ff000000",
        terminalArgb = "ff171c22 ffe7eef5 ffe7eef5 ff264f78",
        ansiArgb = "ff000000 ffcd3131 ff0dbc79 ffe5e510 ff2472c8 ffbc3fbc ff11a8cd ffe5e5e5 " +
          "ff666666 fff14c4c ff23d18b fff5f543 ff3b8eea ffd670d6 ff29b8db ffffffff",
      ),
      ExpectedTheme(
        name = "tether-light", label = "Tether Light", isDark = false,
        chromeArgb = "ffffffff fff3f3f3 ffececec ffe8e8e8 ffd6d6d6 ff1f1f1f ff616161 ff646b78 ffd4d4d4 ff0078d4 ff16a34a ffd97706 ff9ca3af ffdc2626 ffffffff",
        terminalArgb = "ffffffff ff1f1f1f ff1f1f1f ffadd6ff",
        ansiArgb = "ff000000 ffcd3131 ff00bc00 ff949800 ff0451a5 ffbc05bc ff0598bc ff555555 " +
          "ff666666 ffcd3131 ff14ce14 ffb5ba00 ff0451a5 ffbc05bc ff0598bc ffa5a5a5",
      ),
      ExpectedTheme(
        name = "tether", label = "Brass", isDark = true,
        chromeArgb = "ff1f1c18 ff1a1714 ff2c2723 ff3a342d ff4a4239 ffece4d4 ffc9bfae ffad9f8b ff3a342d ffc68a5c ff22c55e ffeab308 ff6b7280 ffef4444 ff1f1c18",
        terminalArgb = "ff1f1c18 ffece4d4 ffc68a5c 4dc68a5c",
        ansiArgb = "ff3a342d ffe87b6e ffa8c97a ffe0bd6d ff7da7c2 ffd29ab4 ff7ec0bd ffc9bfae " +
          "ff4a4239 fff0908a ffbcd991 ffecc985 ff95b9d1 ffdcaec3 ff9ad2c5 ffece4d4",
      ),
      ExpectedTheme(
        name = "nord", label = "Nord", isDark = true,
        chromeArgb = "ff2e3440 ff272d38 ff3b4252 ff434c5e ff4c566a ffeceff4 ffd8dee9 ffb3bdce ff4c566a ff88c0d0 ffa3be8c ffebcb8b ffb3bdce ffbf616a ff2e3440",
        terminalArgb = "ff2e3440 ffd8dee9 ff88c0d0 ff434c5e",
        ansiArgb = "ff3b4252 ffbf616a ffa3be8c ffebcb8b ff81a1c1 ffb48ead ff88c0d0 ffe5e9f0 " +
          "ff4c566a ffbf616a ffa3be8c ffebcb8b ff81a1c1 ffb48ead ff8fbcbb ffeceff4",
      ),
      ExpectedTheme(
        name = "gruvbox-dark", label = "Gruvbox Dark", isDark = true,
        chromeArgb = "ff282828 ff1d2021 ff32302f ff3c3836 ff504945 ffebdbb2 ffd5c4a1 ffbdae93 ff665c54 fffe8019 ffb8bb26 fffabd2f ffbdae93 fffb4934 ff282828",
        terminalArgb = "ff282828 ffebdbb2 fffe8019 ff504945",
        ansiArgb = "ff282828 ffcc241d ff98971a ffd79921 ff458588 ffb16286 ff689d6a ffa89984 " +
          "ff928374 fffb4934 ffb8bb26 fffabd2f ff83a598 ffd3869b ff8ec07c ffebdbb2",
      ),
      ExpectedTheme(
        name = "gruvbox-light", label = "Gruvbox Light", isDark = false,
        chromeArgb = "fffbf1c7 fff2e5bc ffebdbb2 ffebdbb2 ffd5c4a1 ff3c3836 ff504945 ff665c54 ffbdae93 ffaf3a03 ff427b58 ff8f6200 ff665c54 ff9d0006 fffbf1c7",
        terminalArgb = "fffbf1c7 ff3c3836 ffaf3a03 ffd5c4a1",
        ansiArgb = "ff3c3836 ff9d0006 ff79740e ffb57614 ff076678 ff8f3f71 ff427b58 ff7c6f64 " +
          "ff928374 ff9d0006 ff79740e ffb57614 ff076678 ff8f3f71 ff427b58 ff3c3836",
      ),
      ExpectedTheme(
        name = "harbor", label = "Harbor", isDark = true,
        chromeArgb = "ff15232d ff101b23 ff1c303b ff25414c ff31535f ffe7e3d5 ffc4d3d3 ffa4b9bb ff3e606b ff70c1b3 ff9bc88d ffe8c078 ffa4b9bb ffee8f88 ff101b23",
        terminalArgb = "ff15232d ffe7e3d5 ff70c1b3 ff31535f",
        ansiArgb = "ff25414c ffee8f88 ff9bc88d ffe8c078 ff83b4d5 ffcba3c8 ff70c1b3 ffc4d3d3 " +
          "ff6e929a ffffaaa0 ffb4dda1 fff5d595 ff9dcbea ffdfbadb ff98dacd fff4f0e5",
      ),
    ).map { arrayOf<Any>(it) }
  }
}

data class ExpectedTheme(
  val name: String,
  val label: String,
  val isDark: Boolean,
  // Same field order as the named assertions above; all values are ARGB, not CSS RGBA.
  val chromeArgb: String,
  val terminalArgb: String,
  // Standard ANSI 0–7, then bright ANSI 8–15.
  val ansiArgb: String,
) {
  override fun toString(): String = name
}

private fun String.toArgbArray(): IntArray =
  split(' ').map { it.toLong(16).toInt() }.toIntArray()
