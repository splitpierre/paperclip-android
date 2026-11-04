package com.bu.paperclip.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.bu.paperclip.net.ServerFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bu.paperclip.R
import com.bu.paperclip.util.L10n
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
import com.bu.paperclip.ui.components.taskPriorityLabel
import com.bu.paperclip.ui.components.taskStatusLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewIssueScreen(nav: Nav, parentId: String?, projectId: String?, assigneeAgentId: String?) {
    val ctx = LocalContext.current
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
    val files = rememberSaveable(saver = pickedFilesSaver) { mutableStateListOf() }
    val uploadState = remember { mutableStateMapOf<Uri, NewIssueUpload>() }
    var uploadIndex by remember { mutableStateOf(0) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        scope.launch {
            val picked = withContext(Dispatchers.IO) { uris.map { pickedFile(ctx, it) } }
            picked.forEach { f -> if (files.none { it.uri == f.uri }) files.add(f) }
        }
    }
    // Leaving while files upload would cancel them halfway; the screen moves on by itself when done.
    BackHandler(enabled = saving) {}

    LaunchedEffect(parentId) {
        if (parentId != null) runCatching { parent = Repo.issue(parentId) }.onSuccess {
            if (project == null) project = parent?.projectId
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

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
                // The task exists from here on: a failed upload is reported, never undoes it.
                val failed = mutableListOf<Pair<String, String>>()
                files.toList().forEachIndexed { n, f ->
                    uploadIndex = n + 1
                    uploadState[f.uri] = NewIssueUpload.Uploading
                    try {
                        uploadPickedFile(ctx, created.id, f)
                        uploadState[f.uri] = NewIssueUpload.Done
                    } catch (c: CancellationException) {
                        throw c
                    } catch (t: Throwable) {
                        uploadState[f.uri] = NewIssueUpload.Failed
                        failed += f.name to errorMessage(t)
                    }
                }
                AppState.toast(uploadFailureMessage(created.identifier ?: L10n.str(R.string.new_issue_task_fallback), failed, files.size), sticky = failed.isNotEmpty())
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
                navigationIcon = { BackIcon { if (!saving) nav.back() } },
                title = { Text(stringResource(if (parentId != null) R.string.new_issue_title_sub_task else R.string.new_issue_title)) },
                actions = {
                    if (saving) CircularProgressIndicator(Modifier.padding(end = 16.dp).size(22.dp), strokeWidth = 2.dp)
                    else TextButton(onClick = { create() }, enabled = title.isNotBlank()) { Text(stringResource(R.string.new_issue_create)) }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            parent?.let { p ->
                Text(stringResource(R.string.new_issue_sub_task_of, p.identifier ?: "", p.title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.new_issue_field_title)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PropertyChip(onClick = { sheet = "status" }, label = { Text(taskStatusLabel(status)) }, leadingIcon = { StatusIcon(status, 14.dp) })
                PropertyChip(onClick = { sheet = "priority" }, label = { Text(taskPriorityLabel(priority)) }, leadingIcon = { PriorityIcon(priority, 16.dp) })
                PropertyChip(
                    onClick = { sheet = "assignee" },
                    label = { Text(agentId?.let { AppState.agentName(it) } ?: userId?.let { AppState.userName(it) } ?: stringResource(R.string.common_unassigned)) },
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
                    label = { Text(AppState.project(project)?.name ?: stringResource(R.string.task_no_project)) },
                    leadingIcon = { Icon(Icons.Outlined.FolderOpen, null, Modifier.size(18.dp)) },
                )
            }
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.new_issue_field_description)) },
                minLines = 8,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
            )
            NewIssueFiles(
                files = files,
                state = uploadState,
                enabled = !saving,
                onAdd = { picker.launch(arrayOf("*/*")) },
                onRemove = { f -> files.remove(f) },
            )
            if (saving && files.isNotEmpty() && uploadIndex > 0) {
                Text(
                    stringResource(R.string.new_issue_uploading, uploadIndex, files.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (status == "in_progress" && agentId == null && userId == null) {
                Text(stringResource(R.string.new_issue_needs_assignee), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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

private enum class NewIssueUpload { Uploading, Done, Failed }

/** Picks survive rotation; the document permission belongs to the activity, which outlives it. */
private val pickedFilesSaver = listSaver<MutableList<PickedFile>, String>(
    save = { list -> list.flatMap { listOf(it.uri.toString(), it.name, it.size?.toString().orEmpty(), it.mime) } },
    restore = { flat ->
        mutableStateListOf<PickedFile>().apply {
            flat.chunked(4).forEach { (uri, name, size, mime) -> add(PickedFile(Uri.parse(uri), name, size.toLongOrNull(), mime)) }
        }
    },
)

@Composable
private fun NewIssueFiles(
    files: List<PickedFile>,
    state: Map<Uri, NewIssueUpload>,
    enabled: Boolean,
    onAdd: () -> Unit,
    onRemove: (PickedFile) -> Unit,
) {
    Column {
        files.forEach { f ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (ServerFiles.isImage(f.mime)) {
                    AsyncImage(
                        model = f.uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Outlined.InsertDriveFile, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp).padding(8.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    f.size?.let {
                        Text(issueSize(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                when (state[f.uri]) {
                    NewIssueUpload.Uploading -> CircularProgressIndicator(Modifier.padding(12.dp).size(22.dp), strokeWidth = 2.dp)
                    NewIssueUpload.Done -> Icon(Icons.Outlined.CheckCircle, stringResource(R.string.new_issue_uploaded), Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.primary)
                    NewIssueUpload.Failed -> Icon(Icons.Outlined.ErrorOutline, stringResource(R.string.new_issue_upload_failed), Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.error)
                    null -> IconButton(onClick = { onRemove(f) }, enabled = enabled) { Icon(Icons.Default.Close, stringResource(R.string.new_issue_remove_file, f.name)) }
                }
            }
        }
        TextButton(onClick = onAdd, enabled = enabled) {
            Icon(Icons.Outlined.AttachFile, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.new_issue_attach_files))
        }
    }
}
