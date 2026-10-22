package com.bu.paperclip.data

import com.bu.paperclip.util.SupersededResponse
import com.bu.paperclip.net.Api
import com.bu.paperclip.net.AppJson
import com.bu.paperclip.net.LiveEvents
import com.bu.paperclip.ui.components.errorMessage
import com.bu.paperclip.util.catching
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import java.time.Instant

/** A foreground-only task mirror. Full snapshots reconcile deletions; deltas drain every page. */
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
    val liveIssueIds: StateFlow<Set<String>> = _live
    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    @Volatile private var companyId: String? = null
    @Volatile private var generation = 0L
    private var syncedAt: Instant? = null
    private var lastFullAt = 0L
    private var jobs: List<Job> = emptyList()
    private fun snapshotKey(cid: String) = "local:tasks:$cid"
    private fun query(extra: Map<String, Any?>) = mapOf(
        "view" to "compact", "includeBlockedBy" to "true", "includeRoutineExecutions" to "true",
    ) + extra

    fun stop() { generation++; jobs.forEach { it.cancel() }; jobs = emptyList() }
    fun clear() {
        stop(); companyId = null; syncedAt = null; lastFullAt = 0
        _issues.value = emptyMap(); _live.value = emptySet(); _status.value = Status.Idle
    }
    fun start(cid: String) {
        if (cid == companyId && jobs.isNotEmpty()) return
        stop()
        if (companyId != cid) {
            companyId = cid; syncedAt = null; lastFullAt = 0
            _issues.value = emptyMap(); _live.value = emptySet()
        }
        val version = generation
        jobs = listOf(
            scope.launch {
                if (_issues.value.isEmpty()) catching {
                    val saved = Api.decode(ListSerializer(Issue.serializer()), ReadCache.read(snapshotKey(cid)))
                    if (generation == version) { _issues.value = saved.associateBy { it.id }; _status.value = Status.Ready }
                }
                refresh(full = true)
            },
            scope.launch {
                LiveEvents.events.filter { it.type == "activity.logged" && it.s("entityType") == "issue" }
                    .conflate().collect {
                        delay(700)
                        refresh(full = it.s("action")?.contains("delete") == true)
                    }
            },
            scope.launch {
                LiveEvents.events.filter { it.type.startsWith("heartbeat.run.") && it.type != "heartbeat.run.log" && it.type != "heartbeat.run.event" }
                    .conflate().collect { delay(800); refreshLive() }
            },
            scope.launch { LiveEvents.epoch.drop(1).collect { refresh(full = false) } },
            scope.launch {
                while (true) {
                    delay(20_000)
                    if (!LiveEvents.connected.value || System.currentTimeMillis() - lastFullAt > 300_000) refresh(full = false)
                }
            },
        )
    }

    suspend fun refresh(full: Boolean) {
        val cid = companyId ?: return
        val version = generation
        mutex.withLock {
            if (version != generation) return
            val replace = full || syncedAt == null || System.currentTimeMillis() - lastFullAt > 300_000
            // Server time from before this sync avoids relying on the phone clock.
            val watermark = Api.serverTime
            try {
                if (_issues.value.isEmpty()) _status.value = Status.Loading
                val collected = LinkedHashMap<String, Issue>()
                drainPages(500, Issue::id, fetch = { after ->
                    Api.get<List<Issue>>("/companies/$cid/issues", query(mapOf(
                        "limit" to 500, "sortField" to "id", "sortDir" to "asc", "afterId" to after,
                        "updatedSince" to if (replace) null else syncedAt?.minusSeconds(60)?.toString(),
                    )))
                }, onPage = { page ->
                    ensureActiveVersion(version)
                    page.forEach { collected[it.id] = it }
                    // First-use results are useful before the complete graph arrives.
                    if (replace && lastFullAt == 0L) _issues.value = _issues.value + collected
                })
                ensureActiveVersion(version)
                _issues.value = if (replace) collected else _issues.value + collected
                // No Date header? Retain the previous watermark, or use the oldest row conservatively.
                syncedAt = watermark ?: syncedAt ?: collected.values.mapNotNull { i ->
                    catching { Instant.parse(i.updatedAt) }.getOrNull()
                }.minOrNull()
                if (replace) lastFullAt = System.currentTimeMillis()
                _status.value = Status.Ready
                val snapshot = _issues.value.values.toList()
                ReadCache.fetch(snapshotKey(cid)) { AppJson.encodeToJsonElement(ListSerializer(Issue.serializer()), snapshot) }
            } catch (_: SupersededResponse) {
                // The owner changed; keep collectors and polling alive.
            } catch (c: CancellationException) { throw c }
            catch (t: Throwable) {
                if (version == generation) _status.value = if (_issues.value.isEmpty()) Status.Error(errorMessage(t)) else Status.Ready
            }
        }
        refreshLive()
    }
    private suspend fun ensureActiveVersion(version: Long) {
        currentCoroutineContext().ensureActive()
        if (version != generation) throw SupersededResponse()
    }
    suspend fun refreshLive() {
        val cid = companyId ?: return
        val version = generation
        catching {
            val runs = Repo.liveRuns(cid, minCount = 0, limit = 50)
            ensureActiveVersion(version)
            _live.value = runs.filter { it.status == "running" || it.status == "queued" }.mapNotNull { it.issueId }.toSet()
        }
    }
    fun upsert(issue: Issue) { _issues.update { it + (issue.id to issue) } }
    fun get(id: String?): Issue? = id?.let { _issues.value[it] }
}
