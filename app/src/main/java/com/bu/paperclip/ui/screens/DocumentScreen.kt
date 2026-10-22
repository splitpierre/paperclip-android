package com.bu.paperclip.ui.screens

import com.bu.paperclip.util.catching

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.bu.paperclip.ui.theme.Mono
import com.bu.paperclip.data.AppState
import com.bu.paperclip.data.IssueDocument
import com.bu.paperclip.data.Repo
import com.bu.paperclip.net.ApiException
import com.bu.paperclip.ui.Nav
import com.bu.paperclip.ui.components.LiveRefresh
import com.bu.paperclip.ui.components.LoadContent
import com.bu.paperclip.ui.components.Markdown
import com.bu.paperclip.ui.components.errorMessage
import com.bu.paperclip.ui.components.rememberLoader
import com.bu.paperclip.util.relativeTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScreen(nav: Nav, issueId: String, key: String) {
    val scope = rememberCoroutineScope()
    val loader = rememberLoader(issueId, key) { Repo.document(issueId, key) }
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var titleDraft by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val doc = loader.value

    LiveRefresh(filter = { e ->
        e.type == "activity.logged" && e.s("action")?.contains("document") == true && e.s("entityId") == issueId
    }) { if (!editing) loader.refresh() }

    fun startEdit(d: IssueDocument) { draft = d.body; titleDraft = d.title ?: ""; editing = true }

    fun save(d: IssueDocument) {
        saving = true
        scope.launch {
            try {
                val saved = Repo.saveDocument(issueId, key, titleDraft.trim().ifEmpty { null }, draft, d.latestRevisionId)
                loader.set(saved)
                editing = false
                AppState.toast("Saved revision ${saved.latestRevisionNumber ?: ""}".trim())
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                if (t is ApiException.Http && t.status == 409) {
                    AppState.toast("Someone changed this document meanwhile. Reloaded the latest version; your edit is still in the editor.")
                    catching { loader.set(Repo.document(issueId, key)) }
                } else AppState.toast(errorMessage(t))
            } finally {
                saving = false
            }
        }
    }

    BackHandler(enabled = editing) { if (draft != doc?.body) confirmDiscard = true else editing = false }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackIcon { if (editing && draft != doc?.body) confirmDiscard = true else if (editing) editing = false else nav.back() } },
                title = {
                    Column {
                        Text(doc?.title?.takeIf { it.isNotBlank() } ?: key, style = MaterialTheme.typography.titleMedium)
                        if (doc != null) Text(
                            listOfNotNull(key, doc.latestRevisionNumber?.let { "rev $it" }, relativeTime(doc.updatedAt).ifEmpty { null }).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (doc != null) {
                        if (editing) {
                            if (saving) CircularProgressIndicator(Modifier.padding(end = 16.dp).size(22.dp), strokeWidth = 2.dp)
                            else TextButton(onClick = { save(doc) }) { Text("Save") }
                        } else IconButton(onClick = { startEdit(doc) }) { Icon(Icons.Outlined.Edit, "Edit") }
                    }
                },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            LoadContent(loader) { d ->
                if (editing) {
                    Column(Modifier.fillMaxSize().imePadding().padding(12.dp)) {
                        OutlinedTextField(titleDraft, { titleDraft = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                } else {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                        if (d.body.isBlank()) Text("This document is empty.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else Markdown(d.body, fontSizeSp = 15f)
                        Spacer(Modifier.height(48.dp))
                    }
                }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; editing = false }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }
}
