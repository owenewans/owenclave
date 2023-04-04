package com.termux.terminal

/** A circular byte buffer allowing one producer and one consumer thread.  */
class ByteQueue(size: Int) {

    private val mBuffer: ByteArray
    private var mHead = 0
    private var mStoredBytes = 0
    private var mOpen = true

    init {
        mBuffer = ByteArray(size)
    }

    @Synchronized
    fun close() {
        mOpen = false
        (this as Object).notify()
    }

    @Synchronized
    fun read(buffer: ByteArray, block: Boolean): Int {
        while (mStoredBytes == 0 && mOpen) {
            if (block) {
                try {
                    (this as Object).wait()
                } catch (e: InterruptedException) {
                    // Ignore.
                }
            } else {
                return 0
            }
        }
        if (!mOpen) return -1
        var totalRead = 0
        val bufferLength = mBuffer.size
        val wasFull = bufferLength == mStoredBytes
        var length = buffer.size
        var offset = 0
        while (length > 0 && mStoredBytes > 0) {
            val oneRun = (bufferLength - mHead).coerceAtMost(mStoredBytes)
            val bytesToCopy = length.coerceAtMost(oneRun)
            System.arraycopy(mBuffer, mHead, buffer, offset, bytesToCopy)
            mHead += bytesToCopy
            if (mHead >= bufferLength) mHead = 0
            mStoredBytes -= bytesToCopy
            length -= bytesToCopy
            offset += bytesToCopy
            totalRead += bytesToCopy
        }
        if (wasFull) (this as Object).notify()
        return totalRead
    }

    /**
     * Attempt to write the specified portion of the provided buffer to the queue.
     *
     *
     * Returns whether the output was totally written, false if it was closed before.
     */
    fun write(buffer: ByteArray, offset: Int, lengthToWrite: Int): Boolean {

        require(lengthToWrite + offset <= buffer.size) { "length + offset > buffer.size" }
        require(lengthToWrite > 0) { "length <= 0" }

        val bufferLength = mBuffer.size
        synchronized(this) {
            var srcPos = offset
            var toWrite = lengthToWrite

            while (toWrite > 0) {
                while (bufferLength == mStoredBytes && mOpen) {
                    try {
                        (this as Object).wait()
                    } catch (e: InterruptedException) {
                        // Ignore.
                    }
                }
                if (!mOpen) return false
                val wasEmpty = mStoredBytes == 0
                var bytesToWriteBeforeWaiting = toWrite.coerceAtMost(bufferLength - mStoredBytes)
                toWrite -= bytesToWriteBeforeWaiting
                while (bytesToWriteBeforeWaiting > 0) {
                    var tail = mHead + mStoredBytes
                    var oneRun: Int
                    if (tail >= bufferLength) {
                        // Buffer: [.............]
                        // ________________H_______T
                        // =>
                        // Buffer: [.............]
                        // ___________T____H
                        // onRun= _____----_
                        tail -= bufferLength
                        oneRun = mHead - tail
                    } else {
                        oneRun = bufferLength - tail
                    }
                    val bytesToCopy = oneRun.coerceAtMost(bytesToWriteBeforeWaiting)
                    System.arraycopy(buffer, srcPos, mBuffer, tail, bytesToCopy)
                    srcPos += bytesToCopy
                    bytesToWriteBeforeWaiting -= bytesToCopy
                    mStoredBytes += bytesToCopy
                }
                if (wasEmpty) (this as Object).notify()
            }
        }
        return true
    }
}