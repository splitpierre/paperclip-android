package com.bu.paperclip

import com.bu.paperclip.data.*
import com.bu.paperclip.net.AppJson
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class TaskGraphTest {
    private fun issue(id: String, status: String = "todo", parent: String? = null, priority: String = "medium", blockedBy: List<String> = emptyList(), n: Int = 0) =
        Issue(id = id, identifier = "T-$n", issueNumber = n, title = id, status = status, parentId = parent, priority = priority,
            blockedBy = blockedBy.map { IssueSummaryRef(id = it) })

    private fun graph(vararg i: Issue): TaskGraph {
        val map = i.associateBy { it.id }
        return TaskGraph(i.toList(), MatchCtx(null, emptySet()) { map[it]?.status })
    }

    @Test
    fun nextUpSkipsBlockedParentsAndClosed() {
        val g = graph(
            issue("epic", "in_progress", n = 1),
            issue("a", "done", "epic", n = 2),
            issue("b", "todo", "epic", blockedBy = listOf("c"), n = 3),
            issue("c", "todo", "epic", priority = "low", n = 4),
            issue("d", "backlog", "epic", priority = "high", n = 5),
            issue("e", "todo", "d", n = 6),
        )
        val next = g.nextUp("epic").map { it.issue.id }
        // d has an open child (e) so e is the actionable one; b is held by c; c unblocks b.
        assertEquals(listOf("e", "c"), next)
        assertEquals(1, g.nextUp("epic").first { it.issue.id == "c" }.unblocks)
    }

    @Test
    fun forestKeepsAncestorsAsContext() {
        val g = graph(issue("root", "done", n = 1), issue("mid", "done", "root", n = 2), issue("leaf", "todo", "mid", n = 3))
        val f = g.forest(TaskFilter())
        assertEquals("root", f.single().issue.id)
        assertTrue(!f.single().matched)
        assertEquals("leaf", f.single().children.single().children.single().issue.id)
        assertEquals(2, f.single().rollup.total)
        assertEquals(1, f.single().rollup.closed)
    }

    @Test
    fun largeForestRollupsAgreeWithDescendantScan() {
        val issues = (0 until 2000).map { n ->
            issue("n$n", if (n % 3 == 0) "done" else "todo", if (n == 0) null else "n${(n - 1) / 4}", n = n)
        }
        val g = graph(*issues.toTypedArray())
        issues.forEach { i ->
            val descendants = g.descendants(i.id)
            assertEquals(descendants.size, g.rollup(i.id).total)
            assertEquals(descendants.count { isClosed(it.status) }, g.rollup(i.id).closed)
        }
    }

    @Test
    fun realCompanyGraph() {
        val f = System.getenv("PAPERCLIP_FIXTURES")?.let { File(it, "allissues.json") }
        assumeTrue(f?.exists() == true)
        val issues = AppJson.decodeFromString(ListSerializer(Issue.serializer()), f!!.readText())
        val byId = issues.associateBy { it.id }
        val g = TaskGraph(issues, MatchCtx(null, emptySet()) { byId[it]?.status })
        val forest = g.forest(TaskFilter())
        fun count(n: TaskNode): Int = 1 + n.children.sumOf { count(it) }
        println("issues=${issues.size} roots(active filter)=${forest.size} visibleNodes=${forest.sumOf { count(it) }}")
        g.nextUp(limit = 8).forEach { println("NEXT ${it.issue.identifier} ${it.issue.status}/${it.issue.priority} ${it.reasons} :: ${it.issue.title.take(60)}") }
        println("inFlight=" + g.inFlight().map { it.identifier })
        val biggest = forest.maxByOrNull { it.rollup.total }
        println("biggest root ${biggest?.issue?.identifier} total=${biggest?.rollup?.total} closed=${biggest?.rollup?.closed}")
    }
}
