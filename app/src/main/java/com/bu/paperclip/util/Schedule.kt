package com.bu.paperclip.util

import androidx.annotation.StringRes
import com.bu.paperclip.R
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * Routine schedules as the web UI's presets (every hour/day/weekday/week/month, or custom cron).
 * Weekdays use cron numbering: 0 = Sunday … 6 = Saturday.
 */
data class Schedule(
    val preset: Preset,
    val minute: Int = 0,
    val hour: Int = 9,
    val weekday: Int = 1,
    val dayOfMonth: Int = 1,
    val custom: String = "",
) {
    enum class Preset(@StringRes val labelRes: Int) {
        Hourly(R.string.schedule_preset_hourly), Daily(R.string.schedule_preset_daily), Weekdays(R.string.schedule_preset_weekdays),
        Weekly(R.string.schedule_preset_weekly), Monthly(R.string.schedule_preset_monthly), Custom(R.string.schedule_preset_custom);

        val label: String get() = L10n.str(labelRes)
    }

    /** Why a cron expression is rejected; see [validate]. */
    sealed interface CronError {
        data object Empty : CronError
        data class FieldCount(val count: Int) : CronError
        data object BadField : CronError
    }

    fun toCron(): String = when (preset) {
        Preset.Hourly -> "$minute * * * *"
        Preset.Daily -> "$minute $hour * * *"
        Preset.Weekdays -> "$minute $hour * * 1-5"
        Preset.Weekly -> "$minute $hour * * $weekday"
        Preset.Monthly -> "$minute $hour $dayOfMonth * *"
        Preset.Custom -> custom.trim()
    }

    companion object {
        /** Weekday names in the app locale, Sunday first (cron numbering). */
        val WEEKDAYS: List<String> get() = (0..6).map { weekdayName(it) }

        fun weekdayName(cronDay: Int, locale: Locale = L10n.locale()): String =
            DayOfWeek.of(if (cronDay % 7 == 0) 7 else cronDay % 7).getDisplayName(TextStyle.FULL, locale)

        private fun num(s: String, range: IntRange): Int? = s.toIntOrNull()?.takeIf { it in range }

        /** Reads a cron string back into a preset; anything else is Custom. */
        fun parse(cron: String?): Schedule {
            val c = cron?.trim().orEmpty()
            val f = c.split(Regex("\\s+"))
            if (f.size != 5) return Schedule(Preset.Custom, custom = c)
            val (mi, h, dom, mon, dow) = f
            val minute = num(mi, 0..59) ?: return Schedule(Preset.Custom, custom = c)
            if (mon != "*") return Schedule(Preset.Custom, custom = c)
            if (h == "*" && dom == "*" && dow == "*") return Schedule(Preset.Hourly, minute = minute)
            val hour = num(h, 0..23) ?: return Schedule(Preset.Custom, custom = c)
            return when {
                dom == "*" && dow == "*" -> Schedule(Preset.Daily, minute, hour)
                dom == "*" && dow == "1-5" -> Schedule(Preset.Weekdays, minute, hour)
                dom == "*" && num(dow, 0..7) != null -> Schedule(Preset.Weekly, minute, hour, weekday = num(dow, 0..7)!! % 7)
                dow == "*" && num(dom, 1..31) != null -> Schedule(Preset.Monthly, minute, hour, dayOfMonth = num(dom, 1..31)!!)
                else -> Schedule(Preset.Custom, custom = c)
            }
        }

        /** A wall-clock time in the locale's short style, e.g. "6:30 PM" or "18:30". */
        fun clock(hour: Int, minute: Int, locale: Locale = L10n.locale()): String =
            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(LocalTime.of(hour, minute))

        /** "Every day at 9:00 AM", "Weekdays at 6:30 PM", "Every Monday at …", or the raw cron. */
        fun describe(cron: String?): String {
            val s = parse(cron)
            return when (s.preset) {
                Preset.Hourly -> if (s.minute == 0) L10n.str(R.string.schedule_every_hour) else L10n.str(R.string.schedule_every_hour_at, s.minute)
                Preset.Daily -> L10n.str(R.string.schedule_daily_at, clock(s.hour, s.minute))
                Preset.Weekdays -> L10n.str(R.string.schedule_weekdays_at, clock(s.hour, s.minute))
                Preset.Weekly -> L10n.str(R.string.schedule_weekly_at, weekdayName(s.weekday), clock(s.hour, s.minute))
                Preset.Monthly -> L10n.str(R.string.schedule_monthly_at, s.dayOfMonth, clock(s.hour, s.minute))
                Preset.Custom -> if (s.custom == "* * * * *") L10n.str(R.string.schedule_every_minute) else L10n.str(R.string.schedule_cron, s.custom)
            }
        }

        private val cronField = Regex("""^(\*|\d+(-\d+)?)(/\d+)?(,(\*|\d+(-\d+)?)(/\d+)?)*$""")

        /** The web UI's checks; null when valid. The server still has the final word. */
        fun check(cron: String): CronError? {
            val f = cron.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            return when {
                f.isEmpty() -> CronError.Empty
                f.size != 5 -> CronError.FieldCount(f.size)
                f.any { !cronField.matches(it) } -> CronError.BadField
                else -> null
            }
        }

        /** [check] as a message for the user; null when valid. */
        fun validate(cron: String): String? = when (val e = check(cron)) {
            null -> null
            CronError.Empty -> L10n.str(R.string.schedule_error_empty)
            is CronError.FieldCount -> L10n.str(R.string.schedule_error_field_count, e.count)
            CronError.BadField -> L10n.str(R.string.schedule_error_bad_field)
        }
    }
}
