package com.bu.paperclip.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bu.paperclip.data.AppState
import com.bu.paperclip.data.Repo
import com.bu.paperclip.data.Routine
import com.bu.paperclip.data.RoutineTrigger
import com.bu.paperclip.data.jsonOf
import com.bu.paperclip.net.ApiException
import com.bu.paperclip.ui.Nav
import com.bu.paperclip.ui.components.AgentAvatar
import com.bu.paperclip.ui.components.Card
import com.bu.paperclip.ui.components.ErrorState
import com.bu.paperclip.ui.components.LiveRefresh
import com.bu.paperclip.ui.components.Markdown
import com.bu.paperclip.ui.components.Pill
import com.bu.paperclip.ui.components.PriorityIcon
import com.bu.paperclip.ui.components.PropertyChip
import com.bu.paperclip.ui.components.SectionHeader
import com.bu.paperclip.ui.components.errorMessage
import com.bu.paperclip.ui.components.statusLabel
import com.bu.paperclip.util.Schedule
import com.bu.paperclip.util.relativeTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import java.time.ZoneId
import androidx.compose.ui.res.stringResource
import com.bu.paperclip.R
import com.bu.paperclip.ui.components.priorityLabel
import com.bu.paperclip.util.appString
import com.bu.paperclip.util.appPlural

private val CONCURRENCY = listOf(
    Triple("coalesce_if_active", appString(R.string.merge_into_the_open_run), appString(R.string.if_a_run_is_still_open)),
    Triple("skip_if_active", appString(R.string.skip_while_one_is_open), appString(R.string.ignore_triggers_until_the_open_run)),
    Triple("always_enqueue", appString(R.string.always_start_a_new_run), appString(R.string.every_trigger_creates_its_own_task)),
)
private val CATCH_UP = listOf(
    Triple("skip_missed", appString(R.string.skip_missed_runs), appString(R.string.schedules_missed_while_paused_or_offline)),
    Triple("enqueue_missed_with_cap", appString(R.string.catch_up_with_a_cap), appString(R.string.missed_schedules_run_when_possible_up)),
)

/** The editable part of a routine; Save sends all of it, like the web UI's save bar. */
private data class RoutineDraft(
    val title: String,
    val description: String,
    val assigneeAgentId: String?,
    val projectId: String?,
    val priority: String,
    val concurrencyPolicy: String,
    val catchUpPolicy: String,
) {
    constructor(r: Routine) : this(r.title, r.description.orEmpty(), r.assigneeAgentId, r.projectId, r.priority, r.concurrencyPolicy, r.catchUpPolicy)

    fun body(baseRevisionId: String?): JsonObject = jsonOf(
        "title" to title.trim(),
        "description" to (description.trim().ifEmpty { null } ?: JsonNull),
        "assigneeAgentId" to (assigneeAgentId ?: JsonNull),
        "projectId" to (projectId ?: JsonNull),
        "priority" to priority,
        "concurrencyPolicy" to concurrencyPolicy,
        "catchUpPolicy" to catchUpPolicy,
        "baseRevisionId" to baseRevisionId,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RoutineEditScreen(nav: Nav, id: String) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var routine by remember { mutableStateOf<Routine?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf<RoutineDraft?>(null) }
    var saving by remember { mutableStateOf(false) }
    var conflict by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmRun by remember { mutableStateOf(false) }
    var preview by rememberSaveable { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var editTrigger by remember { mutableStateOf<RoutineTrigger?>(null) }
    var addingTrigger by remember { mutableStateOf(false) }
    var hiddenTriggers by remember { mutableStateOf(emptySet<String>()) }

    suspend fun load(resetDraft: Boolean) {
        try {
            val r = Repo.routine(id)
            routine = r
            if (resetDraft || draft == null) draft = RoutineDraft(r)
            loadError = null
        } catch (c: CancellationException) { throw c } catch (t: Throwable) {
            if (routine == null) loadError = errorMessage(t) else AppState.toast(errorMessage(t))
        }
    }
    LaunchedEffect(id) { load(resetDraft = true) }
    // Trigger and status changes from elsewhere refresh the routine but never overwrite unsaved edits.
    LiveRefresh(filter = { it.type == "activity.logged" && it.s("entityId") == id }) { scope.launch { load(resetDraft = false) } }

    val r = routine
    val d = draft
    val dirty = r != null && d != null && d != RoutineDraft(r)

    fun save(force: Boolean = false) {
        val cur = routine ?: return
        val body = draft ?: return
        if (body.title.isBlank() || saving) return
        saving = true
        scope.launch {
            try {
                Repo.updateRoutine(cur.id, body.body(if (force) null else cur.latestRevisionId))
                load(resetDraft = true)
                AppState.toast(appString(R.string.routine_saved))
            } catch (c: CancellationException) { throw c } catch (t: Throwable) {
                if (t is ApiException.Http && t.status == 409) conflict = true else AppState.toast(errorMessage(t))
            } finally { saving = false }
        }
    }

    fun setStatus(next: String) {
        val cur = routine ?: return
        if (next == "active" && cur.assigneeAgentId == null) {
            AppState.toast(appString(R.string.set_a_default_agent_and_save))
            return
        }
        routine = cur.copy(status = next)
        scope.launch {
            try {
                Repo.updateRoutine(cur.id, jsonOf("status" to next))
                AppState.toast(if (next == "paused") appString(R.string.automation_paused) else appString(R.string.automation_enabled))
            } catch (c: CancellationException) { throw c } catch (t: Throwable) {
                routine = cur; AppState.toast(errorMessage(t))
            }
            load(resetDraft = false)
        }
    }

    fun triggerCall(okMessage: String?, block: suspend () -> Unit) {
        scope.launch {
            try { block(); okMessage?.let { AppState.toast(it) } }
            catch (c: CancellationException) { throw c }
            catch (t: Throwable) { AppState.toast(errorMessage(t)) }
            load(resetDraft = false)
        }
    }

    fun removeTrigger(t: RoutineTrigger) {
        hiddenTriggers = hiddenTriggers + t.id
        scope.launch {
            try {
                Repo.updateRoutineTrigger(t.id, jsonOf("archived" to true))
                load(resetDraft = false)
                if (snackbar.showSnackbar(appString(R.string.schedule_removed), appString(R.string.undo), duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                    Repo.updateRoutineTrigger(t.id, jsonOf("archived" to false))
                    load(resetDraft = false)
                }
            } catch (c: CancellationException) { throw c } catch (e: Throwable) {
                AppState.toast(errorMessage(e))
            } finally { hiddenTriggers = hiddenTriggers - t.id }
        }
    }

    BackHandler(enabled = dirty) { confirmDiscard = true }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = { BackIcon { if (dirty) confirmDiscard = true else nav.back() } },
                title = { Text(if (dirty) stringResource(R.string.edit_routine) else stringResource(R.string.routine), maxLines = 1) },
                actions = {
                    if (saving) CircularProgressIndicator(Modifier.padding(end = 16.dp).size(22.dp), strokeWidth = 2.dp)
                    else if (dirty) {
                        TextButton(onClick = { draft = r?.let(::RoutineDraft) }) { Text(stringResource(R.string.discard)) }
                        TextButton(onClick = { save() }, enabled = d?.title?.isNotBlank() == true) { Text(stringResource(R.string.save)) }
                    } else if (r != null) {
                        IconButton(onClick = { confirmRun = true }, enabled = r.status != "archived") { Icon(Icons.Outlined.PlayArrow, stringResource(R.string.run_now)) }
                    }
                },
            )
        },
    ) { pad ->
        when {
            r == null && loadError != null -> ErrorState(loadError!!, Modifier.padding(pad)) { scope.launch { load(true) } }
            r == null || d == null -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> Column(
                Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()),
            ) {
                // ---- automation status ----
                Card(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.automation), style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.width(8.dp))
                                Pill(
                                    when {
                                        r.status == "archived" -> stringResource(R.string.archived)
                                        r.assigneeAgentId == null -> stringResource(R.string.draft)
                                        else -> statusLabel(r.status)
                                    },
                                    adminStatusColor(r.status),
                                )
                            }
                            Text(
                                if (r.assigneeAgentId == null) stringResource(R.string.needs_a_default_agent_before_schedules)
                                else if (r.status == "active") stringResource(R.string.schedules_and_triggers_start_runs) else stringResource(R.string.triggers_are_ignored_until_you_resume),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = r.status == "active",
                            enabled = r.status != "archived",
                            onCheckedChange = { setStatus(if (it) "active" else "paused") },
                        )
                    }
                }
                r.managedByPlugin?.let {
                    Text(stringResource(R.string.managed_by_a_plugin_its_defaults), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                }

                // ---- overview ----
                SectionHeader(stringResource(R.string.overview))
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = d.title,
                        onValueChange = { draft = d.copy(title = it) },
                        label = { Text(stringResource(R.string.title)) },
                        isError = d.title.isBlank(),
                        supportingText = if (d.title.isBlank()) ({ Text(stringResource(R.string.a_title_is_required)) }) else null,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PropertyChip(
                            onClick = { sheet = "assignee" },
                            label = { Text(d.assigneeAgentId?.let { AppState.agentName(it) } ?: stringResource(R.string.no_default_agent)) },
                            leadingIcon = {
                                if (d.assigneeAgentId != null) AgentAvatar(d.assigneeAgentId, 18.dp)
                                else Icon(Icons.Outlined.PersonOutline, null, Modifier.size(18.dp))
                            },
                        )
                        PropertyChip(
                            onClick = { sheet = "project" },
                            label = { Text(AppState.project(d.projectId)?.name ?: stringResource(R.string.no_project)) },
                            leadingIcon = { Icon(Icons.Outlined.FolderOpen, null, Modifier.size(18.dp)) },
                        )
                        PropertyChip(
                            onClick = { sheet = "priority" },
                            label = { Text(priorityLabel(d.priority)) },
                            leadingIcon = { PriorityIcon(d.priority, 16.dp) },
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.instructions), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        FilterChip(selected = !preview, onClick = { preview = false }, label = { Text(stringResource(R.string.edit)) })
                        Spacer(Modifier.width(6.dp))
                        FilterChip(selected = preview, onClick = { preview = true }, label = { Text(stringResource(R.string.preview)) })
                    }
                    if (preview) {
                        Card {
                            if (d.description.isBlank()) Text(stringResource(R.string.no_instructions_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            else Markdown(d.description, fontSizeSp = 14f)
                        }
                    } else {
                        OutlinedTextField(
                            value = d.description,
                            onValueChange = { draft = d.copy(description = it) },
                            placeholder = { Text("Add instructions… (markdown, {{variables}})") },
                            minLines = 6,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                        )
                    }
                    val vars = r.variables?.size ?: 0
                    if (vars > 0) Text(
                        "$vars variable${if (vars == 1) "" else "s"} from {{placeholders}}; edit their types and defaults in the web UI.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // ---- schedules ----
                SectionHeader(stringResource(R.string.schedules)) {
                    TextButton(onClick = { addingTrigger = true }) {
                        Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.add))
                    }
                }
                val triggers = r.triggers.orEmpty().filter { !it.archived && it.id !in hiddenTriggers }
                if (triggers.isEmpty()) Text(
                    stringResource(R.string.no_triggers_add_a_schedule_or),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                triggers.forEach { t ->
                    RoutineTriggerRow(
                        t,
                        onToggle = { on -> triggerCall(if (on) appString(R.string.schedule_enabled) else appString(R.string.schedule_paused)) { Repo.updateRoutineTrigger(t.id, jsonOf("enabled" to on)) } },
                        onEdit = { editTrigger = t },
                        onRemove = { removeTrigger(t) },
                    )
                }

                // ---- delivery ----
                SectionHeader(stringResource(R.string.when_runs_overlap))
                CONCURRENCY.forEach { (value, label, help) ->
                    RoutineRadio(d.concurrencyPolicy == value, label, help) { draft = d.copy(concurrencyPolicy = value) }
                }
                SectionHeader(stringResource(R.string.missed_schedules))
                CATCH_UP.forEach { (value, label, help) ->
                    RoutineRadio(d.catchUpPolicy == value, label, help) { draft = d.copy(catchUpPolicy = value) }
                }
                Spacer(Modifier.height(48.dp))
            }
        }
    }

    when (sheet) {
        "assignee" -> IssueAssigneeSheet(d?.assigneeAgentId, null, allowMe = false, title = stringResource(R.string.default_agent), onPick = { a ->
            draft = draft?.copy(assigneeAgentId = (a as? IssueAssignee.AgentA)?.id)
        }, onDismiss = { sheet = null })
        "project" -> IssueProjectSheet(d?.projectId, onPick = { draft = draft?.copy(projectId = it) }, onDismiss = { sheet = null })
        "priority" -> IssuePrioritySheet(d?.priority, onPick = { draft = draft?.copy(priority = it) }, onDismiss = { sheet = null })
    }

    if (addingTrigger || editTrigger != null) {
        val t = editTrigger
        ScheduleDialog(
            initialCron = t?.cronExpression ?: "0 9 * * 1-5",
            initialZone = t?.timezone ?: ZoneId.systemDefault().id,
            title = if (t == null) stringResource(R.string.add_schedule) else stringResource(R.string.edit_schedule),
            onDismiss = { addingTrigger = false; editTrigger = null },
            onSave = { cron, zone ->
                addingTrigger = false; editTrigger = null
                val cur = routine ?: return@ScheduleDialog
                if (t == null) triggerCall(appString(R.string.schedule_added)) {
                    Repo.createRoutineTrigger(cur.id, jsonOf("kind" to "schedule", "cronExpression" to cron, "timezone" to zone))
                } else triggerCall(appString(R.string.schedule_saved)) {
                    Repo.updateRoutineTrigger(t.id, jsonOf("cronExpression" to cron, "timezone" to zone))
                }
            },
        )
    }

    if (conflict) AlertDialog(
        onDismissRequest = { conflict = false },
        title = { Text(stringResource(R.string.routine_changed_elsewhere)) },
        text = { Text(stringResource(R.string.someone_else_saved_this_routine_since)) },
        confirmButton = { TextButton(onClick = { conflict = false; save(force = true) }) { Text(stringResource(R.string.overwrite)) } },
        dismissButton = { TextButton(onClick = { conflict = false; scope.launch { load(resetDraft = true) } }) { Text(stringResource(R.string.reload_latest)) } },
    )

    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text(stringResource(R.string.discard_changes)) },
        text = { Text(stringResource(R.string.your_edits_to_this_routine_haven)) },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; draft = r?.let(::RoutineDraft); nav.back() }) { Text(stringResource(R.string.discard)) } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.keep_editing)) } },
    )

    if (confirmRun && r != null) AlertDialog(
        onDismissRequest = { confirmRun = false },
        title = { Text(stringResource(R.string.run_now_2)) },
        text = { Text(AppState.agent(r.assigneeAgentId)?.let { appString(R.string.start_x_now_assigned_to_x, r.title, it.name) } ?: appString(R.string.start_x_now, r.title)) },
        confirmButton = {
            TextButton(onClick = {
                confirmRun = false
                triggerCall(appString(R.string.started_x, r.title)) { Repo.runRoutine(r.id) }
            }) { Text(stringResource(R.string.run)) }
        },
        dismissButton = { TextButton(onClick = { confirmRun = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun RoutineTriggerRow(t: RoutineTrigger, onToggle: (Boolean) -> Unit, onEdit: () -> Unit, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Schedule, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (t.kind == "schedule") {
                Text(Schedule.describe(t.cronExpression), style = MaterialTheme.typography.bodyLarge)
                Text(
                    listOfNotNull(
                        t.timezone,
                        if (!t.enabled) "paused" else t.nextRunAt?.let { "next ${relativeTime(it)}" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(t.label ?: "${t.kind.replaceFirstChar { it.uppercase() }} trigger", style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.manage_x_triggers_in_the_web, t.kind), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = t.enabled, onCheckedChange = onToggle)
        if (t.kind == "schedule") {
            IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, stringResource(R.string.edit_schedule)) }
            IconButton(onClick = onRemove) { Icon(Icons.Outlined.Delete, stringResource(R.string.remove_schedule)) }
        }
    }
}

@Composable
private fun RoutineRadio(selected: Boolean, label: String, help: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected, onClick = onClick, role = Role.RadioButton).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Preset-based schedule editor (the web UI's presets), with a raw cron escape hatch. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ScheduleDialog(initialCron: String, initialZone: String, title: String, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var s by remember { mutableStateOf(Schedule.parse(initialCron).let { if (it.preset == Schedule.Preset.Custom && it.custom.isEmpty()) it.copy(custom = initialCron) else it }) }
    var zone by remember { mutableStateOf(initialZone) }
    val cron = s.toCron()
    val cronError = Schedule.validate(cron)
    val zoneValid = remember(zone) { runCatching { ZoneId.of(zone.trim()) }.isSuccess }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Schedule.Preset.entries.forEach { p ->
                        FilterChip(selected = s.preset == p, onClick = {
                            s = if (p == Schedule.Preset.Custom) s.copy(preset = p, custom = cron) else s.copy(preset = p)
                        }, label = { Text(p.label) })
                    }
                }
                when (s.preset) {
                    Schedule.Preset.Custom -> OutlinedTextField(
                        value = s.custom, onValueChange = { s = s.copy(custom = it) }, singleLine = true,
                        label = { Text(stringResource(R.string.cron_minute_hour_day_month_weekday)) },
                        isError = cronError != null,
                        supportingText = { Text(cronError ?: Schedule.describe(s.custom)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Schedule.Preset.Hourly -> NumberField(stringResource(R.string.minute_past_the_hour), s.minute, 0..59) { s = s.copy(minute = it) }
                    else -> {
                        if (s.preset == Schedule.Preset.Weekly) {
                            ScheduleDropdown(stringResource(R.string.day), Schedule.WEEKDAYS[s.weekday], Schedule.WEEKDAYS) { s = s.copy(weekday = Schedule.WEEKDAYS.indexOf(it)) }
                        }
                        if (s.preset == Schedule.Preset.Monthly) NumberField(stringResource(R.string.day_of_month), s.dayOfMonth, 1..31) { s = s.copy(dayOfMonth = it) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f)) { NumberField(stringResource(R.string.hour_0_23), s.hour, 0..23) { s = s.copy(hour = it) } }
                            Box(Modifier.weight(1f)) { NumberField(stringResource(R.string.minute), s.minute, 0..59) { s = s.copy(minute = it) } }
                        }
                    }
                }
                OutlinedTextField(
                    value = zone, onValueChange = { zone = it }, singleLine = true,
                    label = { Text(stringResource(R.string.time_zone)) },
                    isError = !zoneValid,
                    supportingText = { Text(if (zoneValid) stringResource(R.string.follows_this_zone_including_daylight_saving) else stringResource(R.string.use_an_iana_name_e_g)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (cronError == null) Text(Schedule.describe(cron), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(cron, zone.trim()) }, enabled = cronError == null && zoneValid) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun NumberField(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    val n = text.toIntOrNull()
    OutlinedTextField(
        value = text,
        onValueChange = { v -> text = v.filter(Char::isDigit).take(2); text.toIntOrNull()?.takeIf { it in range }?.let(onChange) },
        label = { Text(label) },
        singleLine = true,
        isError = n == null || n !in range,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleDropdown(label: String, value: String, options: List<String>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = value, onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { onPick(o); open = false }) }
        }
    }
}
