package com.bu.paperclip

import com.bu.paperclip.data.Agent
import com.bu.paperclip.ui.screens.appendSpoken
import com.bu.paperclip.ui.screens.defaultQuickAssignee
import com.bu.paperclip.ui.screens.quickTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickTaskTest {
    @Test fun titleIsTheFirstNonBlankLine() {
        assertEquals("Post for Sunday", quickTitle("\n  Post for Sunday  \nwith the photo from church"))
    }

    @Test fun longTitlesAreCutAtAWord() {
        val t = quickTitle("word ".repeat(40))
        assertTrue(t, t.length <= 81 && t.endsWith("…") && !t.contains("wor…"))
    }

    @Test fun defaultAssigneeIsTheHeadOfTheOrgChart() {
        val ceo = Agent(id = "c", role = "ceo")
        val writer = Agent(id = "w", reportsTo = "c")
        assertEquals("c", defaultQuickAssignee(listOf(writer, ceo)))
        // No CEO role: the single agent that reports to nobody.
        assertEquals("c", defaultQuickAssignee(listOf(writer, Agent(id = "c"))))
        // Two roots and no CEO: no guess.
        assertNull(defaultQuickAssignee(listOf(Agent(id = "a"), Agent(id = "b"))))
    }

    @Test fun dictationAppendsWithASpace() {
        assertEquals("Hello", appendSpoken("", "hello"))
        assertEquals("Make a post about grace", appendSpoken("Make a post ", "about grace"))
    }
}
