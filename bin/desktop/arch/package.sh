#!/usr/bin/env bash
# Builds the Arch package and a pacman repository from the jpackage application image.
#
# The application image already contains its own runtime plus the proxy core,
# NaiveProxy and tun2socks, so the package is just a relocation into /opt/owenclave
# plus a launcher symlink, a desktop entry and an icon.
#
# Usage (on Arch):
#   ./run desktop arch package
#   ./run desktop arch repo     # also refresh owenclave.db for a pacman repository
#
# The same script runs inside an `archlinux` container, which is how CI builds the
# package: makepkg refuses to run as root, so when we are root a build user is used.
set -euo pipefail

PROJECT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
APP_IMAGE="${APP_IMAGE:-$PROJECT/desktop/app/build/compose/binaries/main/app/Owenclave}"
DIST="${DIST:-$PROJECT/desktop/arch/dist}"
WORK="${WORK:-$PROJECT/desktop/arch/build}"

if [ ! -d "$APP_IMAGE" ]; then
  echo "!!! application image not found in $APP_IMAGE" >&2
  echo "!!! build it first: ./gradlew :desktop:app:createDistributable" >&2
  exit 1
fi

VERSION="$(grep -E '^VERSION_NAME=' "$PROJECT/version.properties" | cut -d= -f2 | tr -d '[:space:]')"
# desktop packages use 1.a.b for the android 0.a.b, see desktop/app/build.gradle.kts
PKGVER="$(printf '%s' "$VERSION" | sed -E 's/^0+(\.)/1\1/')"
PKGARCH="$(uname -m)"
case "$PKGARCH" in
  x86_64) PKGARCH="x86_64" ;;
  aarch64|arm64) PKGARCH="aarch64" ;;
esac

echo ">>> packaging owenclave-desktop $PKGVER for $PKGARCH"
rm -rf "$WORK" "$DIST"
mkdir -p "$WORK/opt/owenclave" "$DIST"

# 1. the application image becomes /opt/owenclave
cp -a "$APP_IMAGE/." "$WORK/opt/owenclave/"
if [ ! -x "$WORK/opt/owenclave/bin/Owenclave" ]; then
  echo "!!! $WORK/opt/owenclave/bin/Owenclave is not executable" >&2
  exit 1
fi

install -m755 "$PROJECT/packaging/linux/owenclave-tun-helper" "$WORK/opt/owenclave/bin/owenclave-tun-helper"
case "$PKGARCH" in
  aarch64) TUN2SOCKS_ARCH="linux-arm64" ;;
  *) TUN2SOCKS_ARCH="linux-amd64" ;;
esac
if [ -f "$PROJECT/desktop/tun2socks/dist/$TUN2SOCKS_ARCH/tun2socks" ]; then
  install -m755 "$PROJECT/desktop/tun2socks/dist/$TUN2SOCKS_ARCH/tun2socks" "$WORK/opt/owenclave/bin/tun2socks"
fi

# 2. desktop entry, polkit policy and icon, taken from the image when possible
cp "$PROJECT/packaging/arch/owenclave.desktop" "$WORK/owenclave.desktop"
cp "$PROJECT/packaging/arch/org.owenewans.owenclave.policy" "$WORK/org.owenewans.owenclave.policy"
if [ -f "$PROJECT/packaging/icons/owenclave.png" ]; then
  cp "$PROJECT/packaging/icons/owenclave.png" "$WORK/owenclave.png"
elif [ -f "$APP_IMAGE/lib/Owenclave.png" ]; then
  cp "$APP_IMAGE/lib/Owenclave.png" "$WORK/owenclave.png"
elif [ -f "$PROJECT/app/src/main/ic_launcher-playstore.png" ]; then
  cp "$PROJECT/app/src/main/ic_launcher-playstore.png" "$WORK/owenclave.png"
else
  # a 1x1 transparent png keeps the package valid even without an icon
  printf '\x89PNG\r\n\x1a\n' >"$WORK/owenclave.png"
fi

# 3. source tarball expected by the PKGBUILD
tar -czf "$WORK/owenclave-desktop-$PKGVER.tar.gz" -C "$WORK" \
  opt owenclave.desktop owenclave.png org.owenewans.owenclave.policy --owner=0 --group=0

sed -e "s/@PKGVER@/$PKGVER/g" \
    -e "s/@PKGDESC@/Owenclave desktop proxy client $PKGVER/" \
    "$PROJECT/packaging/arch/PKGBUILD.in" >"$WORK/PKGBUILD"
cp "$PROJECT/packaging/arch/owenclave.install" "$WORK/owenclave.install"

build_package() {
  cd "$WORK"
  makepkg --force --nodeps --noconfirm
}

if [ "$(id -u)" = "0" ]; then
  # makepkg refuses to run as root, which is the case inside the CI container
  if ! id builder >/dev/null 2>&1; then
    useradd -m builder
  fi
  chown -R builder "$WORK" "$DIST"
  su builder -c "cd '$WORK' && makepkg --force --nodeps --noconfirm"
else
  build_package
fi

PACKAGE="$(ls "$WORK"/owenclave-desktop-"$PKGVER"-*.pkg.tar.* | head -1)"
cp "$PACKAGE" "$DIST/"

# 4. pacman repository database, so users can install from it
if command -v repo-add >/dev/null 2>&1; then
  cd "$DIST"
  repo-add -q owenclave.db.tar.gz "$(basename "$PACKAGE")"
  # GitHub serves plain files: `repo-add` leaves <repo>.db as a symlink to the
  # tarball, and a symlink cannot be uploaded, so real copies are made here.
  rm -f owenclave.db owenclave.files
  cp owenclave.db.tar.gz owenclave.db
  [ -f owenclave.files.tar.gz ] && cp owenclave.files.tar.gz owenclave.files
  ls -la "$DIST"
else
  echo "!!! repo-add not found, skipping the repository database"
fi

echo "=== arch artifacts in $DIST ==="
