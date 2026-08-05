<div align="center">

```
                          __                    
 ___ _    _____ ___  ____/ /_____
/ _ \ |/|/ / -_) _ \/ __/ __/ __/
\___/__,__/\__/_//_/_/  \__/\__/ 
```

fork of exclave (best proxy client in the world) with custom features
<br>

</div>

stack       kotlin / java / go / android sdk / gradle / nix
license     gpl-3.0-or-later

features:
- various proxy protocols
- group and subscription
- routing
- proxy chain
- socks proxy chaining (proxy -> proxy -> site)
- olcrtc protocol support
- twps2 (zapret2) global dpi bypass
- unlock ai and en services for russia (global)
- direct proxy mode
- material 3 expressive ui

supported protocols:
- shadowsocks (with sip003 plugin support)
- shadowsocks 2022 (with sip003 plugin support)
- trojan
- hysteria 2
- anytls
- mieru
- naïveproxy (as a standalone plugin)
- tuic
- juicity
- vmess (with various optional sub-protocols)
- vless (with various optional sub-protocols)
- wireguard (tcp and udp only)
- trusttunnel (no icmp echo support)
- snell v4 and snell v6
- shadowquic
- ssh proxy ("dynamic port forwarding")
- http connect tunnel (http/1.1, http/1.1 with tls, http/2 and http/3)
- socks4, socks4a and socks5
- olcrtc

quick start:
```sh
git clone https://github.com/owenewans/owenclave --recurse-submodules
cd owenclave
# install jdk 21, go 1.26, go mobile, android sdk
./run lib core
./gradlew :app:assembleOssRelease
```

nix users:
```sh
git clone https://github.com/owenewans/owenclave --recurse-submodules
cd owenclave
nix develop
./run lib core
./gradlew :app:assembleOssRelease
```

build from source:
- install and configure jdk 21, go 1.26 and go mobile
- android sdk is provided via nix flake or install manually:
  - android sdk platform 37.0, android sdk build-tools 37.0.0, android sdk platform-tools and android ndk r29
- replace `release.keystore` with your own (generated with java `keytool`)
- create `local.properties` with:
```
KEYSTORE_PASS=your_keystore_pass
ALIAS_NAME=your_alias_name
ALIAS_PASS=your_alias_pass
```
- build libowenclavecore: `./run lib core` or `./library/core/build.sh`
- download assets: `./gradlew :app:downloadAssets`
- build owenclave: `./gradlew :app:assembleOssRelease`
- apk files are located in `./app/build/outputs/apk/oss/release`

links:
upstream    [owenewans/owenclave](https://github.com/owenewans/owenclave)
olcrtc      [openlibrecommunity/olcrtc](https://github.com/openlibrecommunity/olcrtc)
zapret2     [bol-van/zapret2](https://github.com/bol-van/zapret2)

---
author      [owenewans.org](https://owenewans.org)
email       [owenewans@owenewans.org](mailto:owenewans@owenewans.org)
tg          [t.me/owenewans](https://t.me/owenewans)
