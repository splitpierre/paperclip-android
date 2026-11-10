package com.bu.paperclip.data

import com.bu.paperclip.net.Api
import com.bu.paperclip.net.LiveEvents
import com.bu.paperclip.ui.components.errorMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

/**
 * Local mirror of every task in the selected company. Loaded once with keyset paging, then kept fresh
 * with `updatedSince` deltas on live events (or polling without the socket). All task views filter,
 * group and build trees from this in memory, so switching views is instant.
 */
object IssueStore {
    sealed class Status {
        data object Idle : Status()
        data object Loading : Status()
        data object Ready : Status()
        data class Error(val message: String) : Status()
    }

    private val _issues = MutableStateFlow<Map<String, Issue>>(emptyMap())
    val issues: StateFlow<Map<String, Issue>> = _issues
    private val _live = MutableStateFlow<Set<String>>(emptySet())
    /** Issue ids with a queued or running agent run. */
    val liveIssueIds: StateFlow<Set<String>> = _live
    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var companyId: String? = null
    private var syncedAt: Instant? = null
    private var jobs: List<Job> = emptyList()

    private fun query(extra: Map<String, Any?>) = mapOf(
        "view" to "compact",
        "includeBlockedBy" to "true",
        "includeRoutineExecutions" to "true",
    ) + extra

    @OptIn(FlowPreview::class)
    fun start(cid: String) {
        if (cid == companyId && jobs.isNotEmpty()) return
        jobs.forEach { it.cancel() }
        companyId = cid
        _issues.value = emptyMap()
        _live.value = emptySet()
        syncedAt = null
        jobs = listOf(
            scope.launch { refresh(full = true) },
            scope.launch {
                LiveEvents.events
                    .filter { it.type == "activity.logged" && it.s("entityType") == "issue" }
                    .debounce(700)
                    .collect { refresh(full = false) }
            },
            scope.launch {
                LiveEvents.events
                    .filter { it.type.startsWith("heartbeat.run.") && it.type != "heartbeat.run.log" && it.type != "heartbeat.run.event" }
                    .debounce(800)
                    .collect { refreshLive() }
            },
            scope.launch {
                var first = true
                LiveEvents.epoch.collect { if (!first) refresh(full = false); first = false }
            },
            scope.launch {
                while (true) {
                    delay(20_000)
                    if (!LiveEvents.connected.value) refresh(full = false)
                }
            },
        )
    }

    /** Full reload (pull-to-refresh) or a delta of everything changed since the last sync. */
    suspend fun refresh(full: Boolean) {
        val cid = companyId ?: return
        mutex.withLock {
            val startedAt = Instant.now()
            try {
                if (full || syncedAt == null) {
                    if (_issues.value.isEmpty()) _status.value = Status.Loading
                    val all = LinkedHashMap<String, Issue>()
                    var after: String? = null
                    while (true) {
                        val page: List<Issue> = Api.get(
                            "/companies/$cid/issues",
                            query(mapOf("limit" to 500, "sortField" to "id", "sortDir" to "asc", "afterId" to after)),
                        )
                        page.forEach { all[it.id] = it }
                        if (page.size < 500) break
                        after = page.last().id
                    }
                    if (cid != companyId) return
                    _issues.value = all
                } else {
                    val since = syncedAt!!.minusSeconds(15)
                    val changed: List<Issue> = Api.get(
                        "/companies/$cid/issues",
                        query(mapOf("limit" to 1000, "updatedSince" to since.toString())),
                    )
                    if (changed.isNotEmpty() && cid == companyId) {
                        _issues.value = _issues.value.toMutableMap().apply { changed.forEach { put(it.id, it) } }
                    }
                }
                syncedAt = startedAt
                _status.value = Status.Ready
            } catch (t: Throwable) {
                if (_issues.value.isEmpty()) _status.value = Status.Error(errorMessage(t))
            }
        }
        refreshLive()
    }

    suspend fun refreshLive() {
        val cid = companyId ?: return
        runCatching {
            val runs = Repo.liveRuns(cid, minCount = 0, limit = 50)
            _live.value = runs.filter { it.status == "running" || it.status == "queued" }.mapNotNull { it.issueId }.toSet()
        }
    }

    /** Optimistic local change (e.g. moving a card); the next delta sync reconciles it. */
    fun upsert(issue: Issue) {
        _issues.value = _issues.value.toMutableMap().apply { put(issue.id, issue) }
    }

    fun get(id: String?): Issue? = id?.let { _issues.value[it] }

    /**
     * `PATCH /issues/{id}` with an optimistic local edit: [change] is applied to the mirrored copy at once,
     * so tree, board and Next up re-render before the server answers. On failure the copy is restored and
     * the error rethrown for the caller to show. On success the server's copy replaces it and a delta sync
     * picks up anything the change touched elsewhere (the old and new parent's roll-ups, for instance).
     */
    suspend fun patch(id: String, body: kotlinx.serialization.json.JsonObject, change: (Issue) -> Issue): Issue {
        val before = _issues.value[id]
        if (before != null) upsert(change(before))
        val updated = try {
            Repo.updateIssue(id, body)
        } catch (t: Throwable) {
            if (before != null && _issues.value.containsKey(id)) upsert(before)
            throw t
        }
        if (before != null && updated.companyId.let { it.isEmpty() || it == companyId }) {
            // The PATCH response may omit the list-only fields the mirror was loaded with.
            val optimistic = change(before)
            upsert(updated.copy(blockedBy = updated.blockedBy ?: optimistic.blockedBy, labels = updated.labels ?: optimistic.labels))
        }
        scope.launch { refresh(full = false) }
        return updated
    }
}
