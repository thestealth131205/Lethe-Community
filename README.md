<p align="center">
  <img src="docs/assets/lethe-logo.png" alt="Lethe Logo" width="140">
</p>

<h1 align="center">Lethe</h1>

<p align="center">
  The end-to-end encrypted messenger that puts you back in control.
</p>

---

## What is Lethe?

**Lethe** is an end-to-end encrypted (E2EE) messenger built around one simple idea:
your conversations belong to you — not to a server, not to an advertiser, not to
anyone else. Every message, call and file is encrypted on your device before it
ever leaves it. The server only ever sees ciphertext, never your plaintext content.

On top of that, Lethe is genuinely comfortable to use — fast one-to-one and group
chats, voice and video calls, status updates, polls and multi-device support via
secure QR pairing. Security shouldn't mean compromise, and Lethe is built to prove
that it doesn't have to.

## Absolute security, real comfort

- **End-to-end encryption everywhere** — 1:1 chats, group chats and multi-device
  sync are all encrypted client-side. The server stores ciphertext only.
- **Optional peer-to-peer messaging** — when both sides opt in, messages can be
  exchanged directly over a WebRTC data channel instead of relaying through the
  server.
- **Optional routing over the Tor network** — Lethe can connect through the
  server's Tor hidden service (`.onion`), letting you communicate without
  revealing your network location.
- **Multi-device key sync** — pair a browser or a second device via QR code,
  secured with an ECDH-derived key that never touches the server in the clear.
- **No compromises on usability** — voice/video calls, status updates, polls,
  location sharing (OpenStreetMap-based) and more, all wrapped in the same
  security model.

## Open source

This repository contains the **application source code** for Lethe — the
Android messenger client and the media player companion app. It is released
under the [GPL-3.0 license](LICENSE): you're free to read it, audit it, build
it yourself, and modify it.

This is a source mirror of the client. It does not include the backend server,
infrastructure, or any secrets/keys — only what's needed to build and audit the
app itself.

*(App screenshots will be added here soon.)*

## Learn more

- Website: [letheapp.de](https://letheapp.de)
- F-Droid repository: coming soon

## License

Lethe is licensed under the [GNU General Public License v3.0](LICENSE).
