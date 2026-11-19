package androidx.core.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.PendingIntent
import android.content.Context

object NotificationCompat {
    const val PRIORITY_LOW = -1
    const val PRIORITY_DEFAULT = 0
    const val PRIORITY_HIGH = 1
    const val VISIBILITY_PUBLIC = 1
    const val VISIBILITY_PRIVATE = 0
    const val VISIBILITY_SECRET = -1
    const val CATEGORY_SERVICE = "service"

    class Action(
        val icon: Int,
        val title: CharSequence?,
        val actionIntent: PendingIntent?
    ) {
        class Builder(
            private val icon: Int,
            private val title: CharSequence?,
            private val intent: PendingIntent?
        ) {
            fun build(): Action = Action(icon, title, intent)
        }
    }

    class BigTextStyle {
        fun bigText(cs: CharSequence?): BigTextStyle = this
        fun setBigContentTitle(title: CharSequence?): BigTextStyle = this
    }

    class Builder(val context: Context, val channelId: String) {
        private val notification = Notification()
        fun setSmallIcon(icon: Int): Builder = this
        fun setContentTitle(title: CharSequence?): Builder {
            notification.contentTitle = title
            return this
        }
        fun setContentText(text: CharSequence?): Builder {
            notification.contentText = text
            return this
        }
        fun setSubText(text: CharSequence?): Builder {
            notification.subText = text
            return this
        }
        fun setContentIntent(intent: PendingIntent?): Builder = this
        fun setOngoing(ongoing: Boolean): Builder = this
        fun setShowWhen(show: Boolean): Builder = this
        fun setWhen(whenMs: Long): Builder = this
        fun setPriority(priority: Int): Builder = this
        fun setVisibility(visibility: Int): Builder = this
        fun setCategory(category: String?): Builder = this
        fun setColor(color: Int): Builder = this
        fun setSilent(silent: Boolean): Builder = this
        fun setAutoCancel(autoCancel: Boolean): Builder = this
        fun setOnlyAlertOnce(onlyAlertOnce: Boolean): Builder = this
        fun setGroup(groupKey: String?): Builder = this
        fun setStyle(style: Any?): Builder = this
        fun setProgress(max: Int, progress: Int, indeterminate: Boolean): Builder = this
        fun addAction(icon: Int, title: CharSequence?, intent: PendingIntent?): Builder = this
        fun addAction(action: Action?): Builder = this
        fun clearActions(): Builder = this
        fun setForegroundServiceBehavior(behavior: Int): Builder = this
        fun build(): Notification = notification
    }

    const val FOREGROUND_SERVICE_IMMEDIATE = 1
}

class NotificationManagerCompat private constructor(private val context: Context) {
    fun notify(id: Int, notification: Notification) {}
    fun cancel(id: Int) {}
    fun cancelAll() {}
    fun createNotificationChannel(channel: NotificationChannel) {}
    fun areNotificationsEnabled(): Boolean = true

    companion object {
        @JvmStatic
        fun from(context: Context): NotificationManagerCompat = NotificationManagerCompat(context)
    }
}

