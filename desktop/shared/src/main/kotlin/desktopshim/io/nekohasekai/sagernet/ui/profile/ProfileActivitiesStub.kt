package io.nekohasekai.sagernet.ui.profile

import android.app.Activity

open class ProfileSettingsActivity : Activity() {
    companion object {
        const val EXTRA_PROFILE_ID = "id"
        const val EXTRA_IS_SUBSCRIPTION = "sub"
    }
}

class SocksSettingsActivity : ProfileSettingsActivity()
class HttpSettingsActivity : ProfileSettingsActivity()
class ShadowsocksSettingsActivity : ProfileSettingsActivity()
class ShadowsocksRSettingsActivity : ProfileSettingsActivity()
class VMessSettingsActivity : ProfileSettingsActivity()
class VLESSSettingsActivity : ProfileSettingsActivity()
class TrojanSettingsActivity : ProfileSettingsActivity()
class NaiveSettingsActivity : ProfileSettingsActivity()
class Hysteria2SettingsActivity : ProfileSettingsActivity()
class SSHSettingsActivity : ProfileSettingsActivity()
class WireGuardSettingsActivity : ProfileSettingsActivity()
class MieruSettingsActivity : ProfileSettingsActivity()
class Tuic5SettingsActivity : ProfileSettingsActivity()
class JuicitySettingsActivity : ProfileSettingsActivity()
class Http3SettingsActivity : ProfileSettingsActivity()
class AnyTLSSettingsActivity : ProfileSettingsActivity()
class ShadowQUICSettingsActivity : ProfileSettingsActivity()
class TrustTunnelSettingsActivity : ProfileSettingsActivity()
class SnellSettingsActivity : ProfileSettingsActivity()
class MASQUESettingsActivity : ProfileSettingsActivity()
class ConfigSettingsActivity : ProfileSettingsActivity()
class ChainSettingsActivity : ProfileSettingsActivity()
class BalancerSettingsActivity : ProfileSettingsActivity()

