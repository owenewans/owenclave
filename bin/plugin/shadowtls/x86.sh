#!/bin/bash

source "bin/init/env.sh"
source "bin/plugin/shadowtls/build.sh"

DIR="$ROOT/x86"
mkdir -p $DIR

export CC=$ANDROID_X86_CC_21
export CXX=$ANDROID_X86_CXX_21
export RUST_ANDROID_GRADLE_CC=$ANDROID_X86_CC_21
export CARGO_TARGET_I686_LINUX_ANDROID_LINKER=$PROJECT/bin/rust-linker/linker-wrapper.sh

rustup override set nightly-2023-05-17
cargo build --release -p shadow-tls --target i686-linux-android
cp target/i686-linux-android/release/shadow-tls $DIR/$LIB_OUTPUT
