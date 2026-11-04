package com.bu.paperclip.ui

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.HowToVote
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.bu.paperclip.net.Api

object Routes {
    const val DASHBOARD = "dashboard"
    const val DECISIONS = "decisions"
    const val ISSUES = "issues"
    const val AGENTS = "agents"
    const val MORE = "more"
    val TABS = listOf(DASHBOARD, DECISIONS, ISSUES, AGENTS, MORE)

    const val INBOX = "inbox"
    const val ROUTINE = "routine/{id}"

    const val ISSUE = "issue/{id}"
    const val NEW_ISSUE = "newIssue?parentId={parentId}&projectId={projectId}&assigneeAgentId={assigneeAgentId}"
    const val QUICK_TASK = "quickTask"
    const val DOCUMENT = "document/{issueId}/{key}"
    const val AGENT = "agent/{id}"
    const val RUN = "run/{id}"
    const val INSTRUCTIONS = "instructions/{agentId}"
    const val APPROVALS = "approvals"
    const val APPROVAL = "approval/{id}"
    const val COSTS = "costs"
    const val PROJECTS = "projects"
    const val PROJECT = "project/{id}"
    const val GOALS = "goals"
    const val ROUTINES = "routines"
    const val ACTIVITY = "activity"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
}

data class AppTab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

/** The bottom-bar tabs, in order. */
val AppTabs = listOf(
    AppTab(Routes.DASHBOARD, "Home", Icons.Outlined.Home, Icons.Filled.Home),
    AppTab(Routes.DECISIONS, "Decisions", Icons.Outlined.HowToVote, Icons.Filled.HowToVote),
    AppTab(Routes.ISSUES, "Tasks", Icons.Outlined.Checklist, Icons.Filled.Checklist),
    AppTab(Routes.AGENTS, "Agents", Icons.Outlined.SmartToy, Icons.Filled.SmartToy),
    AppTab(Routes.MORE, "More", Icons.Outlined.Menu, Icons.Filled.Menu),
)

/** A decision to open when the Decisions screen next shows (from a notification or link). */
object DecisionFocus {
    val pending = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
}

/** The navigator for the signed-in shell; null outside it (sign-in screens). */
val LocalNav = staticCompositionLocalOf<Nav?> { null }

private fun enc(s: String) = Uri.encode(s)

/** Thin, typed wrapper over the NavController so screens never build route strings. */
class Nav(val controller: NavHostController) {
    fun back() { controller.popBackStack() }
    /** Closes every screen pushed on top of [route] (a tab), returning to it with its state intact. */
    fun backToTab(route: String) { if (!controller.popBackStack(route, inclusive = false)) tab(route) }
    fun tab(route: String) = controller.navigate(route) {
        popUpTo(controller.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    fun issue(id: String) = controller.navigate("issue/${enc(id)}")
    fun newIssue(parentId: String? = null, projectId: String? = null, assigneeAgentId: String? = null) =
        controller.navigate("newIssue?parentId=${enc(parentId ?: "")}&projectId=${enc(projectId ?: "")}&assigneeAgentId=${enc(assigneeAgentId ?: "")}")
    fun quickTask() = controller.navigate(Routes.QUICK_TASK)
    fun document(issueId: String, key: String) = controller.navigate("document/${enc(issueId)}/${enc(key)}")
    fun agent(id: String) = controller.navigate("agent/${enc(id)}")
    fun run(id: String) = controller.navigate("run/${enc(id)}")
    fun instructions(agentId: String) = controller.navigate("instructions/${enc(agentId)}")
    fun approvals() = controller.navigate(Routes.APPROVALS)
    fun approval(id: String) = controller.navigate("approval/${enc(id)}")
    fun costs() = controller.navigate(Routes.COSTS)
    fun projects() = controller.navigate(Routes.PROJECTS)
    fun project(id: String) = controller.navigate("project/${enc(id)}")
    fun goals() = controller.navigate(Routes.GOALS)
    fun routines() = controller.navigate(Routes.ROUTINES)
    fun routine(id: String) = controller.navigate("routine/${enc(id)}")
    fun inbox() = controller.navigate(Routes.INBOX)
    fun activity() = controller.navigate(Routes.ACTIVITY)
    fun search() = controller.navigate(Routes.SEARCH)
    fun settings() = controller.navigate(Routes.SETTINGS)

    private val decisionsRe = Regex("""(?:^|/)decisions(?:/?$|[?#])""")
    private val decisionIdRe = Regex("""[?&]decisionId=([0-9a-f-]{36})""")
    private val inboxRe = Regex("""(?:^|/)inbox(?:/|$|[?#])""")

    private val issueRe = Regex("""(?:^|/)issues/([A-Za-z][A-Za-z0-9]*-\d+|[0-9a-f-]{36})""")
    private val agentRe = Regex("""(?:^|/)agents/([^/?#]+)""")
    private val approvalRe = Regex("""(?:^|/)approvals/([0-9a-f-]{36})""")
    private val projectRe = Regex("""(?:^|/)projects/([^/?#]+)""")
    private val runRe = Regex("""(?:^|/)runs/([0-9a-f-]{36})""")

    /** Routes Paperclip links (mentions, /issues/X, full web-UI URLs) to native screens. */
    fun handleLink(link: String): Boolean {
        when {
            link.startsWith("agent://") -> { agent(link.removePrefix("agent://").substringBefore('?')); return true }
            link.startsWith("project://") -> { project(link.removePrefix("project://").substringBefore('?')); return true }
            link.startsWith("user://") || link.startsWith("skill://") || link.startsWith("routine://") || link.startsWith("pipeline://") -> return true
        }
        val local = when {
            link.startsWith("/") -> link
            Api.baseUrl.isNotEmpty() && link.startsWith(Api.baseUrl) -> link.removePrefix(Api.baseUrl)
            else -> return false
        }
        if (decisionsRe.containsMatchIn(local)) {
            DecisionFocus.pending.value = decisionIdRe.find(local)?.groupValues?.get(1)
            tab(Routes.DECISIONS); return true
        }
        if (inboxRe.containsMatchIn(local)) { inbox(); return true }
        runRe.find(local)?.let { run(it.groupValues[1]); return true }
        issueRe.find(local)?.let { issue(it.groupValues[1]); return true }
        approvalRe.find(local)?.let { approval(it.groupValues[1]); return true }
        agentRe.find(local)?.let { m -> if (m.groupValues[1] !in setOf("all", "active", "paused", "error", "builtin", "new")) { agent(m.groupValues[1]); return true } }
        projectRe.find(local)?.let { project(it.groupValues[1]); return true }
        return false
    }
}
