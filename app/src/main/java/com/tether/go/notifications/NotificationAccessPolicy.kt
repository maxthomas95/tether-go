package com.tether.go.notifications

/** Android access is independent of the user's in-app session-alert preference. */
data class NotificationAccessState(
  val sdkInt: Int,
  val permissionGranted: Boolean,
  val systemNotificationsEnabled: Boolean,
  /** Null means the channel has not been created, not that it is enabled. */
  val sessionChannelEnabled: Boolean?,
  val permissionRequestAttempted: Boolean,
)

enum class NotificationAccessStatus {
  PERMISSION_REQUIRED,
  APP_BLOCKED,
  CHANNEL_BLOCKED,
  CHANNEL_UNAVAILABLE,
  ALLOWED,
}

enum class NotificationAccessAction {
  REQUEST_PERMISSION,
  OPEN_APP_SETTINGS,
  OPEN_CHANNEL_SETTINGS,
}

data class NotificationAccessPolicy(
  val status: NotificationAccessStatus,
  val action: NotificationAccessAction,
  /** Permission to post, not a guarantee of delivery or an audible alert. */
  val sessionAlertsAllowed: Boolean,
)

/** Chooses an affordance only; callers must never execute it without a user action. */
fun notificationAccessPolicy(
  appPreferenceEnabled: Boolean,
  access: NotificationAccessState,
): NotificationAccessPolicy {
  val status = when {
    access.sdkInt >= 33 && !access.permissionGranted -> NotificationAccessStatus.PERMISSION_REQUIRED
    !access.systemNotificationsEnabled -> NotificationAccessStatus.APP_BLOCKED
    access.sessionChannelEnabled == null -> NotificationAccessStatus.CHANNEL_UNAVAILABLE
    !access.sessionChannelEnabled -> NotificationAccessStatus.CHANNEL_BLOCKED
    else -> NotificationAccessStatus.ALLOWED
  }
  val action = when {
    status == NotificationAccessStatus.PERMISSION_REQUIRED && !access.permissionRequestAttempted ->
      NotificationAccessAction.REQUEST_PERMISSION
    status == NotificationAccessStatus.CHANNEL_BLOCKED -> NotificationAccessAction.OPEN_CHANNEL_SETTINGS
    else -> NotificationAccessAction.OPEN_APP_SETTINGS
  }
  return NotificationAccessPolicy(
    status = status,
    action = action,
    sessionAlertsAllowed = appPreferenceEnabled && status == NotificationAccessStatus.ALLOWED,
  )
}
