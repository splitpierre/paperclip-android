package com.bu.paperclip.ui.components

import androidx.annotation.StringRes
import com.bu.paperclip.R
import com.bu.paperclip.data.ActivityEvent
import com.bu.paperclip.data.AppState
import com.bu.paperclip.util.L10n
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private fun ActivityEvent.d(key: String): String? = (details?.get(key) as? JsonPrimitive)?.contentOrNull

fun activityActor(e: ActivityEvent): String = when (e.actorType) {
    "agent" -> AppState.agentName(e.actorId ?: e.agentId)
    "user" -> AppState.userName(e.actorId)
    "system" -> L10n.str(R.string.app_name)
    else -> e.actorType.replaceFirstChar { it.uppercase() }
}

/** Human-readable one-liner for an activity event, e.g. "Ada commented on PAP-12". */
fun activitySentence(e: ActivityEvent): String {
    val me = AppState.me.value
    val self = e.actorType == "user" && e.actorId != null && e.actorId == me?.id
    // Languages whose verbs change for "you" leave activity_actor_you empty, so sentences use your name.
    val who = if (self) L10n.str(R.string.activity_actor_you).ifEmpty { me?.name?.takeIf { it.isNotBlank() } ?: me?.email ?: activityActor(e) }
    else activityActor(e)
    val target = e.d("identifier") ?: e.d("issueIdentifier") ?: e.d("title") ?: e.d("name") ?: e.entityType ?: ""
    // Sentences take the actor, then the target; a missing target leaves a trailing space we trim.
    fun s(@StringRes id: Int, vararg extra: Any) = L10n.str(id, who, target, *extra).trim()
    return when (e.action) {
        "issue.created" -> s(R.string.activity_created)
        "issue.updated" -> when {
            e.d("status") != null -> s(R.string.activity_moved_to, statusLabel(e.d("status")!!))
            e.details?.containsKey("assigneeAgentId") == true || e.details?.containsKey("assigneeUserId") == true -> s(R.string.activity_reassigned)
            e.d("priority") != null -> s(R.string.activity_set_priority, taskPriorityLabel(e.d("priority")!!))
            else -> s(R.string.activity_updated)
        }
        "issue.comment_added" -> s(R.string.activity_commented)
        "issue.checked_out" -> s(R.string.activity_picked_up)
        "issue.released" -> s(R.string.activity_released)
        "issue.document_upserted", "issue.document_updated", "issue.document_created" -> s(R.string.activity_document_updated)
        "issue.attachment_added", "issue.attachment_created" -> s(R.string.activity_attached)
        "issue.work_product_created", "issue.work_product_updated" -> s(R.string.activity_output_added)
        "approval.created" -> L10n.str(R.string.activity_approval_requested, who)
        "approval.approved" -> s(R.string.activity_approved)
        "approval.rejected" -> s(R.string.activity_rejected)
        "agent.paused" -> s(R.string.activity_paused)
        "agent.resumed" -> s(R.string.activity_resumed)
        "agent.created" -> s(R.string.activity_hired)
        "agent.updated" -> s(R.string.activity_updated)
        else -> when {
            e.action.startsWith("issue.thread_interaction_") ->
                when (val kind = e.action.removePrefix("issue.thread_interaction_")) {
                    "created" -> s(R.string.activity_interaction_created)
                    "accepted" -> s(R.string.activity_interaction_accepted)
                    "rejected" -> s(R.string.activity_interaction_rejected)
                    "answered", "responded" -> s(R.string.activity_interaction_answered)
                    else -> s(R.string.activity_interaction_other, kind.replace('_', ' '))
                }
            else -> s(R.string.activity_other, e.action.substringAfter('.').replace('_', ' '))
        }
    }
}

/** In-app link for an activity event, if it points at something we can open. */
fun activityLink(e: ActivityEvent): String? = when (e.entityType) {
    "issue" -> (e.d("identifier") ?: e.entityId)?.let { "/issues/$it" }
    "agent" -> e.entityId?.let { "agent://$it" }
    "approval" -> e.entityId?.let { "/approvals/$it" }
    "project" -> e.entityId?.let { "project://$it" }
    "heartbeat_run" -> e.entityId?.let { "/runs/$it" }
    else -> e.runId?.let { "/runs/$it" }
}
