# Repository Guidelines

## Project Overview

Tether Go is an Android client for managing SSH-backed agent CLI sessions from a phone. It is part of the Tether product family:

- **Tether** - desktop session multiplexer.
- **Tether Go** - mobile, remote-first session client.
- **VoidCode VR** - spatial VR session manager.

**Status:** The Tether product UI shell has landed on top of the SSH transport.
The app has a session-list home, a New Session flow, a full-screen terminal view
with a mobile quick bar, host & key management, and settings (theme + font),
backed by a `SessionManager` that owns multiple phone-owned SSH session runtimes
with persistent `termlib` emulators. Direct SSH, host-key TOFU, encrypted
private-key storage, phone-owned notifications, and push-to-talk voice input are
in place. The current roadmap focus is release-candidate validation and v0.1
hardening.

## Core Principle

**Dumb pipe, smart shell.** Never parse, filter, intercept, or re-render CLI output. The terminal stream should remain raw PTY bytes flowing into a terminal emulator. Status detection and notifications are passive side-channel features, not terminal renderers.

## v0.1 Product Scope

- Direct SSH to a reachable host or VM.
- Open a real remote PTY.
- Launch Claude, Codex, OpenCode, or a custom CLI.
- Render a real terminal TUI on Android.
- Keep sessions phone-owned for v0.1.
- Let users manually run CLI-native resume commands when they want resume.
- Keep Coder, Vault, desktop API, Tether Agent, local Android PTY, and public relay out of v0.1.

## Expected Tech Direction

The first terminal-renderer and SSH PTY spikes have landed. Future work should
preserve the selected native stack unless a focused spike proves a replacement
is needed.

Current scaffold:
- Kotlin + Jetpack Compose.
- Android Gradle Plugin 9.2.0 with Gradle 9.4.1.
- AGP 9 built-in Kotlin support plus the Compose compiler plugin.
- Compose BOM 2026.05.00.
- Compile/target SDK 36, min SDK 26.
- Application ID and namespace `com.tether.go`.

Selected terminal renderer:
- ConnectBot `termlib` native Compose terminal component backed by `libvterm`.

Selected SSH transport:
- ConnectBot `cbssh` (`org.connectbot.sshlib:sshlib`) with PTY support.

Fallback:
- Native SSH bridge plus xterm.js in Android WebView.

## Project Structure

Current structure:

- `app/`: Android application module.
- `docs/`: architecture, release, and product notes.
- `.github/`: PR templates, issue templates, and CI workflows.

## Build, Test, and Development Commands

Use the Gradle wrapper. These commands exist now:

- `./gradlew test`: run unit tests.
- `./gradlew lint`: run Android lint.
- `./gradlew assembleDebug`: build a debug APK.

Do not document `./gradlew assembleRelease` as a release command until signing is
configured.

## Coding Style and Naming Conventions

When app code exists:

- Prefer Kotlin for native Android code unless the stack decision changes.
- Keep terminal data flow byte-preserving.
- Keep cross-boundary protocol types centralized.
- Use descriptive names for session, transport, terminal, and host-key concepts.
- Keep UI state in UI/view-model layers and transport lifecycle in transport/session layers.
- Keep secrets out of plaintext files and logs.

## Testing Guidelines

Add tests with implementation. Prioritize:

- SSH host-key verification.
- Transport lifecycle and close behavior.
- Terminal input/output byte routing.
- Launch command construction.
- Secure storage wrappers.
- Status detection side-channel logic.

Run the relevant Gradle checks before submitting once they exist.

## Commit and Pull Request Guidelines

### Commit Message Format

Use conventional commit prefixes for subject lines: `feat:`, `fix(scope):`, `refactor(scope):`, `docs:`, `chore:`, etc. Keep subjects under one line.

**Commit body:** Write detailed paragraphs explaining what changed, why, and how. Cover user-visible behavior, persistence/schema impacts, transport impacts, security boundaries, and deliberately deferred work.

**Agent trailers:** End commits with `Co-authored-by: <agent> <email>`. When squash-merging, use explicit `--subject` and `--body` to avoid duplicate trailers.

### Worktree and Branch Policy

Multiple agent sessions may be active simultaneously. Follow these rules:

- **Never work directly on `main`** - always create a feature branch.
- **Prefer separate worktrees** (`git worktree add`) for substantial tasks.
- **Stage only relevant files** - do not disturb unrelated uncommitted changes.
- **GitHub is the single source of truth** - always push to the `github` remote.
- **`main` is protected** - all changes go through branch + PR + squash merge.

### PR Process

1. **Branch:** Create a feature branch from `main` (for example, `feat/ssh-terminal-spike`).
2. **Push:** `git push github HEAD:refs/heads/<branch-name>`.
3. **Create PR:** Against `main` with a body containing:
   - `## Summary` - user-visible behavior, persistence/schema impacts, transport impacts, and security boundaries.
   - `## Test plan` - checkboxes with commands actually run.
   - `## Out of scope` - deliberately deferred follow-up work.
4. **Squash merge:** Use explicit `--subject` and `--body` to produce a single clean commit.

## Development Rules

- Preserve the dumb-pipe terminal invariant.
- Do not store private keys, passphrases, tokens, or Vault material in plaintext.
- Use Android secure storage / Keystore-backed encryption for sensitive local data once app code exists.
- Implement SSH host-key TOFU from the first real SSH implementation.
- Do not add public relay behavior without a separate security design.
- Keep Coder, Vault, desktop API, and local Android PTY as later scoped work unless the roadmap changes.
- Keep SonarCloud/quality-gate configuration current once Android code and CI exist. Do not require status checks that do not exist yet.
