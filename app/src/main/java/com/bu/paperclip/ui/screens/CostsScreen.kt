package com.bu.paperclip.ui.screens

import com.bu.paperclip.util.L10n
import com.bu.paperclip.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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

private val costPeriods = listOf(R.string.costs_period_month, R.string.costs_period_7d, R.string.costs_period_30d, R.string.costs_period_all)

private fun costsBillingLabel(type: String): String = when (type) {
    "metered_api" -> L10n.str(R.string.costs_billing_metered_api)
    "subscription_included" -> L10n.str(R.string.costs_billing_subscription_included)
    "subscription_overage" -> L10n.str(R.string.costs_billing_subscription_overage)
    "credits" -> L10n.str(R.string.costs_billing_credits)
    "fixed" -> L10n.str(R.string.costs_billing_fixed)
    "unknown" -> L10n.str(R.string.costs_billing_unknown)
    else -> type.replace('_', ' ')
}

private fun costsScopeLabel(type: String): String = when (type) {
    "agent" -> L10n.str(R.string.costs_scope_agent)
    "project" -> L10n.str(R.string.costs_scope_project)
    "company" -> L10n.str(R.string.costs_scope_company)
    else -> type
}

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

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.costs_title)) }, navigationIcon = { BackIcon { nav.back() } }) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                costPeriods.forEachIndexed { i, label ->
                    AppFilterChip(selected = period == i, onClick = { period = i }, label = { Text(stringResource(label)) })
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
                    Text(stringResource(costPeriods[period]), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(formatCents(b.summary.spendCents), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    if (b.summary.budgetCents > 0) {
                        val frac = b.summary.spendCents.toFloat() / b.summary.budgetCents
                        AdminUsageBar(frac, stringResource(R.string.costs_monthly_budget, formatCents(b.summary.budgetCents)), stringResource(R.string.costs_percent, b.summary.utilizationPercent.toInt()))
                    } else {
                        Text(stringResource(R.string.costs_no_company_budget), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val apiRuns = b.byAgent.sumOf { it.apiRunCount }
                    val subRuns = b.byAgent.sumOf { it.subscriptionRunCount }
                    if (apiRuns + subRuns > 0) {
                        Text(
                            "${pluralStringResource(R.plurals.costs_metered_runs, apiRuns, apiRuns)} · ${pluralStringResource(R.plurals.costs_subscription_runs, subRuns, subRuns)}",
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
                SectionHeader(stringResource(R.string.costs_provider_quotas)) {
                    if (quota.asOf != null) {
                        Text(stringResource(R.string.costs_as_of, relativeTime(quota.asOf)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
                    }
                }
            }
            items(quota.results, key = { "q-" + it.provider }) { p ->
                Card(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(com.bu.paperclip.util.providerLabel(p.provider), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            if (!p.ok) Pill(stringResource(R.string.costs_unavailable), adminStatusColor("failed"))
                        }
                        if (!p.ok && !p.error.isNullOrBlank()) {
                            Text(p.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                        p.windows.forEach { w ->
                            val used = w.usedPercent
                            val usedLabel = used?.let { stringResource(R.string.costs_percent, it.toInt()) } ?: (w.valueLabel ?: "—")
                            val trailing = if (w.resetsAt != null) "$usedLabel · ${stringResource(R.string.costs_resets, relativeTime(w.resetsAt))}" else usedLabel
                            AdminUsageBar(((used ?: 0.0) / 100.0).toFloat(), w.label, trailing)
                            if (!w.detail.isNullOrBlank()) {
                                Text(w.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (p.windows.isEmpty() && p.ok) {
                            Text(stringResource(R.string.costs_no_usage_windows), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        item { SectionHeader(stringResource(R.string.costs_by_agent)) }
        val agents = b.byAgent.sortedByDescending { it.costCents }
        if (agents.isEmpty()) {
            item { EmptyState(stringResource(R.string.costs_no_agent_spend)) }
        }
        items(agents, key = { "a-" + it.agentId }) { a -> CostAgentRow(a) { if (a.agentId.isNotEmpty()) nav.agent(a.agentId) } }

        val providers = b.byProvider.sortedByDescending { it.costCents }
        if (providers.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.costs_by_provider)) }
            items(providers.size) { i ->
                val p = providers[i]
                AdminListRow(
                    title = listOfNotNull(p.provider.ifEmpty { null }?.let { com.bu.paperclip.util.providerLabel(it) }, p.model).joinToString(" · "),
                    subtitle = run {
                        val runs = p.apiRunCount + p.subscriptionRunCount
                        listOfNotNull(
                            stringResource(R.string.costs_tokens_in_out, adminCompact(p.inputTokens), adminCompact(p.outputTokens)),
                            if (runs > 0) pluralStringResource(R.plurals.costs_runs, runs, runs) else null,
                            p.billingType?.let(::costsBillingLabel),
                        ).joinToString(" · ")
                    },
                    trailing = { Text(formatCents(p.costCents), style = MaterialTheme.typography.titleSmall) },
                )
            }
        }

        val budgets = b.budgets
        if (budgets != null) {
            item { SectionHeader(stringResource(R.string.costs_budgets)) }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CostStat(stringResource(R.string.costs_incidents), budgets.activeIncidents.size.toString(), Modifier.weight(1f))
                    CostStat(stringResource(R.string.costs_paused_agents), budgets.pausedAgentCount.toString(), Modifier.weight(1f))
                    CostStat(stringResource(R.string.costs_paused_projects), budgets.pausedProjectCount.toString(), Modifier.weight(1f))
                }
            }
            if (budgets.policies.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.costs_no_policies),
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
                        stringResource(if (p.windowKind == "lifetime") R.string.costs_policy_lifetime else R.string.costs_policy, name, costsScopeLabel(p.scopeType)),
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
                listOfNotNull(
                    stringResource(R.string.costs_tokens_in_out, adminCompact(a.inputTokens), adminCompact(a.outputTokens)),
                    if (a.cachedInputTokens > 0) stringResource(R.string.run_tokens_cached, adminCompact(a.cachedInputTokens)) else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val runs = a.apiRunCount + a.subscriptionRunCount
            if (runs > 0) {
                Text(
                    pluralStringResource(R.plurals.costs_runs, runs, runs).let {
                        if (a.subscriptionRunCount > 0) stringResource(R.string.costs_runs_with_subscription, it, a.subscriptionRunCount) else it
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(formatCents(a.costCents), style = MaterialTheme.typography.titleSmall)
    }
}
