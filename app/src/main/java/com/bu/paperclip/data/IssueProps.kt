package com.bu.paperclip.data

/**
 * Pure helpers behind the task Properties tab: which tasks can become a parent or a blocker, and the
 * optimistic shape of an issue after an edit. Kept free of Android so they are unit-tested directly.
 */

/** Work modes the web composer offers, in its order (Auto, Plan, Ask). `skill_test` is internal. */
val WORK_MODES = listOf("standard", "planning", "ask")

fun Issue.toRef(): IssueSummaryRef = IssueSummaryRef(id = id, identifier = identifier, title = title, status = status, priority = priority)

/** Every task below [rootId] in [issues] (children, grandchildren…). Cycle-safe. */
fun descendantIds(issues: Collection<Issue>, rootId: String): Set<String> {
    val childrenOf = issues.filter { it.parentId != null }.groupBy { it.parentId!! }
    val out = HashSet<String>()
    val stack = ArrayDeque(listOf(rootId))
    while (stack.isNotEmpty()) {
        childrenOf[stack.removeLast()].orEmpty().forEach { c -> if (c.id != rootId && out.add(c.id)) stack.addLast(c.id) }
    }
    return out
}

/** Ancestors of a task whose parent is [parentId], nearest first (the server's `ancestors` order). Cycle-safe. */
fun ancestorRefs(byId: Map<String, Issue>, parentId: String?): List<IssueSummaryRef> {
    val out = mutableListOf<IssueSummaryRef>()
    val seen = HashSet<String>()
    var p = parentId
    while (p != null && seen.add(p)) {
        val i = byId[p] ?: break
        out.add(i.toRef())
        p = i.parentId
    }
    return out
}

private fun Issue.matches(q: String): Boolean {
    val s = q.trim()
    return s.isEmpty() || (identifier ?: "").contains(s, true) || title.contains(s, true)
}

/** Open tasks first, then the most recently touched. */
private val pickerOrder = compareBy<Issue> { isClosed(it.status) }.thenByDescending { it.updatedAt ?: it.createdAt ?: "" }

/**
 * Tasks [self] can be moved under: never itself or one of its descendants (that would make a cycle),
 * archived tasks excluded, filtered by identifier or title.
 */
fun parentCandidates(issues: Collection<Issue>, self: Issue, query: String): List<Issue> {
    val excluded = descendantIds(issues, self.id) + self.id
    return issues.filter { it.id !in excluded && it.archivedAt == null && it.matches(query) }.sortedWith(pickerOrder)
}

/** Tasks that can block [self]: anything but itself, filtered by identifier or title. Current blockers come first. */
fun blockerCandidates(issues: Collection<Issue>, self: Issue, query: String): List<Issue> {
    val current = self.blockedBy.orEmpty().mapTo(HashSet()) { it.id }
    return issues.filter { it.id != self.id && it.archivedAt == null && it.matches(query) }
        .sortedWith(compareByDescending<Issue> { it.id in current }.then(pickerOrder))
}

/** Adds or removes [id] from the blocker list, keeping the order of the others. */
fun toggleId(ids: List<String>, id: String): List<String> = if (id in ids) ids - id else ids + id

/** The issue as it will look once the server has applied a new parent; the breadcrumb comes from the local store. */
fun Issue.withParent(parentId: String?, byId: Map<String, Issue>): Issue =
    copy(parentId = parentId, ancestors = ancestorRefs(byId, parentId))

/** Blocked-by refs for [ids], using what the issue or the local store knows about each one. */
fun Issue.withBlockers(ids: List<String>, byId: Map<String, Issue>): Issue {
    val known = blockedBy.orEmpty().associateBy { it.id }
    return copy(blockedBy = ids.map { id -> byId[id]?.toRef() ?: known[id] ?: IssueSummaryRef(id = id) })
}

fun Issue.withLabels(ids: List<String>, all: List<Label>): Issue {
    val byId = all.associateBy { it.id } + labels.orEmpty().associateBy { it.id }
    return copy(labelIds = ids, labels = ids.mapNotNull { byId[it] })
}

