package com.bu.paperclip.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bu.paperclip.ui.components.AppFilterChip
import com.bu.paperclip.data.AppState
import com.bu.paperclip.data.BudgetOverview
import com.bu.paperclip.data.CachedQuota
import com.bu.paperclip.data.CostByAgent
import com.bu.paperclip.data.CostByProvider
import com.bu.paperclip.data.CostSummary
import com.bu.paperclip.data.Repo
import com.bu.paperclip.ui.Nav
import com.bu.paperclip.ui.components.AgentAvatar
import com.bu.paperclip.ui.components.Card
import com.bu.paperclip.ui.components.EmptyState
import com.bu.paperclip.ui.components.LiveRefresh
import com.bu.paperclip.ui.components.LoadContent
import com.bu.paperclip.ui.components.Pill
import com.bu.paperclip.ui.components.SectionHeader
import com.bu.paperclip.ui.components.rememberLoader
import com.bu.paperclip.ui.components.statusLabel
import com.bu.paperclip.util.formatCents
import com.bu.paperclip.util.relativeTime
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import androidx.compose.ui.res.stringResource
import com.bu.paperclip.R
import com.bu.paperclip.util.appString

private val costPeriods = listOf(appString(R.string.this_month), appString(R.string.last_7_days), appString(R.string.last_30_days), appString(R.string.all_time))

private fun costFrom(period: Int): String? = when (period) {
    0 -> LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant().toString()
    1 -> Instant.now().minus(Duration.ofDays(7)).toString()
    2 -> Instant.now().minus(Duration.ofDays(30)).toString()
    else -> null
}

private data class CostBundle(
    val summary: CostSummary,
    val byAgent: List<CostByAgent>,
    val byProvider: List<CostByProvider>,
    val quota: CachedQuota?,
    val budgets: BudgetOverview?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CostsScreen(nav: Nav) {
    val company by AppState.company.collectAsState()
    val cid = company?.id ?: ""
    var period by rememberSaveable { mutableIntStateOf(0) }
    val loader = rememberLoader(cid, period) {
        val from = costFrom(period)
        coroutineScope {
            val s = async { Repo.costSummary(cid, from) }
            val a = async { runCatching { Repo.costByAgent(cid, from) }.getOrDefault(emptyList()) }
            val p = async { runCatching { Repo.costByProvider(cid, from) }.getOrDefault(emptyList()) }
            val q = async { runCatching { Repo.quotas(cid) }.getOrNull() }
            val b = async { runCatching { Repo.budgets(cid) }.getOrNull() }
            CostBundle(s.await(), a.await(), p.await(), q.await(), b.await())
        }
    }
    LiveRefresh(debounceMs = 3000, filter = { it.type == "activity.logged" && it.s("entityType") == "cost_event" }) { loader.refresh() }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.costs)) }, navigationIcon = { BackIcon { nav.back() } }) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                costPeriods.forEachIndexed { i, label ->
                    AppFilterChip(selected = period == i, onClick = { period = i }, label = { Text(label) })
                }
            }
            LoadContent(loader) { b -> CostsContent(nav, b, period) }
        }
    }
}

@Composable
private fun CostsContent(nav: Nav, b: CostBundle, period: Int) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Card(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Column {
                    Text(costPeriods[period], style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(formatCents(b.summary.spendCents), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    if (b.summary.budgetCents > 0) {
                        val frac = b.summary.spendCents.toFloat() / b.summary.budgetCents
                        AdminUsageBar(frac, stringResource(R.string.monthly_budget_x, formatCents(b.summary.budgetCents)), "${b.summary.utilizationPercent.toInt()}%")
                    } else {
                        Text(stringResource(R.string.no_company_budget_set), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val apiRuns = b.byAgent.sumOf { it.apiRunCount }
                    val subRuns = b.byAgent.sumOf { it.subscriptionRunCount }
                    if (apiRuns + subRuns > 0) {
                        Text(
                            stringResource(R.string.x_metered_runs_x_subscription_runs, apiRuns, subRuns),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        val quota = b.quota
        if (quota != null && quota.results.isNotEmpty()) {
            item {
                SectionHeader(stringResource(R.string.provider_quotas)) {
                    if (quota.asOf != null) {
                        Text(stringResource(R.string.as_of_x, relativeTime(quota.asOf)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
                    }
                }
            }
            items(quota.results, key = { "q-" + it.provider }) { p ->
                Card(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(com.bu.paperclip.util.providerLabel(p.provider), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            if (!p.ok) Pill("unavailable", adminStatusColor("failed"))
                        }
                        if (!p.ok && !p.error.isNullOrBlank()) {
                            Text(p.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                        p.windows.forEach { w ->
                            val used = w.usedPercent
                            val trailing = buildString {
                                append(used?.let { "${it.toInt()}%" } ?: (w.valueLabel ?: "—"))
                                if (w.resetsAt != null) append(stringResource(R.string.resets_x, relativeTime(w.resetsAt)))
                            }
                            AdminUsageBar(((used ?: 0.0) / 100.0).toFloat(), w.label, trailing)
                            if (!w.detail.isNullOrBlank()) {
                                Text(w.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (p.windows.isEmpty() && p.ok) {
                            Text(stringResource(R.string.no_usage_windows_reported), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        item { SectionHeader(stringResource(R.string.by_agent)) }
        val agents = b.byAgent.sortedByDescending { it.costCents }
        if (agents.isEmpty()) {
            item { EmptyState(stringResource(R.string.no_agent_spend_in_this_period)) }
        }
        items(agents, key = { "a-" + it.agentId }) { a -> CostAgentRow(a) { if (a.agentId.isNotEmpty()) nav.agent(a.agentId) } }

        val providers = b.byProvider.sortedByDescending { it.costCents }
        if (providers.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.by_provider_model)) }
            items(providers.size) { i ->
                val p = providers[i]
                AdminListRow(
                    title = listOfNotNull(p.provider.ifEmpty { null }?.let { com.bu.paperclip.util.providerLabel(it) }, p.model).joinToString(" · "),
                    subtitle = buildString {
                        append(stringResource(R.string.x_in_x_out, adminCompact(p.inputTokens), adminCompact(p.outputTokens)))
                        val runs = p.apiRunCount + p.subscriptionRunCount
                        if (runs > 0) append(stringResource(R.string.x_runs, runs))
                        p.billingType?.let { append(" · ${it.replace('_', ' ')}") }
                    },
                    trailing = { Text(formatCents(p.costCents), style = MaterialTheme.typography.titleSmall) },
                )
            }
        }

        val budgets = b.budgets
        if (budgets != null) {
            item { SectionHeader(stringResource(R.string.budgets)) }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CostStat(stringResource(R.string.incidents), budgets.activeIncidents.size.toString(), Modifier.weight(1f))
                    CostStat(stringResource(R.string.paused_agents), budgets.pausedAgentCount.toString(), Modifier.weight(1f))
                    CostStat(stringResource(R.string.paused_projects), budgets.pausedProjectCount.toString(), Modifier.weight(1f))
                }
            }
            if (budgets.policies.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_budget_policies_configured),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(budgets.policies.size) { i ->
                val p = budgets.policies[i]
                val name = p.scopeName ?: when (p.scopeType) {
                    "agent" -> AppState.agent(p.scopeId)?.name
                    "project" -> AppState.project(p.scopeId)?.name
                    "company" -> AppState.company.value?.name
                    else -> null
                } ?: p.scopeType.replaceFirstChar { it.uppercase() }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                    val frac = if (p.amount > 0) p.observedAmount.toFloat() / p.amount else 0f
                    AdminUsageBar(
                        frac,
                        "$name (${p.scopeType}${p.windowKind?.let { if (it == "lifetime") ", lifetime" else "" } ?: ""})",
                        "${formatCents(p.observedAmount)} / ${formatCents(p.amount)}",
                    )
                    if (p.status != null && p.status != "ok") Pill(statusLabel(p.status), adminStatusColor(p.status))
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun CostStat(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun CostAgentRow(a: CostByAgent, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AgentAvatar(a.agentId, 32.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(a.agentName ?: AppState.agentName(a.agentId), style = MaterialTheme.typography.titleSmall)
            Text(
                buildString {
                    append(stringResource(R.string.x_in_x_out, adminCompact(a.inputTokens), adminCompact(a.outputTokens)))
                    if (a.cachedInputTokens > 0) append(stringResource(R.string.x_cached, adminCompact(a.cachedInputTokens)))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val runs = a.apiRunCount + a.subscriptionRunCount
            if (runs > 0) {
                Text(
                    "$runs runs" + if (a.subscriptionRunCount > 0) stringResource(R.string.x_subscription, a.subscriptionRunCount) else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(formatCents(a.costCents), style = MaterialTheme.typography.titleSmall)
    }
}
