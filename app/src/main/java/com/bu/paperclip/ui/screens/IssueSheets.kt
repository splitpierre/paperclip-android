package com.bu.paperclip.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import com.bu.paperclip.R
import com.bu.paperclip.data.AppState
import com.bu.paperclip.ui.components.AgentAvatar
import com.bu.paperclip.ui.components.Avatar
import com.bu.paperclip.ui.components.ISSUE_STATUSES
import com.bu.paperclip.ui.components.PRIORITIES
import com.bu.paperclip.ui.components.PriorityIcon
import com.bu.paperclip.ui.components.StatusIcon
import com.bu.paperclip.ui.components.agentStatusColor
import com.bu.paperclip.ui.components.statusLabel
import com.bu.paperclip.ui.components.taskPriorityLabel
import com.bu.paperclip.ui.components.taskStatusLabel
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

// ---------- JSON helpers for loosely-typed payloads ----------

internal fun JsonObject?.issueStr(key: String): String? = (this?.get(key) as? JsonPrimitive)?.contentOrNull
internal fun JsonObject?.issueBool(key: String): Boolean? = (this?.get(key) as? JsonPrimitive)?.booleanOrNull
internal fun JsonObject?.issueInt(key: String): Int? = (this?.get(key) as? JsonPrimitive)?.intOrNull
internal fun JsonObject?.issueObj(key: String): JsonObject? = this?.get(key) as? JsonObject
internal fun JsonObject?.issueArr(key: String): List<JsonElement> = (this?.get(key) as? JsonArray) ?: emptyList()
internal fun JsonObject?.issueObjs(key: String): List<JsonObject> = issueArr(key).mapNotNull { it as? JsonObject }
internal fun JsonObject?.issueStrs(key: String): List<String> = issueArr(key).mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

internal fun parseIssueColor(hex: String?): Color? = runCatching {
    val h = hex?.removePrefix("#") ?: return null
    if (h.length != 6) return null
    Color(0xFF000000 or h.toLong(16))
}.getOrNull()

// ---------- Generic selectable sheet row ----------

@Composable
internal fun IssueSheetRow(selected: Boolean, onClick: () -> Unit, leading: @Composable () -> Unit, title: String, subtitle: String? = null) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IssueSheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        modifier = Modifier.statusBarsPadding().padding(top = 12.dp),
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.navigationBarsPadding()) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            content()
        }
    }
}

@Composable
internal fun IssueStatusSheet(current: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    IssueSheet(stringResource(R.string.task_field_status), onDismiss) {
        ISSUE_STATUSES.forEach { s ->
            IssueSheetRow(s == current, { onPick(s); onDismiss() }, { StatusIcon(s, 18.dp) }, taskStatusLabel(s))
        }
    }
}

@Composable
internal fun IssuePrioritySheet(current: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    IssueSheet(stringResource(R.string.task_field_priority), onDismiss) {
        PRIORITIES.forEach { p ->
            IssueSheetRow(p == current, { onPick(p); onDismiss() }, { PriorityIcon(p, 20.dp) }, taskPriorityLabel(p))
        }
    }
}

/** Assignee choice: agent id, the signed-in user, another board user, or nobody. */
sealed class IssueAssignee {
    data class AgentA(val id: String) : IssueAssignee()
    data class UserA(val id: String) : IssueAssignee()
    data object Me : IssueAssignee()
    data object Nobody : IssueAssignee()
}

@Composable
internal fun IssueAssigneeSheet(
    currentAgentId: String?,
    currentUserId: String?,
    allowMe: Boolean = true,
    title: String = stringResource(R.string.task_field_assignee),
    unassignedSelected: Boolean = currentAgentId == null && currentUserId == null,
    /** Also list the company's other board users, as the web panel does. */
    showUsers: Boolean = false,
    onPick: (IssueAssignee) -> Unit,
    onDismiss: () -> Unit,
) {
    val agents by AppState.agents.collectAsState()
    val me by AppState.me.collectAsState()
    val users by AppState.users.collectAsState()
    var q by remember { mutableStateOf("") }
    IssueSheet(title, onDismiss) {
        if (agents.size > 8) {
            OutlinedTextField(
                value = q, onValueChange = { q = it }, singleLine = true,
                placeholder = { Text(stringResource(R.string.issue_sheet_filter_agents)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        LazyColumn(Modifier.heightIn(max = 520.dp)) {
            if (q.isBlank()) {
                item {
                    IssueSheetRow(unassignedSelected, { onPick(IssueAssignee.Nobody); onDismiss() },
                        { Icon(Icons.Outlined.PersonOff, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }, stringResource(R.string.common_unassigned))
                }
                if (allowMe && me != null) item {
                    IssueSheetRow(currentUserId != null && currentUserId == me?.id, { onPick(IssueAssignee.Me); onDismiss() },
                        { Avatar(me?.name ?: stringResource(R.string.task_assignee_me), me?.image, 28.dp, isAgent = false) }, stringResource(R.string.task_assignee_me), me?.email)
                }
            }
            val filtered = agents.filter { q.isBlank() || it.name.contains(q, true) || (it.title ?: "").contains(q, true) }
            items(filtered, key = { it.id }) { a ->
                IssueSheetRow(
                    currentAgentId == a.id,
                    { onPick(IssueAssignee.AgentA(a.id)); onDismiss() },
                    { AgentAvatar(a.id, 28.dp) },
                    a.name,
                    listOfNotNull(a.title, statusLabel(a.status).takeIf { a.status != "idle" && a.status != "active" }).joinToString(" · "),
                )
            }
            if (showUsers) {
                val others = users.entries
                    .filter { (id, u) -> id != me?.id && (q.isBlank() || (u.name ?: "").contains(q, true) || (u.email ?: "").contains(q, true)) }
                    .sortedBy { (_, u) -> (u.name ?: u.email ?: "").lowercase() }
                items(others, key = { "u-" + it.key }) { (id, u) ->
                    val name = u.name?.takeIf { it.isNotBlank() } ?: u.email ?: stringResource(R.string.common_user)
                    IssueSheetRow(currentUserId == id, { onPick(IssueAssignee.UserA(id)); onDismiss() },
                        { Avatar(name, u.image, 28.dp, isAgent = false) }, name, u.email?.takeIf { it != name })
                }
            }
        }
    }
}

@Composable
internal fun IssueProjectSheet(currentId: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val projects by AppState.projects.collectAsState()
    IssueSheet(stringResource(R.string.task_field_project), onDismiss) {
        LazyColumn(Modifier.heightIn(max = 520.dp)) {
            item {
                IssueSheetRow(currentId == null, { onPick(null); onDismiss() },
                    { Icon(Icons.Outlined.FolderOff, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }, stringResource(R.string.task_no_project))
            }
            items(projects.filter { it.archivedAt == null }, key = { it.id }) { p ->
                IssueSheetRow(currentId == p.id, { onPick(p.id); onDismiss() }, {
                    androidx.compose.foundation.layout.Box(
                        Modifier.size(14.dp).clip(CircleShape).background(parseIssueColor(p.color) ?: MaterialTheme.colorScheme.outline),
                    )
                }, p.name, statusLabel(p.status))
            }
        }
    }
}

@Composable
internal fun issueAgentDot(id: String?): Color = agentStatusColor(AppState.agent(id)?.status ?: "idle")
