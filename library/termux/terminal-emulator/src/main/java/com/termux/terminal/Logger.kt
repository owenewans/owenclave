/******************************************************************************
 * Copyright (C) 2023 by LeafStative <leafstative@gmail.com>                  *
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

package com.termux.terminal

import android.util.Log
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter

object Logger {

    fun logError(client: TerminalSessionClient?, logTag: String?, message: String?) {
        client?.logError(logTag, message) ?: Log.e(logTag, message!!)
    }

    fun logWarn(client: TerminalSessionClient?, logTag: String?, message: String?) {
        client?.logWarn(logTag, message) ?: Log.w(logTag, message!!)
    }

    fun logInfo(client: TerminalSessionClient?, logTag: String?, message: String?) {
        client?.logInfo(logTag, message) ?: Log.i(logTag, message!!)
    }

    fun logDebug(client: TerminalSessionClient?, logTag: String?, message: String?) {
        client?.logDebug(logTag, message) ?: Log.d(logTag, message!!)
    }

    fun logVerbose(client: TerminalSessionClient?, logTag: String?, message: String?) {
        client?.logVerbose(logTag, message) ?: Log.v(logTag, message!!)
    }

    fun logStackTraceWithMessage(
        client: TerminalSessionClient?, tag: String?, message: String?, throwable: Throwable?
    ) {
        logError(client, tag, getMessageAndStackTraceString(message, throwable))
    }

    fun getMessageAndStackTraceString(message: String?, throwable: Throwable?): String? {
        return if (message == null && throwable == null) null else if (message != null && throwable != null) "$message:\n" + getStackTraceString(
            throwable
        ) else if (throwable == null) message else getStackTraceString(
            throwable
        )
    }

    fun getStackTraceString(throwable: Throwable?): String? {
        if (throwable == null) return null
        var stackTraceString: String? = null
        try {
            val errors = StringWriter()
            val pw = PrintWriter(errors)
            throwable.printStackTrace(pw)
            pw.close()
            stackTraceString = errors.toString()
            errors.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return stackTraceString
    }
}