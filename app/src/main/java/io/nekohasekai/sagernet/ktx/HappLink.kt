package io.nekohasekai.sagernet.ktx

import com.google.gson.Gson
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Decryption for `happ://crypt`, `crypt2`, `crypt3`, `crypt4` and `crypt5`
 * deep links, as used by the Happ Android client (`su.happ.proxyutility`) to
 * hide a subscription's real URL behind an RSA / ChaCha20-Poly1305-wrapped
 * opaque token.
 *
 * The scheme was recovered by decompiling Happ v4.3.0 (jadx) and
 * cross-checking the result against the independent, already-public
 * write-ups of the same format:
 *  - https://github.com/LeeeeT/happ-decryptor (JS reference implementation
 *    + the 36 published `crypt5` marker keys bundled in `happ-keys.json`)
 *  - https://github.com/amurcanov/happ-decrypt-universal (independent
 *    confirmation of the RSA -> ChaCha20-Poly1305 pipeline)
 *
 * Deliberately pure JVM crypto (`java.security` / `javax.crypto`) with a
 * single small-footprint dependency (Gson, already used elsewhere in this
 * app) so it needs no network access, no native library, and no Android
 * `Context` -- it is fully unit-testable on the plain JVM.
 */
object HappLink {

    private val CRYPT_PREFIXES = listOf("crypt5/", "crypt4/", "crypt3/", "crypt2/", "crypt/")

    /** True for any `happ://crypt*` link this class knows how to attempt decrypting. */
    fun isHappCryptLink(link: String): Boolean {
        if (!link.startsWith("happ://", ignoreCase = true)) return false
        val path = link.substring("happ://".length)
        return CRYPT_PREFIXES.any { path.startsWith(it, ignoreCase = true) }
    }

    /**
     * Returns the plain URL/text behind a `happ://crypt*` link, or `null` if
     * [link] doesn't look like one, its marker/key isn't recognised, or the
     * payload fails to decrypt/authenticate. Never throws.
     */
    fun decrypt(link: String): String? {
        if (!link.startsWith("happ://", ignoreCase = true)) return null
        val path = link.substring("happ://".length)
        return try {
            when {
                path.startsWith("crypt5/", ignoreCase = true) -> decryptCrypt5(path.substring(7))
                path.startsWith("crypt4/", ignoreCase = true) -> decryptLegacy(3, keys.crypt4, path.substring(7))
                path.startsWith("crypt3/", ignoreCase = true) -> decryptLegacy(2, keys.crypt3, path.substring(7))
                path.startsWith("crypt2/", ignoreCase = true) -> decryptLegacy(1, keys.crypt2, path.substring(7))
                path.startsWith("crypt/", ignoreCase = true) -> decryptLegacy(0, keys.crypt, path.substring(6))
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    // ---------------------------------------------------------------------
    // Embedded key material
    // ---------------------------------------------------------------------

    /** `crypt`/`crypt2`/`crypt3`/`crypt4` are PKCS#1 RSA keys (one each); `crypt5` is 36 PKCS#8 keys keyed by an 8-char "marker". */
    private data class KeyBundle(
        val crypt: String,
        val crypt2: String,
        val crypt3: String,
        val crypt4: String,
        val crypt5: Map<String, String>,
    )

    private val keys: KeyBundle by lazy {
        val stream = HappLink::class.java.getResourceAsStream("/happ/happ-keys.json")
            ?: error("happ/happ-keys.json resource missing from classpath")
        stream.use { input ->
            Gson().fromJson(input.reader(StandardCharsets.UTF_8), KeyBundle::class.java)
        }
    }

    private val crypt5KeyCache = ConcurrentHashMap<String, PrivateKey>()
    private val legacyKeyCache = ConcurrentHashMap<Int, PrivateKey>()

    private fun crypt5Key(marker: String): PrivateKey = crypt5KeyCache.getOrPut(marker) {
        val b64 = keys.crypt5[marker] ?: throw IllegalArgumentException("unknown crypt5 marker: $marker")
        val der = Base64.getMimeDecoder().decode(b64)
        KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(der))
    }

    private fun legacyKey(ordinal: Int, keyB64: String): PrivateKey = legacyKeyCache.getOrPut(ordinal) {
        val pkcs1Der = Base64.getMimeDecoder().decode(keyB64)
        val pkcs8Der = wrapPkcs1ToPkcs8(pkcs1Der)
        KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(pkcs8Der))
    }

    // ---------------------------------------------------------------------
    // crypt5: RSA(marker key) -> ChaCha20-Poly1305, wrapped in two byte
    // shuffles recovered from the Java side of the Happ app.
    //
    //   swapBlockHalves(payload)                 // every 4 bytes: ABCD -> CDAB
    //   marker = shuffled[0:4] + shuffled[-4:]    // selects the RSA key
    //   body   = shuffled[4:-4]
    //   body   = nonce(12) [+ salt(8) at offset 14, "salted" layout only]
    //            + ASCII-decimal segment length
    //            + 0x01
    //            + Base64(ChaCha20-Poly1305 ciphertext)
    //            + Base64(RSA-PKCS1v1.5(ChaCha20 key))
    //   RSA plaintext and the ChaCha20-Poly1305 plaintext are themselves
    //   Base64 text with every adjacent character pair swapped.
    // ---------------------------------------------------------------------

    private fun decryptCrypt5(payload: String): String {
        val shuffled = swapBlockHalves(payload.toByteArray(StandardCharsets.UTF_8))
        require(shuffled.size >= 8) { "crypt5 payload is too short" }

        val markerBytes = ByteArray(8)
        System.arraycopy(shuffled, 0, markerBytes, 0, 4)
        System.arraycopy(shuffled, shuffled.size - 4, markerBytes, 4, 4)
        val marker = String(markerBytes, StandardCharsets.US_ASCII)

        val privateKey = crypt5Key(marker)
        val body = shuffled.copyOfRange(4, shuffled.size - 4)
        val preferSalted = body.size > 12 && !isAsciiDigit(body[12])

        var firstError: Exception? = null
        for (salted in listOf(preferSalted, !preferSalted)) {
            try {
                return decryptCrypt5Body(body, privateKey, salted)
            } catch (e: Exception) {
                if (firstError == null) firstError = e
            }
        }
        throw firstError ?: IllegalStateException("crypt5 decryption failed")
    }

    private fun decryptCrypt5Body(body: ByteArray, privateKey: PrivateKey, salted: Boolean): String {
        require(body.size >= 13) { "crypt5 body is too short" }
        val nonce = body.copyOfRange(0, 12)

        var salt: ByteArray? = null
        var lengthStart = 12
        if (salted) {
            require(body.size >= 22) { "crypt5 salted header is too short" }
            salt = body.copyOfRange(14, 22)
            lengthStart = 22
        }

        var lengthEnd = lengthStart
        while (lengthEnd < body.size && isAsciiDigit(body[lengthEnd])) lengthEnd++
        require(lengthEnd != lengthStart) { "crypt5 segment length is missing" }

        val segmentLength = String(body, lengthStart, lengthEnd - lengthStart, StandardCharsets.US_ASCII).toInt()
        val packed = body.copyOfRange(lengthEnd, body.size)
        require(packed.isNotEmpty() && segmentLength <= packed.size - 1) { "crypt5 segment is truncated" }

        val encryptedUrlAscii = packed.copyOfRange(1, segmentLength + 1)
        val rsaCiphertextAscii = packed.copyOfRange(segmentLength + 1, packed.size)
        val rsaCiphertext = decodeBase64Ascii(rsaCiphertextAscii)

        val rsaPlaintext = rsaPkcs1Decrypt(privateKey, rsaCiphertext)
        val rsaValue = decodeBase64Ascii(swapAdjacent(rsaPlaintext))
        require(rsaValue.size == 32) { "unexpected crypt5 key length: ${rsaValue.size}" }

        val chachaKey = rsaValue.copyOf()
        if (salt != null) {
            for (i in chachaKey.indices) {
                chachaKey[i] = (chachaKey[i].toInt() xor salt[i % salt.size].toInt()).toByte()
            }
        }

        val ciphertext = decodeBase64Ascii(encryptedUrlAscii)
        val intermediate = chaCha20Poly1305Decrypt(chachaKey, nonce, ciphertext)

        val plaintext = decodeBase64Ascii(swapAdjacent(intermediate))
        return String(plaintext, StandardCharsets.UTF_8)
    }

    // ---------------------------------------------------------------------
    // crypt / crypt2 / crypt3 / crypt4: plain Base64 payload, split into
    // RSA-modulus-sized chunks, each decrypted independently with RSA
    // PKCS#1 v1.5 (no chaining/IV).
    // ---------------------------------------------------------------------

    private fun decryptLegacy(ordinal: Int, keyB64: String, payload: String): String {
        val privateKey = legacyKey(ordinal, keyB64)
        val keySize = ((privateKey as RSAPrivateKey).modulus.bitLength() + 7) / 8
        val cipherBytes = decodeBase64Url(payload)

        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, privateKey)
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < cipherBytes.size) {
            val end = minOf(i + keySize, cipherBytes.size)
            out.writeBytes(cipher.doFinal(cipherBytes, i, end - i))
            i += keySize
        }
        return String(out.toByteArray(), StandardCharsets.UTF_8)
    }

    // ---------------------------------------------------------------------
    // Shared byte-shuffle / Base64 / cipher helpers
    // ---------------------------------------------------------------------

    private fun isAsciiDigit(b: Byte) = b in '0'.code.toByte()..'9'.code.toByte()

    /** Swaps every adjacent byte pair: `b0 b1 b2 b3 ... -> b1 b0 b3 b2 ...`. Self-inverse. */
    private fun swapAdjacent(bytes: ByteArray): ByteArray {
        val out = bytes.copyOf()
        var i = 0
        while (i + 1 < out.size) {
            val tmp = out[i]
            out[i] = out[i + 1]
            out[i + 1] = tmp
            i += 2
        }
        return out
    }

    /** Swaps the two halves of every complete 4-byte block: `A B C D -> C D A B`. Self-inverse. */
    private fun swapBlockHalves(bytes: ByteArray): ByteArray {
        val out = bytes.copyOf()
        val full = out.size - out.size % 4
        var i = 0
        while (i < full) {
            val a = out[i]
            val b = out[i + 1]
            out[i] = out[i + 2]
            out[i + 1] = out[i + 3]
            out[i + 2] = a
            out[i + 3] = b
            i += 4
        }
        return out
    }

    /** Decodes a Base64 string that may use the URL-safe alphabet and/or be missing `=` padding. */
    private fun decodeBase64Url(value: String): ByteArray {
        var clean = value.replace(Regex("\\s"), "").replace('-', '+').replace('_', '/')
        clean = clean.trimEnd('=')
        val pad = (4 - clean.length % 4) % 4
        return Base64.getDecoder().decode(clean + "=".repeat(pad))
    }

    private fun decodeBase64Ascii(bytes: ByteArray): ByteArray =
        decodeBase64Url(String(bytes, StandardCharsets.ISO_8859_1))

    private fun rsaPkcs1Decrypt(key: PrivateKey, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, key)
        return cipher.doFinal(ciphertext)
    }

    private fun chaCha20Poly1305Decrypt(key: ByteArray, nonce: ByteArray, ciphertextAndTag: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("ChaCha20-Poly1305")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "ChaCha20"), IvParameterSpec(nonce))
        return cipher.doFinal(ciphertextAndTag)
    }

    // ---------------------------------------------------------------------
    // PKCS#1 -> PKCS#8 DER wrapping: java.security.KeyFactory only accepts
    // PKCS#8, but the legacy crypt/crypt2/crypt3/crypt4 keys are raw PKCS#1.
    //
    //   PrivateKeyInfo ::= SEQUENCE {
    //     version               INTEGER (0),
    //     privateKeyAlgorithm   AlgorithmIdentifier { rsaEncryption, NULL },
    //     privateKey            OCTET STRING (the PKCS#1 DER bytes)
    //   }
    // ---------------------------------------------------------------------

    // SEQUENCE { OID 1.2.840.113549.1.1.1 (rsaEncryption), NULL }
    private val RSA_ALGORITHM_ID = byteArrayOf(
        0x30, 0x0D,
        0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x01,
        0x05, 0x00,
    )

    private fun wrapPkcs1ToPkcs8(pkcs1Der: ByteArray): ByteArray {
        val version = byteArrayOf(0x02, 0x01, 0x00)
        val privateKeyOctetString = derTlv(0x04, pkcs1Der)
        val body = version + RSA_ALGORITHM_ID + privateKeyOctetString
        return derTlv(0x30, body)
    }

    private fun derTlv(tag: Int, content: ByteArray): ByteArray {
        val length = derLength(content.size)
        return byteArrayOf(tag.toByte()) + length + content
    }

    private fun derLength(len: Int): ByteArray {
        if (len < 0x80) return byteArrayOf(len.toByte())
        var numBytes = 1
        var tmp = len
        while (true) {
            tmp = tmp shr 8
            if (tmp == 0) break
            numBytes++
        }
        val out = ByteArray(1 + numBytes)
        out[0] = (0x80 or numBytes).toByte()
        var l = len
        for (i in numBytes downTo 1) {
            out[i] = (l and 0xFF).toByte()
            l = l shr 8
        }
        return out
    }
}
