/******************************************************************************
 *                                                                            *
 * Copyright (C) 2026  dyhkwong                                               *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 *  (at your option) any later version.                                       *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program. If not, see <https://www.gnu.org/licenses/>.      *
 *                                                                            *
 ******************************************************************************/

package io.nekohasekai.sagernet.ui.profile


import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.fmt.masque.MASQUEBean
import io.nekohasekai.sagernet.ktx.unwrapIDN

class MASQUESettingsActivity: ProfileSettingsActivity<MASQUEBean>() {

    override fun createEntity() = MASQUEBean()

    override fun MASQUEBean.init() {
        DataStore.profileName = name
        DataStore.serverAddress = serverAddress
        DataStore.serverPort = serverPort
        DataStore.serverUsername = username
        DataStore.serverPassword = password
        DataStore.serverMASQUEURITemplate = uriTemplate
        DataStore.serverMASQUEHTTP3 = http3
        DataStore.serverSecurity = security
        DataStore.serverMASQUEMTU = mtu
        DataStore.serverSNI = sni
        DataStore.serverCertificates = certificates
        DataStore.serverPinnedCertificateChain = pinnedPeerCertificateChainSha256
        DataStore.serverPinnedCertificatePublicKey = pinnedPeerCertificatePublicKeySha256
        DataStore.serverPinnedCertificate = pinnedPeerCertificateSha256
        DataStore.serverUTLSFingerprint = utlsFingerprint
        DataStore.serverEchEnabled = echEnabled
        DataStore.serverEchConfigList = echConfigList
        DataStore.serverEchQueryName = echQueryName
        DataStore.serverAllowInsecure = allowInsecure
        DataStore.serverMtlsCertificate = mtlsCertificate
        DataStore.serverMtlsCertificatePrivateKey = mtlsCertificatePrivateKey
        DataStore.serverServerNameToVerify = serverNameToVerify
    }

    override fun MASQUEBean.serialize() {
        name = DataStore.profileName
        serverAddress = DataStore.serverAddress.unwrapIDN()
        serverPort = DataStore.serverPort
        username = DataStore.serverUsername
        password = DataStore.serverPassword
        uriTemplate = DataStore.serverMASQUEURITemplate
        http3 = DataStore.serverMASQUEHTTP3
        security = DataStore.serverSecurity
        mtu = DataStore.serverMASQUEMTU
        sni =  DataStore.serverSNI
        certificates = DataStore.serverCertificates
        pinnedPeerCertificateChainSha256 = DataStore.serverPinnedCertificateChain
        pinnedPeerCertificatePublicKeySha256 = DataStore.serverPinnedCertificatePublicKey
        pinnedPeerCertificateSha256 = DataStore.serverPinnedCertificate
        utlsFingerprint = DataStore.serverUTLSFingerprint
        echEnabled = DataStore.serverEchEnabled
        echConfigList = DataStore.serverEchConfigList
        echQueryName = DataStore.serverEchQueryName
        allowInsecure = DataStore.serverAllowInsecure
        mtlsCertificate = DataStore.serverMtlsCertificate
        mtlsCertificatePrivateKey = DataStore.serverMtlsCertificatePrivateKey
        serverNameToVerify = DataStore.serverServerNameToVerify
    }

    lateinit var http3: SwitchPreference
    lateinit var securityCategory: PreferenceCategory
    lateinit var security: ListPreference
    lateinit var sni: EditTextPreference
    lateinit var certificates: EditTextPreference
    lateinit var pinnedCertificateChain: EditTextPreference
    lateinit var pinnedPeerCertificatePublicKey: EditTextPreference
    lateinit var pinnedPeerCertificate: EditTextPreference
    lateinit var allowInsecure: SwitchPreference
    lateinit var utlsFingerprint: ListPreference
    lateinit var mtlsCertificate: EditTextPreference
    lateinit var mtlsCertificatePrivateKey: EditTextPreference
    lateinit var serverNameToVerify: EditTextPreference
    lateinit var echEnabled: SwitchPreference
    lateinit var echConfigList: EditTextPreference
    lateinit var echQueryName: EditTextPreference

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.masque_preferences)

        findPreference<EditTextPreference>(Key.SERVER_PORT)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        }
        findPreference<EditTextPreference>(Key.SERVER_PASSWORD)!!.apply {
            summaryProvider = PasswordSummaryProvider
        }
        findPreference<EditTextPreference>(Key.SERVER_MASQUE_MTU)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Number)
        }
        http3 = findPreference(Key.SERVER_MASQUE_HTTP3)!!
        securityCategory = findPreference(Key.SERVER_SECURITY_CATEGORY)!!
        security = findPreference(Key.SERVER_SECURITY)!!
        sni = findPreference(Key.SERVER_SNI)!!
        certificates = findPreference(Key.SERVER_CERTIFICATES)!!
        pinnedCertificateChain = findPreference(Key.SERVER_PINNED_CERTIFICATE_CHAIN)!!
        pinnedPeerCertificatePublicKey = findPreference(Key.SERVER_PINNED_CERTIFICATE_PUBLIC_KEY)!!
        pinnedPeerCertificate = findPreference(Key.SERVER_PINNED_CERTIFICATE)!!
        allowInsecure = findPreference(Key.SERVER_ALLOW_INSECURE)!!
        utlsFingerprint = findPreference(Key.SERVER_UTLS_FINGERPRINT)!!
        echEnabled = findPreference(Key.SERVER_ECH_ENABLED)!!
        echConfigList = findPreference(Key.SERVER_ECH_CONFIG_LIST)!!
        echQueryName = findPreference(Key.SERVER_ECH_QUERY_NAME)!!
        echConfigList.isEnabled = echEnabled.isChecked
        echQueryName.isEnabled = echEnabled.isChecked
        echEnabled.setOnPreferenceChangeListener { _, newValue ->
            echConfigList.isEnabled = newValue as Boolean
            echQueryName.isEnabled = newValue
            true
        }
        mtlsCertificate = findPreference(Key.SERVER_MTLS_CERTIFICATE)!!
        mtlsCertificatePrivateKey = findPreference(Key.SERVER_MTLS_CERTIFICATE_PRIVATE_KEY)!!
        serverNameToVerify = findPreference(Key.SERVER_SERVER_NAME_TO_VERIFY)!!
        val securityValues = resources.getStringArray(R.array.masque_transport_layer_encryption_value)
        if (security.value !in securityValues) {
            security.value = "tls"
        }
        updateSecurity(security.value, http3.isChecked)
        security.setOnPreferenceChangeListener { _, newValue ->
            updateSecurity(newValue as String, http3.isChecked)
            true
        }
        http3.setOnPreferenceChangeListener { _, newValue ->
            updateSecurity(security.value, newValue as Boolean)
            true
        }
    }

    fun updateSecurity(security: String, http3: Boolean) {
        val isTLS = security == "tls"
        securityCategory.isVisible = isTLS
        certificates.isVisible = isTLS
        pinnedCertificateChain.isVisible = isTLS
        pinnedPeerCertificatePublicKey.isVisible = isTLS
        pinnedPeerCertificate.isVisible = isTLS
        allowInsecure.isVisible = isTLS
        sni.isVisible = isTLS
        mtlsCertificate.isVisible = isTLS
        mtlsCertificatePrivateKey.isVisible = isTLS
        serverNameToVerify.isVisible = isTLS
        echEnabled.isVisible = isTLS
        echConfigList.isVisible = isTLS
        echQueryName.isVisible = isTLS
        utlsFingerprint.isVisible = isTLS && !http3
    }

}
