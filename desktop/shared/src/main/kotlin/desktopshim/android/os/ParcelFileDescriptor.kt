package android.os

import java.io.Closeable

class ParcelFileDescriptor(val fd: Int = -1) : Closeable {
    fun detachFd(): Int = fd
    override fun close() {}
}

