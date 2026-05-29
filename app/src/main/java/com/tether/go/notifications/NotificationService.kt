package com.tether.go.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tether.go.MainActivity
import com.tether.go.R

/**
 * Posts local, phone-owned notifications via the system NotificationManager.
 * No server or relay is involved: these fire only for sessions Tether Go owns
 * and is currently observing. Two channels — a quiet ongoing channel for the
 * foreground service, and a high-importance channel for "needs you" pings.
 */
class NotificationService(private val context: Context) {
  init {
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
      NotificationChannel(CHANNEL_RUNNING, "Running sessions", NotificationManager.IMPORTANCE_LOW).apply {
        description = "Shows while Tether Go is keeping SSH sessions alive."
        setShowBadge(false)
      },
    )
    manager.createNotificationChannel(
      NotificationChannel(CHANNEL_STATUS, "Session activity", NotificationManager.IMPORTANCE_HIGH).apply {
        description = "Pings when a session is waiting for your input."
      },
    )
  }

  /** Persistent notification shown while the foreground service holds sessions. */
  fun foregroundNotification(activeCount: Int): Notification =
    NotificationCompat.Builder(context, CHANNEL_RUNNING)
      .setSmallIcon(R.drawable.ic_stat_tether)
      .setContentTitle("Tether Go")
      .setContentText(
        when {
          activeCount <= 0 -> "Keeping sessions ready"
          activeCount == 1 -> "1 session active"
          else -> "$activeCount sessions active"
        },
      )
      .setOngoing(true)
      .setContentIntent(openAppIntent(null))
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .build()

  /** Fire a "needs you" ping for [sessionId]; tapping opens that session. */
  fun notifySession(sessionId: String, label: String, detail: String, isBell: Boolean) {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
    val notification = NotificationCompat.Builder(context, CHANNEL_STATUS)
      .setSmallIcon(R.drawable.ic_stat_tether)
      .setContentTitle(if (isBell) "$label rang the bell" else "$label is waiting for input")
      .setContentText(detail)
      .setContentIntent(openAppIntent(sessionId))
      .setAutoCancel(true)
      .setCategory(NotificationCompat.CATEGORY_MESSAGE)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .build()
    runCatching {
      NotificationManagerCompat.from(context).notify(notificationIdFor(sessionId), notification)
    }
  }

  fun cancelSession(sessionId: String) {
    NotificationManagerCompat.from(context).cancel(notificationIdFor(sessionId))
  }

  private fun openAppIntent(sessionId: String?): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
      if (sessionId != null) putExtra(EXTRA_SESSION_ID, sessionId)
    }
    return PendingIntent.getActivity(
      context,
      sessionId?.hashCode() ?: 0,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  private fun notificationIdFor(sessionId: String): Int = sessionId.hashCode()

  companion object {
    const val CHANNEL_RUNNING = "tether_go_running"
    const val CHANNEL_STATUS = "tether_go_sessions"
    const val FOREGROUND_NOTIFICATION_ID = 1
    const val EXTRA_SESSION_ID = "com.tether.go.SESSION_ID"
  }
}
