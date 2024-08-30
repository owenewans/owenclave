#!/usr/bin/env bash

bin/plugin/shadowtls/init.sh &&
  #bin/plugin/shadowtls/armeabi-v7a.sh &&
  bin/plugin/shadowtls/arm64-v8a.sh &&
  #bin/plugin/shadowtls/x86.sh &&
  bin/plugin/shadowtls/x86_64.sh &&
  bin/plugin/shadowtls/end.sh
