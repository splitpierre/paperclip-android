package com.bu.paperclip.ui.screens

import com.bu.paperclip.util.catching

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.bu.paperclip.ui.components.PropertyChip
import com.bu.paperclip.data.AppState
import com.bu.paperclip.data.Issue
import com.bu.paperclip.data.Repo
import com.bu.paperclip.data.jsonOf
import com.bu.paperclip.ui.Nav
import com.bu.paperclip.ui.components.AgentAvatar
import com.bu.paperclip.ui.components.Avatar
import com.bu.paperclip.ui.components.PriorityIcon
import com.bu.paperclip.ui.components.StatusIcon
import com.bu.paperclip.ui.components.errorMessage
import com.bu.paperclip.ui.components.statusLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewIssueScreen(nav: Nav, parentId: String?, projectId: String?, assigneeAgentId: String?) {
    val scope = rememberCoroutineScope()
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("todo") }
    var priority by rememberSaveable { mutableStateOf("medium") }
    var agentId by rememberSaveable { mutableStateOf(assigneeAgentId) }
    var userId by rememberSaveable { mutableStateOf<String?>(null) }
    var project by rememberSaveable { mutableStateOf(projectId) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var parent by remember { mutableStateOf<Issue?>(null) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(parentId) {
        if (parentId != null) catching { parent = Repo.issue(parentId) }.onSuccess {
            if (project == null) project = parent?.projectId
        }
    }
    LaunchedEffect(Unit) { catching { focus.requestFocus() } }

    fun create() {
        if (title.isBlank() || saving) return
        saving = true; error = null
        scope.launch {
            try {
                val body = jsonOf(
                    "title" to title.trim(),
                    "description" to description.trim().ifEmpty { null },
                    "status" to status,
                    "priority" to priority,
                    "assigneeAgentId" to agentId,
                    "assigneeUserId" to userId,
                    "projectId" to project,
                    "parentId" to (parent?.id ?: parentId),
                ).let { o -> kotlinx.serialization.json.JsonObject(o.filterValues { v -> v !is kotlinx.serialization.json.JsonNull }) }
                val created = Repo.createIssue(AppState.cid, body)
                AppState.toast("Created ${created.identifier ?: "task"}")
                nav.back()
                nav.issue(created.identifier ?: created.id)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                error = errorMessage(t)
            } finally {
                saving = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackIcon { nav.back() } },
                title = { Text(if (parentId != null) "New sub-task" else "New task") },
                actions = {
                    if (saving) CircularProgressIndicator(Modifier.padding(end = 16.dp).size(22.dp), strokeWidth = 2.dp)
                    else TextButton(onClick = { create() }, enabled = title.isNotBlank()) { Text("Create") }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            parent?.let { p ->
                Text("Sub-task of ${p.identifier ?: ""} · ${p.title}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PropertyChip(onClick = { sheet = "status" }, label = { Text(statusLabel(status)) }, leadingIcon = { StatusIcon(status, 14.dp) })
                PropertyChip(onClick = { sheet = "priority" }, label = { Text(priority.replaceFirstChar { it.uppercase() }) }, leadingIcon = { PriorityIcon(priority, 16.dp) })
                PropertyChip(
                    onClick = { sheet = "assignee" },
                    label = { Text(agentId?.let { AppState.agentName(it) } ?: userId?.let { AppState.userName(it) } ?: "Unassigned") },
                    leadingIcon = {
                        when {
                            agentId != null -> AgentAvatar(agentId, 18.dp)
                            userId != null -> Avatar(AppState.userName(userId), null, 18.dp, isAgent = false)
                            else -> Icon(Icons.Outlined.PersonOutline, null, Modifier.size(18.dp))
                        }
                    },
                )
                PropertyChip(
                    onClick = { sheet = "project" },
                    label = { Text(AppState.project(project)?.name ?: "No project") },
                    leadingIcon = { Icon(Icons.Outlined.FolderOpen, null, Modifier.size(18.dp)) },
                )
            }
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (markdown)") },
                minLines = 8,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
            )
            if (status == "in_progress" && agentId == null && userId == null) {
                Text("In progress tasks need an assignee.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(40.dp))
        }
    }

    when (sheet) {
        "status" -> IssueStatusSheet(status, onPick = { status = it }, onDismiss = { sheet = null })
        "priority" -> IssuePrioritySheet(priority, onPick = { priority = it }, onDismiss = { sheet = null })
        "assignee" -> IssueAssigneeSheet(agentId, userId, onPick = { a ->
            when (a) {
                is IssueAssignee.AgentA -> { agentId = a.id; userId = null }
                IssueAssignee.Me -> { agentId = null; userId = AppState.me.value?.id }
                IssueAssignee.Nobody -> { agentId = null; userId = null }
            }
        }, onDismiss = { sheet = null })
        "project" -> IssueProjectSheet(project, onPick = { project = it }, onDismiss = { sheet = null })
    }
}
