#!/usr/bin/env bash

source "bin/init/env.sh"

export CGO_ENABLED=1
export GOOS=android
export CGO_LDFLAGS="-Wl,-z,max-page-size=16384"

CURR="plugin/hysteria"
CURR_PATH="$PROJECT/$CURR"

ROOT="$CURR_PATH/src/main/jniLibs"
OUTPUT="hysteria"
LIB_OUTPUT="lib$OUTPUT.so"

cd $CURR_PATH/src/main/go/hysteria
