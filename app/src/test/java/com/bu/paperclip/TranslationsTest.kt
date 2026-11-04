package com.bu.paperclip

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Checks every `values-<lang>` translation against the default `values` strings:
 * - fails on keys the default file doesn't have, on translated `translatable="false"` keys,
 *   on format arguments that differ from the English ones, and on locales missing from `locales_config.xml`;
 * - only warns about missing keys, since Android falls back to English for those.
 */
class TranslationsTest {
    private val res = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }

    /** A string or one plural item: key ("name" or "name#quantity") to text. */
    private class Strings(val values: Map<String, String>, val plurals: Map<String, Map<String, String>>, val untranslatable: Set<String>)

    private fun load(dir: File): Strings {
        val values = mutableMapOf<String, String>()
        val plurals = mutableMapOf<String, Map<String, String>>()
        val untranslatable = mutableSetOf<String>()
        val files = dir.listFiles { f -> f.extension == "xml" }.orEmpty().sortedBy { it.name }
        for (f in files) {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
            val nodes = doc.documentElement.childNodes
            for (i in 0 until nodes.length) {
                val e = nodes.item(i) as? Element ?: continue
                val name = e.getAttribute("name")
                when (e.tagName) {
                    "string" -> {
                        check(name !in values) { "${f.path}: duplicate string $name" }
                        values[name] = e.textContent
                        if (e.getAttribute("translatable") == "false") untranslatable += name
                    }
                    "plurals" -> {
                        val items = e.getElementsByTagName("item")
                        plurals[name] = (0 until items.length).associate { j ->
                            val it = items.item(j) as Element
                            it.getAttribute("quantity") to it.textContent
                        }
                        if (e.getAttribute("translatable") == "false") untranslatable += name
                    }
                }
            }
        }
        return Strings(values, plurals, untranslatable)
    }

    private val formatArg = Regex("""%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?([a-zA-Z%])""")

    /** Format arguments as "index:conversion", ignoring `%%`. Unnumbered ones count up from 1. */
    private fun args(s: String): Set<String> {
        var next = 1
        return formatArg.findAll(s).mapNotNull { m ->
            val conv = m.groupValues[3]
            if (conv == "%") return@mapNotNull null
            val index = m.groupValues[1].removeSuffix("$").toIntOrNull() ?: next++
            "$index:${conv.lowercase()}"
        }.toSet()
    }

    private fun translationDirs() = res.listFiles { f -> f.isDirectory && f.name.startsWith("values-") }.orEmpty()
        .filter { dir -> dir.listFiles().orEmpty().any { f -> f.readText().contains("<string ") || f.readText().contains("<plurals ") } }
        .sortedBy { it.name }

    /** `values-pt-rBR` → `pt-BR`, `values-b+sr+Latn` → `sr-Latn`. */
    private fun languageTag(dir: File): String {
        val q = dir.name.removePrefix("values-")
        return if (q.startsWith("b+")) q.removePrefix("b+").replace('+', '-') else q.replace("-r", "-")
    }

    @Test fun translationsMatchDefaultStrings() {
        val base = load(File(res, "values"))
        assertTrue("no default strings found in $res/values", base.values.isNotEmpty())
        val errors = mutableListOf<String>()
        for (dir in translationDirs()) {
            val t = load(dir)
            val where = dir.name
            for ((k, v) in t.values) {
                val en = base.values[k]
                when {
                    en == null -> errors += "$where: string \"$k\" is not in values/ (renamed or removed?)"
                    k in base.untranslatable -> errors += "$where: \"$k\" is marked translatable=\"false\"; remove it"
                    args(v) != args(en) -> errors += "$where: \"$k\" has format arguments ${args(v)}, English has ${args(en)}"
                }
            }
            for ((k, items) in t.plurals) {
                val en = base.plurals[k]
                if (en == null) { errors += "$where: plurals \"$k\" is not in values/ (renamed or removed?)"; continue }
                if (k in base.untranslatable) { errors += "$where: \"$k\" is marked translatable=\"false\"; remove it"; continue }
                val allowed = en.values.flatMap { args(it) }.toSet()
                if ("other" !in items) errors += "$where: plurals \"$k\" needs an \"other\" item"
                for ((q, v) in items) {
                    // Singular forms may drop the number ("one task"), but may not invent arguments.
                    if (!allowed.containsAll(args(v))) errors += "$where: plurals \"$k\" ($q) has format arguments ${args(v)}, English allows $allowed"
                }
                items["other"]?.let { if (args(it) != args(en["other"] ?: "")) errors += "$where: plurals \"$k\" (other) has format arguments ${args(it)}, English has ${args(en["other"] ?: "")}" }
            }
            val missing = (base.values.keys - base.untranslatable - t.values.keys) + (base.plurals.keys - base.untranslatable - t.plurals.keys)
            if (missing.isNotEmpty()) {
                System.err.println("warning: $where is missing ${missing.size} key(s), shown in English: ${missing.sorted().joinToString()}")
            }
        }
        if (errors.isNotEmpty()) fail("Translation problems:\n" + errors.joinToString("\n"))
    }

    @Test fun defaultStringsUsePositionalArgumentsWhenThereAreSeveral() {
        val base = load(File(res, "values"))
        val all = base.values + base.plurals.flatMap { (k, items) -> items.map { (q, v) -> "$k#$q" to v } }
        val bad = all.filter { (_, v) ->
            val found = formatArg.findAll(v).filter { it.groupValues[3] != "%" }.toList()
            found.size > 1 && found.any { it.groupValues[1].isEmpty() }
        }.keys
        assertTrue("Use %1\$s, %2\$d… when a string has several arguments: $bad", bad.isEmpty())
    }

    @Test fun everyTranslationIsRegisteredInLocalesConfig() {
        val config = File(res, "xml/locales_config.xml").readText()
        val declared = Regex("""android:name="([^"]+)"""").findAll(config).map { it.groupValues[1] }.toSet()
        val shipped = translationDirs().map(::languageTag).toSet() + "en"
        assertTrue("Add these to res/xml/locales_config.xml: ${shipped - declared}", (shipped - declared).isEmpty())
        assertTrue("locales_config.xml lists languages with no values-<lang> folder: ${declared - shipped}", (declared - shipped).isEmpty())
    }
}
