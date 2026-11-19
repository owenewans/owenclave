# Owenclave desktop (macOS / Windows / Linux)

Compose Multiplatform client that reuses the Android app's protocol layer and runs
the same Go proxy engine as a child process.

```
┌────────────────────────────── Owenclave desktop (JVM, Compose) ─────────────────────────────┐
│  UI (Compose Multiplatform) / Headless Mihomo-compatible Server (--daemon)                  │
│  ProfileStore  ──  shared beans / parsers  (app/src/main/java/.../fmt, .../group)            │
│  DesktopConfigBuilder ──► V2Ray-format JSON ──► CoreRunner ──► owenclave-core (child proc)   │
│                                                     │                                        │
│                                                     └──► naive (child proc, local SOCKS)     │
└─────────────────────────────────────────────────────────────────────────────────────────────┘
```

* `owenclave-core` is the `exclave-core` CLI, i.e. exactly the engine the Android
  app links through gomobile (`libexclavecore.aar`). It consumes the same
  V2Ray-format JSON that `fmt/ConfigBuilder.kt` produces for Android; the desktop
  builder is now a thin adapter over that very generator, so every protocol the
  Android app supports is buildable on desktop.
* `naive` is the upstream NaiveProxy binary. Like on Android it is an external
  plugin: it listens on a local SOCKS port and the core dials into it. ShadowQUIC
  is handled natively by the desktop core, so no plugin process is started for it.
* Profiles, share links, Clash YAML, V2Ray JSON and sing-box JSON are parsed by
  the **shared Android code** (`:desktop:shared` compiles
  `app/src/main/java/io/nekohasekai/sagernet/{fmt,group,ktx}` directly), so
  import behaviour is identical on both platforms.

## Modules

| Module | Purpose |
| --- | --- |
| `:desktop:shared` | JVM view of the Android protocol model + desktop shims (`desktop/shared/src/main/kotlin/desktopshim`) |
| `:desktop:app` | Compose Multiplatform application, Mihomo-compatible daemon + Web UI, core/plugin supervision, packaging |
| `desktop/core/dist/<os>-<arch>` | cross compiled `owenclave-core` + `libgojni` binaries |
| `desktop/naive/dist/<os>-<arch>` | downloaded NaiveProxy binaries |
| `desktop/tun2socks/dist/<os>-<arch>` | downloaded `tun2socks` binaries |

The Android build is untouched: the shims only exist inside the desktop modules,
and the shared sources are consumed read-only through Gradle `srcDirs`.

## Prerequisites

* JDK 21+
* Go 1.27.3+ (to build the core)
* `python3` and `curl` (only to download the NaiveProxy and tun2socks binaries)

## Build

```sh
# whole client for the current platform (core + plugins + installer)
./run desktop build

# just the cross platform binaries for every target
./run desktop build all
```

Individual steps:

```sh
TARGETS="linux/amd64"   ./run desktop core build      # or: all
TARGETS="darwin/arm64"  ./run desktop naive download  # or: all
TARGETS="linux/amd64"   ./run desktop tun2socks download # or: all
./gradlew :desktop:app:packageDistributionForCurrentOS
```

Desktop artifacts are versioned `1.a.b` for an Android app version `0.a.b`:
`jpackage` refuses an app-version whose first component is zero on macOS
("The first number in an app-version cannot be zero or negative"), and all three
desktop platforms should carry the same version. `--version` and the UI still show
the application version from `version.properties`.

Artifacts land in `desktop/app/build/compose/binaries/main/`:

| Artifact | Path | Notes |
| --- | --- | --- |
| Application image | `app/Owenclave/` | unpack and run, produced by `createDistributable` |
| **Portable archive** | `portable/owenclave-desktop-<version>-<os>-<arch>-portable.zip` | `:desktop:app:packagePortable`, no installation needed |
| Installer | `deb/`, `dmg/`, `msi/` | `:desktop:app:packageDistributionForCurrentOS` |

```sh
# portable only
./gradlew :desktop:app:packagePortable

# installer for the host OS (needs dpkg/dpkg-deb on Linux, WiX on Windows)
./gradlew :desktop:app:packageDistributionForCurrentOS
```

### Portable build

Unpack the archive and start the launcher:

```text
Windows   Owenclave\Owenclave.exe
Linux     Owenclave/bin/Owenclave
macOS     Owenclave.app/Contents/MacOS/Owenclave     (unzip keeps the bundle)
```

The archive contains a `data` directory next to the launcher. As soon as it
exists, the client keeps profiles, settings, the generated core config and the
extracted core/plugin binaries inside the unpacked folder instead of
`%APPDATA%` / `~/Library/Application Support` / `~/.config`, so the whole thing is
self contained and can live on a USB stick. Remove `data/` to fall back to the
per user location.

`jpackage` can only build for the host OS, which is why
`.github/workflows/desktop.yml` runs the build on `ubuntu-latest`, `macos-26-intel`
(x64), `xcode-27` / `macos-26` (arm64) and `windows-latest`, and publishes both the
portable archives and the installers.

## Run from the sources

```sh
./gradlew :desktop:app:run
```

`run` resolves the binaries straight from `desktop/core/dist`,
`desktop/naive/dist` and `desktop/tun2socks/dist` for the current host.

## Verification

```sh
# what is bundled / resolvable
./gradlew :desktop:app:run --args="--version"

# parse an input and print the generated core config
./gradlew :desktop:app:run --args="--print-config /path/to/subscription.txt"

# end to end smoke test, no external server required:
# starts the core as a local Shadowsocks server, connects through the client
# and fetches an HTTP page over the local SOCKS inbound
./gradlew :desktop:app:run --args="--selftest"

# same, but from the packaged application image / portable archive
desktop/app/build/compose/binaries/main/app/Owenclave/bin/Owenclave --selftest

# NaiveProxy plugin integration (config accepted by the real naive binary)
./gradlew :desktop:app:run --args="--selftest-naive"

# every core-native protocol the core can also serve, end to end:
# vless, vmess, trojan, anytls, shadowsocks, socks, http, https.
# `--selftest-vless` is a shortcut for one of them, `--selftest-protocol <name>`
# for any single protocol.
./gradlew :desktop:app:run --args="--selftest-protocols"

# headless Mihomo-compatible server + Web UI (`/ui/`)
./gradlew :desktop:app:run --args="--daemon --config docker/config.example.yaml"

# unit tests of the importers, the rule builder and the config builder; the
# config test also feeds every generated protocol config to `owenclave-core test`
./gradlew :desktop:app:test

# open a specific tab right away, handy when checking that every screen renders
./gradlew :desktop:app:run -Psection=RULES
```

The self test prints `[selftest] PASS` and exits with code 0 on success, which
makes it usable on Linux and Windows (including under Wine) in CI.

`:desktop:app:test` also covers the host state changes without touching the
developer machine: `SystemProxyTest` drives the GNOME/KDE/macOS/Windows proxy layer
and `PerAppRoutingTest` the Linux cgroup/nftables rules, both through a fake
`CommandRunner` (set/restore symmetry, idempotence, stale-backup restore, hand-edit
protection and every failure path).

The one test that *does* touch the host is opt-in, because it sets the real system
proxy, fetches through the endpoint the OS proxy setting points at with `curl` and
checks that the previous values come back byte for byte:

```sh
./gradlew :desktop:app:test --tests '*SystemProxyE2eTest*' \
  -Dowenclave.e2e.systemProxy=true -i
```

## UI

The window is a left navigation rail with the screens modelled after the Android
app:

| Tab | What it does |
| --- | --- |
| **Profiles** | imported profiles (with the subscription they came from), one click connect, delete; paste/share-link/raw-config import on the right |
| **Subscriptions** | subscription URLs with a name and a per-subscription HWID switch, "Update" / "Update all", last update time and the last error |
| **Rules** | routing rules (`Proxy` / `Direct` / `Block`) with domains (including the `domain:` / `full:` / `keyword:` / `regexp:` prefixes), IP CIDR, port, network and sniffed protocol; rules apply on the next connect |
| **Settings** | the **entire Android global settings screen**, generated from `app/src/main/res/xml/global_preferences.xml` (seven categories, 86 rows; see "Settings" below), plus a "Desktop only" section for the settings Android has no row for (TUN interface name, subscription fetching, HWID identity, runtime paths) |
| **Log** | everything the core and the plugins print, with copy and clear |

Subscriptions are refreshed through the connected profile when one is running and
`Settings → Fetch through the connected profile` is on, which also gets around
local filtering that would break a plain HTTPS request.

HWID reporting follows the Android implementation: the same `x-hwid`,
`x-device-os`, `x-ver-os` and `x-device-model` headers, sent when the global
switch or the per-subscription switch is on.

### Differences compared to Android

* the default desktop window runs the shared Android Compose screens (`ComposeMainActivity` and `UniversalProfileSettingsScreen`) directly with all seven bottom navigation tabs (`Configuration`, `Groups`, `Settings`, `Route`, `Logcat`, `Traffic`, `Tools`) visible side-by-side on desktop widths; pass `--legacy-ui` for the compact five-tab desktop layout;
* `owenclave://`, `owenkey://`, `happ://` and proxy share-link URIs (`vless://`, `vmess://`, `trojan://`, `ss://`, `hysteria2://`, `naive+https://`, ...) are registered with the OS on Linux (`xdg-mime` / `.desktop`), Windows (`HKCU\Software\Classes`) and macOS (`CFBundleURLTypes` + `OpenURIHandler`) and forwarded over a loopback single-instance socket to an already-open Owenclave window;
* rules have no Android package-UID or Wi-Fi SSID matching; Linux per-app routing is a
  separate cgroup v2 include list (see [Per-app routing](#per-app-routing-linux));
* camera QR scanning is Android-only (use clipboard/file/share-link import on desktop).

## Transparent mode (TUN)

The core CLI cannot create a TUN device in this fork: on Android the app creates one
through `VpnService` and hands the fd to the gomobile library. Desktop transparent
mode is therefore assembled from two pieces:

```
system traffic -> TUN device -> tun2socks -> SOCKS 127.0.0.1:<port> -> core -> upstream
```

* `tun2socks` (v2.7.0, downloaded by `./run desktop tun2socks download`) owns the
  device and pumps it into the core's local SOCKS inbound;
* the core's outbound is pinned to the physical interface with
  `streamSettings.sockopt.bindToDevice`, which the core implements on Linux
  (`SO_BINDTODEVICE`), macOS (`IP_BOUND_IF`) and Windows (`IP_UNICAST_IF`). Without
  that pin the upstream connection would follow the tunnel's default route and loop;
* addresses and routes follow the upstream tun2socks recipes: split defaults
  (`0.0.0.0/1` + `128.0.0.0/1`) on Linux, the documented route list on macOS,
  `netsh` address/DNS/route on Windows. Everything is removed again on disconnect.

Enable it with **Settings → Service mode = VPN** (the Android VpnService row maps to
the desktop TUN device; "Proxy only" is the default). The IPv6 address and routes are
controlled by the Android **IPv6 route** row. It applies on the next connect.

### Per-app routing (Linux)

The core has **no process based route rules** (`process_name`/`process_path` is
rejected with "this rule has no effective fields"), and the Android app expresses
per-app proxying through `VpnService` package UIDs, which do not exist on desktop.
On Linux the operating system can do it, so the desktop client implements the include
list instead of faking it:

```
listed processes -> cgroup v2 slice -> nftables mark -> policy route -> TUN device
unlisted processes -> the normal routing table
```

`PerAppRouting.kt` (one file, every command through the injectable `CommandRunner`):

1. creates `/sys/fs/cgroup/owenclave.slice` and moves the matching PIDs into it;
2. marks packets whose socket belongs to that cgroup
   (`nft ... meta cgroup <slice inode> meta mark set 0x1`; when `nft` is missing the
   `iptables -m cgroup --path` fallback is used);
3. adds `ip rule add fwmark 0x1 lookup 1188` plus a default route in table 1188
   pointing at the TUN device.

The global split default routes are **not** installed in this mode (otherwise every
process would be captured), so the list is meaningful. Everything needs root and is
removed on disconnect. A process that is not running yet has to be started and the
connection re-done; the log says how many processes matched.

Turn it on with **Settings → Proxy apps** (the Android row is real on Linux) and edit
the process list under **Desktop only → Per-app routing**, one process name or
absolute path per line.

Failure paths are explicit: not root, no cgroup v2, neither `nft` nor `iptables`
with the cgroup match, or a failing command each raise a message naming the cause.

### Privileges

TUN / VPN mode elevates privileges on demand when connecting:

| OS | How |
| --- | --- |
| Linux | Polkit (`pkexec`) / graphical askpass via `owenclave-tun-helper` (or run as root / `CAP_NET_ADMIN`) |
| macOS | macOS administrator prompt via `osascript` (or run with `sudo`) |
| Windows | Windows UAC prompt (`RunAs` Administrator) with bundled `tun2socks.exe` + `wintun.dll` |

Windows additionally bundles `wintun.dll` next to `tun2socks.exe` (downloaded and verified from `wintun.net` by `./run desktop tun2socks download`).

### Verification

```sh
# authoritative end-to-end VPN / TUN self-test (routes traffic through local proxy via real TUN/Wintun)
sudo ./desktop/app/build/compose/binaries/main/app/Owenclave/bin/Owenclave --selftest-vpn-e2e

# public-network TUN self-test
sudo ./desktop/app/build/compose/binaries/main/app/Owenclave/bin/Owenclave --selftest-tun
```

CI runs `--selftest-vpn-e2e` as a blocking verification gate on Linux, macOS and Windows before any desktop artifact is uploaded.

## System proxy mode

The third Service mode ("System proxy", a desktop-only choice on top of the Android
VPN / Proxy only list) points the operating system proxy at the local HTTP port on
connect and reliably restores the previous state:

| OS | Backend | What is set |
| --- | --- | --- |
| Linux | GNOME (`gsettings`) | `org.gnome.system.proxy` mode = manual, `.http` and `.socks` host/port, `ignore-hosts` when a bypass list is configured |
| Linux | KDE (`kwriteconfig5`/`kwriteconfig6`) | `kioslaverc` `[Proxy Settings]` `ProxyType`, `httpProxy`, `httpsProxy`, `socksProxy`, `NoProxyFor` |
| macOS | `networksetup` | `-setwebproxy` / `-setsecurewebproxy` / `-setsocksfirewallproxy` plus the matching `-set*proxystate` on the active service (resolved from `networkserviceorder`, primary interface first), bypass domains when configured |
| Windows | `reg.exe` + WinINet | `HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings` `ProxyEnable`/`ProxyServer`/`ProxyOverride`, then `InternetSetOption` (`INTERNET_OPTION_SETTINGS_CHANGED` 39 and `INTERNET_OPTION_REFRESH` 37) through an inline PowerShell `Add-Type` P/Invoke |

GNOME is preferred over KDE; if neither is available the client says so explicitly
instead of pretending (the connect fails with that reason). The whole layer is
`SystemProxy.kt`; every command goes through the injectable `CommandRunner`.

**Restore semantics.** Before touching anything the previous values are written to
`system-proxy-backup.json` in the data directory (GNOME/KDE/macOS/Windows all use the
same JSON). On disconnect, on exit (JVM shutdown hook) and on the next start when the
file is still there (the process crashed) the backup is consumed: a field is put back
only when the OS still holds exactly the value this client set - a value the user
changed by hand while connected is left alone and reported. A restore deletes the
backup, so it is idempotent; if a restore command fails, the backup is kept and the
next start retries. Applying twice is safe: when the OS still holds our values, the
already recorded original backup is kept instead of being overwritten. A backup
written on another OS is discarded, not applied.

## Arch Linux

The client is packaged as `owenclave-desktop` for `/opt/owenclave` (the jpackage
application image, so no system JRE is needed) with a launcher in `/usr/bin`, a
desktop entry and an icon.

One command adds the pacman repository and installs the client:

```sh
sudo bash -c "$(curl -fsSL https://raw.githubusercontent.com/owenewans/owenclave/dev/packaging/arch/setup-repo.sh)"
```

The repository lives in the moving `desktop-repo` GitHub release, so the same URL
keeps working and `pacman -Syu` picks up new builds. The packages are not GPG
signed, which the installer reflects with `SigLevel = Optional TrustAll` (the files
come over HTTPS from that single release URL). Remove the repository entry again
with `... setup-repo.sh --uninstall`.

Building the package locally (needs `base-devel`):

```sh
./run desktop arch package    # writes desktop/arch/dist/*.pkg.tar.zst and owenclave.db
```

## Settings

The settings screen is not written twice: it is generated from the Android app's own
preference definition. The `copyAndroidPreferences` Gradle task copies
`app/src/main/res/xml/global_preferences.xml` plus every value resource it resolves
(`values/strings.xml`, `values/arrays.xml`, `values/locale.xml` and both copies of
`values{,-v29}/preference.xml`, with the API 29 override applied like the platform
resource merger) into the application resources, and `SettingsCatalog.kt` parses
them at runtime, so the desktop shows the same seven categories in the same order,
with the same titles, summaries, defaults and widget kinds as Android - 86
preferences today. Entries the running platform cannot honour (packet capture,
WakeLock, the quick settings tile, Tasker, the per-app rows on Windows/macOS, ...)
stay in their place but are disabled and state why - 18 of the 86 on Linux, 19 on
Windows and macOS today.

Mobile-only preferences that *do* have a desktop meaning are adapted instead of
disabled, through the binding table:

| Android row | Desktop behaviour |
| --- | --- |
| Service mode (`serviceMode`) | the VpnService replacement, with a desktop-only third choice: **VPN** creates the desktop TUN device (tun2socks), **System proxy** points the OS proxy at the local HTTP port (see [System proxy mode](#system-proxy-mode)), **Proxy only** keeps the local SOCKS/HTTP inbounds. The generated core config is always built in proxy mode, so no `--android_vpn` arguments are emitted |
| IPv6 route (`enableVPNInterfaceIPv6Address`) | IPv6 address (`fdfe:dcba:9876::1`) plus split default routes on the desktop TUN device |
| Per-app proxy (`proxyApps`) | real on Linux: only the processes listed under Desktop only → Per-app routing go through the TUN device (see [Per-app routing](#per-app-routing-linux)). Disabled on Windows/macOS with the WFP-callout / NetworkExtension reason |
| Auto connect (`isAutoConnect`) | connect the selected profile when the client starts |
| Night mode (`nightTheme`) | light / dark / follow the system theme |
| Theme colour (`appTheme`) | the Compose accent colour (palette) |
| Reset HWID (`resetHwid`) | an active row that regenerates the device identity |
| Security tips (`profileSecurityAdvisory`) | insecure-profile warning in the profile list |
| Always show address (`alwaysShowAddress`) | show or hide the server address in the profile list |
| Local DNS inbound + port (`requireDnsInbound`, `portLocalDns`) | the core serves DNS on `127.0.0.1:<port>` (only the Android UDS inbound is dropped) |
| TUN MTU (`mtu`) | the desktop TUN device MTU |
| Route mode (`routeMode`) | desktop rules / proxy all / direct only |
| SOCKS proxy chaining, fragment, sniffing, DNS, inbounds, ... | pushed straight into the shared `ConfigBuilder` |

Still not available on desktop, and why: `tunImplementation` (tun2socks has a single
userspace stack), `enablePcap`/`discardICMP` (no pcap/ICMP policy in the core or
tun2socks), `allowAppsBypassVpn` (the desktop supports an include list on Linux, not
a bypass list), `proxyApps` on Windows/macOS (needs a WFP callout driver /
NetworkExtension), `appendHttpProxy`/`httpProxyException` (Android sets the VPN HTTP
proxy; use Service mode = System proxy), `requireTransproxy`/`transproxyPort` (use
Service mode = VPN), `meteredNetwork`, `acquireWakeLock`,
`queryAllPackagesAlternativeMethod`, the notification/statistics rows and
`appLanguage`.

The platform matrix for the two features that are not portable is:

| Feature | Linux | Windows | macOS |
| --- | --- | --- | --- |
| System proxy (Service mode = System proxy) | GNOME `gsettings` or KDE `kwriteconfig` | `reg.exe` + WinINet refresh | `networksetup` on the active service |
| Per-app include list | cgroup v2 + nftables (or iptables) marks + policy routing | **not implemented** (WFP callout driver out of scope; use the rule list) | **not implemented** (NetworkExtension out of scope; use the rule list) |
| Per-app bypass (`allowAppsBypassVpn`) | **not implemented** (include list only) | **not implemented** | **not implemented** |

Values are persisted next to the profiles and pushed into the shared `DataStore`
before every config build through the binding table in `SettingsBindings.kt`, so a
setting the user changes changes the config the core receives. A few desktop
specific defaults are listed in `DesktopSettings.DESKTOP_DEFAULTS` (`requireHttp`
on, `profileTrafficStatistics` off, `alwaysShowAddress` on) to keep the older
desktop behaviour, everything else follows the Android defaults - except Service
mode, where the desktop default is "Proxy only" because a TUN device needs root.

The page also has a filter box (match key, title or summary), masks password
fields, renders list values (hosts, STUN servers, ...) as multi-line fields and
treats ports/sizes as integers even though the Android XML declares no
`inputType`.

* `--print-settings` prints the whole catalog (section, key, title, summary, widget,
  default, current value, enabled/disabled and reason) without starting the UI, which
  makes it easy to diff against the Android XML.
* `SettingsParityTest` parses `global_preferences.xml` on its own and fails when a
  preference is added on Android without reaching the desktop;
  `SettingsConfigEffectTest` proves that changing a setting changes the generated
  config.

## Supported protocols

Desktop builds configs with the **shared Android generator**
(`app/src/main/java/io/nekohasekai/sagernet/fmt/ConfigBuilder.kt`, compiled
read-only into `:desktop:shared`), so the protocol coverage matches the Android
app: Shadowsocks (incl. 2022 methods and SIP003 plugins), ShadowsocksR, VMess,
VLESS, Trojan, Hysteria2, TUIC, AnyTLS, SSH, Snell, ShadowQUIC, Mieru, Juicity,
TrustTunnel, HTTP/3, MASQUE, WireGuard, SOCKS and HTTP/HTTPS.

NaiveProxy is the protocol the core does not implement directly: it runs as an
external plugin process behind a local SOCKS listener, exactly like on Android.
ShadowQUIC is the opposite case and is handled natively by the desktop core, so
unlike Android it needs no plugin binary.

Transports: tcp, ws, grpc, h2/http, httpupgrade, quic, kcp, splithttp/xhttp with
TLS, uTLS, REALITY, ALPN, certificate pinning, ECH and mux/smux.

A raw V2Ray JSON config can always be added as a custom profile and is passed to
the core unchanged (only the TUN interface binding is injected in transparent
mode).

Android-only concepts have no desktop equivalent and are intentionally absent:
`VpnService` (desktop builds the same idea from `tun2socks` plus the core's SOCKS
inbound, see [Transparent mode](#transparent-mode-tun)), package-UID based per-app
routing on Windows/macOS (Linux uses its own cgroup list, see
[Per-app routing](#per-app-routing-linux)), WorkManager
based subscription refresh and the AIDL plugin discovery.

## Layout

```
desktop/
├── app/             Compose application, Mihomo-compatible daemon + Web UI, self tests
├── shared/          JVM module: shared Android sources + desktop shims
├── core/dist/       cross compiled owenclave-core + libgojni
├── naive/dist/      downloaded NaiveProxy binaries
└── tun2socks/dist/  downloaded tun2socks binaries
```
