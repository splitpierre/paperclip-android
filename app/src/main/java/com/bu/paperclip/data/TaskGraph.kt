package com.bu.paperclip.data

import kotlinx.serialization.Serializable

val ACTIVE_STATUSES = setOf("backlog", "todo", "in_progress", "in_review", "blocked")
val CLOSED_STATUSES = setOf("done", "cancelled")

fun isClosed(status: String) = status in CLOSED_STATUSES

private val statusRank = mapOf("in_progress" to 0, "in_review" to 1, "todo" to 2, "blocked" to 3, "backlog" to 4, "done" to 5, "cancelled" to 6)
private val priorityRank = mapOf("critical" to 0, "high" to 1, "medium" to 2, "low" to 3)
fun priorityRankOf(p: String) = priorityRank[p] ?: 2
fun statusRankOf(s: String) = statusRank[s] ?: 9

/** Every filter dimension; an empty set means "any". Persisted as JSON. */
@Serializable
data class TaskFilter(
    val statuses: Set<String> = ACTIVE_STATUSES,
    val priorities: Set<String> = emptySet(),
    /** Agent ids, "me", "unassigned" or "user:<id>". */
    val assignees: Set<String> = emptySet(),
    /** Project ids or "none". */
    val projects: Set<String> = emptySet(),
    val labels: Set<String> = emptySet(),
    val liveOnly: Boolean = false,
    val blockedOnly: Boolean = false,
    val hideRoutine: Boolean = false,
    val query: String = "",
    /** updated | priority | created | number */
    val sort: String = "updated",
    /** none | status | priority | assignee | project | parent */
    val groupBy: String = "status",
) {
    /** Count of non-default constraints, for the filter badge. */
    val activeCount: Int
        get() = listOf(
            statuses != ACTIVE_STATUSES, priorities.isNotEmpty(), assignees.isNotEmpty(), projects.isNotEmpty(),
            labels.isNotEmpty(), liveOnly, blockedOnly, hideRoutine,
        ).count { it }
}

data class MatchCtx(val meId: String?, val live: Set<String>, val blockerStatus: (String) -> String?)

fun Issue.isRoutineRun(): Boolean = originKind?.startsWith("routine") == true

/** True when an open blocker still holds this issue (blocker status read from the mirror when possible). */
fun Issue.openBlockers(ctx: MatchCtx): List<IssueSummaryRef> =
    blockedBy.orEmpty().filter { b -> !isClosed(ctx.blockerStatus(b.id) ?: b.status ?: "todo") }

fun TaskFilter.matches(i: Issue, ctx: MatchCtx): Boolean {
    if (statuses.isNotEmpty() && i.status !in statuses) return false
    if (priorities.isNotEmpty() && i.priority !in priorities) return false
    if (assignees.isNotEmpty()) {
        val ok = assignees.any { a ->
            when {
                a == "unassigned" -> i.assigneeAgentId == null && i.assigneeUserId == null
                a == "me" -> i.assigneeUserId != null && i.assigneeUserId == ctx.meId
                a.startsWith("user:") -> i.assigneeUserId == a.removePrefix("user:")
                else -> i.assigneeAgentId == a
            }
        }
        if (!ok) return false
    }
    if (projects.isNotEmpty()) {
        val p = i.projectId ?: "none"
        if (p !in projects) return false
    }
    if (labels.isNotEmpty()) {
        val ids = i.labelIds ?: i.labels?.map { it.id }.orEmpty()
        if (labels.none { it in ids }) return false
    }
    if (liveOnly && i.id !in ctx.live) return false
    if (blockedOnly && i.status != "blocked" && i.openBlockers(ctx).isEmpty()) return false
    if (hideRoutine && i.isRoutineRun()) return false
    if (query.isNotBlank()) {
        val q = query.trim().lowercase()
        val hay = (i.identifier.orEmpty() + " " + i.title).lowercase()
        if (q.split(' ').filter { it.isNotBlank() }.any { it !in hay }) return false
    }
    return true
}

fun Issue.activityKey(): String = lastActivityAt ?: updatedAt ?: createdAt ?: ""

fun TaskFilter.comparator(): Comparator<Issue> = when (sort) {
    "plan" -> siblingOrder
    "priority" -> compareBy<Issue>({ priorityRankOf(it.priority) }, { statusRankOf(it.status) }).thenByDescending { it.activityKey() }
    "created" -> compareByDescending { it.createdAt ?: "" }
    "number" -> compareByDescending { it.issueNumber ?: 0 }
    else -> compareByDescending { it.activityKey() }
}

/** Sibling order inside trees and kanban columns: what's moving first, then priority, then plan order. */
val siblingOrder: Comparator<Issue> = compareBy<Issue>({ statusRankOf(it.status) }, { priorityRankOf(it.priority) }, { it.issueNumber ?: Int.MAX_VALUE })

data class Rollup(val total: Int, val closed: Int, val inFlight: Int, val blocked: Int, val live: Int) {
    val open: Int get() = total - closed
    val progress: Float get() = if (total == 0) 0f else closed.toFloat() / total
}

class TaskNode(
    val issue: Issue,
    val depth: Int,
    val children: List<TaskNode>,
    /** False when the node is only shown as context for a matching descendant. */
    val matched: Boolean,
    /** Over all descendants, regardless of filters. */
    val rollup: Rollup,
)

class TaskGraph(issues: Collection<Issue>, val ctx: MatchCtx) {
    val byId: Map<String, Issue> = issues.associateBy { it.id }
    val childrenOf: Map<String, List<Issue>> = issues.filter { it.parentId != null && byId.containsKey(it.parentId) }.groupBy { it.parentId!! }
    /** issue id -> open issues it is blocking. */
    val blocking: Map<String, List<Issue>> = buildMap<String, MutableList<Issue>> {
        issues.filter { !isClosed(it.status) }.forEach { i -> i.blockedBy.orEmpty().forEach { b -> getOrPut(b.id) { mutableListOf() }.add(i) } }
    }
    private val rollups = java.util.concurrent.ConcurrentHashMap<String, Rollup>()

    init {
        // Bottom-up aggregation is linear for a forest; scanning every subtree was quadratic.
        val remaining = byId.keys.associateWith { childrenOf[it].orEmpty().size }.toMutableMap()
        val sums = byId.keys.associateWith { Rollup(0, 0, 0, 0, 0) }.toMutableMap()
        val ready = ArrayDeque(byId.keys.filter { remaining[it] == 0 })
        while (ready.isNotEmpty()) {
            val id = ready.removeFirst()
            val issue = byId.getValue(id)
            val subtotal = sums.getValue(id)
            rollups[id] = subtotal
            val parent = issue.parentId?.takeIf { it in byId } ?: continue
            val old = sums.getValue(parent)
            sums[parent] = Rollup(
                old.total + subtotal.total + 1,
                old.closed + subtotal.closed + if (isClosed(issue.status)) 1 else 0,
                old.inFlight + subtotal.inFlight + if (issue.status in setOf("in_progress", "in_review")) 1 else 0,
                old.blocked + subtotal.blocked + if (issue.status == "blocked") 1 else 0,
                old.live + subtotal.live + if (id in ctx.live) 1 else 0,
            )
            remaining[parent] = remaining.getValue(parent) - 1
            if (remaining[parent] == 0) ready.addLast(parent)
        }
        // Malformed cyclic input uses the existing cycle-safe descendant fallback.
    }

    fun children(id: String): List<Issue> = childrenOf[id].orEmpty()

    fun ancestors(i: Issue): List<Issue> {
        val out = ArrayList<Issue>()
        var p = i.parentId?.let { byId[it] }
        val seen = HashSet<String>()
        while (p != null && seen.add(p.id)) { out.add(0, p); p = p.parentId?.let { byId[it] } }
        return out
    }

    fun descendants(id: String): List<Issue> {
        val out = ArrayList<Issue>()
        val stack = ArrayDeque(children(id))
        val seen = HashSet<String>()
        while (stack.isNotEmpty()) {
            val c = stack.removeLast()
            if (!seen.add(c.id)) continue
            out.add(c)
            stack.addAll(children(c.id))
        }
        return out
    }

    fun rollup(id: String): Rollup = rollups.getOrPut(id) {
        val d = descendants(id)
        Rollup(
            total = d.size,
            closed = d.count { isClosed(it.status) },
            inFlight = d.count { it.status == "in_progress" || it.status == "in_review" },
            blocked = d.count { it.status == "blocked" },
            live = d.count { it.id in ctx.live },
        )
    }

    /**
     * Matching issues plus their ancestors (as dimmed context), as a forest. With [rootId], only that
     * issue's subtree is considered and its children become the roots.
     */
    fun forest(filter: TaskFilter, rootId: String? = null): List<TaskNode> {
        val scope: Collection<Issue> = if (rootId != null) descendants(rootId) else byId.values
        val scopeIds = scope.mapTo(HashSet()) { it.id }
        val matched = scope.filter { filter.matches(it, ctx) }.mapTo(HashSet()) { it.id }
        val visible = HashSet(matched)
        matched.forEach { id ->
            var p = byId[id]?.parentId
            while (p != null && p in scopeIds && visible.add(p)) p = byId[p]?.parentId
        }
        fun build(i: Issue, depth: Int): TaskNode = TaskNode(
            issue = i,
            depth = depth,
            children = children(i.id).filter { it.id in visible }.sortedWith(siblingOrder).map { build(it, depth + 1) },
            matched = i.id in matched,
            rollup = rollup(i.id),
        )
        val roots = visible.mapNotNull { byId[it] }.filter { it.parentId == null || it.parentId !in visible || it.parentId == rootId }
        val rootOrder = filter.comparator()
        return roots.sortedWith(rootOrder).map { build(it, 0) }
    }

    data class Next(val issue: Issue, val reasons: List<String>, val unblocks: Int)

    /**
     * What to do next in a scope: open (todo/backlog), not held by an open blocker, and with no open
     * sub-tasks of its own. Ranked by priority, how much it unblocks, todo before backlog, plan order.
     */
    fun nextUp(rootId: String? = null, limit: Int = 5, filter: TaskFilter? = null): List<Next> {
        val scope = if (rootId != null) descendants(rootId) else byId.values
        return scope.asSequence()
            .filter { it.status == "todo" || it.status == "backlog" }
            .filter { it.openBlockers(ctx).isEmpty() }
            .filter { i -> children(i.id).none { !isClosed(it.status) } }
            // A "done" parent can still have open children; only abandoned (cancelled) branches are skipped.
            .filter { i -> ancestors(i).none { it.status == "cancelled" } }
            .filter { filter == null || filter.copy(statuses = emptySet(), blockedOnly = false, liveOnly = false).matches(it, ctx) }
            .map { i ->
                val unblocks = blocking[i.id].orEmpty().size
                val reasons = buildList {
                    if (i.priority == "critical" || i.priority == "high") add(i.priority.replaceFirstChar { it.uppercase() })
                    if (unblocks > 0) add("Unblocks $unblocks")
                    if (i.status == "backlog") add("Backlog")
                    if (i.assigneeAgentId == null && i.assigneeUserId == null) add("Unassigned")
                }
                Next(i, reasons, unblocks)
            }
            .sortedWith(
                compareBy<Next>({ priorityRankOf(it.issue.priority) })
                    .thenByDescending { it.unblocks }
                    .thenBy { if (it.issue.status == "todo") 0 else 1 }
                    .thenBy { it.issue.issueNumber ?: Int.MAX_VALUE },
            )
            .take(limit)
            .toList()
    }

    /** Open issues in scope currently being worked or reviewed. */
    fun inFlight(rootId: String? = null): List<Issue> =
        (if (rootId != null) descendants(rootId) else byId.values)
            .filter { it.status == "in_progress" || it.status == "in_review" || it.id in ctx.live }
            .sortedWith(siblingOrder)
}
