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

package io.nekohasekai.sagernet.group

import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.SubscriptionType
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.GroupManager
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.database.SubscriptionBean
import io.nekohasekai.sagernet.ktx.*
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

@Suppress("EXPERIMENTAL_API_USAGE")
abstract class GroupUpdater {

    abstract suspend fun doUpdate(
        proxyGroup: ProxyGroup,
        subscription: SubscriptionBean,
        userInterface: GroupManager.Interface?,
        byUser: Boolean
    )

    data class Progress(
        var max: Int
    ) {
        var progress by AtomicInteger()
    }

    companion object {

        val updating = Collections.synchronizedSet<Long>(mutableSetOf())
        val progress = Collections.synchronizedMap<Long, Progress>(mutableMapOf())

        fun startUpdate(proxyGroup: ProxyGroup, byUser: Boolean) {
            runOnDefaultDispatcher {
                executeUpdate(proxyGroup, byUser)
            }
        }

        suspend fun executeUpdate(proxyGroup: ProxyGroup, byUser: Boolean): Boolean {
            return coroutineScope {
                if (!updating.add(proxyGroup.id)) cancel()
                GroupManager.postReload(proxyGroup.id)

                val subscription = proxyGroup.subscription!!
                val connected = SagerNet.started && DataStore.startedProfile > 0
                val userInterface = GroupManager.userInterface

                if (subscription.updateWhenConnectedOnly && !connected) {
                    if (!byUser || userInterface == null) {
                        finishUpdate(proxyGroup)
                        cancel()
                    } else {
                        if (!userInterface.confirm(app.getString(R.string.update_subscription_warning))) {
                            finishUpdate(proxyGroup)
                            cancel()
                        }
                    }
                }

                try {
                    when (subscription.type) {
                        SubscriptionType.RAW -> RawUpdater
                        SubscriptionType.SIP008 -> SIP008Updater
                        SubscriptionType.AGE -> AgeUpdater
                        else -> error("unsupported")
                    }.doUpdate(proxyGroup, subscription, userInterface, byUser)
                    if (subscription.autoSwitchToNewest == true) {
                        switchToNewestProxy(proxyGroup)
                    }
                    true
                } catch (e: Throwable) {
                    Logs.w(e)
                    if (byUser && userInterface != null) {
                        userInterface.onUpdateFailure(proxyGroup, e.readableMessage)
                    }
                    finishUpdate(proxyGroup)
                    false
                }
            }
        }


        /**
         * Switches [DataStore.selectedProxy] to the newest profile in [proxyGroup] (the one
         * with the lowest userOrder, i.e. first in the order returned by the subscription
         * server) and reconnects the tunnel through it, if it isn't already selected.
         *
         * Used by subscriptions with a FIFO rotation scheme (e.g. short-lived rooms pushed
         * by an external rotator) where the freshest, most likely-to-be-alive profile should
         * always be the one in use after an update. See owenclave#9.
         */
        private suspend fun switchToNewestProxy(proxyGroup: ProxyGroup) {
            val newest = SagerDatabase.proxyDao.getByGroup(proxyGroup.id)
                .minByOrNull { it.userOrder } ?: return
            if (DataStore.selectedProxy == newest.id) return
            DataStore.selectedProxy = newest.id
            if (SagerNet.started) {
                SagerNet.reloadService()
            }
        }

        suspend fun finishUpdate(proxyGroup: ProxyGroup) {
            updating.remove(proxyGroup.id)
            progress.remove(proxyGroup.id)
            GroupManager.postUpdate(proxyGroup)
        }

    }

}