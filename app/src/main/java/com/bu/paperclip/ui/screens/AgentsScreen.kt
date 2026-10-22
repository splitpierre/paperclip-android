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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bu.paperclip.ui.components.AppFilterChip
import com.bu.paperclip.data.Agent
import com.bu.paperclip.data.AppState
import com.bu.paperclip.data.LiveRun
import com.bu.paperclip.data.Repo
import com.bu.paperclip.ui.Nav
import com.bu.paperclip.ui.components.AgentAvatar
import com.bu.paperclip.ui.components.EmptyState
import com.bu.paperclip.ui.components.LiveRefresh
import com.bu.paperclip.ui.components.LoadContent
import com.bu.paperclip.ui.components.Pill
import com.bu.paperclip.ui.components.agentStatusColor
import com.bu.paperclip.ui.components.rememberAction
import com.bu.paperclip.ui.components.rememberLoader
import com.bu.paperclip.ui.components.statusLabel
import com.bu.paperclip.ui.theme.LocalStatusColors
import com.bu.paperclip.ui.theme.Mono
import androidx.compose.ui.text.font.FontWeight

private val agentFilters = listOf("all" to "All", "running" to "Running", "active" to "Active", "paused" to "Paused", "error" to "Error")

private data class AgentsData(val agents: List<Agent>, val live: List<LiveRun>)

internal fun agentAdapterLabel(adapterType: String?): String = when (adapterType) {
    null -> ""
    "claude_local" -> "Claude"
    "codex_local" -> "Codex"
    "gemini_local" -> "Gemini"
    "antigravity_local" -> "Antigravity"
    "cursor", "cursor_cloud" -> "Cursor"
    "opencode_local" -> "OpenCode"
    "process" -> "Process"
    "http" -> "HTTP"
    else -> adapterType.removeSuffix("_local").replace('_', ' ').replaceFirstChar { it.uppercase() }
}

internal fun agentRoleLabel(role: String): String = when (role) {
    "ceo", "cto", "cmo", "cfo", "pm", "qa" -> role.uppercase()
    "devops" -> "DevOps"
    else -> role.replaceFirstChar { it.uppercase() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentsScreen(nav: Nav) {
    val company by AppState.company.collectAsState()
    val cid = company?.id ?: ""
    var filter by rememberSaveable { mutableStateOf("all") }
    var orgView by rememberSaveable { mutableStateOf(false) }
    val act = rememberAction()
    // Optimistic status while a pause/resume request is in flight.
    val pending = remember { mutableStateMapOf<String, String>() }

    val loader = rememberLoader(cid) {
        val agents = Repo.agents(cid).filter { it.status != "terminated" }
        AppState.agents.value = agents
        AgentsData(agents, catching { Repo.liveRuns(cid) }.getOrDefault(emptyList()))
    }
    LiveRefresh(filter = { it.type == "agent.status" || it.type == "heartbeat.run.queued" || it.type == "heartbeat.run.status" || (it.type == "activity.logged" && it.s("entityType") == "agent") }) {
        loader.refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agents", style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    IconToggleButton(checked = orgView, onCheckedChange = { orgView = it }) {
                        Icon(Icons.Outlined.AccountTree, "Org view", tint = if (orgView) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val counts = loader.value?.agents?.groupingBy { agentFilterBucket(it.status) }?.eachCount().orEmpty()
                agentFilters.forEach { (k, label) ->
                    val n = if (k == "all") loader.value?.agents?.size else counts[k]
                    AppFilterChip(
                        selected = filter == k,
                        onClick = { filter = k },
                        label = { Text(if (n != null && n > 0) "$label $n" else label) },
                    )
                }
            }
            LoadContent(loader) { data ->
                val agents = data.agents.map { a -> pending[a.id]?.let { a.copy(status = it) } ?: a }
                val liveByAgent = data.live.filter { it.status == "running" || it.status == "queued" }.groupBy { it.agentId }
                val shown = agents.filter { filter == "all" || agentFilterBucket(it.status) == filter }
                val rows: List<Pair<Agent, Int>> = if (orgView && filter == "all") agentOrgOrder(shown) else shown.sortedWith(agentSort).map { it to 0 }
                if (rows.isEmpty()) {
                    EmptyState(if (filter == "all") "No agents yet" else "No ${filter} agents")
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                        items(rows, key = { it.first.id }) { (a, depth) ->
                            AgentListRow(
                                agent = a,
                                depth = depth,
                                live = liveByAgent[a.id]?.firstOrNull(),
                                onClick = { nav.agent(a.id) },
                                onToggle = {
                                    val resume = a.status == "paused"
                                    pending[a.id] = if (resume) "idle" else "paused"
                                    act({
                                        try {
                                            if (resume) Repo.resume(a.id) else Repo.pause(a.id)
                                            AppState.refreshAgents()
                                            loader.refresh()
                                        } finally {
                                            pending.remove(a.id)
                                        }
                                    }, if (resume) "${a.name} resumed" else "${a.name} paused")
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun agentFilterBucket(status: String): String = when (status) {
    "running" -> "running"
    "paused" -> "paused"
    "error" -> "error"
    else -> "active"
}

private val agentSort = compareBy<Agent>(
    { when (it.status) { "running" -> 0; "error" -> 1; "active", "idle" -> 2; "paused" -> 3; else -> 4 } },
    { it.name.lowercase() },
)

/** Depth-first walk of the reportsTo tree; orphans (manager filtered out or missing) become roots. */
private fun agentOrgOrder(agents: List<Agent>): List<Pair<Agent, Int>> {
    val ids = agents.map { it.id }.toSet()
    val children = agents.filter { it.reportsTo != null && it.reportsTo in ids }.groupBy { it.reportsTo!! }
    val roots = agents.filter { it.reportsTo == null || it.reportsTo !in ids }.sortedBy { it.name.lowercase() }
    val out = ArrayList<Pair<Agent, Int>>()
    val seen = HashSet<String>()
    fun walk(a: Agent, d: Int) {
        if (!seen.add(a.id)) return
        out.add(a to d)
        children[a.id].orEmpty().sortedBy { it.name.lowercase() }.forEach { walk(it, d + 1) }
    }
    roots.forEach { walk(it, 0) }
    agents.filter { it.id !in seen }.forEach { walk(it, 0) }
    return out
}

@Composable
private fun AgentListRow(agent: Agent, depth: Int, live: LiveRun?, onClick: () -> Unit, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(start = 16.dp + (depth * 18).dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AgentAvatar(agent.id, 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(agent.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(8.dp))
                Pill(statusLabel(agent.status), agentStatusColor(agent.status))
            }
            Text(
                agent.title?.takeIf { it.isNotBlank() } ?: agentRoleLabel(agent.role),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val modelLine = listOfNotNull(agent.model, agentAdapterLabel(agent.adapterType).takeIf { it.isNotEmpty() }).joinToString(" · ")
            if (modelLine.isNotEmpty()) {
                Text(modelLine, style = MaterialTheme.typography.labelSmall.copy(fontFamily = Mono, fontWeight = FontWeight.Normal), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            val snippet = live?.let { it.currentStatusMessage ?: it.currentToolName?.let { t -> "Using $t" } ?: it.lastAssistantSnippet }
                ?: if (agent.status == "error") agent.errorReason else if (agent.status == "paused") agent.pauseReason?.let { "Paused: $it" } else null
            if (!snippet.isNullOrBlank()) {
                Text(snippet, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (agent.status != "pending_approval") {
            IconButton(onClick = onToggle, modifier = Modifier.size(48.dp)) {
                if (agent.status == "paused") Icon(Icons.Outlined.PlayCircle, "Resume ${agent.name}", tint = LocalStatusColors.current.yellow, modifier = Modifier.size(22.dp))
                else Icon(Icons.Outlined.PauseCircle, "Pause ${agent.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f), modifier = Modifier.size(22.dp))
            }
        }
    }
}
