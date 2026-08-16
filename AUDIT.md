# Security / Production Audit

## Scope

Disposable Privacy Workspace V1, Android application boundary, disposable storage, embedded Tor, browser proxy routing, Cloudflare DoH proxy, lifecycle and privacy controls.

## Implemented

- CSPRNG session IDs using `SecureRandom`.
- Android Keystore-backed session encryption keys.
- Session directory deletion on destruction.
- Orphaned-session cleanup on startup.
- Tor Android 0.4.9.11 integration.
- Tor operational-state check before activation.
- Tor SOCKS routing for the disposable WebView through AndroidX WebKit ProxyController.
- No direct proxy fallback in the browser.
- TorService private data removal after shutdown.
- Cloudflare DoH lookup proxy with TLS SNI bootstrap to `1.1.1.1`.
- WebView file/content access disabled.
- Mixed-content loading disabled.
- Cleartext HTTP disabled at application level.
- No analytics SDK, advertising SDK, account or backend.
- No sensitive content logging in the application code.
- Minimal manifest permissions.

## Important correction

The previous implementation contained a raw `VpnService` TUN interface without a packet-to-SOCKS forwarding engine. That interface has been removed from the production V1 path rather than being marketed as a Tor VPN. Android documents that `VpnService` provides the VPN interface/gateway boundary; a separate forwarding implementation is required. A future full VPN mode should use a maintained native tun2socks implementation and dedicated device tests.

## Remaining release blockers

These cannot be honestly marked verified in this execution environment:

1. Android Gradle build cannot be executed because the environment does not contain an Android SDK/Gradle installation.
2. Instrumentation tests cannot run without an Android emulator or physical device.
3. Real Tor circuit bootstrap cannot be executed without network access.
4. `.onion` browser behavior requires device-level verification.
5. Cloudflare DoH proxy behavior requires device/network verification.
6. Native dependency CVE/license review must be repeated at release time.

## Threat-model limitations

The application cannot control Android OS telemetry, other applications, keyboard/input-method data, screenshots, DNS/network traffic generated outside the supported browser, baseband/radio metadata, or flash wear-leveling. It cannot guarantee forensic erasure of physical storage.

## Release recommendation

**Not yet release-certified.** Source-level security gates are substantially implemented, but the required Android build/device/network validation has not been executed in this environment.
