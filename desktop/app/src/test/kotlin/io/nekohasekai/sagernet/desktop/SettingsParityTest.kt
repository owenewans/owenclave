package io.nekohasekai.sagernet.desktop

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Settings parity guard.
 *
 * This test parses `app/src/main/res/xml/global_preferences.xml` (and the value
 * resources it resolves) with its **own** DOM code and its **own** resource
 * resolver, then compares the result against the desktop catalog that
 * [SettingsCatalogParser] built. It does not reuse the production parser, so a
 * bug in the parser cannot hide a drift.
 *
 * Consequences, which are the point:
 *  - adding/removing/reordering a preference on Android fails this test until the
 *    desktop catalog matches it (the copied resource is regenerated on every
 *    build, so a stale copy is not silently accepted either);
 *  - using a widget tag the desktop renderer does not know fails;
 *  - adding a key without a [SettingsBindings] entry fails.
 */
class SettingsParityTest {

    private val repoRoot: File =
        File(requireNotNull(System.getProperty("owenclave.devRoot")) { "owenclave.devRoot is not set" })

    private val resDir = File(repoRoot, "app/src/main/res")

    /** Section order and count of the Android screen, quoted in the task spec. */
    private val expectedSectionSizes = listOf(
        "App settings" to 16,
        "Route settings" to 15,
        "Protocol settings" to 17,
        "HWID" to 2,
        "DNS settings" to 11,
        "Inbound settings" to 16,
        "Misc settings" to 11,
    )

    /** The widget tags the desktop renderer knows; a new tag must be added here and in production. */
    private val knownTags = mapOf(
        "SwitchPreference" to "switch",
        "EditTextPreference" to "edit",
        "NonBlackEditTextPreference" to "edit",
        "Preference" to "plain",
        "SimpleMenuPreference" to "menu",
        "ReselectableSimpleMenuPreference" to "menu",
        "ColorPickerPreference" to "color",
        "LinkPreference" to "link",
        "LinkOrContentPreference" to "link",
    )

    private data class ExpectedEntry(
        val key: String,
        val tag: String,
        val widget: String,
        val title: String,
        val summary: String,
        val defaultValue: String?,
        val options: List<String>,
    )

    private data class ExpectedSection(val title: String, val entries: List<ExpectedEntry>)

    // ------------------------------------------------------------------ parsing

    private val appNs = "http://schemas.android.com/apk/res-auto"
    private val androidNs = "http://schemas.android.com/apk/res/android"

    private fun document(file: File) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    }.newDocumentBuilder().parse(file)

    private fun Element.children(): List<Element> = buildList {
        val nodes = childNodes
        for (index in 0 until nodes.length) {
            val node = nodes.item(index)
            if (node.nodeType == Node.ELEMENT_NODE) add(node as Element)
        }
    }

    private fun Element.attr(name: String): String? {
        getAttributeNS(appNs, name).takeIf { it.isNotEmpty() }?.let { return it }
        getAttributeNS(androidNs, name).takeIf { it.isNotEmpty() }?.let { return it }
        getAttribute("app:$name").takeIf { it.isNotEmpty() }?.let { return it }
        getAttribute("android:$name").takeIf { it.isNotEmpty() }?.let { return it }
        return null
    }

    /**
     * Resolves `@string/...`/`@array/...` the way the Android resource merger
     * would for a modern (API 29+) device. Deliberately independent of the
     * production resolver.
     */
    private class Resolver(
        private val strings: Map<String, String>,
        private val arrays: Map<String, List<String>>,
    ) {
        fun scalar(raw: String?): String? {
            if (raw == null) return null
            val trimmed = raw.trim()
            if (!trimmed.startsWith("@")) return trimmed
            val body = trimmed.substring(1)
            val kind = body.substringBefore('/')
            val name = body.substringAfter('/', "")
            return when (kind) {
                "string" -> strings[name]?.let { scalar(it) }
                "array", "integer-array" -> arrays[name]?.joinToString(",") { scalar(it).orEmpty() }
                else -> trimmed
            }
        }

        fun list(raw: String?): List<String> {
            if (raw == null) return emptyList()
            val trimmed = raw.trim()
            if (!trimmed.startsWith("@")) return listOf(trimmed)
            val body = trimmed.substring(1)
            if (body.startsWith("string/")) return listOf(scalar(trimmed).orEmpty())
            val name = body.substringAfter('/', "")
            return arrays[name]?.map { scalar(it).orEmpty() } ?: emptyList()
        }
    }

    private fun unescape(value: String): String = value
        .replace("\\'", "'")
        .replace("\\\"", "\"")
        .replace("\\n", "\n")
        .replace("\\t", "\t")

    private fun parseExpected(): List<ExpectedSection> {
        val strings = HashMap<String, String>()
        val arrays = HashMap<String, List<String>>()
        // Merge order mirrors the Android values / values-v29 override.
        listOf(
            "values/strings.xml",
            "values/locale.xml",
            "values/arrays.xml",
            "values/preference.xml",
            "values-v29/preference.xml",
        ).forEach { relative ->
            val root = document(File(resDir, relative)).documentElement ?: return@forEach
            root.children().forEach { element ->
                val name = element.getAttribute("name").takeIf { it.isNotEmpty() } ?: return@forEach
                when (element.tagName) {
                    "string" -> strings[name] = unescape(element.textContent.orEmpty())
                    "string-array", "integer-array", "array" ->
                        arrays[name] = element.children()
                            .filter { it.tagName == "item" }
                            .map { unescape(it.textContent.orEmpty()) }
                }
            }
        }
        val resolver = Resolver(strings, arrays)
        val root = document(File(resDir, "xml/global_preferences.xml")).documentElement
            ?: error("the Android preferences XML has no root")

        val sections = ArrayList<ExpectedSection>()
        root.children().forEach { category ->
            assertEquals("PreferenceCategory", category.tagName)
            val title = resolver.scalar(category.attr("title")).orEmpty()
            val entries = category.children()
                .filter { it.attr("key") != null }
                .map { element ->
                    val tag = element.tagName.substringAfterLast('.')
                    val kind = knownTags[tag] ?: error("unknown widget tag in the Android XML: $tag")
                    val widget = if (kind == "edit" && element.attr("inputType").orEmpty().contains("number")) {
                        "number"
                    } else {
                        kind
                    }
                    val entriesRaw = element.attr("entries")
                    val valuesRaw = element.attr("entryValues")
                    val options = if (entriesRaw == null) {
                        emptyList()
                    } else {
                        resolver.list(entriesRaw).mapIndexed { index, optionTitle ->
                            "$optionTitle=${resolver.list(valuesRaw).getOrElse(index) { "" }}"
                        }
                    }
                    ExpectedEntry(
                        key = element.attr("key").orEmpty(),
                        tag = tag,
                        widget = widget,
                        title = resolver.scalar(element.attr("title")).orEmpty(),
                        summary = resolver.scalar(element.attr("summary")).orEmpty(),
                        defaultValue = resolver.scalar(element.attr("defaultValue")),
                        options = options,
                    )
                }
            sections += ExpectedSection(title, entries)
        }
        return sections
    }

    // -------------------------------------------------------------------- tests

    @Test
    fun `desktop catalog mirrors the Android preferences XML one to one`() {
        val expected = parseExpected()
        val catalog = SettingsCatalogParser.reload()

        assertEquals(7, catalog.sections.size, "the Android screen has exactly 7 categories")
        assertEquals(expected.size, catalog.sections.size)
        assertEquals(88, catalog.entries.size, "the Android screen has exactly 88 preferences")
        assertEquals(expectedSectionSizes, catalog.sections.map { it.title to it.entries.size })

        expected.forEachIndexed { sectionIndex, expectedSection ->
            val actualSection = catalog.sections[sectionIndex]
            assertEquals(expectedSection.title, actualSection.title, "section #$sectionIndex title")
            assertEquals(
                expectedSection.entries.map { it.key },
                actualSection.entries.map { it.key },
                "keys of section \"${expectedSection.title}\"",
            )
            expectedSection.entries.forEachIndexed { entryIndex, expectedEntry ->
                val actual = actualSection.entries[entryIndex]
                val where = "key ${expectedEntry.key}"
                assertEquals(expectedEntry.tag, actual.tag, "$where tag")
                assertEquals(expectedEntry.widget, actual.widget.id, "$where widget")
                assertEquals(expectedEntry.title, actual.title, "$where title")
                assertEquals(expectedEntry.summary, actual.summary, "$where summary")
                assertEquals(expectedEntry.defaultValue, actual.defaultValue, "$where default")
                assertEquals(expectedEntry.options, actual.options.map { "${it.title}=${it.value}" }, "$where options")
                assertFalse(actual.title.contains("@string/"), "$where has an unresolved title: ${actual.title}")
                assertFalse(actual.summary.contains("@string/"), "$where has an unresolved summary")
                assertFalse(
                    actual.defaultValue?.contains("@string/") == true,
                    "$where has an unresolved default: ${actual.defaultValue}",
                )
            }
        }
        assertTrue(catalog.unknownTags.isEmpty(), "unknown widget tags: ${catalog.unknownTags}")
    }

    @Test
    fun `every Android key has an explicit desktop binding and no binding is stale`() {
        val xmlKeys = parseExpected().flatMap { section -> section.entries.map { it.key } }
        val catalog = SettingsCatalogParser.reload()

        val unmapped = catalog.entries.filter { it.binding is Unsupported }
        assertTrue(
            unmapped.isEmpty(),
            "no desktop binding for: ${unmapped.map { it.key }} - add them to SettingsBindings",
        )

        // Both directions: a key in the XML without a binding fails, and a binding
        // for a key that Android removed is stale and also fails.
        assertEquals(
            xmlKeys.toSet(),
            SettingsBindings.keys,
            "the binding table and the Android preference XML disagree",
        )
        assertTrue(
            catalog.entries.none { !it.enabled && it.disabledReason.isNullOrBlank() },
            "every disabled entry needs a reason",
        )
    }

    @Test
    fun `the catalog is enabled except for the documented platform-only keys`() {
        val catalog = SettingsCatalogParser.reload()
        val os = DesktopRuntime.os
        val disabled = catalog.entries.filter { !it.enabled }

        val alwaysDisabled = setOf(
            "appLanguage",
            "tunImplementation",
            "meteredNetwork",
            "enablePcap",
            "discardICMP",
            "appTrafficStatistics",
            "speedInterval",
            "showDirectSpeed",
            "appendHttpProxy",
            "httpProxyException",
            "requireTransproxy",
            "transproxyPort",
            "showGroupName",
            "acquireWakeLock",
            "fabStyle",
            "useIECUnit",
            "queryAllPackagesAlternativeMethod",
            "allowAppsBypassVpn",
        )
        val expected = if (os == "linux") alwaysDisabled else alwaysDisabled + "proxyApps"

        assertEquals(expected, disabled.map { it.key }.toSet())
        assertTrue(disabled.all { !it.disabledReason.isNullOrBlank() }, "every disabled row needs a reason")
        // The catalog agrees with the binding table for the running platform.
        assertTrue(
            catalog.entries.all { it.enabled == (it.binding.disabledReason(os) == null) },
            "catalog enabled flags disagree with SettingsBindings",
        )
    }

    @Test
    fun `service mode offers the desktop system proxy choice`() {
        val entry = SettingsCatalogParser.reload().entries.single { it.key == "serviceMode" }
        assertTrue(entry.enabled, "Service mode is real on desktop")
        // The Android XML only knows vpn/proxy; the desktop adds System proxy.
        assertEquals(listOf("vpn", "proxy"), entry.options.map { it.value })
        assertEquals(listOf("vpn", "system", "proxy"), entry.choices.map { it.value })
    }

    @Test
    fun `the per-app rows state the truth for the running platform`() {
        val catalog = SettingsCatalogParser.reload()
        val proxyApps = catalog.entries.single { it.key == "proxyApps" }
        val bypass = catalog.entries.single { it.key == "allowAppsBypassVpn" }
        when (DesktopRuntime.os) {
            "linux" -> {
                assertTrue(proxyApps.enabled, "per-app routing is implemented on Linux")
                assertFalse(bypass.enabled)
                assertTrue(bypass.disabledReason!!.contains("include list"))
            }
            "windows" -> {
                assertFalse(proxyApps.enabled)
                assertFalse(bypass.enabled)
                assertTrue(proxyApps.disabledReason!!.contains("WFP callout"))
                assertTrue(bypass.disabledReason!!.contains("WFP callout"))
            }
            else -> {
                assertFalse(proxyApps.enabled)
                assertFalse(bypass.enabled)
                assertTrue(proxyApps.disabledReason!!.contains("NetworkExtension"))
                assertTrue(bypass.disabledReason!!.contains("NetworkExtension"))
            }
        }
    }
}
