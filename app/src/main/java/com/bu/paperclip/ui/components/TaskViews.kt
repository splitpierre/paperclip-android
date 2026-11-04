package com.bu.paperclip.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import com.bu.paperclip.R
import com.bu.paperclip.data.AppState
import com.bu.paperclip.data.Issue
import com.bu.paperclip.data.Rollup
import com.bu.paperclip.data.TaskGraph
import com.bu.paperclip.data.TaskNode
import com.bu.paperclip.data.isClosed
import com.bu.paperclip.ui.theme.LocalStatusColors
import com.bu.paperclip.util.L10n

@StringRes
fun taskStatusRes(status: String): Int? = if (status in ISSUE_STATUSES) statusLabelRes(status) else null

@StringRes
fun taskPriorityRes(priority: String): Int? = when (priority) {
    "critical" -> R.string.task_priority_critical
    "high" -> R.string.task_priority_high
    "medium" -> R.string.task_priority_medium
    "low" -> R.string.task_priority_low
    else -> null
}

/** Task status label; unknown values fall back to [statusLabel]. Usable outside Compose. */
fun taskStatusLabel(status: String): String = statusLabel(status)

/** Task priority label; unknown values are capitalised as-is. Usable outside Compose. */
fun taskPriorityLabel(priority: String): String = taskPriorityRes(priority)?.let { L10n.str(it) } ?: priority.replaceFirstChar { it.uppercase() }

/** Thin progress bar with "closed/total"; green when everything is closed. */
@Composable
fun RollupBar(r: Rollup, modifier: Modifier = Modifier, showLabel: Boolean = true) {
    if (r.total == 0) return
    val colors = LocalStatusColors.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        LinearProgressIndicator(
            progress = { r.progress },
            modifier = Modifier.width(64.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = if (r.open == 0) colors.green else colors.blue,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeCap = StrokeCap.Round,
            drawStopIndicator = {},
        )
        if (showLabel) {
            Spacer(Modifier.width(6.dp))
            Text("${r.closed}/${r.total}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (r.live > 0) { Spacer(Modifier.width(8.dp)); Pill(pluralStringResource(R.plurals.task_rollup_live, r.live, r.live), colors.blue) }
        if (r.blocked > 0) { Spacer(Modifier.width(6.dp)); Pill(pluralStringResource(R.plurals.task_rollup_blocked, r.blocked, r.blocked), colors.red) }
    }
}

/** Depth-first list of the nodes currently on screen given which ones are collapsed. */
fun flattenTree(forest: List<TaskNode>, collapsed: Set<String>, out: MutableList<TaskNode> = ArrayList()): List<TaskNode> {
    forest.forEach { n ->
        out.add(n)
        if (n.issue.id !in collapsed) flattenTree(n.children, collapsed, out)
    }
    return out
}

/** One row of a task tree: indent guides, chevron, status, identifier/title, progress rollup, assignee. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskTreeRow(
    node: TaskNode,
    expanded: Boolean,
    live: Boolean,
    blocked: Boolean,
    isNext: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    baseDepth: Int = 0,
) {
    val i = node.issue
    val depth = node.depth - baseDepth
    val hasChildren = node.children.isNotEmpty()
    val colors = LocalStatusColors.current
    val guide = MaterialTheme.colorScheme.outlineVariant
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
            .alpha(if (node.matched) 1f else 0.55f)
            .padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(8.dp))
        repeat(depth) {
            Box(Modifier.width(18.dp).height(44.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(1.dp).fillMaxHeight().background(guide))
            }
        }
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).clickable(enabled = hasChildren, onClick = onToggle), contentAlignment = Alignment.Center) {
            if (hasChildren) {
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(if (expanded) R.string.task_collapse else R.string.task_expand),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        StatusIcon(i.status, 15.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(i.ref, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (isNext) { Spacer(Modifier.width(6.dp)); Pill(stringResource(R.string.task_pill_next), colors.green) }
                if (live) { Spacer(Modifier.width(6.dp)); Pill(stringResource(R.string.task_pill_live), colors.blue) }
                if (blocked && i.status != "blocked") {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.Block, stringResource(R.string.task_waiting_on_blocker), tint = colors.red, modifier = Modifier.size(13.dp))
                }
            }
            Text(
                i.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (hasChildren && depth == 0) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (isClosed(i.status)) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            if (node.rollup.total > 0) {
                Spacer(Modifier.height(3.dp))
                RollupBar(node.rollup)
            }
        }
        Spacer(Modifier.width(8.dp))
        PriorityIcon(i.priority, 15.dp)
        Spacer(Modifier.width(6.dp))
        when {
            i.assigneeAgentId != null -> AgentAvatar(i.assigneeAgentId, 22.dp)
            i.assigneeUserId != null -> Avatar(AppState.userName(i.assigneeUserId), null, 22.dp, isAgent = false)
            else -> Spacer(Modifier.width(22.dp))
        }
    }
}

/** "Next up" + "In flight" summary for a scope (whole company or one subtree). Collapsible to one line. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NextUpCard(
    next: List<TaskGraph.Next>,
    inFlight: List<Issue>,
    onOpen: (Issue) -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.task_next_up),
) {
    if (next.isEmpty() && inFlight.isEmpty()) return
    val colors = LocalStatusColors.current
    var expanded by androidx.compose.runtime.saveable.rememberSaveable(title) { androidx.compose.runtime.mutableStateOf(true) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(bottom = if (expanded) 6.dp else 0.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = colors.green, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(8.dp))
                if (!expanded && next.isNotEmpty()) Text(
                    next.first().issue.let { (it.identifier?.let { id -> "$id " } ?: "") + it.title },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                ) else Spacer(Modifier.weight(1f))
                if (inFlight.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Outlined.Bolt, null, tint = colors.blue, modifier = Modifier.size(14.dp))
                    Text("${inFlight.size}", style = MaterialTheme.typography.labelMedium, color = colors.blue)
                }
                Spacer(Modifier.width(4.dp))
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(if (expanded) R.string.task_collapse else R.string.task_expand),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (!expanded) return@Column
            if (next.isEmpty()) Text(
                stringResource(R.string.task_next_up_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            )
            next.forEachIndexed { idx, n ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(n.issue) }.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${idx + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            (n.issue.identifier?.let { "$it  " } ?: "") + n.issue.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (n.reasons.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 2.dp)) {
                                n.reasons.forEach { r ->
                                    val (label, c) = when (r) {
                                        TaskGraph.Reason.Unblocks -> stringResource(R.string.task_next_unblocks, n.unblocks) to colors.green
                                        TaskGraph.Reason.Critical -> stringResource(R.string.task_priority_critical) to colors.red
                                        TaskGraph.Reason.High -> stringResource(R.string.task_priority_high) to colors.orange
                                        TaskGraph.Reason.Unassigned -> stringResource(R.string.common_unassigned) to colors.yellow
                                        TaskGraph.Reason.Backlog -> stringResource(R.string.status_backlog) to colors.gray
                                    }
                                    Pill(label, c)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    when {
                        n.issue.assigneeAgentId != null -> AgentAvatar(n.issue.assigneeAgentId, 20.dp)
                        n.issue.assigneeUserId != null -> Avatar(AppState.userName(n.issue.assigneeUserId), null, 20.dp, isAgent = false)
                    }
                }
            }
            if (inFlight.isNotEmpty()) {
                Divider()
                Text(
                    stringResource(R.string.task_in_flight),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 2.dp),
                )
                inFlight.take(3).forEach { i ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(i) }.padding(horizontal = 14.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusIcon(i.status, 12.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            (i.identifier?.let { "$it  " } ?: "") + i.title,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (i.assigneeAgentId != null) { Spacer(Modifier.width(6.dp)); AgentAvatar(i.assigneeAgentId, 18.dp) }
                    }
                }
                if (inFlight.size > 3) Text(
                    pluralStringResource(R.plurals.task_more_count, inFlight.size - 3, inFlight.size - 3),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** Compact kanban card. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BoardCard(issue: Issue, parent: Issue?, rollup: Rollup, live: Boolean, blocked: Boolean, onOpen: () -> Unit, onLongPress: () -> Unit) {
    val colors = LocalStatusColors.current
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).combinedClickable(onClick = onOpen, onLongClick = onLongPress),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PriorityIcon(issue.priority, 14.dp)
                Spacer(Modifier.width(4.dp))
                Text(issue.ref, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (live) { Spacer(Modifier.width(6.dp)); Pill(stringResource(R.string.task_pill_live), colors.blue) }
                if (blocked && issue.status != "blocked") { Spacer(Modifier.width(6.dp)); Icon(Icons.Default.Block, stringResource(R.string.task_waiting_on_blocker), tint = colors.red, modifier = Modifier.size(13.dp)) }
                Spacer(Modifier.weight(1f))
                when {
                    issue.assigneeAgentId != null -> AgentAvatar(issue.assigneeAgentId, 20.dp)
                    issue.assigneeUserId != null -> Avatar(AppState.userName(issue.assigneeUserId), null, 20.dp, isAgent = false)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(issue.title, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (parent != null) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AccountTree, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        (parent.identifier?.let { "$it · " } ?: "") + parent.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (rollup.total > 0) { Spacer(Modifier.height(6.dp)); RollupBar(rollup) }
        }
    }
}
