package androidx.core.content

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import java.io.File
import java.util.concurrent.Executor

object ContextCompat {
    const val RECEIVER_EXPORTED = 0x2
    const val RECEIVER_NOT_EXPORTED = 0x4

    @JvmStatic
    fun <T> getSystemService(context: Context, serviceClass: Class<T>): T? {
        return context.getSystemService(serviceClass)
    }

    @JvmStatic
    fun checkSelfPermission(context: Context, permission: String): Int =
        PackageManager.PERMISSION_GRANTED

    @JvmStatic
    fun getColor(context: Context, id: Int): Int = id

    @JvmStatic
    fun startForegroundService(context: Context, intent: Intent) {
        context.startService(intent)
    }

    @JvmStatic
    fun registerReceiver(
        context: Context,
        receiver: BroadcastReceiver?,
        filter: IntentFilter,
        flags: Int
    ): Intent? = context.registerReceiver(receiver, filter, flags)

    @JvmStatic
    fun registerReceiver(
        context: Context,
        receiver: BroadcastReceiver?,
        filter: IntentFilter,
        broadcastPermission: String?,
        scheduler: Handler?,
        flags: Int
    ): Intent? = context.registerReceiver(receiver, filter, broadcastPermission, scheduler, flags)

    @JvmStatic
    fun getMainExecutor(context: Context): Executor = Executor { it.run() }
}

inline fun <reified T : Any> Context.getSystemService(): T? =
    ContextCompat.getSystemService(this, T::class.java)

object FileProvider {
    @JvmStatic
    fun getUriForFile(context: Context, authority: String, file: File): Uri =
        Uri.fromFile(file)
}

