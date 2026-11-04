package com.bu.paperclip

import com.bu.paperclip.data.TEntry
import com.bu.paperclip.data.TextSanitizer
import com.bu.paperclip.data.TranscriptBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSanitizerTest {
    private fun b64(n: Int) = buildString { repeat(n) { append("ABCDEFGHabcdefgh0123456789+/"[it % 28]) } }

    @Test fun dataUriBecomesPlaceholder() {
        val s = TextSanitizer.sanitize("see data:image/png;base64,${b64(40_000)} done")
        assertEquals("see [image image/png, ~29 KB] done", s)
    }

    @Test fun longRawBase64BecomesBinaryPlaceholder() {
        val s = TextSanitizer.sanitize("out: ${b64(8_000)}\nnext")
        assertEquals("out: [binary data, ~6 KB]\nnext", s)
    }

    @Test fun normalTextIsUntouched() {
        val text = "id 3f2b8c1e-9d4a-4c7e-8a1b-2f6d5e0c9b7a, sha256 " + "a".repeat(64) +
            " token sk_abc123XYZ and path /usr/local/bin/some/long/path/" + "x".repeat(300) + " fin"
        assertEquals(text, TextSanitizer.sanitize(text))
    }

    @Test fun shortBase64LikeRunsAreKept() {
        val text = "key=" + b64(1_900)
        assertEquals(text, TextSanitizer.sanitize(text))
    }

    @Test fun separatorLinesAreNotBinary() {
        val text = "=".repeat(5_000) + "\n" + "/".repeat(3_000)
        assertEquals(text, TextSanitizer.sanitize(text))
    }

    @Test fun capsLongText() {
        val s = TextSanitizer.sanitize("hello world ".repeat(5_000))
        assertTrue(s.length < TextSanitizer.MAX_BLOCK_CHARS + 100)
        assertTrue(s.contains("truncated"))
    }

    @Test fun builderStripsToolResultAndImageBlock() {
        val b = TranscriptBuilder("claude_local")
        val call = """{"type":"assistant","message":{"content":[{"type":"tool_use","id":"t1","name":"shot","input":{"a":1}}]}}"""
        val res = """{"type":"user","message":{"content":[{"type":"tool_result","tool_use_id":"t1","content":[{"type":"image","source":{"type":"base64","media_type":"image/png","data":"${b64(3_000_000)}"}},{"type":"text","text":"ok"}]}]}}"""
        b.feedRecord(null, "stdout", call + "\n" + res + "\n", 1)
        val tc = b.entries.filterIsInstance<TEntry.ToolCall>().single()
        assertTrue(tc.result!!.startsWith("[image image/png, ~2"))
        assertTrue(tc.result!!.endsWith("ok"))
    }

    @Test fun builderStripsPlainStdoutBlob() {
        val b = TranscriptBuilder(null)
        b.feedRecord(null, "stdout", "data:image/jpeg;base64,${b64(2_000_000)}\n", 1)
        val l = b.entries.filterIsInstance<TEntry.Line>().single()
        assertFalse(l.text.length > 100)
        assertTrue(l.text.startsWith("[image image/jpeg"))
    }
}
