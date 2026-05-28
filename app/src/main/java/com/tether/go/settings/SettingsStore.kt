package com.tether.go.settings

import android.content.Context

/** Lightweight app preferences: selected theme and terminal font size. */
class SettingsStore(context: Context) {
  private val prefs = context.applicationContext
    .getSharedPreferences("tether_go_settings", Context.MODE_PRIVATE)

  fun themeName(): String = prefs.getString(KEY_THEME, null) ?: DEFAULT_THEME

  fun setThemeName(name: String) {
    prefs.edit().putString(KEY_THEME, name).apply()
  }

  fun fontSize(): Int = prefs.getInt(KEY_FONT_SIZE, DEFAULT_FONT_SIZE)

  fun setFontSize(size: Int) {
    prefs.edit().putInt(KEY_FONT_SIZE, size.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)).apply()
  }

  companion object {
    const val DEFAULT_THEME = "mocha"
    const val DEFAULT_FONT_SIZE = 13
    const val MIN_FONT_SIZE = 8
    const val MAX_FONT_SIZE = 22
    private const val KEY_THEME = "theme"
    private const val KEY_FONT_SIZE = "font_size"
  }
}
