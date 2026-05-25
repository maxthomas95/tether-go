# Architecture - Tether Go

## Current Status

This repository contains the initial Android scaffold plus the first focused
terminal-renderer spike. The app module now renders a ConnectBot `termlib`
terminal fed by a high-volume fake PTY byte stream. Keyboard and quick-bar input
are routed into a local stdin test buffer so input plumbing can be inspected
without introducing SSH yet.

The next implementation goal remains a technical spike that proves direct SSH PTY
streaming into a mobile terminal while preserving Tether's dumb-pipe invariant.

No SSH transport, session management, persistence, host-key storage, or product
session UI has landed yet.

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
- ConnectBot `termlib` is used for the first native terminal renderer spike.

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
- The renderer path keeps Tether Go's data flow byte-preserving: fake PTY bytes
  are written to the terminal emulator, and keyboard/quick-bar bytes are captured
  from the terminal input callback without parsing terminal output.

The xterm.js WebView fallback is not used in this spike because the native
renderer is available, builds in the app, supports mobile terminal gestures, and
avoids adding a JavaScript/WebView bridge before it is needed.

Current validation:

- The app streams ANSI-colored fake PTY output at scrollback-building volume.
- Portrait and landscape emulator screenshots verify readable text, theme fit,
  automatic resize, and stable quick-bar/input-strip layout.
- Forced-size controls exercise fixed 80x24 and 132x40 terminal sizing.
- Hardware-style keyboard input and quick-bar input both reach the stdin test
  buffer.

Remaining acceptance work before the stack is fully accepted:

- Wire a real SSH PTY transport into the same terminal input/output path.
- Run real TUIs such as `bash`, `vim`, `top`, Claude, Codex, and OpenCode.
- Test Android lifecycle behavior with a live SSH channel.
- Add host-key TOFU and secure key storage with the first SSH implementation.

## Leading SSH Spike

The preferred first spike is Kotlin + Jetpack Compose with native SSH and terminal libraries. Candidate libraries:

- ConnectBot `cbssh` for SSH.
- ConnectBot `termlib` for terminal rendering. The renderer side is now the
  selected path for the first native spike.

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

- SSH host key TOFU from the first SSH implementation.
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
