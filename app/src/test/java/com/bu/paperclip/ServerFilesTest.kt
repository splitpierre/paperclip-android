package com.bu.paperclip

import com.bu.paperclip.net.ServerFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerFilesTest {
    private val base = "https://paperclip.example.com"

    @Test fun relativeAttachmentAndAssetLinksResolveAgainstTheServer() {
        assertEquals("$base/api/attachments/abc/content", ServerFiles.contentUrl("/api/attachments/abc/content", base).toString())
        assertEquals("$base/api/assets/x-1/content", ServerFiles.contentUrl("/api/assets/x-1/content", base).toString())
        assertEquals("$base/api/attachments/abc/content?download=1", ServerFiles.contentUrl("/api/attachments/abc/content?download=1", base).toString())
    }

    @Test fun absoluteLinksMustBeOnTheSameOrigin() {
        assertEquals("$base/api/attachments/abc/content", ServerFiles.contentUrl("$base/api/attachments/abc/content", base).toString())
        assertNull(ServerFiles.contentUrl("https://elsewhere.example/api/attachments/abc/content", base))
        assertNull(ServerFiles.contentUrl("https://paperclip.example.com:8443/api/attachments/abc/content", base))
        assertNull(ServerFiles.contentUrl("http://10.0.2.2:3100/api/attachments/abc/content", "http://10.0.2.2:3101"))
        assertEquals("http://localhost:3100/api/assets/a/content", ServerFiles.contentUrl("http://localhost:3100/api/assets/a/content", "http://localhost:3100").toString())
    }

    @Test fun otherServerLinksAreNotFiles() {
        assertNull(ServerFiles.contentUrl("/LUM/issues/LUM-1", base))
        assertNull(ServerFiles.contentUrl("/api/attachments/abc", base))
        assertNull(ServerFiles.contentUrl("/api/issues/abc/attachments", base))
        assertNull(ServerFiles.contentUrl("agent://abc", base))
        assertNull(ServerFiles.contentUrl("/api/attachments/abc/content", ""))
    }

    @Test fun serverUnderAPathPrefix() {
        val sub = "https://example.com/paperclip"
        assertEquals("$sub/api/attachments/abc/content", ServerFiles.contentUrl("$sub/api/attachments/abc/content", sub).toString())
        assertNull(ServerFiles.contentUrl("https://example.com/api/attachments/abc/content", sub))
    }

    @Test fun mimeTypeUsesTheDeclaredTypeWhenItIsSpecific() {
        assertEquals("application/pdf", ServerFiles.mimeType("a.bin", "application/pdf"))
        assertEquals("text/markdown", ServerFiles.mimeType("notes.md", "text/markdown; charset=utf-8"))
        assertEquals("image/png", ServerFiles.mimeType(null, "IMAGE/PNG"))
    }

    @Test fun mimeTypeFallsBackToTheExtension() {
        assertEquals("image/png", ServerFiles.mimeType("shot.PNG", null))
        assertEquals("application/pdf", ServerFiles.mimeType("spec.pdf", "application/octet-stream"))
        assertEquals("text/markdown", ServerFiles.mimeType("README.md", ""))
        assertEquals("video/x-matroska", ServerFiles.mimeType("clip.mkv", null) { if (it == "mkv") "video/x-matroska" else null })
        assertEquals("application/octet-stream", ServerFiles.mimeType("fixtures.orbitpkg", null))
        assertEquals("application/octet-stream", ServerFiles.mimeType(null, null))
    }

    @Test fun kinds() {
        assertTrue(ServerFiles.isImage("image/jpeg"))
        assertFalse(ServerFiles.isImage("image/svg+xml"))
        assertTrue(ServerFiles.isMarkdown("text/plain", "notes.markdown"))
        assertTrue(ServerFiles.isText("text/plain"))
        assertTrue(ServerFiles.isText("application/json"))
        assertFalse(ServerFiles.isText("text/html"))
        assertFalse(ServerFiles.isText("application/pdf"))
    }

    @Test fun filenameFromContentDisposition() {
        assertEquals("report.pdf", ServerFiles.filenameFromDisposition("inline; filename=\"report.pdf\""))
        assertEquals("plain.txt", ServerFiles.filenameFromDisposition("attachment; filename=plain.txt"))
        assertEquals("relatório final.pdf", ServerFiles.filenameFromDisposition("attachment; filename=\"relat_rio final.pdf\"; filename*=UTF-8''relat%C3%B3rio%20final.pdf"))
        assertNull(ServerFiles.filenameFromDisposition("inline"))
        assertNull(ServerFiles.filenameFromDisposition(null))
    }

    @Test fun safeFilenameKeepsOneReadableSegment() {
        assertEquals("passwd", ServerFiles.safeFilename("../../etc/passwd"))
        assertEquals("a_b.txt", ServerFiles.safeFilename("a:b.txt"))
        assertEquals("relatório final.pdf", ServerFiles.safeFilename("relatório final.pdf"))
        assertEquals("file", ServerFiles.safeFilename(".."))
        assertEquals("file", ServerFiles.safeFilename(null))
    }
}
