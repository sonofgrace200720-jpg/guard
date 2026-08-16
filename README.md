# Disposable Privacy Workspace

A privacy-focused Android workspace for **standard, non-rooted Android devices**. It is not a virtual Android phone and cannot provide OS-level isolation from Android or other applications.

## Production V1 security boundary

The production V1 deliberately defines **supported private traffic** as the application's disposable WebView browser. This avoids claiming that a raw `VpnService` TUN interface is a Tor tunnel when a packet-forwarding bridge is not present.

### Tor mode

- Uses Guardian Project `tor-android` 0.4.9.11 and `jtorctl` 0.4.5.7.
- Waits for Tor to report an operational circuit and a non-zero SOCKS listener.
- Uses AndroidX WebKit `ProxyController` with a SOCKS5 proxy rule pointing at Tor's local SOCKS listener.
- Does not add a direct-network fallback.
- Removes the TorService private data directory when the disposable session is destroyed.
- Supports normal HTTPS browsing and `.onion` access through the Tor SOCKS proxy where the WebView/Tor combination supports it.

Guardian Project documents the native Android Tor service and the current 0.4.9.11 binary. citeturn20search0turn20search1

### Cloudflare mode

The disposable browser uses a local HTTP CONNECT proxy. The proxy resolves CONNECT hostnames through Cloudflare DNS-over-HTTPS and then relays the encrypted HTTPS stream to the resolved address. This is **DNS privacy for supported browser traffic**, not a general VPN and not an advertisement blocker.

### Kill-switch behavior

The browser proxy is configured with no direct fallback. If the proxy cannot be applied or its upstream connection fails, the browser does not silently switch to the normal network path.

AndroidX WebKit's `ProxyController` supports process-wide WebView proxy overrides and SOCKS/HTTP proxy rules. citeturn19search0turn19search4

## Why VpnService is not used for production V1

A `VpnService` interface captures packets but does not itself translate those packets into SOCKS/Tor traffic. Android's VPN API explicitly separates creation of the TUN interface from the VPN gateway/forwarding implementation. citeturn0search1turn0search4

The project therefore does not expose a fake "Tor VPN". A future full-device/per-app VPN mode should integrate and audit a native tun2socks implementation such as the approach used by Orbot with `hev-socks5-tunnel`. citeturn1search0turn1search10

## Ephemeral storage

All application-managed sandbox files are associated with a random session ID. Session keys use Android Keystore. On destruction the app stops network components, removes browser cookies, deletes the session directory, deletes the session key, removes TorService state, and clears session metadata.

This does not guarantee physical destruction of every flash-storage bit because Android storage controllers and flash wear-leveling are outside the application boundary.

## Permissions

The V1 permission set is intentionally minimal. The app requires Internet access for network features. It does not request contacts, SMS, microphone, camera, location, call logs, accessibility access, or a user account.

## Build

Requires JDK 17 and Android SDK 35. A Gradle installation or Gradle Wrapper is required.

```bash
gradle assembleDebug
gradle test
gradle lint
```

## Testing

Unit tests cover sandbox lifecycle and cleanup primitives. Real Tor, WebView proxy, DNS, network switching, crash recovery and `.onion` tests require an Android emulator or physical device with network access. They must be executed in CI/device testing before a public release.

## Production release gate

Do not label a build production-ready until:

- the Android debug build succeeds;
- unit and instrumentation tests pass;
- Tor bootstrap and SOCKS proxy are tested on supported Android versions;
- HTTPS and `.onion` browser traffic are verified through Tor;
- direct fallback is tested and rejected;
- Cloudflare DoH proxy resolution is tested during Wi-Fi/mobile transitions;
- session destruction is verified after browser and Tor use;
- static analysis and dependency scanning pass;
- the release APK is reproducibly built and signed outside this repository.
