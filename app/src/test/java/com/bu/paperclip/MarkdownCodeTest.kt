package com.bu.paperclip

import com.bu.paperclip.ui.components.MdBlock
import com.bu.paperclip.ui.components.splitMarkdownBlocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownCodeTest {
    @Test fun fenceWithLanguage() {
        val blocks = splitMarkdownBlocks("Before\n\n```Kotlin\nval a = 1\n```\n\nAfter")
        assertEquals(3, blocks.size)
        assertEquals("Before", (blocks[0] as MdBlock.Prose).text.trim())
        assertEquals(MdBlock.Code("val a = 1", "kotlin"), blocks[1])
        assertEquals("After", (blocks[2] as MdBlock.Prose).text.trim())
    }

    @Test fun fenceWithoutLanguage() {
        assertEquals(listOf(MdBlock.Code("Linha um\n\nLinha dois #fé", "")), splitMarkdownBlocks("```\nLinha um\n\nLinha dois #fé\n```"))
    }

    @Test fun inlineTripleBacktickLineDoesNotOpenAFence() {
        val md = "Use ```foo``` here\n```foo```\nand the rest of the comment"
        val blocks = splitMarkdownBlocks(md)
        assertEquals(1, blocks.size)
        assertEquals(md, (blocks[0] as MdBlock.Prose).text)
    }

    @Test fun unclosedFenceRunsToTheEnd() {
        assertEquals(listOf(MdBlock.Code("texto\nsem fim", "")), splitMarkdownBlocks("```\ntexto\nsem fim"))
    }

    @Test fun nestedBackticksInsideAFence() {
        val blocks = splitMarkdownBlocks("````md\nuse `x` or\n```\ninner\n```\n````\nafter")
        assertEquals(MdBlock.Code("use `x` or\n```\ninner\n```", "md"), blocks[0])
        assertEquals("after", (blocks[1] as MdBlock.Prose).text.trim())
        // a shorter line of backticks does not close a longer fence
        assertEquals(listOf(MdBlock.Code("a\n``\nb", "")), splitMarkdownBlocks("```\na\n``\nb\n```"))
    }

    @Test fun tablesOutsideFencesAreStillTables() {
        val blocks = splitMarkdownBlocks("| a | b |\n|---|---|\n| 1 | 2 |\n\n```\n| x | y |\n```")
        assertTrue(blocks[0] is MdBlock.Table)
        assertEquals(MdBlock.Code("| x | y |", ""), blocks[1])
    }
}
