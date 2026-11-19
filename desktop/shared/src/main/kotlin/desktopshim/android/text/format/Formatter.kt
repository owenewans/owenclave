package android.text.format

import android.content.Context
import io.nekohasekai.sagernet.utils.FormatFileSize

object Formatter {
    @JvmStatic
    fun formatFileSize(context: Context?, sizeBytes: Long): String =
        FormatFileSize.formatFileSize(context, sizeBytes)

    @JvmStatic
    fun formatShortFileSize(context: Context?, sizeBytes: Long): String =
        FormatFileSize.formatShortFileSize(context, sizeBytes)
}

