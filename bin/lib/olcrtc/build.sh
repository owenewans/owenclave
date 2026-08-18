#!/usr/bin/env bash
# build olcrtc cli for all android abis as libolcrtc.so
# cgo enables android interface discovery required by ice
set -euo pipefail

OLCRTC_COMMIT="${OLCRTC_COMMIT:-48cae636f88e16863c99d4147bbc327a856cdf00}"
OLCRTC_SRC="${OLCRTC_SRC:-${TMPDIR:-/tmp}/owenclave-olcrtc}"
OUT_ROOT="${OUT_ROOT:-$(cd "$(dirname "$0")/../../.." && pwd)/app/src/main/jniLibs}"
TC="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin"

if [ ! -d "$OLCRTC_SRC/.git" ]; then
  rm -rf "$OLCRTC_SRC"
  git clone https://github.com/openlibrecommunity/olcrtc.git "$OLCRTC_SRC"
fi
git -C "$OLCRTC_SRC" fetch --depth 1 origin "$OLCRTC_COMMIT"
git -C "$OLCRTC_SRC" checkout --detach "$OLCRTC_COMMIT"

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
