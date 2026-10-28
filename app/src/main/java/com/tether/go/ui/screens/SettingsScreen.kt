package com.tether.go.ui.screens

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tether.go.notifications.NotificationAccessAction
import com.tether.go.notifications.NotificationAccessState
import com.tether.go.notifications.NotificationAccessStatus
import com.tether.go.notifications.NotificationService
import com.tether.go.notifications.notificationAccessPolicy
import com.tether.go.settings.SettingsStore
import com.tether.go.settings.LaunchDefaultsStore
import com.tether.go.ui.components.LaunchDefaultsEditor
import com.tether.go.ui.branding.TetherLogo
import com.tether.go.ui.components.SectionHeader
import com.tether.go.ui.components.TetherTopBar
import com.tether.go.ui.theme.LocalTetherTheme
import com.tether.go.ui.theme.TetherThemes

/**
 * Settings: theme picker (full desktop palette set, Mocha default), terminal
 * font size, notification preferences/access, explicit CLI defaults, and an About card.
 * Theme + font changes are applied live.
 */
@Composable
fun SettingsScreen(
  launchDefaultsStore: LaunchDefaultsStore,
  currentThemeName: String,
  onThemeChange: (String) -> Unit,
  fontSize: Int,
  onFontSizeChange: (Int) -> Unit,
  notificationsEnabled: Boolean,
  onNotificationsChange: (Boolean) -> Unit,
  appVersion: String,
  onBack: () -> Unit,
) {
  val theme = LocalTetherTheme.current

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(theme.bgPrimary),
  ) {
    TetherTopBar(title = "Settings", onBack = onBack)

    Column(
      modifier = Modifier
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      SectionHeader("Theme")
      TetherThemes.all.forEach { candidate ->
        val selected = candidate.name == currentThemeName
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) theme.accent.copy(alpha = 0.15f) else theme.bgSidebar, RoundedCornerShape(10.dp))
            .border(1.dp, if (selected) theme.accent else theme.border, RoundedCornerShape(10.dp))
            .clickable { onThemeChange(candidate.name) }
            .padding(horizontal = 12.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Swatch(candidate.bgPrimary)
          Swatch(candidate.accent)
          Swatch(candidate.statusRunning)
          Text(
            candidate.label,
            color = theme.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
          )
          if (selected) {
            Text("✓", color = theme.accent, fontWeight = FontWeight.Bold)
          }
        }
      }

      SectionHeader("Terminal")
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(theme.bgSidebar, RoundedCornerShape(10.dp))
          .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Font size", color = theme.textPrimary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        FilledTonalButton(
          onClick = { onFontSizeChange((fontSize - 1).coerceAtLeast(SettingsStore.MIN_FONT_SIZE)) },
          shape = RoundedCornerShape(8.dp),
        ) { Text("−") }
        Text(
          "$fontSize",
          color = theme.textPrimary,
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.width(40.dp).padding(horizontal = 8.dp),
        )
        FilledTonalButton(
          onClick = { onFontSizeChange((fontSize + 1).coerceAtMost(SettingsStore.MAX_FONT_SIZE)) },
          shape = RoundedCornerShape(8.dp),
        ) { Text("+") }
      }

      SectionHeader("Notifications")
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(theme.bgSidebar, RoundedCornerShape(10.dp))
          .padding(start = 12.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text("Session notifications", color = theme.textPrimary, style = MaterialTheme.typography.bodyMedium)
          Text(
            "Allow waiting-for-input and terminal bell alerts",
            color = theme.textMuted,
            style = MaterialTheme.typography.labelSmall,
          )
        }
        Switch(
          checked = notificationsEnabled,
          onCheckedChange = onNotificationsChange,
          colors = SwitchDefaults.colors(
            checkedThumbColor = theme.btnPrimaryText,
            checkedTrackColor = theme.accent,
            uncheckedTrackColor = theme.bgActive,
            uncheckedBorderColor = theme.border,
          ),
        )
      }
      NotificationAccessCard(notificationsEnabled)

      LaunchDefaultsEditor(launchDefaultsStore)

      SectionHeader("About")
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(theme.bgSidebar, RoundedCornerShape(10.dp))
          .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        TetherLogo(size = 56.dp)
        Spacer(Modifier.height(10.dp))
        Text("Tether Go $appVersion", color = theme.textPrimary, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text("Dumb pipe, smart shell.", color = theme.textMuted, style = MaterialTheme.typography.bodySmall)
      }
      Spacer(Modifier.height(16.dp))
    }
  }
}

private const val NOTIFICATION_ACCESS_PREFS = "tether_go_notification_access"
private const val KEY_PERMISSION_REQUEST_ATTEMPTED = "permission_request_attempted"

@Composable
private fun NotificationAccessCard(appPreferenceEnabled: Boolean) {
  val theme = LocalTetherTheme.current
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val prefs = remember(context) {
    context.applicationContext.getSharedPreferences(NOTIFICATION_ACCESS_PREFS, Context.MODE_PRIVATE)
  }
  fun readAccess() = readNotificationAccess(context, prefs.getBoolean(KEY_PERMISSION_REQUEST_ATTEMPTED, false))
  var access by remember(context) { mutableStateOf(readAccess()) }
  var actionInFlight by rememberSaveable { mutableStateOf(false) }
  var actionError by rememberSaveable { mutableStateOf<String?>(null) }

  val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
    // Denial or dismissal never changes the app preference or assumes permission was granted.
    actionInFlight = false
    access = readAccess()
  }
  val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
    actionInFlight = false
    access = readAccess()
  }
  DisposableEffect(lifecycleOwner, context) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) access = readAccess()
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val policy = notificationAccessPolicy(appPreferenceEnabled, access)
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(theme.bgSidebar, RoundedCornerShape(10.dp))
      .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      when {
        !appPreferenceEnabled -> "Session alerts are off in Tether Go. Android settings do not change the switch above."
        policy.sessionAlertsAllowed -> "Session alerts are on. Android allows notifications."
        else -> "Session alerts are on in Tether Go, but Android is not allowing them."
      },
      color = theme.textPrimary,
      style = MaterialTheme.typography.bodyMedium,
    )
    Text(
      when (policy.status) {
        NotificationAccessStatus.PERMISSION_REQUIRED -> if (access.permissionRequestAttempted) {
          "Android notification permission is not granted. You can enable it in notification settings."
        } else {
          "Allow Android notifications to receive alerts from phone-owned sessions."
        }
        NotificationAccessStatus.APP_BLOCKED -> "Notifications are disabled for Tether Go in Android settings."
        NotificationAccessStatus.CHANNEL_BLOCKED -> "The Session activity notification channel is disabled in Android settings."
        NotificationAccessStatus.CHANNEL_UNAVAILABLE -> "The Session activity notification channel is unavailable in Android settings."
        NotificationAccessStatus.ALLOWED -> "Per-session mutes and active-terminal suppression still apply. Android controls sound and delivery."
      },
      color = theme.textMuted,
      style = MaterialTheme.typography.bodySmall,
    )
    FilledTonalButton(
      enabled = !actionInFlight,
      onClick = {
        if (!actionInFlight) {
          // Recheck before acting in case Android settings changed while the screen stayed visible.
          access = readAccess()
          val action = notificationAccessPolicy(appPreferenceEnabled, access).action
          actionError = null
          actionInFlight = true
          if (action == NotificationAccessAction.REQUEST_PERMISSION && Build.VERSION.SDK_INT >= 33) {
            // Persist only request history, never consent. A denial/dismissal routes future taps to settings.
            prefs.edit().putBoolean(KEY_PERMISSION_REQUEST_ATTEMPTED, true).apply()
            runCatching { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
              .onFailure {
                actionInFlight = false
                actionError = "Could not open the permission prompt. Use Android notification settings instead."
              }
            access = readAccess()
          } else {
            val intent = Intent(
              if (action == NotificationAccessAction.OPEN_CHANNEL_SETTINGS) {
                Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS
              } else {
                Settings.ACTION_APP_NOTIFICATION_SETTINGS
              },
            ).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            if (action == NotificationAccessAction.OPEN_CHANNEL_SETTINGS) {
              intent.putExtra(Settings.EXTRA_CHANNEL_ID, NotificationService.CHANNEL_STATUS)
            }
            runCatching { settingsLauncher.launch(intent) }
              .onFailure {
                actionInFlight = false
                actionError = "Open Android Settings > Apps > Tether Go > Notifications to change notification access."
              }
          }
        }
      },
      shape = RoundedCornerShape(8.dp),
    ) {
      Text(
        if (policy.action == NotificationAccessAction.REQUEST_PERMISSION) "Enable Android notifications"
        else "Open notification settings",
      )
    }
    actionError?.let { Text(it, color = theme.textMuted, style = MaterialTheme.typography.bodySmall) }
    Text(
      "The app switch only controls session alerts, not the ongoing running-session service notification.",
      color = theme.textMuted,
      style = MaterialTheme.typography.labelSmall,
    )
  }
}

private fun readNotificationAccess(context: Context, permissionRequestAttempted: Boolean): NotificationAccessState {
  val channel = context.getSystemService(NotificationManager::class.java)
    .getNotificationChannel(NotificationService.CHANNEL_STATUS)
  return NotificationAccessState(
    sdkInt = Build.VERSION.SDK_INT,
    permissionGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED,
    systemNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
    sessionChannelEnabled = channel?.let { it.importance != NotificationManager.IMPORTANCE_NONE },
    permissionRequestAttempted = permissionRequestAttempted,
  )
}

@Composable
private fun Swatch(color: androidx.compose.ui.graphics.Color) {
  Box(
    modifier = Modifier
      .size(16.dp)
      .clip(CircleShape)
      .background(color, CircleShape),
  )
}
