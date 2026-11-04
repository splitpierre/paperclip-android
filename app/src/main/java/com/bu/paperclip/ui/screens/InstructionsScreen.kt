package com.bu.paperclip.ui.screens

import com.bu.paperclip.util.L10n
import com.bu.paperclip.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bu.paperclip.ui.theme.Mono
import com.bu.paperclip.data.AppState
import com.bu.paperclip.data.InstructionsFile
import com.bu.paperclip.data.Repo
import com.bu.paperclip.ui.Nav
import com.bu.paperclip.ui.components.EmptyState
import com.bu.paperclip.ui.components.LoadContent
import com.bu.paperclip.ui.components.Pill
import com.bu.paperclip.ui.components.Markdown
import com.bu.paperclip.ui.components.rememberAction
import com.bu.paperclip.ui.components.rememberLoader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstructionsScreen(nav: Nav, agentId: String) {
    var openPath by rememberSaveable(agentId) { mutableStateOf<String?>(null) }
    val agentName = AppState.agent(agentId)?.name
    val bundle = rememberLoader(agentId) { Repo.instructions(agentId) }

    BackHandler(enabled = openPath != null) { openPath = null }

    val path = openPath
    if (path != null) {
        AgentInstructionsFileView(agentId, path, agentName, onBack = { openPath = null })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.instructions_title))
                        if (agentName != null) Text(agentName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { BackIcon { nav.back() } },
            )
        },
    ) { pad ->
        LoadContent(bundle, Modifier.padding(pad)) { b ->
            val files = b.files.sortedWith(compareBy<InstructionsFile>({ !it.isEntryFile }, { it.path.lowercase() }))
            if (files.isEmpty()) {
                EmptyState(stringResource(R.string.instructions_empty))
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    item(key = "note") { AgentInstructionsSyncNote(Modifier.padding(16.dp)) }
                    items(files, key = { it.path }) { f ->
                        Row(
                            Modifier.fillMaxWidth().clickable { openPath = f.path }.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Description, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(f.path, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                    if (f.isEntryFile) { Spacer(Modifier.width(8.dp)); Pill(stringResource(R.string.instructions_entry), MaterialTheme.colorScheme.tertiary) }
                                }
                                val meta = listOfNotNull(f.size?.let { agentFmtBytes(it) }, if (!f.editable) stringResource(R.string.instructions_read_only) else null).joinToString(" · ")
                                if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentInstructionsSyncNote(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Top) {
        Icon(Icons.Outlined.Info, null, Modifier.padding(top = 2.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.instructions_sync_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentInstructionsFileView(agentId: String, path: String, agentName: String?, onBack: () -> Unit) {
    val act = rememberAction()
    val file = rememberLoader(agentId, path) { Repo.instructionsFile(agentId, path) }
    var editing by rememberSaveable(path) { mutableStateOf(false) }
    var draft by rememberSaveable(path) { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val original = file.value?.content ?: ""
    val dirty = editing && draft != original

    fun leave() { if (dirty) confirmDiscard = true else if (editing) editing = false else onBack() }
    BackHandler { leave() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(path, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (agentName != null) Text(agentName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { BackIcon { leave() } },
                actions = {
                    val f = file.value
                    if (f != null && !editing && f.editable) {
                        IconButton(onClick = { draft = f.content; editing = true }) { Icon(Icons.Outlined.Edit, stringResource(R.string.common_edit)) }
                    }
                    if (editing) {
                        TextButton(
                            enabled = dirty && !saving,
                            onClick = {
                                saving = true
                                act({
                                    try {
                                        val saved = Repo.saveInstructionsFile(agentId, path, draft)
                                        file.set(saved.copy(content = saved.content.ifEmpty { draft }))
                                        editing = false
                                    } finally {
                                        saving = false
                                    }
                                }, L10n.str(R.string.instructions_saved, path))
                            },
                        ) { Text(if (saving) stringResource(R.string.instructions_saving) else stringResource(R.string.common_save)) }
                    }
                },
            )
        },
    ) { pad ->
        LoadContent(file, Modifier.padding(pad)) { f ->
            if (editing) {
                Column(Modifier.fillMaxSize().imePadding()) {
                    AgentInstructionsSyncNote(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        textStyle = TextStyle(fontFamily = Mono, fontSize = 13.sp, lineHeight = 18.sp),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
                    if (path.endsWith(".md", ignoreCase = true) || path.endsWith(".markdown", ignoreCase = true)) {
                        Markdown(f.content, Modifier.fillMaxWidth().padding(16.dp))
                    } else {
                        SelectionContainer {
                            Text(
                                f.content,
                                style = TextStyle(fontFamily = Mono, fontSize = 12.sp, lineHeight = 17.sp),
                                softWrap = false,
                                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.instructions_discard_title)) },
            text = { Text(stringResource(R.string.instructions_discard_body, path)) },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; editing = false }) { Text(stringResource(R.string.common_discard)) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.common_keep_editing)) } },
        )
    }
}

internal fun agentFmtBytes(n: Long): String = com.bu.paperclip.util.formatBytes(n, 1000)
