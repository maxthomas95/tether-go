# Architecture - Tether Go

## Current Status

This repository contains the initial Android scaffold plus focused terminal and
SSH PTY spikes. The app module renders a ConnectBot `termlib` terminal and can
connect directly to a user-provided SSH host with password auth, request an
`xterm-256color` PTY, open a shell, stream raw SSH channel bytes into the
terminal, and route keyboard and quick-bar input back to the SSH channel
unchanged.

No polished host manager, persisted session management, secure key storage,
host-key TOFU UI, or product session UI has landed yet.

## Scaffold Stack

- Kotlin + Jetpack Compose.
- Android Gradle Plugin 9.2.0 with Gradle 9.4.1.
- AGP 9 built-in Kotlin support plus the Compose compiler plugin.
- Compose BOM 2026.05.00.
- Compile/target SDK 36, min SDK 26.
- Application ID and namespace `com.tether.go`.
- GitHub Actions runs `./gradlew test`, `./gradlew lint`, and
  `./gradlew assembleDebug` on pull requests and pushes to `main`.
- SonarQube Cloud is configured for CI-based Gradle scanner analysis under
  project key `maxthomas95_tether-go`.
- ConnectBot `termlib` is used for the native terminal renderer.
- ConnectBot `cbssh`, published as `org.connectbot.sshlib:sshlib`, is used for
  the SSH PTY spike.

## Core Invariant

```
Keyboard / quick-bar / voice input
    -> terminal input path
    -> SSH channel stdin
    -> remote PTY
    -> CLI
    -> remote PTY stdout/stderr
    -> terminal renderer
    -> screen
```

The output stream must not be parsed, filtered, transformed into chat UI, or re-rendered as custom widgets. Status detection may observe a passive copy of the stream.

## v0.1 Architecture Direction

```
Android app
    - host/session list
    - terminal view
    - quick input bar
    - local known-hosts store
    - secure key storage
        |
        v
SSH transport
    - authenticate
    - verify/pin host key
    - request PTY
    - open shell or launch command
    - stream bytes
        |
        v
Remote host / VM
    - shell
    - Claude / Codex / OpenCode / custom CLI
    - CLI-native transcript and resume metadata
```

## Terminal Renderer Decision

The first renderer spike uses the preferred native path: ConnectBot `termlib`,
an Android Compose terminal component backed by `libvterm`.

Why this path is viable:

- `org.connectbot:termlib` is published as an AAR on Maven Central and is aligned
  with this scaffold's Kotlin 2.3.21 and Compose BOM 2026.05.00 stack.
- The library exposes a Compose `Terminal` surface plus a service-compatible
  `TerminalEmulator` API with raw byte writes, keyboard byte callbacks, resize
  callbacks, color palette control, scrollback, selection, zoom, and soft
  keyboard handling.
- The renderer path keeps Tether Go's data flow byte-preserving: SSH PTY output
  bytes are written to the terminal emulator, and keyboard/quick-bar bytes are
  captured from the terminal input callback and written to SSH stdin without
  parsing terminal output.

The xterm.js WebView fallback is not used in this spike because the native
renderer is available, builds in the app, supports mobile terminal gestures, and
avoids adding a JavaScript/WebView bridge before it is needed.

Renderer validation from PR #4:

- The app streams ANSI-colored fake PTY output at scrollback-building volume.
- Portrait and landscape emulator screenshots verify readable text, theme fit,
  automatic resize, and stable quick-bar/input-strip layout.
- Forced-size controls exercise fixed 80x24 and 132x40 terminal sizing.
- Hardware-style keyboard input and quick-bar input both reach the stdin test
  buffer.

Current SSH PTY validation path:

- Direct SSH connection uses password auth and an in-memory host-key verifier.
- The app requests an `xterm-256color` PTY, opens a shell, and routes terminal
  input/output through the same native terminal path.
- Run real TUIs such as `bash`, `vim`, `top`, Claude, Codex, and OpenCode.
- Test Android lifecycle behavior with a live SSH channel.
- Add persisted host-key TOFU and secure key storage before using production
  credentials.

## Leading SSH Spike

The preferred first spike is Kotlin + Jetpack Compose with native SSH and
terminal libraries.

- ConnectBot `cbssh` is viable and selected for SSH. The current published
  artifact is `org.connectbot.sshlib:sshlib:0.3.0`, while the older
  `org.connectbot:sshlib` artifact is not used for this spike.
- ConnectBot `termlib` remains selected for terminal rendering.
- The app keeps the terminal data path byte-preserving: SSH stdout/stderr chunks
  are written directly to `termlib`, and terminal keyboard/quick-bar bytes are
  written directly to the SSH session.

Fallback:

- SSHJ or mwiede/jsch for SSH.
- xterm.js inside Android WebView, with a native byte bridge.

The spike must run real TUIs (`bash`, `vim`, `top`, Claude, Codex) before the stack is accepted.

## Lifecycle Semantics

For v0.1, sessions are phone-owned.

- While the app is open, keep SSH sessions connected.
- If the user intentionally closes the app, close SSH channels and let remote PTYs die where Android allows.
- Background, swipe-away, and OS-kill behavior must be tested and documented with the chosen stack.

Durable shared sessions are a later backend/agent problem, not a v0.1 requirement.

## Security Baseline

- SSH host key TOFU from the first production SSH implementation. The current
  spike accepts the first presented key in memory and rejects a different key
  during the same connection attempt, but it does not persist known hosts.
- Fail closed on host key changes.
- Private key material encrypted at rest.
- No plaintext tokens, passphrases, private keys, or Vault material in logs or config.
- No public relay service.

## Deferred Architecture

The following need separate design before implementation:

- Coder transport.
- Vault integration.
- Usage/quota tracking.
- Tether Desktop API.
- Tether Agent.
- Local Android transport.
- Remote Control handoff.
