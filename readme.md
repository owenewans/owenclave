<div align="center">

# owenclave

android proxy client based on exclave, with bundled naiveproxy and a Compose UI.

<a href="https://count.owenewans.org/owenewans/owenclave?theme=moebooru-h&notitle"><img src="https://count.owenewans.org/owenewans/owenclave?theme=moebooru-h&notitle" alt="repository views"></a>

`kotlin` `go` `android`

</div>

## features

- subscriptions, groups, routing rules and proxy chains
- Shadowsocks and Shadowsocks 2022 with SIP003 plugins, plus ShadowsocksR
- VMess, VLESS and Trojan, including TLS, REALITY and transport settings
- Hysteria 2, TUIC, Juicity, AnyTLS, Mieru and ShadowQUIC
- Snell v4/v6, TrustTunnel and MASQUE
- bundled NaiveProxy with HTTPS and QUIC, including Clash/Mihomo YAML import
- WireGuard (TCP and UDP), SSH, HTTP/HTTPS CONNECT, HTTP/3 and SOCKS4/4a/5
- desktop client (Linux, macOS, Windows) and Dockerized Mihomo-compatible server with Web UI (`/ui/`)
- local decryption of supported `happ://` subscription links
- `owenclave://` subscription links and optional per-subscription HWID
- TLS ClientHello fragmentation, direct proxy mode and Russia-specific routing presets
- Material 3 Expressive UI with editable profile field presets

## install

Download an APK for your device from [releases](https://github.com/owenewans/owenclave/releases).
Main builds require Android 7.0+; legacy builds support Android 5.0+.
NaiveProxy requires Android 7.0+.

To build, install JDK 25, Go 1.27.3+, gomobile and gobind, Android SDK
Platform 37.2, Build-Tools 37.0.0 and NDK r29. Then:

```sh
git clone https://github.com/owenewans/owenclave
cd owenclave
export ANDROID_HOME=/path/to/android-sdk
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/29.0.14206865"
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
./run lib core
./bin/lib/naive/download.sh
./gradlew :app:downloadAssets
./gradlew :app:assembleOssRelease
```

Signing uses `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and
`KEY_PASSWORD`. Use the same keystore as your installed build to keep
updates installable. APKs are written to `app/build/outputs/apk/oss/release/`.

The Nix environment is available through `nix develop`, then `owenclave-fhs`.

## usage

Import a subscription URL, paste a share link or scan a QR code. Select a
profile and connect.

See [configuration](docs/configuration.md) for subscription deep links,
HWID and NaiveProxy YAML fields, [desktop/README.md](desktop/README.md) for
desktop builds, and [docker/README.md](docker/README.md) for the Mihomo-style
Docker server and Web UI.

## license

[GPL-3.0](LICENSE)
