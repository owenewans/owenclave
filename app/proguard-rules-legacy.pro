# Build fingerprint: 'Android/sdk_phone_x86_64/generic_x86_64:5.1.1/LMY48X/4174727:userdebug/test-keys'
# Revision: '0'
# ABI: 'x86'
# pid: 6560, tid: 6588, name: DefaultDispatch  >>> com.github.dyhkwong.sagernet:bg <<<
# signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x28
#     eax 80000000  ebx 80000001  ecx 00000000  edx 00000000
#     esi 00000010  edi 00000018
#     xcs 00000023  xds 0000002b  xes 0000002b  xfs 00000077  xss 0000002b
#     eip e3cdb747  ebp 12dd62b0  esp bcd55938  flags 00210246
# backtrace:
#     #00 pc 00feb747  /data/dalvik-cache/x86/data@app@com.github.dyhkwong.sagernet-2@base.apk@classes.dex
# The crash log is reproduced with x86 build in Android Emulator (Android 5.0/5.1 x86/x86_64 images).
# I don't have an armeabi-v7a/arm64-v8a Android 5.0/5.1 device to test if armeabi-v7a build also suffers from this.
# This is an AGP 9.3.0 bug. Downgrade AGP to 9.2.1 will make the crash disappear.
-dontoptimize