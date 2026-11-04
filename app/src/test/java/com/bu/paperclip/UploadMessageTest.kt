package com.bu.paperclip

import com.bu.paperclip.ui.screens.UploadMessages
import com.bu.paperclip.ui.screens.uploadFailureMessage as message
import org.junit.Assert.assertEquals
import org.junit.Test

class UploadMessageTest {
    // The English resources from strings_issues.xml (unit tests run without Android resources).
    private val en = UploadMessages(
        created = { "Created $it" },
        oneFailed = { task, file, reason -> "Created $task, but $file didn't upload: $reason" },
        someFailed = { task, n, total, list -> "Created $task, but $n of $total files didn't upload: $list" },
    )
    private fun uploadFailureMessage(task: String, failed: List<Pair<String, String>>, total: Int) = message(task, failed, total, en)

    @Test fun noFailuresJustConfirmsTheTask() {
        assertEquals("Created LUM-1", uploadFailureMessage("LUM-1", emptyList(), 2))
        assertEquals("Created LUM-1", uploadFailureMessage("LUM-1", emptyList(), 0))
    }

    @Test fun oneFailureNamesTheFileAndWhy() {
        assertEquals(
            "Created LUM-1, but spec.pdf didn't upload: Attachment is larger than the 10 MB limit",
            uploadFailureMessage("LUM-1", listOf("spec.pdf" to "Attachment is larger than the 10 MB limit"), 2),
        )
    }

    @Test fun severalFailuresNameEveryFile() {
        assertEquals(
            "Created LUM-1, but 2 of 3 files didn't upload: a.png (Network error); b.pdf (Attachment is empty)",
            uploadFailureMessage("LUM-1", listOf("a.png" to "Network error", "b.pdf" to "Attachment is empty"), 3),
        )
    }
}
