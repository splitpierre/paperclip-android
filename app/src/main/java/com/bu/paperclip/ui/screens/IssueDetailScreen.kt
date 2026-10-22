package com.bu.paperclip.ui.screens

import com.bu.paperclip.util.SupersededResponse
import com.bu.paperclip.util.catching

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bu.paperclip.ui.theme.Mono
import com.bu.paperclip.ui.components.ScrollJumpButton
import com.bu.paperclip.ui.components.PropertyChip
import com.bu.paperclip.data.ActivityEvent
import com.bu.paperclip.data.AppState
import com.bu.paperclip.data.Attachment
import com.bu.paperclip.data.Interaction
import com.bu.paperclip.data.Issue
import com.bu.paperclip.data.IssueComment
import com.bu.paperclip.data.IssueCostSummary
import com.bu.paperclip.data.IssueDocument
import com.bu.paperclip.data.LiveRun
import com.bu.paperclip.data.ProjectRef
import com.bu.paperclip.data.Repo
import com.bu.paperclip.data.WorkProduct
import com.bu.paperclip.data.jsonOf
import com.bu.paperclip.net.Api
import com.bu.paperclip.net.LiveEvents
import com.bu.paperclip.ui.Nav
import com.bu.paperclip.ui.components.AgentAvatar
import com.bu.paperclip.ui.components.Avatar
import com.bu.paperclip.ui.components.Divider
import com.bu.paperclip.ui.components.EmptyState
import com.bu.paperclip.ui.components.ErrorState
import com.bu.paperclip.ui.components.Markdown
import com.bu.paperclip.ui.components.Pill
import com.bu.paperclip.ui.components.PriorityIcon
import com.bu.paperclip.ui.components.StatusIcon
import com.bu.paperclip.ui.components.errorMessage
import com.bu.paperclip.ui.components.rememberMinuteTick
import com.bu.paperclip.ui.components.runStatusColor
import com.bu.paperclip.ui.components.statusLabel
import com.bu.paperclip.util.durationLabel
import com.bu.paperclip.util.formatCents
import com.bu.paperclip.util.parseInstant
import com.bu.paperclip.util.relativeTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.conflate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.bu.paperclip.data.ReadCache
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.Instant

private const val ISSUE_COMMENT_PAGE = 50
private val ISSUE_TABS = listOf("Thread", "Docs", "Sub-tasks", "Files", "Activity")

private sealed class IssueThreadEntry(val at: Instant, val key: String) {
    class C(val c: IssueComment, at: Instant) : IssueThreadEntry(at, "c-" + c.id)
    class I(val i: Interaction, at: Instant) : IssueThreadEntry(at, "i-" + i.id)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun IssueDetailScreen(nav: Nav, id: String) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val me by AppState.me.collectAsState()
    val company by AppState.company.collectAsState()

    var issue by remember(id) { mutableStateOf<Issue?>(null) }
    var loadError by remember(id) { mutableStateOf<String?>(null) }
    val comments = remember(id) { mutableStateListOf<IssueComment>() }
    var commentsLoaded by remember(id) { mutableStateOf(false) }
    var hasOlder by remember(id) { mutableStateOf(false) }
    var loadingOlder by remember(id) { mutableStateOf(false) }
    val interactions = remember(id) { mutableStateListOf<Interaction>() }
    var liveRuns by remember(id) { mutableStateOf<List<LiveRun>>(emptyList()) }
    var cost by remember(id) { mutableStateOf<IssueCostSummary?>(null) }
    var tab by rememberSaveable(id) { mutableIntStateOf(0) }
    var docs by remember(id) { mutableStateOf<List<IssueDocument>?>(null) }
    var children by remember(id) { mutableStateOf<List<Issue>?>(null) }
    var attachments by remember(id) { mutableStateOf<List<Attachment>?>(null) }
    var workProducts by remember(id) { mutableStateOf<List<WorkProduct>?>(null) }
    var events by remember(id) { mutableStateOf<List<ActivityEvent>?>(null) }
    var runs by remember(id) { mutableStateOf<List<JsonObject>?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableIntStateOf(0) }
    var tabTick by remember { mutableIntStateOf(0) }
    var markedRead by remember(id) { mutableStateOf(false) }

    var editTitle by remember { mutableStateOf(false) }
    var editDescription by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val key = issue?.id ?: id

    // ---- loaders ----
    suspend fun loadIssue() { issue = Repo.issue(key) }
    suspend fun loadInteractions() {
        val list = Repo.interactions(key)
        interactions.clear(); interactions.addAll(list)
    }
    suspend fun loadLiveRuns() { liveRuns = Repo.issueLiveRuns(key) }
    suspend fun loadLatestComments(reset: Boolean) {
        val page = Repo.comments(key, ISSUE_COMMENT_PAGE)
        if (reset) {
            comments.clear()
            comments.addAll(page.reversed())
            hasOlder = page.size >= ISSUE_COMMENT_PAGE
        } else {
            page.forEach { c ->
                val i = comments.indexOfFirst { it.id == c.id }
                if (i >= 0) comments[i] = c else comments.add(c)
            }
            // Drop optimistic copies that the server has now echoed back.
            val mine = page.filter { it.authorType == "user" && it.authorUserId == me?.id }.map { it.body.trim() }.toSet()
            comments.removeAll { it.id.startsWith(ISSUE_LOCAL_PREFIX) && it.body.trim() in mine }
            val sorted = comments.sortedBy { parseInstant(it.createdAt) ?: Instant.MAX }
            comments.clear(); comments.addAll(sorted)
        }
        commentsLoaded = true
    }
    suspend fun loadOlder() {
        val oldest = comments.firstOrNull { !it.id.startsWith(ISSUE_LOCAL_PREFIX) } ?: return
        val page = Repo.comments(key, ISSUE_COMMENT_PAGE, after = oldest.id)
        val known = comments.map { it.id }.toHashSet()
        comments.addAll(0, page.reversed().filter { it.id !in known })
        hasOlder = page.size >= ISSUE_COMMENT_PAGE
    }
    suspend fun loadTab(t: Int) {
        val i = issue ?: return
        when (t) {
            1 -> docs = Repo.documents(i.id)
            2 -> children = Repo.issues(AppState.cid, parentId = i.id, limit = 200)
            3 -> coroutineScope {
                val a = async { Repo.attachments(i.id) }
                val w = async { Repo.workProducts(i.id) }
                attachments = a.await(); workProducts = w.await()
            }
            4 -> coroutineScope {
                val e = async { Repo.issueActivity(i.id) }
                val r = async { Repo.issueRuns(i.id) }
                events = e.await(); runs = r.await()
            }
        }
    }

    fun safe(block: suspend () -> Unit) = scope.launch {
        try { block() } catch (_: SupersededResponse) { /* Ignore a previous owner. */ } catch (c: CancellationException) { throw c } catch (t: Throwable) { AppState.toast(errorMessage(t)) }
    }

    // ---- initial + manual reload ----
    LaunchedEffect(id, lifecycle) {
        if (issue == null) catching {
            ReadCache.only {
                loadIssue()
                catching { loadLatestComments(true) }
                catching { loadInteractions() }
            }
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            snapshotFlow { reloadTick }.conflate().collect {
                try {
                    coroutineScope {
                        val iss = async { Repo.issue(issue?.id ?: id) }
                        issue = iss.await()
                        launch { catching { loadLatestComments(reset = reloadTick == 0 || !commentsLoaded) } }
                        launch { catching { loadInteractions() } }
                        launch { catching { loadLiveRuns() } }
                        launch { catching { cost = Repo.issueCost(issue!!.id) } }
                    }
                    loadError = null
                    if (tab != 0) catching { loadTab(tab) }
                } catch (_: SupersededResponse) {
                    // Keep the reload collector alive for the next request.
                } catch (c: CancellationException) {
                    throw c
                } catch (t: Throwable) {
                    if (issue == null) loadError = errorMessage(t) else AppState.toast(errorMessage(t))
                } finally {
                    refreshing = false
                }
            }
        }
    }

    LaunchedEffect(issue?.id) {
        val i = issue ?: return@LaunchedEffect
        if (!markedRead) {
            markedRead = true
            catching { Repo.markRead(i.id); AppState.refreshBadges() }
        }
    }

    LaunchedEffect(tab, issue?.id, tabTick) {
        if (issue != null && tab != 0) catching { loadTab(tab) }.onFailure { AppState.toast(errorMessage(it)) }
    }

    // ---- live updates: targeted refetches, coalesced ----
    LaunchedEffect(issue?.id) {
        val i = issue ?: return@LaunchedEffect
        val pendingKinds = mutableSetOf<String>()
        val requests = Channel<Unit>(Channel.CONFLATED)
        fun schedule(vararg kinds: String) {
            pendingKinds.addAll(kinds)
            requests.trySend(Unit)
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch {
                for (request in requests) {
                    delay(450)
                    val k = pendingKinds.toSet(); pendingKinds.clear()
                    catching {
                        coroutineScope {
                            if ("issue" in k) launch { catching { loadIssue() } }
                            if ("comments" in k) launch { catching { loadLatestComments(reset = false) } }
                            if ("interactions" in k) launch { catching { loadInteractions() } }
                            if ("runs" in k) launch { catching { loadLiveRuns() } }
                            if ("tab" in k) tabTick++
                        }
                    }
                }
            }
            LiveEvents.events.collect { e ->
                when (e.type) {
                    "activity.logged" -> {
                        val details = e.payload["details"] as? JsonObject
                        val ident = (details?.get("identifier") as? JsonPrimitive)?.contentOrNull
                        val match = e.s("entityId") == i.id || (ident != null && ident == i.identifier) ||
                            (details?.get("issueId") as? JsonPrimitive)?.contentOrNull == i.id
                        if (!match) return@collect
                        val action = e.s("action") ?: ""
                        when {
                            action == "issue.comment_added" || action == "issue.conversation_session_started" -> schedule("comments", "issue")
                            action.startsWith("issue.thread_interaction_") -> schedule("interactions", "comments")
                            action.startsWith("issue.attachment_") || action.startsWith("issue.work_product_") || action.contains("document") -> schedule("tab", "issue")
                            else -> schedule("issue", "interactions", "tab")
                        }
                    }
                    "heartbeat.run.queued", "heartbeat.run.status" -> if (e.s("issueId") == i.id || liveRuns.any { it.id == e.s("runId") }) {
                        schedule("runs", "issue")
                        if (e.s("status") in setOf("succeeded", "failed", "cancelled", "timed_out", "interrupted")) schedule("comments", "interactions")
                    }
                    "heartbeat.run.progress" -> if (e.s("issueId") == i.id) {
                        val runId = e.s("runId")
                        liveRuns = liveRuns.map { r ->
                            if (r.id == runId) r.copy(
                                currentStatusMessage = e.s("message") ?: r.currentStatusMessage,
                                currentToolName = e.s("currentToolName"),
                                lastAssistantSnippet = e.s("lastAssistantSnippet") ?: r.lastAssistantSnippet,
                            ) else r
                        }
                        if (liveRuns.none { it.id == runId }) schedule("runs")
                    }
                }
            }
        }
    }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch { LiveEvents.epoch.drop(1).collect { reloadTick++ } }
            launch {
                while (true) {
                    delay(20_000)
                    if (!LiveEvents.connected.value && issue != null) reloadTick++
                }
            }
        }
    }

    // ---- mutations ----
    fun patch(optimistic: Issue, body: JsonObject) {
        val before = issue
        issue = optimistic
        scope.launch {
            try {
                Repo.updateIssue(optimistic.id, body)
                catching { loadIssue() }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                issue = before
                AppState.toast(errorMessage(t))
            }
        }
    }

    val i = issue
    val activeLive = liveRuns.filter { it.status == "running" || it.status == "queued" || it.status == "scheduled_retry" }
    val runLive = activeLive.isNotEmpty() || (i?.activeRun?.status == "running" || i?.activeRun?.status == "queued")
    val linkUrl = i?.let { "${Api.baseUrl}/${company?.issuePrefix ?: ""}/issues/${it.identifier ?: it.id}" }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackIcon { nav.back() } },
                title = {
                    Column {
                        Text(i?.identifier ?: "Task", style = MaterialTheme.typography.titleMedium)
                        if (i != null) Text(
                            statusLabel(i.status) + (if (runLive) " · agent working" else ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Copy link") }, enabled = linkUrl != null, onClick = {
                                menu = false
                                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("link", linkUrl))
                                AppState.toast("Link copied")
                            })
                            DropdownMenuItem(text = { Text("Open in browser") }, enabled = linkUrl != null, onClick = {
                                menu = false
                                catching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(linkUrl))) }
                            })
                            DropdownMenuItem(text = { Text("Mark unread") }, enabled = i != null, onClick = {
                                menu = false
                                safe { Repo.markUnread(i!!.id); AppState.refreshBadges(); AppState.toast("Marked unread"); nav.back() }
                            })
                            DropdownMenuItem(text = { Text("New sub-task") }, enabled = i != null, onClick = {
                                menu = false; nav.newIssue(parentId = i!!.id, projectId = i.projectId)
                            })
                            DropdownMenuItem(text = { Text("Refresh") }, onClick = { menu = false; reloadTick++ })
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (i != null && tab == 0) {
                IssueComposer(
                    issueId = i.id,
                    runLive = runLive,
                    onLocal = { c ->
                        comments.add(c)
                        scope.launch { delay(60); listState.animateScrollToItem(maxOf(0, listState.layoutInfo.totalItemsCount - 1)) }
                    },
                    onSent = { localId, real ->
                        val idx = comments.indexOfFirst { it.id == localId }
                        if (comments.any { it.id == real.id }) { if (idx >= 0) comments.removeAt(idx) }
                        else if (idx >= 0) comments[idx] = real else comments.add(real)
                        if (i.status == "done" || i.status == "cancelled" || i.status == "blocked") safe { loadIssue() }
                    },
                    onFailed = { localId -> comments.removeAll { it.id == localId } },
                )
            }
        },
        floatingActionButton = {
            ScrollJumpButton(listState, enabled = tab == 0 && commentsLoaded && comments.size > 3)
        },
    ) { pad ->
        when {
            i == null && loadError != null -> ErrorState(loadError!!, Modifier.padding(pad)) { reloadTick++ }
            i == null -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { refreshing = true; reloadTick++ },
                modifier = Modifier.fillMaxSize().padding(pad),
            ) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    item(key = "header", contentType = "header") {
                        IssueHeader(
                            issue = i,
                            cost = cost,
                            nav = nav,
                            onEditTitle = { editTitle = true },
                            onEditDescription = { editDescription = true },
                            onSheet = { sheet = it },
                        )
                    }
                    if (activeLive.isNotEmpty() || runLive) item(key = "live", contentType = "live") {
                        IssueLiveBanner(
                            runs = activeLive,
                            fallbackRunId = i.activeRun?.id,
                            fallbackAgentId = i.activeRun?.agentId ?: i.assigneeAgentId,
                            fallbackStatus = i.activeRun?.status,
                            onWatch = { nav.run(it) },
                            onStop = { runId -> safe { Repo.cancelRun(runId); AppState.toast("Stopping run"); loadLiveRuns() } },
                        )
                    }
                    stickyHeader(key = "tabs", contentType = "tabs") {
                        Surface(color = MaterialTheme.colorScheme.background) {
                            Column {
                                ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp, containerColor = MaterialTheme.colorScheme.background) {
                                    ISSUE_TABS.forEachIndexed { idx, label ->
                                        val badge = when (idx) {
                                            0 -> interactions.count { it.status == "pending" }.takeIf { it > 0 }?.let { " ($it)" } ?: ""
                                            1 -> (docs?.size ?: i.documentSummaries?.size)?.takeIf { it > 0 }?.let { " $it" } ?: ""
                                            else -> ""
                                    }
                                        Tab(selected = tab == idx, onClick = { tab = idx }, text = { Text(label + badge) })
                                }
                            }
                                Divider()
                        }
                        }
                    }
                    when (tab) {
                        0 -> {
                            val pending = interactions.filter { it.status == "pending" }.sortedBy { it.createdAt ?: "" }
                            items(pending, key = { "p-" + it.id }, contentType = { "interaction" }) { inter ->
                                IssueInteractionCard(
                                    issueId = i.id,
                                    interaction = inter,
                                    nav = nav,
                                    onUpdated = { upd ->
                                        val idx = interactions.indexOfFirst { it.id == upd.id }
                                        if (idx >= 0) interactions[idx] = upd
                                        safe { loadIssue(); loadLatestComments(false) }
                                    },
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                        }
                            if (hasOlder) item(key = "older", contentType = "older") {
                                IssueLoadOlderButton(loadingOlder) {
                                    loadingOlder = true
                                    scope.launch {
                                        try { loadOlder() } catch (c: CancellationException) { throw c } catch (t: Throwable) { AppState.toast(errorMessage(t)) }
                                        loadingOlder = false
                                }
                            }
                        }
                            val oldestLoaded = comments.firstOrNull()?.createdAt?.let { parseInstant(it) }
                            val timeline = buildList<IssueThreadEntry> {
                                comments.forEach { c -> add(IssueThreadEntry.C(c, parseInstant(c.createdAt) ?: Instant.MAX)) }
                                interactions.forEach { inter ->
                                    val at = parseInstant(inter.createdAt) ?: Instant.EPOCH
                                    // Older interactions than the loaded comment window appear once older comments load.
                                    if (!hasOlder || oldestLoaded == null || !at.isBefore(oldestLoaded)) add(IssueThreadEntry.I(inter, at))
                            }
                            }.sortedBy { it.at }
                            if (!commentsLoaded) item { IssueTabLoading() }
                            else if (timeline.isEmpty()) item { EmptyState("No comments yet. Say something to the assignee below.") }
                            items(timeline, key = { it.key }, contentType = { it::class }) { entry ->
                                when (entry) {
                                    is IssueThreadEntry.C -> {
                                        val c = entry.c
                                        val mine = c.authorType == "user" && c.authorUserId != null && c.authorUserId == me?.id
                                        IssueCommentItem(c, onDelete = if (mine) ({
                                            safe { Repo.deleteComment(i.id, c.id); loadLatestComments(false); comments.removeAll { it.id == c.id } }
                                        }) else null)
                                }
                                    is IssueThreadEntry.I -> {
                                        if (entry.i.status == "pending") {
                                            Box(Modifier.padding(horizontal = 16.dp)) { IssuePinnedMarker(entry.i) }
                                        } else {
                                            IssueInteractionCard(i.id, entry.i, nav, onUpdated = { upd ->
                                                val idx = interactions.indexOfFirst { it.id == upd.id }
                                                if (idx >= 0) interactions[idx] = upd
                                            }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                                    }
                                }
                            }
                        }
                            item { Spacer(Modifier.height(16.dp)) }
                        }
                        1 -> item { IssueDocsTab(i, docs, nav) }
                        2 -> item { IssueSubtasksTab(i, children, nav) }
                        3 -> item { IssueFilesTab(attachments, workProducts) }
                        4 -> item { IssueActivityTab(events, runs, nav) }
                    }
                }
            }
        }
    }

    // ---- dialogs & sheets ----
    if (i != null && editTitle) {
        var v by remember { mutableStateOf(i.title) }
        AlertDialog(
            onDismissRequest = { editTitle = false },
            title = { Text("Title") },
            text = { OutlinedTextField(v, { v = it }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(enabled = v.isNotBlank(), onClick = {
                    editTitle = false
                    patch(i.copy(title = v.trim()), jsonOf("title" to v.trim()))
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editTitle = false }) { Text("Cancel") } },
        )
    }
    if (i != null && editDescription) {
        var v by remember { mutableStateOf(i.description ?: "") }
        AlertDialog(
            onDismissRequest = { editDescription = false },
            title = { Text("Description") },
            text = {
                OutlinedTextField(
                    v, { v = it },
                    modifier = Modifier.fillMaxWidth().height(360.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    editDescription = false
                    patch(i.copy(description = v), jsonOf("description" to v))
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editDescription = false }) { Text("Cancel") } },
        )
    }
    if (i != null) when (sheet) {
        "status" -> IssueStatusSheet(i.status, onPick = { s -> if (s != i.status) patch(i.copy(status = s), jsonOf("status" to s)) }, onDismiss = { sheet = null })
        "priority" -> IssuePrioritySheet(i.priority, onPick = { p -> if (p != i.priority) patch(i.copy(priority = p), jsonOf("priority" to p)) }, onDismiss = { sheet = null })
        "assignee" -> IssueAssigneeSheet(i.assigneeAgentId, i.assigneeUserId, onPick = { a ->
            when (a) {
                is IssueAssignee.AgentA -> patch(i.copy(assigneeAgentId = a.id, assigneeUserId = null), jsonOf("assigneeAgentId" to a.id, "assigneeUserId" to kotlinx.serialization.json.JsonNull))
                IssueAssignee.Me -> me?.id?.let { mid -> patch(i.copy(assigneeAgentId = null, assigneeUserId = mid), jsonOf("assigneeAgentId" to kotlinx.serialization.json.JsonNull, "assigneeUserId" to mid)) }
                IssueAssignee.Nobody -> patch(i.copy(assigneeAgentId = null, assigneeUserId = null), jsonOf("assigneeAgentId" to kotlinx.serialization.json.JsonNull, "assigneeUserId" to kotlinx.serialization.json.JsonNull))
            }
        }, onDismiss = { sheet = null })
        "project" -> IssueProjectSheet(i.projectId, onPick = { p ->
            val pr = AppState.project(p)
            patch(i.copy(projectId = p, project = pr?.let { ProjectRef(it.id, it.name, it.color) }), jsonOf("projectId" to (p ?: kotlinx.serialization.json.JsonNull)))
        }, onDismiss = { sheet = null })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IssueHeader(
    issue: Issue,
    cost: IssueCostSummary?,
    nav: Nav,
    onEditTitle: () -> Unit,
    onEditDescription: () -> Unit,
    onSheet: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        // Ancestor breadcrumb, root → parent.
        val ancestors = issue.ancestors.orEmpty().let { a -> if (a.firstOrNull()?.id == issue.parentId) a.reversed() else a }
        if (ancestors.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                ancestors.forEachIndexed { idx, a ->
                    if (idx > 0) Text(" › ", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    Text(
                        a.identifier ?: a.title ?: "Parent",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.clickable { nav.issue(a.identifier ?: a.id) }.padding(vertical = 6.dp),
                    )
                }
            }
        }
        Text(
            issue.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onEditTitle).padding(vertical = 4.dp),
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PropertyChip(onClick = { onSheet("status") }, label = { Text(statusLabel(issue.status)) }, leadingIcon = { StatusIcon(issue.status, 14.dp) })
            PropertyChip(onClick = { onSheet("priority") }, label = { Text(issue.priority.replaceFirstChar { it.uppercase() }) }, leadingIcon = { PriorityIcon(issue.priority, 16.dp) })
            PropertyChip(
                onClick = { onSheet("assignee") },
                label = {
                    Text(
                        when {
                            issue.assigneeAgentId != null -> AppState.agentName(issue.assigneeAgentId)
                            issue.assigneeUserId != null -> AppState.userName(issue.assigneeUserId)
                            else -> "Unassigned"
                        },
                    )
                },
                leadingIcon = {
                    when {
                        issue.assigneeAgentId != null -> AgentAvatar(issue.assigneeAgentId, 18.dp)
                        issue.assigneeUserId != null -> Avatar(AppState.userName(issue.assigneeUserId), null, 18.dp, isAgent = false)
                        else -> Icon(Icons.Outlined.PersonOutline, null, Modifier.size(18.dp))
                    }
                },
            )
            val project = issue.project ?: AppState.project(issue.projectId)?.let { ProjectRef(it.id, it.name, it.color) }
            PropertyChip(
                onClick = { onSheet("project") },
                label = { Text(project?.name ?: "No project", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Outlined.FolderOpen, null, Modifier.size(18.dp), tint = parseIssueColor(project?.color) ?: MaterialTheme.colorScheme.onSurfaceVariant) },
            )
            issue.labels.orEmpty().forEach { l ->
                Pill(l.name, parseIssueColor(l.color) ?: MaterialTheme.colorScheme.onSurfaceVariant, Modifier.padding(top = 14.dp))
            }
        }
        val blockedBy = issue.blockedBy.orEmpty()
        if (blockedBy.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Block, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(6.dp))
                Text("Blocked by", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            blockedBy.forEach { b ->
                Row(
                    Modifier.fillMaxWidth().clickable { nav.issue(b.identifier ?: b.id) }.padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusIcon(b.status ?: "todo", 13.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(b.identifier ?: "", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Text(b.title ?: "", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        val meta = buildList {
            issue.createdByAgentId?.let { add("Created by ${AppState.agentName(it)}") } ?: issue.createdByUserId?.let { add("Created by ${AppState.userName(it)}") }
            issue.createdAt?.let { add(relativeTime(it)) }
            cost?.takeIf { it.runCount > 0 || it.costCents > 0 }?.let { c ->
                add("${formatCents(c.costCents)} · ${c.runCount} run${if (c.runCount == 1) "" else "s"}")
            }
        }
        if (meta.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(meta.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        val desc = issue.description
        if (desc.isNullOrBlank()) {
            TextButton(onClick = onEditDescription) { Icon(Icons.Outlined.Edit, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Add description") }
        } else {
            var expanded by remember(issue.id) { mutableStateOf(desc.length < 700) }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Box(Modifier.fillMaxWidth().then(if (expanded) Modifier else Modifier.height(180.dp))) {
                        Markdown(desc, fontSizeSp = 15f)
                    }
                    Row {
                        if (desc.length >= 700) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Show less" else "Show more") }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onEditDescription) { Icon(Icons.Outlined.Edit, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Edit") }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun IssueLiveBanner(
    runs: List<LiveRun>,
    fallbackRunId: String?,
    fallbackAgentId: String?,
    fallbackStatus: String?,
    onWatch: (String) -> Unit,
    onStop: (String) -> Unit,
) {
    rememberMinuteTick()
    val shown = runs.ifEmpty {
        if (fallbackRunId != null) listOf(LiveRun(id = fallbackRunId, status = fallbackStatus ?: "running", agentId = fallbackAgentId ?: "")) else emptyList()
    }
    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        shown.forEach { r ->
            val color = runStatusColor(r.status)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = color.copy(alpha = 0.08f),
                border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth().clickable { onWatch(r.id) },
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AgentAvatar(r.agentId, 26.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(r.agentName ?: AppState.agentName(r.agentId), style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.width(8.dp))
                        Pill(statusLabel(r.status), color)
                        Spacer(Modifier.weight(1f))
                        Text(durationLabel(r.startedAt ?: r.createdAt, null), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val line = r.currentStatusMessage ?: r.currentToolName?.let { "Using $it" } ?: r.lastAssistantSnippet
                    if (!line.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(line, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { onWatch(r.id) }) { Icon(Icons.Outlined.Visibility, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Watch") }
                        OutlinedButton(onClick = { onStop(r.id) }) { Icon(Icons.Outlined.Stop, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Stop") }
                    }
                }
            }
        }
    }
}
