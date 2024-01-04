/******************************************************************************
 * Copyright (C) 2021 by nekohasekai <contact-git@sekai.icu>                  *
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

package io.nekohasekai.sagernet.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.ScrollView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import cn.hutool.core.util.RuntimeUtil
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.databinding.LayoutLogcatBinding
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.utils.CrashHandler
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

class LogcatFragment : ToolbarFragment(R.layout.layout_logcat),
    Toolbar.OnMenuItemClickListener {

    lateinit var binding: LayoutLogcatBinding
    var fontSize = dp2px(8)

    @SuppressLint("RestrictedApi")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar.setTitle(R.string.menu_log)

        toolbar.inflateMenu(R.menu.logcat_menu)
        toolbar.setOnMenuItemClickListener(this)

        binding = LayoutLogcatBinding.bind(view)
        // Make it divisible by 2 since that is the minimal adjustment step:
        if (fontSize % 2 == 1) fontSize--

        reloadSession()
    }

    fun reloadSession() {
        binding.textView.text = "Not Implemented" // TODO

        binding.textView.post {
            binding.scrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_clear_logcat -> {
                runOnDefaultDispatcher {
                    try {
                        RuntimeUtil.exec("/system/bin/logcat", "-c").waitFor()
                    } catch (e: Exception) {
                        onMainDispatcher {
                            snackbar(e.readableMessage).show()
                        }
                        return@runOnDefaultDispatcher
                    }
                    onMainDispatcher {
                        reloadSession()
                    }
                }

            }
            R.id.action_send_logcat -> {
                val context = requireContext()

                runOnDefaultDispatcher {
                    val logFile = File.createTempFile("SagerNet ",
                        ".log",
                        File(app.cacheDir, "log").also { it.mkdirs() })

                    var report = CrashHandler.buildReportHeader()

                    report += "Logcat: \n\n"

                    logFile.writeText(report)

                    try {
                        Runtime.getRuntime().exec(arrayOf("logcat", "-d")).inputStream.use(
                            FileOutputStream(
                                logFile, true
                            )
                        )
                    } catch (e: IOException) {
                        Logs.w(e)
                        logFile.appendText("Export logcat error: " + CrashHandler.formatThrowable(e))
                    }

                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).setType("text/x-log")
                                .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                .putExtra(
                                    Intent.EXTRA_STREAM, FileProvider.getUriForFile(
                                        context, BuildConfig.APPLICATION_ID + ".cache", logFile
                                    )
                                ), context.getString(R.string.abc_shareactionprovider_share_with)
                        )
                    )
                }
            }
        }
        return true
    }

    companion object {
        private val MIN_FONTSIZE = dp2px(4)
        private val MAX_FONTSIZE = dp2px(12)
    }
}