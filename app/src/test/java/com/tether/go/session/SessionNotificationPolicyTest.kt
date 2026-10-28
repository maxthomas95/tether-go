package com.tether.go.session

import com.tether.go.cli.CliToolId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionNotificationPolicyTest {
  @Test
  fun connectedBackgroundSessionCanNotifyEvenWhenLastActive() {
    assertTrue(eligible(appForeground = false, activeSessionId = session.id))
  }

  @Test
  fun foregroundSessionOnlySuppressesItsOwnAlert() {
    assertFalse(eligible(appForeground = true, activeSessionId = session.id))
    assertTrue(eligible(appForeground = true, activeSessionId = "another-session"))
    assertTrue(eligible(appForeground = true, activeSessionId = null))
  }

  @Test
  fun globalSettingSuppressesEveryOtherwiseEligibleSession() {
    for (foreground in listOf(false, true)) {
      for (activeId in listOf(null, session.id, "another-session")) {
        assertFalse(eligible(
          notificationsEnabled = false,
          appForeground = foreground,
          activeSessionId = activeId,
        ))
      }
    }
  }

  @Test
  fun persistedMuteSuppressesInForegroundAndBackgroundRegardlessOfSelection() {
    for (foreground in listOf(false, true)) {
      for (activeId in listOf(null, session.id, "another-session")) {
        assertFalse(eligible(
          model = session.copy(notificationsMuted = true),
          appForeground = foreground,
          activeSessionId = activeId,
        ))
      }
    }
  }

  @Test
  fun mutingOneSessionDoesNotSuppressAnotherSession() {
    val muted = session.copy(notificationsMuted = true)
    val unmuted = session.copy(id = "another-session")

    assertFalse(eligible(model = muted, activeSessionId = null))
    assertTrue(eligible(model = unmuted, activeSessionId = null))
  }

  @Test
  fun disconnectedSessionsNeverNotifyRegardlessOfOtherSettings() {
    for (muted in listOf(false, true)) {
      for (enabled in listOf(false, true)) {
        for (foreground in listOf(false, true)) {
          for (activeId in listOf(null, session.id, "another-session")) {
            assertFalse(eligible(
              model = session.copy(notificationsMuted = muted),
              notificationsEnabled = enabled,
              appForeground = foreground,
              activeSessionId = activeId,
              connected = false,
            ))
          }
        }
      }
    }
  }

  private fun eligible(
    model: Session = session,
    notificationsEnabled: Boolean = true,
    appForeground: Boolean = true,
    activeSessionId: String? = null,
    connected: Boolean = true,
  ): Boolean = shouldNotifySession(
    session = model,
    notificationsEnabled = notificationsEnabled,
    appForeground = appForeground,
    activeSessionId = activeSessionId,
    connected = connected,
  )

  private val session = Session(
    id = "session",
    label = "Session",
    hostId = null,
    host = "example.test",
    port = 22,
    username = "user",
    cliTool = CliToolId.CLAUDE,
    customBinary = null,
    workingDir = null,
    flags = emptyList(),
    env = emptyMap(),
    privateKeyId = null,
    createdAtMillis = 100,
    updatedAtMillis = 200,
  )
}
