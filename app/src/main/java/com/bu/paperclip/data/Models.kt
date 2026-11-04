@file:UseSerializers(LenientLongSerializer::class, LenientIntSerializer::class)

package com.bu.paperclip.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

@Serializable
data class Company(
    val id: String,
    val name: String = "",
    val description: String? = null,
    val status: String = "active",
    val issuePrefix: String = "",
    val budgetMonthlyCents: Long? = null,
    val spentMonthlyCents: Long? = null,
    val logoUrl: String? = null,
)

@Serializable
data class SessionUser(val id: String = "", val email: String? = null, val name: String? = null, val image: String? = null)

@Serializable
data class AuthSession(val user: SessionUser? = null)

@Serializable
data class Label(val id: String = "", val name: String = "", val color: String? = null)

@Serializable
data class IssueSummaryRef(
    val id: String = "",
    val identifier: String? = null,
    val title: String? = null,
    val status: String? = null,
    val priority: String? = null,
)

@Serializable
data class ActiveRunRef(
    val id: String = "",
    val status: String = "",
    val agentId: String? = null,
    val startedAt: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class DocumentSummary(
    val id: String = "",
    val key: String = "",
    val title: String? = null,
    val latestRevisionNumber: Int? = null,
    val updatedAt: String? = null,
)

@Serializable
data class Issue(
    val id: String,
    val companyId: String = "",
    val identifier: String? = null,
    val issueNumber: Int? = null,
    val title: String = "",
    val description: String? = null,
    val status: String = "todo",
    val priority: String = "medium",
    val workMode: String? = null,
    val projectId: String? = null,
    val goalId: String? = null,
    val parentId: String? = null,
    val ancestors: List<IssueSummaryRef>? = null,
    val assigneeAgentId: String? = null,
    val assigneeUserId: String? = null,
    val createdByAgentId: String? = null,
    val createdByUserId: String? = null,
    val executionRunId: String? = null,
    val startedAt: String? = null,
    val completedAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val lastActivityAt: String? = null,
    val labels: List<Label>? = null,
    val labelIds: List<String>? = null,
    val blockedBy: List<IssueSummaryRef>? = null,
    val blocks: List<IssueSummaryRef>? = null,
    val documentSummaries: List<DocumentSummary>? = null,
    val activeRun: ActiveRunRef? = null,
    val isUnreadForMe: Boolean? = null,
    val archivedAt: String? = null,
    val liveDescendantCount: Int? = null,
    val project: ProjectRef? = null,
    val originKind: String? = null,
) {
    val ref: String get() = identifier ?: id.take(8)
}

@Serializable
data class ProjectRef(val id: String = "", val name: String = "", val color: String? = null)

@Serializable
data class CommentPresentation(
    val kind: String = "message",
    val tone: String = "neutral",
    val title: String? = null,
    val detailsDefaultOpen: Boolean = false,
)

@Serializable
data class IssueComment(
    val id: String,
    val issueId: String = "",
    val authorType: String = "user",
    val authorAgentId: String? = null,
    val authorUserId: String? = null,
    val derivedAuthorAgentId: String? = null,
    val body: String = "",
    val presentation: CommentPresentation? = null,
    val createdByRunId: String? = null,
    val deletedAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class Interaction(
    val id: String,
    val issueId: String = "",
    val kind: String = "",
    val status: String = "pending",
    val title: String? = null,
    val summary: String? = null,
    val createdByAgentId: String? = null,
    val createdByUserId: String? = null,
    val addresseeAgentId: String? = null,
    val addresseeUserId: String? = null,
    val resolvedByUserId: String? = null,
    val resolvedByAgentId: String? = null,
    val payload: JsonObject? = null,
    val result: JsonObject? = null,
    val createdAt: String? = null,
    val resolvedAt: String? = null,
)

@Serializable
data class IssueDocument(
    val id: String = "",
    val key: String = "",
    val title: String? = null,
    val body: String = "",
    val latestRevisionId: String? = null,
    val latestRevisionNumber: Int? = null,
    val updatedAt: String? = null,
    val updatedByAgentId: String? = null,
    val updatedByUserId: String? = null,
)

@Serializable
data class Attachment(
    val id: String,
    val originalFilename: String? = null,
    val contentType: String? = null,
    val byteSize: Long? = null,
    val contentPath: String? = null,
    val openPath: String? = null,
    val downloadPath: String? = null,
    val createdAt: String? = null,
    val createdByAgentId: String? = null,
)

@Serializable
data class WorkProduct(
    val id: String,
    val type: String = "",
    val provider: String? = null,
    val title: String? = null,
    val url: String? = null,
    val status: String? = null,
    val reviewState: String? = null,
    val isPrimary: Boolean = false,
    val summary: String? = null,
    val metadata: JsonObject? = null,
    val createdAt: String? = null,
)

@Serializable
data class Agent(
    val id: String,
    val companyId: String = "",
    val name: String = "",
    val urlKey: String? = null,
    val role: String = "general",
    val title: String? = null,
    val icon: String? = null,
    val avatarUrl: String? = null,
    val status: String = "idle",
    val reportsTo: String? = null,
    val adapterType: String? = null,
    val adapterConfig: JsonObject? = null,
    val budgetMonthlyCents: Long? = null,
    val spentMonthlyCents: Long? = null,
    val pauseReason: String? = null,
    val pausedAt: String? = null,
    val errorReason: String? = null,
    val lastHeartbeatAt: String? = null,
    val createdAt: String? = null,
) {
    val model: String? get() = (adapterConfig?.get("model") as? kotlinx.serialization.json.JsonPrimitive)?.content
}

@Serializable
data class HeartbeatRun(
    val id: String,
    val agentId: String = "",
    val status: String = "",
    val invocationSource: String? = null,
    val triggerDetail: String? = null,
    val startedAt: String? = null,
    val finishedAt: String? = null,
    val createdAt: String? = null,
    val error: String? = null,
    val errorCode: String? = null,
    val logBytes: Long? = null,
    val lastOutputBytes: Long? = null,
    val usageJson: JsonElement? = null,
    val contextSnapshot: JsonObject? = null,
    val currentStatusMessage: String? = null,
    val currentToolName: String? = null,
    val lastAssistantSnippet: String? = null,
    val livenessState: String? = null,
    val retryOfRunId: String? = null,
) {
    val issueId: String? get() = (contextSnapshot?.get("issueId") as? kotlinx.serialization.json.JsonPrimitive)?.content
    val isLive: Boolean get() = status == "queued" || status == "running" || status == "scheduled_retry"
}

@Serializable
data class LiveRun(
    val id: String,
    val status: String = "",
    val agentId: String = "",
    val agentName: String? = null,
    val adapterType: String? = null,
    val issueId: String? = null,
    val startedAt: String? = null,
    val finishedAt: String? = null,
    val createdAt: String? = null,
    val currentStatusMessage: String? = null,
    val currentToolName: String? = null,
    val lastAssistantSnippet: String? = null,
    val lastEventAt: String? = null,
    val invocationSource: String? = null,
)

@Serializable
data class RunLogPage(val runId: String = "", val content: String = "", val nextOffset: Long? = null)

@Serializable
data class RunEvent(
    val seq: Long = 0,
    val eventType: String = "",
    val stream: String? = null,
    val level: String? = null,
    val message: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class DashboardAgents(val active: Int = 0, val running: Int = 0, val paused: Int = 0, val error: Int = 0)

@Serializable
data class DashboardTasks(val open: Int = 0, val inProgress: Int = 0, val blocked: Int = 0, val done: Int = 0)

@Serializable
data class DashboardCosts(val monthSpendCents: Long = 0, val monthBudgetCents: Long = 0, val monthUtilizationPercent: Double = 0.0)

@Serializable
data class DashboardBudgets(val activeIncidents: Int = 0, val pendingApprovals: Int = 0, val pausedAgents: Int = 0, val pausedProjects: Int = 0)

@Serializable
data class RunActivityDay(val date: String = "", val succeeded: Int = 0, val failed: Int = 0, val recovered: Int = 0, val other: Int = 0, val total: Int = 0)

@Serializable
data class DashboardSummary(
    val agents: DashboardAgents = DashboardAgents(),
    val tasks: DashboardTasks = DashboardTasks(),
    val costs: DashboardCosts = DashboardCosts(),
    val pendingApprovals: Int = 0,
    val budgets: DashboardBudgets = DashboardBudgets(),
    val runActivity: List<RunActivityDay> = emptyList(),
)

@Serializable
data class ActivityEvent(
    val id: String = "",
    val actorType: String = "",
    val actorId: String? = null,
    val action: String = "",
    val entityType: String? = null,
    val entityId: String? = null,
    val agentId: String? = null,
    val runId: String? = null,
    val details: JsonObject? = null,
    val createdAt: String? = null,
)

@Serializable
data class SidebarBadges(val inbox: Int = 0, val approvals: Int = 0, val failedRuns: Int = 0, val joinRequests: Int = 0)

@Serializable
data class AttentionSubject(
    val kind: String = "",
    val id: String = "",
    val title: String? = null,
    val identifier: String? = null,
    val status: String? = null,
    val href: String? = null,
    val metadata: JsonObject? = null,
)

@Serializable
data class AttentionItem(
    val id: String,
    val sourceKind: String = "",
    val subject: AttentionSubject = AttentionSubject(),
    val relatedIssue: AttentionSubject? = null,
    val whyNow: String? = null,
    val severity: String = "medium",
    val activityAt: String? = null,
    val createdAt: String? = null,
    val dismissalKey: String? = null,
    val originAgentName: String? = null,
    val inlineResolvable: Boolean = false,
    val detail: JsonObject? = null,
    val decisionVerbs: List<AttentionVerb> = emptyList(),
    val dismissal: AttentionDismissal? = null,
    val decideBy: String? = null,
    val snoozedUntil: String? = null,
    val expiresAt: String? = null,
    val shelf: Boolean = false,
    val queues: List<AttentionQueueRef> = emptyList(),
)

@Serializable
data class AttentionVerb(val id: String = "", val label: String = "", val description: String? = null)

@Serializable
data class AttentionDismissal(val kind: String = "dismiss", val dismissedAt: String? = null, val snoozedUntil: String? = null, val isActive: Boolean = false)

@Serializable
data class AttentionQueueRef(val key: String = "", val title: String = "")

/** An agent-proposed choice (the Decisions desk). Effects run on the server when an option is chosen. */
@Serializable
data class Decision(
    val id: String,
    val title: String = "",
    val body: String = "",
    val options: List<DecisionOption> = emptyList(),
    val inputs: List<DecisionInput>? = null,
    val status: String = "open",
    val executionStatus: String? = null,
    val chosenOptionId: String? = null,
    val decidedAt: String? = null,
    val expiresAt: String? = null,
    val originAgentId: String? = null,
    val originIssueId: String? = null,
    val originRunId: String? = null,
    val targetSnapshots: Map<String, DecisionTargetSnapshot>? = null,
    val targetChanged: Map<String, Boolean>? = null,
    val executions: List<DecisionExecution>? = null,
    val metadata: JsonObject? = null,
    val createdAt: String? = null,
)

@Serializable
data class DecisionOption(
    val id: String,
    val label: String = "",
    val description: String? = null,
    val style: String? = null,
    val effects: List<JsonObject> = emptyList(),
)

@Serializable
data class DecisionInput(val id: String, val label: String = "", val placeholder: String? = null, val required: Boolean = false, val maxLength: Int? = null)

@Serializable
data class DecisionTargetSnapshot(val status: String? = null, val descendantIds: List<String>? = null, val descendantCount: Int? = null)

@Serializable
data class DecisionExecution(val id: String = "", val effectType: String = "", val targetIssueId: String? = null, val status: String = "", val error: String? = null)

@Serializable
data class AttentionFeed(
    val totalCount: Int = 0,
    val deskBadgeCount: Int = 0,
    val nextCursor: String? = null,
    val items: List<AttentionItem> = emptyList(),
)

@Serializable
data class Approval(
    val id: String,
    val type: String = "",
    val status: String = "",
    val requestedByAgentId: String? = null,
    val requestedByUserId: String? = null,
    val payload: JsonObject? = null,
    val decisionNote: String? = null,
    val decidedAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class ApprovalComment(
    val id: String,
    val authorAgentId: String? = null,
    val authorUserId: String? = null,
    val body: String = "",
    val createdAt: String? = null,
)

@Serializable
data class CostSummary(val spendCents: Long = 0, val budgetCents: Long = 0, val utilizationPercent: Double = 0.0)

@Serializable
data class CostByAgent(
    val agentId: String = "",
    val agentName: String? = null,
    val agentStatus: String? = null,
    val costCents: Long = 0,
    val inputTokens: Long = 0,
    val cachedInputTokens: Long = 0,
    val outputTokens: Long = 0,
    val apiRunCount: Int = 0,
    val subscriptionRunCount: Int = 0,
)

@Serializable
data class CostByProvider(
    val provider: String = "",
    val biller: String? = null,
    val billingType: String? = null,
    val model: String? = null,
    val costCents: Long = 0,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val apiRunCount: Int = 0,
    val subscriptionRunCount: Int = 0,
)

@Serializable
data class QuotaWindow(val label: String = "", val usedPercent: Double? = null, val resetsAt: String? = null, val valueLabel: String? = null, val detail: String? = null)

@Serializable
data class ProviderQuota(val provider: String = "", val ok: Boolean = true, val error: String? = null, val windows: List<QuotaWindow> = emptyList())

@Serializable
data class CachedQuota(val asOf: String? = null, val results: List<ProviderQuota> = emptyList())

@Serializable
data class BudgetPolicy(
    val id: String? = null,
    val scopeType: String = "",
    val scopeId: String? = null,
    val scopeName: String? = null,
    val amount: Long = 0,
    val observedAmount: Long = 0,
    val status: String? = null,
    val windowKind: String? = null,
)

@Serializable
data class BudgetOverview(
    val policies: List<BudgetPolicy> = emptyList(),
    val activeIncidents: List<JsonObject> = emptyList(),
    val pausedAgentCount: Int = 0,
    val pausedProjectCount: Int = 0,
    val pendingApprovalCount: Int = 0,
)

@Serializable
data class Project(
    val id: String,
    val name: String = "",
    val description: String? = null,
    val status: String = "",
    val color: String? = null,
    val leadAgentId: String? = null,
    val targetDate: String? = null,
    val taskCount: Int? = null,
    val archivedAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class Goal(
    val id: String,
    val title: String = "",
    val description: String? = null,
    val level: String = "",
    val status: String = "",
    val parentId: String? = null,
    val ownerAgentId: String? = null,
)

@Serializable
data class RoutineLastRun(val id: String? = null, val status: String? = null, val createdAt: String? = null)

@Serializable
data class RoutineTrigger(
    val id: String = "",
    val kind: String = "",
    val label: String? = null,
    val enabled: Boolean = true,
    val cronExpression: String? = null,
    val timezone: String? = null,
    val nextRunAt: String? = null,
    val lastFiredAt: String? = null,
    val archived: Boolean = false,
)

@Serializable
data class Routine(
    val id: String,
    val title: String = "",
    val description: String? = null,
    val status: String = "",
    val assigneeAgentId: String? = null,
    val projectId: String? = null,
    val lastTriggeredAt: String? = null,
    val triggers: List<RoutineTrigger>? = null,
    val lastRun: RoutineLastRun? = null,
    val priority: String = "medium",
    val concurrencyPolicy: String = "coalesce_if_active",
    val catchUpPolicy: String = "skip_missed",
    val latestRevisionId: String? = null,
    val variables: JsonArray? = null,
    val managedByPlugin: JsonObject? = null,
)

@Serializable
data class SearchResult(
    val id: String = "",
    val type: String = "",
    val title: String? = null,
    val href: String? = null,
    val snippet: String? = null,
    val issue: IssueSummaryRef? = null,
    val updatedAt: String? = null,
)

@Serializable
data class SearchResponse(val results: List<SearchResult> = emptyList())

@Serializable
data class InstructionsFile(val path: String = "", val size: Long? = null, val isEntryFile: Boolean = false, val editable: Boolean = true)

@Serializable
data class InstructionsBundle(val entryFile: String? = null, val editable: Boolean = true, val files: List<InstructionsFile> = emptyList())

@Serializable
data class InstructionsFileDetail(val path: String = "", val content: String = "", val editable: Boolean = true)

@Serializable
data class RuntimeState(
    val sessionId: String? = null,
    val lastRunStatus: String? = null,
    val totalInputTokens: Long = 0,
    val totalOutputTokens: Long = 0,
    val totalCachedInputTokens: Long = 0,
    val totalCostCents: Long = 0,
    val lastError: String? = null,
)

@Serializable
data class IssueCostSummary(val costCents: Long = 0, val inputTokens: Long = 0, val outputTokens: Long = 0, val runCount: Int = 0, val runtimeMs: Long = 0)

@Serializable
data class UserDirectoryEntry(val principalId: String = "", val user: SessionUser? = null)

@Serializable
data class UserDirectory(val users: List<UserDirectoryEntry> = emptyList())
