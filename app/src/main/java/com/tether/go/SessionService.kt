package com.tether.go

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.tether.go.notifications.NotificationService
import com.tether.go.session.Session
import com.tether.go.session.SessionManager
import com.tether.go.session.SessionNotifier
import com.tether.go.session.SharedPreferencesSessionStore
import com.tether.go.settings.SettingsStore
import com.tether.go.ssh.AndroidSshPrivateKeyStore
import com.tether.go.ssh.SharedPreferencesSshHostStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Foreground service that owns the [SessionManager] for the whole process, so
 * phone-owned SSH sessions and their terminal buffers survive configuration
 * changes and backgrounding (screen off / app pocketed). It shows a persistent
 * "Tether Go — N sessions" notification while sessions are live, and tears
 * everything down when the app is intentionally closed (swiped away).
 *
 * Started + bound: the Activity binds for UI access, and the connect paths call
 * `startForegroundService` so the service can legitimately enter the foreground.
 */
class SessionService : Service() {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  lateinit var hostStore: SharedPreferencesSshHostStore
    private set
  lateinit var privateKeyStore: AndroidSshPrivateKeyStore
    private set
  lateinit var settings: SettingsStore
    private set
  lateinit var sessionManager: SessionManager
    private set

  private lateinit var notifications: NotificationService
  private var isForeground = false
  private var lastConnectedCount = 0

  inner class LocalBinder : Binder() {
    val service: SessionService get() = this@SessionService
  }

  private val binder = LocalBinder()

  override fun onCreate() {
    super.onCreate()
    hostStore = SharedPreferencesSshHostStore(this)
    privateKeyStore = AndroidSshPrivateKeyStore(this)
    settings = SettingsStore(this)
    notifications = NotificationService(this)
    sessionManager = SessionManager(scope, hostStore, privateKeyStore, SharedPreferencesSessionStore(this))
    sessionManager.notificationsEnabled = settings.notificationsEnabled()
    sessionManager.notifier = object : SessionNotifier {
      override fun notifyWaiting(session: Session, isBell: Boolean) {
        val detail = session.workingDir
          ?.takeIf { it.isNotBlank() }
          ?.let { "$it · ${session.endpointLabel}" }
          ?: session.endpointLabel
        notifications.notifySession(session.id, session.label, detail, isBell)
      }

      override fun cancel(sessionId: String) {
        notifications.cancelSession(sessionId)
      }
    }
    sessionManager.sessions.onEach { updateForeground() }.launchIn(scope)
  }

  override fun onBind(intent: Intent?): IBinder = binder

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    // Satisfy the startForegroundService 5s contract immediately.
    startForegroundCompat(sessionManager.connectedCount())
    return START_NOT_STICKY
  }

  fun setAppForeground(foreground: Boolean) {
    sessionManager.setAppForeground(foreground)
  }

  fun setNotificationsEnabled(enabled: Boolean) {
    settings.setNotificationsEnabled(enabled)
    sessionManager.notificationsEnabled = enabled
  }

  fun notificationsEnabled(): Boolean = settings.notificationsEnabled()

  override fun onTaskRemoved(rootIntent: Intent?) {
    // The user intentionally closed the app → phone-owned sessions die.
    sessionManager.disconnectAll()
    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    isForeground = false
    stopSelf()
    super.onTaskRemoved(rootIntent)
  }

  override fun onDestroy() {
    scope.cancel()
    super.onDestroy()
  }

  private fun updateForeground() {
    val count = sessionManager.connectedCount()
    if (count > 0) {
      startForegroundCompat(count)
    } else if (isForeground && lastConnectedCount > 0) {
      ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
      isForeground = false
      stopSelf()
    }
    lastConnectedCount = count
  }

  private fun startForegroundCompat(count: Int) {
    val notification = notifications.foregroundNotification(count)
    val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
    } else {
      0
    }
    ServiceCompat.startForeground(this, NotificationService.FOREGROUND_NOTIFICATION_ID, notification, type)
    isForeground = true
  }

  companion object {
    fun intent(context: android.content.Context): Intent = Intent(context, SessionService::class.java)
  }
}
