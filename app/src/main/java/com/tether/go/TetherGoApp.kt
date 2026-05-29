package com.tether.go

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.tether.go.session.TerminalAppearance
import com.tether.go.ui.nav.Screen
import com.tether.go.ui.screens.HostsScreen
import com.tether.go.ui.screens.NewSessionScreen
import com.tether.go.ui.screens.SessionListScreen
import com.tether.go.ui.screens.SettingsScreen
import com.tether.go.ui.screens.TerminalScreen
import com.tether.go.ui.theme.TetherGoTheme
import com.tether.go.ui.theme.TetherTheme
import com.tether.go.ui.theme.TetherThemes
import org.connectbot.terminal.TerminalDimensions

private const val APP_VERSION = "v0.1.0"
private val DEFAULT_TERMINAL_SIZE = TerminalDimensions(rows = 32, columns = 96)

private fun appearanceFor(theme: TetherTheme) = TerminalAppearance(
  foreground = theme.terminalFg,
  background = theme.terminalBg,
  ansiPalette = theme.ansiPalette(),
)

/**
 * Application root. Reads the stores and [com.tether.go.session.SessionManager]
 * from the bound [SessionService] (the process-scoped owner), tracks the
 * selected theme/font, and drives a small in-memory navigation back stack.
 * Connect actions promote the service to the foreground so sessions survive
 * backgrounding.
 */
@Composable
fun TetherGoApp(
  service: SessionService,
  pendingSessionId: String?,
  onPendingConsumed: () -> Unit,
) {
  val context = LocalContext.current
  val hostStore = service.hostStore
  val privateKeyStore = service.privateKeyStore
  val settings = service.settings
  val manager = service.sessionManager

  var themeName by remember { mutableStateOf(settings.themeName()) }
  var fontSize by remember { mutableIntStateOf(settings.fontSize()) }
  var notificationsEnabled by remember { mutableStateOf(service.notificationsEnabled()) }
  val theme = TetherThemes.byName(themeName)

  // Under forced edge-to-edge (targetSdk 35+) the system bars are transparent and
  // sit over the app background, so the battery/clock/nav icons must contrast the
  // active theme: light icons on dark themes, dark icons on light ones. Re-runs
  // when the theme changes in Settings.
  val view = LocalView.current
  SideEffect {
    view.context.findActivity()?.window?.let { window ->
      WindowCompat.getInsetsController(window, view).apply {
        isAppearanceLightStatusBars = !theme.isDark
        isAppearanceLightNavigationBars = !theme.isDark
      }
    }
  }

  val sessions by manager.sessions.collectAsState()

  fun ensureForeground() {
    ContextCompat.startForegroundService(context, SessionService.intent(context))
  }

  TetherGoTheme(theme) {
    val backStack = remember { mutableStateListOf<Screen>(Screen.SessionList) }
    fun navigate(screen: Screen) {
      backStack.add(screen)
    }
    fun pop() {
      if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }
    BackHandler(enabled = backStack.size > 1) { pop() }

    // A notification tap routes straight to that session's terminal.
    LaunchedEffect(pendingSessionId) {
      val target = pendingSessionId ?: return@LaunchedEffect
      if (manager.sessionMetadata(target) != null) {
        if (backStack.lastOrNull() != Screen.Terminal(target)) navigate(Screen.Terminal(target))
      }
      onPendingConsumed()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = theme.bgPrimary) {
      // Keep the Surface full-bleed so the themed background paints behind the
      // transparent system bars, but inset the actual screen content with
      // safeDrawingPadding so nothing draws under the status bar or the gesture/
      // nav bar — otherwise bottom buttons land in the nav-bar region and can't
      // be tapped. (safeDrawing also consumes the IME inset; TerminalScreen's own
      // imePadding becomes a no-op rather than double-padding.)
      Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        when (val current = backStack.last()) {
          Screen.SessionList -> SessionListScreen(
            sessions = sessions,
            onOpenSession = { navigate(Screen.Terminal(it)) },
            onNewSession = { navigate(Screen.NewSession) },
            onOpenHosts = { navigate(Screen.Hosts) },
            onOpenSettings = { navigate(Screen.Settings) },
            onRename = { id, label -> manager.rename(id, label) },
            onDisconnect = { manager.disconnect(it) },
            onReconnect = { id ->
              ensureForeground()
              manager.reconnect(id, appearanceFor(theme), DEFAULT_TERMINAL_SIZE)
            },
            onRemove = { manager.removeSession(it) },
            canReconnect = { manager.canReconnectSilently(it) },
          )

          Screen.NewSession -> NewSessionScreen(
            hostStore = hostStore,
            privateKeyStore = privateKeyStore,
            onCancel = { pop() },
            onStart = { draft, auth ->
              ensureForeground()
              val id = manager.createSession(draft, auth, appearanceFor(theme), DEFAULT_TERMINAL_SIZE)
              backStack.removeAt(backStack.lastIndex)
              navigate(Screen.Terminal(id))
            },
          )

          is Screen.Terminal -> TerminalScreen(
            sessionId = current.sessionId,
            manager = manager,
            fontSize = fontSize,
            onBack = { pop() },
            onEnsureForeground = { ensureForeground() },
          )

          Screen.Hosts -> HostsScreen(
            hostStore = hostStore,
            privateKeyStore = privateKeyStore,
            onBack = { pop() },
          )

          Screen.Settings -> SettingsScreen(
            currentThemeName = themeName,
            onThemeChange = { name ->
              themeName = name
              settings.setThemeName(name)
            },
            fontSize = fontSize,
            onFontSizeChange = { size ->
              fontSize = size
              settings.setFontSize(size)
            },
            notificationsEnabled = notificationsEnabled,
            onNotificationsChange = { enabled ->
              notificationsEnabled = enabled
              service.setNotificationsEnabled(enabled)
            },
            appVersion = APP_VERSION,
            onBack = { pop() },
          )
        }
      }
    }
  }
}

/** Unwrap the (possibly themed/wrapped) Compose context to its host [Activity]. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}
