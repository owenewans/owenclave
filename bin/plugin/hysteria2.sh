#!/usr/bin/env bash

bin/plugin/hysteria2/init.sh &&
  bin/plugin/hysteria2/armeabi-v7a.sh &&
  bin/plugin/hysteria2/arm64-v8a.sh &&
  bin/plugin/hysteria2/x86.sh &&
  bin/plugin/hysteria2/x86_64.sh &&
  bin/plugin/hysteria2/end.sh
