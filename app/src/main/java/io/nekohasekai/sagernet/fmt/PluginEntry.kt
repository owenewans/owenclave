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

package io.nekohasekai.sagernet.fmt

import androidx.annotation.StringRes
import io.nekohasekai.sagernet.R

enum class PluginEntry(
    val pluginId: String,
    @StringRes val nameId: Int,
    val packageName: String,
) {
    // sagernet plugins
    TrojanGo("trojan-go-plugin", R.string.action_trojan_go, "io.nekohasekai.sagernet.plugin.trojan_go"),
    NaiveProxy("naive-plugin", R.string.action_naive, "io.nekohasekai.sagernet.plugin.naive"),
    PingTunnel("pingtunnel-plugin", R.string.action_ping_tunnel, "io.nekohasekai.sagernet.plugin.pingtunnel"),
    RelayBaton("relaybaton-plugin", R.string.action_relay_baton, "io.nekohasekai.sagernet.plugin.relaybaton"),
    Brook("brook-plugin", R.string.action_brook, "com.github.dyhkwong.sagernet.plugin.brook"),
    Hysteria("hysteria-plugin", R.string.action_hysteria, "io.nekohasekai.sagernet.plugin.hysteria"),
    Hysteria2("hysteria2-plugin", R.string.action_hysteria2, "com.github.dyhkwong.sagernet.plugin.hysteria2"),
    Mieru("mieru-plugin", R.string.action_mieru, "com.github.dyhkwong.sagernet.sagernet.plugin.mieru"),
    Mieru2("mieru2-plugin", R.string.action_mieru2, "io.nekohasekai.sagernet.plugin.mieru2"),
    TUIC("tuic-plugin", R.string.action_tuic, "io.nekohasekai.sagernet.plugin.tuic"),
    TUIC5("tuic5-plugin", R.string.action_tuic5, "io.nekohasekai.sagernet.plugin.tuic5"),
    ShadowTLS("shadowtls-plugin", R.string.action_shadowtls, "com.github.dyhkwong.sagernet.plugin.shadowtls"),

    // shadowsocks plugins

    ObfsLocal("shadowsocks-obfs-local", R.string.shadowsocks_plugin_simple_obfs, "com.github.shadowsocks.plugin.obfs_local");

    companion object {

        fun find(name: String): PluginEntry? {
            for (pluginEntry in enumValues<PluginEntry>()) {
                if (name == pluginEntry.pluginId) {
                    return pluginEntry
                }
            }
            return null
        }

    }

}