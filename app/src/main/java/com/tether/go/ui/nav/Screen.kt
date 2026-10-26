package com.tether.go.ui.nav

/** Tether Go navigation destinations, driven by a simple in-memory back stack. */
sealed interface Screen {
  data object SessionList : Screen
  /** New Session form, optionally prefilled with a saved host and directory. */
  data class NewSession(val hostId: String? = null, val workingDir: String? = null) : Screen
  data class Terminal(val sessionId: String) : Screen
  data object Hosts : Screen
  /** Add (`hostId == null`) or edit a saved host. */
  data class HostEditor(val hostId: String?) : Screen
  /** Browse, resume, and start Claude chats on a saved host. */
  data class HostChats(val hostId: String) : Screen
  data object Settings : Screen
}
