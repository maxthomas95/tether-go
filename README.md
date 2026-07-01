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
command into a full-screen terminal with a mobile quick bar. The Catppuccin
Mocha / Tether theme set, the Tether logo, and live theme switching are in
place. Under the hood it builds a ConnectBot `termlib` terminal per session and
keeps raw SSH bytes flowing both ways unchanged.

Passwords remain temporary in-memory connection input and are not persisted.
Imported private keys and optional passphrases are stored separately from host
records and are not written to plaintext preferences or logs. The Android
Keystore key used for encrypted storage requires recent device credential or
strong biometric authentication before use. Phone-owned notifications and
push-to-talk voice input are implemented and still need release-candidate device
validation.

The initial product direction is intentionally small:

- Direct SSH to a reachable VM or remote host.
- Open a real PTY and run Claude, Codex, OpenCode, or a custom CLI.
- Preserve the dumb-pipe terminal model: raw PTY bytes into a terminal emulator, input back to stdin unchanged.
- Phone-owned sessions only for v0.1.
- Manual CLI-native resume when the user wants to continue an older conversation.
- No Coder, Vault, desktop API, agent, public relay, or local Android PTY in v0.1.

## Core Principle

**Dumb pipe, smart shell.** Do not parse, intercept, filter, or re-render CLI output. The terminal stream should remain a real PTY stream. Status detection, notifications, and usage tracking must be passive side channels.

## v0.1 Scope

- SSH host management with host key verification.
- Private-key import and Android secure storage.
- Full-screen terminal view with phone-shaped input controls.
- CLI tool selection for Claude, Codex, OpenCode, and custom commands.
- Working directory, env vars, launch flags, and session labels.
- Phone-owned notifications for sessions currently observed by the app.
- Push-to-talk voice input that types final transcripts into the PTY.
- Tether visual identity and theme continuity where practical.

## Documentation

| File | Contents |
|---|---|
| [ROADMAP.md](ROADMAP.md) | v0.1 scope, later phases, and deferred ideas |
| [CHANGELOG.md](CHANGELOG.md) | Release history once development starts |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Initial architecture direction and open decisions |
| [docs/SSH_PTY_SPIKE_TESTING.md](docs/SSH_PTY_SPIKE_TESTING.md) | Manual validation notes for the SSH PTY spike |
| [docs/RELEASE_CHECKLIST.md](docs/RELEASE_CHECKLIST.md) | Release mechanics to fill in before first APK |
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
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

The GitHub Actions workflow runs the same checks on pull requests and pushes to
`main`, then runs CI-based SonarQube Cloud analysis with the Gradle scanner.
Release signing is not configured yet, so release APK commands are still out of
scope.

## License

MIT - see [LICENSE](LICENSE).
