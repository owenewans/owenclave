#!/usr/bin/env bash
# Adds the owenclave pacman repository and installs the desktop client.
#
# One command (as root, which is why it is meant to be piped into sudo bash):
#
#   sudo bash -c "$(curl -fsSL https://raw.githubusercontent.com/owenewans/owenclave/dev/packaging/arch/setup-repo.sh)"
#
# The repository lives in the moving `desktop-repo` release, so the same URL keeps
# working across versions and `pacman -Syu` picks up new builds.
#
# Options:
#   --uninstall        remove the repository entry (the package stays installed)
#   --repo <url>       use a different repository URL
#   --no-install       only touch /etc/pacman.conf
#
# The packages are not GPG signed, hence SigLevel = Optional TrustAll below: the
# repository is fetched over HTTPS from one fixed GitHub release URL.
set -euo pipefail

REPO_NAME="owenclave"
GITHUB_REPO="${OWENCLAVE_GITHUB_REPO:-owenewans/owenclave}"
REPO_URL="${OWENCLAVE_REPO_URL:-https://github.com/${GITHUB_REPO}/releases/download/desktop-repo}"
CONF="/etc/pacman.conf"
DO_INSTALL=1

while [ $# -gt 0 ]; do
  case "$1" in
    --uninstall)
      if grep -q "^\[$REPO_NAME\]" "$CONF"; then
        # drop the section header, its SigLevel/Server lines and a trailing blank line
        sed -i "/^\[$REPO_NAME\]/,/^$/d" "$CONF"
        echo "removed [$REPO_NAME] from $CONF"
      else
        echo "[$REPO_NAME] is not configured in $CONF"
      fi
      exit 0
      ;;
    --repo)
      shift
      REPO_URL="$1"
      ;;
    --no-install)
      DO_INSTALL=0
      ;;
    *)
      echo "unknown option: $1" >&2
      exit 1
      ;;
  esac
  shift
done

if [ "$(id -u)" != "0" ]; then
  echo "this script writes to $CONF and runs pacman, so it needs root" >&2
  echo "run it as: sudo bash -c \"\$(curl -fsSL <url of this script>)\"" >&2
  exit 1
fi

if ! command -v pacman >/dev/null 2>&1; then
  echo "pacman not found: this installer is for Arch Linux and derivatives" >&2
  exit 1
fi

if grep -q "^\[$REPO_NAME\]" "$CONF"; then
  echo "[$REPO_NAME] is already configured, updating its server line"
  # replace the server line that follows the section header
  awk -v repo="[$REPO_NAME]" -v url="$REPO_URL" '
    $0 == repo { in_section = 1; print; next }
    in_section && /^Server *=/ { print "Server = " url; in_section = 0; next }
    { print }
  ' "$CONF" >"$CONF.tmp"
  mv "$CONF.tmp" "$CONF"
else
  {
    echo ""
    echo "[$REPO_NAME]"
    echo "SigLevel = Optional TrustAll"
    echo "Server = $REPO_URL"
  } >>"$CONF"
  echo "added [$REPO_NAME] to $CONF"
fi

if [ "$DO_INSTALL" = "1" ]; then
  pacman -Sy --noconfirm "$REPO_NAME"-desktop
  echo ""
  echo "installed. start it from the menu or with: owenclave"
  echo "VPN (TUN) mode prompts for privileges via Polkit (pkexec) when connecting"
fi
