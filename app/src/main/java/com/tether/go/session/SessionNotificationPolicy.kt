package com.tether.go.session

/**
 * Phone-owned alert policy, independent of the passive detector and transport.
 * Being active only suppresses a session while its terminal is in the foreground.
 */
internal fun shouldNotifySession(
  session: Session,
  notificationsEnabled: Boolean,
  appForeground: Boolean,
  activeSessionId: String?,
  connected: Boolean,
): Boolean = notificationsEnabled &&
  !session.notificationsMuted &&
  connected &&
  !(appForeground && activeSessionId == session.id)
