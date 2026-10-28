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
- [ ] Required branch checks reflect the active CI/SonarCloud jobs; preserve
  the **Gradle checks** build + applicable Sonar aggregate.
- [ ] On the exact release PR head, inspect the coverage artifact and confirm
  both **SonarQube scan** and the Sonar quality gate pass; scan submission alone
  is not approval. Resolve the existing bootstrap 403 through project/token
  access, not by weakening the gate (see [SONAR.md](SONAR.md)).
- [ ] Confirm `versionName` is the intended release version; Sonar reads the same
  value. Recheck the main-branch scan and overall coverage after merge.
- [ ] GitHub Release notes written.

## Release Commands

Current debug build checks:

```bash
./gradlew test :app:createDebugUnitTestCoverageReport
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

### Explicit Native Resume and Launch Validation

These device/real-host checks remain outstanding; JVM tests use fake local CLIs,
not real agent accounts. Validate on phone and expanded/foldable layouts:

- [ ] Default / manual with empty guided settings keeps existing raw flags (including manual resume) and
  is never labeled New. Custom cannot enable guided resume.
- [ ] Resume a known full native ID on the selected host with Claude, Codex,
  Copilot, and OpenCode; verify native CLI results and raw terminal behavior.
  Do not infer Copilot remote history discovery from its explicit command helper.
- [ ] Blank, whitespace/control, overlong, or option-like IDs show an inline
  error and disable Start. Correcting input restores the preview and Start.
- [ ] Conflicting split/equals/attached/grouped/quoted selectors, incomplete
  known options, and shell expansions/operators/globs block guided launch;
  safe quoted values work. Default / manual with all guided settings cleared
  remains the escape hatch.
- [ ] Switching CLI clears guided mode and ID, does not reuse another CLI's ID,
  and revalidates retained Extra arguments when guided mode is selected again.
- [ ] Cancel/back/reopen, repeated taps, and fold/unfold never start a hidden
  launch or silently change resume intent. No latest/last choice is offered.
- [ ] Unknown/missing IDs are handled by the native CLI without any app retry
  as fresh/latest or a Claude/Copilot `--session-id` substitution.
- [ ] Restart, rename, mute, and reconnect preserve the configured explicit ID;
  upgrade fourteen-/fifteen-field records without losing flags/env/timestamps.
- [ ] Invalid saved guided configuration fails before SSH, with a readable
  reconnect error rather than a crash or fresh launch.
- [ ] Valid env values with spaces, quotes, dollars, and semicolons arrive
  unchanged; invalid names show an error instead of crashing the preview.
  Do not use secrets: launch fields are not encrypted.

### Guided Model, Agent, and Codex Settings

These device/real-host checks remain outstanding; fake local CLI argv tests do
not verify remote model, agent, profile, or reasoning availability:

- [ ] Claude shows Model; OpenCode shows Model and Agent; Codex shows Model,
  Native profile, and Reasoning effort. Copilot/Custom show none of these.
- [ ] Empty controls preserve manual/native defaults. Valid identifiers produce
  the expected preview; overlapping manual selectors, invalid identifiers,
  incomplete options, ambiguous short groups, and `--` show an error and block
  Start without rewriting Extra arguments or incorrectly marking the resume ID.
- [ ] Claude/OpenCode bracket model aliases arrive literally. Codex reasoning
  reaches the CLI as one `model_reasoning_effort="high"` config argument; test
  another supported effort such as `xhigh` on the actual remote installation.
- [ ] Choose an existing Codex native profile on the selected host, including a
  safe name such as `resume`/`fork` if configured. Combine settings with explicit
  resume IDs and confirm the remote CLI accepts the invocation.
- [ ] Clear fields, change CLI repeatedly, cancel/back/reopen, and fold/unfold.
  No stale guided values cross tools; retained Extra arguments are revalidated;
  repeated Start taps never duplicate the launch.
- [ ] Restart/reconnect reproduces resolved flags both with and without an
  explicit resume ID. Existing fourteen-/fifteen-/sixteen-field records retain
  flags, environment, timestamps, mute, and resume behavior. No new schema
  column or app-saved launch profile is introduced.
