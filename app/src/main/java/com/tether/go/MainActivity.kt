package com.tether.go

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tether.go.notifications.NotificationService

class MainActivity : ComponentActivity() {
  private var boundService by mutableStateOf<SessionService?>(null)
  private var pendingSessionId by mutableStateOf<String?>(null)

  private val connection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
      boundService = (binder as? SessionService.LocalBinder)?.service
      boundService?.setAppForeground(true)
    }

    override fun onServiceDisconnected(name: ComponentName?) {
      boundService = null
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    pendingSessionId = intent?.getStringExtra(NotificationService.EXTRA_SESSION_ID)
    bindService(SessionService.intent(this), connection, BIND_AUTO_CREATE)
    setContent {
      val service = boundService
      if (service != null) {
        TetherGoApp(
          service = service,
          pendingSessionId = pendingSessionId,
          onPendingConsumed = { pendingSessionId = null },
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    intent.getStringExtra(NotificationService.EXTRA_SESSION_ID)?.let { pendingSessionId = it }
  }

  override fun onStart() {
    super.onStart()
    boundService?.setAppForeground(true)
  }

  override fun onStop() {
    super.onStop()
    boundService?.setAppForeground(false)
  }

  override fun onDestroy() {
    runCatching { unbindService(connection) }
    super.onDestroy()
  }
}
