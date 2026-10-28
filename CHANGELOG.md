# Changelog

All notable changes to Tether Go will be documented in this file.

This project follows a human-maintained changelog. Dates use `YYYY-MM-DD`.

## Unreleased

### Added

- Explicit Android 13+ notification permission action in Settings, with an
  Android settings fallback after denial/dismissal and refreshed app/channel
  access status on return. The app alert preference remains independent.

- Nord, Gruvbox Dark, Gruvbox Light, and Harbor appearance themes, completing
  the 11 built-in desktop theme choices.

- Ranked fuzzy session search across labels, SSH endpoints, working directories,
  and CLI tools, plus an All / Waiting filter in phone and expanded layouts.
  No-match results can be cleared without changing the active terminal.
- Per-session notification mute / unmute actions and a visible muted state.
  Mute preferences persist across app restarts; existing saved sessions default
  to unmuted, and muting clears an existing session alert.
- Tether product UI shell with the house visual identity. The session list is
  now the home screen, grouped by host, with CLI-tool chips, status dots, and
  per-session rename / disconnect / reconnect / remove actions.
- New Session flow: pick or enter an SSH host, choose password or imported
  private-key auth, select a CLI tool (Claude / Codex / Copilot / OpenCode /
  Custom), a working directory, common launch flags, environment variables, and
  a session label, with a live launch-command preview.
- Full-screen terminal view with a mobile quick bar (Esc, Tab, Enter, arrows,
  Ctrl-C, Ctrl-D, `/`, `Y`, `N`, soft-keyboard toggle) and inline reconnect.
- `SessionManager` owns multiple phone-owned SSH session runtimes, each with a
  persistent ConnectBot `termlib` emulator so scrollback survives navigation,
  and exposes observable session status. Launch commands are typed into the PTY
  on connect, preserving the dumb-pipe invariant.
- CLI tool registry, POSIX shell quoting, and launch-command builder ported from
  desktop Tether shared code.
- Theme system ported from desktop Tether: Catppuccin Mocha (default),
  Macchiato, Frappé, Latte, Tether (Default Dark), Tether Light, and Brass, with
  live theme switching and a terminal font-size setting.
- Hosts & keys management screen and a Settings screen (theme picker, font
  size, about).
- Tether logo branding for the launcher icon and in-app, plus a dark launch
  window background.
- CI now uploads the debug APK as a build artifact for sideloading.

### Changed

- Built-in theme colors now match corresponding current desktop tokens,
  including improved muted-text contrast. Stored theme IDs, picker order for
  existing themes, and the Mocha default are unchanged. Native terminal
  cursor/selection behavior and custom-theme import remain separate work.

- Session counts and connection actions consistently treat Connecting, Running,
  Waiting, and Idle as live; Idle sessions correctly offer Disconnect.
- `MainActivity` hosts the product navigation (`TetherGoApp`) instead of the
  terminal renderer spike screen.

### Removed

- The terminal renderer spike screen and fake-PTY pressure-test scaffolding,
  superseded by the product terminal view.

## Initial scaffold

- Initial repository structure, Android scaffold, terminal-renderer and SSH PTY
  spikes, host-key TOFU store, and private-key import storage.
