#!/usr/bin/env bash
set -euo pipefail

VERSION="154.0.8037.49-5"
BASE_URL="https://github.com/klzgrad/naiveproxy/releases/download/v$VERSION"
OUT_ROOT="${OUT_ROOT:-$(cd "$(dirname "$0")/../../.." && pwd)/app/src/main/jniLibs}"
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT

download() {
  local abi="$1" apk_sha256="$2" lib_sha256="$3"
  local out="$OUT_ROOT/$abi/libnaive.so"
  local apk="$TEMP_DIR/$abi.apk"
  mkdir -p "$(dirname "$out")"
  if [ -s "$out" ] && echo "$lib_sha256  $out" | sha256sum -c --status; then
    return
  fi
  curl -fL --retry 3 "$BASE_URL/naiveproxy-plugin-v$VERSION-$abi.apk" -o "$apk"
  echo "$apk_sha256  $apk" | sha256sum -c --status
  unzip -p "$apk" "lib/$abi/libnaive.so" > "$TEMP_DIR/libnaive.so"
  echo "$lib_sha256  $TEMP_DIR/libnaive.so" | sha256sum -c --status
  chmod +x "$TEMP_DIR/libnaive.so"
  mv "$TEMP_DIR/libnaive.so" "$out"
}

download arm64-v8a 3a532e6894d87c1fca5c527d3598892627829c76cb5b75ed831555f384841bd0 23f950b4a813a63456070a7780001658da21e059b352f5a441ae8e66e5c97a74
download armeabi-v7a 9acbf08afea2d35aad492d5312444b6aecd5fafa41f29d794d4821549f330da5 e9a0268da4b2e1ba6e89fbac3333ea232c041cc6d5ee75bd2bed2854b0cdac14
download x86_64 af3e8070e882f0008b6511394ae4d3737ece508e9b34abee94cb2f59ddb5e940 e9119426194f256607ed34a0e92abe2c06b7929129c17e36984d6ebfdb1467b4
download x86 82742a51d671778d264fefccd6834d40da94755f2eefb96a494fd2dc5b75fb61 9ea84dd08e162a2f07b9a1d3a73652ac9e4459be5b0bee017755f88fe7e71149
