# Changelog

All notable changes to Tether Go will be documented in this file.

This project follows a human-maintained changelog. Dates use `YYYY-MM-DD`.

## Unreleased

### Added

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

- `MainActivity` hosts the product navigation (`TetherGoApp`) instead of the
  terminal renderer spike screen.

### Removed

- The terminal renderer spike screen and fake-PTY pressure-test scaffolding,
  superseded by the product terminal view.

## Initial scaffold

- Initial repository structure, Android scaffold, terminal-renderer and SSH PTY
  spikes, host-key TOFU store, and private-key import storage.
