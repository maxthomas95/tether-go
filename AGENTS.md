# Repository Guidelines

## Project Overview

Tether Go is an Android client for managing SSH-backed agent CLI sessions from a phone. It is part of the Tether product family:

- **Tether** - desktop session multiplexer.
- **Tether Go** - mobile, remote-first session client.
- **VoidCode VR** - spatial VR session manager.

**Status:** Early repository setup. No app implementation has landed yet.

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

The first technical spike should validate SSH PTY + terminal rendering before committing to full product UI.

Current leading option:
- Kotlin + Jetpack Compose.
- Native SSH library with PTY support.
- Native terminal component if fidelity is good enough.

Fallback:
- Native SSH bridge plus xterm.js in Android WebView.

Do not add an Android project until the first implementation PR is ready to own the build/test commands and stack choice.

## Project Structure

This repository is intentionally documentation-only at setup time.

Planned structure once implementation starts:

- `app/`: Android application module.
- `docs/`: architecture, release, and product notes.
- `.github/`: PR templates, issue templates, and CI workflows.

## Build, Test, and Development Commands

No build system exists yet. Do not invent commands in PR descriptions until they exist.

Expected future commands:

- `./gradlew test`: run unit tests.
- `./gradlew lint`: run Android lint.
- `./gradlew assembleDebug`: build a debug APK.
- `./gradlew assembleRelease`: build a release APK once signing is configured.

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
