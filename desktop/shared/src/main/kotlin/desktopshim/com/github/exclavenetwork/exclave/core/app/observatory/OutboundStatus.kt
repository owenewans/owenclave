package com.github.exclavenetwork.exclave.core.app.observatory

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class OutboundStatus(
    val alive: Boolean = false,
    val delay: Long = 0L,
    val lastErrorReason: String = "",
    val outboundTag: String = "",
    val lastSeenTime: Long = 0L,
    val lastTryTime: Long = 0L,
) {
    fun toByteArray(): ByteArray {
        val out = ByteArrayOutputStream()
        fun writeVarint(v: Long) {
            var value = v
            while ((value and -0x80L) != 0L) {
                out.write(((value.toInt() and 0x7F) or 0x80))
                value = value ushr 7
            }
            out.write(value.toInt() and 0x7F)
        }
        fun writeString(fieldNumber: Int, s: String) {
            if (s.isEmpty()) return
            val bytes = s.toByteArray(StandardCharsets.UTF_8)
            writeVarint(((fieldNumber shl 3) or 2).toLong())
            writeVarint(bytes.size.toLong())
            out.write(bytes)
        }
        if (alive) {
            writeVarint(((1 shl 3) or 0).toLong())
            writeVarint(1L)
        }
        if (delay != 0L) {
            writeVarint(((2 shl 3) or 0).toLong())
            writeVarint(delay)
        }
        writeString(3, lastErrorReason)
        writeString(4, outboundTag)
        if (lastSeenTime != 0L) {
            writeVarint(((5 shl 3) or 0).toLong())
            writeVarint(lastSeenTime)
        }
        if (lastTryTime != 0L) {
            writeVarint(((6 shl 3) or 0).toLong())
            writeVarint(lastTryTime)
        }
        return out.toByteArray()
    }

    class Builder {
        var alive: Boolean = false
        var delay: Long = 0L
        var lastErrorReason: String = ""
        var outboundTag: String = ""
        var lastSeenTime: Long = 0L
        var lastTryTime: Long = 0L

        fun setAlive(value: Boolean) = apply { alive = value }
        fun setDelay(value: Long) = apply { delay = value }
        fun setLastErrorReason(value: String) = apply { lastErrorReason = value }
        fun setOutboundTag(value: String) = apply { outboundTag = value }
        fun setLastSeenTime(value: Long) = apply { lastSeenTime = value }
        fun setLastTryTime(value: Long) = apply { lastTryTime = value }

        fun build(): OutboundStatus = OutboundStatus(
            alive = alive,
            delay = delay,
            lastErrorReason = lastErrorReason,
            outboundTag = outboundTag,
            lastSeenTime = lastSeenTime,
            lastTryTime = lastTryTime,
        )
    }

    companion object {
        @JvmStatic
        fun newBuilder(): Builder = Builder()

        @JvmStatic
        fun parseFrom(data: ByteArray): OutboundStatus {
            val builder = Builder()
            var pos = 0
            fun readVarint(): Long {
                var result = 0L
                var shift = 0
                while (pos < data.size) {
                    val b = data[pos++].toInt()
                    result = result or ((b and 0x7F).toLong() shl shift)
                    if ((b and 0x80) == 0) return result
                    shift += 7
                }
                return result
            }
            while (pos < data.size) {
                val tag = readVarint().toInt()
                val fieldNumber = tag ushr 3
                val wireType = tag and 0x07
                when (wireType) {
                    0 -> {
                        val v = readVarint()
                        when (fieldNumber) {
                            1 -> builder.alive = (v != 0L)
                            2 -> builder.delay = v
                            5 -> builder.lastSeenTime = v
                            6 -> builder.lastTryTime = v
                        }
                    }
                    2 -> {
                        val len = readVarint().toInt()
                        if (pos + len <= data.size) {
                            val str = String(data, pos, len, StandardCharsets.UTF_8)
                            pos += len
                            when (fieldNumber) {
                                3 -> builder.lastErrorReason = str
                                4 -> builder.outboundTag = str
                            }
                        } else {
                            break
                        }
                    }
                    1 -> pos += 8
                    5 -> pos += 4
                    else -> break
                }
            }
            return builder.build()
        }
    }
}

