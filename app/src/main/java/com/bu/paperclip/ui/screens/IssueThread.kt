package com.bu.paperclip.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import com.bu.paperclip.net.ServerFiles
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextField
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bu.paperclip.R
import com.bu.paperclip.ui.components.AppFilterChip
import com.bu.paperclip.data.AppState
import com.bu.paperclip.data.IssueComment
import com.bu.paperclip.ui.components.AgentAvatar
import com.bu.paperclip.ui.components.Avatar
import com.bu.paperclip.ui.components.Markdown
import com.bu.paperclip.ui.components.errorMessage
import com.bu.paperclip.ui.theme.LocalStatusColors
import com.bu.paperclip.util.L10n
import com.bu.paperclip.util.absoluteTime
import com.bu.paperclip.util.relativeTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal const val ISSUE_LOCAL_PREFIX = "local-"

@Composable
private fun issueToneColor(tone: String): Color {
    val c = LocalStatusColors.current
    return when (tone) {
        "info" -> c.blue
        "success" -> c.green
        "warning" -> c.orange
        "danger" -> c.red
        else -> c.gray
    }
}

internal fun issueCommentAuthor(c: IssueComment): String = when (c.authorType) {
    "agent" -> AppState.agentName(c.authorAgentId ?: c.derivedAuthorAgentId)
    "user" -> AppState.userName(c.authorUserId)
    else -> c.derivedAuthorAgentId?.let { AppState.agentName(it) } ?: "Paperclip"
}

/** One comment in the thread: avatar, author, time, markdown body; system notices are compact. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun IssueCommentItem(c: IssueComment, onDelete: (() -> Unit)?) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val sending = c.id.startsWith(ISSUE_LOCAL_PREFIX)
    if (c.deletedAt != null) {
        Text(
            stringResource(R.string.issue_thread_deleted_comment, issueCommentAuthor(c), relativeTime(c.createdAt)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        return
    }
    if (c.presentation?.kind == "system_notice") {
        val tone = issueToneColor(c.presentation.tone)
        var open by remember { mutableStateOf(c.presentation.detailsDefaultOpen) }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tone.copy(alpha = 0.08f))
                .combinedClickable(onClick = { open = !open }, onLongClick = { menu = true })
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Outlined.Info, null, Modifier.size(16.dp).padding(top = 2.dp), tint = tone)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Row {
                    Text(c.presentation.title ?: issueCommentAuthor(c), style = MaterialTheme.typography.labelLarge, color = tone, modifier = Modifier.weight(1f))
                    Text(relativeTime(c.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (open || c.presentation.title == null) Markdown(c.body, fontSizeSp = 13f, selectable = false)
            }
        }
        IssueCommentMenu(menu, { menu = false }, ctx, c, onDelete)
        return
    }
    Row(
        Modifier.fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = { menu = true })
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        when (c.authorType) {
            "agent" -> AgentAvatar(c.authorAgentId ?: c.derivedAuthorAgentId, 30.dp)
            "user" -> {
                val u = AppState.users.collectAsState().value[c.authorUserId]
                Avatar(issueCommentAuthor(c), u?.image, 30.dp, isAgent = false)
            }
            else -> Avatar("Paperclip", null, 30.dp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(issueCommentAuthor(c), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (sending) stringResource(R.string.issue_thread_sending) else relativeTime(c.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(2.dp))
            Markdown(c.body, fontSizeSp = 15f)
        }
    }
    IssueCommentMenu(menu, { menu = false }, ctx, c, onDelete)
}

@Composable
private fun IssueCommentMenu(open: Boolean, onClose: () -> Unit, ctx: Context, c: IssueComment, onDelete: (() -> Unit)?) {
    val copied = stringResource(R.string.common_copied)
    DropdownMenu(expanded = open, onDismissRequest = onClose) {
        DropdownMenuItem(text = { Text(stringResource(R.string.issue_thread_copy_text)) }, onClick = {
            onClose()
            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("comment", c.body))
            AppState.toast(copied)
        })
        DropdownMenuItem(text = { Text(absoluteTime(c.createdAt)) }, onClick = onClose, enabled = false)
        if (onDelete != null && !c.id.startsWith(ISSUE_LOCAL_PREFIX)) {
            DropdownMenuItem(text = { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }, onClick = { onClose(); onDelete() })
        }
    }
}

private data class IssuePendingUpload(val uri: Uri, val file: PickedFile? = null, val attachmentId: String? = null, val failed: Boolean = false)

/**
 * Bottom composer: markdown text, @mentions (agent links), image and file attachments and an
 * "interrupt" toggle while an agent run is live. Optimistic: [onLocal] shows the message
 * immediately; [onSent]/[onFailed] reconcile it.
 */
@Composable
internal fun IssueComposer(
    issueId: String,
    runLive: Boolean,
    onLocal: (IssueComment) -> Unit,
    onSent: (localId: String, IssueComment) -> Unit,
    onFailed: (localId: String) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember(issueId) { mutableStateOf(TextFieldValue("")) }
    var interrupt by remember { mutableStateOf(false) }
    var mentionSheet by remember { mutableStateOf(false) }
    val uploads = remember(issueId) { mutableStateListOf<IssuePendingUpload>() }

    var attachMenu by remember { mutableStateOf(false) }
    fun upload(uris: List<Uri>) {
        uris.forEach { uri ->
            uploads.add(IssuePendingUpload(uri))
            scope.launch {
                try {
                    val file = withContext(Dispatchers.IO) { pickedFile(ctx, uri) }
                    uploads.indexOfFirst { it.uri == uri }.takeIf { it >= 0 }?.let { uploads[it] = uploads[it].copy(file = file) }
                    val att = uploadPickedFile(ctx, issueId, file)
                    val i = uploads.indexOfFirst { it.uri == uri }
                    if (i >= 0) uploads[i] = uploads[i].copy(attachmentId = att.id)
                } catch (c: CancellationException) {
                    throw c
                } catch (t: Throwable) {
                    val i = uploads.indexOfFirst { it.uri == uri }
                    if (i >= 0) uploads[i] = uploads[i].copy(failed = true)
                    AppState.toast(L10n.str(R.string.issue_thread_upload_failed, errorMessage(t)))
                }
            }
        }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { upload(it) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { upload(it) }

    val uploading = uploads.any { it.attachmentId == null && !it.failed }
    val canSend = text.text.isNotBlank() && !uploading

    fun send() {
        val body = text.text.trim()
        if (body.isEmpty()) return
        val ids = uploads.mapNotNull { it.attachmentId }
        val localId = ISSUE_LOCAL_PREFIX + System.nanoTime()
        val me = AppState.me.value
        onLocal(IssueComment(id = localId, issueId = issueId, authorType = "user", authorUserId = me?.id, body = body, createdAt = java.time.Instant.now().toString()))
        val saved = text
        val savedUploads = uploads.toList()
        val doInterrupt = interrupt && runLive
        text = TextFieldValue("")
        uploads.clear()
        interrupt = false
        scope.launch {
            try {
                onSent(localId, issueAddComment(issueId, body, doInterrupt, ids))
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                onFailed(localId)
                text = saved
                uploads.addAll(savedUploads)
                AppState.toast(L10n.str(R.string.issue_thread_not_sent, errorMessage(t)))
            }
        }
    }

    val hairline = MaterialTheme.colorScheme.outlineVariant
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.drawBehind { drawLine(hairline, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 8.dp, vertical = 6.dp)) {
            if (uploads.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    uploads.forEach { u ->
                        Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                            if (u.file == null || ServerFiles.isImage(u.file.mime)) {
                                AsyncImage(model = u.uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp))
                            } else {
                                Column(Modifier.size(56.dp).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Icon(Icons.AutoMirrored.Outlined.InsertDriveFile, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(u.file.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (u.attachmentId == null && !u.failed) {
                                CircularProgressIndicator(Modifier.size(22.dp).align(Alignment.Center), strokeWidth = 2.dp)
                            }
                            if (u.failed) Box(Modifier.size(56.dp).background(MaterialTheme.colorScheme.error.copy(alpha = 0.4f)))
                            IconButton(
                                onClick = { uploads.remove(u) },
                                modifier = Modifier.size(22.dp).align(Alignment.TopEnd).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50)),
                            ) { Icon(Icons.Default.Close, stringResource(R.string.common_remove), tint = Color.White, modifier = Modifier.size(14.dp)) }
                        }
                    }
                }
            }
            if (runLive) {
                AppFilterChip(
                    selected = interrupt,
                    onClick = { interrupt = !interrupt },
                    label = { Text(stringResource(if (interrupt) R.string.issue_thread_will_interrupt else R.string.issue_thread_interrupt)) },
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                IconButton(onClick = { mentionSheet = true }) { Icon(Icons.Default.AlternateEmail, stringResource(R.string.issue_thread_mention_agent)) }
                Box {
                    IconButton(onClick = { attachMenu = true }) { Icon(Icons.Outlined.AttachFile, stringResource(R.string.issue_thread_attach)) }
                    DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.issue_thread_attach_photo)) },
                            leadingIcon = { Icon(Icons.Outlined.Image, null) },
                            onClick = { attachMenu = false; photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.issue_thread_attach_file)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.InsertDriveFile, null) },
                            onClick = { attachMenu = false; filePicker.launch(arrayOf("*/*")) },
                        )
                    }
                }
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(stringResource(R.string.issue_thread_placeholder)) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp, max = 180.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                )
                Spacer(Modifier.width(6.dp))
                val sendBg by androidx.compose.animation.animateColorAsState(
                    if (canSend) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHigh, label = "send",
                )
                IconButton(
                    onClick = { send() },
                    enabled = canSend,
                    modifier = Modifier.padding(bottom = 2.dp).size(44.dp).background(sendBg, RoundedCornerShape(50)),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send, stringResource(R.string.common_send),
                        tint = if (canSend) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }

    if (mentionSheet) {
        IssueAgentPickerSheet(title = stringResource(R.string.issue_thread_mention_title), onDismiss = { mentionSheet = false }) { agentId ->
            val name = AppState.agentName(agentId)
            val insert = "[@$name](agent://$agentId) "
            val sel = text.selection
            val t = text.text
            val start = sel.min.coerceIn(0, t.length)
            val end = sel.max.coerceIn(0, t.length)
            val prefix = if (start > 0 && !t[start - 1].isWhitespace()) " " else ""
            val newText = t.substring(0, start) + prefix + insert + t.substring(end)
            text = TextFieldValue(newText, TextRange(start + prefix.length + insert.length))
        }
    }
}

@Composable
internal fun IssueAgentPickerSheet(title: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val agents by AppState.agents.collectAsState()
    var q by remember { mutableStateOf("") }
    IssueSheet(title, onDismiss) {
        if (agents.size > 8) {
            OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text(stringResource(R.string.issue_thread_filter_agents)) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        }
        LazyColumn(Modifier.heightIn(max = 520.dp)) {
            items(agents.filter { q.isBlank() || it.name.contains(q, true) }, key = { it.id }) { a ->
                IssueSheetRow(false, { onPick(a.id); onDismiss() }, { AgentAvatar(a.id, 28.dp) }, a.name, a.title)
            }
        }
    }
}

@Composable
internal fun IssueLoadOlderButton(loading: Boolean, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        else TextButton(onClick = onClick) { Text(stringResource(R.string.issue_thread_load_older)) }
    }
}
