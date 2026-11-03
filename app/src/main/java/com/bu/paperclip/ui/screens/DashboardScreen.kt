package com.bu.paperclip.ui.screens

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
import com.bu.paperclip.ui.components.PollWhileDisconnected
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
import androidx.compose.ui.res.stringResource
import com.bu.paperclip.R
import com.bu.paperclip.util.appString
import com.bu.paperclip.util.appPlural

private data class DashData(val summary: DashboardSummary, val live: List<LiveRun>, val activity: List<ActivityEvent>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(nav: Nav) {
    val company by AppState.company.collectAsState()
    val connected by LiveEvents.connected.collectAsState()
    val agents by AppState.agents.collectAsState()
    val cid = company?.id ?: ""
    val act = rememberAction()

    val loader = rememberLoader(cid) {
        DashData(Repo.dashboard(cid), Repo.liveRuns(cid, minCount = 5), Repo.activity(cid, limit = 20))
    }
    LiveRefresh(filter = { it.type == "heartbeat.run.queued" || it.type == "heartbeat.run.status" || it.type == "activity.logged" || it.type == "agent.status" }) {
        loader.refresh()
    }
    PollWhileDisconnected { loader.refresh() }

    // Issue titles for live runs, fetched once each.
    val issueLabels = remember(cid) { mutableStateMapOf<String, String>() }
    val liveIssueIds = loader.value?.live?.mapNotNull { it.issueId }?.distinct().orEmpty()
    LaunchedEffect(liveIssueIds) {
        liveIssueIds.filter { it !in issueLabels }.forEach { id ->
            runCatching { Repo.issue(id) }.getOrNull()?.let { issueLabels[id] = "${it.ref} · ${it.title}" }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(company?.name ?: stringResource(R.string.paperclip), style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(8.dp))
                        Dot(if (connected) LocalStatusColors.current.green else LocalStatusColors.current.yellow, 8.dp)
                    }
                },
                actions = {
                    IconButton(onClick = { nav.search() }) { Icon(Icons.Outlined.Search, stringResource(R.string.search)) }
                    IconButton(onClick = { nav.settings() }) { Icon(Icons.Outlined.Settings, stringResource(R.string.settings)) }
                },
            )
        },
    ) { pad ->
        LoadContent(loader, Modifier.padding(pad)) { d ->
            rememberMinuteTick()
            val paused = agents.filter { it.status == "paused" }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (paused.isNotEmpty()) {
                    item(key = "paused") {
                        DashPausedBanner(paused.size) {
                            act({
                                paused.forEach { Repo.resume(it.id) }
                                AppState.refreshAgents()
                                loader.refresh()
                            }, appPlural(R.plurals.resumed_n_agents, paused.size, paused.size))
                        }
                    }
                }
                item(key = "metrics") { DashMetrics(d.summary, nav) }
                item(key = "live-h") {
                    SectionHeader(stringResource(R.string.live_now)) {
                        SectionAction(stringResource(R.string.agents)) { nav.tab(Routes.AGENTS) }
                    }
                }
                if (d.live.isEmpty()) {
                    item(key = "live-empty") { EmptyState(stringResource(R.string.no_runs_right_now)) }
                } else {
                    items(d.live, key = { "live-" + it.id }) { r ->
                        LiveRunRow(r, onClick = { nav.run(r.id) }, issueLabel = r.issueId?.let { issueLabels[it] })
                    }
                }
                item(key = "chart-h") { SectionHeader(stringResource(R.string.run_activity_14_days)) }
                item(key = "chart") {
                    Card(Modifier.padding(horizontal = 16.dp)) {
                        DashRunActivityChart(d.summary.runActivity)
                    }
                }
                item(key = "act-h") {
                    SectionHeader(stringResource(R.string.recent_activity)) {
                        SectionAction(stringResource(R.string.see_all)) { nav.activity() }
                    }
                }
                if (d.activity.isEmpty()) item(key = "act-empty") { EmptyState(stringResource(R.string.nothing_has_happened_yet)) }
                items(d.activity) { e -> DashActivityRow(e) }
            }
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
                appPlural(R.plurals.n_agents_paused, count, count),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onResume) { Text(stringResource(R.string.resume_all), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold) }
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
                stringResource(R.string.agents), "$enabled", Icons.Outlined.SmartToy,
                if (s.agents.running > 0) "${s.agents.running} running" else stringResource(R.string.none_running),
                Modifier.weight(1f),
                accent = if (s.agents.error > 0) st.red else null,
                extra = if (s.agents.error > 0) stringResource(R.string.x_in_error, s.agents.error) else null,
            ) { nav.tab(Routes.AGENTS) }
            DashMetric(
                stringResource(R.string.status_in_progress), "${s.tasks.inProgress}", Icons.Outlined.Timelapse,
                if (s.tasks.blocked > 0) "${s.tasks.blocked} blocked" else "${s.tasks.open} open",
                Modifier.weight(1f),
                accent = if (s.tasks.blocked > 0) st.red else null,
            ) { nav.tab(Routes.ISSUES) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val pct = s.costs.monthUtilizationPercent
            DashMetric(
                stringResource(R.string.month_spend), formatCents(s.costs.monthSpendCents), Icons.Outlined.Payments,
                if (s.costs.monthBudgetCents > 0) "of ${formatCents(s.costs.monthBudgetCents)}" else stringResource(R.string.no_budget_set),
                Modifier.weight(1f),
                progress = if (s.costs.monthBudgetCents > 0) (pct / 100.0).toFloat().coerceIn(0f, 1f) else null,
                progressColor = when {
                    pct >= 100 -> st.red
                    pct >= 80 -> st.yellow
                    else -> MaterialTheme.colorScheme.primary
                },
            ) { nav.costs() }
            DashMetric(
                stringResource(R.string.approvals), "$approvals", Icons.Outlined.Gavel,
                if (s.budgets.activeIncidents > 0) appPlural(R.plurals.n_budget_incidents, s.budgets.activeIncidents, s.budgets.activeIncidents) else stringResource(R.string.waiting_on_you),
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
    val link = activityLink(e)
    Row(
        Modifier.fillMaxWidth()
            .then(if (link != null) Modifier.clickable { Links.handler(link) } else Modifier)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (e.actorType == "agent") AgentAvatar(e.actorId ?: e.agentId, 26.dp)
        else Avatar(activityActor(e), null, 26.dp, isAgent = false)
        Spacer(Modifier.width(12.dp))
        Text(activitySentence(e), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(relativeTime(e.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
