package com.bu.paperclip.ui.screens

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.bu.paperclip.data.Agent
import com.bu.paperclip.data.AppState
import com.bu.paperclip.data.Repo
import com.bu.paperclip.data.jsonOf
import com.bu.paperclip.ui.Nav
import com.bu.paperclip.ui.components.AgentAvatar
import com.bu.paperclip.ui.components.Avatar
import com.bu.paperclip.ui.components.PropertyChip
import com.bu.paperclip.ui.components.errorMessage
import com.bu.paperclip.util.Dictation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private const val QUICK_ASSIGNEE_KEY = "quickTask.assigneeAgentId"
private const val MAX_ATTACHMENT_BYTES = 100L * 1024 * 1024

private data class QuickFile(val uri: Uri, val name: String, val mime: String, val size: Long)

/** Head of the org chart: the CEO role, or else the only agent that reports to nobody. */
internal fun defaultQuickAssignee(agents: List<Agent>): String? =
    agents.firstOrNull { it.role == "ceo" }?.id ?: agents.filter { it.reportsTo == null }.singleOrNull()?.id

internal fun appendSpoken(text: String, said: String): String =
    if (text.isBlank()) said.replaceFirstChar { it.uppercase() } else text.trimEnd() + " " + said

/** First line of the request, cut at a word near 80 characters. */
internal fun quickTitle(text: String): String {
    val line = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
    if (line.length <= 80) return line
    val cut = line.take(80).substringBeforeLast(' ').ifBlank { line.take(80) }
    return "$cut…"
}

private fun quickFile(ctx: Context, uri: Uri): QuickFile {
    var name = uri.lastPathSegment ?: "file"
    var size = -1L
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            c.getString(0)?.let { name = it }
            if (!c.isNull(1)) size = c.getLong(1)
        }
    }
    return QuickFile(uri, name, ctx.contentResolver.getType(uri) ?: "application/octet-stream", size)
}

/**
 * The simple way to ask the team for something, like the web composer: write or dictate the request,
 * attach files, send. It goes to the default agent (the coordinator), who splits the work.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickTaskScreen(nav: Nav) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val agents by AppState.agents.collectAsState()
    var text by rememberSaveable { mutableStateOf("") }
    var agentId by rememberSaveable { mutableStateOf<String?>(null) }
    var userId by rememberSaveable { mutableStateOf<String?>(null) }
    var chosen by rememberSaveable { mutableStateOf(false) }
    val files = remember { mutableStateListOf<QuickFile>() }
    var sheet by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(agents) {
        if (!chosen && agentId == null && userId == null) {
            val saved = AppState.settings.getString(QUICK_ASSIGNEE_KEY)
            agentId = agents.firstOrNull { it.id == saved }?.id ?: defaultQuickAssignee(agents)
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    var listening by remember { mutableStateOf(false) }
    var partial by remember { mutableStateOf("") }
    val dictation = remember {
        Dictation(
            ctx,
            onPartial = { partial = it },
            onFinal = { said -> text = appendSpoken(text, said) },
            onListening = { listening = it },
            onError = { code -> AppState.toast("Speech recognition stopped (code $code).") },
        )
    }
    DisposableEffect(Unit) { onDispose { dictation.release() } }

    // Fallback for phones without a speech service other apps can bind to: the system dictation dialog.
    val voiceDialog = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val said = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
        if (r.resultCode == Activity.RESULT_OK && !said.isNullOrEmpty()) text = appendSpoken(text, said)
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) dictation.start() else AppState.toast("Allow the microphone to dictate.")
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri -> if (files.none { it.uri == uri }) files.add(quickFile(ctx, uri)) }
    }

    // Uses the phone's own speech recognition (the same one the keyboard dictation uses).
    fun toggleDictation() {
        when {
            dictation.isActive -> dictation.stop()
            !SpeechRecognizer.isRecognitionAvailable(ctx) -> {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                    .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say what you need")
                try {
                    voiceDialog.launch(intent)
                } catch (e: ActivityNotFoundException) {
                    AppState.toast("Speech recognition is not available on this phone.")
                }
            }
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> dictation.start()
            else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun send() {
        val body = text.trim()
        if (body.isEmpty() || saving) return
        files.firstOrNull { it.size > MAX_ATTACHMENT_BYTES }?.let { error = "${it.name} is larger than 100 MB."; return }
        saving = true; error = null
        scope.launch {
            try {
                // With files, hand the task over only after they are uploaded, so the agent starts with them.
                val assignNow = files.isEmpty()
                val created = Repo.createIssue(
                    AppState.cid,
                    jsonOf(
                        "title" to quickTitle(body),
                        "description" to body,
                        "status" to "todo",
                        "priority" to "medium",
                        "assigneeAgentId" to agentId.takeIf { assignNow },
                        "assigneeUserId" to userId.takeIf { assignNow },
                    ),
                )
                for (f in files) {
                    val bytes = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(f.uri)!!.use { it.readBytes() } }
                    Repo.uploadAttachment(AppState.cid, created.id, f.name, f.mime, bytes)
                }
                if (!assignNow && (agentId != null || userId != null)) {
                    Repo.updateIssue(created.id, jsonOf("assigneeAgentId" to agentId, "assigneeUserId" to userId))
                }
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
                title = { Text("New task") },
                actions = {
                    TextButton(onClick = { nav.back(); nav.newIssue() }) { Text("Full form") }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PropertyChip(
                onClick = { sheet = true },
                label = { Text("To: ${agentId?.let { AppState.agentName(it) } ?: userId?.let { AppState.userName(it) } ?: "Unassigned"}") },
                leadingIcon = {
                    when {
                        agentId != null -> AgentAvatar(agentId, 18.dp)
                        userId != null -> Avatar(AppState.userName(userId), null, 18.dp, isAgent = false)
                        else -> Icon(Icons.Outlined.PersonOutline, null, Modifier.size(18.dp))
                    }
                },
            )
            OutlinedTextField(
                // While dictating, the words still being recognised show after the text, and typing waits.
                value = if (partial.isEmpty()) text else appendSpoken(text, partial),
                onValueChange = { text = it; error = null },
                readOnly = listening,
                placeholder = { Text("Describe what you need…") },
                minLines = 6,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp).focusRequester(focus),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(
                    onClick = { toggleDictation() },
                    enabled = !saving,
                    modifier = Modifier.size(52.dp),
                    colors = if (listening) {
                        IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        )
                    } else IconButtonDefaults.filledTonalIconButtonColors(),
                ) {
                    Icon(
                        if (listening) Icons.Filled.Mic else Icons.Outlined.Mic,
                        if (listening) "Stop dictating" else "Dictate",
                        Modifier.size(26.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalIconButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !saving, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Outlined.AttachFile, "Attach file", Modifier.size(24.dp))
                }
                Spacer(Modifier.weight(1f))
                if (saving) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                else FilledIconButton(onClick = { send() }, enabled = text.isNotBlank() && !listening, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Send", Modifier.size(24.dp))
                }
            }
            files.forEach { f ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.InsertDriveFile, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(f.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = { files.remove(f) }, enabled = !saving) { Icon(Icons.Outlined.Close, "Remove") }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }

    if (sheet) {
        IssueAssigneeSheet(agentId, userId, onPick = { a ->
            chosen = true
            when (a) {
                is IssueAssignee.AgentA -> { agentId = a.id; userId = null; scope.launch { AppState.settings.setString(QUICK_ASSIGNEE_KEY, a.id) } }
                IssueAssignee.Me -> { agentId = null; userId = AppState.me.value?.id }
                IssueAssignee.Nobody -> { agentId = null; userId = null }
            }
        }, onDismiss = { sheet = false })
    }
}
