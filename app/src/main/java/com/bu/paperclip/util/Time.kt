package com.bu.paperclip.util

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import com.bu.paperclip.R

/** Accepts `Z` and `+hh:mm` ISO timestamps (older Android rejects offsets in Instant.parse) and epoch numbers. */
fun parseInstant(s: String?): Instant? {
    if (s.isNullOrBlank()) return null
    runCatching { return Instant.parse(s) }
    runCatching { return java.time.OffsetDateTime.parse(s).toInstant() }
    s.toLongOrNull()?.let { n -> return if (n > 100_000_000_000L) Instant.ofEpochMilli(n) else Instant.ofEpochSecond(n) }
    return null
}

fun providerLabel(p: String): String = when (p.lowercase()) {
    "anthropic" -> "Anthropic"
    "openai" -> "OpenAI"
    "google", "gemini", "google_antigravity" -> "Google"
    "zai", "z.ai" -> "Z.ai"
    else -> p.replaceFirstChar { it.uppercase() }
}

fun relativeTime(iso: String?, now: Instant = Instant.now()): String {
    val t = parseInstant(iso) ?: return ""
    val d = Duration.between(t, now)
    val s = d.seconds
    return when {
        s < 0 -> L10n.str(R.string.time_in, short(-s))
        s < 45 -> L10n.str(R.string.time_now)
        else -> L10n.str(R.string.time_ago, short(s))
    }
}

private fun short(s: Long): String = when {
    s < 3600 -> L10n.str(R.string.time_short_minutes, (s / 60).coerceAtLeast(1))
    s < 86400 -> L10n.str(R.string.time_short_hours, s / 3600)
    s < 86400 * 30 -> L10n.str(R.string.time_short_days, s / 86400)
    s < 86400 * 365 -> L10n.str(R.string.time_short_months, s / (86400 * 30))
    else -> L10n.str(R.string.time_short_years, s / (86400 * 365))
}

private val absFmtCache = java.util.concurrent.ConcurrentHashMap<Locale, DateTimeFormatter>()

/** A date and time in the app locale, e.g. "4 Nov 2026, 09:41" or "Nov 4, 2026, 9:41 AM". */
fun absoluteTime(iso: String?): String {
    val t = parseInstant(iso) ?: return ""
    val loc = L10n.locale()
    val f = absFmtCache.getOrPut(loc) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(loc).withZone(ZoneId.systemDefault())
    }
    return f.format(t)
}

fun durationLabel(startIso: String?, endIso: String?): String {
    val a = parseInstant(startIso) ?: return ""
    val b = parseInstant(endIso) ?: Instant.now()
    val s = Duration.between(a, b).seconds.coerceAtLeast(0)
    return when {
        s < 60 -> L10n.str(R.string.duration_seconds, s)
        s < 3600 -> L10n.str(R.string.duration_minutes_seconds, s / 60, s % 60)
        else -> L10n.str(R.string.duration_hours_minutes, s / 3600, (s % 3600) / 60)
    }
}

/** US dollars in the app locale ("$1,234.56", "US$ 1.234,56"). */
fun formatCents(cents: Long?): String = formatUsd((cents ?: 0) / 100.0)

fun formatUsd(amount: Double, fractionDigits: Int = 2): String =
    NumberFormat.getCurrencyInstance(L10n.locale()).apply {
        currency = Currency.getInstance("USD")
        minimumFractionDigits = fractionDigits
        maximumFractionDigits = fractionDigits
    }.format(amount)

/** Locale-aware number formatting ("1,234" / "1.234"); `%d` alone doesn't group digits. */
fun formatNumber(n: Long): String = NumberFormat.getIntegerInstance(L10n.locale()).format(n)

/** Short counts: 950, 1.2k, 34k, 5.6M, 1.2B, with the locale's decimal separator. */
fun formatCompact(n: Long, billionsDigits: Int = 1): String {
    val loc = L10n.locale()
    return when {
        n >= 1_000_000_000 -> L10n.str(R.string.number_billions, String.format(loc, "%.${billionsDigits}f", n / 1e9))
        n >= 1_000_000 -> L10n.str(R.string.number_millions, String.format(loc, "%.1f", n / 1e6))
        n >= 10_000 -> L10n.str(R.string.number_thousands, String.format(loc, "%.0f", n / 1e3))
        n >= 1_000 -> L10n.str(R.string.number_thousands, String.format(loc, "%.1f", n / 1e3))
        else -> n.toString()
    }
}

/** File sizes in the locale's number format: 512 B, 3.4 KB, 1.2 MB. [k] is 1000 or 1024. */
fun formatBytes(b: Long, k: Long = 1024): String {
    val loc = L10n.locale()
    return when {
        b < k -> L10n.str(R.string.size_bytes, b)
        b < k * k -> L10n.str(R.string.size_kb, String.format(loc, "%.1f", b / k.toDouble()))
        else -> L10n.str(R.string.size_mb, String.format(loc, "%.1f", b / (k * k).toDouble()))
    }
}
