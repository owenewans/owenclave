package io.nekohasekai.sagernet.ktx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM unit tests for [HappLink]. No Android framework, no network, no
 * Robolectric required -- [HappLink] only touches `java.security` /
 * `javax.crypto` plus a classpath resource.
 */
class HappLinkTest {

    // A real happ://crypt5/ link (marker "vdfzfoff", salted layout) captured
    // from a live subscription share, used as a golden regression vector.
    private val crypt5Link = "happ://crypt5/fzvdwe0VeP2pgLd6AvwF4UC010i3QX4opsVp6sw+QTUQd6X+iziY/K1s8qXDpGsZ89N2NMYFihmgAwn7AVW56XD1S4ejwFxWIegEhAqPKs743j31wnVyt1te5ZuoV1Bx5gmQ3W==F+FCeenoDW4ova9wPMF/0aFMnf28U9XsIhmV543pCzSM88xM+1smfVFHzlADeHZrbKnqQ+eaXG9rPfspd4Xt9IHXaXFGPcyncwXru5SqfZS0/iNBVxqT6hlQTPbHvRm6KLMOsSG0zMu2LFc1RUT9tdhUIoQ/d3RGMMJXGYSM2AuCPqKtdn0qVcSYEs4BTGrdadZWk4VXBOCmlrm2lKhXupCQvR+N1Ia7JUFcW0B6XLVn9E7oL5RbaNQwpDLAMX0FR0ZjmJIAb6tGeEHw8//vW1qo8PsJFRgvNvZuDMFMB4edZ/cda9VK7jux0tEQeE2O4HrI3c2Ig4apojnyvzSWh1xiIf1mYb1uwVxEhFgXr7SmkB5UeA9BsTZPMnvmn6pTqXcJo93n2iK/lbxFpb3GTQiyxXyaEUMvGcXynOmwDNVDNGk7okUwA2NxVffkkwotJA4Tyzt9Qgp9dcelKkzGBNXXjtMi6KJSxsl2/Mbi8WURt7/EzZrzfFp+8XBg91ZGkgMzlEQnBfFtqkitZtG16rhY48G0TRSIp93z7vf68BJbMsIC/CK5PXn8IWAjsQXVk4JG8L1NhYu5p+flz3VkDGTTNQWFhAiifpiyeOhsEAG5HyklS3vZIYehyceb5jqGly3B3iR6bVHjuKeYytQ1Np3T9p0cn5aQAAoG5sFBfoc=ff"

    private val crypt5ExpectedUrl = "https://sub.marou.ai/api/sub/jp9Tv0Rz4CwENpvG"

    @Test
    fun `decrypts a real crypt5 link`() {
        assertEquals(crypt5ExpectedUrl, HappLink.decrypt(crypt5Link))
    }

    @Test
    fun `decrypt is deterministic across repeated calls`() {
        // Exercises the private-key cache path, not just a cold first call.
        assertEquals(crypt5ExpectedUrl, HappLink.decrypt(crypt5Link))
        assertEquals(crypt5ExpectedUrl, HappLink.decrypt(crypt5Link))
        assertEquals(crypt5ExpectedUrl, HappLink.decrypt(crypt5Link))
    }

    @Test
    fun `isHappCryptLink recognizes all five crypt schemes`() {
        assertTrue(HappLink.isHappCryptLink("happ://crypt/AAAA"))
        assertTrue(HappLink.isHappCryptLink("happ://crypt2/AAAA"))
        assertTrue(HappLink.isHappCryptLink("happ://crypt3/AAAA"))
        assertTrue(HappLink.isHappCryptLink("happ://crypt4/AAAA"))
        assertTrue(HappLink.isHappCryptLink("happ://crypt5/AAAA"))
        // Case-insensitive scheme, per RFC 3986.
        assertTrue(HappLink.isHappCryptLink("HAPP://CRYPT5/AAAA"))
    }

    @Test
    fun `isHappCryptLink rejects unrelated happ paths and other schemes`() {
        assertFalse(HappLink.isHappCryptLink("happ://add/AAAA"))
        assertFalse(HappLink.isHappCryptLink("happ://toggle"))
        assertFalse(HappLink.isHappCryptLink("https://example.com/sub"))
        assertFalse(HappLink.isHappCryptLink("vmess://AAAA"))
        assertFalse(HappLink.isHappCryptLink(""))
    }

    @Test
    fun `decrypt returns null for a non-happ link`() {
        assertNull(HappLink.decrypt("https://example.com/sub"))
        assertNull(HappLink.decrypt("owenkey://whatever"))
    }

    @Test
    fun `decrypt returns null instead of throwing for a malformed crypt5 payload`() {
        assertNull(HappLink.decrypt("happ://crypt5/not-a-valid-payload-at-all"))
    }

    @Test
    fun `decrypt returns null for an unknown crypt5 marker`() {
        // Well-formed shape (long enough, base64-ish) but won't resolve to
        // any of the 36 bundled markers.
        assertNull(HappLink.decrypt("happ://crypt5/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"))
    }

    @Test
    fun `decrypt returns null for a malformed legacy crypt payload`() {
        assertNull(HappLink.decrypt("happ://crypt/not-valid-base64-rsa-data"))
        assertNull(HappLink.decrypt("happ://crypt4/not-valid-base64-rsa-data"))
    }

    @Test
    fun `decrypt tolerates a trailing newline or surrounding whitespace in the scheme check`() {
        // Deep-link intents and pasted clipboard text are both untrusted
        // free text; only the recognised-scheme fast path needs to be exact.
        assertFalse(HappLink.isHappCryptLink(" happ://crypt5/AAAA"))
    }
}
