package com.bu.paperclip.ui.screens

import com.bu.paperclip.util.catching

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bu.paperclip.ui.theme.Mono
import com.bu.paperclip.ui.components.JumpTarget
import com.bu.paperclip.ui.components.ScrollJumpButton
import com.bu.paperclip.ui.components.PropertyChip
import com.bu.paperclip.ui.components.AppFilterChip
import com.bu.paperclip.data.Agent
import com.bu.paperclip.data.AppState
import com.bu.paperclip.data.HeartbeatRun
import com.bu.paperclip.data.Issue
import com.bu.paperclip.data.Repo
import com.bu.paperclip.data.RunEvent
import com.bu.paperclip.data.TEntry
import com.bu.paperclip.net.LiveEvents
import com.bu.paperclip.ui.Nav
import com.bu.paperclip.ui.components.AgentAvatar
import com.bu.paperclip.ui.components.Card
import com.bu.paperclip.ui.components.EmptyState
import com.bu.paperclip.ui.components.LiveRefresh
import com.bu.paperclip.ui.components.LoadContent
import com.bu.paperclip.ui.components.Pill
import com.bu.paperclip.ui.components.StatusIcon
import com.bu.paperclip.ui.components.errorMessage
import com.bu.paperclip.ui.components.rememberAction
import com.bu.paperclip.ui.components.rememberLoader
import com.bu.paperclip.ui.components.runStatusColor
import com.bu.paperclip.ui.components.statusLabel
import com.bu.paperclip.ui.theme.LocalStatusColors
import com.bu.paperclip.util.absoluteTime
import com.bu.paperclip.util.durationLabel
import com.bu.paperclip.util.relativeTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

private const val RUN_TAIL_BYTES = 256_000L
private const val RUN_PAGE_BYTES = 512_000

private enum class RunView { Transcript, Raw, Events }

private fun runIsTerminal(status: String) = status in setOf("succeeded", "failed", "cancelled", "timed_out", "interrupted")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunScreen(nav: Nav, id: String) {
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val act = rememberAction()
    val connected by LiveEvents.connected.collectAsState()
    var view by rememberSaveable { mutableStateOf(RunView.Transcript) }
    var confirmStop by remember { mutableStateOf(false) }
    var fullLog by rememberSaveable(id) { mutableStateOf(false) }

    val runLoader = rememberLoader(id) { Repo.run(id) }
    val run = runLoader.value
    val agent: Agent? = run?.let { AppState.agent(it.agentId) }
    var fetchedAgent by remember(id) { mutableStateOf<Agent?>(null) }
    LaunchedEffect(run?.agentId) {
        val aid = run?.agentId ?: return@LaunchedEffect
        if (AppState.agent(aid) == null) fetchedAgent = catching { Repo.agent(aid) }.getOrNull()
    }
    val theAgent = agent ?: fetchedAgent
    var issue by remember(id) { mutableStateOf<Issue?>(null) }
    LaunchedEffect(run?.issueId) {
        val iid = run?.issueId ?: return@LaunchedEffect
        issue = catching { Repo.issue(iid) }.getOrNull()
    }

    LiveRefresh(debounceMs = 300, filter = { (it.type == "heartbeat.run.status" || it.type == "heartbeat.run.queued") && it.s("runId") == id }) {
        runLoader.refresh()
    }

    // ---- Transcript state ----
    val adapterType = theAgent?.adapterType
    val ts = remember(id, adapterType, fullLog) { RunTranscriptState(adapterType) }
    var entries by remember(ts) { mutableStateOf<List<TEntry>>(emptyList()) }
    var rawLines by remember(ts) { mutableStateOf<List<RunRawLine>>(emptyList()) }
    var entryStart by remember(ts) { mutableLongStateOf(0L) }
    var rawStart by remember(ts) { mutableLongStateOf(0L) }
    val listState = rememberLazyListState()
    var logError by remember(ts) { mutableStateOf<String?>(null) }
    var logLoaded by remember(ts) { mutableStateOf(false) }
    val live = run?.let { !runIsTerminal(it.status) } ?: false

    suspend fun pollOnce() {
        var guard = 0
        while (guard++ < 40) {
            val page = Repo.runLog(id, ts.offset, RUN_PAGE_BYTES)
            if (page.content.isNotEmpty()) { ts.feedContent(page.content); logLoaded = true }
            val next = page.nextOffset
            if (next == null) {
                ts.offset += page.content.toByteArray(Charsets.UTF_8).size
                break
            }
            if (next <= ts.offset) break
            ts.offset = next
        }
    }

    // Initial load, live chunks and fallback polling. Waits until we know the adapter (for parsing).
    LaunchedEffect(ts, run != null && (theAgent != null || run.agentId.isEmpty())) {
        val r = run ?: return@LaunchedEffect
        if (theAgent == null && r.agentId.isNotEmpty()) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val catchUp = RunLogCatchUp()
            val liveJob = launch {
                LiveEvents.events.filter { it.type == "heartbeat.run.log" && it.s("runId") == id }.collect { e ->
                    if (!catchUp.offer(e.payload)) {
                        ts.feedLive(e.s("ts"), e.s("stream") ?: "stdout", e.s("chunk") ?: "", (e.payload["seq"] as? JsonPrimitive)?.longOrNull)
                    }
                }
            }
            try {
                val end = r.lastOutputBytes ?: r.logBytes ?: 0L
                if (!ts.loaded) {
                    ts.startOffset = if (fullLog) 0 else (end - RUN_TAIL_BYTES).coerceAtLeast(0)
                    ts.offset = ts.startOffset
                }
                pollOnce()
                ts.loaded = true
                logError = null
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                logError = errorMessage(t)
            }
            catchUp.complete(replay = { p ->
                ts.feedLive(
                    (p["ts"] as? JsonPrimitive)?.contentOrNull, (p["stream"] as? JsonPrimitive)?.contentOrNull ?: "stdout",
                    (p["chunk"] as? JsonPrimitive)?.contentOrNull ?: "", (p["seq"] as? JsonPrimitive)?.longOrNull,
                )
            }, recover = { catching { pollOnce() }; Unit })
            logLoaded = true

            // Fallback polling while the socket is down, plus a catch-up poll after reconnects.
            var lastEpoch = LiveEvents.epoch.value
            try {
                while (true) {
                    delay(2000)
                    val current = runLoader.value
                    val stillLive = current != null && !runIsTerminal(current.status)
                    val reconnected = LiveEvents.epoch.value != lastEpoch
                    lastEpoch = LiveEvents.epoch.value
                    if (stillLive && (!LiveEvents.connected.value || reconnected)) catching { pollOnce() }
                    if (!stillLive) {
                        catching { pollOnce() }
                        break
                    }
                }
            } finally {
                liveJob.cancel()
            }
        }
    }

    // Keep the visible list steady during a gesture. Parsing continues into the bounded builder;
    // publish its latest snapshot once scrolling settles, rather than rebuild rows mid-fling.
    LaunchedEffect(ts, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                if (!listState.isScrollInProgress) {
                    ts.snapshot()?.let { snapshot ->
                        entries = snapshot.entries
                        rawLines = snapshot.rawLines
                        entryStart = snapshot.entryStart
                        rawStart = snapshot.rawStart
                    }
                }
                delay(250)
            }
        }
    }

    val events = rememberLoader(id, view == RunView.Events) {
        if (view == RunView.Events) Repo.runEvents(id, 0, 500) else emptyList()
    }

    var follow by remember(id) { mutableStateOf(true) }
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount == 0 || last >= info.totalItemsCount - 2
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.distinctUntilChanged().collect { scrolling ->
            // Disable follow as soon as a gesture starts, so incoming output cannot cancel it.
            follow = !scrolling && atBottom
        }
    }
    val itemCount = when (view) { RunView.Transcript -> entries.size; RunView.Raw -> rawLines.size; RunView.Events -> events.value?.size ?: 0 }
    LaunchedEffect(itemCount, view, logLoaded) {
        if (logLoaded && follow && !listState.isScrollInProgress && itemCount > 0) {
            val total = listState.layoutInfo.totalItemsCount
            if (total > 0) listState.scrollToItem(total - 1)
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(theAgent?.name ?: "Run", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Run ${id.take(8)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { BackIcon { nav.back() } },
                actions = {
                    if (live) TextButton(onClick = { confirmStop = true }) {
                        Icon(Icons.Outlined.StopCircle, null, tint = LocalStatusColors.current.red)
                        Spacer(Modifier.width(4.dp))
                        Text("Stop", color = LocalStatusColors.current.red)
                    }
                },
            )
        },
        floatingActionButton = {
            ScrollJumpButton(
                listState,
                enabled = itemCount > 0,
                label = { t -> if (t == JumpTarget.Top) "Jump to top" else if (live) "Jump to latest" else "Jump to end" },
                onJump = { follow = it == JumpTarget.Bottom },
            )
        },
    ) { pad ->
        LoadContent(runLoader, Modifier.padding(pad)) { r ->
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 96.dp)) {
                item(key = "header", contentType = "header") {
                    RunHeader(r, theAgent, issue, nav, onRetry = {
                        act({
                            val res = Repo.wake(r.agentId, reason = "retry_failed_run", failedRunId = r.id)
                            val o = res as? JsonObject
                            val newId = (o?.get("id") as? JsonPrimitive)?.contentOrNull
                                ?: (o?.get("runId") as? JsonPrimitive)?.contentOrNull
                            val skipped = (o?.get("status") as? JsonPrimitive)?.contentOrNull == "skipped"
                            if (newId != null && !skipped) nav.run(newId)
                            else AppState.toast(
                                (o?.get("message") as? JsonPrimitive)?.contentOrNull ?: "Retry requested",
                            )
                        }, null)
                    })
                }
                item(key = "views", contentType = "views") {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppFilterChip(view == RunView.Transcript, onClick = { view = RunView.Transcript }, label = { Text("Transcript") })
                        AppFilterChip(view == RunView.Raw, onClick = { view = RunView.Raw }, label = { Text("Raw log") })
                        AppFilterChip(view == RunView.Events, onClick = { view = RunView.Events }, label = { Text("Events") })
                        if (live) {
                            Spacer(Modifier.width(4.dp))
                            Pill(if (connected) "streaming" else "polling", if (connected) LocalStatusColors.current.blue else LocalStatusColors.current.yellow)
                        }
                    }
                }
                if (view != RunView.Events && logLoaded && ts.startOffset > 0) {
                    item(key = "truncated", contentType = "truncated") {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Showing the last ${RUN_TAIL_BYTES / 1000} KB of output",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { fullLog = true; follow = false }) { Text("Load full log") }
                        }
                    }
                }
                if (ts.trimmed && view != RunView.Events) {
                    item(key = "retention", contentType = "retention") {
                        Text("Showing recent output to keep scrolling responsive. Earlier output remains in the server log.",
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
                    }
                }
                if (logError != null && view != RunView.Events) {
                    item(key = "logerr", contentType = "logerr") {
                        Text(logError!!, color = LocalStatusColors.current.red, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
                    }
                }
                when (view) {
                    RunView.Transcript -> {
                        if (!logLoaded) item(key = "loading", contentType = "loading") { RunLoadingRow() }
                        else if (entries.isEmpty()) item(key = "empty", contentType = "empty") { EmptyState(if (live) "Waiting for output…" else "No output was recorded for this run") }
                        itemsIndexed(entries, key = { i, _ -> "e${entryStart + i}" }, contentType = { _, e -> e::class }) { _, e -> RunEntryView(e) }
                        if (live && logLoaded) item(key = "live-tail", contentType = "live-tail") { RunLiveTail(r) }
                    }
                    RunView.Raw -> {
                        if (!logLoaded) item(key = "loading", contentType = "loading") { RunLoadingRow() }
                        else if (rawLines.isEmpty()) item(key = "empty", contentType = "empty") { EmptyState("No raw output") }
                        itemsIndexed(rawLines, key = { i, _ -> "r${rawStart + i}" }, contentType = { _, _ -> "raw" }) { _, l -> RunLogLine(l.stream, l.text) }
                    }
                    RunView.Events -> {
                        val list = events.value
                        if (list == null) item(key = "loading", contentType = "loading") { RunLoadingRow() }
                        else if (list.isEmpty()) item(key = "empty", contentType = "empty") { EmptyState("No events recorded") }
                        else itemsIndexed(list, key = { _, ev -> "ev${ev.seq}" }, contentType = { _, _ -> "event" }) { _, ev -> RunEventRow(ev) }
                    }
                }
            }
        }
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("Stop this run?") },
            text = { Text("${theAgent?.name ?: "The agent"} will be interrupted. Work already committed stays; the current turn is lost.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmStop = false
                    act({ Repo.cancelRun(id); runLoader.refresh() }, "Run stopped")
                }) { Text("Stop", color = LocalStatusColors.current.red) }
            },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RunLoadingRow() {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp)) }
}

@Composable
private fun RunLiveTail(r: HeartbeatRun) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.6.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            r.currentStatusMessage ?: r.currentToolName?.let { "Using $it" } ?: if (r.status == "queued") "Queued…" else "Working…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RunHeader(r: HeartbeatRun, agent: Agent?, issue: Issue?, nav: Nav, onRetry: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(r.status, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (!runIsTerminal(r.status)) { delay(1000); tick++ }
        }
    }
    val elapsed = remember(tick, r.status, r.startedAt, r.finishedAt) {
        durationLabel(r.startedAt ?: r.createdAt, if (runIsTerminal(r.status)) r.finishedAt else null)
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable(enabled = agent != null) { agent?.let { nav.agent(it.id) } },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AgentAvatar(r.agentId, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill(statusLabel(r.status), runStatusColor(r.status))
                    Spacer(Modifier.width(8.dp))
                    Text(elapsed, style = MaterialTheme.typography.labelLarge)
                }
                val trig = listOfNotNull(r.invocationSource?.replace('_', ' '), r.triggerDetail).joinToString(" · ")
                Text(
                    listOfNotNull(trig.ifEmpty { null }, (r.startedAt ?: r.createdAt)?.let { "started ${relativeTime(it)}" }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (agent?.model != null) Text(agent.model!!, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (r.issueId != null) {
            Spacer(Modifier.height(10.dp))
            Card(Modifier.clickable { nav.issue(issue?.identifier ?: r.issueId!!) }, padding = PaddingValues(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusIcon(issue?.status ?: "todo", 14.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(issue?.ref ?: "Task", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(issue?.title ?: "Loading…", style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (!r.error.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                (r.errorCode?.let { "$it: " } ?: "") + r.error,
                style = MaterialTheme.typography.bodySmall,
                color = LocalStatusColors.current.red,
            )
        }
        if (r.status == "failed" || r.status == "timed_out") {
            Spacer(Modifier.height(6.dp))
            PropertyChip(onClick = onRetry, label = { Text("Retry run") }, leadingIcon = { Icon(Icons.Outlined.Replay, null, Modifier.size(18.dp)) })
        }
        if (runIsTerminal(r.status) && r.finishedAt != null) {
            Text("Finished ${absoluteTime(r.finishedAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RunEventRow(ev: RunEvent) {
    val st = LocalStatusColors.current
    val color = when (ev.level) { "error" -> st.red; "warn" -> st.yellow; else -> MaterialTheme.colorScheme.onSurfaceVariant }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("#${ev.seq}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = Mono), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(ev.eventType, style = MaterialTheme.typography.labelMedium.copy(fontFamily = Mono), color = color, modifier = Modifier.weight(1f))
            Text(relativeTime(ev.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!ev.message.isNullOrBlank()) {
            Text(ev.message, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), maxLines = 6, overflow = TextOverflow.Ellipsis)
        }
    }
}
