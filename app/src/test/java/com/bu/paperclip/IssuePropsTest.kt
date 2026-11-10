package com.bu.paperclip

import com.bu.paperclip.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IssuePropsTest {
    private fun issue(id: String, parent: String? = null, status: String = "todo", n: Int = 0, updated: String = "2026-01-01T00:00:00Z", blockedBy: List<String> = emptyList()) =
        Issue(id = id, identifier = "T-$n", issueNumber = n, title = "Task $id", status = status, parentId = parent, updatedAt = updated,
            blockedBy = blockedBy.map { IssueSummaryRef(id = it) })

    private val tree = listOf(
        issue("root", n = 1),
        issue("a", "root", n = 2),
        issue("a1", "a", n = 3),
        issue("a2", "a", n = 4),
        issue("a1x", "a1", n = 5),
        issue("b", "root", n = 6),
        issue("other", n = 7),
    )

    @Test
    fun descendantsAreTransitive() {
        assertEquals(setOf("a1", "a2", "a1x"), descendantIds(tree, "a"))
        assertEquals(emptySet<String>(), descendantIds(tree, "other"))
    }

    @Test
    fun descendantsSurviveCycles() {
        val cyclic = listOf(issue("x", "y"), issue("y", "x"))
        assertEquals(setOf("y"), descendantIds(cyclic, "x"))
    }

    @Test
    fun parentCandidatesExcludeSelfAndDescendants() {
        val self = tree.first { it.id == "a" }
        val ids = parentCandidates(tree, self, "").map { it.id }.toSet()
        assertEquals(setOf("root", "b", "other"), ids)
    }

    @Test
    fun parentCandidatesFilterByIdentifierOrTitle() {
        val self = tree.first { it.id == "b" }
        assertEquals(listOf("a1x"), parentCandidates(tree, self, "t-5").map { it.id })
        assertEquals(listOf("other"), parentCandidates(tree, self, "Task other").map { it.id })
    }

    @Test
    fun parentCandidatesPutOpenAndRecentFirst() {
        val list = listOf(
            issue("self", n = 1),
            issue("old", n = 2, updated = "2026-01-01T00:00:00Z"),
            issue("new", n = 3, updated = "2026-03-01T00:00:00Z"),
            issue("closed", n = 4, status = "done", updated = "2026-04-01T00:00:00Z"),
        )
        assertEquals(listOf("new", "old", "closed"), parentCandidates(list, list[0], "").map { it.id })
    }

    @Test
    fun blockerCandidatesListCurrentBlockersFirstAndNeverSelf() {
        val list = listOf(issue("self", n = 1, blockedBy = listOf("old")), issue("old", n = 2), issue("new", n = 3, updated = "2026-05-01T00:00:00Z"))
        assertEquals(listOf("old", "new"), blockerCandidates(list, list[0], "").map { it.id })
    }

    @Test
    fun toggleAddsAndRemoves() {
        assertEquals(listOf("a", "b"), toggleId(listOf("a"), "b"))
        assertEquals(listOf("b"), toggleId(listOf("a", "b"), "a"))
    }

    @Test
    fun reparentRebuildsBreadcrumbNearestFirst() {
        val byId = tree.associateBy { it.id }
        val moved = tree.first { it.id == "other" }.withParent("a1", byId)
        assertEquals("a1", moved.parentId)
        assertEquals(listOf("a1", "a", "root"), moved.ancestors!!.map { it.id })
        val detached = moved.withParent(null, byId)
        assertEquals(null, detached.parentId)
        assertTrue(detached.ancestors!!.isEmpty())
    }

    @Test
    fun reparentShowsUpInTheTaskGraphAtOnce() {
        val byId = tree.associateBy { it.id }.toMutableMap()
        byId["other"] = byId.getValue("other").withParent("b", byId)
        val g = TaskGraph(byId.values, MatchCtx(null, emptySet()) { byId[it]?.status })
        assertEquals(listOf("other"), g.childrenOf["b"]!!.map { it.id })
        assertFalse(g.forest(TaskFilter(), null).any { it.issue.id == "other" })
    }

    @Test
    fun blockersUseStoreDetails() {
        val byId = tree.associateBy { it.id }
        val i = tree.first { it.id == "other" }.withBlockers(listOf("b", "missing"), byId)
        assertEquals("T-6", i.blockedBy!![0].identifier)
        assertEquals("missing", i.blockedBy!![1].id)
    }

    @Test
    fun labelsFollowIds() {
        val all = listOf(Label("l1", "bug"), Label("l2", "ux"))
        val i = tree[0].withLabels(listOf("l2"), all)
        assertEquals(listOf("l2"), i.labelIds)
        assertEquals(listOf("ux"), i.labels!!.map { it.name })
    }
}
