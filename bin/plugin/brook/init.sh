#!/usr/bin/env bash

source "bin/init/env.sh"

export CGO_ENABLED=1
export GOOS=android

CURR="plugin/brook"
CURR_PATH="$PROJECT/$CURR"

git submodule update --init "$CURR/*"
cp bin/plugin/brook/diff.patch $CURR_PATH/src/main/go/brook
cd $CURR_PATH/src/main/go/brook
git apply diff.patch
rm diff.patch
go mod download -x
