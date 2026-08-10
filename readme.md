<div align="center">

# owenclave

fork of exclave with custom features, olcrtc support and dpi bypass.

<a href="https://count.owenewans.org/owenewans/owenclave?theme=moebooru-h&notitle"><img src="https://count.owenewans.org/owenewans/owenclave?theme=moebooru-h&notitle" alt="repository views"></a>

`kotlin` `java` `go` `android-sdk` `gradle` `nix` `gpl-3.0`

</div>

features:
- various proxy protocols support
- group and subscription management
- flexible routing rules
- proxy chaining and socks proxy chaining (proxy -> proxy -> site)
- olcrtc protocol support
- twps2 (zapret2) global dpi bypass
- unlock ai and en services for russia (global)
- direct proxy mode
- material 3 expressive ui

supported protocols:
- shadowsocks & shadowsocks 2022 (with sip003 plugin support)
- trojan
- hysteria 2
- anytls
- mieru
- naïveproxy (as a standalone plugin)
- tuic
- juicity
- vmess & vless (with various optional sub-protocols)
- wireguard (tcp and udp only)
- trusttunnel (no icmp echo support)
- snell v4 and snell v6
- shadowquic
- ssh proxy ("dynamic port forwarding")
- http connect tunnel (http/1.1, http/1.1 with tls, http/2 and http/3)
- socks4, socks4a and socks5
- olcrtc

requirements:
- jdk 21
- go 1.26 and gomobile
- android sdk platform 37.0, build-tools 37.0.0, platform-tools and ndk r29 (or nix flake)

quick start:

1.  git clone https://github.com/owenewans/owenclave --recurse-submodules
2.  install and configure requirements (or use nix shell)
3.  replace release.keystore with your own (generated with java keytool)
4.  create local.properties:

``` env
KEYSTORE_PASS=your_keystore_pass
ALIAS_NAME=your_alias_name
ALIAS_PASS=your_alias_pass
```

linux: 

```sh
./run lib core
./gradlew :app:assembleOssRelease
```

nix:

```sh
nix develop
./run lib core
./gradlew :app:assembleOssRelease
```

apk output directory: ./app/build/outputs/apk/oss/release

links:

  - upstream: exclavenetwork/exclave
  - olcrtc: openlibrecommunity/olcrtc
  - zapret2: bol-van/zapret2

