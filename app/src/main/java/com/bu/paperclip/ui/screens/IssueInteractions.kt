package com.bu.paperclip.ui.screens

import com.bu.paperclip.util.catching

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.automirrored.outlined.PlaylistAddCheck
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bu.paperclip.data.AppState
import com.bu.paperclip.data.Interaction
import com.bu.paperclip.data.Repo
import com.bu.paperclip.data.jsonOf
import com.bu.paperclip.ui.Nav
import com.bu.paperclip.ui.components.AgentAvatar
import com.bu.paperclip.ui.components.Links
import com.bu.paperclip.ui.components.Markdown
import com.bu.paperclip.ui.components.Pill
import com.bu.paperclip.ui.components.PriorityIcon
import com.bu.paperclip.ui.components.errorMessage
import com.bu.paperclip.ui.theme.LocalStatusColors
import com.bu.paperclip.util.relativeTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

private const val ISSUE_OTHER_ID = "__paperclip_other__"

private fun issueInteractionLabel(kind: String): String = when (kind) {
    "ask_user_questions" -> "Questions"
    "request_confirmation" -> "Confirmation"
    "request_checkbox_confirmation" -> "Choose options"
    "suggest_tasks" -> "Suggested tasks"
    "request_item_verdicts" -> "Review items"
    "connection_intent" -> "Connection request"
    else -> kind.replace('_', ' ')
}

private fun issueInteractionIcon(kind: String) = when (kind) {
    "ask_user_questions" -> Icons.AutoMirrored.Outlined.HelpOutline
    "request_confirmation" -> Icons.Outlined.VerifiedUser
    "request_checkbox_confirmation" -> Icons.Outlined.Checklist
    "suggest_tasks" -> Icons.AutoMirrored.Outlined.PlaylistAddCheck
    "request_item_verdicts" -> Icons.AutoMirrored.Outlined.Rule
    "connection_intent" -> Icons.Outlined.Link
    else -> Icons.Outlined.TaskAlt
}

@Composable
private fun issueInteractionStatusColor(status: String): Color {
    val c = LocalStatusColors.current
    return when (status) {
        "pending" -> c.yellow
        "accepted", "answered" -> c.green
        "rejected", "failed" -> c.red
        else -> c.gray
    }
}

/** Shared state + runner for one card's mutation buttons. */
private class IssueCardWork {
    var working by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
}

@Composable
private fun rememberIssueRunner(onUpdated: (Interaction) -> Unit): Pair<IssueCardWork, (suspend () -> Interaction) -> Unit> {
    val scope = rememberCoroutineScope()
    val work = remember { IssueCardWork() }
    val run: (suspend () -> Interaction) -> Unit = { block ->
        if (!work.working) {
            work.working = true
            work.error = null
            scope.launch {
                try {
                    onUpdated(block())
                    AppState.refreshBadges()
                } catch (c: CancellationException) {
                    throw c
                } catch (t: Throwable) {
                    work.error = errorMessage(t)
                } finally {
                    work.working = false
                }
            }
        }
    }
    return work to run
}

/** One interaction card (agent question / confirmation / suggestion…), actionable while pending. */
@Composable
internal fun IssueInteractionCard(issueId: String, interaction: Interaction, nav: Nav, onUpdated: (Interaction) -> Unit, modifier: Modifier = Modifier) {
    val pending = interaction.status == "pending"
    val (work, run) = rememberIssueRunner(onUpdated)
    var menu by remember { mutableStateOf(false) }
    val accent = issueInteractionStatusColor(interaction.status)
    Surface(
        modifier = modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(14.dp),
        color = if (pending) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(if (pending) 1.5.dp else 1.dp, if (pending) accent.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(issueInteractionIcon(interaction.kind), null, Modifier.size(18.dp), tint = accent)
                Spacer(Modifier.width(8.dp))
                Text(issueInteractionLabel(interaction.kind).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Pill(if (pending) "Waiting for you" else interaction.status.replace('_', ' '), accent)
                Spacer(Modifier.weight(1f))
                Text(relativeTime(interaction.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (pending) Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Skip") }, onClick = { menu = false; run { issueSkipInteraction(issueId, interaction.id) } })
                        DropdownMenuItem(text = { Text("Cancel request") }, onClick = { menu = false; run { issueCancelInteraction(issueId, interaction.id) } })
                    }
                }
            }
            if (interaction.createdByAgentId != null) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AgentAvatar(interaction.createdByAgentId, 18.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("From ${AppState.agentName(interaction.createdByAgentId)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            interaction.title?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.titleMedium)
            }
            interaction.summary?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Markdown(it, fontSizeSp = 14f)
            }
            Spacer(Modifier.height(8.dp))
            when (interaction.kind) {
                "ask_user_questions" -> IssueQuestionsBody(issueId, interaction, pending, work, run)
                "request_confirmation" -> IssueConfirmationBody(issueId, interaction, pending, work, run, nav)
                "request_checkbox_confirmation" -> IssueCheckboxBody(issueId, interaction, pending, work, run)
                "suggest_tasks" -> IssueSuggestTasksBody(issueId, interaction, pending, work, run, nav)
                "request_item_verdicts" -> IssueVerdictsBody(issueId, interaction, pending, work, run)
                "connection_intent" -> IssueConnectionBody(issueId, interaction, pending, work, run)
                else -> Text("This request type isn't supported in the app yet; open it in the web UI.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!pending) IssueResolvedFooter(interaction)
            work.error?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun IssueResolvedFooter(interaction: Interaction) {
    val r = interaction.result
    val outcome = r.issueStr("outcome")
    val reason = r.issueStr("reason") ?: r.issueStr("rejectionReason") ?: r.issueStr("cancellationReason")
    val who = when {
        interaction.resolvedByUserId != null -> AppState.userName(interaction.resolvedByUserId)
        interaction.resolvedByAgentId != null -> AppState.agentName(interaction.resolvedByAgentId)
        else -> null
    }
    val parts = listOfNotNull(
        outcome?.replace('_', ' ')?.replaceFirstChar { it.uppercase() },
        who?.let { "by $it" },
        interaction.resolvedAt?.let { relativeTime(it) },
    )
    if (parts.isNotEmpty() || reason != null) {
        Spacer(Modifier.height(8.dp))
        if (parts.isNotEmpty()) Text(parts.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        reason?.takeIf { it.isNotBlank() }?.let { Text("“$it”", style = MaterialTheme.typography.bodySmall) }
    }
}

// ---------- Reject row shared by confirmations ----------

@Composable
private fun IssueAcceptRejectRow(
    payload: JsonObject?,
    work: IssueCardWork,
    acceptEnabled: Boolean = true,
    acceptDefault: String = "Approve",
    extraActions: @Composable () -> Unit = {},
    onAccept: () -> Unit,
    onReject: (String?) -> Unit,
) {
    var rejecting by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    val allowReason = payload.issueBool("allowDeclineReason") != false
    val reasonRequired = payload.issueBool("rejectRequiresReason") == true
    val rejectLabel = payload.issueStr("rejectLabel") ?: "Request changes"
    if (rejecting && (allowReason || reasonRequired)) {
        OutlinedTextField(
            value = reason,
            onValueChange = { reason = it },
            label = { Text(payload.issueStr("rejectReasonLabel") ?: if (reasonRequired) "Reason (required)" else "Reason (optional)") },
            placeholder = { payload.issueStr("declineReasonPlaceholder")?.let { Text(it) } },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { onReject(reason.trim().ifEmpty { null }) },
                enabled = !work.working && (!reasonRequired || reason.isNotBlank()),
            ) { Text(rejectLabel) }
            TextButton(onClick = { rejecting = false }) { Text("Back") }
            if (work.working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
    } else {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onAccept, enabled = acceptEnabled && !work.working) { Text(payload.issueStr("acceptLabel") ?: acceptDefault) }
            OutlinedButton(
                onClick = { if (allowReason || reasonRequired) rejecting = true else onReject(null) },
                enabled = !work.working,
            ) { Text(rejectLabel) }
            extraActions()
            if (work.working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun IssueCollapsibleMarkdown(text: String, label: String = "Details") {
    var open by remember { mutableStateOf(text.length < 500) }
    if (open) {
        Markdown(text, fontSizeSp = 14f)
        if (text.length >= 500) TextButton(onClick = { open = false }) { Text("Hide $label".lowercase().replaceFirstChar { it.uppercase() }) }
    } else {
        TextButton(onClick = { open = true }) { Text("Show ${label.lowercase()}") }
    }
}

// ---------- ask_user_questions ----------

@Composable
private fun IssueQuestionsBody(issueId: String, it: Interaction, pending: Boolean, work: IssueCardWork, run: (suspend () -> Interaction) -> Unit) {
    val payload = it.payload
    val questions = payload.issueObjs("questions")
    val setQuestions = payload.issueObj("questionSet").issueObjs("questions").associateBy { q -> q.issueStr("id") ?: "" }
    payload.issueObj("questionSet").issueStr("description")?.takeIf { d -> d.isNotBlank() }?.let { d -> Markdown(d, fontSizeSp = 14f); Spacer(Modifier.height(8.dp)) }

    if (!pending) {
        val answers = it.result.issueObjs("answers").associateBy { a -> a.issueStr("questionId") ?: "" }
        questions.forEach { q ->
            val qid = q.issueStr("id") ?: return@forEach
            val a = answers[qid]
            val labels = a.issueStrs("optionIds").mapNotNull { oid -> q.issueObjs("options").firstOrNull { o -> o.issueStr("id") == oid }?.issueStr("label") }
            val other = a.issueStr("otherText")
            Text(q.issueStr("prompt") ?: "", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                (labels + listOfNotNull(other)).joinToString(", ").ifEmpty { "No answer" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
        }
        it.result.issueStr("summaryMarkdown")?.let { s -> Markdown(s, fontSizeSp = 14f) }
        return
    }

    val selected = remember(it.id) { mutableStateMapOf<String, List<String>>() }
    val otherActive = remember(it.id) {
        mutableStateMapOf<String, Boolean>().apply {
            // Text-only questions (one free-text option) start with their field open.
            questions.forEach { q ->
                val opts = q.issueObjs("options")
                if (opts.size == 1 && opts[0].issueBool("freeText") == true) put(q.issueStr("id") ?: "", true)
            }
        }
    }
    val otherText = remember(it.id) { mutableStateMapOf<String, String>() }

    questions.forEachIndexed { index, q ->
        val qid = q.issueStr("id") ?: return@forEachIndexed
        val multi = q.issueStr("selectionMode") == "multi"
        val required = q.issueBool("required") == true
        val opts = q.issueObjs("options")
        val setQ = setQuestions[qid]
        val recommended = setQ.issueObjs("options").filter { o -> o.issueBool("recommended") == true }.mapNotNull { o -> o.issueStr("id") }.toSet()
        val hasFreeText = opts.any { o -> o.issueBool("freeText") == true }
        val textOnly = opts.size == 1 && hasFreeText
        if (index > 0) Spacer(Modifier.height(12.dp))
        setQ.issueStr("header")?.let { h -> Text(h.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Row {
            Text(q.issueStr("prompt") ?: "", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (required) Text(" *", color = MaterialTheme.colorScheme.error)
        }
        q.issueStr("helpText")?.takeIf { h -> h.isNotBlank() }?.let { h -> Text(h, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(4.dp))
        if (!textOnly) {
            opts.forEach { o ->
                val oid = o.issueStr("id") ?: return@forEach
                val free = o.issueBool("freeText") == true
                val checked = if (free) otherActive[qid] == true else (selected[qid] ?: emptyList()).contains(oid)
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = !work.working) {
                        issueToggleOption(qid, oid, multi, free, selected, otherActive)
                    }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (multi) Checkbox(checked = checked, onCheckedChange = { _ -> issueToggleOption(qid, oid, multi, free, selected, otherActive) })
                    else RadioButton(selected = checked, onClick = { issueToggleOption(qid, oid, multi, free, selected, otherActive) })
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(o.issueStr("label") ?: oid, style = MaterialTheme.typography.bodyMedium)
                            if (oid in recommended) { Spacer(Modifier.width(6.dp)); Pill("Recommended", LocalStatusColors.current.green) }
                        }
                        o.issueStr("description")?.takeIf { d -> d.isNotBlank() }?.let { d ->
                            Text(d, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (!hasFreeText && q.issueBool("allowOther") != false) {
                val checked = otherActive[qid] == true
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = !work.working) { issueToggleOption(qid, ISSUE_OTHER_ID, multi, true, selected, otherActive) }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (multi) Checkbox(checked = checked, onCheckedChange = { _ -> issueToggleOption(qid, ISSUE_OTHER_ID, multi, true, selected, otherActive) })
                    else RadioButton(selected = checked, onClick = { issueToggleOption(qid, ISSUE_OTHER_ID, multi, true, selected, otherActive) })
                    Text("Other…", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (otherActive[qid] == true) {
            val placeholder = setQ.issueObj("customAnswer").issueStr("placeholder") ?: if (textOnly) "Your answer" else "Type your answer"
            OutlinedTextField(
                value = otherText[qid] ?: "",
                onValueChange = { v -> otherText[qid] = v },
                placeholder = { Text(placeholder) },
                minLines = if (textOnly) 2 else 1,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }

    val canSubmit = questions.filter { q -> q.issueBool("required") == true }.all { q ->
        val qid = q.issueStr("id") ?: ""
        (selected[qid] ?: emptyList()).isNotEmpty() || (otherActive[qid] == true && !otherText[qid].isNullOrBlank())
    }
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(
            enabled = canSubmit && !work.working,
            onClick = {
                val answers = JsonArray(questions.mapNotNull { q ->
                    val qid = q.issueStr("id") ?: return@mapNotNull null
                    val other = if (otherActive[qid] == true) otherText[qid]?.trim().orEmpty() else ""
                    jsonOf(
                        "questionId" to qid,
                        "optionIds" to (selected[qid] ?: emptyList()),
                        "otherText" to other.ifEmpty { null },
                    ).let { o -> if (other.isEmpty()) JsonObject(o.filterKeys { k -> k != "otherText" }) else o }
                })
                run { Repo.respond(issueId, it.id, answers) }
            },
        ) { Text(payload.issueStr("submitLabel") ?: payload.issueObj("questionSet").issueStr("submitLabel") ?: "Submit answers") }
        if (work.working) { Spacer(Modifier.width(10.dp)); CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) }
    }
}

private fun issueToggleOption(
    qid: String,
    oid: String,
    multi: Boolean,
    freeText: Boolean,
    selected: MutableMap<String, List<String>>,
    otherActive: MutableMap<String, Boolean>,
) {
    if (freeText || oid == ISSUE_OTHER_ID) {
        otherActive[qid] = otherActive[qid] != true
        if (!multi) selected[qid] = emptyList()
        return
    }
    val existing = selected[qid] ?: emptyList()
    selected[qid] = if (!multi) listOf(oid) else if (oid in existing) existing - oid else existing + oid
    if (!multi) otherActive[qid] = false
}

// ---------- request_confirmation ----------

@Composable
private fun IssueConfirmationBody(issueId: String, it: Interaction, pending: Boolean, work: IssueCardWork, run: (suspend () -> Interaction) -> Unit, nav: Nav) {
    val p = it.payload
    p.issueStr("prompt")?.let { prompt -> Markdown(prompt, fontSizeSp = 15f) }
    val tool = p.issueObj("toolAction")
    val secret = p.issueObj("secretProposal")
    if (tool != null) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tool.issueStr("toolDisplayName") ?: tool.issueStr("toolName") ?: "Tool", fontWeight = FontWeight.SemiBold)
            tool.issueStr("appDisplayName")?.let { a -> Text("  ·  $a", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.width(8.dp))
            val risk = tool.issueStr("risk") ?: "read"
            val c = LocalStatusColors.current
            Pill(risk, when (risk) { "destructive" -> c.red; "write" -> c.orange; else -> c.green })
        }
        tool.issueStr("previewMarkdown")?.takeIf { s -> s.isNotBlank() }?.let { s -> Spacer(Modifier.height(4.dp)); IssueCollapsibleMarkdown(s, "Preview") }
    }
    if (secret != null) {
        Spacer(Modifier.height(8.dp))
        Text("Bind secret “${secret.issueStr("sourceSecretLabel") ?: "?"}” to ${secret.issueStr("targetAgentName") ?: "agent"} at ${secret.issueStr("configPath") ?: ""}", style = MaterialTheme.typography.bodyMedium)
        secret.issueStr("justification")?.let { j -> Text(j, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    p.issueStr("detailsMarkdown")?.takeIf { d -> d.isNotBlank() }?.let { d -> Spacer(Modifier.height(6.dp)); IssueCollapsibleMarkdown(d) }
    val target = p.issueObj("target")
    if (target != null) {
        Spacer(Modifier.height(6.dp))
        val label = target.issueStr("label") ?: if (target.issueStr("type") == "issue_document") "Open ${target.issueStr("key")} document" else "Open"
        val rev = target.issueInt("revisionNumber")?.let { r -> " (rev $r)" } ?: ""
        OutlinedButton(onClick = {
            if (target.issueStr("type") == "issue_document" && target.issueStr("key") != null) {
                nav.document(target.issueStr("issueId") ?: issueId, target.issueStr("key")!!)
            } else target.issueStr("href")?.let { h -> Links.handler(h) }
        }) {
            Icon(Icons.Outlined.Description, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(label + rev)
        }
    }
    if (!pending) {
        tool?.let { _ ->
            it.result.issueObj("toolAction")?.let { tr ->
                Spacer(Modifier.height(4.dp))
                Text("Tool: ${tr.issueStr("status") ?: ""}" + (tr.issueStr("resultSummary")?.let { s -> " — $s" } ?: ""), style = MaterialTheme.typography.bodySmall)
            }
        }
        return
    }
    Spacer(Modifier.height(10.dp))
    if (p.issueObj("connectionAuthorization") != null) {
        Text("This authorization has to be completed in the web UI.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
    }
    IssueAcceptRejectRow(
        payload = p,
        work = work,
        acceptEnabled = p.issueObj("connectionAuthorization") == null,
        acceptDefault = if (tool != null) "Allow once" else if (secret != null) "Approve & bind" else "Approve",
        extraActions = {
            if (tool != null && tool["rememberActionScope"] != null) {
                TextButton(onClick = { run { Repo.accept(issueId, it.id, jsonOf("rememberAction" to true)) } }, enabled = !work.working) { Text("Always allow") }
            }
        },
        onAccept = { run { Repo.accept(issueId, it.id) } },
        onReject = { reason -> run { issueRejectInteraction(issueId, it.id, reason) } },
    )
}

// ---------- request_checkbox_confirmation ----------

@Composable
private fun IssueCheckboxBody(issueId: String, it: Interaction, pending: Boolean, work: IssueCardWork, run: (suspend () -> Interaction) -> Unit) {
    val p = it.payload
    p.issueStr("prompt")?.let { prompt -> Markdown(prompt, fontSizeSp = 15f) }
    p.issueStr("detailsMarkdown")?.takeIf { d -> d.isNotBlank() }?.let { d -> Spacer(Modifier.height(6.dp)); IssueCollapsibleMarkdown(d) }
    val options = p.issueObjs("options")
    val resultSelected = it.result.issueStrs("selectedOptionIds").toSet()
    val chosen = remember(it.id) { mutableStateMapOf<String, Boolean>().apply { p.issueStrs("defaultSelectedOptionIds").forEach { id -> put(id, true) } } }
    val min = p.issueInt("minSelected") ?: 0
    val max = p.issueInt("maxSelected")
    Spacer(Modifier.height(6.dp))
    if (pending && options.size > 3) {
        Row {
            TextButton(onClick = { options.forEach { o -> o.issueStr("id")?.let { id -> chosen[id] = true } } }) { Text("Select all") }
            TextButton(onClick = { chosen.clear() }) { Text("Clear") }
        }
    }
    options.forEach { o ->
        val id = o.issueStr("id") ?: return@forEach
        val checked = if (pending) chosen[id] == true else id in resultSelected
        Row(
            Modifier.fillMaxWidth().clickable(enabled = pending && !work.working) { chosen[id] = !(chosen[id] ?: false) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = if (pending) ({ v -> chosen[id] = v }) else null, enabled = pending)
            Column(Modifier.weight(1f)) {
                Text(o.issueStr("label") ?: id)
                o.issueStr("description")?.let { d -> Text(d, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
    if (!pending) return
    val count = chosen.count { e -> e.value }
    val valid = count >= min && (max == null || count <= max)
    if (min > 0 || max != null) {
        Text(
            buildString {
                append("$count selected")
                if (min > 0) append(" · min $min")
                if (max != null) append(" · max $max")
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (valid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        )
    }
    Spacer(Modifier.height(8.dp))
    IssueAcceptRejectRow(
        payload = p,
        work = work,
        acceptEnabled = valid,
        onAccept = {
            val ids = options.mapNotNull { o -> o.issueStr("id") }.filter { id -> chosen[id] == true }
            run { Repo.accept(issueId, it.id, jsonOf("selectedOptionIds" to ids)) }
        },
        onReject = { reason -> run { issueRejectInteraction(issueId, it.id, reason) } },
    )
}

// ---------- suggest_tasks ----------

@Composable
private fun IssueSuggestTasksBody(issueId: String, it: Interaction, pending: Boolean, work: IssueCardWork, run: (suspend () -> Interaction) -> Unit, nav: Nav) {
    val tasks = it.payload.issueObjs("tasks")
    if (!pending) {
        val created = it.result.issueObjs("createdTasks")
        if (created.isNotEmpty()) {
            Text("Created ${created.size} task${if (created.size == 1) "" else "s"}", style = MaterialTheme.typography.labelMedium)
            created.forEach { c ->
                Text(
                    "${c.issueStr("identifier") ?: ""}  ${c.issueStr("title") ?: ""}",
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.fillMaxWidth().clickable { nav.issue(c.issueStr("identifier") ?: c.issueStr("issueId") ?: return@clickable) }.padding(vertical = 4.dp),
                )
            }
        } else {
            tasks.forEach { t -> Text("• ${t.issueStr("title") ?: ""}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        return
    }
    val children = tasks.groupBy { t -> t.issueStr("parentClientKey") }
    val keys = tasks.mapNotNull { t -> t.issueStr("clientKey") }.toSet()
    val roots = tasks.filter { t -> t.issueStr("parentClientKey").let { pk -> pk == null || pk !in keys } }
    val selected = remember(it.id) { mutableStateMapOf<String, Boolean>().apply { keys.forEach { k -> put(k, true) } } }
    fun descendants(key: String): List<String> = (children[key] ?: emptyList()).flatMap { c -> listOfNotNull(c.issueStr("clientKey")) + descendants(c.issueStr("clientKey") ?: "") }

    @Composable
    fun Node(t: JsonObject, depth: Int) {
        val key = t.issueStr("clientKey") ?: return
        var expanded by remember { mutableStateOf(false) }
        Row(
            Modifier.fillMaxWidth().padding(start = (depth * 18).dp).clickable { expanded = !expanded },
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(
                checked = selected[key] == true,
                onCheckedChange = { v -> selected[key] = v; descendants(key).forEach { d -> selected[d] = v } },
            )
            Column(Modifier.weight(1f).padding(top = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    t.issueStr("priority")?.let { pr -> PriorityIcon(pr, 14.dp); Spacer(Modifier.width(4.dp)) }
                    Text(t.issueStr("title") ?: "", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                t.issueStr("assigneeAgentId")?.let { a ->
                    Text("→ ${AppState.agentName(a)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val desc = t.issueStr("description")
                if (expanded && !desc.isNullOrBlank()) Markdown(desc, fontSizeSp = 13f)
                else if (!desc.isNullOrBlank()) Text("Tap to read description", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }
        (children[key] ?: emptyList()).forEach { c -> Node(c, depth + 1) }
    }
    roots.forEach { r -> Node(r, 0) }
    val count = selected.count { e -> e.value && e.key in keys }
    Spacer(Modifier.height(8.dp))
    IssueAcceptRejectRow(
        payload = it.payload,
        work = work,
        acceptEnabled = count > 0,
        acceptDefault = "Create $count task${if (count == 1) "" else "s"}",
        onAccept = {
            val chosen = tasks.mapNotNull { t -> t.issueStr("clientKey") }.filter { k -> selected[k] == true }
            run { Repo.accept(issueId, it.id, jsonOf("selectedClientKeys" to chosen)) }
        },
        onReject = { reason -> run { issueRejectInteraction(issueId, it.id, reason) } },
    )
}

// ---------- request_item_verdicts ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IssueVerdictsBody(issueId: String, it: Interaction, pending: Boolean, work: IssueCardWork, run: (suspend () -> Interaction) -> Unit) {
    val p = it.payload
    p.issueStr("prompt")?.let { prompt -> Markdown(prompt, fontSizeSp = 15f) }
    p.issueStr("detailsMarkdown")?.takeIf { d -> d.isNotBlank() }?.let { d -> Spacer(Modifier.height(6.dp)); IssueCollapsibleMarkdown(d) }
    val items = p.issueObjs("items")
    val verdictOptions = p.issueStrs("verdicts").ifEmpty { listOf("approve", "reject") }
    val requireReason = p.issueStrs("requireReasonOn").ifEmpty { listOf("reject") }.toSet()
    val existing = it.result.issueObjs("items").associateBy { r -> r.issueStr("id") ?: "" }
    val verdicts = remember(it.id) { mutableStateMapOf<String, String>() }
    val reasons = remember(it.id) { mutableStateMapOf<String, String>() }
    val c = LocalStatusColors.current
    Spacer(Modifier.height(6.dp))
    items.forEach { item ->
        val id = item.issueStr("id") ?: return@forEach
        val done = existing[id]
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.issueStr("label") ?: id, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                item.issueStr("href")?.let { h -> TextButton(onClick = { Links.handler(h) }) { Text("Open") } }
            }
            item.issueStr("description")?.let { d -> Text(d, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item.issueStr("previewMarkdown")?.takeIf { m -> m.isNotBlank() }?.let { m -> IssueCollapsibleMarkdown(m, "Preview") }
            if (done != null) {
                val v = done.issueStr("verdict") ?: ""
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill(v, when (v) { "approve" -> c.green; "reject" -> c.red; else -> c.gray })
                    done.issueStr("reason")?.let { r -> Spacer(Modifier.width(6.dp)); Text(r, style = MaterialTheme.typography.bodySmall) }
                }
            } else if (pending) {
                Spacer(Modifier.height(4.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    verdictOptions.forEachIndexed { i, v ->
                        SegmentedButton(
                            selected = verdicts[id] == v,
                            onClick = { if (verdicts[id] == v) verdicts.remove(id) else verdicts[id] = v },
                            shape = SegmentedButtonDefaults.itemShape(i, verdictOptions.size),
                        ) { Text(v.replaceFirstChar { ch -> ch.uppercase() }) }
                    }
                }
                val v = verdicts[id]
                if (v != null) {
                    OutlinedTextField(
                        value = reasons[id] ?: "",
                        onValueChange = { r -> reasons[id] = r },
                        label = { Text((p.issueStr("reasonLabel") ?: "Reason") + if (v in requireReason) " (required)" else " (optional)") },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
            }
        }
    }
    if (!pending) return
    val pendingIds = items.mapNotNull { i -> i.issueStr("id") }.filter { id -> existing[id] == null }
    val ready = verdicts.filterKeys { k -> k in pendingIds }
    val valid = ready.isNotEmpty() && ready.all { (id, v) -> v !in requireReason || !reasons[id].isNullOrBlank() }
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            enabled = valid && !work.working,
            onClick = {
                val arr = JsonArray(ready.map { (id, v) -> jsonOf("id" to id, "verdict" to v, "reason" to reasons[id]?.trim()?.ifEmpty { null }) })
                run { Repo.verdicts(issueId, it.id, arr) }
            },
        ) { Text("Submit ${ready.size}/${pendingIds.size}") }
        if (p.issueBool("allowBulkApprove") != false && "approve" in verdictOptions) {
            OutlinedButton(onClick = { pendingIds.forEach { id -> verdicts[id] = "approve" } }, enabled = !work.working) { Text("Approve all") }
        }
        if (work.working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
    }
}

// ---------- connection_intent ----------

@Composable
private fun IssueConnectionBody(issueId: String, it: Interaction, pending: Boolean, work: IssueCardWork, run: (suspend () -> Interaction) -> Unit) {
    val p = it.payload
    Text(
        "${p.issueStr("requestingAgentName") ?: "An agent"} wants to connect ${p.issueStr("serviceName") ?: p.issueStr("serviceSlug") ?: "a service"}.",
        style = MaterialTheme.typography.bodyMedium,
    )
    if (!pending) return
    Spacer(Modifier.height(4.dp))
    Text(
        "Connecting needs the sign-in flow of the web UI. Open the task there to finish, or decline here.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        Button(onClick = {
            val company = AppState.company.value
            val url = com.bu.paperclip.net.Api.baseUrl + "/" + (company?.issuePrefix ?: "") + "/issues/" + issueId
            catching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
        }) { Text("Open web UI") }
        OutlinedButton(onClick = { run { issueRejectInteraction(issueId, it.id, null) } }, enabled = !work.working) { Text("Decline") }
        if (work.working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
    }
}

/** Compact inline marker for a pending card that is pinned above the thread. */
@Composable
internal fun IssuePinnedMarker(interaction: Interaction) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(issueInteractionIcon(interaction.kind), null, Modifier.size(16.dp), tint = LocalStatusColors.current.yellow)
        Spacer(Modifier.width(8.dp))
        Text(
            "${AppState.agentName(interaction.createdByAgentId)} asked for ${issueInteractionLabel(interaction.kind).lowercase()} — pinned above",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Default,
        )
    }
}

