package android.app

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.IBinder
import android.os.LocaleList

open class Application : ContextWrapper() {
    open fun onCreate() {}
    open fun onConfigurationChanged(newConfig: Configuration) {}

    companion object {
        @JvmStatic
        fun getProcessName(): String = io.nekohasekai.sagernet.BuildConfig.APPLICATION_ID
    }
}

abstract class Service : ContextWrapper() {
    companion object {
        const val START_STICKY = 1
        const val START_NOT_STICKY = 2
    }

    open fun onCreate() {}
    open fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY
    open fun onBind(intent: Intent): IBinder? = null
    open fun onDestroy() {}
    open fun stopSelf() {}
    open fun stopForeground(removeNotification: Boolean) {}
}

class PendingIntent {
    companion object {
        const val FLAG_UPDATE_CURRENT = 0x08000000
        const val FLAG_IMMUTABLE = 0x04000000

        @JvmStatic
        fun getActivity(context: Context, requestCode: Int, intent: Intent, flags: Int): PendingIntent = PendingIntent()

        @JvmStatic
        fun getBroadcast(context: Context, requestCode: Int, intent: Intent, flags: Int): PendingIntent = PendingIntent()

        @JvmStatic
        fun getService(context: Context, requestCode: Int, intent: Intent, flags: Int): PendingIntent = PendingIntent()
    }
}

class Notification {
    var contentTitle: CharSequence? = null
    var contentText: CharSequence? = null
    var subText: CharSequence? = null
}

class NotificationChannel(val id: String, val name: CharSequence, val importance: Int)

class NotificationManager {
    companion object {
        const val IMPORTANCE_NONE = 0
        const val IMPORTANCE_MIN = 1
        const val IMPORTANCE_LOW = 2
        const val IMPORTANCE_DEFAULT = 3
        const val IMPORTANCE_HIGH = 4
    }

    fun createNotificationChannel(channel: NotificationChannel) {}
    fun createNotificationChannels(channels: List<NotificationChannel>) {}
    fun notify(id: Int, notification: Notification) {}
    fun cancel(id: Int) {}
}

class ActivityManager

class UiModeManager {
    val currentModeType: Int = Configuration.UI_MODE_TYPE_NORMAL
}

class LocaleManager {
    val systemLocales: LocaleList = LocaleList.getDefault()
}

