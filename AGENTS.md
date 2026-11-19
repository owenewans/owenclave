# AGENTS.md

## Build

This is an Android proxy client app (fork of exclave/sagernet).

### Prerequisites
- JDK 25
- Go 1.27.3+ and Go Mobile
- Android SDK Platform 37.2, Build-Tools 37.0.0, Platform-Tools, NDK r29
- Or use Nix: `nix develop`

### Build steps
1. Build libowenclavecore: `./run lib core` or `./library/core/build.sh`
2. Download assets: `./gradlew :app:downloadAssets`
3. Build APK: `./gradlew :app:assembleOssRelease`
4. APK output: `./app/build/outputs/apk/oss/release/`

### Nix
```sh
nix develop
owenclave-fhs
./run lib core
./gradlew :app:assembleOssRelease
```

### Lint/Typecheck
- Lint: `./gradlew :app:lintOssRelease`
- No dedicated typecheck command; use `./gradlew :app:compileOssReleaseKotlin`

### Testing
- `./gradlew test`
- Desktop smoke test: `./gradlew :desktop:app:run --args="--selftest"`
- Desktop naive plugin test: `./gradlew :desktop:app:run --args="--selftest-naive"`
- Desktop protocol tests (real core as local server, end to end):
  `./gradlew :desktop:app:run --args="--selftest-protocols"` covers
  vless, vmess, trojan, anytls, shadowsocks, socks, http and https;
  `--selftest-vless` and `--selftest-protocol <name>` run a single one.
- Desktop settings parity: `./gradlew :desktop:app:run --args="--print-settings"`

### Desktop & Docker Server
- `./run desktop build` - core + naive + tun2socks + portable/installer for the current OS
- `./run desktop build all` - cross platform binaries in `desktop/core/dist`,
  `desktop/naive/dist` and `desktop/tun2socks/dist`
- `./gradlew :desktop:app:prepareServerDist` + `docker build -t owenclave:latest .` -
  Mihomo-compatible headless server + Web UI (`/ui/`)
- `:desktop:shared` compiles the Android `fmt`/`group`/`ktx` sources read-only; keep
  both Android and desktop compiling when touching shared model code.
- Docs: `desktop/README.md`, `docker/README.md`

### Key files
- `Constants.kt` - connection test URL, key constants
- `DataStore.kt` - settings defaults and storage
- `global_preferences.xml` - settings UI layout
- `ProxyEntity.kt` - protocol types and entity management
- `ConfigBuilder.kt` - V2Ray config generation
- `V2RayInstance.kt` - external plugin management (naive, shadowquic)
- `themes.xml` / `styles.xml` - UI theme and dialog styling
- `Theme.kt` - theme application logic

### Package
- Application ID: `org.owenewans.owenclave`
- Namespace: `io.nekohasekai.sagernet`
- Go library: `libexclavecore` (built from `github.com/exclavenetwork/libexclavecore`)
