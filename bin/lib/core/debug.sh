#!/usr/bin/env bash

source "bin/init/env.sh"

rm -rf library/core/build
cd library/core
./debug.sh || exit 1

mkdir -p "$PROJECT/app/libs"
cp -f libowenclavecore.aar "$PROJECT/app/libs"
