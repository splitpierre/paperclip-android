package com.bu.paperclip

import com.bu.paperclip.util.AppText
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** Points AppText at the default (English) values/strings.xml, so plain JVM tests can read resources. */
object TestStrings {
    private var installed = false

    fun install() {
        if (installed) return
        installed = true
        Locale.setDefault(Locale.US)
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/values/strings.xml"))
        val strings = HashMap<String, String>()
        val plurals = HashMap<String, Map<String, String>>()
        val nodes = doc.documentElement.childNodes
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as? Element ?: continue
            val name = e.getAttribute("name")
            when (e.tagName) {
                "string" -> strings[name] = unescape(e.textContent)
                "plurals" -> {
                    val items = e.getElementsByTagName("item")
                    plurals[name] = (0 until items.length).associate { j ->
                        val it = items.item(j) as Element
                        it.getAttribute("quantity") to unescape(it.textContent)
                    }
                }
            }
        }
        val stringNames = R.string::class.java.fields.associate { it.getInt(null) to it.name }
        val pluralNames = R.plurals::class.java.fields.associate { it.getInt(null) to it.name }
        AppText.string = { id, args ->
            val s = strings.getValue(stringNames.getValue(id))
            if (args.isEmpty()) s else String.format(Locale.US, s, *args)
        }
        AppText.plural = { id, count, args ->
            val forms = plurals.getValue(pluralNames.getValue(id))
            String.format(Locale.US, forms[if (count == 1) "one" else "other"] ?: forms.getValue("other"), *args)
        }
    }

    private fun unescape(s: String) = s.replace("\\'", "'").replace("\\\"", "\"").replace("\\n", "\n").replace("\\@", "@").replace("\\?", "?")
}
