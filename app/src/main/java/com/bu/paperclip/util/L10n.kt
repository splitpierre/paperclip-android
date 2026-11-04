package com.bu.paperclip.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.LocaleManagerCompat
import androidx.core.os.LocaleListCompat
import com.bu.paperclip.R
import org.xmlpull.v1.XmlPullParser
import java.util.Locale

/**
 * Strings and locale outside Compose (workers, the data layer, formatters).
 *
 * The app language is stored by AppCompat (`autoStoreLocales`). From Android 13 the system applies it to the whole
 * process; below that AppCompat only overrides activities, so [res] wraps the application context in the app locale.
 * Composables should use `stringResource` instead.
 */
@SuppressLint("StaticFieldLeak") // Holds only the application context.
object L10n {
    private lateinit var app: Context
    @Volatile private var cached: Pair<String, Resources>? = null

    fun init(context: Context) { app = context.applicationContext }

    /** The chosen app locales, or empty for the system default. */
    fun appLocales(): LocaleListCompat {
        val set = AppCompatDelegate.getApplicationLocales()
        if (!set.isEmpty) return set
        return if (::app.isInitialized) LocaleManagerCompat.getApplicationLocales(app) else set
    }

    /** Language tag picked in Settings, or "" for the system default. */
    fun appLanguageTag(): String = appLocales().toLanguageTags()

    fun setAppLanguage(tag: String) {
        AppCompatDelegate.setApplicationLocales(
            if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag),
        )
    }

    /** The locale strings, dates and numbers are shown in. */
    fun locale(): Locale = if (::app.isInitialized) res().configuration.locales[0] else Locale.getDefault()

    fun res(): Resources {
        val base = app.resources
        val tags = appLocales()
        if (Build.VERSION.SDK_INT >= 33 || tags.isEmpty) return base
        val key = tags.toLanguageTags()
        cached?.let { (k, r) -> if (k == key) return r }
        val conf = Configuration(base.configuration)
        conf.setLocales(android.os.LocaleList.forLanguageTags(key))
        val r = app.createConfigurationContext(conf).resources
        cached = key to r
        return r
    }

    fun str(@StringRes id: Int, vararg args: Any): String = res().getString(id, *args)

    fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String = res().getQuantityString(id, count, *args)

    /** Language tags declared in `res/xml/locales_config.xml`, the one list of shipped languages. */
    fun shippedLanguageTags(context: Context): List<String> {
        val out = mutableListOf<String>()
        context.resources.getXml(R.xml.locales_config).use { p ->
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                if (p.eventType == XmlPullParser.START_TAG && p.name == "locale") {
                    p.getAttributeValue("http://schemas.android.com/apk/res/android", "name")?.let(out::add)
                }
            }
        }
        return out
    }

    /** A language's name in that language, e.g. "Português (Brasil)". */
    fun nativeName(tag: String): String {
        val l = Locale.forLanguageTag(tag)
        return l.getDisplayName(l).replaceFirstChar { it.titlecase(l) }
    }
}
