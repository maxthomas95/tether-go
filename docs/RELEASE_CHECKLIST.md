# Release Checklist - Tether Go

This checklist tracks the path from the initial Android scaffold to the first
debug and public releases.

## Before First Debug APK

- [x] Android stack selected and documented.
- [x] Gradle project committed.
- [x] Debug build command documented.
- [ ] SonarQube Cloud project imported, `SONAR_TOKEN` configured, automatic
  analysis disabled, and first CI-based analysis passing.
- [ ] License notices for Android dependencies started.
- [x] Basic smoke test documented.

## Before First Public Release

- [ ] Release signing approach documented.
- [ ] Signing keys excluded from the repo.
- [ ] Dependency licenses reviewed.
- [ ] Host key verification tested.
- [ ] Private key storage reviewed.
- [ ] Logs checked for secret redaction.
- [ ] Release APK built from a clean checkout.
- [ ] Required branch checks reflect the active CI/SonarCloud jobs.
- [ ] GitHub Release notes written.

## Release Commands

Current debug build checks:

```bash
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

Release signing is not configured yet. Do not use or document release APK
commands until the signing approach exists.

## Debug Smoke Test

Build the debug APK with `./gradlew assembleDebug`, install it on an emulator or
device, launch Tether Go, and confirm the terminal screen renders. SSH terminal
rendering, host-key verification, and private-key import/auth have separate
manual checks in `docs/SSH_PTY_SPIKE_TESTING.md`.

### Session Discovery and Notification Controls

Validate on a phone and an expanded tablet/foldable layout:

- [ ] With no saved sessions, show **No sessions yet**. With saved sessions but
  no search/filter matches, show **No matching sessions** and a clear action.
- [ ] Find sessions by label, abbreviated fuzzy query, username, hostname, port,
  directory, CLI display name, and custom binary; matching results rank across
  hosts. Clearing search restores host grouping.
- [ ] All / Waiting combines with search; Idle and Running are not Waiting.
- [ ] Filtering the selected session out leaves its detail terminal open and
  connected. Clear the filter to restore its selected-row highlight. Navigate
  away/back and fold/unfold without losing the query or selected terminal.
- [ ] Connecting, Running, Waiting, and Idle count as active and offer
  Disconnect. Saved Disconnected/Error sessions offer Reconnect when supported.
- [ ] Mute one session through its menu; its row displays **Notifications muted**
  and an existing session alert disappears. Another session can still alert.
- [ ] Navigate away/back and restart the app: the mute preference remains.
  Upgrade from old saved sessions: all records remain and start unmuted.
- [ ] Unmute permits the next observed waiting/bell alert without replaying an
  old one. Global notification disable and active-terminal suppression still
  apply, and menu toggles do not interrupt raw terminal input/output.

### Built-in Theme Parity

- [ ] All 11 built-in themes are reachable in the Settings picker, including
  Nord, Gruvbox Dark, Gruvbox Light, and Harbor.
- [ ] Switching themes updates phone/expanded chrome and terminal foreground,
  background, and ANSI colors without reconnecting sessions.
- [ ] Saved theme choices survive restart; missing/unknown choices use Mocha.
- [ ] Check muted labels and light/dark terminal readability on a device.
  Native cursor/selection rendering is not claimed to match desktop exactly.

### Android Notification Access

- [ ] On Android 13+, a fresh install shows accurate permission status and
  requests permission only after tapping Enable Android notifications.
- [ ] Grant, deny, and dismiss the prompt: no fake success or automatic retry.
  After denial/dismissal, the action opens Android notification settings.
- [ ] Disable app notifications and the Session activity channel separately;
  the settings affordance targets the relevant page and status refreshes on return.
- [ ] On Android 8-12, never request POST_NOTIFICATIONS; use system settings.
- [ ] Repeated taps, cancellation, navigation, and configuration changes do not
  leave a stuck action or change the in-app session-alert switch.
- [ ] The foreground service continues its required ongoing notification
  independently of the in-app session-alert preference.
