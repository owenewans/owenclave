#!/bin/bash

source "bin/init/env.sh"
source "bin/plugin/shadowtls/build.sh"

DIR="$ROOT/arm64-v8a"
mkdir -p $DIR

export CC=$ANDROID_ARM64_CC
export CXX=$ANDROID_ARM64_CXX
export RUST_ANDROID_GRADLE_CC=$ANDROID_ARM64_CC
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER=$PROJECT/bin/rust-linker/linker-wrapper.sh

rustup override set nightly-2023-12-13
cargo build --release -p shadow-tls --target aarch64-linux-android
cp target/aarch64-linux-android/release/shadow-tls $DIR/$LIB_OUTPUT
