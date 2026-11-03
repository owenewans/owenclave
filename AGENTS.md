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
