package com.bu.paperclip.ui.components

import com.bu.paperclip.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bu.paperclip.data.AppState
import com.bu.paperclip.data.HeartbeatRun
import com.bu.paperclip.data.Issue
import com.bu.paperclip.data.LiveRun
import com.bu.paperclip.ui.theme.LocalStatusColors
import com.bu.paperclip.util.durationLabel
import com.bu.paperclip.util.relativeTime

/** Standard issue row: status, identifier, title, assignee, priority, recency, live/unread markers. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun IssueRow(issue: Issue, onClick: () -> Unit, modifier: Modifier = Modifier, showProject: Boolean = true, onLongClick: (() -> Unit)? = null) {
    rememberMinuteTick()
    Row(
        modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusIcon(issue.status, 16.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    issue.ref,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (issue.activeRun != null && (issue.activeRun.status == "running" || issue.activeRun.status == "queued")) {
                    Spacer(Modifier.width(6.dp))
                    Pill(stringResource(R.string.component_live), LocalStatusColors.current.blue)
                }
                DraftPill(issue.id)
                if (showProject) {
                    val p = issue.project ?: AppState.project(issue.projectId)?.let { com.bu.paperclip.data.ProjectRef(it.id, it.name, it.color) }
                    if (p != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(p.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    relativeTime(issue.lastActivityAt ?: issue.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (issue.isUnreadForMe == true) {
                    Dot(MaterialTheme.colorScheme.tertiary, 7.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    issue.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (issue.isUnreadForMe == true) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PriorityIcon(issue.priority, 16.dp)
            when {
                issue.assigneeAgentId != null -> AgentAvatar(issue.assigneeAgentId, 22.dp)
                issue.assigneeUserId != null -> Avatar(AppState.userName(issue.assigneeUserId), null, 22.dp, isAgent = false)
            }
        }
    }
}

/** Live run row used on the dashboard and agent pages. */
@Composable
fun LiveRunRow(run: LiveRun, onClick: () -> Unit, issueLabel: String? = null) {
    rememberMinuteTick()
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AgentAvatar(run.agentId, 32.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(run.agentName ?: AppState.agentName(run.agentId), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(8.dp))
                Pill(statusLabel(run.status), runStatusColor(run.status))
                Spacer(Modifier.weight(1f))
                Text(durationLabel(run.startedAt ?: run.createdAt, run.finishedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val line = run.currentStatusMessage ?: run.lastAssistantSnippet ?: run.currentToolName?.let { stringResource(R.string.component_using_tool, it) }
            if (issueLabel != null) Text(issueLabel, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!line.isNullOrBlank()) Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun RunRow(run: HeartbeatRun, onClick: () -> Unit, showAgent: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showAgent) { AgentAvatar(run.agentId, 28.dp); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showAgent) { Text(AppState.agentName(run.agentId), style = MaterialTheme.typography.titleSmall); Spacer(Modifier.width(8.dp)) }
                Pill(statusLabel(run.status), runStatusColor(run.status))
                Spacer(Modifier.width(8.dp))
                Text(run.invocationSource ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(relativeTime(run.startedAt ?: run.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val sub = run.error ?: run.currentStatusMessage ?: run.lastAssistantSnippet
            if (!sub.isNullOrBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            else Text(durationLabel(run.startedAt, run.finishedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A small "Draft" pill when this task has an unsent reply saved on the device. */
@Composable
fun DraftPill(issueId: String) {
    val drafts by com.bu.paperclip.data.Drafts.commentIssueIds.collectAsState()
    if (issueId in drafts) {
        Spacer(Modifier.width(6.dp))
        Pill(stringResource(R.string.task_pill_draft), LocalStatusColors.current.gray)
    }
}
