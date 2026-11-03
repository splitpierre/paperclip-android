package com.bu.paperclip.util

import com.bu.paperclip.R
import java.time.DayOfWeek
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
    enum class Preset(val label: String) { Hourly(appString(R.string.every_hour)), Daily(appString(R.string.every_day)), Weekdays(appString(R.string.weekdays)), Weekly(appString(R.string.weekly)), Monthly(appString(R.string.monthly)), Custom(appString(R.string.custom_cron)) }

    fun toCron(): String = when (preset) {
        Preset.Hourly -> "$minute * * * *"
        Preset.Daily -> "$minute $hour * * *"
        Preset.Weekdays -> "$minute $hour * * 1-5"
        Preset.Weekly -> "$minute $hour * * $weekday"
        Preset.Monthly -> "$minute $hour $dayOfMonth * *"
        Preset.Custom -> custom.trim()
    }

    companion object {
        /** Cron order, in the device language. */
        val WEEKDAYS = (0..6).map { DayOfWeek.of(if (it == 0) 7 else it).getDisplayName(TextStyle.FULL, Locale.getDefault()).replaceFirstChar { c -> c.uppercase() } }

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

        /** "9:00 AM" in English; translations pick their own form from the same arguments (pt-BR: "09:00"). */
        fun clock(hour: Int, minute: Int): String {
            val h12 = if (hour % 12 == 0) 12 else hour % 12
            return appString(R.string.clock_time, h12, minute, if (hour < 12) "AM" else "PM", hour)
        }

        /** "Every day at 9:00 AM", "Weekdays at 6:30 PM", "Every Monday at …", or the raw cron. */
        fun describe(cron: String?): String {
            val s = parse(cron)
            return when (s.preset) {
                Preset.Hourly -> if (s.minute == 0) appString(R.string.every_hour) else appString(R.string.every_hour_at_02d).format(s.minute)
                Preset.Daily -> appString(R.string.every_day_at_x, clock(s.hour, s.minute))
                Preset.Weekdays -> appString(R.string.weekdays_at_x, clock(s.hour, s.minute))
                Preset.Weekly -> appString(R.string.every_x_at_x, WEEKDAYS[s.weekday], clock(s.hour, s.minute))
                Preset.Monthly -> appString(R.string.monthly_on_day_x_at_x, s.dayOfMonth, clock(s.hour, s.minute))
                Preset.Custom -> if (s.custom == "* * * * *") appString(R.string.every_minute) else appString(R.string.cron_x, s.custom)
            }
        }

        private val cronField = Regex("""^(\*|\d+(-\d+)?)(/\d+)?(,(\*|\d+(-\d+)?)(/\d+)?)*$""")

        /** The web UI's checks; null when valid. The server still has the final word. */
        fun validate(cron: String): String? {
            val f = cron.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            return when {
                f.isEmpty() -> appString(R.string.enter_a_5_field_cron_expression)
                f.size != 5 -> appString(R.string.use_exactly_5_fields_this_has, f.size)
                f.any { !cronField.matches(it) } -> appString(R.string.cron_fields_must_use_valid_numbers)
                else -> null
            }
        }
    }
}
