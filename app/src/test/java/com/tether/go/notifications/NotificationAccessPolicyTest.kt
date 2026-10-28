package com.tether.go.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationAccessPolicyTest {
  @Test
  fun firstAndroid13RequestIsAnExplicitAffordanceNotConsent() {
    for (sdk in listOf(33, 36)) {
      val result = policy(allowed.copy(sdkInt = sdk, permissionGranted = false, systemNotificationsEnabled = false))

      assertEquals(NotificationAccessAction.REQUEST_PERMISSION, result.action)
      assertEquals(NotificationAccessStatus.PERMISSION_REQUIRED, result.status)
      assertFalse(result.sessionAlertsAllowed)
    }
  }

  @Test
  fun denialOrDismissalRoutesToSettingsWithoutClaimingSuccess() {
    val result = policy(allowed.copy(
      permissionGranted = false,
      systemNotificationsEnabled = false,
      permissionRequestAttempted = true,
    ))

    assertEquals(NotificationAccessAction.OPEN_APP_SETTINGS, result.action)
    assertEquals(NotificationAccessStatus.PERMISSION_REQUIRED, result.status)
    assertFalse(result.sessionAlertsAllowed)
  }

  @Test
  fun revocationAfterAnEarlierGrantDoesNotOfferAnotherPrompt() {
    val priorGrant = allowed.copy(permissionRequestAttempted = true)
    assertTrue(policy(priorGrant).sessionAlertsAllowed)

    val revoked = policy(priorGrant.copy(permissionGranted = false, systemNotificationsEnabled = false))
    assertEquals(NotificationAccessAction.OPEN_APP_SETTINGS, revoked.action)
    assertFalse(revoked.sessionAlertsAllowed)
  }

  @Test
  fun android12AndEarlierIgnoreUnsupportedRuntimePermission() {
    for (sdk in listOf(26, 28, 32)) {
      for (attempted in listOf(false, true)) {
        val state = allowed.copy(sdkInt = sdk, permissionGranted = false, permissionRequestAttempted = attempted)
        assertTrue(policy(state).sessionAlertsAllowed)
        assertEquals(NotificationAccessAction.OPEN_APP_SETTINGS, policy(state).action)

        val blocked = policy(state.copy(systemNotificationsEnabled = false))
        assertEquals(NotificationAccessStatus.APP_BLOCKED, blocked.status)
        assertEquals(NotificationAccessAction.OPEN_APP_SETTINGS, blocked.action)
        assertFalse(blocked.sessionAlertsAllowed)
      }
    }
  }

  @Test
  fun appWideSystemBlockTakesPriorityOverChannelBlock() {
    val result = policy(allowed.copy(systemNotificationsEnabled = false, sessionChannelEnabled = false))

    assertEquals(NotificationAccessStatus.APP_BLOCKED, result.status)
    assertEquals(NotificationAccessAction.OPEN_APP_SETTINGS, result.action)
    assertFalse(result.sessionAlertsAllowed)
  }

  @Test
  fun blockedSessionChannelOpensItsSettingsEvenWhenPermissionIsGranted() {
    for (sdk in listOf(26, 32, 33, 36)) {
      val result = policy(allowed.copy(sdkInt = sdk, sessionChannelEnabled = false))

      assertEquals(NotificationAccessStatus.CHANNEL_BLOCKED, result.status)
      assertEquals(NotificationAccessAction.OPEN_CHANNEL_SETTINGS, result.action)
      assertFalse(result.sessionAlertsAllowed)
    }
  }

  @Test
  fun absentChannelIsNotReportedAsAllowedOrOfferedAsASettingsDestination() {
    val result = policy(allowed.copy(sessionChannelEnabled = null))

    assertEquals(NotificationAccessStatus.CHANNEL_UNAVAILABLE, result.status)
    assertEquals(NotificationAccessAction.OPEN_APP_SETTINGS, result.action)
    assertFalse(result.sessionAlertsAllowed)
  }

  @Test
  fun globalAppPreferenceStaysIndependentOfEveryAndroidState() {
    for (sdk in listOf(26, 32, 33, 36)) {
      for (permissionGranted in listOf(false, true)) {
        for (systemEnabled in listOf(false, true)) {
          for (channelEnabled in listOf(null, false, true)) {
            for (attempted in listOf(false, true)) {
              val state = NotificationAccessState(sdk, permissionGranted, systemEnabled, channelEnabled, attempted)
              val preferenceOn = policy(state, appPreferenceEnabled = true)
              val preferenceOff = policy(state, appPreferenceEnabled = false)

              assertEquals(preferenceOn.status, preferenceOff.status)
              assertEquals(preferenceOn.action, preferenceOff.action)
              assertFalse(preferenceOff.sessionAlertsAllowed)
              assertEquals(
                (sdk < 33 || permissionGranted) && systemEnabled && channelEnabled == true,
                preferenceOn.sessionAlertsAllowed,
              )
              if (sdk < 33 || attempted || permissionGranted) {
                assertFalse(preferenceOn.action == NotificationAccessAction.REQUEST_PERMISSION)
              }
            }
          }
        }
      }
    }
  }

  @Test
  fun settingsReturnUsesNewSystemStateButDoesNotEnableAnOffAppPreference() {
    val blocked = allowed.copy(systemNotificationsEnabled = false, permissionRequestAttempted = true)
    assertFalse(policy(blocked).sessionAlertsAllowed)
    assertTrue(policy(blocked.copy(systemNotificationsEnabled = true)).sessionAlertsAllowed)
    assertFalse(policy(blocked.copy(systemNotificationsEnabled = true), appPreferenceEnabled = false).sessionAlertsAllowed)
  }

  @Test
  fun grantedPermissionDoesNotBypassDisabledSessionChannel() {
    val beforeGrant = allowed.copy(permissionGranted = false, sessionChannelEnabled = false)
    assertEquals(NotificationAccessAction.REQUEST_PERMISSION, policy(beforeGrant).action)

    val afterGrant = policy(beforeGrant.copy(permissionGranted = true, permissionRequestAttempted = true))
    assertEquals(NotificationAccessAction.OPEN_CHANNEL_SETTINGS, afterGrant.action)
    assertFalse(afterGrant.sessionAlertsAllowed)
  }

  private fun policy(access: NotificationAccessState, appPreferenceEnabled: Boolean = true) =
    notificationAccessPolicy(appPreferenceEnabled, access)

  private val allowed = NotificationAccessState(
    sdkInt = 33,
    permissionGranted = true,
    systemNotificationsEnabled = true,
    sessionChannelEnabled = true,
    permissionRequestAttempted = false,
  )
}
