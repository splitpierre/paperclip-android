package com.bu.paperclip

import androidx.compose.ui.text.style.TextAlign
import com.bu.paperclip.data.AttentionItem
import com.bu.paperclip.ui.components.MdBlock
import com.bu.paperclip.ui.components.splitMarkdownTables
import com.bu.paperclip.ui.components.splitTableRow
import com.bu.paperclip.ui.screens.Shelf
import com.bu.paperclip.ui.screens.decideByDeadline
import com.bu.paperclip.ui.screens.shelfOf
import com.bu.paperclip.util.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.Locale

class ReleaseLogicTest {
    // ---- markdown tables ----

    @Test fun tablesAreSplitFromProse() {
        val md = """
            Intro paragraph.

            | Name | Status | Notes |
            |:-----|:------:|------:|
            | `a|b` | **done** | escaped \| pipe |
            | short |

            After the table.
        """.trimIndent()
        val blocks = splitMarkdownTables(md)
        assertEquals(3, blocks.size)
        assertEquals("Intro paragraph.", (blocks[0] as MdBlock.Prose).text.trim())
        val t = blocks[1] as MdBlock.Table
        assertEquals(listOf("Name", "Status", "Notes"), t.header)
        assertEquals(listOf(TextAlign.Start, TextAlign.Center, TextAlign.End), t.align)
        assertEquals(listOf("`a|b`", "**done**", "escaped | pipe"), t.rows[0])
        assertEquals(listOf("short", "", ""), t.rows[1]) // short rows are padded
        assertEquals("After the table.", (blocks[2] as MdBlock.Prose).text.trim())
    }

    @Test fun tablesInsideCodeFencesStayProse() {
        val md = "```\n| a | b |\n|---|---|\n| 1 | 2 |\n```"
        val blocks = splitMarkdownTables(md)
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MdBlock.Prose)
    }

    @Test fun pipesWithoutDelimiterRowAreNotTables() {
        assertEquals(1, splitMarkdownTables("a | b\nc | d").size)
        assertEquals(listOf("a", "b"), splitTableRow("| a | b |"))
        assertEquals(listOf("a", "b"), splitTableRow("a | b"))
    }

    // ---- routine schedules ----

    @Test fun cronPresetsRoundTrip() {
        listOf("0 9 * * *", "30 18 * * 1-5", "15 8 * * 1", "0 10 5 * *", "45 * * * *").forEach { cron ->
            val s = Schedule.parse(cron)
            assertTrue("$cron → ${s.preset}", s.preset != Schedule.Preset.Custom)
            assertEquals(cron, s.toCron())
        }
        assertEquals(Schedule.Preset.Custom, Schedule.parse("*/15 9-17 * * 1-5").preset)
        assertEquals(Schedule(Schedule.Preset.Weekdays, 30, 18), Schedule.parse("30 18 * * 1-5"))
        assertEquals(Schedule(Schedule.Preset.Weekly, 15, 8, weekday = 1), Schedule.parse("15 8 * * 1"))
        assertEquals(Schedule(Schedule.Preset.Weekly, 0, 9, weekday = 0), Schedule.parse("0 9 * * 7")) // 7 is Sunday too
        assertEquals("Monday", Schedule.weekdayName(1, Locale.US))
        assertEquals("Sunday", Schedule.weekdayName(0, Locale.US))
        assertEquals("6:30 PM", Schedule.clock(18, 30, Locale.US).replace('\u202F', ' '))
        assertEquals("12:00 AM", Schedule.clock(0, 0, Locale.US).replace('\u202F', ' '))
        assertEquals("18:30", Schedule.clock(18, 30, Locale.forLanguageTag("pt-BR")))
    }

    @Test fun cronValidation() {
        assertNull(Schedule.check("*/15 9-17 * * 1-5"))
        assertEquals(Schedule.CronError.FieldCount(4), Schedule.check("0 9 * *"))
        assertEquals(Schedule.CronError.BadField, Schedule.check("0 nine * * *"))
    }

    // ---- decisions desk ----

    private val now = Instant.parse("2026-10-20T15:00:00Z") // a Tuesday

    private fun item(createdAt: String, decideBy: String? = null) = AttentionItem(id = createdAt + decideBy, createdAt = createdAt, decideBy = decideBy)

    @Test fun deskShelvesFollowTheWebRules() {
        assertEquals(Shelf.DecideNow, shelfOf(item("2026-10-01T00:00:00Z", "today"), now))
        assertEquals(Shelf.DecideNow, shelfOf(item("2026-10-01T00:00:00Z", "2026-10-19"), now)) // overdue
        assertEquals(Shelf.NewToday, shelfOf(item("2026-10-20T01:00:00Z", "this_week"), now))
        assertEquals(Shelf.Earlier, shelfOf(item("2026-10-19T23:59:00Z", "whenever"), now))
        assertEquals(Instant.parse("2026-10-25T23:59:59.999Z"), decideByDeadline("this_week", now)) // Sunday
        assertNull(decideByDeadline("whenever", now))
    }
}
