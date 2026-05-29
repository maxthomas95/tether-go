package com.tether.go.ui.nav

/** Tether Go navigation destinations, driven by a simple in-memory back stack. */
sealed interface Screen {
  data object SessionList : Screen
  data object NewSession : Screen
  data class Terminal(val sessionId: String) : Screen
  data object Hosts : Screen
  data object Settings : Screen
}
