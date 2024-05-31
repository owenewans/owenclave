/******************************************************************************
 *                                                                            *
 * Copyright (C) 2021 by nekohasekai <contact-sagernet@sekai.icu>             *
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
 * along with this program. If not, see <http://www.gnu.org/licenses/>.       *
 *                                                                            *
 ******************************************************************************/

package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.SwitchPreference
import com.takisoft.preferencex.PreferenceFragmentCompat
import com.takisoft.preferencex.SimpleMenuPreference
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.fmt.http.HttpBean

class HttpSettingsActivity : ProfileSettingsActivity<HttpBean>() {

    override fun createEntity() = HttpBean()

    override fun HttpBean.init() {
        DataStore.profileName = name
        DataStore.serverAddress = serverAddress
        DataStore.serverPort = serverPort
        DataStore.serverUsername = username
        DataStore.serverPassword = password
        DataStore.serverTLS = tls
        DataStore.serverSNI = sni
        DataStore.serverUTLSFingerprint = utlsFingerprint
    }

    override fun HttpBean.serialize() {
        name = DataStore.profileName
        serverAddress = DataStore.serverAddress
        serverPort = DataStore.serverPort
        username = DataStore.serverUsername
        password = DataStore.serverPassword
        tls = DataStore.serverTLS
        sni = DataStore.serverSNI
        utlsFingerprint = DataStore.serverUTLSFingerprint
    }

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.http_preferences)
        findPreference<EditTextPreference>(Key.SERVER_PORT)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        }
        findPreference<EditTextPreference>(Key.SERVER_PASSWORD)!!.apply {
            summaryProvider = PasswordSummaryProvider
        }
        val useTLS = findPreference<SwitchPreference>(Key.SERVER_TLS)!!
        val sni = findPreference<EditTextPreference>(Key.SERVER_SNI)!!
        val utlsFingerprint = findPreference<SimpleMenuPreference>(Key.SERVER_UTLS_FINGERPRINT)!!
        fun updateTLS(useTLS: Boolean) {
            sni.isVisible = useTLS
            utlsFingerprint.isVisible = useTLS
        }
        updateTLS(DataStore.serverTLS)
        useTLS.setOnPreferenceChangeListener { _, newValue ->
            updateTLS(newValue as Boolean)
            true
        }
    }

}