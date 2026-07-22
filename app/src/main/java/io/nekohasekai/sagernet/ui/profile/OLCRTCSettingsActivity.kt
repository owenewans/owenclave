package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.fmt.olcrtc.OLCRTCBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues

class OLCRTCSettingsActivity : ProfileSettingsActivity<OLCRTCBean>() {

    override fun createEntity() = OLCRTCBean()

    override fun OLCRTCBean.init() {
        DataStore.profileName = name
        DataStore.serverOlcrtcAuthProvider = authProvider
        DataStore.serverOlcrtcTransport = transport
        DataStore.serverOlcrtcRoomId = roomId
        DataStore.serverOlcrtcEncryptionKey = encryptionKey
        DataStore.serverOlcrtcDnsServer = dnsServer
        DataStore.serverOlcrtcSocksHost = socksHost
        DataStore.serverOlcrtcSocksPort = socksPort
    }

    override fun OLCRTCBean.serialize() {
        name = DataStore.profileName
        authProvider = DataStore.serverOlcrtcAuthProvider
        transport = DataStore.serverOlcrtcTransport
        roomId = DataStore.serverOlcrtcRoomId
        encryptionKey = DataStore.serverOlcrtcEncryptionKey
        dnsServer = DataStore.serverOlcrtcDnsServer
        socksHost = DataStore.serverOlcrtcSocksHost
        socksPort = DataStore.serverOlcrtcSocksPort
    }

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.olcrtc_preferences)
        findPreference<EditTextPreference>(Key.SERVER_OLCRTC_ENCRYPTION_KEY)?.apply {
            summaryProvider = PasswordSummaryProvider
        }
        findPreference<EditTextPreference>(Key.SERVER_OLCRTC_SOCKS_PORT)?.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        }
    }
}
