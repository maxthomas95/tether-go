package com.tether.go

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.tether.go.session.SessionUiModel
import com.tether.go.session.TerminalAppearance
import com.tether.go.ui.nav.Screen
import com.tether.go.ui.screens.EmptyDetailPane
import com.tether.go.ui.screens.ExpandedLayout
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

/**
 * Window width (post-inset) at or above which we switch to the two-pane desktop
 * layout. 840dp is the Material "Expanded" width breakpoint — it matches the
 * unfolded inner screen of book-style foldables and tablets, while the cover
 * screen and all phones stay below it and keep the single-column UI.
 */
private val EXPANDED_WIDTH = 840.dp

private fun appearanceFor(theme: TetherTheme) = TerminalAppearance(
  foreground = theme.terminalFg,
  background = theme.terminalBg,
  ansiPalette = theme.ansiPalette(),
)

/** Navigation actions bound differently by each layout (back stack vs detail pane). */
private class TetherNav(
  val openSession: (String) -> Unit,
  val newSession: () -> Unit,
  val openHosts: () -> Unit,
  val openSettings: () -> Unit,
  val back: () -> Unit,
  val onSessionCreated: (String) -> Unit,
)

/**
 * Application root. Reads the stores and [com.tether.go.session.SessionManager]
 * from the bound [SessionService] (the process-scoped owner), tracks the
 * selected theme/font, and renders one of two layouts off the current window
 * width:
 *  - **compact** (< [EXPANDED_WIDTH]) — the single-column in-memory back stack,
 *    unchanged from the phone build;
 *  - **expanded** (>= [EXPANDED_WIDTH]) — a persistent session-list sidebar plus
 *    a detail pane (the selected terminal, or New session / Hosts / Settings).
 *
 * Both layouts render screens through the shared [ScreenContent], so there is a
 * single definition per screen. The back stack and the detail selection are
 * hoisted above the width branch and synced on a fold/unfold flip, so switching
 * screens keeps the session you were looking at (the activity is not recreated —
 * `MainActivity` declares the relevant `configChanges`). Connect actions promote
 * the service to the foreground so sessions survive backgrounding.
 */
@Composable
fun TetherGoApp(
  service: SessionService,
  pendingSessionId: String?,
  onPendingConsumed: () -> Unit,
) {
  val context = LocalContext.current
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
    // Hoisted above the width branch so it survives a fold/unfold (which only
    // reconfigures, never recreates, the activity). `backStack` drives compact;
    // `detail` drives the expanded detail pane (null = empty placeholder).
    val backStack = remember { mutableStateListOf<Screen>(Screen.SessionList) }
    var detail by remember { mutableStateOf<Screen?>(null) }

    fun pop() {
      if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = theme.bgPrimary) {
      // Keep the Surface full-bleed so the themed background paints behind the
      // transparent system bars, but inset the actual screen content with
      // safeDrawingPadding so nothing draws under the status bar or the gesture/
      // nav bar. BoxWithConstraints then gives us the post-inset content width —
      // exactly the space we're deciding whether to split into two panes.
      BoxWithConstraints(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        val expanded = maxWidth >= EXPANDED_WIDTH

        // Carry the current screen across a layout flip so unfolding while
        // viewing terminal X keeps X open (and vice-versa).
        LaunchedEffect(expanded) {
          if (expanded) {
            detail = backStack.lastOrNull()?.takeUnless { it is Screen.SessionList }
          } else {
            val current = detail
            backStack.clear()
            backStack.add(Screen.SessionList)
            if (current != null && current !is Screen.SessionList) backStack.add(current)
          }
        }

        // A notification tap routes straight to that session's terminal, in
        // whichever layout is active.
        LaunchedEffect(pendingSessionId) {
          val target = pendingSessionId ?: return@LaunchedEffect
          if (manager.sessionMetadata(target) != null) {
            val terminal = Screen.Terminal(target)
            if (expanded) {
              detail = terminal
            } else if (backStack.lastOrNull() != terminal) {
              backStack.add(terminal)
            }
          }
          onPendingConsumed()
        }

        if (expanded) {
          // Hardware back closes a secondary screen (New session / Hosts /
          // Settings) back to the empty pane; terminals don't trap back.
          BackHandler(enabled = detail != null && detail !is Screen.Terminal) { detail = null }

          val sidebarNav = TetherNav(
            openSession = { detail = Screen.Terminal(it) },
            newSession = { detail = Screen.NewSession },
            openHosts = { detail = Screen.Hosts },
            openSettings = { detail = Screen.Settings },
            back = { detail = null },
            onSessionCreated = { detail = Screen.Terminal(it) },
          )

          ExpandedLayout(
            sidebar = {
              ScreenContent(
                screen = Screen.SessionList,
                nav = sidebarNav,
                service = service,
                sessions = sessions,
                theme = theme,
                selectedSessionId = (detail as? Screen.Terminal)?.sessionId,
                fontSize = fontSize,
                themeName = themeName,
                onThemeChange = { themeName = it; settings.setThemeName(it) },
                onFontSizeChange = { fontSize = it; settings.setFontSize(it) },
                notificationsEnabled = notificationsEnabled,
                onNotificationsChange = { notificationsEnabled = it; service.setNotificationsEnabled(it) },
                ensureForeground = ::ensureForeground,
              )
            },
            detail = {
              val current = detail
              if (current == null || current is Screen.SessionList) {
                EmptyDetailPane()
              } else {
                ScreenContent(
                  screen = current,
                  nav = sidebarNav,
                  service = service,
                  sessions = sessions,
                  theme = theme,
                  selectedSessionId = null,
                  fontSize = fontSize,
                  themeName = themeName,
                  onThemeChange = { themeName = it; settings.setThemeName(it) },
                  onFontSizeChange = { fontSize = it; settings.setFontSize(it) },
                  notificationsEnabled = notificationsEnabled,
                  onNotificationsChange = { notificationsEnabled = it; service.setNotificationsEnabled(it) },
                  ensureForeground = ::ensureForeground,
                )
              }
            },
          )
        } else {
          BackHandler(enabled = backStack.size > 1) { pop() }

          val stackNav = TetherNav(
            openSession = { backStack.add(Screen.Terminal(it)) },
            newSession = { backStack.add(Screen.NewSession) },
            openHosts = { backStack.add(Screen.Hosts) },
            openSettings = { backStack.add(Screen.Settings) },
            back = { pop() },
            onSessionCreated = { id ->
              backStack.removeAt(backStack.lastIndex)
              backStack.add(Screen.Terminal(id))
            },
          )

          ScreenContent(
            screen = backStack.last(),
            nav = stackNav,
            service = service,
            sessions = sessions,
            theme = theme,
            selectedSessionId = null,
            fontSize = fontSize,
            themeName = themeName,
            onThemeChange = { themeName = it; settings.setThemeName(it) },
            onFontSizeChange = { fontSize = it; settings.setFontSize(it) },
            notificationsEnabled = notificationsEnabled,
            onNotificationsChange = { notificationsEnabled = it; service.setNotificationsEnabled(it) },
            ensureForeground = ::ensureForeground,
          )
        }
      }
    }
  }
}

/**
 * Renders a single [Screen]. The one definition per destination is shared by
 * both layouts; navigation differs only through [nav] (push/pop vs detail-pane
 * selection). Per-session actions (rename/disconnect/reconnect/remove) talk to
 * the [com.tether.go.session.SessionManager] directly and are identical in
 * either layout.
 */
@Composable
private fun ScreenContent(
  screen: Screen,
  nav: TetherNav,
  service: SessionService,
  sessions: List<SessionUiModel>,
  theme: TetherTheme,
  selectedSessionId: String?,
  fontSize: Int,
  themeName: String,
  onThemeChange: (String) -> Unit,
  onFontSizeChange: (Int) -> Unit,
  notificationsEnabled: Boolean,
  onNotificationsChange: (Boolean) -> Unit,
  ensureForeground: () -> Unit,
) {
  val manager = service.sessionManager
  when (screen) {
    Screen.SessionList -> SessionListScreen(
      sessions = sessions,
      onOpenSession = nav.openSession,
      onNewSession = nav.newSession,
      onOpenHosts = nav.openHosts,
      onOpenSettings = nav.openSettings,
      onRename = { id, label -> manager.rename(id, label) },
      onDisconnect = { manager.disconnect(it) },
      onReconnect = { id ->
        ensureForeground()
        manager.reconnect(id, appearanceFor(theme), DEFAULT_TERMINAL_SIZE)
      },
      onRemove = { manager.removeSession(it) },
      canReconnect = { manager.canReconnectSilently(it) },
      selectedSessionId = selectedSessionId,
    )

    Screen.NewSession -> NewSessionScreen(
      hostStore = service.hostStore,
      privateKeyStore = service.privateKeyStore,
      onCancel = nav.back,
      onStart = { draft, auth ->
        ensureForeground()
        val id = manager.createSession(draft, auth, appearanceFor(theme), DEFAULT_TERMINAL_SIZE)
        nav.onSessionCreated(id)
      },
    )

    is Screen.Terminal -> TerminalScreen(
      sessionId = screen.sessionId,
      manager = manager,
      fontSize = fontSize,
      onBack = nav.back,
      onEnsureForeground = ensureForeground,
    )

    Screen.Hosts -> HostsScreen(
      hostStore = service.hostStore,
      privateKeyStore = service.privateKeyStore,
      onBack = nav.back,
    )

    Screen.Settings -> SettingsScreen(
      currentThemeName = themeName,
      onThemeChange = onThemeChange,
      fontSize = fontSize,
      onFontSizeChange = onFontSizeChange,
      notificationsEnabled = notificationsEnabled,
      onNotificationsChange = onNotificationsChange,
      appVersion = APP_VERSION,
      onBack = nav.back,
    )
  }
}

/** Unwrap the (possibly themed/wrapped) Compose context to its host [Activity]. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}
