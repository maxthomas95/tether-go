# Roadmap - Tether Go

This is a living product and engineering roadmap. The goal is to ship a small,
real, phone-owned SSH terminal first, then expand only after the direct mobile
experience is reliable.

Status legend: **[planned]** not started, **[in progress]** active, **[blocked]**
waiting on something, **[done]** implemented in the current codebase.

## Current Bet

Tether Go should prove that a phone can be a useful remote control surface for
agent CLIs without becoming a chat renderer or desktop-session clone. v0.1 stays
small: direct SSH, real PTY, native terminal rendering, phone-owned sessions,
host-key TOFU, encrypted private keys, local notifications, and manual
CLI-native resume.

The core invariant does not change by phase: raw PTY bytes go into the terminal
emulator, and user input goes back to stdin unchanged. Any status, notification,
usage, or resume feature must be a passive side channel.

## Phase 0 - Stack Proof

Status: **[done]**

Goal: prove the Android stack can render a real terminal surface before product
work depends on it.

- [done] Kotlin + Jetpack Compose Android scaffold.
- [done] ConnectBot `termlib` selected as the native terminal renderer.
- [done] Fake PTY pressure test for ANSI output, scrollback, resize, soft
  keyboard behavior, hardware-style keyboard input, and quick-bar input.
- [done] xterm.js WebView kept as a fallback, not the active path.

Exit gate:

- [done] A real terminal emulator can render TUI-shaped output on phone-sized
  and landscape layouts without app-side terminal re-rendering.

## Phase 1 - Direct SSH Terminal

Status: **[done]**

Goal: connect to a reachable host, open a real remote PTY, and keep the terminal
data path byte-preserving.

- [done] ConnectBot `cbssh` selected for SSH transport.
- [done] Password authentication for direct SSH.
- [done] Private-key authentication with imported key material.
- [done] Remote PTY allocation with `xterm-256color`.
- [done] Shell launch and CLI launch-command typing through PTY stdin.
- [done] Keyboard, terminal, and quick-bar input routed to SSH stdin unchanged.
- [done] SSH output streamed directly into `termlib`.
- [done] Resize propagated to the remote PTY through SSH window-change.

Exit gate:

- [done] Real TUIs such as `bash`, `vim`, `top`, Claude, Codex, and OpenCode can
  run through the same terminal path.

## Phase 2 - v0.1 Product Shell

Status: **[done]**

Goal: turn the transport spike into the smallest usable Tether Go app.

- [done] Session-list home grouped by host, with CLI chips and status dots.
- [done] New Session flow for host, auth, CLI tool, working directory, launch
  flags, environment variables, and labels.
- [done] Full-screen terminal view with mobile quick bar.
- [done] `SessionManager` owning multiple phone-owned SSH runtimes.
- [done] Persistent `termlib` emulator per session so scrollback survives
  navigation.
- [done] Host management, known-host pinning, and fail-closed changed-key
  behavior.
- [done] Encrypted private-key and optional passphrase storage through Android
  Keystore-backed local encryption.
- [done] Tether visual identity, launcher/in-app logo, desktop theme set, live
  theme switching, and terminal font-size setting.
- [done] Tablet/foldable expanded layout with session list plus detail pane.

Exit gate:

- [done] A user can create, reconnect, rename, disconnect, and remove
  phone-owned sessions without introducing desktop, relay, Vault, Coder, or
  local Android PTY dependencies.

## Phase 3 - v0.1 Release Hardening

Status: **[in progress]**

Goal: make the implemented app dependable enough for a first sideloaded v0.1.

- [done] Foreground `SessionService` owns sessions across configuration changes
  and backgrounding.
- [done] Persistent "sessions active" notification while the service is holding
  live SSH sessions.
- [done] Passive status detector for OSC 9, bell, waiting, and idle signals.
- [done] Phone-owned "waiting for input" and bell notifications for observed
  sessions.
- [done] Settings toggle for session notifications.
- [done] Push-to-talk voice input that types final speech recognition text into
  PTY stdin.
- [planned] Add Android 13+ runtime notification-permission request/status UX so
  session pings are not silently disabled by the platform permission state.
- [in progress] Device validation for notification permission behavior,
  notification tap routing, foreground-service lifecycle, and swipe-away
  teardown.
- [in progress] Device validation for voice input permissions, unavailable
  recognizer handling, transcript insertion, and terminal focus behavior.
- [planned] Update `docs/SSH_PTY_SPIKE_TESTING.md` with release-candidate manual
  checks for notifications, voice, lifecycle, and foldable/tablet layout.
- [planned] Refresh release checklist for unsigned debug artifact distribution.
- [planned] Confirm lint/test/assemble checks on the release-candidate branch.

Exit gate:

- [planned] A debug APK artifact can be sideloaded and tested against a real SSH
  host with password auth, private-key auth, host-key TOFU, terminal resize,
  app backgrounding, notification pings, notification tap-to-session, and voice
  input.

## Phase 4 - v0.1 Sideload Release

Status: **[planned]**

Goal: publish a first intentionally scoped APK for trusted testers.

- [planned] Finalize `CHANGELOG.md` for v0.1.
- [planned] Fill `docs/RELEASE_CHECKLIST.md` with the actual debug-artifact
  release process.
- [planned] Add tester-facing known issues and support notes.
- [planned] Tag the first v0.1 build once validation is complete.
- [planned] Keep Play Store/App Store signing and public distribution out of
  this phase.

Exit gate:

- [planned] Trusted testers can install the APK, connect to their own SSH host,
  run an agent CLI, and report issues against a documented known-good baseline.

## Phase 5 - Post-v0.1 Depth

Status: **[planned]**

Goal: improve the phone-owned experience before adding shared-session backends.

- [planned] Better session lifecycle controls and clearer reconnect failure
  recovery.
- [planned] CLI-native resume helpers or transcript pickers that do not parse or
  replace the terminal stream.
- [planned] Import/export of desktop-style environment configuration.
- [planned] Usage and quota tracking as passive side-channel data.
- [planned] More complete key management, including key labels, rotation
  guidance, and safer deletion flows.
- [planned] Broader manual test matrix across Android versions, phone sizes,
  foldables, tablets, keyboards, and SSH server configurations.

Exit gate:

- [planned] Tether Go feels reliable as a standalone direct-SSH mobile client,
  with fewer rough edges around reconnects, permissions, and repeat workflows.

## Phase 6 - Tether Family Integration

Status: **[planned]**

Goal: expand toward cross-device Tether behavior only after the direct mobile
client has earned the complexity.

- [planned] Tether Desktop API or Tether Agent for shared live sessions and
  richer notifications.
- [planned] Coder transport once there is a concrete test path.
- [planned] Vault-backed environment variables after a separate secrets design.
- [planned] Claude Remote Control handoff after the local direct-SSH story is
  stable.
- [planned] Local Android transport, likely Termux-backed or bundled userland,
  after a separate Android execution design.
- [planned] iOS only if Android proves valuable enough to justify a second
  platform.

Exit gate:

- [planned] A written architecture/security design exists for each cross-device
  or secrets-related integration before implementation begins.

## Non-Goals for v0.1

- Desktop session discovery.
- Automatic handoff from desktop to phone.
- Public relay service.
- Split panes beyond the existing wide-screen list/detail layout.
- Coder.
- Vault.
- Local Android PTY.
- App store distribution.
