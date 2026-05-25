# Roadmap - Tether Go

This is a living document. The goal is to validate a small, real mobile terminal experience before expanding into broader Tether parity.

Status legend: **[planned]** not started, **[in progress]** active, **[blocked]** waiting on something, **[done]** shipped.

## v0.1

- [planned] Android stack decision and technical spike.
- [planned] Direct SSH connection to a reachable host.
- [planned] Remote PTY allocation with `xterm-256color`.
- [planned] Terminal renderer with real TUI clarity.
- [planned] Keyboard and quick-bar input routed to PTY stdin unchanged.
- [planned] Host key TOFU and pinned known-hosts store.
- [planned] Private key import with encrypted local storage.
- [planned] New session flow: host, working directory, CLI tool, launch flags, env vars.
- [planned] Phone-owned session list and full-screen terminal view.
- [planned] Basic phone-owned notifications for observed sessions.
- [planned] Tether visual identity and theme continuity.

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
