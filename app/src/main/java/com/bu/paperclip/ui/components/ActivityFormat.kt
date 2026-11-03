package com.bu.paperclip.ui.components

import com.bu.paperclip.data.ActivityEvent
import com.bu.paperclip.data.AppState
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import androidx.compose.ui.res.stringResource
import com.bu.paperclip.R
import com.bu.paperclip.util.appString
import com.bu.paperclip.util.appPlural

private fun ActivityEvent.d(key: String): String? = (details?.get(key) as? JsonPrimitive)?.contentOrNull

fun activityActor(e: ActivityEvent): String = when (e.actorType) {
    "agent" -> AppState.agentName(e.actorId ?: e.agentId)
    "user" -> AppState.userName(e.actorId)
    "system" -> appString(R.string.paperclip)
    else -> e.actorType.replaceFirstChar { it.uppercase() }
}

/** Human-readable one-liner for an activity event, e.g. "Ada commented on PAP-12". */
fun activitySentence(e: ActivityEvent): String {
    val who = activityActor(e)
    val target = e.d("identifier") ?: e.d("issueIdentifier") ?: e.d("title") ?: e.d("name") ?: e.entityType ?: ""
    val verb = when (e.action) {
        "issue.created" -> "created"
        "issue.updated" -> when {
            e.d("status") != null -> appString(R.string.moved_to_x, statusLabel(e.d("status")!!))
            e.details?.containsKey("assigneeAgentId") == true || e.details?.containsKey("assigneeUserId") == true -> "reassigned"
            e.d("priority") != null -> appString(R.string.set_priority_x_on, priorityLabel(e.d("priority")!!))
            else -> "updated"
        }
        "issue.comment_added" -> appString(R.string.commented_on)
        "issue.checked_out" -> appString(R.string.picked_up)
        "issue.released" -> "released"
        "issue.document_upserted", "issue.document_updated", "issue.document_created" -> appString(R.string.updated_a_document_on)
        "issue.attachment_added", "issue.attachment_created" -> appString(R.string.attached_a_file_to)
        "issue.work_product_created", "issue.work_product_updated" -> appString(R.string.added_output_to)
        "approval.created" -> appString(R.string.requested_approval)
        "approval.approved" -> "approved"
        "approval.rejected" -> "rejected"
        "agent.paused" -> "paused"
        "agent.resumed" -> "resumed"
        "agent.created" -> "hired"
        "agent.updated" -> "updated"
        else -> when {
            e.action.startsWith("issue.thread_interaction_") ->
                when (e.action.removePrefix("issue.thread_interaction_")) {
                    "created" -> appString(R.string.asked_for_input_on)
                    "accepted" -> appString(R.string.accepted_a_request_on)
                    "rejected" -> appString(R.string.declined_a_request_on)
                    "answered", "responded" -> appString(R.string.answered_on)
                    else -> e.action.removePrefix("issue.thread_interaction_").replace('_', ' ') + " on"
                }
            else -> e.action.substringAfter('.').replace('_', ' ')
        }
    }
    return if (target.isNotEmpty() && !verb.endsWith("approval")) "$who $verb $target" else "$who $verb"
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
