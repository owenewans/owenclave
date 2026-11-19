package io.nekohasekai.sagernet

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.xml.parsers.DocumentBuilderFactory

object DesktopResourceLoader {

    private data class LocaleBundle(
        val strings: Map<String, String>,
        val plurals: Map<String, Map<String, String>>,
        val arrays: Map<String, List<String>>,
    )

    private val bundles = ConcurrentHashMap<String, LocaleBundle>()

    @Volatile
    var overrideLanguageTag: String = ""

    private fun currentLangKey(): String {
        val tag = overrideLanguageTag.ifBlank { Locale.getDefault().toLanguageTag() }
        val lower = tag.lowercase()
        return when {
            lower.startsWith("ru") -> "ru"
            lower.startsWith("zh-tw") || lower.startsWith("zh-hk") || lower.startsWith("zh-hant") -> "zh-rTW"
            lower.startsWith("zh") -> "zh-rCN"
            else -> "default"
        }
    }

    private fun loadBundle(langKey: String): LocaleBundle {
        return bundles.getOrPut(langKey) {
            val strings = HashMap<String, String>()
            val plurals = HashMap<String, MutableMap<String, String>>()
            val arrays = HashMap<String, List<String>>()

            val filesToLoad = mutableListOf(
                "values/strings.xml",
                "values/locale.xml",
                "values/arrays.xml",
                "values/preference.xml",
                "values-v29/preference.xml",
            )
            if (langKey != "default") {
                filesToLoad.add("values-$langKey/strings.xml")
            }

            val dbf = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            }

            for (relPath in filesToLoad) {
                val stream = openResStream(relPath) ?: continue
                runCatching {
                    val doc = stream.use { dbf.newDocumentBuilder().parse(it) }
                    val nodes = doc.documentElement?.childNodes ?: return@runCatching
                    for (i in 0 until nodes.length) {
                        val node = nodes.item(i)
                        if (node.nodeType != Node.ELEMENT_NODE) continue
                        val el = node as Element
                        val rawName = el.getAttribute("name")?.takeIf { it.isNotBlank() } ?: continue
                        val name = rawName.replace('.', '_').replace('-', '_')
                        when (el.tagName) {
                            "string" -> strings[name] = unescape(el.textContent.orEmpty())
                            "plurals" -> {
                                val map = plurals.getOrPut(name) { HashMap() }
                                val itemNodes = el.childNodes
                                for (j in 0 until itemNodes.length) {
                                    val itemNode = itemNodes.item(j)
                                    if (itemNode.nodeType == Node.ELEMENT_NODE && (itemNode as Element).tagName == "item") {
                                        val qty = itemNode.getAttribute("quantity")
                                        map[qty] = unescape(itemNode.textContent.orEmpty())
                                    }
                                }
                            }
                            "string-array", "integer-array", "array" -> {
                                val list = ArrayList<String>()
                                val itemNodes = el.childNodes
                                for (j in 0 until itemNodes.length) {
                                    val itemNode = itemNodes.item(j)
                                    if (itemNode.nodeType == Node.ELEMENT_NODE && (itemNode as Element).tagName == "item") {
                                        list.add(unescape(itemNode.textContent.orEmpty()))
                                    }
                                }
                                arrays[name] = list
                            }
                        }
                    }
                }
            }
            LocaleBundle(strings, plurals, arrays)
        }
    }

    private fun openResStream(relPath: String): InputStream? {
        DesktopResourceLoader::class.java.classLoader
            ?.getResourceAsStream("android_res/$relPath")
            ?.let { return it }
        val devRoot = System.getProperty("owenclave.devRoot")
        if (!devRoot.isNullOrBlank()) {
            val file = File(devRoot, "app/src/main/res/$relPath")
            if (file.isFile) return file.inputStream()
        }
        val cwdFile = File("app/src/main/res/$relPath")
        if (cwdFile.isFile) return cwdFile.inputStream()
        return null
    }

    fun openAssetStream(relPath: String): InputStream? {
        val clean = relPath.trimStart('/')
        DesktopResourceLoader::class.java.classLoader
            ?.getResourceAsStream("android_assets/$clean")
            ?.let { return it }
        val devRoot = System.getProperty("owenclave.devRoot")
        if (!devRoot.isNullOrBlank()) {
            val file = File(devRoot, "app/src/main/assets/$clean")
            if (file.isFile) return file.inputStream()
        }
        val cwdFile = File("app/src/main/assets/$clean")
        if (cwdFile.isFile) return cwdFile.inputStream()
        return null
    }

    fun openRawResource(id: Int): InputStream {
        val key = DesktopResourceMaps.idToKey[id]
        if (key == "raw/aboutlibraries") {
            return openAssetStream("license/notices.txt") ?: ByteArrayInputStream(ByteArray(0))
        }
        return ByteArrayInputStream(ByteArray(0))
    }

    private fun resolveStringRef(value: String, bundle: LocaleBundle, defaultBundle: LocaleBundle, depth: Int = 0): String {
        if (depth > 8 || !value.startsWith("@string/")) return value
        val refName = value.removePrefix("@string/").trim().replace('.', '_').replace('-', '_')
        val target = bundle.strings[refName] ?: defaultBundle.strings[refName] ?: return value
        return resolveStringRef(target, bundle, defaultBundle, depth + 1)
    }

    fun getString(resId: Int): String {
        val key = DesktopResourceMaps.idToKey[resId] ?: return ""
        val name = key.substringAfter('/')
        val langKey = currentLangKey()
        val bundle = loadBundle(langKey)
        val defaultBundle = loadBundle("default")
        val raw = bundle.strings[name] ?: defaultBundle.strings[name] ?: return name
        return resolveStringRef(raw, bundle, defaultBundle)
    }

    fun getString(resId: Int, vararg formatArgs: Any?): String {
        val template = getString(resId)
        if (formatArgs.isEmpty()) return template
        return runCatching {
            String.format(Locale.getDefault(), template, *formatArgs)
        }.getOrElse { template }
    }

    fun getStringArray(resId: Int): Array<String> {
        val key = DesktopResourceMaps.idToKey[resId] ?: return emptyArray()
        val name = key.substringAfter('/')
        val langKey = currentLangKey()
        val bundle = loadBundle(langKey)
        val defaultBundle = loadBundle("default")
        val list = bundle.arrays[name] ?: defaultBundle.arrays[name] ?: return emptyArray()
        return list.map { resolveStringRef(it, bundle, defaultBundle) }.toTypedArray()
    }

    fun getQuantityString(resId: Int, quantity: Int, vararg formatArgs: Any?): String {
        val key = DesktopResourceMaps.idToKey[resId] ?: return ""
        val name = key.substringAfter('/')
        val langKey = currentLangKey()
        val bundle = loadBundle(langKey)
        val defaultBundle = loadBundle("default")
        val map = bundle.plurals[name] ?: defaultBundle.plurals[name] ?: return ""
        val qtyKey = if (quantity == 1) "one" else "other"
        val template = map[qtyKey] ?: map["other"] ?: map.values.firstOrNull() ?: return ""
        val resolved = resolveStringRef(template, bundle, defaultBundle)
        if (formatArgs.isEmpty()) return resolved
        return runCatching {
            String.format(Locale.getDefault(), resolved, *formatArgs)
        }.getOrElse { resolved }
    }

    private fun unescape(value: String): String {
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

