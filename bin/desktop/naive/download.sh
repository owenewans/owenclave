#!/usr/bin/env bash
# Download the upstream NaiveProxy binary for desktop platforms.
#
# NaiveProxy is not part of the Go core: the Android app ships it as an external
# plugin (`libnaive.so`), and the desktop client runs it the same way, as a child
# process that exposes a local SOCKS listener which the core dials into.
# klzgrad/naiveproxy publishes prebuilt binaries, so no Chromium toolchain is
# needed here.
#
# Usage:
#   ./run desktop naive download            # host platform only
#   TARGETS="darwin/arm64" ./run desktop naive download
#   ./run desktop naive download all        # linux + macos + windows
set -euo pipefail

PROJECT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"

VERSION="${NAIVE_VERSION:-$(grep -E '^NAIVE_VERSION_NAME=' "$PROJECT/version.properties" | cut -d= -f2 | tr -d '[:space:]')}"
BASE_URL="https://github.com/klzgrad/naiveproxy/releases/download/v$VERSION"
OUT_ROOT="${OUT_ROOT:-$PROJECT/desktop/naive/dist}"
CACHE_DIR="${CACHE_DIR:-$PROJECT/desktop/naive/cache}"

host_os="$(uname -s | tr '[:upper:]' '[:lower:]')"
case "$host_os" in
  darwin) host_os="darwin" ;;
  linux) host_os="linux" ;;
  mingw*|msys*|cygwin*) host_os="windows" ;;
esac
host_arch="$(uname -m)"
case "$host_arch" in
  x86_64|amd64) host_arch="amd64" ;;
  aarch64|arm64) host_arch="arm64" ;;
esac
default_targets="$host_os/$host_arch"

if [ "${1:-}" = "all" ]; then
  default_targets="linux/amd64 linux/arm64 darwin/amd64 darwin/arm64 windows/amd64 windows/arm64 windows/386"
fi

TARGETS="${TARGETS:-$default_targets}"

asset_for() {
  case "$1" in
    linux/amd64)   echo "linux-x64.tar.xz" ;;
    linux/arm64)   echo "linux-arm64.tar.xz" ;;
    darwin/amd64)  echo "mac-x64-x64.tar.xz" ;;
    darwin/arm64)  echo "mac-arm64-arm64.tar.xz" ;;
    windows/amd64) echo "win-x64.zip" ;;
    windows/arm64) echo "win-arm64.zip" ;;
    windows/386)   echo "win-x86.zip" ;;
    *) return 1 ;;
  esac
}

sha_for() {
  case "$1" in
    linux/amd64)   echo "80214dfea1767f6ecf63a3a79dc9f48b203460f51abc609357fff48dd5844c7d" ;;
    linux/arm64)   echo "8f4d917fa39a5d8cf1c17fc12ffd99ba84c015b62b43461d09cc074ca4114d8d" ;;
    darwin/amd64)  echo "a3904193bd8d099eb034366c19a019f22573171aeece34e8d0206a92f0b8141d" ;;
    darwin/arm64)  echo "8b330140e0fed15fae71732cefee16f052325c30352ae3e5638a1e9f9d54d542" ;;
    windows/amd64) echo "36d14c117ed052c8d04d10a30db083423ad4e423c012c2ef2ca7286ec06afbbc" ;;
    windows/arm64) echo "68a7378ce6414df325f8d9d38fd8792906d63a601ae0b2f1c76d263bd917af5d" ;;
    windows/386)   echo "335d31bd2ffdb905f2ad7974390cddeb9a11ccafee76993facd12cf7b210e3f0" ;;
    *) return 1 ;;
  esac
}

# `sha256sum` is not guaranteed to exist in Git Bash, while python3 is available on
# every platform we build for.
sha256_of() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | cut -d' ' -f1
  else
    python3 -c 'import hashlib,sys;print(hashlib.sha256(open(sys.argv[1],"rb").read()).hexdigest())' "$1"
  fi
}

mkdir -p "$CACHE_DIR"

for target in $TARGETS; do
  target_os="${target%%/*}"
  target_arch="${target##*/}"
  suffix="$(asset_for "$target")"
  expected="$(sha_for "$target")"
  asset="naiveproxy-v$VERSION-$suffix"
  archive="$CACHE_DIR/$asset"

  if [ ! -s "$archive" ] || [ "$(sha256_of "$archive")" != "$expected" ]; then
    echo ">>> downloading $asset"
    curl -fL --retry 3 "$BASE_URL/$asset" -o "$archive"
  fi
  actual="$(sha256_of "$archive")"
  if [ "$actual" != "$expected" ]; then
    echo "!!! checksum mismatch for $asset: expected $expected, got $actual" >&2
    exit 1
  fi

  out_dir="$OUT_ROOT/$target_os-$target_arch"
  rm -rf "$out_dir"
  mkdir -p "$out_dir"

  python3 - "$archive" "$out_dir" "$target_os" <<'PY'
import os, shutil, sys, tarfile, tempfile, zipfile

archive, out_dir, target_os = sys.argv[1], sys.argv[2], sys.argv[3]
wanted = "naive.exe" if target_os == "windows" else "naive"

with tempfile.TemporaryDirectory() as tmp:
    if archive.endswith(".zip"):
        with zipfile.ZipFile(archive) as zf:
            zf.extractall(tmp)
    else:
        with tarfile.open(archive) as tf:
            tf.extractall(tmp)

    found = None
    for root, _dirs, files in os.walk(tmp):
        for name in files:
            if name == wanted:
                found = os.path.join(root, name)
                break
        if found:
            break
    if not found:
        sys.exit(f"{wanted} not found inside {archive}")

    destination = os.path.join(out_dir, wanted)
    shutil.copy2(found, destination)
    os.chmod(destination, 0o755 if target_os != "windows" else 0o644)
    print(f"    extracted -> {destination}")
PY

  ls -la "$out_dir"
done

echo "=== desktop naive binaries in $OUT_ROOT ==="
