package com.bu.paperclip.ui.screens

import com.bu.paperclip.util.catching

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bu.paperclip.ui.components.PropertyChip
import com.bu.paperclip.data.Agent
import com.bu.paperclip.data.AppState
import com.bu.paperclip.data.Issue
import com.bu.paperclip.data.LiveRun
import com.bu.paperclip.data.Repo
import com.bu.paperclip.data.RuntimeState
import com.bu.paperclip.ui.Nav
import com.bu.paperclip.ui.components.AgentAvatar
import com.bu.paperclip.ui.components.Card
import com.bu.paperclip.ui.components.EmptyState
import com.bu.paperclip.ui.components.IssueRow
import com.bu.paperclip.ui.components.KeyValue
import com.bu.paperclip.ui.components.LiveRefresh
import com.bu.paperclip.ui.components.LiveRunRow
import com.bu.paperclip.ui.components.LoadContent
import com.bu.paperclip.ui.components.Pill
import com.bu.paperclip.ui.components.RunRow
import com.bu.paperclip.ui.components.SectionHeader
import com.bu.paperclip.ui.components.agentStatusColor
import com.bu.paperclip.ui.components.rememberAction
import com.bu.paperclip.ui.components.rememberLoader
import com.bu.paperclip.ui.components.rememberMinuteTick
import com.bu.paperclip.ui.components.statusLabel
import com.bu.paperclip.ui.theme.LocalStatusColors
import com.bu.paperclip.util.absoluteTime
import com.bu.paperclip.util.formatCents
import com.bu.paperclip.util.relativeTime
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private data class AgentDetailData(
    val agent: Agent,
    val runtime: RuntimeState?,
    val live: List<LiveRun>,
)

private val agentTabs = listOf("Overview", "Runs", "Tasks", "Instructions")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentDetailScreen(nav: Nav, id: String) {
    val cid by AppState.company.collectAsState()
    val companyId = cid?.id ?: ""
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var runLimit by rememberSaveable { mutableIntStateOf(30) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    val act = rememberAction()

    val loader = rememberLoader(id, companyId) {
        val a = Repo.agent(id)
        AgentDetailData(
            a,
            catching { Repo.runtimeState(a.id) }.getOrNull(),
            catching { Repo.liveRuns(companyId) }.getOrDefault(emptyList()).filter { it.agentId == a.id && (it.status == "running" || it.status == "queued") },
        )
    }
    val agentId = loader.value?.agent?.id
    val runs = rememberLoader(agentId, companyId, runLimit) {
        if (agentId == null) emptyList() else Repo.runs(companyId, agentId, runLimit)
    }
    val tasks = rememberLoader(agentId, companyId) {
        if (agentId == null) emptyList<Issue>()
        else Repo.issues(companyId, assigneeAgentId = agentId, status = "todo,in_progress,in_review,blocked,backlog", limit = 100)
    }

    LiveRefresh(filter = { e ->
        val target = agentId
        target != null && when (e.type) {
            "agent.status", "heartbeat.run.queued", "heartbeat.run.status" -> e.s("agentId") == target
            "activity.logged" -> e.s("agentId") == target || e.s("entityId") == target
            else -> false
        }
    }) {
        loader.refresh(); runs.refresh(); tasks.refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(loader.value?.agent?.name ?: "Agent", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackIcon { nav.back() } },
            )
        },
    ) { pad ->
        LoadContent(loader, Modifier.padding(pad)) { d ->
            val a = d.agent
            rememberMinuteTick()
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item(key = "header") { AgentHeader(a) }
                item(key = "actions") {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AgentActionChip("Run now", Icons.Outlined.Bolt) {
                            act({
                                val r = Repo.invoke(a.id)
                                nav.run(r.id)
                            }, null)
                        }
                        if (a.status == "paused") {
                            AgentActionChip("Resume", Icons.Outlined.PlayArrow) {
                                act({ Repo.resume(a.id); AppState.refreshAgents(); loader.refresh() }, "${a.name} resumed")
                            }
                        } else if (a.status != "pending_approval" && a.status != "terminated") {
                            AgentActionChip("Pause", Icons.Outlined.Pause) {
                                act({ Repo.pause(a.id); AppState.refreshAgents(); loader.refresh() }, "${a.name} paused")
                            }
                        }
                        if (a.status == "error") {
                            AgentActionChip("Clear error", Icons.Outlined.ErrorOutline) {
                                act({ Repo.clearError(a.id); AppState.refreshAgents(); loader.refresh() }, "Error cleared")
                            }
                        }
                        AgentActionChip("Reset session", Icons.Outlined.RestartAlt) { confirmReset = true }
                    }
                }
                item(key = "tabs") {
                    PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                        agentTabs.forEachIndexed { i, t ->
                            Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                        }
                    }
                }
                when (tab) {
                    0 -> agentOverview(d, nav)
                    1 -> {
                        val list = runs.value
                        when {
                            list == null -> item(key = "runs-loading") { EmptyState("Loading runs…") }
                            list.isEmpty() -> item(key = "runs-empty") { EmptyState("No runs yet") }
                            else -> {
                                items(list, key = { "run-" + it.id }) { r -> RunRow(r, onClick = { nav.run(r.id) }, showAgent = false) }
                                if (list.size >= runLimit) {
                                    item(key = "runs-more") {
                                        TextButton(onClick = { runLimit += 30 }, modifier = Modifier.fillMaxWidth().padding(8.dp)) { Text("Load more") }
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        val list = tasks.value
                        when {
                            list == null -> item(key = "tasks-loading") { EmptyState("Loading tasks…") }
                            list.isEmpty() -> item(key = "tasks-empty") { EmptyState("No open tasks assigned") }
                            else -> items(list.sortedWith(agentTaskSort), key = { "task-" + it.id }) { i -> IssueRow(i, onClick = { nav.issue(i.identifier ?: i.id) }) }
                        }
                    }
                    else -> item(key = "instr") {
                        Card(Modifier.padding(16.dp)) {
                            Column {
                                Text("Instructions bundle", style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "View or edit the AGENTS.md and other instruction files this agent runs with.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(8.dp))
                                TextButton(onClick = { nav.instructions(a.id) }) {
                                    Icon(Icons.AutoMirrored.Outlined.Article, null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Open instructions")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmReset) {
        val a = loader.value?.agent
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset session?") },
            text = { Text("${a?.name ?: "The agent"} will start its next run with a fresh session and no memory of the current conversation.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    if (a != null) act({ Repo.resetSession(a.id); loader.refresh() }, "Session reset")
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

private val agentTaskSort = compareBy<Issue>(
    { when (it.status) { "in_progress" -> 0; "in_review" -> 1; "blocked" -> 2; "todo" -> 3; else -> 4 } },
    { when (it.priority) { "critical" -> 0; "high" -> 1; "medium" -> 2; else -> 3 } },
)

@Composable
private fun AgentActionChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    PropertyChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) },
    )
}

@Composable
private fun AgentHeader(a: Agent) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AgentAvatar(a.id, 56.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(a.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(8.dp))
                    Pill(statusLabel(a.status), agentStatusColor(a.status))
                }
                Text(a.title?.takeIf { it.isNotBlank() } ?: agentRoleLabel(a.role), style = MaterialTheme.typography.bodyMedium)
                val modelLine = listOfNotNull(a.model, agentAdapterLabel(a.adapterType).takeIf { it.isNotEmpty() }).joinToString(" · ")
                if (modelLine.isNotEmpty()) {
                    Text(modelLine, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val note = when {
            a.status == "error" && !a.errorReason.isNullOrBlank() -> "Error: ${a.errorReason}"
            a.status == "paused" -> "Paused" + (a.pauseReason?.let { " ($it)" } ?: "") + (a.pausedAt?.let { " · ${relativeTime(it)}" } ?: "")
            else -> null
        }
        if (note != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = if (a.status == "error") LocalStatusColors.current.red else LocalStatusColors.current.yellow,
            )
        }
    }
}

private fun LazyListScope.agentOverview(d: AgentDetailData, nav: Nav) {
    val a = d.agent
    if (d.live.isNotEmpty()) {
        item(key = "ov-live-h") { SectionHeader("Running now") }
        items(d.live, key = { "ov-live-" + it.id }) { r -> LiveRunRow(r, onClick = { nav.run(r.id) }) }
    }
    item(key = "ov-budget") {
        SectionHeader("This month")
        Card(Modifier.padding(horizontal = 16.dp)) {
            Column {
                val spent = a.spentMonthlyCents ?: 0
                val budget = a.budgetMonthlyCents ?: 0
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(formatCents(spent), style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(6.dp))
                    Text(if (budget > 0) "of ${formatCents(budget)}" else "no monthly budget", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (budget > 0) {
                    val frac = (spent.toFloat() / budget).coerceIn(0f, 1f)
                    val st = LocalStatusColors.current
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { frac },
                        modifier = Modifier.fillMaxWidth().height(5.dp),
                        color = when { frac >= 1f -> st.red; frac >= 0.8f -> st.yellow; else -> MaterialTheme.colorScheme.primary },
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        drawStopIndicator = {},
                    )
                }
            }
        }
    }
    d.runtime?.let { rt ->
        item(key = "ov-runtime") {
            SectionHeader("Runtime")
            Card(Modifier.padding(horizontal = 16.dp)) {
                Column {
                    KeyValue("Session", rt.sessionId?.take(18) ?: "none")
                    KeyValue("Last run", rt.lastRunStatus?.let { statusLabel(it) } ?: "—")
                    KeyValue("Tokens in", agentFmtTokens(rt.totalInputTokens))
                    KeyValue("Cached in", agentFmtTokens(rt.totalCachedInputTokens))
                    KeyValue("Tokens out", agentFmtTokens(rt.totalOutputTokens))
                    KeyValue("Total cost", formatCents(rt.totalCostCents))
                    if (!rt.lastError.isNullOrBlank()) KeyValue("Last error", rt.lastError)
                }
            }
        }
    }
    item(key = "ov-org") {
        val agents by AppState.agents.collectAsState()
        val manager = agents.firstOrNull { it.id == a.reportsTo }
        val reports = remember(agents, a.id) { agents.filter { it.reportsTo == a.id } }
        SectionHeader("Team")
        Card(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(vertical = 6.dp)) {
            Column {
                if (manager != null) AgentPersonRow("Reports to", manager) { nav.agent(manager.id) }
                if (reports.isEmpty() && manager == null) {
                    Text("No reporting lines", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(14.dp))
                }
                reports.sortedBy { it.name }.forEach { r -> AgentPersonRow("Direct report", r) { nav.agent(r.id) } }
            }
        }
    }
    item(key = "ov-meta") {
        SectionHeader("Details")
        Card(Modifier.padding(horizontal = 16.dp)) {
            Column {
                KeyValue("Role", agentRoleLabel(a.role))
                KeyValue("Adapter", a.adapterType ?: "—")
                KeyValue("Model", a.model ?: "default")
                KeyValue("Last heartbeat", a.lastHeartbeatAt?.let { "${relativeTime(it)} · ${absoluteTime(it)}" } ?: "never")
                KeyValue("Created", absoluteTime(a.createdAt).ifEmpty { "—" })
                val cwd = (a.adapterConfig?.get("cwd") as? JsonPrimitive)?.contentOrNull
                if (!cwd.isNullOrBlank()) KeyValue("Working dir", cwd)
            }
        }
    }
}

@Composable
private fun AgentPersonRow(label: String, agent: Agent, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AgentAvatar(agent.id, 28.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(agent.name, style = MaterialTheme.typography.bodyMedium)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun agentFmtTokens(n: Long): String = when {
    n >= 1_000_000_000 -> String.format("%.2fB", n / 1e9)
    n >= 1_000_000 -> String.format("%.1fM", n / 1e6)
    n >= 10_000 -> String.format("%.0fk", n / 1e3)
    n >= 1_000 -> String.format("%.1fk", n / 1e3)
    else -> n.toString()
}
