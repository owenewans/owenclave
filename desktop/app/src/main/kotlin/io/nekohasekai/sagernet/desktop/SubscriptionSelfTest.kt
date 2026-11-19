package io.nekohasekai.sagernet.desktop

import io.nekohasekai.sagernet.fmt.AbstractBean
import java.net.URI
import java.util.Base64

/**
 * Functional self test for the subscription download path.
 *
 * The packaged application once shipped an incomplete jlink runtime: `java.net.http`
 * was missing from the application image, so [SubscriptionImporter] died with a
 * `NoClassDefFoundError` the first time a subscription was fetched. The unit tests
 * run on a full JDK and cannot see that, and no self test touched the download path
 * at all.
 *
 * The offline part is self contained: a tiny loopback HTTP server (the hand written
 * one from [SelfTest], so it works inside the jlink image, which has no
 * `jdk.httpserver`) serves a plain and a base64 encoded share link list, and the real
 * [SubscriptionImporter.download] plus [SubscriptionImporter.parse] pair is run
 * against it, exactly like `AppState.refreshSubscription` does. A runtime without
 * `java.net.http` fails here.
 *
 * `--selftest-subscription <url>` additionally runs the same code path against a real
 * subscription. Credentials are never printed, and the subscription URL itself is
 * masked to its scheme, host and the first two characters of the path.
 */
object SubscriptionSelfTest {

    /** Throwaway link: `aes-256-gcm:owenclave-sub-selftest`, not a real credential. */
    private const val SS_LINK =
        "ss://YWVzLTI1Ni1nY206b3dlbmNsYXZlLXN1Yi1zZWxmdGVzdA==@198.51.100.10:8388#selftest-ss"

    private const val VLESS_LINK =
        "vless://b831381d-6324-4d53-ad4f-8cda48b30811@198.51.100.11:443" +
            "?encryption=none&security=none&type=tcp#selftest-vless"

    /** The document the loopback servers answer with: two share links, one per line. */
    internal val sampleDocument: String get() = "$SS_LINK\n$VLESS_LINK"

    /**
     * Runs the offline check and, when [online] is given, the same code path against
     * that real subscription URL.
     *
     * @return `0` when every selected check passes, `1` otherwise.
     */
    fun run(online: String? = null): Int {
        var failed = 0
        if (runOffline() != 0) failed++
        online?.takeIf { it.isNotBlank() }?.let { if (runOnline(it) != 0) failed++ }
        if (failed == 0) {
            println("[selftest-subscription] PASS")
            return 0
        }
        println("[selftest-subscription] FAIL: $failed check(s) did not pass")
        return 1
    }

    /** Loopback HTTP + the real download/parse pair; no network and no secrets. */
    private fun runOffline(): Int {
        val plain = SelfTest.LocalHttpServer(sampleDocument)
        val encoded = Base64.getEncoder().encodeToString(sampleDocument.toByteArray(Charsets.UTF_8))
        val base64Server = SelfTest.LocalHttpServer(encoded)
        var problems = 0
        return try {
            println("[selftest-subscription] offline: checking the plain share link list")
            problems += checkDocument("plain list", "http://127.0.0.1:${plain.port}/")
            println("[selftest-subscription] offline: checking the base64 encoded list")
            problems += checkDocument("base64 list", "http://127.0.0.1:${base64Server.port}/")
            if (problems == 0) {
                println("[selftest-subscription] offline PASS")
                0
            } else {
                println("[selftest-subscription] offline FAIL: $problems problem(s)")
                1
            }
        } catch (error: Throwable) {
            // A missing java.net.http module surfaces as NoClassDefFoundError, an
            // Error rather than an Exception: report it instead of dying on a stack.
            println("[selftest-subscription] offline FAIL: ${reasonOf(error)}")
            1
        } finally {
            plain.stop()
            base64Server.stop()
        }
    }

    /** Downloads through [SubscriptionImporter] and asserts the parsed profiles. */
    private fun checkDocument(label: String, url: String): Int {
        val beans = downloadAndParse(url)
        val protocols = beans.groupingBy { it.javaClass.simpleName }.eachCount()
        println("[selftest-subscription] $label: ${beans.size} profile(s), protocols=$protocols")
        val problems = ArrayList<String>()
        if (beans.size != 2) problems += "expected 2 profiles, got ${beans.size}"
        if (protocols["ShadowsocksBean"] != 1) problems += "expected one shadowsocks profile"
        if (protocols["VLESSBean"] != 1) problems += "expected one vless profile"
        beans.forEach { bean -> problems += describeProblems(bean) }
        problems.forEach { println("[selftest-subscription] $label: $it") }
        return problems.size
    }

    /** Checks a real subscription; never prints its credentials or full URL. */
    private fun runOnline(url: String): Int {
        val masked = maskUrl(url)
        println("[selftest-subscription] online: $masked")
        return try {
            val beans = downloadAndParse(url)
            val protocols = beans.groupingBy { it.javaClass.simpleName.removeSuffix("Bean") }.eachCount()
            println("[selftest-subscription] online: ${beans.size} profile(s)")
            protocols.entries.sortedBy { it.key }.forEach { (protocol, count) ->
                println("[selftest-subscription] online:   $protocol: $count")
            }
            val sane = beans.count { it.serverAddress.isNotBlank() && it.serverPort in 1..65535 }
            val named = beans.count { it.displayName().isNotBlank() }
            println(
                "[selftest-subscription] online: sane host/port: $sane/${beans.size}, " +
                    "non-empty names: $named/${beans.size}"
            )
            if (beans.isEmpty()) {
                println("[selftest-subscription] online FAIL: no profile recognised in the document")
                1
            } else {
                println("[selftest-subscription] online PASS")
                0
            }
        } catch (error: Throwable) {
            println("[selftest-subscription] online FAIL: ${reasonOf(error, url)}")
            1
        }
    }

    /** Exactly the two calls `AppState.refreshSubscription` makes. */
    private fun downloadAndParse(url: String): List<AbstractBean> {
        val body = SubscriptionImporter.download(url)
        return SubscriptionImporter.parse(body)
    }

    private fun describeProblems(bean: AbstractBean): List<String> {
        val problems = ArrayList<String>()
        if (bean.serverAddress.isBlank()) {
            problems += "${bean.javaClass.simpleName}: empty server address"
        }
        if (bean.serverPort !in 1..65535) {
            problems += "${bean.javaClass.simpleName}: invalid server port ${bean.serverPort}"
        }
        return problems
    }

    /**
     * Scheme, host and the first two characters of the path only. User info, the rest
     * of the path, the query and the fragment (all of which can carry a secret) are
     * dropped.
     */
    internal fun maskUrl(url: String): String {
        val uri = runCatching { URI(url) }.getOrNull() ?: return "(unparseable URL)"
        val scheme = uri.scheme ?: return "(URL without a scheme)"
        val host = uri.host ?: return "$scheme://(no host)"
        return "$scheme://$host" + uri.rawPath.orEmpty().take(2)
    }

    /**
     * Flattens an exception chain without re-triggering a failed
     * [SubscriptionImporter] class initialisation, and replaces [secret] so a URL that
     * ended up in a message is never printed in clear.
     */
    private fun reasonOf(error: Throwable, secret: String? = null): String {
        val described = runCatching { SubscriptionImporter.describe(error) }
            .getOrElse { error.toString() }
        if (secret.isNullOrEmpty()) return described
        return described.replace(secret, maskUrl(secret))
    }

}
