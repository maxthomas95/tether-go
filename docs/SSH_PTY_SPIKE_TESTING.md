# SSH PTY Spike Manual Test Notes

This spike connects directly to a user-provided SSH host, authenticates with a
password, requests an `xterm-256color` PTY, opens a shell, streams SSH channel
bytes into ConnectBot `termlib`, and sends terminal input bytes back to the SSH
session unchanged.

## Stack Decision

- Renderer: ConnectBot `termlib`, the native Compose/libvterm renderer selected
  in PR #4.
- SSH: ConnectBot `cbssh`, published as `org.connectbot.sshlib:sshlib:0.3.0`.
- `cbssh` is viable for this spike because it supports password and public-key
  auth, host-key verification callbacks, session channels, `requestPty`,
  `requestShell`, raw stdout/stderr channels, stdin writes, and window-change
  resize requests.
- The older `org.connectbot:sshlib` artifact is not used; that artifact points
  at the previous ConnectBot SSH library lineage, while the current `cbssh`
  repository publishes under `org.connectbot.sshlib`.

## Security Boundary

- Passwords are held only in the running Compose state for this spike.
- Private key UI and secure key storage are not implemented.
- The spike verifier accepts the first presented host key in memory and rejects a
  different key during the same connection attempt. Persisted TOFU, pinned
  known-hosts storage, and host-key acceptance UI are deferred.
- Do not use this spike with untrusted networks or production credentials.

## Setup

1. Build and install the debug APK.
2. Launch Tether Go.
3. Enter host, port, username, and password.
4. Tap `Connect`.
5. Confirm the status line reports an `xterm-256color` shell and shows a host-key
   SHA-256 fingerprint.

## Bash

Run:

```bash
echo "$TERM"
printf '\e[31mred\e[0m \e[32mgreen\e[0m\n'
stty size
```

Expected:

- `$TERM` is `xterm-256color`.
- ANSI color renders in the terminal.
- `stty size` matches the displayed terminal rows and columns.
- Typed text, Enter, Tab, arrows, Ctrl-C, and Ctrl-D route to the remote shell.

## Resize

1. Rotate the device or use the quick bar `80x24`, `132x40`, and `Auto` controls.
2. Run `stty size` after each resize.

Expected:

- The remote PTY receives the new rows and columns through SSH window-change.
- Full-screen TUIs redraw rather than being clipped to the old dimensions.

## Vim

Run:

```bash
vim /tmp/tether-go-vim-test.txt
```

Expected:

- Vim opens full screen without corrupted borders.
- Insert mode receives typed text exactly.
- Esc exits insert mode.
- Arrow keys move the cursor.
- `:wq` exits cleanly and returns to the shell.

## Top

Run:

```bash
top
```

Expected:

- The screen updates in place without app-side parsing or repaint artifacts.
- Ctrl-C or `q` returns to the shell.
- Resizing while `top` is running causes a TUI redraw.

## Claude

Run:

```bash
claude
```

Expected:

- The CLI owns the full terminal surface.
- Prompts, spinners, alternate-screen behavior, and streaming output render as
  raw PTY output.
- Keyboard and quick-bar Enter/Esc/Ctrl-C reach the CLI unchanged.

## Codex

Run:

```bash
codex
```

Expected:

- The CLI starts and draws its native terminal UI.
- Text input, command submission, arrows, and Ctrl-C behave as they do in a
  normal SSH terminal.
- Resume commands remain CLI-native and manual.
