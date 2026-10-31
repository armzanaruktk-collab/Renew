# SSH Server and Payload Manager Design

## Goal

Keep SSH server profiles and reusable payload templates as separate collections. A payload is not owned by a server; one saved payload can be selected by multiple server profiles.

## User experience

### SSH Servers

- Add, edit, duplicate, delete, and select multiple named SSH server profiles.
- Each profile stores: UUID, display name, hostname/IP, SSH port, username, authentication type, and a reference to an optional saved payload.
- Connection mode is either `DIRECT` or `PAYLOAD_TUNNEL`.
- `DIRECT` is the default and does not require proxy or payload fields.
- `PAYLOAD_TUNNEL` requires a selected saved payload and its proxy endpoint.

### Payload Manager

- Separate screen reachable from the main screen and from the server editor.
- Create, edit, duplicate, and delete payload templates.
- Each payload stores: UUID, display name, payload text, proxy host, proxy port, and optional notes.
- Payload records are stored independently from SSH server records.
- The server editor uses a picker showing saved payload names; an inline “Manage payloads” action opens the manager.
- Deleting a payload referenced by a server must ask for confirmation and clear or reassign references safely; never silently leave a broken reference.

## Persistence model

Use two independent versioned JSON stores in app-private internal storage:

- `ssh_servers.json`: array of SSH server profile records.
- `payloads.json`: array of payload template records.

Use UUIDs as stable identifiers, not list positions or display names. Load into typed model classes, tolerate missing files as empty collections, write atomically through a temporary file followed by rename, and preserve unknown/newer fields where feasible for forward compatibility.

Credentials (passwords/private keys) must remain local to the device and must never be committed to this repository. For production, move secrets to Android Keystore-backed encrypted storage; do not put credentials in plain JSON or logs. Payload text and proxy endpoints are also user data and remain in app-private storage.

## Connection behavior

1. User selects a server profile.
2. For `DIRECT`, connect SSH directly to the profile hostname and port.
3. For `PAYLOAD_TUNNEL`, resolve the referenced payload, validate proxy host/port and payload text, establish the configured proxy transport to an endpoint the user owns or is authorized to use, then establish SSH through that transport.
4. Only after SSH authentication and local SOCKS forwarding succeed should the VPN/TUN layer be started.
5. If any stage fails, close partial sockets/SSH sessions and show the failing stage without claiming VPN is connected.

Important: a payload editor alone does not implement payload tunneling. The SSH transport must explicitly support connecting through the selected proxy/gateway. Until that transport exists and is tested, the UI must label the mode as not yet operational rather than silently treating it as direct SSH.

## Validation

- Server: non-empty name/host/username; port 1–65535; valid authentication material.
- Direct mode: no payload selection required.
- Payload mode: existing payload UUID, non-empty proxy host and payload text, proxy port 1–65535.
- Prevent deleting the currently active server or payload while a connection is using it, or disconnect cleanly first.

## Acceptance checks

- Create two SSH servers and three payloads; close/reopen the app and confirm both collections persist independently.
- Assign one payload to two servers and confirm changing the payload does not duplicate or overwrite server records.
- Edit/delete a payload and verify reference handling.
- Direct mode connects without proxy configuration.
- Payload mode must demonstrably establish SSH through the configured proxy, not merely save/display the payload.
- Verify authentication failures, proxy failures, malformed ports, empty fields, app restart, and interrupted connections.
- Run the repository's GitHub Actions APK workflow; CI build success is not a substitute for testing a real SSH/VPN connection on a device.
