#!/bin/bash

CGO_LDFLAGS="-Wl,-z,max-page-size=16384" gomobile bind -v -androidapi 21 -trimpath -ldflags='-s -buildid=' -tags='with_clash' "github.com/dyhkwong/libsagernetcore" || exit 1
rm -r libsagernetcore-sources.jar

proj=../../app/libs
if [ -d $proj ]; then
  cp -f libsagernetcore.aar $proj
  echo ">> install $(realpath $proj)/libsagernetcore.aar"
fi
