package com.tether.go

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tether.go.session.SessionManager
import com.tether.go.session.SharedPreferencesSessionStore
import com.tether.go.settings.SettingsStore
import com.tether.go.ssh.AndroidSshPrivateKeyStore
import com.tether.go.ssh.SharedPreferencesSshHostStore

/**
 * Owns the app-wide stores and the [SessionManager] at ViewModel scope so live
 * SSH sessions and their persistent terminal buffers survive configuration
 * changes (rotation, theme/font changes) and are torn down only when the
 * Activity is genuinely finished — `onCleared` cancels [viewModelScope], which
 * matches the v0.1 "sessions live while the app is open" lifecycle.
 *
 * `viewModelScope` is confined to `Dispatchers.Main.immediate`, so the
 * SessionManager's per-session state collectors mutate its maps on a single
 * thread (no locking needed).
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
  val hostStore = SharedPreferencesSshHostStore(application)
  val privateKeyStore = AndroidSshPrivateKeyStore(application)
  val settings = SettingsStore(application)

  private val sessionStore = SharedPreferencesSessionStore(application)

  val sessionManager = SessionManager(
    scope = viewModelScope,
    hostStore = hostStore,
    privateKeyStore = privateKeyStore,
    store = sessionStore,
  )
}
