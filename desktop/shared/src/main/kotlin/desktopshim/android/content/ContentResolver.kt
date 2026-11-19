package android.content

import android.net.Uri
import android.os.Bundle
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

class ContentResolver {
    companion object {
        const val SCHEME_CONTENT = "content"
    }

    fun openInputStream(uri: Uri): InputStream? {
        val file = uri.toFileOrNull() ?: return null
        return if (file.isFile) FileInputStream(file) else null
    }

    fun openOutputStream(uri: Uri): OutputStream? {
        val file = uri.toFileOrNull() ?: return null
        file.parentFile?.mkdirs()
        return FileOutputStream(file)
    }

    fun call(uri: Uri, method: String, arg: String?, extras: Bundle?): Bundle? = null
}
