#!/usr/bin/env bash

bin/plugin/juicity/init.sh &&
  bin/plugin/juicity/armeabi-v7a.sh &&
  bin/plugin/juicity/arm64-v8a.sh &&
  bin/plugin/juicity/x86.sh &&
  bin/plugin/juicity/x86_64.sh &&
  bin/plugin/juicity/end.sh
