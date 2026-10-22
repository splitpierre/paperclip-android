package com.bu.paperclip.ui.screens

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Healing
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.ThumbsUpDown
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bu.paperclip.data.AppState
import com.bu.paperclip.ui.components.AgentAvatar
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Color
import com.bu.paperclip.data.AttentionItem
import com.bu.paperclip.data.Issue
import com.bu.paperclip.data.Repo
import com.bu.paperclip.net.Api
import com.bu.paperclip.ui.Nav
import com.bu.paperclip.ui.components.Dot
import com.bu.paperclip.ui.components.EmptyState
import com.bu.paperclip.ui.components.IssueRow
import com.bu.paperclip.ui.components.LiveRefresh
import com.bu.paperclip.ui.components.LoadContent
import com.bu.paperclip.ui.components.Links
import com.bu.paperclip.ui.components.Pill
import com.bu.paperclip.ui.components.PollWhileDisconnected
import com.bu.paperclip.ui.components.errorMessage
import com.bu.paperclip.ui.components.rememberLoader
import com.bu.paperclip.ui.components.rememberMinuteTick
import com.bu.paperclip.ui.theme.LocalStatusColors
import com.bu.paperclip.util.relativeTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private const val INBOX_MINE_STATUSES = "backlog,todo,in_progress,in_review,blocked,done"

private fun inboxIsLiveRelevant(type: String) = type == "activity.logged" || type == "heartbeat.run.status"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(nav: Nav) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val company by AppState.company.collectAsState()
    val badges by AppState.badges.collectAsState()
    val cid = company?.id ?: ""
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Inbox", style = MaterialTheme.typography.headlineSmall) }, navigationIcon = { BackIcon { nav.back() } }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
                listOf("Needs me", "Unread", "Mine").forEachIndexed { i, label ->
                    Tab(
                        selected = tab == i,
                        onClick = { tab = i },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(label)
                                if (i == 0 && badges.inbox > 0) {
                                    Spacer(Modifier.width(6.dp))
                                    Badge(containerColor = LocalStatusColors.current.red, contentColor = Color.White) { Text(if (badges.inbox > 99) "99+" else "${badges.inbox}") }
                                }
                            }
                        },
                    )
                }
            }
            if (cid.isNotEmpty()) {
                when (tab) {
                    0 -> InboxNeedsMe(nav, cid, snackbar, scope)
                    1 -> InboxUnread(nav, cid)
                    else -> InboxMine(nav, cid, snackbar, scope)
                }
            }
        }
    }
}

// ---------------- Needs me (attention feed) ----------------

private data class InboxFeedPage(val items: List<AttentionItem>, val nextCursor: String?)

@Composable
private fun InboxNeedsMe(nav: Nav, cid: String, snackbar: SnackbarHostState, scope: CoroutineScope) {
    val loader = rememberLoader(cid) { Repo.attention(cid).let { InboxFeedPage(it.items, it.nextCursor) } }
    var more by remember(cid) { mutableStateOf<List<AttentionItem>>(emptyList()) }
    var cursor by remember(cid) { mutableStateOf<String?>(null) }
    var loadingMore by remember(cid) { mutableStateOf(false) }
    var hidden by remember(cid) { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(loader.value) {
        more = emptyList()
        cursor = loader.value?.nextCursor
        hidden = emptySet()
    }
    LiveRefresh(filter = { inboxIsLiveRelevant(it.type) }) { loader.refresh() }
    PollWhileDisconnected { loader.refresh() }

    fun hideWithUndo(item: AttentionItem, message: String, call: suspend (String) -> Unit) {
        val key = item.dismissalKey ?: return
        hidden = hidden + item.id
        scope.launch {
            try {
                call(key)
                AppState.refreshBadges()
                val res = snackbar.showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Short)
                if (res == SnackbarResult.ActionPerformed) {
                    Api.delete("/companies/$cid/inbox-dismissals/" + Uri.encode(key))
                    hidden = hidden - item.id
                    AppState.refreshBadges()
                }
            } catch (t: Throwable) {
                hidden = hidden - item.id
                snackbar.showSnackbar(errorMessage(t))
            }
        }
    }

    fun dismiss(item: AttentionItem) = hideWithUndo(item, "Dismissed") { Repo.dismiss(cid, it) }
    fun snooze(item: AttentionItem, until: Instant, label: String) =
        hideWithUndo(item, "Snoozed $label") { Repo.snooze(cid, it, until.toString()) }

    LoadContent(loader) { page ->
        val items = (page.items + more).distinctBy { it.id }.filter { it.id !in hidden }
        LazyColumn(Modifier.fillMaxSize()) {
            if (items.isEmpty()) {
                item {
                    EmptyState(
                        "Nothing needs you right now.\nAgent questions, approvals and failures show up here.",
                        Icons.Outlined.TaskAlt,
                    )
                }
            }
            items(items, key = { it.id }, contentType = { it.sourceKind }) { item ->
                InboxSwipeable(
                    enabled = item.dismissalKey != null,
                    icon = Icons.Outlined.DoneAll,
                    label = "Dismiss",
                    onSwiped = { dismiss(item) },
                ) {
                    InboxAttentionRow(
                        item = item,
                        onClick = { inboxOpenAttention(nav, item) },
                        onSnooze = { until, label -> snooze(item, until, label) },
                        onDismiss = { dismiss(item) },
                    )
                }
            }
            if (cursor != null && items.isNotEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        OutlinedButton(
                            enabled = !loadingMore,
                            onClick = {
                                val c = cursor ?: return@OutlinedButton
                                loadingMore = true
                                scope.launch {
                                    try {
                                        val next = Repo.attention(cid, cursor = c)
                                        more = more + next.items
                                        cursor = next.nextCursor
                                    } catch (t: Throwable) {
                                        snackbar.showSnackbar(errorMessage(t))
                                    } finally {
                                        loadingMore = false
                                    }
                                }
                            },
                        ) { Text(if (loadingMore) "Loading…" else "Load more") }
                    }
                }
            }
        }
    }
}

internal fun inboxOpenAttention(nav: Nav, item: AttentionItem) {
    val issueRef = item.relatedIssue?.let { it.identifier ?: it.id.ifEmpty { null } }
    val s = item.subject
    val issueKinds = setOf("issue_thread_interaction", "blocker_attention", "review", "recovery_action", "productivity_review")
    when {
        item.sourceKind == "approval" || s.kind == "approval" -> nav.approval(s.id)
        item.sourceKind == "failed_run" && s.kind == "run" -> nav.run(s.id)
        item.sourceKind == "agent_error_alert" && s.kind == "agent" -> nav.agent(s.id)
        item.sourceKind in issueKinds && issueRef != null -> nav.issue(issueRef)
        s.kind == "issue" && s.id.isNotEmpty() -> nav.issue(s.identifier ?: s.id)
        s.kind == "run" && s.id.isNotEmpty() -> nav.run(s.id)
        s.kind == "agent" && s.id.isNotEmpty() -> nav.agent(s.id)
        !s.href.isNullOrBlank() && Links.handler(s.href) -> {}
        issueRef != null -> nav.issue(issueRef)
        item.sourceKind == "budget_alert" -> nav.costs()
    }
}

internal fun inboxDetailKind(item: AttentionItem): String? =
    (item.detail?.get("kind") as? JsonPrimitive)?.contentOrNull

internal fun inboxIcon(item: AttentionItem): ImageVector = when (item.sourceKind) {
    "issue_thread_interaction" -> when (inboxDetailKind(item)) {
        "questions" -> Icons.Outlined.QuestionAnswer
        "suggested_tasks" -> Icons.Outlined.Checklist
        "item_verdicts" -> Icons.Outlined.ThumbsUpDown
        else -> Icons.Outlined.CheckCircle
    }
    "approval" -> Icons.Outlined.Gavel
    "decision" -> Icons.Outlined.Lightbulb
    "failed_run" -> Icons.Outlined.ErrorOutline
    "budget_alert" -> Icons.Outlined.AttachMoney
    "agent_error_alert" -> Icons.Outlined.SmartToy
    "blocker_attention" -> Icons.Outlined.Block
    "review", "productivity_review" -> Icons.Outlined.RateReview
    "recovery_action" -> Icons.Outlined.Healing
    "join_request" -> Icons.Outlined.PersonAdd
    else -> Icons.Outlined.NotificationsNone
}

internal fun inboxKindLabel(item: AttentionItem): String = when (item.sourceKind) {
    "issue_thread_interaction" -> when (inboxDetailKind(item)) {
        "questions" -> "Question"
        "plan_approval" -> "Plan approval"
        "suggested_tasks" -> "Suggested tasks"
        "checkbox_confirmation" -> "Choose options"
        "item_verdicts" -> "Review items"
        else -> "Confirmation"
    }
    "approval" -> "Approval"
    "decision" -> "Decision"
    "failed_run" -> "Failed run"
    "budget_alert" -> "Budget"
    "agent_error_alert" -> "Agent error"
    "blocker_attention" -> "Blocked"
    "review", "productivity_review" -> "Review"
    "recovery_action" -> "Recovery"
    "join_request" -> "Join request"
    else -> item.sourceKind.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InboxAttentionRow(
    item: AttentionItem,
    onClick: () -> Unit,
    onSnooze: (Instant, String) -> Unit,
    onDismiss: () -> Unit,
) {
    rememberMinuteTick()
    val sc = LocalStatusColors.current
    val sevColor = when (item.severity) {
        "critical" -> sc.red
        "high" -> sc.orange
        "medium" -> sc.yellow
        else -> sc.gray
    }
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .combinedClickable(onClick = onClick, onLongClick = { menu = true })
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(sevColor.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center,
            ) { Icon(inboxIcon(item), null, tint = sevColor, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(inboxKindLabel(item), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = sevColor)
                    val ref = item.relatedIssue?.identifier ?: item.subject.identifier
                    if (ref != null) {
                        Text(" · $ref", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        relativeTime(item.activityAt ?: item.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    item.subject.title ?: item.relatedIssue?.title ?: "Untitled",
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // One line of context: the task it's about, or why it's here.
                val relatedTitle = item.relatedIssue?.title?.takeIf { it != item.subject.title }
                val context = relatedTitle ?: item.whyNow
                if (!context.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        context,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val agentName = item.originAgentName
                if (!agentName.isNullOrBlank()) {
                    val agent = AppState.agents.collectAsState().value.firstOrNull { it.name == agentName }
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (agent != null) { AgentAvatar(agent.id, 18.dp); Spacer(Modifier.width(6.dp)) }
                        Text(agentName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Open") }, onClick = { menu = false; onClick() })
            if (item.dismissalKey != null) {
                DropdownMenuItem(
                    text = { Text("Snooze 1 hour") },
                    leadingIcon = { Icon(Icons.Outlined.Snooze, null) },
                    onClick = { menu = false; onSnooze(Instant.now().plusSeconds(3600), "for 1 hour") },
                )
                DropdownMenuItem(
                    text = { Text("Snooze until tomorrow") },
                    leadingIcon = { Icon(Icons.Outlined.Snooze, null) },
                    onClick = {
                        menu = false
                        val zone = ZoneId.systemDefault()
                        val t = LocalDate.now(zone).plusDays(1).atTime(LocalTime.of(9, 0)).atZone(zone).toInstant()
                        onSnooze(t, "until tomorrow 9:00")
                    },
                )
                DropdownMenuItem(
                    text = { Text("Dismiss") },
                    leadingIcon = { Icon(Icons.Outlined.DoneAll, null) },
                    onClick = { menu = false; onDismiss() },
                )
            }
        }
    }
}

/** Swipe either way to trigger [onSwiped]; the caller removes the row. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InboxSwipeable(
    enabled: Boolean,
    icon: ImageVector,
    label: String,
    onSwiped: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            if (v != SwipeToDismissBoxValue.Settled) {
                onSwiped()
                true
            } else {
                false
            }
        },
        positionalThreshold = { it * 0.35f },
    )
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val alignEnd = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
            Row(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 24.dp),
                horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.width(8.dp))
                Text(label, color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.labelLarge)
            }
        },
    ) { content() }
}

// ---------------- Unread ----------------

@Composable
private fun InboxUnread(nav: Nav, cid: String) {
    val scope = rememberCoroutineScope()
    val loader = rememberLoader(cid) { Repo.issues(cid, unreadForUserId = "me", status = INBOX_MINE_STATUSES, limit = 200) }
    var marking by remember { mutableStateOf(false) }
    LiveRefresh(filter = { inboxIsLiveRelevant(it.type) }) { loader.refresh() }
    PollWhileDisconnected { loader.refresh() }

    LoadContent(loader) { issues ->
        val list = issues.filter { it.isUnreadForMe != false }
        LazyColumn(Modifier.fillMaxSize()) {
            if (list.isEmpty()) {
                item { EmptyState("You're all caught up.", Icons.Outlined.MarkEmailRead) }
            } else {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${list.size} unread",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            enabled = !marking,
                            onClick = {
                                marking = true
                                scope.launch {
                                    try {
                                        list.forEach { runCatching { Repo.markRead(it.id) } }
                                        loader.set(emptyList())
                                        AppState.refreshBadges()
                                        AppState.toast("Marked ${list.size} as read")
                                    } finally {
                                        marking = false
                                        loader.refresh()
                                    }
                                }
                            },
                        ) {
                            Icon(Icons.Outlined.DoneAll, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (marking) "Marking…" else "Mark all read")
                        }
                    }
                }
                items(list, key = { it.id }, contentType = { "task" }) { issue ->
                    IssueRow(issue, onClick = { nav.issue(issue.identifier ?: issue.id) })
                }
            }
        }
    }
}

// ---------------- Mine ----------------

@Composable
private fun InboxMine(nav: Nav, cid: String, snackbar: SnackbarHostState, scope: CoroutineScope) {
    val loader = rememberLoader(cid) {
        Repo.issues(cid, touchedByUserId = "me", inboxArchivedByUserId = "me", status = INBOX_MINE_STATUSES, limit = 200)
    }
    var hidden by remember(cid) { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(loader.value) { hidden = emptySet() }
    LiveRefresh(filter = { inboxIsLiveRelevant(it.type) }) { loader.refresh() }
    PollWhileDisconnected { loader.refresh() }

    fun archive(issue: Issue) {
        hidden = hidden + issue.id
        scope.launch {
            try {
                Repo.archiveFromInbox(issue.id)
                AppState.refreshBadges()
                val res = snackbar.showSnackbar("Archived ${issue.ref}", actionLabel = "Undo", duration = SnackbarDuration.Short)
                if (res == SnackbarResult.ActionPerformed) {
                    Repo.unarchiveFromInbox(issue.id)
                    hidden = hidden - issue.id
                    AppState.refreshBadges()
                }
            } catch (t: Throwable) {
                hidden = hidden - issue.id
                snackbar.showSnackbar(errorMessage(t))
            }
        }
    }

    LoadContent(loader) { issues ->
        val list = issues.filter { it.id !in hidden }
        LazyColumn(Modifier.fillMaxSize()) {
            if (list.isEmpty()) {
                item { EmptyState("Tasks you created, commented on or were assigned show up here.", Icons.Outlined.Archive) }
            } else {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Dot(MaterialTheme.colorScheme.tertiary, 7.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${list.count { it.isUnreadForMe == true }} unread · swipe to archive",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(list, key = { it.id }, contentType = { "task" }) { issue ->
                    InboxSwipeable(enabled = true, icon = Icons.Outlined.Archive, label = "Archive", onSwiped = { archive(issue) }) {
                        Box(Modifier.background(MaterialTheme.colorScheme.background)) {
                            IssueRow(issue, onClick = { nav.issue(issue.identifier ?: issue.id) })
                        }
                    }
                }
            }
        }
    }
}
