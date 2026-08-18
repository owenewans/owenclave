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

package io.nekohasekai.sagernet.ktx

import android.os.Build
import io.nekohasekai.sagernet.database.DataStore
import java.util.UUID

/**
 * Device HWID reporting, per the Remnawave HWID Device Limit protocol:
 * https://docs.rw/features/hwid-device-limit
 *
 * The HWID is a client-owned, locally persisted identifier (not derived from
 * hardware serials), so [resetHwid] doubles as the user-facing "new device
 * identity" action for panels that enforce a per-user device limit.
 */
object Hwid {

    // Panel-side validation (Remnawave v3.0.0+): /^[a-zA-Z0-9=-]{10,64}$/
    private fun generate(): String = UUID.randomUUID().toString().replace("-", "")

    /** Returns the persisted device HWID, generating one on first use. */
    fun current(): String {
        var value = DataStore.hwidValue
        if (value.isEmpty()) {
            value = generate()
            DataStore.hwidValue = value
        }
        return value
    }

    /** Regenerates the persisted HWID, presenting this device as a new one. */
    fun resetHwid(): String {
        val value = generate()
        DataStore.hwidValue = value
        return value
    }

    /**
     * Headers to attach to subscription requests when HWID reporting is
     * enabled in settings. Empty when disabled.
     */
    fun headers(): Map<String, String> {
        if (!DataStore.sendHwid) return emptyMap()
        return mapOf(
            "x-hwid" to current(),
            "x-device-os" to "Android",
            "x-ver-os" to Build.VERSION.RELEASE,
            "x-device-model" to Build.MODEL,
        )
    }

}
