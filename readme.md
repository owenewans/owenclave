<div align="center">

# owenclave

fork of exclave, an android proxy client with naiveproxy, olcrtc and dpi bypass.

<a href="https://count.owenewans.org/owenewans/owenclave?theme=moebooru-h&notitle"><img src="https://count.owenewans.org/owenewans/owenclave?theme=moebooru-h&notitle" alt="repository views"></a>

`kotlin` `proxy` `android`

</div>

## features

- group and subscription management, with flexible routing rules
- proxy chaining and SOCKS proxy chaining (`proxy -> proxy -> site`)
- NaiveProxy and OLCRTC support, including import from Clash/Mihomo YAML
- `owenclave://` deep links for adding subscriptions
- per-subscription Send HWID option alongside the global setting
- TWPS2 (zapret2) global DPI bypass
- unlock AI and EN services for Russia
- direct proxy mode
- Material 3 Expressive UI

protocols:

- Shadowsocks and Shadowsocks 2022, with SIP003 plugin support
- VMess and VLESS, with the usual sub-protocols
- Trojan, Hysteria 2, TUIC, Juicity, AnyTLS, Mieru, ShadowQuic
- Snell v4 and v6, TrustTunnel (no ICMP echo)
- NaiveProxy, as a standalone plugin
- OLCRTC
- WireGuard, TCP and UDP only
- SSH dynamic port forwarding
- HTTP CONNECT over HTTP/1.1, HTTP/1.1 with TLS, HTTP/2 and HTTP/3
- SOCKS4, SOCKS4a and SOCKS5

## install

Download the APK from
[releases](https://github.com/owenewans/owenclave/releases).

To build it instead, install JDK 21, Go 1.26 with gomobile, Android SDK
Platform and Build-Tools 37.0, Platform-Tools and NDK r29, or use the Nix
flake. Then:

```sh
git clone https://github.com/owenewans/owenclave --recurse-submodules
cd owenclave
./run lib core
./gradlew :app:assembleOssRelease
```

The APK lands in `app/build/outputs/apk/oss/release`. Signing reads
`local.properties`:

```properties
KEYSTORE_PASS=your_keystore_pass
ALIAS_NAME=your_alias_name
ALIAS_PASS=your_alias_pass
```

## usage

Add servers by importing a subscription URL, pasting a share link, or
scanning a QR code, then pick one and connect.

A `owenclave://` link opens the app on the Groups screen with the
**Add subscription from URL** dialog pre-filled, so the URL and the Send HWID
option can be reviewed before importing:

```text
owenclave://add-subscription?url=<encoded URL>&hwid=1
```

`hwid` is optional and accepts `1`/`true`/`yes`/`on` or `0`/`false`/`no`/`off`.

## configuration

Send HWID can be set per subscription in the add-subscription dialog, and
defaults to the global setting. The effective value is the global setting OR
the per-subscription one, so a subscription can opt in but cannot turn the
global setting off. It is not included in exported share links.

Clash and Mihomo YAML subscriptions can carry NaiveProxy and OLCRTC entries:

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

`proto` selects `https` or `quic`. These two types are read by owenclave's
parser; a standard Mihomo installation handles them by its own rules.
