package com.bu.paperclip

import com.bu.paperclip.data.AnswerDraft
import com.bu.paperclip.data.CommentDraft
import com.bu.paperclip.data.DraftFile
import com.bu.paperclip.data.DraftStore
import com.bu.paperclip.data.IssueDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DraftStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val server = "https://paperclip.example.test"
    private val company = "company-1"
    private fun store() = DraftStore(File(tmp.root, "drafts"))

    @Test fun savesAndRestoresANewTaskDraft() {
        val s = store()
        val d = IssueDraft(
            title = "Fix login", description = "Steps…", status = "backlog", priority = "high", workMode = "planning",
            assigneeAgentId = "agent-1", projectId = "project-1", parentId = "parent-1",
            files = listOf(DraftFile("content://docs/1", "log.txt", 12, "text/plain")),
        )
        s.save(server, company, DraftStore.newIssueKey("parent-1"), d, IssueDraft.serializer())
        assertEquals(d, s.load(server, company, DraftStore.newIssueKey("parent-1"), IssueDraft.serializer()))
    }

    @Test fun survivesANewStoreInstance() {
        // A new instance over the same directory stands in for an app restart or sign-in again.
        store().save(server, company, DraftStore.commentKey("issue-1"), CommentDraft("half a reply"), CommentDraft.serializer())
        assertEquals("half a reply", store().load(server, company, DraftStore.commentKey("issue-1"), CommentDraft.serializer())?.body)
    }

    @Test fun clearRemovesOnlyThatDraft() {
        val s = store()
        s.save(server, company, DraftStore.commentKey("a"), CommentDraft("A"), CommentDraft.serializer())
        s.save(server, company, DraftStore.commentKey("b"), CommentDraft("B"), CommentDraft.serializer())
        s.clear(server, company, DraftStore.commentKey("a"))
        assertNull(s.load(server, company, DraftStore.commentKey("a"), CommentDraft.serializer()))
        assertEquals("B", s.load(server, company, DraftStore.commentKey("b"), CommentDraft.serializer())?.body)
        s.clear(server, company, DraftStore.commentKey("missing")) // no-op, no throw
    }

    @Test fun overwritesThePreviousVersion() {
        val s = store()
        val k = DraftStore.newIssueKey(null)
        s.save(server, company, k, IssueDraft(title = "one"), IssueDraft.serializer())
        s.save(server, company, k, IssueDraft(title = "two"), IssueDraft.serializer())
        assertEquals("two", s.load(server, company, k, IssueDraft.serializer())?.title)
        assertFalse(File(tmp.root, "drafts").walk().any { it.name.endsWith(".tmp") })
    }

    @Test fun keyedByServerAndCompany() {
        val s = store()
        val k = DraftStore.commentKey("issue-1")
        s.save(server, company, k, CommentDraft("mine"), CommentDraft.serializer())
        assertNull(s.load(server, "company-2", k, CommentDraft.serializer()))
        assertNull(s.load("https://other.example.test", company, k, CommentDraft.serializer()))
        assertNotEquals(s.scope(server, company), s.scope(server, "company-2"))
    }

    @Test fun serverUrlIsNormalised() {
        val s = store()
        val k = DraftStore.commentKey("issue-1")
        s.save("https://Paperclip.Example.test/", company, k, CommentDraft("x"), CommentDraft.serializer())
        assertEquals("x", s.load(server, company, k, CommentDraft.serializer())?.body)
        assertEquals(s.scope(server, company), s.scope("$server/", company))
    }

    @Test fun nothingIsStoredWithoutServerOrCompany() {
        val s = store()
        s.save("", company, "k", CommentDraft("x"), CommentDraft.serializer())
        s.save(server, "", "k", CommentDraft("x"), CommentDraft.serializer())
        assertFalse(File(tmp.root, "drafts").exists())
        assertTrue(s.keys("", company).isEmpty())
    }

    @Test fun listsTasksWithAReplyDraft() {
        val s = store()
        s.save(server, company, DraftStore.commentKey("issue-1"), CommentDraft("x"), CommentDraft.serializer())
        s.save(server, company, DraftStore.newIssueKey(null), IssueDraft(title = "t"), IssueDraft.serializer())
        s.save(server, company, DraftStore.answerKey("int-1"), AnswerDraft(otherText = mapOf("q" to "a")), AnswerDraft.serializer())
        assertEquals(setOf("issue-1"), DraftStore.commentIssueIds(s.keys(server, company)))
    }

    @Test fun keysCannotEscapeTheDraftsDirectory() {
        val s = store()
        s.save(server, company, "../../evil", CommentDraft("x"), CommentDraft.serializer())
        val drafts = File(tmp.root, "drafts").canonicalFile
        assertTrue(tmp.root.walk().filter { it.isFile }.all { it.canonicalPath.startsWith(drafts.path) })
    }

    @Test fun unreadableFileIsIgnored() {
        val s = store()
        val k = DraftStore.commentKey("issue-1")
        s.save(server, company, k, CommentDraft("x"), CommentDraft.serializer())
        File(File(File(tmp.root, "drafts"), s.scope(server, company)), "$k.json").writeText("{not json")
        assertNull(s.load(server, company, k, CommentDraft.serializer()))
    }

    @Test fun emptinessRules() {
        assertTrue(IssueDraft(priority = "high", projectId = "p").isEmpty)
        assertFalse(IssueDraft(title = "t").isEmpty)
        assertFalse(IssueDraft(files = listOf(DraftFile("content://x", "x"))).isEmpty)
        assertTrue(CommentDraft("  ").isEmpty)
        assertTrue(CommentDraft("", listOf(DraftFile("content://x", "x"))).isEmpty) // not uploaded yet
        assertFalse(CommentDraft("", listOf(DraftFile("content://x", "x", attachmentId = "att-1"))).isEmpty)
        assertTrue(AnswerDraft(otherActive = mapOf("q" to true)).isEmpty)
        assertFalse(AnswerDraft(selected = mapOf("q" to listOf("o"))).isEmpty)
    }
}
