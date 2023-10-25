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
import com.takisoft.preferencex.PreferenceFragmentCompat
import com.takisoft.preferencex.SimpleMenuPreference
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.mieru2.Mieru2Bean
import io.nekohasekai.sagernet.ktx.applyDefaultValues

class Mieru2SettingsActivity : ProfileSettingsActivity<Mieru2Bean>() {

    override fun createEntity() = Mieru2Bean().applyDefaultValues()

    override fun Mieru2Bean.init() {
        DataStore.profileName = name
        DataStore.serverAddress = serverAddress
        DataStore.serverPort = serverPort
        DataStore.serverProtocolVersion = protocol
        DataStore.serverUsername = username
        DataStore.serverPassword = password
        DataStore.serverMTU = mtu
        DataStore.serverMieruMuxLevel = muxLevel
    }

    override fun Mieru2Bean.serialize() {
        name = DataStore.profileName
        serverAddress = DataStore.serverAddress
        serverPort = DataStore.serverPort
        protocol = DataStore.serverProtocolVersion
        username = DataStore.serverUsername
        password = DataStore.serverPassword
        mtu = DataStore.serverMTU
        muxLevel = DataStore.serverMieruMuxLevel
    }

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.mieru2_preferences)

        val protocol = findPreference<SimpleMenuPreference>(Key.SERVER_PROTOCOL)!!
        val mtu = findPreference<EditTextPreference>(Key.SERVER_MTU)!!
        mtu.isVisible = protocol.value == "${Mieru2Bean.PROTOCOL_UDP}"
        protocol.setOnPreferenceChangeListener { _, newValue ->
            mtu.isVisible = newValue == "${Mieru2Bean.PROTOCOL_UDP}"
            true
        }

        findPreference<EditTextPreference>(Key.SERVER_PASSWORD)!!.apply {
            summaryProvider = PasswordSummaryProvider
        }
    }

}