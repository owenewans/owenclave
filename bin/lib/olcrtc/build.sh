#!/usr/bin/env bash
# Build olcrtc CLI for all Android ABIs as libolcrtc.so
# Must run inside owenclave-fhs (nix develop -c owenclave-fhs -c "./bin/lib/olcrtc/build.sh")
# Requires CGO_ENABLED=1 for ALL ABIs: the Android netlink-free getifaddrs path
# (internal/protect/pionnet_android.go, //go:build android && cgo) needs cgo.
# Without cgo the nocgo stub returns ErrInterfacesUnavailable and ICE fails.
set -euo pipefail

OLCRTC_SRC="${OLCRTC_SRC:-/tmp/opencode/olcrtc}"
OUT_ROOT="${OUT_ROOT:-$(cd "$(dirname "$0")/../../.." && pwd)/app/src/main/jniLibs}"
TC="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin"

echo "olcrtc src: $OLCRTC_SRC"
echo "jniLibs out: $OUT_ROOT"
echo "toolchain: $TC"

build() {
  local abi="$1" cc="$2" goarch="$3" goarm="${4:-}"
  local out="$OUT_ROOT/$abi/libolcrtc.so"
  echo ">>> building $abi ($goarch${goarm:+ arm$goarm}) with $cc"
  mkdir -p "$OUT_ROOT/$abi"
  ( cd "$OLCRTC_SRC" && \
    export CGO_ENABLED=1 CC="$TC/$cc" GOOS=android GOARCH="$goarch" && \
    if [ -n "$goarm" ]; then export GOARM="$goarm"; fi && \
    go build -trimpath \
      -ldflags "-s -w -checklinkname=0" \
      -o "$out" ./cmd/olcrtc )
  ls -la "$out"
}

build arm64-v8a   aarch64-linux-android24-clang     arm64
build armeabi-v7a armv7a-linux-androideabi24-clang  arm   7
build x86_64      x86_64-linux-android24-clang      amd64
build x86         i686-linux-android24-clang        386

echo "=== all olcrtc ABIs built ==="
