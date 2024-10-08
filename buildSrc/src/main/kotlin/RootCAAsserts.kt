import cn.hutool.crypto.digest.DigestUtil
import cn.hutool.http.HttpUtil
import org.gradle.api.Project
import java.io.File

fun Project.downloadRootCAList() {
    val assets = File(projectDir, "src/main/assets")
    val pem = File(assets, "mozilla_included.pem")
    val pemSha256 = File(assets, "mozilla_included.pem.sha256sum")
    val data = HttpUtil.get("https://ccadb.my.salesforce-sites.com/mozilla/IncludedRootsPEMTxt?TrustBitsInclude=Websites")
        ?: error("download mozilla_included.pem failed")
    val dataSha256 = DigestUtil.sha256Hex(data)
    if (!pem.isFile || !pemSha256.isFile || pemSha256.readText() != dataSha256) {
        pem.writeText(data)
        pemSha256.writeText(dataSha256)
    }
}
