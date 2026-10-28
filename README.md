# Tether Go

Android client for managing SSH-backed agent CLI sessions from a phone.

Tether Go is the "on the go" surface in the Tether product family:

- **Tether** - desktop session multiplexer for local, SSH, and Coder environments.
- **Tether Go** - Android remote-session client focused on direct SSH.
- **VoidCode VR** - spatial session manager for Valve Index / SteamVR.

## Status

Tether Go now carries the Tether product identity. The session list is the home
screen — phone-owned sessions grouped by host, with CLI-tool chips and status
dots. A New Session flow gathers an SSH host, auth, CLI tool (Claude, Codex,
Copilot, OpenCode, or Custom), working directory, launch flags, environment
variables, and a label, then connects directly, confirms and pins the first
presented host key, requests an `xterm-256color` PTY, and types the launch
command into a full-screen terminal with a mobile quick bar. All 11 built-in desktop themes, the Tether logo, and live theme switching
are in place, including Nord, Gruvbox Dark, Gruvbox Light, and Harbor. Saved
theme preferences and the Mocha default are unchanged. Under the hood it builds a ConnectBot `termlib` terminal per session and
keeps raw SSH bytes flowing both ways unchanged.

Passwords remain temporary in-memory connection input and are not persisted.
Imported private keys and optional passphrases are stored separately from host
records and are not written to plaintext preferences or logs. The Android
Keystore key used for encrypted storage requires recent device credential or
strong biometric authentication before use. Phone-owned notifications and
push-to-talk voice input are implemented and still need release-candidate device
validation.

Settings distinguishes the in-app session-alert preference from Android
notification permission and the Session activity channel. Enable Android
notifications explicitly from Settings; after denial or dismissal, use the
notification settings action. Returning from Android settings refreshes the
reported access. No permission prompt opens automatically. The session-alert
switch does not disable the foreground running-session service notification.

Security limitation: session launch environment values and extra arguments are
currently saved in app-private preferences with encoding, not encryption. Do
not put API keys, tokens, or other secrets in those fields. This is separate
from the encrypted SSH private-key store; backup and device-transfer exclusions
do not make the launch fields encrypted. Secure launch-value storage and
migration remain follow-up work before richer saved profiles.

The initial product direction is intentionally small:

- Direct SSH to a reachable VM or remote host.
- Open a real PTY and run Claude, Codex, Copilot, OpenCode, or a custom CLI.
- Preserve the dumb-pipe terminal model: raw PTY bytes into a terminal emulator, input back to stdin unchanged.
- Phone-owned sessions only for v0.1.
- CLI-native resume by an explicit ID, or manual CLI flags, when continuing an older conversation.
- No Coder, Vault, desktop API, agent, public relay, or local Android PTY in v0.1.

## Core Principle

**Dumb pipe, smart shell.** Do not parse, intercept, filter, or re-render CLI output. The terminal stream should remain a real PTY stream. Status detection, notifications, and usage tracking must be passive side channels.

## v0.1 Scope

- SSH host management with host key verification.
- Private-key import and Android secure storage.
- Full-screen terminal view with phone-shaped input controls.
- CLI tool selection for Claude, Codex, Copilot, OpenCode, and custom commands.
- Working directory, env vars, launch flags, and session labels.
- Phone-owned notifications for sessions currently observed by the app.
- Push-to-talk voice input that types final transcripts into the PTY.
- Tether visual identity and theme continuity where practical.

## Guided Launch Settings

**New session** offers Model for Claude; Model and Agent for OpenCode; and
Model, Native profile, and Reasoning effort for Codex. Copilot and Custom keep
manual arguments only. Enter identifiers supported by the CLI installed on your
remote host: there is no model catalog, account access, or availability lookup.
A Codex native profile references existing remote CLI configuration (for example
`work`), not an app-saved Tether Go launch profile. Reasoning is free-form, with
examples such as `low`, `medium`, `high`, and `xhigh`; the remote CLI decides what
it supports.

Empty fields leave manual selectors and native defaults untouched. Surrounding
whitespace is trimmed. Populated fields accept at most 128 ASCII letters/digits
or `. _ : @ / + ~ -` (also `[` and `]` for Claude/OpenCode), with no leading `-`,
embedded whitespace, quotes, or shell operators. A manual selector overlapping
a populated field blocks Start: remove that manual selector or clear the guided
field. No manual token is silently removed or replaced. Incomplete known
options, ambiguous short groups, and `--` also block guided launch.

The controls are temporary New Session input. Only the resolved, shell-quoted
flags are saved in existing session metadata, so restart/reconnect uses the same
command without a new schema field. Clearing controls restores your original
Extra arguments. Switching CLI clears all guided settings and resume mode/ID,
but retains Extra arguments for revalidation. For advanced shell syntax, choose
**Default / manual** and clear every guided launch field. These controls do not
change running sessions or encrypt launch metadata; never enter secrets.

## Resuming by Native ID

In **New session**, keep **Default / manual** with empty guided settings for the existing launch behavior,
including any resume flags you enter yourself. It does not promise a new
conversation. Choose **Resume by ID** for Claude, Codex, Copilot, or OpenCode and
enter the full native session ID from that CLI on the selected remote host.
Custom commands support Default / manual only. Switching CLI clears the guided
mode and ID; retained Extra arguments are validated when guided mode is enabled.

This is a CLI-native command helper, not remote history discovery, a transcript
picker, or desktop synchronization. Tether Go does not verify ID existence or
uniqueness (Copilot itself may also accept names/prefixes). The native CLI decides
whether the supplied ID can resume; Tether Go never retries a missing ID as a
fresh or latest conversation. Reconnect uses the same saved explicit ID.

Guided resume preserves desktop argument order: Claude appends `--resume ID` to
manual arguments; Codex prepends `resume ID`; Copilot prepends `--resume ID`;
OpenCode prepends `--session ID`. It never substitutes Claude or Copilot
`--session-id`, which can create a session rather than resume it.

IDs must be 1–256 characters, without whitespace/control characters or a leading
`-`. Guided arguments accept literal POSIX quotes and escapes, but reject shell
expansions, operators, and unquoted globs. Conflicting resume/continue/fork or
worktree selectors and ambiguous option groups show an inline error and disable
Start; remove the conflict or switch to Default / manual and clear guided launch
settings. Nothing is silently
removed. Invalid saved launch configurations show an error before reconnecting.
Rows marked **Resume by ID configured** describe configuration, not live history.

Environment assignments use `env` so quoted values reach the remote CLI as
values, not shell commands. The existing warning about unencrypted launch fields
still applies; do not put secrets in environment, argument, or session-ID fields.

## Finding Sessions and Controlling Alerts

Use **Search sessions** to find a label, SSH endpoint (`username@host:port`),
working directory, or CLI tool, including a custom binary. Search uses Tether's
case-insensitive fuzzy matching: for example, `apsv` can find `api-server`.
Label matches rank ahead of equivalent directory, endpoint, and CLI matches.
Search results are shown in ranked order; clearing the search restores host
groups. Terminal output, environment values, flags, and credentials are never
searched.

**All** includes saved and live sessions. **Waiting** narrows the list to
sessions whose passive status detector reports waiting, and combines with the
search. Filtering never disconnects a session or changes the terminal selected
in the tablet/foldable detail pane. Search and filter choices survive navigation
and fold/unfold changes while the app UI remains open.

Each session's **⋮** menu has **Mute notifications** / **Unmute notifications**.
Muted rows show **Notifications muted**. This preference survives app restarts;
previously saved sessions start unmuted. Muting dismisses an existing alert for
that session and suppresses future waiting/bell alerts without disconnecting it
or hiding its status. Unmuting permits future alerts; it does not replay past
ones. The global notification setting and active-terminal suppression still
apply. These controls only affect phone-owned alerts, not desktop sessions or
Android's notification permission.

## Documentation

| File | Contents |
|---|---|
| [ROADMAP.md](ROADMAP.md) | v0.1 scope, later phases, and deferred ideas |
| [CHANGELOG.md](CHANGELOG.md) | Release history once development starts |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Initial architecture direction and open decisions |
| [docs/SSH_PTY_SPIKE_TESTING.md](docs/SSH_PTY_SPIKE_TESTING.md) | Manual validation notes for the SSH PTY spike |
| [docs/RELEASE_CHECKLIST.md](docs/RELEASE_CHECKLIST.md) | Release mechanics to fill in before first APK |
| [docs/SONAR.md](docs/SONAR.md) | Android coverage, CI gates, and SonarQube Cloud setup |
| [AGENTS.md](AGENTS.md) | Repository rules for coding agents and contributors |

## Development

The initial scaffold uses Kotlin + Jetpack Compose with:

- Android Gradle Plugin 9.2.0 and Gradle 9.4.1.
- AGP 9 built-in Kotlin support plus the Compose compiler plugin.
- Jetpack Compose BOM 2026.05.00.
- Compile/target SDK 36, min SDK 26.
- Application ID and namespace `com.tether.go`.

Use the Gradle wrapper for local development:

```bash
./gradlew test :app:createDebugUnitTestCoverageReport
./gradlew lint
./gradlew assembleDebug
```

The GitHub Actions workflow runs these checks on pull requests, pushes to
`main`, and manual dispatch. A separate Gradle SonarQube Cloud job imports the
coverage report and waits for the quality gate. The stable **Gradle checks**
context requires both build and applicable Sonar success; fork/Dependabot PRs
intentionally skip only the authenticated scan. See [docs/SONAR.md](docs/SONAR.md)
for reports, coverage scope, and token/bootstrap troubleshooting.
Release signing is not configured yet, so release APK commands are still out of
scope.

## License

MIT - see [LICENSE](LICENSE).
