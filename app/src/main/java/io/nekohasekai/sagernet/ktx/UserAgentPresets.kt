package io.nekohasekai.sagernet.ktx

import io.nekohasekai.sagernet.BuildConfig

// Cosmetic client fingerprints for blending in with other subscription clients.
// Format examples confirmed from https://docs.rw and openlibrecommunity/panel's client list.
val USER_AGENT_PRESETS = listOf(
    "Owenclave/${BuildConfig.VERSION_NAME}",
    "Happ/3.13.0",
    "INCY/3.5.3/android",
    "INCY/3.5.3/ios",
    "Throne/2.16.0",
    "V2rayTun/1.9.7",
    "V2rayNG/1.10.4",
    "Karing/1.6.0",
    "FlClash/0.8.87",
    "Clash Verge/2.4.4",
    "Hiddify/2.5.7",
    "SimpleXray/1.3.5",
    "NekoBoxForAndroid/1.4.5",
)
