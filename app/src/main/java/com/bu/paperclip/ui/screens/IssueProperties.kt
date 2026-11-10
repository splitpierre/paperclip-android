package com.bu.paperclip.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bu.paperclip.R
import com.bu.paperclip.data.AppState
import com.bu.paperclip.data.Issue
import com.bu.paperclip.data.IssueStore
import com.bu.paperclip.data.IssueSummaryRef
import com.bu.paperclip.data.Label
import com.bu.paperclip.data.ProjectRef
import com.bu.paperclip.data.Repo
import com.bu.paperclip.data.WORK_MODES
import com.bu.paperclip.data.blockerCandidates
import com.bu.paperclip.data.jsonOf
import com.bu.paperclip.data.parentCandidates
import com.bu.paperclip.data.toggleId
import com.bu.paperclip.data.withBlockers
import com.bu.paperclip.data.withLabels
import com.bu.paperclip.data.withParent
import com.bu.paperclip.ui.Nav
import com.bu.paperclip.ui.components.AgentAvatar
import com.bu.paperclip.ui.components.Avatar
import com.bu.paperclip.ui.components.Pill
import com.bu.paperclip.ui.components.PriorityIcon
import com.bu.paperclip.ui.components.SectionHeader
import com.bu.paperclip.ui.components.StatusIcon
import com.bu.paperclip.ui.components.errorMessage
import com.bu.paperclip.ui.components.statusLabel
import com.bu.paperclip.ui.components.taskPriorityLabel
import com.bu.paperclip.util.L10n
import com.bu.paperclip.util.absoluteTime
import com.bu.paperclip.util.relativeTime
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/** Applies an edit: the request body plus the optimistic change to the issue. */
internal typealias IssuePatch = (body: JsonObject, change: (Issue) -> Issue) -> Unit

private enum class PropSheet { Status, Priority, WorkMode, Assignee, Project, Labels, Parent, BlockedBy }

/**
 * The web panel's Properties (`IssueProperties.tsx`), in its order: Work (status, priority, assignee,
 * project, labels, plus work mode, which the web sets from its composer), Relationships (parent,
 * blocked by, blocking) and About. Execution policy and workspace rows are left out (see the PR).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IssuePropertiesTab(issue: Issue, nav: Nav, onPatch: IssuePatch) {
    val ctx = LocalContext.current
    var sheet by remember(issue.id) { mutableStateOf<PropSheet?>(null) }
    val me by AppState.me.collectAsState()
    val mode = issue.workMode ?: "standard"

    Column(Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.issue_props_work))
        PropRow(stringResource(R.string.task_field_status), onClick = { sheet = PropSheet.Status }) {
            StatusIcon(issue.status, 14.dp); Spacer(Modifier.width(8.dp)); PropValue(statusLabel(issue.status))
        }
        PropRow(stringResource(R.string.task_field_priority), onClick = { sheet = PropSheet.Priority }) {
            PriorityIcon(issue.priority, 16.dp); Spacer(Modifier.width(8.dp)); PropValue(taskPriorityLabel(issue.priority))
        }
        PropRow(stringResource(R.string.issue_props_work_mode), onClick = { sheet = PropSheet.WorkMode }) {
            Icon(workModeIcon(mode), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp)); PropValue(workModeLabel(mode))
        }
        PropRow(stringResource(R.string.task_field_assignee), onClick = { sheet = PropSheet.Assignee }) {
            when {
                issue.assigneeAgentId != null -> { AgentAvatar(issue.assigneeAgentId, 20.dp); Spacer(Modifier.width(8.dp)); PropValue(AppState.agentName(issue.assigneeAgentId)) }
                issue.assigneeUserId != null -> {
                    Avatar(AppState.userName(issue.assigneeUserId), AppState.users.value[issue.assigneeUserId]?.image ?: me?.image?.takeIf { issue.assigneeUserId == me?.id }, 20.dp, isAgent = false)
                    Spacer(Modifier.width(8.dp)); PropValue(AppState.userName(issue.assigneeUserId))
                }
                else -> { Icon(Icons.Outlined.PersonOutline, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(8.dp)); PropValue(stringResource(R.string.common_unassigned), muted = true) }
            }
        }
        val project = issue.project ?: AppState.project(issue.projectId)?.let { ProjectRef(it.id, it.name, it.color) }
        PropRow(stringResource(R.string.task_field_project), onClick = { sheet = PropSheet.Project }) {
            if (project == null) PropValue(stringResource(R.string.task_no_project), muted = true) else {
                Box(Modifier.size(10.dp).clip(CircleShape).background(parseIssueColor(project.color) ?: MaterialTheme.colorScheme.outline))
                Spacer(Modifier.width(8.dp)); PropValue(project.name)
            }
        }
        PropRow(stringResource(R.string.issue_props_labels), onClick = { sheet = PropSheet.Labels }) {
            val labels = issue.labels.orEmpty()
            if (labels.isEmpty()) PropValue(stringResource(R.string.common_none), muted = true)
            else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                labels.forEach { l -> Pill(l.name, parseIssueColor(l.color) ?: MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }

        SectionHeader(stringResource(R.string.issue_props_relationships))
        val parent = issue.ancestors?.firstOrNull { it.id == issue.parentId }
            ?: IssueStore.get(issue.parentId)?.let { IssueSummaryRef(it.id, it.identifier, it.title, it.status) }
            ?: issue.parentId?.let { IssueSummaryRef(id = it) }
        PropRow(
            stringResource(R.string.issue_props_parent),
            onClick = { sheet = PropSheet.Parent },
            trailing = parent?.let { p -> { OpenButton { nav.issue(p.identifier ?: p.id) } } },
        ) {
            if (parent == null) PropValue(stringResource(R.string.issue_props_no_parent), muted = true) else RefLabel(parent)
        }
        val blockedBy = issue.blockedBy.orEmpty()
        PropRow(stringResource(R.string.issue_blocked_by), onClick = { sheet = PropSheet.BlockedBy }) {
            if (blockedBy.isEmpty()) PropValue(stringResource(R.string.issue_props_no_blockers), muted = true)
            else PropValue(pluralStringResource(R.plurals.issue_props_task_count, blockedBy.size, blockedBy.size))
        }
        blockedBy.forEach { b -> RefRow(b) { nav.issue(b.identifier ?: b.id) } }
        val blocks = issue.blocks.orEmpty()
        if (blocks.isNotEmpty()) {
            PropRow(stringResource(R.string.issue_props_blocking)) { PropValue(pluralStringResource(R.plurals.issue_props_task_count, blocks.size, blocks.size)) }
            blocks.forEach { b -> RefRow(b) { nav.issue(b.identifier ?: b.id) } }
        }

        SectionHeader(stringResource(R.string.issue_props_about))
        PropRow(
            stringResource(R.string.issue_props_identifier),
            onClick = {
                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("identifier", issue.ref))
                AppState.toast(L10n.str(R.string.common_copied))
            },
            trailing = { Icon(Icons.Outlined.ContentCopy, stringResource(R.string.issue_props_copy_identifier), Modifier.padding(12.dp).size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        ) { PropValue(issue.ref) }
        val createdBy = issue.createdByAgentId?.let { AppState.agentName(it) } ?: issue.createdByUserId?.let { AppState.userName(it) }
        if (createdBy != null) PropRow(stringResource(R.string.issue_props_created_by)) { PropValue(createdBy) }
        issue.startedAt?.let { PropRow(stringResource(R.string.issue_props_started)) { PropValue(absoluteTime(it)) } }
        issue.completedAt?.let { PropRow(stringResource(R.string.issue_props_completed)) { PropValue(absoluteTime(it)) } }
        issue.createdAt?.let { PropRow(stringResource(R.string.issue_props_created)) { PropValue(absoluteTime(it)) } }
        issue.updatedAt?.let { PropRow(stringResource(R.string.issue_props_updated)) { PropValue(relativeTime(it)) } }
        Spacer(Modifier.height(24.dp))
    }

    val close = { sheet = null }
    when (sheet) {
        PropSheet.Status -> IssueStatusSheet(issue.status, onPick = { s -> if (s != issue.status) onPatch(jsonOf("status" to s)) { it.copy(status = s) } }, onDismiss = close)
        PropSheet.Priority -> IssuePrioritySheet(issue.priority, onPick = { p -> if (p != issue.priority) onPatch(jsonOf("priority" to p)) { it.copy(priority = p) } }, onDismiss = close)
        PropSheet.WorkMode -> IssueWorkModeSheet(mode, onPick = { m -> if (m != mode) onPatch(jsonOf("workMode" to m)) { it.copy(workMode = m) } }, onDismiss = close)
        PropSheet.Assignee -> IssueAssigneeSheet(issue.assigneeAgentId, issue.assigneeUserId, showUsers = true, onPick = { a ->
            val (agentId, userId) = when (a) {
                is IssueAssignee.AgentA -> a.id to null
                is IssueAssignee.UserA -> null to a.id
                IssueAssignee.Me -> null to (me?.id ?: return@IssueAssigneeSheet)
                IssueAssignee.Nobody -> null to null
            }
            if (agentId != issue.assigneeAgentId || userId != issue.assigneeUserId) {
                onPatch(jsonOf("assigneeAgentId" to (agentId ?: JsonNull), "assigneeUserId" to (userId ?: JsonNull))) {
                    it.copy(assigneeAgentId = agentId, assigneeUserId = userId)
                }
            }
        }, onDismiss = close)
        PropSheet.Project -> IssueProjectSheet(issue.projectId, onPick = { p ->
            if (p != issue.projectId) {
                val pr = AppState.project(p)?.let { ProjectRef(it.id, it.name, it.color) }
                onPatch(jsonOf("projectId" to (p ?: JsonNull))) { it.copy(projectId = p, project = pr) }
            }
        }, onDismiss = close)
        PropSheet.Labels -> IssueLabelsSheet(issue.labelIds ?: issue.labels.orEmpty().map { it.id }, onSet = { ids, all ->
            onPatch(jsonOf("labelIds" to ids)) { it.withLabels(ids, all) }
        }, onDismiss = close)
        PropSheet.Parent -> IssueParentSheet(issue, onPick = { pid ->
            if (pid != issue.parentId) onPatch(jsonOf("parentId" to (pid ?: JsonNull))) { it.withParent(pid, IssueStore.issues.value) }
        }, onDismiss = close)
        PropSheet.BlockedBy -> IssueBlockersSheet(issue, onSet = { ids ->
            onPatch(jsonOf("blockedByIssueIds" to ids)) { it.withBlockers(ids, IssueStore.issues.value) }
        }, onDismiss = close)
        null -> Unit
    }
}

// ---------- rows ----------

@Composable
private fun PropRow(
    label: String,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    value: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 16.dp, end = if (trailing != null) 4.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(112.dp).padding(end = 8.dp))
        Row(Modifier.weight(1f).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { value() }
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PropValue(text: String, muted: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun RefLabel(r: IssueSummaryRef) {
    StatusIcon(r.status ?: "todo", 13.dp)
    Spacer(Modifier.width(8.dp))
    Text(
        listOfNotNull(r.identifier, r.title).joinToString(" ").ifBlank { stringResource(R.string.issue_parent_fallback) },
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** An indented, tappable task reference under a relationship row. */
@Composable
private fun RefRow(r: IssueSummaryRef, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 128.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { RefLabel(r) }
}

@Composable
private fun OpenButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, stringResource(R.string.issue_props_open_parent), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------- work mode ----------

private fun workModeIcon(mode: String): ImageVector = when (mode) {
    "planning" -> Icons.Outlined.Checklist
    "ask" -> Icons.AutoMirrored.Outlined.HelpOutline
    else -> Icons.Outlined.Build
}

@Composable
internal fun workModeLabel(mode: String): String = when (mode) {
    "planning" -> stringResource(R.string.issue_work_mode_plan)
    "ask" -> stringResource(R.string.issue_work_mode_ask)
    "standard" -> stringResource(R.string.issue_work_mode_auto)
    else -> mode.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

@Composable
private fun workModeHint(mode: String): String = when (mode) {
    "planning" -> stringResource(R.string.issue_work_mode_plan_hint)
    "ask" -> stringResource(R.string.issue_work_mode_ask_hint)
    else -> stringResource(R.string.issue_work_mode_auto_hint)
}

@Composable
private fun IssueWorkModeSheet(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    IssueSheet(stringResource(R.string.issue_props_work_mode), onDismiss) {
        WORK_MODES.forEach { m ->
            IssueSheetRow(m == current, { onPick(m); onDismiss() },
                { Icon(workModeIcon(m), null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                workModeLabel(m), workModeHint(m))
        }
    }
}

// ---------- labels ----------

@Composable
private fun IssueLabelsSheet(current: List<String>, onSet: (List<String>, List<Label>) -> Unit, onDismiss: () -> Unit) {
    var all by remember { mutableStateOf<List<Label>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var q by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        runCatching { Repo.labels(AppState.cid) }
            .onSuccess { all = it.sortedBy { l -> l.name.lowercase() } }
            .onFailure { error = errorMessage(it) }
    }
    IssueSheet(stringResource(R.string.issue_props_labels), onDismiss) {
        val list = all
        when {
            error != null -> SheetNote(error!!)
            list == null -> IssueTabLoading()
            list.isEmpty() -> SheetNote(stringResource(R.string.issue_props_labels_empty))
            else -> {
                if (list.size > 8) SheetSearch(q, { q = it }, stringResource(R.string.issue_props_filter_labels))
                LazyColumn(Modifier.heightIn(max = 520.dp)) {
                    items(list.filter { q.isBlank() || it.name.contains(q.trim(), true) }, key = { it.id }) { l ->
                        val on = l.id in current
                        CheckRow(on, { onSet(toggleId(current, l.id), list) }) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(parseIssueColor(l.color) ?: MaterialTheme.colorScheme.outline))
                            Spacer(Modifier.width(12.dp))
                            Text(l.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}

// ---------- parent ----------

@Composable
private fun IssueParentSheet(issue: Issue, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val all by IssueStore.issues.collectAsState()
    var q by remember { mutableStateOf("") }
    val options = remember(all, q, issue.id) { parentCandidates(all.values, issue, q) }
    IssueSheet(stringResource(R.string.issue_props_parent), onDismiss) {
        SheetSearch(q, { q = it }, stringResource(R.string.issue_props_search_tasks))
        LazyColumn(Modifier.heightIn(max = 520.dp)) {
            if (q.isBlank()) item(key = "none") {
                IssueSheetRow(issue.parentId == null, { onPick(null); onDismiss() },
                    { Icon(Icons.Outlined.AccountTree, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    stringResource(R.string.issue_props_no_parent))
            }
            if (all.isEmpty()) item { IssueTabLoading() }
            else if (options.isEmpty()) item { SheetNote(stringResource(R.string.issue_props_no_matches)) }
            items(options, key = { it.id }) { c ->
                IssueSheetRow(c.id == issue.parentId, { onPick(c.id); onDismiss() }, { StatusIcon(c.status, 16.dp) },
                    listOfNotNull(c.identifier, c.title).joinToString(" "), c.project?.name ?: AppState.project(c.projectId)?.name)
            }
        }
    }
}

// ---------- blocked by ----------

@Composable
private fun IssueBlockersSheet(issue: Issue, onSet: (List<String>) -> Unit, onDismiss: () -> Unit) {
    val all by IssueStore.issues.collectAsState()
    var q by remember { mutableStateOf("") }
    val current = issue.blockedBy.orEmpty().map { it.id }
    // Current blockers sort first as of when the sheet opened, so ticking a task doesn't make the list jump.
    val opened = remember(issue.id) { issue.blockedBy }
    val options = remember(all, q, issue.id) { blockerCandidates(all.values, issue.copy(blockedBy = opened), q) }
    IssueSheet(stringResource(R.string.issue_blocked_by), onDismiss) {
        SheetSearch(q, { q = it }, stringResource(R.string.issue_props_search_tasks))
        LazyColumn(Modifier.heightIn(max = 520.dp)) {
            if (q.isBlank()) item(key = "none") {
                IssueSheetRow(current.isEmpty(), { if (current.isNotEmpty()) onSet(emptyList()); onDismiss() },
                    { Icon(Icons.Outlined.Block, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    stringResource(R.string.issue_props_no_blockers))
            }
            if (all.isEmpty()) item { IssueTabLoading() }
            else if (options.isEmpty()) item { SheetNote(stringResource(R.string.issue_props_no_matches)) }
            items(options, key = { it.id }) { c ->
                CheckRow(c.id in current, { onSet(toggleId(current, c.id)) }) {
                    StatusIcon(c.status, 16.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(listOfNotNull(c.identifier, c.title).joinToString(" "), style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ---------- sheet bits ----------

@Composable
private fun SheetSearch(value: String, onChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true,
        placeholder = { Text(placeholder) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun SheetNote(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
}

@Composable
private fun CheckRow(checked: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(start = 8.dp, end = 20.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { content() }
    }
}
