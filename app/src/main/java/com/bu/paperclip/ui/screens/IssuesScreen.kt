package com.bu.paperclip.ui.screens

import com.bu.paperclip.util.catching

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bu.paperclip.ui.components.AppFilterChip
import com.bu.paperclip.data.ACTIVE_STATUSES
import com.bu.paperclip.data.ActiveRunRef
import com.bu.paperclip.data.AppState
import com.bu.paperclip.data.Issue
import com.bu.paperclip.data.IssueStore
import com.bu.paperclip.data.Label
import com.bu.paperclip.data.MatchCtx
import com.bu.paperclip.data.Repo
import com.bu.paperclip.data.TaskFilter
import com.bu.paperclip.data.TaskGraph
import com.bu.paperclip.data.TaskNode
import com.bu.paperclip.data.comparator
import com.bu.paperclip.data.isClosed
import com.bu.paperclip.data.jsonOf
import com.bu.paperclip.data.matches
import com.bu.paperclip.data.openBlockers
import com.bu.paperclip.data.priorityRankOf
import com.bu.paperclip.data.siblingOrder
import com.bu.paperclip.data.statusRankOf
import com.bu.paperclip.net.AppJson
import com.bu.paperclip.ui.Nav
import com.bu.paperclip.ui.components.AgentAvatar
import com.bu.paperclip.ui.components.Avatar
import com.bu.paperclip.ui.components.BoardCard
import com.bu.paperclip.ui.components.Divider
import com.bu.paperclip.ui.components.EmptyState
import com.bu.paperclip.ui.components.ErrorState
import com.bu.paperclip.ui.components.ISSUE_STATUSES
import com.bu.paperclip.ui.components.IssueRow
import com.bu.paperclip.ui.components.NextUpCard
import com.bu.paperclip.ui.components.PRIORITIES
import com.bu.paperclip.ui.components.PriorityIcon
import com.bu.paperclip.ui.components.RollupBar
import com.bu.paperclip.ui.components.StatusChip
import com.bu.paperclip.ui.components.StatusIcon
import com.bu.paperclip.ui.components.TaskTreeRow
import com.bu.paperclip.ui.components.errorMessage
import com.bu.paperclip.ui.components.flattenTree
import com.bu.paperclip.ui.components.statusLabel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.derivedStateOf
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

private enum class TaskView(val key: String, val label: String) { List("list", "List"), Tree("tree", "Tree"), Board("board", "Board") }

/** What every task view renders from: the mirror, the filter and the graph built over them. */
internal class TasksModel(all: Collection<Issue>, val graph: TaskGraph, val filter: TaskFilter, val live: Set<String>, val companyId: String) {
    val matched: List<Issue> = all.filter { filter.matches(it, graph.ctx) }.sortedWith(filter.comparator())
    fun liveCopy(i: Issue): Issue = if (i.id in live && i.activeRun == null) i.copy(activeRun = ActiveRunRef(status = "running")) else i
    fun isBlocked(i: Issue): Boolean = !isClosed(i.status) && (i.status == "blocked" || i.openBlockers(graph.ctx).isNotEmpty())
}

/** Seed from the populated mirror on entry; keep the previous result during background updates. */
@Composable
internal fun rememberTasksModel(
    companyId: String,
    issues: Map<String, Issue>,
    live: Set<String>,
    meId: String?,
    filter: TaskFilter,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
): TasksModel {
    var graph by remember(companyId) {
        mutableStateOf(TaskGraph(issues.values, MatchCtx(meId, live) { issues[it]?.status }))
    }
    LaunchedEffect(companyId, issues, live, meId) {
        graph = withContext(dispatcher) {
            TaskGraph(issues.values, MatchCtx(meId, live) { issues[it]?.status })
        }
    }
    var model by remember(companyId) {
        mutableStateOf(TasksModel(graph.byId.values, graph, filter, live, companyId))
    }
    LaunchedEffect(companyId, graph, filter) {
        val sourceGraph = graph
        model = withContext(dispatcher) { TasksModel(sourceGraph.byId.values, sourceGraph, filter, sourceGraph.ctx.live, companyId) }
    }
    return model
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssuesScreen(nav: Nav) {
    val scope = rememberCoroutineScope()
    val issues by IssueStore.issues.collectAsState()
    val live by IssueStore.liveIssueIds.collectAsState()
    val status by IssueStore.status.collectAsState()
    val me by AppState.me.collectAsState()
    AppState.agents.collectAsState()
    AppState.projects.collectAsState()

    var view by remember { mutableStateOf(TaskView.Tree) }
    var filter by remember { mutableStateOf(TaskFilter()) }
    var prefsLoaded by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var quickEdit by remember { mutableStateOf<Issue?>(null) }
    val cid = AppState.company.collectAsState().value?.id.orEmpty()
    var focusId by remember(cid) { mutableStateOf<String?>(null) }
    // Explicit expand/collapse choices survive leaving the screen and restarts; ids of deleted tasks are pruned.
    val collapsed = rememberSavedFlags("tasks.collapsed.$cid") { id -> issues.isEmpty() || id in issues }

    LaunchedEffect(Unit) {
        AppState.settings.getString("tasks.view")?.let { k -> TaskView.entries.firstOrNull { it.key == k }?.let { view = it } }
        AppState.settings.getString("tasks.filter")?.let { s -> catching { filter = AppJson.decodeFromString(TaskFilter.serializer(), s) } }
        prefsLoaded = true
    }
    LaunchedEffect(view, prefsLoaded) { if (prefsLoaded) AppState.settings.setString("tasks.view", view.key) }
    LaunchedEffect(filter, prefsLoaded) { if (prefsLoaded) AppState.settings.setString("tasks.filter", AppJson.encodeToString(TaskFilter.serializer(), filter)) }

    val model = rememberTasksModel(cid, issues, live, me?.id, filter)
    val graph = model.graph

    fun patch(original: Issue, optimistic: Issue, body: JsonObject) {
        IssueStore.upsert(optimistic)
        scope.launch {
            try {
                IssueStore.upsert(Repo.updateIssue(original.id, body))
            } catch (t: Throwable) {
                IssueStore.upsert(original)
                AppState.toast(errorMessage(t))
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("Tasks", style = MaterialTheme.typography.headlineSmall)
                            Text(
                                "${view.label} · ${model.matched.size} of ${issues.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        TaskViewSwitcher(view) { view = it }
                        IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) filter = filter.copy(query = "") }) {
                            Icon(if (searchOpen) Icons.Default.Close else Icons.Default.Search, "Search")
                        }
                        IconButton(onClick = { showFilters = true }) {
                            BadgedBox(badge = { if (filter.activeCount > 0) Badge { Text("${filter.activeCount}") } }) {
                                Icon(Icons.Outlined.FilterList, "Filters")
                            }
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                            DropdownMenu(menu, { menu = false }) {
                                if (view == TaskView.Tree) {
                                    DropdownMenuItem(text = { Text("Expand all") }, onClick = {
                                        menu = false
                                        graph.forest(filter, focusId).flatMap { taskAllNodes(it) }.forEach { collapsed[it.issue.id] = false }
                                    })
                                    DropdownMenuItem(text = { Text("Collapse all") }, onClick = {
                                        menu = false
                                        graph.forest(filter, focusId).flatMap { taskAllNodes(it) }.filter { it.children.isNotEmpty() }.forEach { collapsed[it.issue.id] = true }
                                    })
                                }
                                DropdownMenuItem(text = { Text("Reset filters") }, onClick = { menu = false; filter = TaskFilter(sort = filter.sort, groupBy = filter.groupBy) })
                                DropdownMenuItem(text = { Text("Reload all tasks") }, onClick = { menu = false; scope.launch { IssueStore.refresh(full = true) } })
                            }
                        }
                    },
                )
                if (searchOpen) {
                    OutlinedTextField(
                        value = filter.query,
                        onValueChange = { filter = filter.copy(query = it) },
                        placeholder = { Text("Filter by title or identifier") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                } else Spacer(Modifier.height(6.dp))
                TaskPresetRow(filter) { filter = it }
                TaskActiveChips(filter) { filter = it }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { nav.newIssue(parentId = if (view == TaskView.Tree) focusId else null) }) { Icon(Icons.Default.Add, "New task") }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            val st = status
            when {
                issues.isEmpty() && st is IssueStore.Status.Error -> ErrorState(st.message) { scope.launch { IssueStore.refresh(full = true) } }
                issues.isEmpty() && st is IssueStore.Status.Ready -> EmptyState("No tasks yet")
                issues.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = { refreshing = true; scope.launch { IssueStore.refresh(full = true); refreshing = false } },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when (view) {
                        TaskView.List -> TaskListView(model, nav) { quickEdit = it }
                        TaskView.Tree -> TaskTreeView(model, nav, focusId, onFocus = { focusId = it }, collapsed = collapsed) { quickEdit = it }
                        TaskView.Board -> TaskBoardView(model, nav) { quickEdit = it }
                    }
                }
            }
        }
    }

    if (showFilters) TaskFilterSheet(filter, onChange = { filter = it }, onDismiss = { showFilters = false }, showGroup = view == TaskView.List)

    quickEdit?.let { q ->
        val current = issues[q.id] ?: q
        TaskQuickSheet(
            issue = current,
            graph = graph,
            onDismiss = { quickEdit = null },
            onOpen = { quickEdit = null; nav.issue(current.identifier ?: current.id) },
            onFocus = { quickEdit = null; view = TaskView.Tree; focusId = current.id },
            onStatus = { s -> patch(current, current.copy(status = s), jsonOf("status" to s)) },
            onPriority = { p -> patch(current, current.copy(priority = p), jsonOf("priority" to p)) },
            onAssign = { a ->
                when (a) {
                    is IssueAssignee.AgentA -> patch(current, current.copy(assigneeAgentId = a.id, assigneeUserId = null), jsonOf("assigneeAgentId" to a.id, "assigneeUserId" to JsonNull))
                    IssueAssignee.Me -> me?.id?.let { mid -> patch(current, current.copy(assigneeAgentId = null, assigneeUserId = mid), jsonOf("assigneeAgentId" to JsonNull, "assigneeUserId" to mid)) }
                    IssueAssignee.Nobody -> patch(current, current.copy(assigneeAgentId = null, assigneeUserId = null), jsonOf("assigneeAgentId" to JsonNull, "assigneeUserId" to JsonNull))
                }
            },
        )
    }
}

private fun taskAllNodes(n: TaskNode): List<TaskNode> = listOf(n) + n.children.flatMap { taskAllNodes(it) }

// ---------------- presets & active filter chips ----------------

@Composable
private fun TaskPresetRow(f: TaskFilter, onChange: (TaskFilter) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AppFilterChip(f.statuses == ACTIVE_STATUSES, { onChange(f.copy(statuses = ACTIVE_STATUSES)) }, { Text("Active") })
        AppFilterChip(f.assignees == setOf("me"), { onChange(f.copy(assignees = if (f.assignees == setOf("me")) emptySet() else setOf("me"))) }, { Text("Mine") })
        AppFilterChip(f.liveOnly, { onChange(f.copy(liveOnly = !f.liveOnly)) }, { Text("Live") })
        AppFilterChip(f.blockedOnly, { onChange(f.copy(blockedOnly = !f.blockedOnly)) }, { Text("Blocked") })
        AppFilterChip(f.assignees == setOf("unassigned"), { onChange(f.copy(assignees = if (f.assignees == setOf("unassigned")) emptySet() else setOf("unassigned"))) }, { Text("Unassigned") })
        AppFilterChip(f.statuses == setOf("done"), { onChange(f.copy(statuses = setOf("done"))) }, { Text("Done") })
        AppFilterChip(f.statuses.isEmpty(), { onChange(f.copy(statuses = emptySet())) }, { Text("All") })
    }
}

@Composable
private fun TaskActiveChips(f: TaskFilter, onChange: (TaskFilter) -> Unit) {
    val chips = buildList<Pair<String, () -> TaskFilter>> {
        val presetStatus = f.statuses == ACTIVE_STATUSES || f.statuses.isEmpty() || f.statuses == setOf("done")
        if (!presetStatus) add(f.statuses.sortedBy { statusRankOf(it) }.joinToString(", ") { statusLabel(it) } to { f.copy(statuses = ACTIVE_STATUSES) })
        f.priorities.forEach { p -> add(p.replaceFirstChar { it.uppercase() } to { f.copy(priorities = f.priorities - p) }) }
        if (f.assignees != setOf("me") && f.assignees != setOf("unassigned")) f.assignees.forEach { a ->
            val label = when {
                a == "me" -> "Me"
                a == "unassigned" -> "Unassigned"
                a.startsWith("user:") -> AppState.userName(a.removePrefix("user:"))
                else -> AppState.agentName(a)
            }
            add(label to { f.copy(assignees = f.assignees - a) })
        }
        f.projects.forEach { p -> add((if (p == "none") "No project" else AppState.project(p)?.name ?: "Project") to { f.copy(projects = f.projects - p) }) }
        if (f.labels.isNotEmpty()) add("${f.labels.size} label${if (f.labels.size > 1) "s" else ""}" to { f.copy(labels = emptySet()) })
        if (f.hideRoutine) add("No routine runs" to { f.copy(hideRoutine = false) })
    }
    if (chips.isEmpty()) { Spacer(Modifier.height(4.dp)); return }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        chips.forEach { (label, remove) ->
            InputChip(selected = true, onClick = { onChange(remove()) }, label = { Text(label, maxLines = 1) }, trailingIcon = { Icon(Icons.Default.Close, "Remove", Modifier.size(16.dp)) })
        }
        TextButton(onClick = { onChange(TaskFilter(sort = f.sort, groupBy = f.groupBy, query = f.query)) }) { Text("Clear") }
    }
}

/**
 * A string→flag map persisted in settings under [prefKey]. Saved values load once; changes made before
 * they arrive win. Writes are debounced, keep only keys accepted by [keep], and are capped at 2,000.
 */
@OptIn(FlowPreview::class)
@Composable
private fun rememberSavedFlags(prefKey: String, keep: (String) -> Boolean = { true }): SnapshotStateMap<String, Boolean> {
    val map = remember(prefKey) { mutableStateMapOf<String, Boolean>() }
    val keepNow by rememberUpdatedState(keep)
    LaunchedEffect(prefKey) {
        val ser = MapSerializer(String.serializer(), Boolean.serializer())
        AppState.settings.getString(prefKey)?.let { saved ->
            catching { AppJson.decodeFromString(ser, saved) }.getOrNull()?.forEach { (k, v) -> if (k !in map) map[k] = v }
        }
        snapshotFlow { map.toMap() }.drop(1).debounce(400).collect { now ->
            val pruned = now.filterKeys { keepNow(it) }.entries.toList().takeLast(2000).associate { it.key to it.value }
            AppState.settings.setString(prefKey, AppJson.encodeToString(ser, pruned))
        }
    }
    return map
}

// ---------------- list ----------------

private class TaskGroup(val key: String, val label: String, val leading: @Composable () -> Unit, val items: List<Issue>)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskListView(m: TasksModel, nav: Nav, onLongPress: (Issue) -> Unit) {
    val collapsedGroups = rememberSavedFlags("tasks.collapsedGroups.${AppState.company.collectAsState().value?.id.orEmpty()}.${m.filter.groupBy}")
    val groups: List<TaskGroup> = remember(m) {
        when (m.filter.groupBy) {
            "status" -> m.matched.groupBy { it.status }.entries.sortedBy { statusRankOf(it.key) }
                .map { (k, v) -> TaskGroup(k, statusLabel(k), { StatusIcon(k, 14.dp) }, v) }
            "priority" -> m.matched.groupBy { it.priority }.entries.sortedBy { priorityRankOf(it.key) }
                .map { (k, v) -> TaskGroup(k, k.replaceFirstChar { it.uppercase() }, { PriorityIcon(k, 16.dp) }, v) }
            "assignee" -> m.matched.groupBy { it.assigneeAgentId ?: it.assigneeUserId?.let { u -> "user:$u" } ?: "none" }.entries
                .sortedWith(compareBy({ it.key == "none" }, { -it.value.size }))
                .map { (k, v) ->
                    when {
                        k == "none" -> TaskGroup(k, "Unassigned", {}, v)
                        k.startsWith("user:") -> TaskGroup(k, AppState.userName(k.removePrefix("user:")), { Avatar(AppState.userName(k.removePrefix("user:")), null, 18.dp, isAgent = false) }, v)
                        else -> TaskGroup(k, AppState.agentName(k), { AgentAvatar(k, 18.dp) }, v)
                    }
                }
            "project" -> m.matched.groupBy { it.projectId ?: "none" }.entries
                .sortedWith(compareBy({ it.key == "none" }, { -it.value.size }))
                .map { (k, v) -> TaskGroup(k, if (k == "none") "No project" else AppState.project(k)?.name ?: "Project", {}, v) }
            "parent" -> m.matched.groupBy { it.parentId ?: "none" }.entries
                .sortedWith(compareBy({ it.key == "none" }, { -it.value.size }))
                .map { (k, v) ->
                    val p = m.graph.byId[k]
                    TaskGroup(k, if (p == null) "Top level" else (p.identifier?.let { "$it · " } ?: "") + p.title, { if (p != null) StatusIcon(p.status, 14.dp) }, v)
                }
            else -> listOf(TaskGroup("all", "", {}, m.matched))
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        if (m.matched.isEmpty()) item { EmptyState("No tasks match these filters") }
        groups.forEach { g ->
            val isCollapsed = collapsedGroups[g.key] == true
            if (g.label.isNotEmpty()) stickyHeader(key = "h-${g.key}", contentType = "group-header") {
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                        .clickable { collapsedGroups[g.key] = !isCollapsed }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (isCollapsed) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.Default.KeyboardArrowDown, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    g.leading()
                    Spacer(Modifier.width(8.dp))
                    Text(g.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(8.dp))
                    Text("${g.items.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!isCollapsed) items(g.items, key = { "${g.key}-${it.id}" }, contentType = { "task" }) { i ->
                IssueRow(m.liveCopy(i), onClick = { nav.issue(i.identifier ?: i.id) }, onLongClick = { onLongPress(i) })
                Divider()
            }
        }
    }
}

// ---------------- tree ----------------

@Composable
internal fun TaskTreeView(
    m: TasksModel,
    nav: Nav,
    focusId: String?,
    onFocus: (String?) -> Unit,
    collapsed: MutableMap<String, Boolean>,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    onLongPress: (Issue) -> Unit,
) {
    key(m.companyId) {
        TaskTreeContent(m, nav, focusId, onFocus, collapsed, dispatcher, onLongPress)
    }
}

@Composable
private fun TaskTreeContent(
    m: TasksModel,
    nav: Nav,
    focusId: String?,
    onFocus: (String?) -> Unit,
    collapsed: MutableMap<String, Boolean>,
    dispatcher: CoroutineDispatcher,
    onLongPress: (Issue) -> Unit,
) {
    val focus = focusId?.let { m.graph.byId[it] }
    var forest by remember { mutableStateOf(m.graph.forest(m.filter, focusId)) }
    LaunchedEffect(m, focusId) {
        forest = withContext(dispatcher) { m.graph.forest(m.filter, focusId) }
    }
    var nextAll by remember { mutableStateOf(m.graph.nextUp(focusId, 200, m.filter)) }
    LaunchedEffect(m, focusId) {
        nextAll = withContext(dispatcher) { m.graph.nextUp(focusId, 200, m.filter) }
    }
    val nextIds = remember(nextAll) { nextAll.mapTo(HashSet()) { it.issue.id } }
    var inFlight by remember {
        val initialFilter = m.filter.copy(statuses = emptySet(), liveOnly = false, blockedOnly = false)
        mutableStateOf(m.graph.inFlight(focusId).filter { initialFilter.matches(it, m.graph.ctx) })
    }
    LaunchedEffect(m, focusId) {
        inFlight = withContext(dispatcher) {
            val f = m.filter.copy(statuses = emptySet(), liveOnly = false, blockedOnly = false)
            m.graph.inFlight(focusId).filter { f.matches(it, m.graph.ctx) }
        }
    }
    fun isCollapsed(n: TaskNode) = collapsed[n.issue.id] ?: (n.children.isNotEmpty() && n.rollup.open == 0 && n.matched)
    val flat by remember(forest, collapsed) { derivedStateOf {
        flattenTree(forest, forest.flatMap { taskAllNodes(it) }.filter { isCollapsed(it) }.mapTo(HashSet()) { it.issue.id })
    } }

    BackHandler(enabled = focus != null) { onFocus(focus?.parentId?.takeIf { m.graph.byId.containsKey(it) }) }
    val listState = rememberLazyListState()
    var previousFocus by rememberSaveable { mutableStateOf(focusId) }
    LaunchedEffect(focusId) {
        if (previousFocus != focusId) {
            listState.scrollToItem(0)
            previousFocus = focusId
        }
    }

    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 96.dp)) {
        if (focus != null) {
            item(key = "crumbs", contentType = "crumbs") { TaskBreadcrumbs(m.graph.ancestors(focus), focus, onFocus) }
            item(key = "focus", contentType = "focus") {
                Surface(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(14.dp))
                        .clickable { nav.issue(focus.identifier ?: focus.id) },
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(focus.ref, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            StatusChip(focus.status)
                            Spacer(Modifier.weight(1f))
                            PriorityIcon(focus.priority)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(focus.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        RollupBar(m.graph.rollup(focus.id))
                    }
                }
            }
        }
        item(key = "next", contentType = "next") {
            NextUpCard(
                nextAll.take(3), inFlight,
                onOpen = { nav.issue(it.identifier ?: it.id) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                title = if (focus != null) "Next up in ${focus.ref}" else "Next up",
            )
        }
        if (flat.isEmpty()) item { EmptyState(if (focus != null) "No sub-tasks match these filters" else "No tasks match these filters") }
        items(flat, key = { "t-" + it.issue.id }, contentType = { "task-tree" }) { n ->
            TaskTreeRow(
                node = n,
                expanded = !isCollapsed(n),
                live = n.issue.id in m.live,
                blocked = m.isBlocked(n.issue),
                isNext = n.issue.id in nextIds,
                onToggle = { collapsed[n.issue.id] = !isCollapsed(n) },
                onOpen = { nav.issue(n.issue.identifier ?: n.issue.id) },
                onLongPress = { onLongPress(n.issue) },
                baseDepth = 0,
            )
        }
        if (focus == null && flat.isNotEmpty()) item {
            Text(
                "Long-press a task for quick edits or to focus its subtree.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun TaskBreadcrumbs(ancestors: List<Issue>, focus: Issue, onFocus: (String?) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("All tasks", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clickable { onFocus(null) })
        ancestors.forEach { a ->
            Text("  ›  ", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(a.ref, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clickable { onFocus(a.id) })
        }
        Text("  ›  ", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(focus.ref, style = MaterialTheme.typography.labelLarge)
    }
}

// ---------------- board ----------------

@Composable
private fun TaskBoardView(m: TasksModel, nav: Nav, onLongPress: (Issue) -> Unit) {
    val columns = remember(m) {
        val wanted = if (m.filter.statuses.isEmpty()) ISSUE_STATUSES.filter { it != "cancelled" } else ISSUE_STATUSES.filter { it in m.filter.statuses }
        wanted.map { s ->
            val items = m.matched.filter { it.status == s }
            s to if (isClosed(s)) items.sortedByDescending { it.completedAt ?: it.updatedAt ?: "" } else items.sortedWith(siblingOrder)
        }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        columns.forEachIndexed { idx, (s, cards) ->
            AppFilterChip(
                selected = listState.firstVisibleItemIndex == idx,
                onClick = { scope.launch { listState.animateScrollToItem(idx) } },
                label = { Text("${statusLabel(s)} ${cards.size}") },
                leadingIcon = { StatusIcon(s, 12.dp) },
            )
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val colWidth = maxWidth * 0.84f
        LazyRow(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(listState),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(columns, key = { it.first }) { (s, cards) ->
                val capped = if (isClosed(s) && cards.size > 40) cards.take(40) else cards
                Surface(
                    Modifier.width(colWidth).fillMaxHeight().padding(vertical = 6.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            StatusIcon(s, 14.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(statusLabel(s), style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.width(8.dp))
                            Text("${cards.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        LazyColumn(
                            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (cards.isEmpty()) item {
                                Text("Nothing here", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
                            }
                            items(capped, key = { it.id }) { i ->
                                BoardCard(
                                    issue = i,
                                    parent = m.graph.byId[i.parentId],
                                    rollup = m.graph.rollup(i.id),
                                    live = i.id in m.live,
                                    blocked = m.isBlocked(i),
                                    onOpen = { nav.issue(i.identifier ?: i.id) },
                                    onLongPress = { onLongPress(i) },
                                )
                            }
                            if (capped.size < cards.size) item {
                                Text("Showing the 40 most recent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
}

// ---------------- quick edit (long-press) ----------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TaskQuickSheet(
    issue: Issue,
    graph: TaskGraph,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onFocus: () -> Unit,
    onStatus: (String) -> Unit,
    onPriority: (String) -> Unit,
    onAssign: (IssueAssignee) -> Unit,
) {
    var assigning by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.statusBarsPadding().padding(top = 12.dp), sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(issue.ref, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(issue.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                val blockers = issue.openBlockers(graph.ctx)
                if (blockers.isNotEmpty()) Text(
                    "Blocked by " + blockers.joinToString { it.identifier ?: it.title ?: "a task" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                val r = graph.rollup(issue.id)
                if (r.total > 0) { Spacer(Modifier.height(6.dp)); RollupBar(r) }
            }
            Text("Move to", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp))
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ISSUE_STATUSES.forEach { s ->
                    AppFilterChip(issue.status == s, { if (issue.status != s) onStatus(s) }, { Text(statusLabel(s)) }, leadingIcon = { StatusIcon(s, 12.dp) })
                }
            }
            Text("Priority", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 6.dp))
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PRIORITIES.forEach { p ->
                    AppFilterChip(issue.priority == p, { if (issue.priority != p) onPriority(p) }, { Text(p.replaceFirstChar { it.uppercase() }) }, leadingIcon = { PriorityIcon(p, 14.dp) })
                }
            }
            Row(
                Modifier.fillMaxWidth().clickable { assigning = true }.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Assignee", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                when {
                    issue.assigneeAgentId != null -> { AgentAvatar(issue.assigneeAgentId, 22.dp); Spacer(Modifier.width(8.dp)); Text(AppState.agentName(issue.assigneeAgentId)) }
                    issue.assigneeUserId != null -> Text(AppState.userName(issue.assigneeUserId))
                    else -> Text("Unassigned", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Divider()
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onFocus, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.AccountTree, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Focus tree")
                }
                Button(onClick = onOpen, modifier = Modifier.weight(1f)) { Text("Open task") }
            }
        }
    }
    if (assigning) IssueAssigneeSheet(issue.assigneeAgentId, issue.assigneeUserId, onPick = { onAssign(it); assigning = false }, onDismiss = { assigning = false })
}

// ---------------- full filter sheet ----------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TaskFilterSheet(f: TaskFilter, onChange: (TaskFilter) -> Unit, onDismiss: () -> Unit, showGroup: Boolean) {
    val agents by AppState.agents.collectAsState()
    val projects by AppState.projects.collectAsState()
    var labels by remember { mutableStateOf<List<Label>>(emptyList()) }
    LaunchedEffect(Unit) { labels = catching { Repo.labels(AppState.cid) }.getOrDefault(emptyList()) }
    fun <T> Set<T>.toggle(v: T) = if (v in this) this - v else this + v

    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.statusBarsPadding().padding(top = 12.dp), sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Filters", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { onChange(TaskFilter(sort = f.sort, groupBy = f.groupBy)) }) { Text("Reset") }
            }
            TaskSheetSection("Status")
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ISSUE_STATUSES.forEach { s ->
                    AppFilterChip(s in f.statuses, { onChange(f.copy(statuses = f.statuses.toggle(s))) }, { Text(statusLabel(s)) }, leadingIcon = { StatusIcon(s, 12.dp) })
                }
            }
            TaskSheetSection("Priority")
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PRIORITIES.forEach { p ->
                    AppFilterChip(p in f.priorities, { onChange(f.copy(priorities = f.priorities.toggle(p))) }, { Text(p.replaceFirstChar { it.uppercase() }) }, leadingIcon = { PriorityIcon(p, 14.dp) })
                }
            }
            TaskSheetSection("Assignee")
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AppFilterChip("me" in f.assignees, { onChange(f.copy(assignees = f.assignees.toggle("me"))) }, { Text("Me") })
                AppFilterChip("unassigned" in f.assignees, { onChange(f.copy(assignees = f.assignees.toggle("unassigned"))) }, { Text("Unassigned") })
                agents.sortedBy { it.name }.forEach { a ->
                    AppFilterChip(a.id in f.assignees, { onChange(f.copy(assignees = f.assignees.toggle(a.id))) }, { Text(a.name) }, leadingIcon = { AgentAvatar(a.id, 18.dp) }, keepIcon = true)
                }
            }
            if (projects.isNotEmpty()) {
                TaskSheetSection("Project")
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppFilterChip("none" in f.projects, { onChange(f.copy(projects = f.projects.toggle("none"))) }, { Text("No project") })
                    projects.filter { it.archivedAt == null }.forEach { p ->
                        AppFilterChip(p.id in f.projects, { onChange(f.copy(projects = f.projects.toggle(p.id))) }, { Text(p.name) })
                    }
                }
            }
            if (labels.isNotEmpty()) {
                TaskSheetSection("Labels")
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    labels.forEach { l ->
                        AppFilterChip(
                            l.id in f.labels, { onChange(f.copy(labels = f.labels.toggle(l.id))) }, { Text(l.name) },
                            leadingIcon = { Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(parseIssueColor(l.color) ?: MaterialTheme.colorScheme.outline)) },
                            keepIcon = true,
                        )
                    }
                }
            }
            TaskSheetSection("Show")
            TaskToggleRow("Live now", "Only tasks with an agent run in progress", f.liveOnly) { onChange(f.copy(liveOnly = it)) }
            TaskToggleRow("Blocked", "Blocked status, or waiting on an open blocker", f.blockedOnly) { onChange(f.copy(blockedOnly = it)) }
            TaskToggleRow("Hide routine runs", "Tasks created by scheduled routines", f.hideRoutine) { onChange(f.copy(hideRoutine = it)) }
            TaskSheetSection("Sort")
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("updated" to "Recently active", "priority" to "Priority", "created" to "Newest", "number" to "Number").forEach { (k, label) ->
                    AppFilterChip(f.sort == k, { onChange(f.copy(sort = k)) }, { Text(label) })
                }
            }
            if (showGroup) {
                TaskSheetSection("Group list by")
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("status" to "Status", "priority" to "Priority", "assignee" to "Assignee", "project" to "Project", "parent" to "Parent", "none" to "None").forEach { (k, label) ->
                        AppFilterChip(f.groupBy == k, { onChange(f.copy(groupBy = k)) }, { Text(label) })
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Text("Done") }
        }
    }
}

@Composable
private fun TaskSheetSection(title: String) =
    Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp))

@Composable
private fun TaskToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}

@Composable
private fun TaskViewSwitcher(view: TaskView, onPick: (TaskView) -> Unit) {
    val icons = mapOf(TaskView.List to Icons.AutoMirrored.Filled.ViewList, TaskView.Tree to Icons.Outlined.AccountTree, TaskView.Board to Icons.Outlined.ViewColumn)
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TaskView.entries.forEach { v ->
            val sel = v == view
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(50))
                    .background(if (sel) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onPick(v) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(icons.getValue(v), v.label, Modifier.size(18.dp), tint = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
