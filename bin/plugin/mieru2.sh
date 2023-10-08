#!/usr/bin/env bash

bin/plugin/mieru2/init.sh &&
  bin/plugin/mieru2/armeabi-v7a.sh &&
  bin/plugin/mieru2/arm64-v8a.sh &&
  bin/plugin/mieru2/x86.sh &&
  bin/plugin/mieru2/x86_64.sh &&
  bin/plugin/mieru2/end.sh
