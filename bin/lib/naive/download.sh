#!/usr/bin/env bash
set -euo pipefail

VERSION="150.0.7871.63-1"
BASE_URL="https://github.com/owenewans/owenclave/releases/download/naive-core-$VERSION"
OUT_ROOT="${OUT_ROOT:-$(cd "$(dirname "$0")/../../.." && pwd)/app/src/main/jniLibs}"

download() {
  local abi="$1" sha256="$2"
  local out="$OUT_ROOT/$abi/libnaive.so"
  mkdir -p "$(dirname "$out")"
  if [ -s "$out" ] && echo "$sha256  $out" | sha256sum -c --status; then
    return
  fi
  curl -fL --retry 3 "$BASE_URL/libnaive-$abi.so" -o "$out"
  echo "$sha256  $out" | sha256sum -c --status
  chmod +x "$out"
}

download arm64-v8a   55b64adbda9fc09f4137800d74ac6772b797f96e224c12f69a8e001886bb82eb
download armeabi-v7a b848f96f0605c2e2e5b07280402fb86f109cdedeeb60bd5880ac7c5b5ff49f38
download x86_64      f60c17f6787f0742c6f186fcef73c536627c47ed45fa7b501adf3bd8b5b4a1b4
download x86         0b8fe4e45273a11fad8f200860fdf19bfad241de6ed06f8d7daf7d3a299ffb57
