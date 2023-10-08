#!/usr/bin/env bash

source "bin/init/env.sh"

export CGO_ENABLED=1
export GOOS=android

CURR="plugin/mieru2"
CURR_PATH="$PROJECT/$CURR"

git submodule update --init "$CURR/*"
cd $CURR_PATH/src/main/go/mieru2
go mod download -x
