#!/bin/bash

source "bin/init/env.sh"

CURR="plugin/naive"
CURR_PATH="$PROJECT/$CURR"

git submodule update --init --recursive "$CURR/*"
cd $CURR_PATH/src/main/jni/naiveproxy/src
