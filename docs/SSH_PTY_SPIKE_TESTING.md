# SSH PTY Spike Manual Test Notes

This spike connects directly to a user-provided SSH host, authenticates with a
password or imported SSH private key, requests an `xterm-256color` PTY, opens a
shell, streams SSH channel bytes into ConnectBot `termlib`, and sends terminal
input bytes back to the SSH session unchanged.

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
- Imported private keys and optional passphrases are persisted only through the
  Android Keystore-backed encrypted private-key store. The Keystore key requires
  recent device credential or strong biometric authentication before use.
- Minimal host records are persisted with host, port, username, timestamps, and
  an optional private-key id reference. Host records do not contain private key
  bytes, passphrases, passwords, or tokens.
- Private-key metadata and material are separate from host records. Deleting a
  host does not delete imported key material; deleting a key removes its
  encrypted material and metadata.
- The first presented host key for a host/port is shown in a fingerprint
  confirmation prompt before authentication. Accepting pins the key in local
  known-hosts storage; rejecting fails the connection closed.
- A later different key for the same host/port is rejected before password
  authentication. Delete the saved host before intentionally trusting a changed
  server key.
- Do not paste private keys into logs, issue comments, screenshots, or bug
  reports. The app should not log private keys, passphrases, passwords, or
  tokens.

## Setup

1. Build and install the debug APK.
2. Launch Tether Go.
3. Enter host, port, username, and password, then tap `Save host` if you want to
   keep the host record before connecting. For private-key auth, use the
   private-key flow below before connecting.
4. Tap `Connect`.
5. On first connect to a host/port, compare the displayed SHA-256 host-key
   fingerprint with a trusted source. For an OpenSSH server, one check is:

   ```bash
   ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub -E sha256
   ```

6. Tap `Accept` only for the intended server. Confirm the status line reports an
   `xterm-256color` shell and shows the host-key SHA-256 fingerprint.

## Host-Key TOFU

Unknown host:

1. Clear app data or delete the saved host from Tether Go.
2. Connect to the host.
3. Confirm the prompt shows host, port, key type, and SHA-256 fingerprint.
4. Tap `Reject`.

Expected:

- The connection fails before password authentication.
- The host key is not pinned.

Accepted host:

1. Connect to the same unknown host again.
2. Tap `Accept`.
3. Disconnect and reconnect to the same host/port.

Expected:

- The first accepted connection pins the host key.
- The reconnect does not show another host-key prompt.
- The shell opens after password authentication.

Changed key:

1. Point the same host/port at an SSH server with a different host key, or rotate
   the test server's host key.
2. Connect again from Tether Go.

Expected:

- Tether Go rejects the connection before password authentication.
- The status line reports a host-key mismatch with the expected and presented
  SHA-256 fingerprints.
- Deleting the saved host removes the pin when no other saved host record uses
  that host/port.

## Private-Key Import and Auth

Import:

1. Tap `Private key`, then `Import key`.
2. Enter a label and paste an existing PEM private key, such as an OpenSSH key
   beginning with `-----BEGIN OPENSSH PRIVATE KEY-----`.
3. Enter the key passphrase when the key is encrypted and you want Tether Go to
   reuse it for auth.
4. Tap `Import`.

Expected:

- A new key chip appears with the label and is selected for private-key auth.
- Invalid PEM input is rejected before it is stored.
- Private key bytes and passphrase text do not appear in app logs.
- If the device has not been recently unlocked with a credential or strong
  biometric, encrypted key reads may fail until it is unlocked again.

Host selection:

1. Select an imported key.
2. Enter host, port, and username.
3. Tap `Save host`.
4. Select another host, then reselect the saved host.

Expected:

- The saved host restores private-key auth when the referenced key still exists.
- The host record stores only the key id reference, not key material.
- If the key was deleted, the host falls back to password auth and reports that
  the saved private key is unavailable.

Private-key connection:

1. Select an imported key for a test SSH account that accepts that key.
2. Tap `Connect`.
3. Accept the host-key fingerprint only after comparing it with a trusted
   source if this is the first connection to that host/port.

Expected:

- Host-key TOFU behavior is unchanged: unknown keys prompt, rejected keys fail,
  and changed keys fail closed before authentication.
- Authentication uses the selected private key and optional passphrase.
- The shell opens in an `xterm-256color` PTY and terminal input/output remains
  byte-preserving.

Delete key:

1. Disconnect.
2. Select an imported key and tap `Delete key`.
3. Try to connect with private-key auth without selecting another key.

Expected:

- The key is removed from the key list.
- Connecting with private-key auth is disabled until another key is selected or
  imported.

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
