package com.bu.paperclip.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bu.paperclip.ui.components.AppFilterChip
import com.bu.paperclip.data.ActivityEvent
import com.bu.paperclip.data.AppState
import com.bu.paperclip.data.Repo
import com.bu.paperclip.data.SearchResult
import com.bu.paperclip.ui.Nav
import com.bu.paperclip.ui.components.AgentAvatar
import com.bu.paperclip.ui.components.Avatar
import com.bu.paperclip.ui.components.EmptyState
import com.bu.paperclip.ui.components.LiveRefresh
import com.bu.paperclip.ui.components.LoadContent
import com.bu.paperclip.ui.components.Links
import com.bu.paperclip.ui.components.SectionHeader
import com.bu.paperclip.ui.components.StatusIcon
import com.bu.paperclip.ui.components.activityActor
import com.bu.paperclip.ui.components.activityLink
import com.bu.paperclip.ui.components.activitySentence
import com.bu.paperclip.ui.components.errorMessage
import com.bu.paperclip.ui.components.rememberLoader
import com.bu.paperclip.ui.components.rememberMinuteTick
import com.bu.paperclip.util.relativeTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

// ---------------- Activity ----------------

private val activityFilters = listOf("All", "Agents", "People", "System")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(nav: Nav) {
    val company by AppState.company.collectAsState()
    val cid = company?.id ?: ""
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val loader = rememberLoader(cid) { Repo.activity(cid, limit = 100) }
    LiveRefresh(debounceMs = 1000, filter = { it.type == "activity.logged" }) { loader.refresh() }

    Scaffold(topBar = { TopAppBar(title = { Text("Activity") }, navigationIcon = { BackIcon { nav.back() } }) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                activityFilters.forEachIndexed { i, label ->
                    AppFilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(label) })
                }
            }
            LoadContent(loader) { events ->
                val shown = events.filter {
                    when (filter) {
                        1 -> it.actorType == "agent"
                        2 -> it.actorType == "user"
                        3 -> it.actorType != "agent" && it.actorType != "user"
                        else -> true
                    }
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    if (shown.isEmpty()) item { EmptyState("No activity yet.", Icons.Outlined.History) }
                    items(shown, key = { e -> e.id.ifEmpty { "${e.createdAt}${e.action}${e.entityId}" } }) { e ->
                        ActivityRow(e) { activityLink(e)?.let { Links.handler(it) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityRow(e: ActivityEvent, onClick: () -> Unit) {
    rememberMinuteTick()
    Row(
        Modifier.fillMaxWidth().clickable(enabled = activityLink(e) != null, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (e.actorType) {
            "agent" -> AgentAvatar(e.actorId ?: e.agentId, 30.dp)
            "user" -> Avatar(activityActor(e), AppState.users.collectAsState().value[e.actorId]?.image, 30.dp, isAgent = false)
            else -> Avatar("Paperclip", null, 30.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(activitySentence(e), style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(relativeTime(e.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------- Search ----------------

private sealed class AdminSearchState {
    data object Idle : AdminSearchState()
    data object Loading : AdminSearchState()
    data class Done(val results: List<SearchResult>) : AdminSearchState()
    data class Failed(val message: String) : AdminSearchState()
}

private val adminTagRe = Regex("<[^>]+>")

private fun adminSearchTypeLabel(type: String) = when (type) {
    "issue" -> "Tasks"
    "comment" -> "Comments"
    "document" -> "Documents"
    "artifact" -> "Artifacts"
    "agent" -> "Agents"
    "project" -> "Projects"
    else -> type.replace('_', ' ').replaceFirstChar { it.uppercase() } + "s"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(nav: Nav) {
    val company by AppState.company.collectAsState()
    val cid = company?.id ?: ""
    var query by rememberSaveable { mutableStateOf("") }
    var state by remember { mutableStateOf<AdminSearchState>(AdminSearchState.Idle) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(query, cid) {
        val q = query.trim()
        if (q.length < 2 || cid.isEmpty()) {
            state = AdminSearchState.Idle
            return@LaunchedEffect
        }
        delay(300)
        state = AdminSearchState.Loading
        state = try {
            AdminSearchState.Done(Repo.search(cid, q).results)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            AdminSearchState.Failed(errorMessage(t))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackIcon { nav.back() } },
                title = {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search tasks, comments, docs…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear") }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (val s = state) {
                AdminSearchState.Idle -> EmptyState("Type at least two characters to search across ${company?.name ?: "the company"}.", Icons.Outlined.Search)
                AdminSearchState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    CircularProgressIndicator(Modifier.padding(32.dp))
                }
                is AdminSearchState.Failed -> EmptyState(s.message)
                is AdminSearchState.Done -> {
                    if (s.results.isEmpty()) {
                        EmptyState("No results for \"${query.trim()}\".", Icons.Outlined.Search)
                    } else {
                        val grouped = s.results.groupBy { it.type }
                        LazyColumn(Modifier.fillMaxSize()) {
                            grouped.forEach { (type, list) ->
                                item(key = "h-$type") { SectionHeader("${adminSearchTypeLabel(type)} · ${list.size}") }
                                items(list, key = { "$type-${it.id}" }, contentType = { "search-result" }) { r -> SearchResultRow(r) { adminOpenSearchResult(nav, r) } }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun adminOpenSearchResult(nav: Nav, r: SearchResult) {
    val issueRef = r.issue?.let { it.identifier ?: it.id.ifEmpty { null } }
    when {
        r.type == "issue" -> nav.issue(issueRef ?: r.id)
        r.type == "agent" -> nav.agent(r.id)
        r.type == "project" -> nav.project(r.id)
        !r.href.isNullOrBlank() && Links.handler(r.href) -> {}
        issueRef != null -> nav.issue(issueRef)
    }
}

@Composable
private fun SearchResultRow(r: SearchResult, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        val status = r.issue?.status
        if (status != null) {
            Box(Modifier.padding(top = 3.dp)) { StatusIcon(status, 15.dp) }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val ref = r.issue?.identifier
                if (ref != null) {
                    Text(ref, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                }
                Spacer(Modifier.weight(1f))
                if (r.updatedAt != null) {
                    Text(relativeTime(r.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                adminTagRe.replace(r.title ?: r.issue?.title ?: "Untitled", ""),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val snippet = r.snippet?.let { adminTagRe.replace(it, "").trim() }
            if (!snippet.isNullOrBlank()) {
                Text(snippet, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
