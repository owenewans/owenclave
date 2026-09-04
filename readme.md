<div align="center">

# owenclave

fork of [exclave](https://github.com/exclavenetwork/exclave) with custom features, **NaiveProxy**, **OLCRTC**, deep links and DPI bypass.

<a href="https://count.owenewans.org/owenewans/owenclave?theme=moebooru-h&notitle"><img src="https://count.owenewans.org/owenewans/owenclave?theme=moebooru-h&notitle" alt="repository views"></a>

`kotlin` `java` `go` `android-sdk` `gradle` `nix` `gpl-3.0`

</div>

## Features

* various proxy protocols support
* group and subscription management
* flexible routing rules
* proxy chaining and SOCKS proxy chaining (`proxy -> proxy -> site`)
* **NaiveProxy support**
* **OLCRTC protocol support**
* **`owenclave://` deep links**
* per-subscription **Send HWID** option
* TWPS2 (zapret2) global DPI bypass
* unlock AI and EN services for Russia (global)
* direct proxy mode
* Material 3 Expressive UI

## Supported protocols

* Shadowsocks & Shadowsocks 2022 (with SIP003 plugin support)
* Trojan
* Hysteria 2
* AnyTLS
* Mieru
* NaiveProxy (as a standalone plugin)
* TUIC
* Juicity
* VMess & VLESS (with various optional sub-protocols)
* WireGuard (TCP and UDP only)
* TrustTunnel (no ICMP echo support)
* Snell v4 and Snell v6
* ShadowQuic
* SSH proxy ("dynamic port forwarding")
* HTTP CONNECT tunnel (HTTP/1.1, HTTP/1.1 with TLS, HTTP/2 and HTTP/3)
* SOCKS4, SOCKS4a and SOCKS5
* OLCRTC

## Custom changes

This fork currently includes the following custom changes.

### Mihomo / Clash YAML parser

Added support for parsing **NaiveProxy** and **OLCRTC** entries from Mihomo/Clash YAML subscriptions.

The project already contains dedicated implementations for both protocols (`NaiveBean` and `OLCRTCBean`), including their respective configuration and URI formats. However, the Mihomo/Clash YAML parser previously did not recognize these protocol types when importing a YAML subscription.

The parser in `ClashYAMLParser.kt` processes each entry from the YAML `proxies` list based on its `type` field. Previously, unsupported types fell through to the default branch and were silently discarded. This meant that a subscription containing otherwise valid `naive` or `olcrtc` entries could be imported while those proxies were simply missing from the resulting configuration.

This fork adds dedicated parser branches for:

* `type: naive`
* `type: olcrtc`

The YAML fields are mapped directly to the existing protocol Bean structures and follow the naming conventions already used by the parser.

#### NaiveProxy

Example:

```yaml
proxies:
  - name: My Naive
    type: naive
    server: example.com
    port: 443
    proto: https
    username: user
    password: pass
    sni: example.com
    extra-headers: "X-Header: value"
    insecure-concurrency: 1
    no-post-quantum: false
```

Supported configuration includes the protocol, server and port, authentication credentials, SNI, extra headers, insecure concurrency and post-quantum settings.

Both `https` and `quic` protocol modes can be represented through the `proto` field.

#### OLCRTC

Example:

```yaml
proxies:
  - name: My OLCRTC
    type: olcrtc
    room-id: some-room-id
    auth-provider: jitsi
    transport: datachannel
    encryption-key: base64key
    dns-server: 8.8.8.8:53
    socks-host: 127.0.0.1
    socks-port: 8808
```

The parser maps the YAML configuration to the existing `OLCRTCBean` implementation, including room identification, authentication, transport, encryption and local SOCKS configuration.

These entries are intended for **owenclave's extended YAML parser**. They are not intended to add new protocol types to Mihomo itself. A standard Mihomo installation that does not implement these protocol types will continue to handle them according to its own configuration logic.

The main purpose of this change is to allow a single YAML subscription to contain NaiveProxy and OLCRTC entries that can be imported directly by owenclave instead of requiring separate custom URI imports.

#### Parser behavior

The change is limited to the import layer and reuses the existing protocol implementations:

```text
Mihomo / Clash YAML
        │
        ▼
   RawUpdater
        │
        ▼
ClashYAMLParser
        │
        ├── ss
        ├── vmess
        ├── vless
        ├── trojan
        ├── hysteria2
        ├── wireguard
        ├── ...
        ├── naive       ← added
        └── olcrtc      ← added
                │
                ▼
        Existing Bean classes
```

No new protocol implementation was introduced for these two protocols. The existing `NaiveBean` and `OLCRTCBean` implementations are reused, keeping YAML import behavior consistent with the rest of the application.

### `owenclave://` deep links

Added support for the `owenclave://` URL scheme.

Example:

```text
owenclave://add-subscription?url=<encoded URL>&hwid=1
```

The `hwid` parameter is optional and accepts:

```text
1 / true / yes / on
0 / false / no / off
```

Deep links do **not** immediately import a subscription.

Instead, opening an `owenclave://add-subscription` link:

1. launches or brings the application to the foreground
2. switches to the Groups screen
3. opens the **Add subscription from URL** dialog
4. pre-fills the URL from the deep link
5. optionally pre-selects the **Send HWID** setting

The user can review or edit the URL and change the HWID option before confirming the import.

The deep link handling is implemented through a small `PendingDeepLink` state bridge between `ComposeMainActivity` and `GroupScreen`. This allows the regular Android Activity intent callback to communicate with the Compose UI without performing the subscription import directly from the Activity.

### Per-subscription Send HWID

Added a per-subscription **Send HWID** option while preserving the existing global setting.

Previously, `sendHwid` was only available as a global `DataStore` setting shared by all subscriptions.

This fork adds:

* `sendHwid: Boolean` to `SubscriptionBean`
* Kryo serialization version bump from `9` to `10`
* per-subscription HWID override support
* updated subscription updaters

The effective behavior is:

```text
global Send HWID OR subscription Send HWID
```

The per-subscription setting therefore acts as an **additional opt-in** and does not override or disable the existing global setting.

Updated subscription update paths include:

* `RawUpdater`
* `SIP008Updater`
* `AgeUpdater`

The **Add subscription from URL** dialog includes a `Send HWID` switch using the existing `SwitchPreferenceItem` component.

The switch defaults to the current global `DataStore.sendHwid` value, allowing the global preference to remain the default while still permitting individual subscriptions to opt in.

The option is hidden when importing an `owenkey://` configuration because that format already contains a complete subscription Bean and the additional toggle has no effect.

The per-subscription HWID setting is intentionally **not** included in `serializeForShare` / owenkey exports. Configuration sharing is treated as a separate feature and is not modified by this change.

## Testing

The changes were tested by building the application against the latest upstream `dev` branch.

Tested:

* successful Android application build
* NaiveProxy entry parsing through the Mihomo/Clash YAML parser
* NaiveProxy YAML parsing without the built-in NaiveProxy plugin
* OLCRTC entry parsing through the Mihomo/Clash YAML parser
* `owenclave://` deep link handling
* subscription URL pre-filling from a deep link
* optional HWID parameter handling
* per-subscription Send HWID option
* existing subscription parsing behavior

NaiveProxy was specifically tested at the **YAML parsing/import level without the built-in NaiveProxy plugin**. The built-in NaiveProxy runtime/plugin itself was not included in this particular test build.

The changes were rebased onto the latest upstream `dev` before testing.

## Requirements

* JDK 21
* Go 1.26 and gomobile
* Android SDK Platform 37.0
* Android SDK Build-Tools 37.0.0
* Android SDK Platform-Tools
* Android NDK r29
* or Nix flake

## Quick start

1. Clone the repository with submodules:

```bash
git clone https://github.com/owenewans/owenclave --recurse-submodules
cd owenclave
```

2. Install and configure the required dependencies, or use the Nix shell.

3. Replace `release.keystore` with your own keystore generated using Java `keytool`.

4. Create `local.properties`:

```properties
KEYSTORE_PASS=your_keystore_pass
ALIAS_NAME=your_alias_name
ALIAS_PASS=your_alias_pass
```

### Linux

```bash
./run lib core
./gradlew :app:assembleOssRelease
```

### Nix

```bash
nix develop
./run lib core
./gradlew :app:assembleOssRelease
```

APK output:

```text
./app/build/outputs/apk/oss/release
```

## Links

* upstream: [exclavenetwork/exclave](https://github.com/exclavenetwork/exclave)
* OLCRTC: [openlibrecommunity/olcrtc](https://github.com/openlibrecommunity/olcrtc)
* zapret2: [bol-van/zapret2](https://github.com/bol-van/zapret2)