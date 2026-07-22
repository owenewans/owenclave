#!/usr/bin/env bash

rm -rf app/build/outputs
./gradlew --stop
./gradlew :app:assembleOssRelease
