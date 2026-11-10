package com.bu.paperclip.data

import com.bu.paperclip.net.Api
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/** Builds a request body; Kotlin nulls are omitted (the server rejects null on optional fields). Pass JsonNull to send an explicit null. */
fun jsonOf(vararg pairs: Pair<String, Any?>): JsonObject = buildJsonObject {
    pairs.forEach { (k, v) -> if (v != null) put(k, toJson(v)) }
}

fun toJson(v: Any?): JsonElement = when (v) {
    null -> JsonNull
    is JsonElement -> v
    is String -> JsonPrimitive(v)
    is Number -> JsonPrimitive(v)
    is Boolean -> JsonPrimitive(v)
    is Map<*, *> -> buildJsonObject { v.forEach { (k, x) -> if (x != null) put(k.toString(), toJson(x)) } }
    is Collection<*> -> buildJsonArray { v.forEach { add(toJson(it)) } }
    else -> JsonPrimitive(v.toString())
}

/** All Paperclip endpoints the app uses. Paths are relative to `/api`. */
object Repo {
    // Session & companies
    suspend fun session(): AuthSession = Api.get("/auth/get-session")
    suspend fun companies(): List<Company> = Api.get("/companies", mapOf("scope" to "accessible"))
    suspend fun userDirectory(cid: String): UserDirectory = Api.get("/companies/$cid/user-directory")

    // Dashboard / activity / badges
    suspend fun dashboard(cid: String): DashboardSummary = Api.get("/companies/$cid/dashboard")
    suspend fun badges(cid: String): SidebarBadges = Api.get("/companies/$cid/sidebar-badges")
    suspend fun activity(cid: String, limit: Int = 60, agentId: String? = null, entityType: String? = null, entityId: String? = null): List<ActivityEvent> =
        Api.get("/companies/$cid/activity", mapOf("limit" to limit, "agentId" to agentId, "entityType" to entityType, "entityId" to entityId))
    suspend fun liveRuns(cid: String, minCount: Int = 0, limit: Int = 50): List<LiveRun> =
        Api.get("/companies/$cid/live-runs", mapOf("minCount" to minCount, "limit" to limit))

    // Inbox / attention
    suspend fun attention(cid: String, cursor: String? = null, limit: Int = 50, sort: String = "activity"): AttentionFeed =
        Api.get("/companies/$cid/attention", mapOf("sort" to sort, "limit" to limit, "cursor" to cursor))
    /** Everything the Decisions desk shows, including the caller's own dismissals and snoozes. */
    suspend fun attentionDesk(cid: String): AttentionFeed =
        Api.get("/companies/$cid/attention", mapOf("includeDismissed" to "true", "all" to "true"))
    suspend fun undismiss(cid: String, itemKey: String): JsonElement = Api.delete("/companies/$cid/inbox-dismissals/" + android.net.Uri.encode(itemKey))
    suspend fun setDecideBy(cid: String, sourceKind: String, sourceId: String, decideBy: String?): JsonElement =
        Api.put("/companies/$cid/decision-triage/$sourceKind/" + android.net.Uri.encode(sourceId), buildJsonObject { put("decideBy", toJson(decideBy)) })

    // Decisions
    suspend fun decision(id: String): Decision = Api.get("/decisions/$id")
    suspend fun decisions(cid: String, status: String, limit: Int = 51): List<Decision> =
        Api.get("/companies/$cid/decisions", mapOf("status" to status, "limit" to limit))
    suspend fun decide(id: String, optionId: String, inputValues: Map<String, String>, idempotencyKey: String): Decision =
        Api.post("/decisions/$id/decide", jsonOf("optionId" to optionId, "inputValues" to inputValues, "idempotencyKey" to idempotencyKey))
    suspend fun dismissDecision(id: String): Decision = Api.post("/decisions/$id/dismiss", JsonObject(emptyMap()))
    suspend fun decideJoinRequest(cid: String, id: String, approve: Boolean): JsonElement =
        Api.post("/companies/$cid/join-requests/$id/" + if (approve) "approve" else "reject", JsonObject(emptyMap()))

    suspend fun dismiss(cid: String, itemKey: String): JsonElement = Api.post("/companies/$cid/inbox-dismissals", jsonOf("itemKey" to itemKey))
    suspend fun snooze(cid: String, itemKey: String, untilIso: String): JsonElement =
        Api.post("/companies/$cid/inbox-dismissals", jsonOf("itemKey" to itemKey, "kind" to "snooze", "snoozedUntil" to untilIso))

    // Issues
    suspend fun issues(
        cid: String,
        status: String? = null,
        assigneeAgentId: String? = null,
        assigneeUserId: String? = null,
        projectId: String? = null,
        parentId: String? = null,
        q: String? = null,
        touchedByUserId: String? = null,
        unreadForUserId: String? = null,
        inboxArchivedByUserId: String? = null,
        attention: String? = null,
        limit: Int = 50,
        offset: Int = 0,
    ): List<Issue> = Api.get(
        "/companies/$cid/issues",
        mapOf(
            "view" to "compact", "status" to status, "assigneeAgentId" to assigneeAgentId, "assigneeUserId" to assigneeUserId,
            "projectId" to projectId, "parentId" to parentId, "q" to q?.takeIf { it.isNotBlank() },
            "touchedByUserId" to touchedByUserId, "unreadForUserId" to unreadForUserId, "inboxArchivedByUserId" to inboxArchivedByUserId,
            "attention" to attention, "limit" to limit, "offset" to offset, "sortField" to "updated", "sortDir" to "desc",
            "includeRoutineExecutions" to "true",
        ),
    )
    suspend fun issue(id: String): Issue = Api.get("/issues/$id")
    suspend fun createIssue(cid: String, body: JsonObject): Issue = Api.post("/companies/$cid/issues", body)
    suspend fun updateIssue(id: String, body: JsonObject): Issue = Api.patch("/issues/$id", body)
    suspend fun markRead(id: String): JsonElement = Api.post("/issues/$id/read")
    suspend fun markUnread(id: String): JsonElement = Api.delete("/issues/$id/read")
    suspend fun archiveFromInbox(id: String): JsonElement = Api.post("/issues/$id/inbox-archive")
    suspend fun unarchiveFromInbox(id: String): JsonElement = Api.delete("/issues/$id/inbox-archive")
    suspend fun issueCost(id: String): IssueCostSummary = Api.get("/issues/$id/cost-summary")
    suspend fun issueActivity(id: String): List<ActivityEvent> = Api.get("/issues/$id/activity")
    suspend fun issueRuns(id: String): List<JsonObject> = Api.get("/issues/$id/runs")
    suspend fun issueLiveRuns(id: String): List<LiveRun> = Api.get("/issues/$id/live-runs")
    suspend fun labels(cid: String): List<Label> = Api.get("/companies/$cid/labels")

    // Comments
    suspend fun comments(id: String, limit: Int = 50, after: String? = null): List<IssueComment> =
        Api.get("/issues/$id/comments", mapOf("order" to "desc", "limit" to limit, "after" to after))
    suspend fun addComment(id: String, body: String, interrupt: Boolean = false, reopen: Boolean = false, attachmentIds: List<String> = emptyList()): IssueComment =
        Api.post(
            "/issues/$id/comments",
            jsonOf(
                "body" to body, "clientRequestId" to UUID.randomUUID().toString(),
                "interrupt" to if (interrupt) true else null, "reopen" to if (reopen) true else null,
                "attachmentIds" to attachmentIds.takeIf { it.isNotEmpty() },
            ),
        )
    suspend fun deleteComment(id: String, commentId: String): JsonElement = Api.delete("/issues/$id/comments/$commentId")

    // Interactions
    suspend fun interactions(id: String): List<Interaction> = Api.get("/issues/$id/interactions")
    private fun ip(id: String, iid: String, action: String) = "/issues/$id/interactions/$iid/$action"
    suspend fun accept(id: String, iid: String, body: JsonObject = JsonObject(emptyMap())): Interaction = Api.post(ip(id, iid, "accept"), body)
    suspend fun reject(id: String, iid: String, reason: String?): Interaction = Api.post(ip(id, iid, "reject"), jsonOf("reason" to reason?.takeIf { it.isNotBlank() }))
    suspend fun respond(id: String, iid: String, answers: JsonArray): Interaction = Api.post(ip(id, iid, "respond"), jsonOf("answers" to answers))
    suspend fun verdicts(id: String, iid: String, verdicts: JsonArray): Interaction = Api.post(ip(id, iid, "verdicts"), jsonOf("verdicts" to verdicts))
    suspend fun skip(id: String, iid: String, reason: String? = null): Interaction = Api.post(ip(id, iid, "skip"), jsonOf("reason" to reason))
    suspend fun cancelInteraction(id: String, iid: String, reason: String? = null): Interaction = Api.post(ip(id, iid, "cancel"), jsonOf("reason" to reason))

    // Documents, attachments, work products
    suspend fun documents(id: String): List<IssueDocument> = Api.get("/issues/$id/documents")
    suspend fun document(id: String, key: String): IssueDocument = Api.get("/issues/$id/documents/$key")
    suspend fun saveDocument(id: String, key: String, title: String?, body: String, baseRevisionId: String?): IssueDocument =
        Api.put("/issues/$id/documents/$key", jsonOf("title" to title, "format" to "markdown", "body" to body, "baseRevisionId" to baseRevisionId))
    suspend fun attachments(id: String): List<Attachment> = Api.get("/issues/$id/attachments")
    suspend fun uploadAttachment(cid: String, issueId: String, name: String, mime: String, bytes: ByteArray): Attachment =
        Api.decode(Attachment.serializer(), Api.upload("/companies/$cid/issues/$issueId/attachments", name, mime, bytes))
    suspend fun workProducts(id: String): List<WorkProduct> = Api.get("/issues/$id/work-products")

    // Agents
    suspend fun agents(cid: String): List<Agent> = Api.get("/companies/$cid/agents")
    suspend fun agent(id: String): Agent = Api.get("/agents/$id", mapOf("companyId" to AppState.cid.ifEmpty { null }))
    suspend fun pause(id: String): Agent = Api.post("/agents/$id/pause")
    suspend fun resume(id: String): Agent = Api.post("/agents/$id/resume")
    suspend fun clearError(id: String): Agent = Api.post("/agents/$id/clear-error")
    suspend fun invoke(id: String): HeartbeatRun = Api.post("/agents/$id/heartbeat/invoke")
    suspend fun wake(id: String, reason: String? = null, failedRunId: String? = null, issueId: String? = null): JsonElement =
        Api.post(
            "/agents/$id/wakeup",
            jsonOf(
                "source" to "on_demand", "triggerDetail" to "manual", "reason" to reason, "failedRunId" to failedRunId,
                "payload" to issueId?.let { mapOf("issueId" to it) },
            ),
        )
    suspend fun runtimeState(id: String): RuntimeState = Api.get("/agents/$id/runtime-state")
    suspend fun resetSession(id: String): JsonElement = Api.post("/agents/$id/runtime-state/reset-session", jsonOf("taskKey" to null))
    suspend fun instructions(id: String): InstructionsBundle = Api.get("/agents/$id/instructions-bundle")
    suspend fun instructionsFile(id: String, path: String): InstructionsFileDetail = Api.get("/agents/$id/instructions-bundle/file", mapOf("path" to path))
    suspend fun saveInstructionsFile(id: String, path: String, content: String): InstructionsFileDetail =
        Api.put("/agents/$id/instructions-bundle/file", jsonOf("path" to path, "content" to content))
    suspend fun updateAgent(id: String, body: JsonObject): Agent = Api.patch("/agents/$id", body)

    // Runs
    suspend fun runs(cid: String, agentId: String? = null, limit: Int = 50): List<HeartbeatRun> =
        Api.get("/companies/$cid/heartbeat-runs", mapOf("agentId" to agentId, "limit" to limit, "summary" to "true"))
    suspend fun run(id: String): HeartbeatRun = Api.get("/heartbeat-runs/$id")
    suspend fun runLog(id: String, offset: Long, limitBytes: Int = 256000): RunLogPage =
        Api.get("/heartbeat-runs/$id/log", mapOf("offset" to offset, "limitBytes" to limitBytes))
    suspend fun runEvents(id: String, afterSeq: Long = 0, limit: Int = 200): List<RunEvent> =
        Api.get("/heartbeat-runs/$id/events", mapOf("afterSeq" to afterSeq, "limit" to limit))
    suspend fun cancelRun(id: String): JsonElement = Api.post("/heartbeat-runs/$id/cancel")
    suspend fun runIssues(id: String): List<IssueSummaryRef> = Api.get("/heartbeat-runs/$id/issues")

    // Approvals
    suspend fun approvals(cid: String, status: String? = null): List<Approval> = Api.get("/companies/$cid/approvals", mapOf("status" to status))
    suspend fun approval(id: String): Approval = Api.get("/approvals/$id")
    suspend fun approvalComments(id: String): List<ApprovalComment> = Api.get("/approvals/$id/comments")
    suspend fun addApprovalComment(id: String, body: String): ApprovalComment = Api.post("/approvals/$id/comments", jsonOf("body" to body))
    suspend fun approvalIssues(id: String): List<Issue> = Api.get("/approvals/$id/issues")
    suspend fun decideApproval(id: String, action: String, note: String?): Approval =
        Api.post("/approvals/$id/$action", jsonOf("decisionNote" to note?.takeIf { it.isNotBlank() }))

    // Costs
    suspend fun costSummary(cid: String, from: String? = null): CostSummary = Api.get("/companies/$cid/costs/summary", mapOf("from" to from))
    suspend fun costByAgent(cid: String, from: String? = null): List<CostByAgent> = Api.get("/companies/$cid/costs/by-agent", mapOf("from" to from))
    suspend fun costByProvider(cid: String, from: String? = null): List<CostByProvider> = Api.get("/companies/$cid/costs/by-provider", mapOf("from" to from))
    suspend fun quotas(cid: String): CachedQuota = Api.get("/companies/$cid/costs/quota-windows/cached")
    suspend fun budgets(cid: String): BudgetOverview = Api.get("/companies/$cid/budgets/overview")

    // Projects, goals, routines, search
    suspend fun projects(cid: String): List<Project> = Api.get("/companies/$cid/projects")
    suspend fun project(id: String): Project = Api.get("/projects/$id")
    suspend fun createProject(cid: String, body: JsonObject): Project = Api.post("/companies/$cid/projects", body)
    suspend fun goals(cid: String): List<Goal> = Api.get("/companies/$cid/goals")
    suspend fun routines(cid: String): List<Routine> = Api.get("/companies/$cid/routines")
    suspend fun runRoutine(id: String): JsonElement = Api.post("/routines/$id/run", jsonOf("source" to "manual"))
    suspend fun updateRoutine(id: String, body: JsonObject): JsonElement = Api.patch("/routines/$id", body)
    suspend fun routine(id: String): Routine = Api.get("/routines/$id")
    suspend fun createRoutineTrigger(id: String, body: JsonObject): JsonElement = Api.post("/routines/$id/triggers", body)
    suspend fun updateRoutineTrigger(triggerId: String, body: JsonObject): JsonElement = Api.patch("/routine-triggers/$triggerId", body)
    suspend fun search(cid: String, q: String): SearchResponse = Api.get("/companies/$cid/search", mapOf("q" to q, "limit" to 40))
}
