package com.bu.paperclip

import com.bu.paperclip.ui.components.MdBlock
import com.bu.paperclip.ui.components.splitMarkdownBlocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownCodeTest {
    @Test fun fencedBlocksBecomeCodeBlocks() {
        val md = "**Legenda:**\n\n```\nLinha um\n\nLinha dois #fé\n```\n\nDepois."
        val blocks = splitMarkdownBlocks(md)
        assertEquals(3, blocks.size)
        assertEquals("**Legenda:**", (blocks[0] as MdBlock.Prose).text.trim())
        assertEquals(MdBlock.Code("Linha um\n\nLinha dois #fé", ""), blocks[1])
        assertEquals("Depois.", (blocks[2] as MdBlock.Prose).text.trim())
    }

    @Test fun languageAndTildeFencesAreKept() {
        val blocks = splitMarkdownBlocks("~~~kotlin\nval a = 1\n~~~")
        assertEquals(listOf(MdBlock.Code("val a = 1", "kotlin")), blocks)
    }

    @Test fun unclosedFenceRunsToTheEnd() {
        assertEquals(listOf(MdBlock.Code("texto\nsem fim", "")), splitMarkdownBlocks("```\ntexto\nsem fim"))
    }

    @Test fun tablesOutsideFencesAreStillTables() {
        val blocks = splitMarkdownBlocks("| a | b |\n|---|---|\n| 1 | 2 |\n\n```\n| x | y |\n```")
        assertTrue(blocks[0] is MdBlock.Table)
        assertEquals(MdBlock.Code("| x | y |", ""), blocks[1])
    }
}
