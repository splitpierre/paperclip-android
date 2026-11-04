package com.bu.paperclip.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.outlined.Commit
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.automirrored.outlined.MergeType
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.bu.paperclip.data.openBlockers
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bu.paperclip.data.ActivityEvent
import com.bu.paperclip.data.AppState
import com.bu.paperclip.data.Attachment
import com.bu.paperclip.data.Issue
import com.bu.paperclip.data.IssueDocument
import com.bu.paperclip.data.WorkProduct
import com.bu.paperclip.net.Api
import com.bu.paperclip.net.ServerFiles
import com.bu.paperclip.ui.Nav
import com.bu.paperclip.ui.components.AgentAvatar
import com.bu.paperclip.ui.components.Avatar
import com.bu.paperclip.ui.components.Card
import com.bu.paperclip.ui.components.Divider
import com.bu.paperclip.ui.components.EmptyState
import com.bu.paperclip.ui.components.IssueRow
import com.bu.paperclip.ui.components.FileOpener
import com.bu.paperclip.ui.components.FileView
import com.bu.paperclip.ui.components.Links
import com.bu.paperclip.ui.components.Pill
import com.bu.paperclip.ui.components.SectionHeader
import com.bu.paperclip.ui.components.activityActor
import com.bu.paperclip.ui.components.activityLink
import com.bu.paperclip.ui.components.activitySentence
import com.bu.paperclip.ui.components.runStatusColor
import com.bu.paperclip.ui.components.statusLabel
import com.bu.paperclip.ui.theme.LocalStatusColors
import com.bu.paperclip.util.durationLabel
import com.bu.paperclip.util.relativeTime
import kotlinx.serialization.json.JsonObject

private val ISSUE_SYSTEM_DOC_KEYS = setOf("continuation-summary", "pipeline-case-body")

@Composable
internal fun IssueDocsTab(issue: Issue, docs: List<IssueDocument>?, nav: Nav) {
    val list = docs ?: issue.documentSummaries?.map { IssueDocument(id = it.id, key = it.key, title = it.title, latestRevisionNumber = it.latestRevisionNumber, updatedAt = it.updatedAt) }
    if (list == null) { IssueTabLoading(); return }
    if (list.isEmpty()) { EmptyState("No documents on this task", Icons.Outlined.Description); return }
    val sorted = list.sortedWith(compareBy<IssueDocument>({ it.key in ISSUE_SYSTEM_DOC_KEYS }, { it.key != "plan" }, { it.key }))
    Column {
        sorted.forEach { d ->
            Row(
                Modifier.fillMaxWidth().clickable { nav.document(issue.id, d.key) }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Description, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.title?.takeIf { it.isNotBlank() } ?: d.key, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        listOfNotNull(d.key, d.latestRevisionNumber?.let { "rev $it" }, relativeTime(d.updatedAt).ifEmpty { null }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (d.key in ISSUE_SYSTEM_DOC_KEYS) Pill("system", LocalStatusColors.current.gray)
            }
            Divider()
        }
    }
}

@Composable
internal fun IssueSubtasksTab(issue: Issue, children: List<Issue>?, nav: Nav) {
    val all by com.bu.paperclip.data.IssueStore.issues.collectAsState()
    val live by com.bu.paperclip.data.IssueStore.liveIssueIds.collectAsState()
    val me by AppState.me.collectAsState()
    var showClosed by remember(issue.id) { mutableStateOf(false) }
    val collapsed = remember(issue.id) { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    val graph = remember(all, live, me) {
        com.bu.paperclip.data.TaskGraph(all.values, com.bu.paperclip.data.MatchCtx(me?.id, live) { all[it]?.status })
    }
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { nav.newIssue(parentId = issue.id, projectId = issue.projectId) }) {
                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("New sub-task")
            }
            Spacer(Modifier.weight(1f))
            Text("Show closed", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            androidx.compose.material3.Switch(showClosed, { showClosed = it })
        }
        if (!all.containsKey(issue.id)) {
            // Mirror still loading: plain list of direct children.
            when {
                children == null -> IssueTabLoading()
                children.isEmpty() -> EmptyState("No sub-tasks")
                else -> children.forEach { c -> IssueRow(c, onClick = { nav.issue(c.identifier ?: c.id) }, showProject = false); Divider() }
            }
            return@Column
        }
        val rollup = graph.rollup(issue.id)
        if (rollup.total == 0) { EmptyState("No sub-tasks"); return@Column }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            com.bu.paperclip.ui.components.RollupBar(rollup)
            Text(
                "${rollup.open} open · ${rollup.inFlight} in flight · ${rollup.blocked} blocked · ${rollup.closed} closed",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        val next = graph.nextUp(issue.id, 50)
        com.bu.paperclip.ui.components.NextUpCard(
            next.take(3), graph.inFlight(issue.id),
            onOpen = { nav.issue(it.identifier ?: it.id) },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
        val filter = com.bu.paperclip.data.TaskFilter(
            statuses = if (showClosed) emptySet() else com.bu.paperclip.data.ACTIVE_STATUSES,
            sort = "plan",
        )
        val forest = graph.forest(filter, issue.id)
        if (forest.isEmpty()) {
            Text(
                "All ${rollup.total} sub-tasks are closed.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            return@Column
        }
        val nextIds = next.mapTo(HashSet()) { it.issue.id }
        fun isCollapsed(n: com.bu.paperclip.data.TaskNode) = collapsed[n.issue.id] ?: false
        fun walk(n: com.bu.paperclip.data.TaskNode): List<com.bu.paperclip.data.TaskNode> = listOf(n) + n.children.flatMap { walk(it) }
        val flat = com.bu.paperclip.ui.components.flattenTree(forest, forest.flatMap { walk(it) }.filter { isCollapsed(it) }.mapTo(HashSet()) { it.issue.id })
        flat.forEach { n ->
            com.bu.paperclip.ui.components.TaskTreeRow(
                node = n,
                expanded = !isCollapsed(n),
                live = n.issue.id in live,
                blocked = !com.bu.paperclip.data.isClosed(n.issue.status) &&
                    (n.issue.status == "blocked" || with(com.bu.paperclip.data.MatchCtx(me?.id, live) { all[it]?.status }) { n.issue.openBlockers(this).isNotEmpty() }),
                isNext = n.issue.id in nextIds,
                onToggle = { collapsed[n.issue.id] = !isCollapsed(n) },
                onOpen = { nav.issue(n.issue.identifier ?: n.issue.id) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IssueFilesTab(attachments: List<Attachment>?, workProducts: List<WorkProduct>?) {
    val opening by FileOpener.view.collectAsState()
    val openingUrl = (opening as? FileView.Loading)?.url
    Column {
        SectionHeader("Output")
        when {
            workProducts == null -> IssueTabLoading()
            workProducts.isEmpty() -> Text("Nothing published yet", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            else -> Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                workProducts.sortedByDescending { it.isPrimary }.forEach { IssueWorkProductCard(it) }
            }
        }
        SectionHeader("Attachments")
        when {
            attachments == null -> IssueTabLoading()
            attachments.isEmpty() -> Text("No attachments", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            else -> {
                // Some attachments have no content type; the file name decides then.
                val (images, files) = attachments.partition { ServerFiles.isImage(ServerFiles.mimeType(it.originalFilename, it.contentType)) }
                if (images.isNotEmpty()) {
                    FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        images.forEach { a ->
                            AsyncImage(
                                model = a.contentUrl(),
                                contentDescription = a.originalFilename,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(104.dp).clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable { a.open() },
                            )
                        }
                    }
                }
                files.forEach { a ->
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = openingUrl == null) { a.open() }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.InsertDriveFile, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.originalFilename ?: "file", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(a.contentType, a.byteSize?.let { issueSize(it) }, relativeTime(a.createdAt)).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (openingUrl == a.contentUrl()) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}

private fun Attachment.contentUrl(): String = Api.absolute(contentPath ?: "/api/attachments/$id/content")

/** Downloads with the session (the browser has none) and shows it in the app or hands it to another app. */
private fun Attachment.open() = FileOpener.open(contentUrl(), originalFilename, contentType)

private fun issueSize(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "${b / 1024} KB"
    else -> String.format("%.1f MB", b / 1048576.0)
}

@Composable
private fun IssueWorkProductCard(wp: WorkProduct) {
    val ctx = LocalContext.current
    val c = LocalStatusColors.current
    val icon = when (wp.type) {
        "pull_request" -> Icons.AutoMirrored.Outlined.MergeType
        "branch" -> Icons.AutoMirrored.Outlined.CallSplit
        "commit" -> Icons.Outlined.Commit
        "preview_url" -> Icons.Outlined.Language
        "runtime_service" -> Icons.Outlined.Dns
        else -> Icons.Outlined.Description
    }
    val meta = wp.metadata
    Card(modifier = Modifier.clickable(enabled = wp.url != null) {
        wp.url?.let { u -> if (!Links.handler(u)) runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Api.absolute(u)))) } }
    }, padding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(wp.title ?: wp.url ?: wp.type, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val sub = buildList {
                    add(wp.type.replace('_', ' '))
                    meta.issueStr("repo")?.let { r -> add(r + (meta.issueInt("number")?.let { n -> " #$n" } ?: "")) }
                    meta.issueStr("headRef")?.let { h -> add(h) }
                    val adds = meta.issueInt("additions"); val dels = meta.issueInt("deletions")
                    if (adds != null || dels != null) add("+${adds ?: 0} −${dels ?: 0}")
                }.joinToString(" · ")
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                wp.summary?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            }
            wp.status?.let { s ->
                val col = when (s) { "merged", "approved" -> c.violet; "active", "ready_for_review" -> c.green; "changes_requested", "failed" -> c.red; else -> c.gray }
                Pill(statusLabel(s), col)
            }
        }
    }
}

@Composable
internal fun IssueActivityTab(events: List<ActivityEvent>?, runs: List<JsonObject>?, nav: Nav) {
    Column {
        SectionHeader("Runs")
        when {
            runs == null -> IssueTabLoading()
            runs.isEmpty() -> Text("No runs yet", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            else -> runs.take(30).forEach { r ->
                val runId = r.issueStr("runId") ?: r.issueStr("id") ?: return@forEach
                val status = r.issueStr("status") ?: ""
                Row(
                    Modifier.fillMaxWidth().clickable { nav.run(runId) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AgentAvatar(r.issueStr("agentId"), 26.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(AppState.agentName(r.issueStr("agentId")), style = MaterialTheme.typography.titleSmall)
                        Text(
                            listOfNotNull(r.issueStr("invocationSource"), durationLabel(r.issueStr("startedAt"), r.issueStr("finishedAt")).ifEmpty { null }).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Pill(statusLabel(status), runStatusColor(status))
                    Spacer(Modifier.width(8.dp))
                    Text(relativeTime(r.issueStr("startedAt") ?: r.issueStr("createdAt")), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        SectionHeader("Activity")
        when {
            events == null -> IssueTabLoading()
            events.isEmpty() -> Text("No activity", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            else -> events.forEach { e ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        val r = e.runId
                        if (r != null) nav.run(r) else activityLink(e)?.let { l -> if (e.entityType != "issue") Links.handler(l) }
                    }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (e.actorType == "agent") AgentAvatar(e.actorId ?: e.agentId, 22.dp) else Avatar(activityActor(e), null, 22.dp, isAgent = false)
                    Spacer(Modifier.width(10.dp))
                    Text(activitySentence(e), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text(relativeTime(e.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun IssueTabLoading() {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
    }
}
