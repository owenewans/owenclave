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

import com.google.gson.JsonParser.parseReader
import com.google.gson.stream.JsonReader
import okhttp3.OkHttpClient
import okhttp3.Request
import org.gradle.api.Project
import java.io.File
import java.io.StringReader
import java.security.MessageDigest

fun Project.downloadAssets(update: Boolean = false) {
    val assetsDir = File(projectDir, "src/main/assets")
    val downloader = OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).build()

    val geoipVersionFile = File(assetsDir, "owenclave-core/geoip.version.txt")
    var geoipVersion: String
    var geoipDat: String
    var geoipDatSha256sum: String
    if (update) {
        val body = downloader.newCall(
            Request.Builder().url("https://api.github.com/repos/v2fly/geoip/releases/latest").build()
        ).execute().body
        val jsonObject = parseReader(JsonReader(StringReader(body.string())))?.asJsonObject
        body.close()
        jsonObject ?: error("unable to list geoip release")
        geoipVersion = jsonObject.get("tag_name")?.asString
            ?: error("unable to list geoip release")
        val assets = jsonObject.getAsJsonArray("assets")
        geoipDat = assets.find { it?.asJsonObject?.get("name")?.asString == "geoip.dat" }
            ?.asJsonObject?.get("browser_download_url")?.asString
            ?: error("unable to list geoip release")
        geoipDatSha256sum = assets.find { it?.asJsonObject?.get("name")?.asString == "geoip.dat.sha256sum" }
            ?.asJsonObject?.get("browser_download_url")?.asString
            ?: error("unable to list geoip release")
    } else {
        geoipVersion = geoipVersionFile.readText().trim()
        geoipDat = "https://github.com/v2fly/geoip/releases/download/${geoipVersion}/geoip.dat"
        geoipDatSha256sum = "https://github.com/v2fly/geoip/releases/download/${geoipVersion}/geoip.dat.sha256sum"
    }

    val geoipFile = File(assetsDir, "owenclave-core/geoip.dat")
    geoipFile.parentFile.mkdirs()

    println("Downloading $geoipDatSha256sum ...")

    var body = downloader.newCall(Request.Builder().url(geoipDatSha256sum).build()).execute().body
    val geoipChecksum = body.string().trim().substringBefore(" ")
    body.close()

    var count = 0
    while (true) {
        count++

        println("Downloading $geoipDat ...")

        body = downloader.newCall(Request.Builder().url(geoipDat).build()).execute().body
        body.byteStream().use {
            geoipFile.outputStream().use { out -> it.copyTo(out) }
        }

        val messageDigest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(1024)
        geoipFile.inputStream().use { fis ->
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                messageDigest.update(buffer, 0, bytesRead)
            }
        }
        body.close()

        val fileSha256 = messageDigest.digest().toHexString()
        if (fileSha256 != geoipChecksum.lowercase()) {
            System.err.println(
                "Error verifying ${geoipFile.name}: \nLocal: $fileSha256\nRemote: ${geoipChecksum.lowercase()}"
            )
            if (count > 3) error("Exit")
            System.err.println("Retrying...")
            continue
        }

        if (update) {
            geoipVersionFile.writeText(geoipVersion)
        }

        break
    }

    val geositeVersionFile = File(assetsDir, "owenclave-core/geosite.version.txt")
    var geositeVersion: String
    var geositeDat: String
    var geositeDatSha256sum: String
    if (update) {
        val body = downloader.newCall(
            Request.Builder().url("https://api.github.com/repos/v2fly/domain-list-community/releases/latest").build()
        ).execute().body
        val jsonObject = parseReader(JsonReader(StringReader(body.string())))?.asJsonObject
        body.close()
        jsonObject ?: error("unable to list geosite release")
        geositeVersion = jsonObject.get("tag_name")?.asString
            ?: error("unable to list geosite release")
        val assets = jsonObject.getAsJsonArray("assets")
        geositeDat = assets.find { it?.asJsonObject?.get("name")?.asString == "dlc.dat" }
            ?.asJsonObject?.get("browser_download_url")?.asString
            ?: error("unable to list geosite release")
        geositeDatSha256sum = assets.find { it?.asJsonObject?.get("name")?.asString == "dlc.dat.sha256sum" }
            ?.asJsonObject?.get("browser_download_url")?.asString
            ?: error("unable to list geosite release")
    } else {
        geositeVersion = geositeVersionFile.readText().trim()
        geositeDat = "https://github.com/v2fly/domain-list-community/releases/download/${geositeVersion}/dlc.dat"
        geositeDatSha256sum = "https://github.com/v2fly/domain-list-community/releases/download/${geositeVersion}/dlc.dat.sha256sum"
    }

    val geositeFile = File(assetsDir, "owenclave-core/geosite.dat")

    println("Downloading $geositeDatSha256sum ...")

    body = downloader.newCall(
        Request.Builder().url(geositeDatSha256sum).build()
    ).execute().body
    val geositeChecksum = body.string().trim().substringBefore(" ")
    body.close()

    count = 0
    while (true) {
        count++

        println("Downloading $geositeDat ...")

        body = downloader.newCall(Request.Builder().url(geositeDat).build()).execute().body
        body.byteStream().use {
            geositeFile.outputStream().use { out -> it.copyTo(out) }
        }

        val messageDigest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(1024)
        geositeFile.inputStream().use { fis ->
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                messageDigest.update(buffer, 0, bytesRead)
            }
        }
        body.close()

        val fileSha256 = messageDigest.digest().toHexString()
        if (fileSha256 != geositeChecksum.lowercase()) {
            System.err.println(
                "Error verifying ${geositeFile.name}: \nLocal: $fileSha256\nRemote: ${geositeChecksum.lowercase()}"
            )
            if (count > 3) error("Exit")
            System.err.println("Retrying...")
            continue
        }

        if (update) {
            geositeVersionFile.writeText(geositeVersion)
        }

        break
    }

}