#!/usr/bin/env bash
# Build the complete desktop client for the current platform:
# the Go core (JNI + CLI), the NaiveProxy plugin and the packaged Compose
# application.
#
# Usage:
#   ./run desktop build            # core + naive + portable zip + installer
#   ./run desktop build all        # cross platform binaries only (no packaging)
set -euo pipefail

PROJECT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$PROJECT"

host_target() {
  local os arch
  case "$(uname -s)" in
    Darwin) os="darwin" ;;
    Linux) os="linux" ;;
    *) os="windows" ;;
  esac
  case "$(uname -m)" in
    x86_64|amd64) arch="amd64" ;;
    aarch64|arm64) arch="arm64" ;;
    *) arch="amd64" ;;
  esac
  echo "$os/$arch"
}

if [ "${1:-}" = "all" ]; then
  TARGETS="linux/amd64 linux/arm64 darwin/amd64 darwin/arm64 windows/amd64 windows/arm64" \
    "$PROJECT/bin/desktop/core/build.sh" all
  TARGETS="linux/amd64 linux/arm64 darwin/amd64 darwin/arm64 windows/amd64 windows/arm64 windows/386" \
    "$PROJECT/bin/desktop/naive/download.sh" all
  TARGETS="linux/amd64 linux/arm64 darwin/amd64 darwin/arm64 windows/amd64 windows/arm64" \
    "$PROJECT/bin/desktop/tun2socks/download.sh" all
  echo "=== cross platform binaries are in desktop/core/dist, desktop/naive/dist and desktop/tun2socks/dist ==="
  echo "=== packaging only works for the host OS: ./gradlew :desktop:app:packagePortable ==="
  exit 0
fi

TARGET="$(host_target)"
echo ">>> desktop target: $TARGET"

TARGETS="$TARGET" "$PROJECT/bin/desktop/core/build.sh"
TARGETS="$TARGET" "$PROJECT/bin/desktop/naive/download.sh"
TARGETS="$TARGET" "$PROJECT/bin/desktop/tun2socks/download.sh"

./gradlew :desktop:app:packagePortable :desktop:app:packageWindowsPortable --console=plain

if [ "$TARGET" = "linux/amd64" ] && command -v makepkg >/dev/null 2>&1; then
  echo ">>> building the Arch package and repository"
  bash "$PROJECT/bin/desktop/arch/package.sh"
fi

echo "=== desktop artifacts ==="
ls -la "$PROJECT/desktop/app/build/compose/binaries/main/portable/" 2>/dev/null || true
ls -la "$PROJECT/desktop/app/build/compose/binaries/main/" 2>/dev/null || true
