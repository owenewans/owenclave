#!/usr/bin/env bash
# Download tun2socks (userspace TUN, gVisor stack) plus wintun for Windows.
#
# The desktop core is the exclave-core CLI, and TUN lives outside of it in this
# fork (on Android the app creates the TUN through VpnService and passes the fd
# to the gomobile library). On desktop the transparent mode is therefore built
# from two pieces: the core serving a local SOCKS inbound, and tun2socks pumping
# the TUN device into it. The core's outbound is bound to the physical interface
# (`bindToDevice`) so it never re-enters the tunnel.
#
# Usage:
#   ./run desktop tun2socks download            # host platform only
#   TARGETS="darwin/arm64" ./run desktop tun2socks download
#   ./run desktop tun2socks download all
set -euo pipefail

PROJECT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"

TUN2SOCKS_VERSION="${TUN2SOCKS_VERSION:-2.7.0}"
WINTUN_VERSION="${WINTUN_VERSION:-0.14.1}"
BASE_URL="https://github.com/xjasonlyu/tun2socks/releases/download/v$TUN2SOCKS_VERSION"
WINTUN_URLS=(
  "https://www.wintun.net/builds/wintun-$WINTUN_VERSION.zip"
  "https://download.wireguard.com/wintun/builds/wintun-$WINTUN_VERSION.zip"
)
WINTUN_SHA256="${WINTUN_SHA256:-07c256185d6ee3652e09fa55c0b673e2624b565e02c4b9091c79ca7d2f24ef51}"

OUT_ROOT="${OUT_ROOT:-$PROJECT/desktop/tun2socks/dist}"
CACHE_DIR="${CACHE_DIR:-$PROJECT/desktop/tun2socks/cache}"

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
  default_targets="linux/amd64 linux/arm64 darwin/amd64 darwin/arm64 windows/amd64 windows/arm64"
fi

TARGETS="${TARGETS:-$default_targets}"

# `sha256sum` is not guaranteed to exist in Git Bash, while python3 is available on
# every platform we build for.
sha256_of() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | cut -d' ' -f1
  else
    python3 -c 'import hashlib,sys;print(hashlib.sha256(open(sys.argv[1],"rb").read()).hexdigest())' "$1"
  fi
}

asset_for() {
  case "$1" in
    linux/amd64)   echo "tun2socks-linux-amd64.zip" ;;
    linux/arm64)   echo "tun2socks-linux-arm64.zip" ;;
    darwin/amd64)  echo "tun2socks-darwin-amd64.zip" ;;
    darwin/arm64)  echo "tun2socks-darwin-arm64.zip" ;;
    windows/amd64) echo "tun2socks-windows-amd64.zip" ;;
    windows/arm64) echo "tun2socks-windows-arm64.zip" ;;
    *) return 1 ;;
  esac
}

sha_for() {
  case "$1" in
    linux/amd64)   echo "a612baa287a3b6de6221f74fd02b442a50888508227ecf51e1288a5ccbb77381" ;;
    linux/arm64)   echo "3931476c9cfa8fa236d23aeaf36767df0eb27cc11ecaab699faba57744450f49" ;;
    darwin/amd64)  echo "6e654da8bab9ca1645862f0e251a69980e0966680713011feea7b1e5901b2a95" ;;
    darwin/arm64)  echo "7c5ebfe2ffb60ecf6e958cc5bbf3e06e74b8b33575ffbb4ba4f6f785a647f1ad" ;;
    windows/amd64) echo "c5d46e9452f6c9cc7c15ab9158d6d6a0169ceecd6bca019ce476b49337d2be43" ;;
    windows/arm64) echo "74497771068da13f42921adfc540f2abb9ac822404582c8cbe34d30e8c0ea1f5" ;;
    *) return 1 ;;
  esac
}

mkdir -p "$CACHE_DIR"

for target in $TARGETS; do
  target_os="${target%%/*}"
  target_arch="${target##*/}"
  asset="$(asset_for "$target")"
  expected="$(sha_for "$target")"
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
import os, shutil, sys, tempfile, zipfile

archive, out_dir, target_os = sys.argv[1], sys.argv[2], sys.argv[3]
wanted = "tun2socks.exe" if target_os == "windows" else "tun2socks"

with tempfile.TemporaryDirectory() as tmp:
    with zipfile.ZipFile(archive) as zf:
        zf.extractall(tmp)

    found = None
    for root, _dirs, files in os.walk(tmp):
        for name in files:
            # upstream ships the binary as `tun2socks-<os>-<arch>[.exe]`
            if name.startswith(wanted.replace(".exe", "")) and name.endswith(wanted[-4:] if wanted.endswith(".exe") else ""):
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

  if [ "$target_os" = "windows" ]; then
    wintun_archive="$CACHE_DIR/wintun-$WINTUN_VERSION.zip"
    wintun_ok=0
    if [ -s "$wintun_archive" ] && [ "$(sha256_of "$wintun_archive")" = "$WINTUN_SHA256" ]; then
      wintun_ok=1
    else
      for wurl in "${WINTUN_URLS[@]}"; do
        for attempt in 1 2 3; do
          echo ">>> downloading wintun-$WINTUN_VERSION.zip from $wurl (attempt $attempt)"
          if curl -fL --retry 2 --connect-timeout 20 "$wurl" -o "$wintun_archive"; then
            if [ "$(sha256_of "$wintun_archive")" = "$WINTUN_SHA256" ]; then
              wintun_ok=1
              break 2
            fi
            echo "!!! checksum mismatch for wintun archive from $wurl" >&2
          fi
          sleep 3
        done
      done
    fi
    if [ "$wintun_ok" != "1" ]; then
      echo "!!! ERROR: failed to download verified wintun-$WINTUN_VERSION.zip" >&2
      exit 1
    fi
    if [ "$target_arch" = "arm64" ]; then
      wintun_dir="arm64"
    else
      wintun_dir="amd64"
    fi
    python3 - "$wintun_archive" "$out_dir" "$wintun_dir" <<'PYWINTUN'
import os, shutil, sys, tempfile, zipfile

archive, out_dir, arch = sys.argv[1], sys.argv[2], sys.argv[3]
with tempfile.TemporaryDirectory() as tmp:
    with zipfile.ZipFile(archive) as zf:
        zf.extractall(tmp)
    source = os.path.join(tmp, "wintun", "bin", arch, "wintun.dll")
    if not os.path.isfile(source):
        sys.exit(f"wintun.dll for {arch} not found inside {archive}")
    shutil.copy2(source, os.path.join(out_dir, "wintun.dll"))
    print(f"    extracted -> {os.path.join(out_dir, 'wintun.dll')}")
PYWINTUN
  fi

  ls -la "$out_dir"
done

echo "=== desktop tun2socks binaries in $OUT_ROOT ==="
