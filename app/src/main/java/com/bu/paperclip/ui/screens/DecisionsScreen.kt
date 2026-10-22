package com.bu.paperclip.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bu.paperclip.data.AppState
import com.bu.paperclip.data.AttentionItem
import com.bu.paperclip.data.Decision
import com.bu.paperclip.data.DecisionOption
import com.bu.paperclip.data.IssueStore
import com.bu.paperclip.data.Repo
import com.bu.paperclip.ui.DecisionFocus
import com.bu.paperclip.ui.Nav
import com.bu.paperclip.ui.components.AgentAvatar
import com.bu.paperclip.ui.components.EmptyState
import com.bu.paperclip.ui.components.LiveRefresh
import com.bu.paperclip.ui.components.LoadContent
import com.bu.paperclip.ui.components.Markdown
import com.bu.paperclip.ui.components.Pill
import com.bu.paperclip.ui.components.errorMessage
import com.bu.paperclip.ui.components.rememberLoader
import com.bu.paperclip.ui.components.rememberMinuteTick
import com.bu.paperclip.ui.theme.LocalStatusColors
import com.bu.paperclip.util.parseInstant
import com.bu.paperclip.util.relativeTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import java.util.UUID

private data class DeskData(val items: List<AttentionItem>, val openDecisions: Map<String, Decision>)

/** Desk shelves, as on the web: decide-by due first, then today's arrivals, then the rest. */
internal enum class Shelf(val label: String) { DecideNow("Decide now"), NewToday("New today"), Earlier("Earlier") }

/** End of the decide-by window in UTC (the server counts the badge in UTC days), or null for "whenever". */
internal fun decideByDeadline(decideBy: String?, now: Instant = Instant.now()): Instant? {
    val today = now.atOffset(ZoneOffset.UTC).toLocalDate()
    fun endOf(d: LocalDate) = d.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).minusMillis(1)
    return when (decideBy) {
        null, "", "whenever" -> null
        "today" -> endOf(today)
        "this_week" -> endOf(today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)))
        else -> runCatching { endOf(LocalDate.parse(decideBy)) }.getOrNull()
    }
}

internal fun shelfOf(item: AttentionItem, now: Instant = Instant.now()): Shelf {
    val endOfToday = now.atOffset(ZoneOffset.UTC).toLocalDate().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
    val deadline = decideByDeadline(item.decideBy, now)
    if (deadline != null && deadline < endOfToday) return Shelf.DecideNow
    val startOfToday = endOfToday.minusSeconds(86_400)
    val created = parseInstant(item.createdAt ?: item.activityAt)
    return if (created != null && !created.isBefore(startOfToday)) Shelf.NewToday else Shelf.Earlier
}

private fun decideByLabel(decideBy: String?): String? = when (decideBy) {
    null, "", "whenever" -> null
    "today" -> "Due today"
    "this_week" -> "Due this week"
    else -> runCatching { "Due " + LocalDate.parse(decideBy).let { "${it.dayOfMonth} ${it.month.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() }}" } }.getOrNull()
}

private val INLINE_KINDS = setOf("approval", "decision", "issue_thread_interaction", "join_request")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DecisionsScreen(nav: Nav) {
    val company by AppState.company.collectAsState()
    val cid = company?.id ?: ""
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val loader = rememberLoader(cid) {
        coroutineScope {
            val feed = async { Repo.attentionDesk(cid) }
            val open = async { runCatching { Repo.decisions(cid, "open", 100) }.getOrDefault(emptyList()) }
            DeskData(feed.await().items, open.await().associateBy { it.id })
        }
    }
    LiveRefresh(filter = { it.type == "activity.logged" || it.type == "heartbeat.run.status" }) { loader.refresh() }

    var kindFilter by rememberSaveable(cid) { mutableStateOf<String?>(null) }
    val expanded = remember(cid) { mutableStateMapOf<String, Boolean>() }
    val openSections = remember(cid) { mutableStateMapOf<String, Boolean>() }
    var hidden by remember(cid) { mutableStateOf(emptySet<String>()) }
    val listState = rememberLazyListState()
    val focus by DecisionFocus.pending.collectAsState()

    fun refreshAll() { loader.refresh(); scope.launch { AppState.refreshBadges() } }

    /** Runs [call], hides [item] meanwhile, and offers Undo when [undo] is given. */
    fun act(item: AttentionItem?, message: String, undo: (suspend () -> Unit)? = null, call: suspend () -> Unit) {
        item?.let { hidden = hidden + it.id }
        scope.launch {
            try {
                call()
                refreshAll()
                val res = snackbar.showSnackbar(message, actionLabel = if (undo != null) "Undo" else null, duration = SnackbarDuration.Short)
                if (res == SnackbarResult.ActionPerformed && undo != null) { undo(); refreshAll() }
            } catch (c: CancellationException) { throw c } catch (t: Throwable) {
                snackbar.showSnackbar(errorMessage(t))
                refreshAll()
            } finally {
                item?.let { hidden = hidden - it.id }
            }
        }
    }

    fun dismiss(item: AttentionItem) {
        val key = item.dismissalKey ?: return
        act(item, "Dismissed", undo = { Repo.undismiss(cid, key) }) { Repo.dismiss(cid, key) }
    }
    fun snooze(item: AttentionItem, until: Instant, label: String) {
        val key = item.dismissalKey ?: return
        act(item, "Snoozed $label", undo = { Repo.undismiss(cid, key) }) { Repo.snooze(cid, key, until.toString()) }
    }
    fun restore(item: AttentionItem) {
        val key = item.dismissalKey ?: return
        act(item, "Back on the desk") { Repo.undismiss(cid, key) }
    }
    fun setDecideBy(item: AttentionItem, value: String?) =
        act(null, if (value == null) "Cleared" else "Decide ${decideByLabel(value)?.removePrefix("Due ") ?: "whenever"}") {
            Repo.setDecideBy(cid, item.sourceKind, item.subject.id, value)
        }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Decisions", style = MaterialTheme.typography.headlineSmall) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LoadContent(loader, Modifier.padding(pad)) { data ->
            rememberMinuteTick()
            val all = data.items.filter { it.id !in hidden }
            val active = all.filter { it.dismissal?.isActive != true }
            val desk = active.filter { !it.shelf }
            val kinds = desk.groupingBy { it.sourceKind }.eachCount().entries.sortedByDescending { it.value }
            val shown = desk.filter { kindFilter == null || it.sourceKind == kindFilter }
            val shelves = shown.groupBy { shelfOf(it) }
            val snoozed = all.filter { it.dismissal?.isActive == true && it.dismissal.kind == "snooze" }
            val dismissed = all.filter { it.dismissal?.isActive == true && it.dismissal.kind == "dismiss" }
            val aging = active.filter { it.shelf }

            // A decision link (notification, mention) opens and scrolls to its card.
            LaunchedEffect(focus, data) {
                val id = focus ?: return@LaunchedEffect
                val target = desk.firstOrNull { it.sourceKind == "decision" && it.subject.id == id } ?: return@LaunchedEffect
                kindFilter = null
                expanded[target.id] = true
                DecisionFocus.pending.value = null
            }

            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)) {
                if (kinds.size > 1) item(key = "filters", contentType = "filters") {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = kindFilter == null, onClick = { kindFilter = null }, label = { Text("All ${desk.size}") })
                        kinds.forEach { (k, n) ->
                            val sample = desk.first { it.sourceKind == k }
                            FilterChip(selected = kindFilter == k, onClick = { kindFilter = if (kindFilter == k) null else k }, label = { Text("${deskKindLabel(sample)} $n") })
                        }
                    }
                }
                if (desk.isEmpty()) item(key = "empty", contentType = "empty") {
                    EmptyState("You're all caught up.\nNothing needs a decision from you right now.", Icons.Outlined.TaskAlt)
                }
                Shelf.entries.forEach { shelf ->
                    val rows = shelves[shelf].orEmpty().let { list ->
                        if (shelf == Shelf.DecideNow) list.sortedBy { decideByDeadline(it.decideBy) }
                        else list.sortedByDescending { parseInstant(it.createdAt ?: it.activityAt) }
                    }
                    if (rows.isNotEmpty()) {
                        item(key = "shelf-${shelf.name}") { DeskHeader(shelf.label, rows.size, urgent = shelf == Shelf.DecideNow) }
                        items(rows, key = { it.id }, contentType = { it.sourceKind }) { item ->
                            InboxSwipeable(enabled = item.dismissalKey != null, icon = Icons.Outlined.DoneAll, label = "Dismiss", onSwiped = { dismiss(item) }) {
                                DeskCard(
                                    item = item,
                                    decision = data.openDecisions[item.subject.id].takeIf { item.sourceKind == "decision" },
                                    expanded = expanded[item.id] == true,
                                    onToggle = { expanded[item.id] = expanded[item.id] != true },
                                    nav = nav,
                                    cid = cid,
                                    onDismiss = { dismiss(item) },
                                    onSnooze = { until, label -> snooze(item, until, label) },
                                    onDecideBy = { setDecideBy(item, it) },
                                    onResolved = { msg -> act(item, msg) {} },
                                    onError = { scope.launch { snackbar.showSnackbar(it) } },
                                )
                            }
                        }
                    }
                }
                listOf(
                    Triple("snoozed", "Snoozed", snoozed),
                    Triple("dismissed", "Dismissed", dismissed),
                    Triple("aging", "Aging · idle 30+ days", aging),
                ).forEach { (key, label, rows) ->
                    if (rows.isNotEmpty()) {
                        item(key = "sec-$key") { SectionToggle(label, rows.size, openSections[key] == true) { openSections[key] = openSections[key] != true } }
                        if (openSections[key] == true) items(rows, key = { "$key-${it.id}" }, contentType = { it.sourceKind }) { item ->
                            CompactRow(item, nav, trailing = {
                                if (key != "aging") TextButton(onClick = { restore(item) }) {
                                    Icon(Icons.Outlined.Restore, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Restore")
                                }
                            }, note = item.dismissal?.snoozedUntil?.let { "until ${relativeTime(it)}" })
                        }
                    }
                }
                listOf("decided" to "Decided", "expired" to "Expired").forEach { (status, label) ->
                    item(key = "hist-$status") {
                        HistorySection(cid, status, label, open = openSections[status] == true, nav = nav) { openSections[status] = openSections[status] != true }
                    }
                }
                item(key = "inbox-link", contentType = "inbox-link") {
                    TextButton(onClick = { nav.inbox() }, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                        Icon(Icons.Outlined.Inbox, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Unread and assigned tasks are in Inbox")
                    }
                }
            }
        }
    }
}

private fun deskKindLabel(item: AttentionItem): String = when (item.sourceKind) {
    "issue_thread_interaction" -> "Requests"
    "approval" -> "Approvals"
    "decision" -> "Decisions"
    "failed_run" -> "Failed runs"
    "join_request" -> "Join requests"
    "blocker_attention" -> "Blocked"
    "agent_error_alert" -> "Agent errors"
    "budget_alert" -> "Budget"
    else -> inboxKindLabel(item)
}

@Composable
private fun DeskHeader(label: String, count: Int, urgent: Boolean) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
            color = if (urgent) LocalStatusColors.current.red else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionToggle(label: String, count: Int, open: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        if (count > 0) { Badge(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = MaterialTheme.colorScheme.onSurfaceVariant) { Text(if (count > 50) "50+" else "$count") }; Spacer(Modifier.width(8.dp)) }
        Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (open) "Collapse" else "Expand", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun severityColor(severity: String): Color {
    val sc = LocalStatusColors.current
    return when (severity) { "critical" -> sc.red; "high" -> sc.orange; "medium" -> sc.yellow; else -> sc.gray }
}

@Composable
private fun CompactRow(item: AttentionItem, nav: Nav, trailing: @Composable () -> Unit, note: String? = null) {
    Row(Modifier.fillMaxWidth().clickable { inboxOpenAttention(nav, item) }.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(inboxIcon(item), null, Modifier.size(18.dp), tint = severityColor(item.severity))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.subject.title ?: item.relatedIssue?.title ?: "Untitled", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(inboxKindLabel(item), note).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun DeskCard(
    item: AttentionItem,
    decision: Decision?,
    expanded: Boolean,
    onToggle: () -> Unit,
    nav: Nav,
    cid: String,
    onDismiss: () -> Unit,
    onSnooze: (Instant, String) -> Unit,
    onDecideBy: (String?) -> Unit,
    onResolved: (String) -> Unit,
    onError: (String) -> Unit,
) {
    val sev = severityColor(item.severity)
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    fun run(message: String, call: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { call(); onResolved(message) }
            catch (c: CancellationException) { throw c }
            catch (t: Throwable) { onError(errorMessage(t)) }
            finally { busy = false }
        }
    }

    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, if (expanded) sev.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .animateContentSize(),
    ) {
        Box {
            Row(
                Modifier.fillMaxWidth().combinedClickable(onClick = onToggle, onLongClick = { menu = true }).padding(14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(sev.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
                    Icon(inboxIcon(item), null, tint = sev, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(inboxKindLabel(item), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = sev)
                        val ref = item.relatedIssue?.identifier ?: item.subject.identifier
                        if (ref != null) Text(" · $ref", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text(relativeTime(item.activityAt ?: item.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        item.subject.title ?: item.relatedIssue?.title ?: "Untitled",
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium),
                        maxLines = if (expanded) 6 else 2, overflow = TextOverflow.Ellipsis,
                    )
                    val why = item.whyNow?.takeIf { it.isNotBlank() }
                    if (why != null) Text(why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (expanded) 4 else 1, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        item.originAgentName?.let { name ->
                            val agent = AppState.agents.collectAsState().value.firstOrNull { it.name == name }
                            if (agent != null) { AgentAvatar(agent.id, 16.dp); Spacer(Modifier.width(5.dp)) }
                            Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                        }
                        decideByLabel(item.decideBy)?.let { Pill(it, if (shelfOf(item) == Shelf.DecideNow) LocalStatusColors.current.red else LocalStatusColors.current.blue) }
                        item.expiresAt?.let { exp ->
                            if (item.sourceKind == "decision") {
                                Spacer(Modifier.width(6.dp))
                                Text("expires ${relativeTime(exp)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Open") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null) }, onClick = { menu = false; inboxOpenAttention(nav, item) })
                if (item.dismissalKey != null) {
                    SnoozeItems(onSnooze) { menu = false }
                    DropdownMenuItem(text = { Text("Dismiss") }, leadingIcon = { Icon(Icons.Outlined.DoneAll, null) }, onClick = { menu = false; onDismiss() })
                }
            }
        }

        if (expanded) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    item.sourceKind == "decision" && decision != null ->
                        DecisionBody(decision, busy, onDecide = { opt, inputs ->
                            run("Decided: ${opt.label}") { Repo.decide(decision.id, opt.id, inputs, UUID.randomUUID().toString()) }
                        }, onDismissDecision = { run("Decision dismissed") { Repo.dismissDecision(decision.id) } })
                    item.sourceKind == "decision" ->
                        Text("This decision is no longer open.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    item.sourceKind == "approval" && item.inlineResolvable -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !busy, onClick = { run("Approved") { Repo.decideApproval(item.subject.id, "approve", null) } }) { Text("Approve") }
                        OutlinedButton(enabled = !busy, onClick = { run("Revision requested") { Repo.decideApproval(item.subject.id, "request-revision", null) } }) { Text("Request changes") }
                        OutlinedButton(enabled = !busy, onClick = { run("Rejected") { Repo.decideApproval(item.subject.id, "reject", null) } },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Reject") }
                    }
                    item.sourceKind == "join_request" && item.inlineResolvable -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !busy, onClick = { run("Join request approved") { Repo.decideJoinRequest(cid, item.subject.id, true) } }) { Text("Approve") }
                        OutlinedButton(enabled = !busy, onClick = { run("Join request rejected") { Repo.decideJoinRequest(cid, item.subject.id, false) } }) { Text("Reject") }
                    }
                    item.sourceKind == "issue_thread_interaction" && inboxDetailKind(item) in setOf("confirmation", "plan_approval") -> {
                        val issueId = (item.subject.metadata?.get("issueId") as? JsonPrimitive)?.contentOrNull ?: item.relatedIssue?.id
                        val excerpt = (item.detail?.get("promptExcerpt") ?: item.detail?.get("summaryExcerpt")) as? JsonPrimitive
                        excerpt?.contentOrNull?.let { Markdown(it, fontSizeSp = 14f) }
                        if (issueId != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val accept = item.decisionVerbs.firstOrNull { it.id == "accept" }?.label ?: "Confirm"
                            val reject = item.decisionVerbs.firstOrNull { it.id == "reject" }?.label ?: "Decline"
                            Button(enabled = !busy, onClick = { run(accept) { Repo.accept(issueId, item.subject.id) } }) { Text(accept) }
                            OutlinedButton(enabled = !busy, onClick = { run(reject) { Repo.reject(issueId, item.subject.id, null) } }) { Text(reject) }
                        }
                    }
                    else -> {
                        val excerpt = listOf("firstQuestionText", "firstTaskTitle", "promptExcerpt", "summaryExcerpt", "failureReasonExcerpt")
                            .firstNotNullOfOrNull { (item.detail?.get(it) as? JsonPrimitive)?.contentOrNull }
                        excerpt?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 6, overflow = TextOverflow.Ellipsis) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { inboxOpenAttention(nav, item) }) {
                        Text(if (item.sourceKind in INLINE_KINDS && item.sourceKind != "decision") "Open details" else "Open")
                    }
                    Spacer(Modifier.weight(1f))
                    DecideByMenu(item.decideBy, onDecideBy)
                }
            }
        }
    }
}

@Composable
private fun SnoozeItems(onSnooze: (Instant, String) -> Unit, close: () -> Unit) {
    val zone = ZoneId.systemDefault()
    listOf(
        Triple("Snooze 1 hour", "for 1 hour") { Instant.now().plusSeconds(3600) },
        Triple("Snooze 4 hours", "for 4 hours") { Instant.now().plusSeconds(4 * 3600) },
        Triple("Snooze until tomorrow", "until tomorrow 9:00") { LocalDate.now(zone).plusDays(1).atTime(LocalTime.of(9, 0)).atZone(zone).toInstant() },
        Triple("Snooze until next week", "until next Monday") {
            LocalDate.now(zone).with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atTime(LocalTime.of(9, 0)).atZone(zone).toInstant()
        },
    ).forEach { (text, label, at) ->
        DropdownMenuItem(text = { Text(text) }, leadingIcon = { Icon(Icons.Outlined.Snooze, null) }, onClick = { close(); onSnooze(at(), label) })
    }
}

@Composable
private fun DecideByMenu(current: String?, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text(decideByLabel(current) ?: "When to decide…") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf("today" to "Today", "this_week" to "This week", "whenever" to "Whenever").forEach { (v, label) ->
                DropdownMenuItem(
                    text = { Text(if (current == v) "$label ✓" else label) },
                    onClick = { open = false; onPick(if (current == v) null else v) },
                )
            }
        }
    }
}

/** An agent-proposed decision: its body, any inputs, and one button per option. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DecisionBody(d: Decision, busy: Boolean, onDecide: (DecisionOption, Map<String, String>) -> Unit, onDismissDecision: () -> Unit) {
    val inputs = remember(d.id) { mutableStateMapOf<String, String>() }
    var confirmCancel by remember { mutableStateOf<DecisionOption?>(null) }
    val stale = d.targetChanged.orEmpty().filterValues { it }.keys
    if (d.body.isNotBlank()) Markdown(d.body, fontSizeSp = 14f)
    if (stale.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.WarningAmber, null, Modifier.size(16.dp), tint = LocalStatusColors.current.orange)
        Spacer(Modifier.width(6.dp))
        Text("A task this affects changed since it was proposed. Some options may be blocked.", style = MaterialTheme.typography.bodySmall, color = LocalStatusColors.current.orange)
    }
    d.inputs.orEmpty().forEach { input ->
        OutlinedTextField(
            value = inputs[input.id].orEmpty(),
            onValueChange = { v -> inputs[input.id] = input.maxLength?.let { v.take(it) } ?: v },
            label = { Text(input.label + if (input.required) " *" else "") },
            placeholder = input.placeholder?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    val missing = d.inputs.orEmpty().any { it.required && inputs[it.id].isNullOrBlank() }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        d.options.forEach { opt ->
            val blocked = opt.effects.any { e -> (e["staleness"] as? JsonPrimitive)?.contentOrNull == "strict" && (e["targetIssueId"] as? JsonPrimitive)?.contentOrNull in stale }
            val enabled = !busy && !missing && !blocked
            val destructive = opt.style == "destructive"
            val click = { if (opt.effects.any { (it["type"] as? JsonPrimitive)?.contentOrNull == "cancel_issue_tree" }) confirmCancel = opt else onDecide(opt, inputs.toMap()) }
            Column(Modifier.fillMaxWidth()) {
                when {
                    opt.style == "primary" -> Button(onClick = click, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(opt.label) }
                    destructive -> OutlinedButton(onClick = click, enabled = enabled, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(opt.label) }
                    else -> OutlinedButton(onClick = click, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(opt.label) }
                }
                val note = listOfNotNull(opt.description?.takeIf { it.isNotBlank() }, if (blocked) "Blocked · target changed" else null).joinToString(" · ")
                if (note.isNotEmpty()) Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
            }
        }
        if (d.options.none { it.effects.isEmpty() }) TextButton(onClick = onDismissDecision, enabled = !busy) { Text("Dismiss — no effects") }
    }
    confirmCancel?.let { opt ->
        val targets = opt.effects.mapNotNull { (it["targetIssueId"] as? JsonPrimitive)?.contentOrNull }.distinct()
        val label = targets.joinToString(", ") { IssueStore.get(it)?.identifier ?: it.take(8) }
        val count = targets.sumOf { (d.targetSnapshots?.get(it)?.descendantIds?.size ?: d.targetSnapshots?.get(it)?.descendantCount ?: 0) + 1 }
        var typed by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { confirmCancel = null },
            title = { Text(opt.label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This cancels $label and its sub-tasks ($count task${if (count == 1) "" else "s"}). Type $label to confirm.")
                    OutlinedTextField(value = typed, onValueChange = { typed = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = typed.trim().equals(label, ignoreCase = true), onClick = { confirmCancel = null; onDecide(opt, inputs.toMap()) }) {
                    Text("Cancel tasks", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmCancel = null }) { Text("Keep") } },
        )
    }
}

/** Decided / Expired history, fetched only when opened. */
@Composable
private fun HistorySection(cid: String, status: String, label: String, open: Boolean, nav: Nav, onToggle: () -> Unit) {
    var rows by remember(cid, status) { mutableStateOf<List<Decision>?>(null) }
    var error by remember(cid, status) { mutableStateOf<String?>(null) }
    LaunchedEffect(open, cid, status) {
        if (open) try { rows = Repo.decisions(cid, status); error = null }
        catch (c: CancellationException) { throw c } catch (t: Throwable) { error = errorMessage(t) }
    }
    Column {
        SectionToggle(label, rows?.size ?: 0, open, onToggle)
        if (open) when {
            error != null -> Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
            rows == null -> Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            rows!!.isEmpty() -> Text("No ${label.lowercase()} decisions.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            else -> rows!!.take(50).forEach { d ->
                val chosen = d.options.firstOrNull { it.id == d.chosenOptionId }?.label
                val outcome = when {
                    d.chosenOptionId == "dismissed" || d.metadata?.get("dismissed")?.let { (it as? JsonPrimitive)?.contentOrNull } == "true" -> "Dismissed"
                    status == "expired" -> when ((d.metadata?.get("expiredReason") as? JsonPrimitive)?.contentOrNull) {
                        "target_gone" -> "Expired · task removed"; "target_completed" -> "Expired · task finished"; else -> "Expired"
                    }
                    d.executionStatus == "failed" -> "Failed"
                    d.executionStatus == "partial" -> "Partly applied"
                    chosen != null -> "Chose: $chosen"
                    else -> "Decided"
                }
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = d.originIssueId != null) { d.originIssueId?.let { nav.issue(IssueStore.get(it)?.identifier ?: it) } }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(d.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(outcome, relativeTime(d.decidedAt ?: d.expiresAt ?: d.createdAt)).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
