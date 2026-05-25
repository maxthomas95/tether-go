# Architecture - Tether Go

## Current Status

This repository contains the initial Android scaffold plus focused terminal and
SSH PTY spikes. The app module renders a ConnectBot `termlib` terminal and can
connect directly to a user-provided SSH host with password or private-key auth,
request an `xterm-256color` PTY, open a shell, stream raw SSH channel bytes
into the terminal, and route keyboard and quick-bar input back to the SSH
channel unchanged.

The current spike also persists minimal host records, pinned known-host keys,
and imported SSH private keys. An unknown host key pauses first connection for
explicit SHA-256 fingerprint confirmation, accepted keys are pinned per
host/port, and changed keys fail closed before authentication. Private keys and
optional passphrases are stored through an Android Keystore-backed AES-GCM
wrapper around local preferences; host records store only a selected private-key
id reference. No polished session management or product session UI has landed
yet.

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

- Direct SSH connection uses password auth or imported private-key auth with
  persisted host-key TOFU.
- Unknown host keys show a fingerprint confirmation prompt before
  authentication.
- Accepted host keys are pinned in local known-hosts storage keyed by host and
  port.
- Reconnecting to the same key continues without another prompt; a different key
  for the same host and port is rejected before password authentication.
- The app requests an `xterm-256color` PTY, opens a shell, and routes terminal
  input/output through the same native terminal path.
- Run real TUIs such as `bash`, `vim`, `top`, Claude, Codex, and OpenCode.
- Test Android lifecycle behavior with a live SSH channel.
- Validate private-key import and auth against representative unencrypted and
  passphrase-protected keys before using production private keys.

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

- SSH host key TOFU is implemented for the current SSH spike.
- First connection to an unknown host/port requires explicit fingerprint
  confirmation.
- Accepted host keys are stored as pinned known-host entries containing key type,
  encoded public key, SHA-256 fingerprint, and acceptance time. Host records and
  host keys are not secrets; passwords remain in transient UI state and are not
  persisted.
- Imported private keys are stored in `tether_go_ssh_private_keys` preferences
  only after AES-GCM encryption with a symmetric key generated and held by
  Android Keystore under the app alias. The Keystore key requires recent device
  credential or strong biometric authentication before use. Optional
  private-key passphrases use the same encrypted store.
- Host records stay in the non-secret host store and include only host, port,
  username, timestamps, and an optional private-key id. They do not contain
  private key bytes or passphrases.
- Android backup and device-transfer extraction are disabled for shared
  preferences in `data_extraction_rules.xml`, and `allowBackup` is disabled in
  the manifest so encrypted blobs are not restored without their Keystore key.
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
