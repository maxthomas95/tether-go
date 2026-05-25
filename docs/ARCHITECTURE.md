# Architecture - Tether Go

## Current Status

This repository is in setup phase. No Android implementation has landed yet.

The first implementation goal is a technical spike that proves direct SSH PTY streaming into a mobile terminal while preserving Tether's dumb-pipe invariant.

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

## Leading Technical Spike

The preferred first spike is Kotlin + Jetpack Compose with native SSH and terminal libraries. Candidate libraries:

- ConnectBot `cbssh` for SSH.
- ConnectBot `termlib` for terminal rendering.

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
