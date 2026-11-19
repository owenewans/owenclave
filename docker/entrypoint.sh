#!/usr/bin/env bash
set -euo pipefail

APP_HOME="${OWENCLAVE_HOME:-/opt/owenclave}"
DATA_DIR="${DATA_DIR:-/data}"
mkdir -p "$DATA_DIR"

export OWENCLAVE_ASSET_DIR="${OWENCLAVE_ASSET_DIR:-$APP_HOME/assets}"
export V2RAY_LOCATION_ASSET="$OWENCLAVE_ASSET_DIR"

JNI_LIB="$APP_HOME/bin/libgojni.so"
JAVA_OPTS_ARR=(
  "-Djava.awt.headless=true"
  "-Dowenclave.dataDir=$DATA_DIR"
  "-Dowenclave.assetDir=$OWENCLAVE_ASSET_DIR"
  "-Dowenclave.core=$APP_HOME/bin/owenclave-core"
  "-Dowenclave.naive=$APP_HOME/bin/naive"
  "-Dowenclave.tun2socks=$APP_HOME/bin/tun2socks"
)

if [ -f "$JNI_LIB" ]; then
  JAVA_OPTS_ARR+=("-Dowenclave.gojni.path=$JNI_LIB")
fi

# Default to Mihomo-compatible daemon mode unless another CLI flag was given
if [ "$#" -eq 0 ]; then
  set -- --daemon
fi

exec java ${JAVA_OPTS:-} "${JAVA_OPTS_ARR[@]}" \
  -cp "$APP_HOME/lib/*" \
  io.nekohasekai.sagernet.desktop.server.DaemonMain \
  "$@"

