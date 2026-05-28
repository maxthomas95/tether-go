# Roadmap - Tether Go

This is a living document. The goal is to validate a small, real mobile terminal experience before expanding into broader Tether parity.

Status legend: **[planned]** not started, **[in progress]** active, **[blocked]** waiting on something, **[done]** shipped.

## v0.1

- [done] Android stack decision: native ConnectBot `termlib` renderer +
  `cbssh` SSH transport.
- [done] Direct SSH connection to a reachable host with password and
  private-key auth.
- [done] Remote PTY allocation with `xterm-256color`.
- [done] Terminal renderer with real TUI clarity in the full-screen terminal
  view.
- [done] Keyboard and quick-bar input routed to PTY stdin unchanged.
- [done] Minimal persisted host records, host key TOFU, and pinned known-hosts
  store.
- [done] Private key import with encrypted local storage.
- [done] New session flow: host, working directory, CLI tool, launch flags, env vars.
- [done] Phone-owned session list and full-screen terminal view.
- [done] Tether visual identity and theme continuity (Mocha default + full
  desktop theme set, live switching).
- [planned] Basic phone-owned notifications for observed sessions.
- [planned] Voice-to-text quick input.

## Later

- Coder transport once there is a concrete test path.
- Vault-backed env vars.
- Usage and quota tracking for phone-owned sessions.
- Better CLI resume helpers or transcript pickers.
- Import/export of desktop-style environment config.
- Tether Desktop API or Tether Agent for shared live sessions and richer notifications.
- Claude Remote Control handoff.
- Local Android transport, likely Termux-backed or bundled userland.
- iOS only if the Android experience proves valuable.

## Non-Goals for v0.1

- Desktop session discovery.
- Automatic handoff from desktop to phone.
- Public relay service.
- Split panes.
- Coder.
- Vault.
- Local Android PTY.
- App store distribution.
