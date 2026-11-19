package io.nekohasekai.sagernet.desktop

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The desktop Settings tab is not a hand written list of preferences: it is a
 * faithful, ordered rendering of the Android app's global settings screen. This
 * file parses the *real* Android resources and turns them into a catalog.
 *
 * Sources (copied into `android-preferences/` resources by the
 * `:desktop:app:copyAndroidPreferences` Gradle task, never modified):
 *  - `app/src/main/res/xml/global_preferences.xml` - sections and entries
 *  - `app/src/main/res/values/strings.xml` - titles and summaries
 *  - `app/src/main/res/values/arrays.xml` - dropdown entries/entryValues
 *  - `app/src/main/res/values/locale.xml` - language display names
 *  - `app/src/main/res/values{,-v29}/preference.xml` - API dependent defaults
 *
 * A new preference on Android therefore shows up here automatically. What is
 * *not* automatic is how the value reaches the core config: every key has to be
 * mapped explicitly in [SettingsBindings]. [SettingsCatalog] fails loudly for an
 * unmapped key and the desktop settings test fails the build for one.
 */

/** The widget the Android XML renders for an entry. */
enum class WidgetKind(val id: String) {
    SWITCH("switch"),
    EDIT("edit"),

    /** An [EDIT] whose `android:inputType` asks for an integer. */
    NUMBER("number"),
    MENU("menu"),
    COLOR("color"),

    /** An [EDIT] with URL validation on Android; rendered as a text field. */
    LINK("link"),

    /** A plain `Preference`: a row that only navigates or opens a dialog. */
    PLAIN("plain"),
}

/** One dropdown option, resolved from `arrays.xml`. */
data class MenuOption(val title: String, val value: String)

/**
 * One Android preference entry, in file order.
 *
 * [enabled] is false for preferences the current platform cannot honour
 * (packet capture, WakeLock, the platform-specific per-app rows, ...):
 * the row stays in its original place so the menu is structurally identical, but
 * it is rendered disabled with [disabledReason].
 *
 * [options] is the dropdown declared by the Android XML; [desktopOptions] is the
 * desktop replacement when the binding declares one (Service mode gains the
 * desktop "System proxy" choice). The renderer uses [choices].
 */
data class CatalogEntry(
    val key: String,
    val tag: String,
    val title: String,
    val summary: String,
    val widget: WidgetKind,
    val defaultValue: String?,
    val options: List<MenuOption>,
    val enabled: Boolean,
    val disabledReason: String?,
    val binding: Binding,
    val note: String?,
    val desktopOptions: List<MenuOption>? = null,
) {

    /** The options the UI renders: the desktop override when there is one. */
    val choices: List<MenuOption> get() = desktopOptions ?: options

}

/** One `PreferenceCategory`, in file order. */
data class CatalogSection(
    val title: String,
    val entries: List<CatalogEntry>,
)

/** The whole `PreferenceScreen`. */
data class SettingsCatalog(
    val sections: List<CatalogSection>,
    /** Widget tags the desktop renderer does not know yet; must stay empty. */
    val unknownTags: List<String>,
) {

    val entries: List<CatalogEntry> get() = sections.flatMap { it.entries }

    val enabledCount: Int get() = entries.count { it.enabled }

    val disabledCount: Int get() = entries.count { !it.enabled }

    /** The default declared in the Android XML for [key], or null. */
    fun defaultValueOf(key: String): String? = entries.firstOrNull { it.key == key }?.defaultValue

    /**
     * Stable, line oriented dump used by `--print-settings`. One `KEY` block per
     * entry, in the exact order of the Android XML, so the output can be diffed
     * against `global_preferences.xml` mechanically.
     */
    fun dump(currentValues: (String) -> String? = { null }): String = buildString {
        appendLine("SETTINGS-CATALOG sections=${sections.size} entries=${entries.size} " +
            "enabled=$enabledCount disabled=$disabledCount unknownTags=${unknownTags.size}")
        sections.forEachIndexed { sectionIndex, section ->
            appendLine("SECTION ${sectionIndex + 1}\t${oneLine(section.title)}")
            section.entries.forEach { entry ->
                appendLine("  KEY\t${entry.key}")
                appendLine("  TITLE\t${oneLine(entry.title)}")
                appendLine("  SUMMARY\t${oneLine(entry.summary)}")
                appendLine("  WIDGET\t${entry.widget.id}")
                appendLine("  TAG\t${entry.tag}")
                appendLine("  DEFAULT\t${entry.defaultValue?.let(::oneLine).orEmpty()}")
                currentValues(entry.key)?.let { appendLine("  CURRENT\t${oneLine(it)}") }
                appendLine("  ENABLED\t${entry.enabled}")
                entry.disabledReason?.let { appendLine("  REASON\t${oneLine(it)}") }
                entry.note?.let { appendLine("  NOTE\t${oneLine(it)}") }
                if (entry.options.isNotEmpty()) {
                    appendLine("  OPTIONS\t" + entry.options.joinToString(", ") {
                        "${oneLine(it.title)}=${oneLine(it.value)}"
                    })
                }
                entry.desktopOptions?.let { options ->
                    appendLine("  DESKTOP-OPTIONS\t" + options.joinToString(", ") {
                        "${oneLine(it.title)}=${oneLine(it.value)}"
                    })
                }
            }
        }
        if (unknownTags.isNotEmpty()) {
            appendLine("UNKNOWN-TAGS\t${unknownTags.joinToString(", ")}")
        }
    }

    private fun oneLine(text: String): String = text.replace("\r", "").replace("\n", "\\n")

}

/**
 * The catalog parser. It reads the copied Android resources from the classpath;
 * [load] is called lazily and the result is cached because the resources cannot
 * change while the process runs.
 */
object SettingsCatalogParser {

    private const val RESOURCE_DIR = "android-preferences"

    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private const val APP_NS = "http://schemas.android.com/apk/res-auto"

    /**
     * Value files in Android's own merge order. `preference-v29.xml` is the
     * resource merged `values-v29` override, which modern Android (API 29+, the
     * desktop client's reference platform) uses.
     */
    private val VALUE_FILES = listOf(
        "strings.xml",
        "locale.xml",
        "arrays.xml",
        "preference.xml",
        "preference-v29.xml",
    )

    /**
     * Every `<...Preference>` tag the Android XML may use. A tag outside this
     * table is reported through [SettingsCatalog.unknownTags] (and fails the
     * settings parity test) instead of being silently rendered as a plain row.
     */
    private val WIDGET_TAGS: Map<String, WidgetKind> = mapOf(
        "SwitchPreference" to WidgetKind.SWITCH,
        "EditTextPreference" to WidgetKind.EDIT,
        "NonBlackEditTextPreference" to WidgetKind.EDIT,
        "Preference" to WidgetKind.PLAIN,
        "SimpleMenuPreference" to WidgetKind.MENU,
        "ReselectableSimpleMenuPreference" to WidgetKind.MENU,
        "ColorPickerPreference" to WidgetKind.COLOR,
        "LinkPreference" to WidgetKind.LINK,
        "LinkOrContentPreference" to WidgetKind.LINK,
    )

    @Volatile
    private var cached: SettingsCatalog? = null

    fun load(): SettingsCatalog = cached ?: synchronized(this) {
        cached ?: parse().also { cached = it }
    }

    /** Forces a re-read; only used by tests. */
    fun reload(): SettingsCatalog = synchronized(this) {
        parse().also { cached = it }
    }

    /** Reads the preference XML straight from a file, bypassing the resources. */
    internal fun parse(xmlFile: File, valueFiles: List<File>): SettingsCatalog {
        val strings = HashMap<String, String>()
        val arrays = HashMap<String, List<String>>()
        valueFiles.forEach { file -> readValueFile(file, strings, arrays) }
        return parsePreferenceFile(xmlFile, strings, arrays)
    }

    private fun parse(): SettingsCatalog {
        val strings = HashMap<String, String>()
        val arrays = HashMap<String, List<String>>()
        VALUE_FILES.forEach { name ->
            val file = resourceFile(name) ?: return@forEach
            readValueFile(file, strings, arrays)
        }
        val preferenceXml = resourceFile("global_preferences.xml")
            ?: error(
                "missing resource $RESOURCE_DIR/global_preferences.xml: " +
                    "run ./gradlew :desktop:app:copyAndroidPreferences"
            )
        return parsePreferenceFile(preferenceXml, strings, arrays)
    }

    private fun resourceFile(name: String): File? {
        // The normal path is the classpath (jar or exploded build/resources).
        val url = SettingsCatalogParser::class.java.classLoader.getResource("$RESOURCE_DIR/$name")
        if (url != null && url.protocol == "file") return File(url.toURI())
        // Fall back to a real file when the classpath entry is not a plain file
        // (for example inside a packaged jar): copy it next to the data dir.
        if (url != null) {
            val target = File(DesktopRuntime.dataDir, "$RESOURCE_DIR-$name")
            if (!target.isFile || target.length() == 0L) {
                url.openStream().use { input ->
                    target.parentFile?.mkdirs()
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
            return target
        }
        return null
    }

    private fun readValueFile(
        file: File,
        strings: MutableMap<String, String>,
        arrays: MutableMap<String, List<String>>,
    ) {
        val root = document(file).documentElement ?: return
        root.childElements().forEach { element ->
            val name = element.getAttribute("name").takeIf { it.isNotEmpty() } ?: return@forEach
            when (element.tagName) {
                "string" -> strings[name] = unescape(element.textContent.orEmpty())
                "string-array", "integer-array", "array" -> {
                    arrays[name] = element.childElements()
                        .filter { it.tagName == "item" }
                        .map { unescape(it.textContent.orEmpty()) }
                }
            }
        }
    }

    private fun parsePreferenceFile(
        file: File,
        strings: Map<String, String>,
        arrays: Map<String, List<String>>,
    ): SettingsCatalog {
        val root = document(file).documentElement
            ?: error("${file.path} has no root element")
        val resolver = ResourceResolver(strings, arrays)
        val unknownTags = LinkedHashSet<String>()
        val sections = ArrayList<CatalogSection>()
        root.childElements().forEach { element ->
            when (element.tagName) {
                "PreferenceCategory" -> sections += parseCategory(element, resolver, unknownTags)
                else -> {
                    // A preference outside a category would change the layout: do
                    // not drop it silently.
                    val title = resolver.scalar(element.attribute("title")).orEmpty()
                    sections += CatalogSection(title, listOf(parseEntry(element, resolver, unknownTags)))
                }
            }
        }
        return SettingsCatalog(sections, unknownTags.toList())
    }

    private fun parseCategory(
        category: Element,
        resolver: ResourceResolver,
        unknownTags: MutableSet<String>,
    ): CatalogSection {
        val title = resolver.scalar(category.attribute("title")).orEmpty()
        val entries = category.childElements()
            .filter { it.attribute("key") != null }
            .map { parseEntry(it, resolver, unknownTags) }
        return CatalogSection(title, entries)
    }

    private fun parseEntry(
        element: Element,
        resolver: ResourceResolver,
        unknownTags: MutableSet<String>,
    ): CatalogEntry {
        val key = element.attribute("key").orEmpty()
        val tag = element.tagName.substringAfterLast('.')
        val widget = WIDGET_TAGS[tag] ?: run {
            unknownTags += element.tagName
            WidgetKind.PLAIN
        }.let { kind ->
            if (kind == WidgetKind.EDIT && element.attribute("inputType").orEmpty().contains("number")) {
                WidgetKind.NUMBER
            } else {
                kind
            }
        }
        val binding = SettingsBindings.bindingFor(key)
        val disabledReason = binding.disabledReason(DesktopRuntime.os)
        val desktopOptions = binding.desktopOptions(DesktopRuntime.os)
        return CatalogEntry(
            key = key,
            tag = tag,
            title = resolver.scalar(element.attribute("title")).orEmpty(),
            summary = resolver.scalar(element.attribute("summary")).orEmpty(),
            widget = widget,
            defaultValue = resolver.scalar(element.attribute("defaultValue")),
            options = resolver.options(
                element.attribute("entries"),
                element.attribute("entryValues"),
            ),
            enabled = disabledReason == null,
            disabledReason = disabledReason,
            binding = binding,
            note = binding.note,
            desktopOptions = desktopOptions,
        )
    }

    // -------------------------------------------------------------------- XML

    private fun document(file: File) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        // The Android resources never declare an external DTD; keep the parser
        // from trying to fetch one.
        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    }.newDocumentBuilder().parse(file)

    private fun Element.childElements(): List<Element> {
        val result = ArrayList<Element>()
        val nodes = childNodes
        for (index in 0 until nodes.length) {
            val node = nodes.item(index)
            if (node.nodeType == Node.ELEMENT_NODE) result += node as Element
        }
        return result
    }

    /** `app:` first, then `android:`, matching how the Android XML is written. */
    private fun Element.attribute(localName: String): String? {
        val app = getAttributeNS(APP_NS, localName)
        if (app.isNotEmpty()) return app
        val android = getAttributeNS(ANDROID_NS, localName)
        if (android.isNotEmpty()) return android
        // Namespace unaware fallback (a hand edited copy of the XML).
        val prefixed = getAttribute("app:$localName")
        if (prefixed.isNotEmpty()) return prefixed
        val androidPrefixed = getAttribute("android:$localName")
        if (androidPrefixed.isNotEmpty()) return androidPrefixed
        return null
    }

    // -------------------------------------------------------------- resolving

    private class ResourceResolver(
        private val strings: Map<String, String>,
        private val arrays: Map<String, List<String>>,
    ) {

        /** Resolves `@string/foo`, recursively, leaving an unknown reference as is. */
        fun scalar(raw: String?, depth: Int = 0): String? {
            if (raw == null) return null
            val match = REFERENCE.matchEntire(raw.trim()) ?: return raw
            val (kind, name) = match.destructured
            if (depth > 8) return raw
            return when (kind) {
                "string" -> {
                    val value = strings[name] ?: return raw
                    scalar(value, depth + 1)
                }
                else -> arrays[name]?.joinToString(",") { scalar(it, depth + 1).orEmpty() } ?: raw
            }
        }

        /** Resolves `@array/foo` into `entries`/`entryValues` pairs. */
        fun options(entries: String?, values: String?): List<MenuOption> {
            val titles = list(entries)
            val mapped = list(values)
            if (titles.isEmpty()) return emptyList()
            return titles.mapIndexed { index, title ->
                MenuOption(title, mapped.getOrElse(index) { "" })
            }
        }

        private fun list(raw: String?): List<String> {
            if (raw == null) return emptyList()
            val match = REFERENCE.matchEntire(raw.trim()) ?: return listOf(raw)
            val (kind, name) = match.destructured
            if (kind == "string") return listOf(scalar(raw).orEmpty())
            return arrays[name]?.map { scalar(it).orEmpty() } ?: emptyList()
        }

        private companion object {
            val REFERENCE = Regex("^@(string|array|integer-array)/(.+)$")
        }
    }

    /** Android string escapes, see `values/strings.xml` documentation. */
    internal fun unescape(value: String): String {
        val builder = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char != '\\' || index == value.lastIndex) {
                builder.append(char)
                index++
                continue
            }
            val next = value[index + 1]
            when (next) {
                'n' -> builder.append('\n')
                't' -> builder.append('\t')
                'r' -> builder.append('\r')
                '\'' -> builder.append('\'')
                '"' -> builder.append('"')
                '\\' -> builder.append('\\')
                '@' -> builder.append('@')
                'u' -> {
                    val hex = value.substring(index + 2, minOf(index + 6, value.length))
                    val code = hex.toIntOrNull(16)
                    if (code != null) {
                        builder.append(code.toChar())
                        index += 6
                        continue
                    }
                    builder.append(next)
                }
                else -> builder.append(next)
            }
            index += 2
        }
        return builder.toString()
    }

}
