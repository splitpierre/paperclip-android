package com.bu.paperclip.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bu.paperclip.data.AppState
import com.bu.paperclip.data.Goal
import com.bu.paperclip.data.Issue
import com.bu.paperclip.data.Project
import com.bu.paperclip.data.Repo
import com.bu.paperclip.data.Routine
import com.bu.paperclip.data.jsonOf
import com.bu.paperclip.net.Api
import com.bu.paperclip.ui.Nav
import com.bu.paperclip.ui.components.AgentAvatar
import com.bu.paperclip.ui.components.Dot
import com.bu.paperclip.ui.components.EmptyState
import com.bu.paperclip.ui.components.IssueRow
import com.bu.paperclip.ui.components.LiveRefresh
import com.bu.paperclip.ui.components.LoadContent
import com.bu.paperclip.ui.components.Markdown
import com.bu.paperclip.ui.components.Pill
import com.bu.paperclip.ui.components.SectionHeader
import com.bu.paperclip.ui.components.rememberAction
import com.bu.paperclip.ui.components.rememberLoader
import com.bu.paperclip.ui.components.rememberMinuteTick
import com.bu.paperclip.ui.components.statusLabel
import com.bu.paperclip.util.relativeTime
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

private const val ADMIN_ACTIVE_ISSUE_STATUSES = "backlog,todo,in_progress,in_review,blocked"

// ---------------- Projects ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(nav: Nav) {
    val company by AppState.company.collectAsState()
    val cid = company?.id ?: ""
    val loader = rememberLoader(cid) { Repo.projects(cid).also { AppState.projects.value = it } }
    LiveRefresh(filter = { it.type == "activity.logged" && it.s("entityType") == "project" }) { loader.refresh() }

    Scaffold(topBar = { TopAppBar(title = { Text("Projects") }, navigationIcon = { BackIcon { nav.back() } }) }) { pad ->
        LoadContent(loader, Modifier.padding(pad)) { list ->
            val visible = list.filter { it.archivedAt == null }
                .sortedWith(compareBy<Project> { adminProjectStatusRank(it.status) }.thenByDescending { it.updatedAt })
            LazyColumn(Modifier.fillMaxSize()) {
                if (visible.isEmpty()) item { EmptyState("No projects yet.", Icons.Outlined.FolderOpen) }
                items(visible, key = { it.id }) { p -> ProjectRow(p) { nav.project(p.id) } }
            }
        }
    }
}

private fun adminProjectStatusRank(s: String) = when (s) {
    "in_progress" -> 0
    "planned" -> 1
    "backlog" -> 2
    "completed" -> 3
    else -> 4
}

@Composable
private fun ProjectRow(p: Project, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(adminParseColor(p.color, MaterialTheme.colorScheme.onSurfaceVariant), 12.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.bodyLarge)
            val sub = listOfNotNull(
                p.taskCount?.let { "$it tasks" },
                p.targetDate?.take(10)?.let { "target $it" },
                AppState.agent(p.leadAgentId)?.name?.let { "lead $it" },
            ).joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Pill(statusLabel(p.status), adminStatusColor(p.status))
    }
}

private data class ProjectBundle(val project: Project, val issues: List<Issue>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(nav: Nav, id: String) {
    val company by AppState.company.collectAsState()
    val cid = company?.id ?: ""
    var seg by rememberSaveable { mutableIntStateOf(0) }
    val loader = rememberLoader(id, cid, seg) {
        coroutineScope {
            val project = Api.get<Project>("/projects/$id", mapOf("companyId" to cid.ifEmpty { null }))
            val issues = async {
                Repo.issues(cid, projectId = project.id, status = if (seg == 0) ADMIN_ACTIVE_ISSUE_STATUSES else null, limit = 200)
            }
            ProjectBundle(project, issues.await())
        }
    }
    LiveRefresh(filter = { it.type == "activity.logged" && (it.s("entityType") == "issue" || it.s("entityType") == "project") }) { loader.refresh() }
    var expanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(loader.value?.project?.name ?: "Project", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackIcon { nav.back() } },
            )
        },
        floatingActionButton = {
            loader.value?.project?.let { p ->
                ExtendedFloatingActionButton(
                    onClick = { nav.newIssue(projectId = p.id) },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("New task") },
                )
            }
        },
    ) { pad ->
        LoadContent(loader, Modifier.padding(pad)) { b ->
            val p = b.project
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Dot(adminParseColor(p.color, MaterialTheme.colorScheme.onSurfaceVariant), 12.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(p.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                            Pill(statusLabel(p.status), adminStatusColor(p.status))
                        }
                        val lead = AppState.agent(p.leadAgentId)
                        if (lead != null) {
                            Spacer(Modifier.height(10.dp))
                            Row(Modifier.clickable { nav.agent(lead.id) }, verticalAlignment = Alignment.CenterVertically) {
                                AgentAvatar(lead.id, 24.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Lead: ${lead.name}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        if (p.targetDate != null) {
                            Spacer(Modifier.height(6.dp))
                            Text("Target ${p.targetDate.take(10)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!p.description.isNullOrBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Column(Modifier.animateContentSize()) {
                                val long = p.description.length > 400
                                Markdown(if (long && !expanded) p.description.take(400) + "…" else p.description)
                                if (long) {
                                    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Show less" else "Show more") }
                                }
                            }
                        }
                    }
                }
                item {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        listOf("Active", "All").forEachIndexed { i, label ->
                            SegmentedButton(selected = seg == i, onClick = { seg = i }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) }
                        }
                    }
                }
                if (b.issues.isEmpty()) item { EmptyState(if (seg == 0) "No active tasks in this project." else "No tasks in this project yet.") }
                items(b.issues, key = { it.id }, contentType = { "task" }) { issue -> IssueRow(issue, onClick = { nav.issue(issue.identifier ?: issue.id) }, showProject = false) }
                item { Spacer(Modifier.height(88.dp)) }
            }
        }
    }
}

// ---------------- Goals ----------------

private val goalLevels = listOf("company", "team", "agent", "task")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(nav: Nav) {
    val company by AppState.company.collectAsState()
    val cid = company?.id ?: ""
    val loader = rememberLoader(cid) { Repo.goals(cid) }
    LiveRefresh(filter = { it.type == "activity.logged" && it.s("entityType") == "goal" }) { loader.refresh() }

    Scaffold(topBar = { TopAppBar(title = { Text("Goals") }, navigationIcon = { BackIcon { nav.back() } }) }) { pad ->
        LoadContent(loader, Modifier.padding(pad)) { goals ->
            val grouped = goals.groupBy { it.level }
            val order = goalLevels + grouped.keys.filter { it !in goalLevels }
            LazyColumn(Modifier.fillMaxSize()) {
                if (goals.isEmpty()) item { EmptyState("No goals yet.", Icons.Outlined.Flag) }
                order.forEach { level ->
                    val list = grouped[level].orEmpty()
                    if (list.isNotEmpty()) {
                        item(key = "h-$level") { SectionHeader("${level.replaceFirstChar { it.uppercase() }} goals") }
                        items(list.sortedBy { adminGoalRank(it.status) }, key = { it.id }, contentType = { "goal" }) { g -> GoalRow(nav, g) }
                    }
                }
            }
        }
    }
}

private fun adminGoalRank(s: String) = when (s) {
    "active" -> 0
    "planned" -> 1
    "achieved" -> 2
    else -> 3
}

@Composable
private fun GoalRow(nav: Nav, g: Goal) {
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clickable(enabled = !g.description.isNullOrBlank()) { open = !open }
            .padding(horizontal = 16.dp, vertical = 12.dp).animateContentSize(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(g.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Pill(statusLabel(g.status), adminStatusColor(g.status))
        }
        val owner = AppState.agent(g.ownerAgentId)
        if (owner != null) {
            Row(Modifier.padding(top = 6.dp).clickable { nav.agent(owner.id) }, verticalAlignment = Alignment.CenterVertically) {
                AgentAvatar(owner.id, 20.dp)
                Spacer(Modifier.width(6.dp))
                Text(owner.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (open && !g.description.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Markdown(g.description)
        } else if (!g.description.isNullOrBlank()) {
            Text(
                g.description.lineSequence().first(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------------- Routines ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutinesScreen(nav: Nav) {
    val company by AppState.company.collectAsState()
    val cid = company?.id ?: ""
    val loader = rememberLoader(cid) { Repo.routines(cid) }
    val act = rememberAction()
    var confirmRun by remember { mutableStateOf<Routine?>(null) }
    LiveRefresh(filter = { it.type == "activity.logged" && (it.s("entityType")?.startsWith("routine") == true) }) { loader.refresh() }

    Scaffold(topBar = { TopAppBar(title = { Text("Routines") }, navigationIcon = { BackIcon { nav.back() } }) }) { pad ->
        LoadContent(loader, Modifier.padding(pad)) { routines ->
            val list = routines.filter { it.status != "archived" }
                .sortedWith(compareBy<Routine> { if (it.status == "active") 0 else 1 }.thenBy { it.title.lowercase() })
            LazyColumn(Modifier.fillMaxSize()) {
                if (list.isEmpty()) item { EmptyState("No routines yet.", Icons.Outlined.Repeat) }
                items(list, key = { it.id }) { r ->
                    RoutineRow(
                        nav = nav,
                        r = r,
                        onRun = { confirmRun = r },
                        onToggle = {
                            val next = if (r.status == "active") "paused" else "active"
                            loader.set(routines.map { if (it.id == r.id) it.copy(status = next) else it })
                            act({
                                try {
                                    Repo.updateRoutine(r.id, jsonOf("status" to next))
                                } finally {
                                    loader.refresh()
                                }
                            }, if (next == "paused") "Paused ${r.title}" else "Resumed ${r.title}")
                        },
                    )
                }
            }
        }
    }

    confirmRun?.let { r ->
        AlertDialog(
            onDismissRequest = { confirmRun = null },
            title = { Text("Run now?") },
            text = { Text("Start \"${r.title}\" now${AppState.agent(r.assigneeAgentId)?.let { " — it will be assigned to ${it.name}" } ?: ""}.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRun = null
                    act({ Repo.runRoutine(r.id); loader.refresh() }, "Started ${r.title}")
                }) { Text("Run") }
            },
            dismissButton = { TextButton(onClick = { confirmRun = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RoutineRow(nav: Nav, r: Routine, onRun: () -> Unit, onToggle: () -> Unit) {
    rememberMinuteTick()
    Column(Modifier.fillMaxWidth().clickable { nav.routine(r.id) }.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Pill(statusLabel(r.status), adminStatusColor(r.status))
        }
        val agent = AppState.agent(r.assigneeAgentId)
        if (agent != null) {
            Row(Modifier.padding(top = 6.dp).clickable { nav.agent(agent.id) }, verticalAlignment = Alignment.CenterVertically) {
                AgentAvatar(agent.id, 20.dp)
                Spacer(Modifier.width(6.dp))
                Text(agent.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        r.triggers.orEmpty().filter { it.kind == "schedule" }.forEach { t ->
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Schedule, null, Modifier.padding(end = 6.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    buildString {
                        append(t.label ?: t.cronExpression ?: "Schedule")
                        if (t.label != null && t.cronExpression != null) append(" (${t.cronExpression})")
                        if (!t.enabled) append(" · disabled")
                        else if (t.nextRunAt != null) append(" · next ${relativeTime(t.nextRunAt)}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val other = r.triggers.orEmpty().filter { it.kind != "schedule" }.map { it.kind }.distinct()
        if (other.isNotEmpty()) {
            Text("Also triggered by ${other.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
        val last = r.lastRun
        if (last?.createdAt != null || r.lastTriggeredAt != null) {
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Last run ${relativeTime(last?.createdAt ?: r.lastTriggeredAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (last?.status != null) {
                    Spacer(Modifier.width(6.dp))
                    Pill(statusLabel(last.status), adminStatusColor(last.status))
                }
            }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onRun) {
                Icon(Icons.Outlined.PlayArrow, null)
                Spacer(Modifier.width(4.dp))
                Text("Run now")
            }
            OutlinedButton(onClick = onToggle) {
                Icon(if (r.status == "active") Icons.Outlined.Pause else Icons.Outlined.PlayArrow, null)
                Spacer(Modifier.width(4.dp))
                Text(if (r.status == "active") "Pause" else "Resume")
            }
        }
    }
}
