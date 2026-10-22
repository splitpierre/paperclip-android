package com.bu.paperclip.ui.screens

import com.bu.paperclip.util.catching

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bu.paperclip.ui.theme.Mono
import com.bu.paperclip.data.AppState
import com.bu.paperclip.data.Approval
import com.bu.paperclip.data.ApprovalComment
import com.bu.paperclip.data.Issue
import com.bu.paperclip.data.Repo
import com.bu.paperclip.ui.Nav
import com.bu.paperclip.ui.components.AgentAvatar
import com.bu.paperclip.ui.components.Avatar
import com.bu.paperclip.ui.components.Card
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import com.bu.paperclip.ui.components.Divider
import com.bu.paperclip.ui.components.EmptyState
import com.bu.paperclip.ui.components.IssueRow
import com.bu.paperclip.ui.components.KeyValue
import com.bu.paperclip.ui.components.LiveRefresh
import com.bu.paperclip.ui.components.LoadContent
import com.bu.paperclip.ui.components.Markdown
import com.bu.paperclip.ui.components.Pill
import com.bu.paperclip.ui.components.SectionHeader
import com.bu.paperclip.ui.components.errorMessage
import com.bu.paperclip.ui.components.rememberLoader
import com.bu.paperclip.ui.components.rememberMinuteTick
import com.bu.paperclip.ui.components.statusLabel
import com.bu.paperclip.util.absoluteTime
import com.bu.paperclip.util.relativeTime
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal fun approvalTypeLabel(type: String): String = when (type) {
    "hire_agent" -> "Hire agent"
    "budget_override_required" -> "Budget override"
    "request_board_approval" -> "Board approval"
    "approve_ceo_strategy" -> "Strategy"
    else -> type.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

private fun approvalTitle(a: Approval): String {
    val p = a.payload ?: return approvalTypeLabel(a.type)
    for (k in listOf("title", "summary", "name", "agentName", "reason")) {
        val v = (p[k] as? JsonPrimitive)?.contentOrNull
        if (!v.isNullOrBlank()) return v.lineSequence().first().take(140)
    }
    (p["agent"] as? JsonObject)?.let { o -> (o["name"] as? JsonPrimitive)?.contentOrNull?.let { return "Hire $it" } }
    return approvalTypeLabel(a.type)
}

private fun approvalRequester(a: Approval): String = when {
    a.requestedByAgentId != null -> AppState.agentName(a.requestedByAgentId)
    a.requestedByUserId != null -> AppState.userName(a.requestedByUserId)
    else -> "Paperclip"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalsScreen(nav: Nav) {
    val company by AppState.company.collectAsState()
    val cid = company?.id ?: ""
    var seg by rememberSaveable { mutableIntStateOf(0) }
    val loader = rememberLoader(cid, seg) { Repo.approvals(cid, if (seg == 0) "pending" else null) }
    LiveRefresh(filter = { it.type == "activity.logged" && it.s("entityType") == "approval" }) { loader.refresh() }

    Scaffold(topBar = { TopAppBar(title = { Text("Approvals") }, navigationIcon = { BackIcon { nav.back() } }) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                listOf("Pending", "All").forEachIndexed { i, label ->
                    SegmentedButton(
                        selected = seg == i,
                        onClick = { seg = i },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                    ) { Text(label) }
                }
            }
            LoadContent(loader) { list ->
                val sorted = list.sortedByDescending { it.updatedAt ?: it.createdAt }
                LazyColumn(Modifier.fillMaxSize()) {
                    if (sorted.isEmpty()) {
                        item { EmptyState(if (seg == 0) "No approvals waiting on you." else "No approvals yet.", Icons.Outlined.Gavel) }
                    }
                    items(sorted, key = { it.id }) { a -> ApprovalRow(a) { nav.approval(a.id) } }
                }
            }
        }
    }
}

@Composable
private fun ApprovalRow(a: Approval, onClick: () -> Unit) {
    rememberMinuteTick()
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (a.requestedByAgentId != null) AgentAvatar(a.requestedByAgentId, 34.dp) else Avatar(approvalRequester(a), null, 34.dp, isAgent = false)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(approvalTypeLabel(a.type), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Pill(statusLabel(a.status), adminStatusColor(a.status))
                Spacer(Modifier.weight(1f))
                Text(relativeTime(a.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(approvalTitle(a), style = MaterialTheme.typography.bodyLarge, maxLines = 2)
            Text("Requested by ${approvalRequester(a)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private data class ApprovalBundle(val approval: Approval, val comments: List<ApprovalComment>, val issues: List<Issue>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalDetailScreen(nav: Nav, id: String) {
    val scope = rememberCoroutineScope()
    val loader = rememberLoader(id) {
        coroutineScope {
            val a = async { Repo.approval(id) }
            val c = async { catching { Repo.approvalComments(id) }.getOrDefault(emptyList()) }
            val i = async { catching { Repo.approvalIssues(id) }.getOrDefault(emptyList()) }
            ApprovalBundle(a.await(), c.await(), i.await())
        }
    }
    LiveRefresh(filter = { it.type == "activity.logged" && it.s("entityId") == id }) { loader.refresh() }

    var pendingAction by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Approval") }, navigationIcon = { BackIcon { nav.back() } }) },
        bottomBar = {
            val a = loader.value?.approval
            if (a != null && (a.status == "pending" || a.status == "revision_requested")) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 2.dp) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(onClick = { pendingAction = "request-revision"; note = "" }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Revise") }
                        OutlinedButton(
                            onClick = { pendingAction = "reject"; note = "" },
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) { Text("Reject") }
                        Button(onClick = { pendingAction = "approve"; note = "" }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Approve") }
                    }
                }
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).imePadding()) {
            LoadContent(loader, Modifier.weight(1f)) { b ->
                val a = b.approval
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(approvalTypeLabel(a.type), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(8.dp))
                                Pill(statusLabel(a.status), adminStatusColor(a.status))
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(approvalTitle(a), style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (a.requestedByAgentId != null) AgentAvatar(a.requestedByAgentId, 24.dp) else Avatar(approvalRequester(a), null, 24.dp, isAgent = false)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "${approvalRequester(a)} · ${absoluteTime(a.createdAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.let { m -> if (a.requestedByAgentId != null) m.clickable { nav.agent(a.requestedByAgentId) } else m },
                                )
                            }
                        }
                    }
                    if (!a.decisionNote.isNullOrBlank() || a.decidedAt != null) {
                        item {
                            Card(Modifier.padding(horizontal = 16.dp)) {
                                Column {
                                    Text("Decision", style = MaterialTheme.typography.labelLarge)
                                    if (a.decidedAt != null) Text(absoluteTime(a.decidedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (!a.decisionNote.isNullOrBlank()) { Spacer(Modifier.height(6.dp)); Markdown(a.decisionNote) }
                                }
                            }
                        }
                    }
                    val payload = a.payload
                    if (payload != null && payload.isNotEmpty()) {
                        item { SectionHeader("Details") }
                        item { Card(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) { Column { ApprovalPayload(payload) } } }
                    }
                    if (b.issues.isNotEmpty()) {
                        item { SectionHeader("Linked tasks") }
                        items(b.issues, key = { "i-" + it.id }) { issue -> IssueRow(issue, onClick = { nav.issue(issue.identifier ?: issue.id) }) }
                    }
                    item { SectionHeader("Comments") }
                    if (b.comments.isEmpty()) {
                        item {
                            Text(
                                "No comments yet.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                    items(b.comments, key = { "c-" + it.id }) { c -> ApprovalCommentItem(c) }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
            if (loader.value != null) {
                Divider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
                    val canSend = draft.isNotBlank() && !sending
                    TextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = { Text("Add a comment…") },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        maxLines = 5,
                        shape = RoundedCornerShape(24.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        enabled = canSend,
                        modifier = Modifier.padding(bottom = 2.dp).size(44.dp).background(
                            if (canSend) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHigh,
                            RoundedCornerShape(50),
                        ),
                        onClick = {
                            val text = draft.trim()
                            sending = true
                            scope.launch {
                                try {
                                    val c = Repo.addApprovalComment(id, text)
                                    loader.value?.let { cur -> loader.set(cur.copy(comments = cur.comments + c)) }
                                    draft = ""
                                } catch (t: Throwable) {
                                    AppState.toast(errorMessage(t))
                                } finally {
                                    sending = false
                                }
                            }
                        },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send, "Send",
                            tint = if (canSend) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }

    pendingAction?.let { action ->
        val title = when (action) {
            "approve" -> "Approve?"
            "reject" -> "Reject?"
            else -> "Request revision?"
        }
        AlertDialog(
            onDismissRequest = { if (!busy) pendingAction = null },
            title = { Text(title) },
            text = {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(if (action == "request-revision") "What should change?" else "Note (optional)") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                val updated = Repo.decideApproval(id, action, note)
                                loader.value?.let { cur -> loader.set(cur.copy(approval = updated)) }
                                AppState.refreshBadges()
                                AppState.toast(
                                    when (action) {
                                        "approve" -> "Approved"
                                        "reject" -> "Rejected"
                                        else -> "Revision requested"
                                    },
                                )
                                pendingAction = null
                            } catch (t: Throwable) {
                                AppState.toast(errorMessage(t))
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) {
                    Text(
                        when (action) {
                            "approve" -> "Approve"
                            "reject" -> "Reject"
                            else -> "Request revision"
                        },
                    )
                }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { pendingAction = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ApprovalCommentItem(c: ApprovalComment) {
    val name = if (c.authorAgentId != null) AppState.agentName(c.authorAgentId) else AppState.userName(c.authorUserId)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (c.authorAgentId != null) AgentAvatar(c.authorAgentId, 28.dp) else Avatar(name, null, 28.dp, isAgent = false)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(8.dp))
                Text(relativeTime(c.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Markdown(c.body)
        }
    }
}

private val approvalPrettyJson = Json { prettyPrint = true }

@Composable
private fun ApprovalPayload(payload: JsonObject) {
    payload.forEach { (key, value) ->
        when (value) {
            is JsonNull -> {}
            is JsonPrimitive -> {
                val s = value.contentOrNull ?: return@forEach
                if (s.isBlank()) return@forEach
                if (s.length > 80 || s.contains('\n')) {
                    Text(adminHumanize(key), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                    Markdown(s)
                } else {
                    val shown = when {
                        key.endsWith("AgentId") || key == "agentId" -> AppState.agent(s)?.name ?: s
                        key.endsWith("Cents") -> com.bu.paperclip.util.formatCents(s.toLongOrNull())
                        else -> s
                    }
                    KeyValue(adminHumanize(key), shown)
                }
            }
            is JsonObject -> {
                val flat = value.values.all { it is JsonPrimitive || it is JsonNull }
                Text(adminHumanize(key), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                if (flat) {
                    ApprovalPayload(value)
                } else {
                    ApprovalJsonBlock(value)
                }
            }
            is JsonArray -> {
                if (value.isEmpty()) return@forEach
                Text(adminHumanize(key), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                if (value.all { it is JsonPrimitive }) {
                    value.forEach { Text("• " + ((it as JsonPrimitive).contentOrNull ?: ""), style = MaterialTheme.typography.bodyMedium) }
                } else {
                    ApprovalJsonBlock(value)
                }
            }
        }
    }
}

@Composable
private fun ApprovalJsonBlock(el: JsonElement) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            approvalPrettyJson.encodeToString(JsonElement.serializer(), el),
            fontFamily = Mono,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(10.dp),
        )
    }
}
