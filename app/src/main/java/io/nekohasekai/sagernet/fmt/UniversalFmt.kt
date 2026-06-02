/******************************************************************************
 *                                                                            *
 * Copyright (C) 2021 by nekohasekai <contact-sagernet@sekai.icu>             *
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

package io.nekohasekai.sagernet.fmt

import io.nekohasekai.sagernet.database.ProxyEntity
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlin.io.encoding.Base64

fun parseBackup(text: String): AbstractBean {
    val typeString = text.substringBefore("?")
    val type = TypeMap[typeString] ?: error("Type $typeString not found")
    val data = text.substringAfter("?")
    if (data.isEmpty()) {
        error("data is empty")
    }
    return ProxyEntity(type = type).apply {
        putByteArray(Base64.decode(data).zlibDecompress())
    }.requireBean()
}

fun AbstractBean.exportBackup(): String {
    return TypeMap.reversed[ProxyEntity().putBean(this).type] + "?" + Base64.encode(KryoConverters.serialize(this).zlibCompress(9))
}

fun ByteArray.zlibCompress(level: Int): ByteArray {
    // Compress the bytes
    // 1 to 4 bytes/char for UTF-8
    val output = ByteArray(size * 4)
    val compressor = Deflater(level).apply {
        setInput(this@zlibCompress)
        finish()
    }
    val compressedDataLength: Int = compressor.deflate(output)
    compressor.end()
    return output.copyOfRange(0, compressedDataLength)
}

fun ByteArray.zlibDecompress(): ByteArray {
    val inflater = Inflater()
    val outputStream = ByteArrayOutputStream()

    return outputStream.use {
        val buffer = ByteArray(1024)

        inflater.setInput(this)

        var count = -1
        while (count != 0) {
            count = inflater.inflate(buffer)
            outputStream.write(buffer, 0, count)
        }

        inflater.end()
        outputStream.toByteArray()
    }
}