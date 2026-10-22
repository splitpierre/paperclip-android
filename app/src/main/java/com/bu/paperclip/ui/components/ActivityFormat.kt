package com.bu.paperclip.ui.components

import com.bu.paperclip.data.ActivityEvent
import com.bu.paperclip.data.AppState
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private fun ActivityEvent.d(key: String): String? = (details?.get(key) as? JsonPrimitive)?.contentOrNull

fun activityActor(e: ActivityEvent): String = when (e.actorType) {
    "agent" -> AppState.agentName(e.actorId ?: e.agentId)
    "user" -> AppState.userName(e.actorId)
    "system" -> "Paperclip"
    else -> e.actorType.replaceFirstChar { it.uppercase() }
}

/** Human-readable one-liner for an activity event, e.g. "Ada commented on PAP-12". */
fun activitySentence(e: ActivityEvent, who: String = activityActor(e)): String {
    val target = e.d("identifier") ?: e.d("issueIdentifier") ?: e.d("title") ?: e.d("name") ?: e.entityType ?: ""
    val verb = when (e.action) {
        "issue.created" -> "created"
        "issue.updated" -> when {
            e.d("status") != null -> "moved to ${statusLabel(e.d("status")!!)}"
            e.details?.containsKey("assigneeAgentId") == true || e.details?.containsKey("assigneeUserId") == true -> "reassigned"
            e.d("priority") != null -> "set priority ${e.d("priority")} on"
            else -> "updated"
        }
        "issue.comment_added" -> "commented on"
        "issue.checked_out" -> "picked up"
        "issue.released" -> "released"
        "issue.document_upserted", "issue.document_updated", "issue.document_created" -> "updated a document on"
        "issue.attachment_added", "issue.attachment_created" -> "attached a file to"
        "issue.work_product_created", "issue.work_product_updated" -> "added output to"
        "approval.created" -> "requested approval"
        "approval.approved" -> "approved"
        "approval.rejected" -> "rejected"
        "agent.paused" -> "paused"
        "agent.resumed" -> "resumed"
        "agent.created" -> "hired"
        "agent.updated" -> "updated"
        else -> when {
            e.action.startsWith("issue.thread_interaction_") ->
                when (e.action.removePrefix("issue.thread_interaction_")) {
                    "created" -> "asked for input on"
                    "accepted" -> "accepted a request on"
                    "rejected" -> "declined a request on"
                    "answered", "responded" -> "answered on"
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
