package io.nekohasekai.sagernet.plugin.juicity

import android.net.Uri
import android.os.ParcelFileDescriptor
import io.nekohasekai.sagernet.plugin.NativePluginProvider
import io.nekohasekai.sagernet.plugin.PathProvider
import java.io.File
import java.io.FileNotFoundException

class BinaryProvider : NativePluginProvider() {
    override fun populateFiles(provider: PathProvider) {
        provider.addPath("juicity-plugin", 0b111101101)
    }

    override fun getExecutable() = context!!.applicationInfo.nativeLibraryDir + "/libjuicity.so"
    override fun openFile(uri: Uri): ParcelFileDescriptor = when (uri.path) {
        "/juicity-plugin" -> ParcelFileDescriptor.open(File(getExecutable()),
            ParcelFileDescriptor.MODE_READ_ONLY)
        else -> throw FileNotFoundException()
    }
}
