package com.bu.paperclip.ui.screens

import com.bu.paperclip.data.IssueStore
import com.bu.paperclip.util.catching

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import com.bu.paperclip.ui.components.SectionAction
import com.bu.paperclip.ui.theme.TABULAR
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Timelapse
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bu.paperclip.data.ActivityEvent
import com.bu.paperclip.data.AppState
import com.bu.paperclip.data.DashboardSummary
import com.bu.paperclip.data.LiveRun
import com.bu.paperclip.data.Repo
import com.bu.paperclip.net.LiveEvents
import com.bu.paperclip.ui.Nav
import com.bu.paperclip.ui.Routes
import com.bu.paperclip.ui.components.AgentAvatar
import com.bu.paperclip.ui.components.Avatar
import com.bu.paperclip.ui.components.Card
import com.bu.paperclip.ui.components.Dot
import com.bu.paperclip.ui.components.EmptyState
import com.bu.paperclip.ui.components.Links
import com.bu.paperclip.ui.components.LiveRefresh
import com.bu.paperclip.ui.components.LiveRunRow
import com.bu.paperclip.ui.components.LoadContent
import com.bu.paperclip.ui.components.SectionHeader
import com.bu.paperclip.ui.components.activityActor
import com.bu.paperclip.ui.components.activityLink
import com.bu.paperclip.ui.components.activitySentence
import com.bu.paperclip.ui.components.rememberAction
import com.bu.paperclip.ui.components.rememberLoader
import com.bu.paperclip.ui.components.rememberMinuteTick
import com.bu.paperclip.ui.theme.LocalStatusColors
import com.bu.paperclip.util.formatCents
import com.bu.paperclip.util.relativeTime

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

internal data class DashData(val summary: DashboardSummary, val live: List<LiveRun>, val activity: List<ActivityEvent>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(nav: Nav) {
    val company by AppState.company.collectAsState()
    val connected by LiveEvents.connected.collectAsState()
    val agents by AppState.agents.collectAsState()
    val cid = company?.id ?: ""
    val act = rememberAction()

    val loader = rememberLoader(cid) {
        coroutineScope {
            val summary = async { Repo.dashboard(cid) }
            val live = async { Repo.liveRuns(cid, minCount = 5) }
            val activity = async { Repo.activity(cid, limit = 20) }
            DashData(summary.await(), live.await(), activity.await())
        }
    }
    LiveRefresh(filter = { it.type == "heartbeat.run.queued" || it.type == "heartbeat.run.status" || it.type == "activity.logged" || it.type == "agent.status" }) {
        loader.refresh()
    }

    // Issue titles for live runs, fetched once each.
    val issueLabels = remember(cid) { mutableStateMapOf<String, String>() }
    val live = loader.value?.live
    val liveIssueIds = remember(live) { live?.mapNotNull { it.issueId }?.distinct().orEmpty() }
    val paused = remember(agents) { agents.filter { it.status == "paused" } }
    LaunchedEffect(liveIssueIds) {
        liveIssueIds.filter { it !in issueLabels }.forEach { id ->
            catching { IssueStore.get(id) ?: Repo.issue(id) }.getOrNull()?.let { issueLabels[id] = "${it.ref} · ${it.title}" }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(company?.name ?: "Paperclip", style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(8.dp))
                        Dot(if (connected) LocalStatusColors.current.green else LocalStatusColors.current.yellow, 8.dp)
                    }
                },
                actions = {
                    IconButton(onClick = { nav.search() }) { Icon(Icons.Outlined.Search, "Search") }
                    IconButton(onClick = { nav.settings() }) { Icon(Icons.Outlined.Settings, "Settings") }
                },
            )
        },
    ) { pad ->
        LoadContent(loader, Modifier.padding(pad)) { d ->
            DashboardFeed(
                d, nav, issueLabels, paused.size,
                onResume = {
                    act({
                        paused.forEach { Repo.resume(it.id) }
                        AppState.refreshAgents()
                        loader.refresh()
                    }, "Resumed ${paused.size} agent${if (paused.size == 1) "" else "s"}")
                },
            )
        }
    }
}

/** Keep feed identity across refreshes; reuse compositions only between matching row layouts. */
@Composable
internal fun DashboardFeed(
    data: DashData,
    nav: Nav,
    issueLabels: Map<String, String> = emptyMap(),
    pausedCount: Int = 0,
    onResume: () -> Unit = {},
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
) {
    LazyColumn(modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(bottom = 24.dp)) {
        if (pausedCount > 0) {
            item(key = "paused", contentType = "paused") { DashPausedBanner(pausedCount, onResume) }
        }
        item(key = "metrics", contentType = "metrics") { DashMetrics(data.summary, nav) }
        item(key = "live-h", contentType = "header") {
            SectionHeader("Live now") { SectionAction("Agents") { nav.tab(Routes.AGENTS) } }
        }
        if (data.live.isEmpty()) {
            item(key = "live-empty", contentType = "empty") { EmptyState("No runs right now") }
        } else {
            items(data.live, key = { "live-" + it.id }, contentType = { "live" }) { run ->
                LiveRunRow(run, onClick = { nav.run(run.id) }, issueLabel = run.issueId?.let { issueLabels[it] })
            }
        }
        item(key = "chart-h", contentType = "header") { SectionHeader("Run activity · 14 days") }
        item(key = "chart", contentType = "chart") {
            Card(Modifier.padding(horizontal = 16.dp)) { DashRunActivityChart(data.summary.runActivity) }
        }
        item(key = "act-h", contentType = "header") {
            SectionHeader("Recent activity") { SectionAction("See all") { nav.activity() } }
        }
        if (data.activity.isEmpty()) {
            item(key = "act-empty", contentType = "empty") { EmptyState("Nothing has happened yet") }
        }
        items(data.activity, key = { "activity-" + it.id }, contentType = { "activity" }) { event ->
            DashActivityRow(event)
        }
    }
}

@Composable
private fun DashPausedBanner(count: Int, onResume: () -> Unit) {
    val yellow = LocalStatusColors.current.yellow
    Surface(
        color = yellow.copy(alpha = 0.10f),
        shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(1.dp, yellow.copy(alpha = 0.28f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.PauseCircle, null, tint = yellow, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "$count agent${if (count == 1) " is" else "s are"} paused",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onResume) { Text("Resume all", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun DashMetrics(s: DashboardSummary, nav: Nav) {
    val st = LocalStatusColors.current
    val enabled = s.agents.active + s.agents.running + s.agents.paused + s.agents.error
    val approvals = s.pendingApprovals + s.budgets.pendingApprovals
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DashMetric(
                "Agents", "$enabled", Icons.Outlined.SmartToy,
                if (s.agents.running > 0) "${s.agents.running} running" else "none running",
                Modifier.weight(1f),
                accent = if (s.agents.error > 0) st.red else null,
                extra = if (s.agents.error > 0) "${s.agents.error} in error" else null,
            ) { nav.tab(Routes.AGENTS) }
            DashMetric(
                "In progress", "${s.tasks.inProgress}", Icons.Outlined.Timelapse,
                if (s.tasks.blocked > 0) "${s.tasks.blocked} blocked" else "${s.tasks.open} open",
                Modifier.weight(1f),
                accent = if (s.tasks.blocked > 0) st.red else null,
            ) { nav.tab(Routes.ISSUES) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val pct = s.costs.monthUtilizationPercent
            DashMetric(
                "Month spend", formatCents(s.costs.monthSpendCents), Icons.Outlined.Payments,
                if (s.costs.monthBudgetCents > 0) "of ${formatCents(s.costs.monthBudgetCents)}" else "no budget set",
                Modifier.weight(1f),
                progress = if (s.costs.monthBudgetCents > 0) (pct / 100.0).toFloat().coerceIn(0f, 1f) else null,
                progressColor = when {
                    pct >= 100 -> st.red
                    pct >= 80 -> st.yellow
                    else -> MaterialTheme.colorScheme.primary
                },
            ) { nav.costs() }
            DashMetric(
                "Approvals", "$approvals", Icons.Outlined.Gavel,
                if (s.budgets.activeIncidents > 0) "${s.budgets.activeIncidents} budget incident${if (s.budgets.activeIncidents == 1) "" else "s"}" else "waiting on you",
                Modifier.weight(1f),
                accent = if (approvals > 0) st.violet else null,
            ) { nav.approvals() }
        }
    }
}

@Composable
private fun DashMetric(
    label: String,
    value: String,
    icon: ImageVector,
    sub: String,
    modifier: Modifier,
    accent: Color? = null,
    extra: String? = null,
    progress: Float? = null,
    progressColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.weight(1f))
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    value,
                    style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = TABULAR),
                    maxLines = 1,
                )
                if (accent != null) { Spacer(Modifier.width(8.dp)); Dot(accent, 7.dp) }
            }
            Spacer(Modifier.height(2.dp))
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (extra != null) Text(extra, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            if (progress != null) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                    color = progressColor,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    drawStopIndicator = {},
                )
            }
        }
    }
}

@Composable
internal fun DashActivityRow(e: ActivityEvent) {
    val link = remember(e) { activityLink(e) }
    val actor = activityActor(e)
    val sentence = remember(e, actor) { activitySentence(e, actor) }
    Row(
        Modifier.fillMaxWidth()
            .then(if (link != null) Modifier.clickable { Links.handler(link) } else Modifier)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (e.actorType == "agent") AgentAvatar(e.actorId ?: e.agentId, 26.dp)
        else Avatar(actor, null, 26.dp, isAgent = false)
        Spacer(Modifier.width(12.dp))
        Text(sentence, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        DashActivityTime(e.createdAt)
    }
}

// Only the timestamp needs a clock subscription, not the entire feed and chart.
@Composable
private fun DashActivityTime(createdAt: String?) {
    val tick = rememberMinuteTick()
    val label = remember(createdAt, tick) { relativeTime(createdAt) }
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
