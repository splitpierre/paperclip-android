package com.bu.paperclip.ui

import androidx.compose.runtime.key
import kotlinx.coroutines.CancellationException
import com.bu.paperclip.data.ReadCache
import com.bu.paperclip.net.AppJson
import com.bu.paperclip.data.Repo
import com.bu.paperclip.data.IssueStore
import com.bu.paperclip.data.AuthSession
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bu.paperclip.data.AppState
import com.bu.paperclip.net.Api
import com.bu.paperclip.net.AuthState
import com.bu.paperclip.net.LiveEvents
import com.bu.paperclip.ui.components.AppBottomBar
import com.bu.paperclip.ui.components.BarItem
import com.bu.paperclip.ui.components.Links
import com.bu.paperclip.ui.components.errorMessage
import com.bu.paperclip.ui.screens.*
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

private sealed class Phase {
    data object Boot : Phase()
    data object Setup : Phase()
    data object Login : Phase()
    data class Failed(val message: String) : Phase()
    data object NoCompany : Phase()
    data object Ready : Phase()
}

@Composable
fun AppRoot(pendingLink: String?, onLinkConsumed: () -> Unit) {
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf<Phase>(Phase.Boot) }
    var bootKey by remember { mutableStateOf(0) }
    val auth by Api.auth.collectAsState()
    val currentUser by AppState.me.collectAsState()

    LaunchedEffect(bootKey) {
        val s = AppState.settings.current()
        if (s.baseUrl.isEmpty()) { phase = Phase.Setup; return@LaunchedEffect }
        Api.configure(s.baseUrl, s.apiToken)
        phase = Phase.Boot
        val cache = ReadCache
        if (cache.restore(Api.baseUrl, Api.bearer)) {
            runCatching {
                cache.only {
                    AppState.me.value = Api.decode(AuthSession.serializer(), cache.read("local:session")).user
                    AppState.loadCompanies(s.companyId)
                    if (AppState.company.value != null) phase = Phase.Ready
                    AppState.refreshLookups()
                }
            }
        }
        try {
            val session = if (Api.bearer == null) Repo.session() else AuthSession()
            if (session.user == null && Api.bearer == null) { phase = Phase.Login; return@LaunchedEffect }
            if (AppState.me.value?.id != session.user?.id) {
                phase = Phase.Boot
                IssueStore.clear()
                AppState.agents.value = emptyList(); AppState.users.value = emptyMap(); AppState.projects.value = emptyList()
            }
            AppState.me.value = session.user
            ReadCache.select(Api.baseUrl, session.user?.id ?: "board", Api.bearer)
            cache.fetch("local:session") { AppJson.encodeToJsonElement(AuthSession.serializer(), session) }
            AppState.loadCompanies(s.companyId)
            if (AppState.company.value == null) { phase = Phase.NoCompany; return@LaunchedEffect }
            phase = Phase.Ready
            AppState.refreshLookups()
        } catch (c: CancellationException) { throw c }
        catch (t: Throwable) {
            if (Api.auth.value == AuthState.SignedOut) phase = Phase.Login
            else if (phase != Phase.Ready) phase = Phase.Failed(errorMessage(t))
            else AppState.toast("Showing saved data. " + errorMessage(t))
        }
    }

    // A session that expires mid-use (Cloudflare Access or Paperclip) drops back to the sign-in browser.
    LaunchedEffect(auth) {
        if (auth == AuthState.SignedOut && phase == Phase.Ready) phase = Phase.Login
    }

    when (val p = phase) {
        Phase.Boot -> SplashScreen()
        Phase.Setup -> SetupScreen(Api.baseUrl, Api.bearer ?: "") { url, token ->
            scope.launch { AppState.settings.setBaseUrl(url); AppState.settings.setApiToken(token); bootKey++ }
        }
        Phase.Login -> if (Api.bearer != null) SplashScreen("The API key was rejected.", onRetry = { phase = Phase.Setup }) else SignInScreen(
            baseUrl = Api.baseUrl,
            onSignedIn = {
                ReadCache.forget()
                IssueStore.clear()
                bootKey++
            },
            onChangeServer = { phase = Phase.Setup },
        )
        is Phase.Failed -> SplashScreen(p.message, onRetry = { bootKey++ }, onSignIn = { phase = Phase.Login })
        Phase.NoCompany -> SplashScreen("Your account has no Paperclip company yet.", onRetry = { bootKey++ })
        Phase.Ready -> key(Api.baseUrl, currentUser?.id) {
            MainShell(pendingLink, onLinkConsumed, onSignedOut = { phase = Phase.Login })
        }
    }
}


@Composable
private fun MainShell(pendingLink: String?, onLinkConsumed: () -> Unit, onSignedOut: () -> Unit) {
    val controller = rememberNavController()
    val nav = remember(controller) { Nav(controller) }
    val backStack by controller.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val company by AppState.company.collectAsState()
    val badges by AppState.badges.collectAsState()
    val decisionsBadge by AppState.decisionsBadge.collectAsState()
    val snackHost = remember { SnackbarHostState() }
    val snack by AppState.snack.collectAsState()

    LaunchedEffect(snack) {
        snack?.let { snackHost.showSnackbar(it); AppState.clearToast() }
    }

    DisposableEffect(nav) {
        Links.handler = { nav.handleLink(it) }
        onDispose { Links.handler = { false } }
    }

    LaunchedEffect(pendingLink) {
        if (pendingLink != null) { nav.handleLink(pendingLink); onLinkConsumed() }
    }

    // Live socket follows the selected company and the app's foreground state.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(company?.id, lifecycle) {
        val cid = company?.id
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> if (cid != null) { LiveEvents.start(cid); IssueStore.start(cid) }
                Lifecycle.Event.ON_STOP -> { LiveEvents.stop(); IssueStore.stop() }
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        if (cid != null && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) { LiveEvents.start(cid); IssueStore.start(cid) }
        onDispose { lifecycle.removeObserver(obs); LiveEvents.stop(); IssueStore.stop() }
    }

    // The local task mirror behind the Tasks views and sub-task trees.


    // Keep agent names/statuses and badge counts fresh.
    LaunchedEffect(company?.id) {
        LiveEvents.events
            .filter { it.type == "agent.status" || it.type == "activity.logged" || it.type.startsWith("heartbeat.run.status") }
            .debounce(800)
            .collect {
                if (it.type == "agent.status" || it.s("entityType") == "agent") AppState.refreshAgents()
                AppState.refreshBadges()
            }
    }

    CompositionLocalProvider(LocalNav provides nav) {
    Scaffold(
        // Inner screens own the status-bar inset; this scaffold only reserves room for the bottom bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        // Without the bottom bar (pushed screens) keep toasts above the system navigation buttons.
        snackbarHost = { SnackbarHost(snackHost, if (route in Routes.TABS) Modifier else Modifier.navigationBarsPadding()) },
        bottomBar = {
            if (route in Routes.TABS) {
                AppBottomBar(
                    items = AppTabs.map { t ->
                        val badge = when (t.route) {
                            Routes.DECISIONS -> decisionsBadge
                            Routes.MORE -> badges.inbox + badges.approvals
                            else -> 0
                        }
                        BarItem(t.route, t.label, t.icon, t.selectedIcon, badge)
                    },
                    selected = route,
                    onSelect = { nav.tab(it) },
                )
            }
        },
    ) { pad ->
        NavHost(
            navController = controller,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier.padding(pad).consumeWindowInsets(pad),
            // Tabs cross-fade; pushed screens slide in a little from the side and slide back out.
            enterTransition = {
                if (targetState.destination.route in Routes.TABS) fadeIn(tween(160))
                else fadeIn(tween(200)) + slideInHorizontally(tween(260)) { it / 6 }
            },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(160)) },
            popExitTransition = {
                if (initialState.destination.route in Routes.TABS) fadeOut(tween(120))
                else fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { it / 6 }
            },
        ) {
            composable(Routes.DASHBOARD) { DashboardScreen(nav) }
            composable(Routes.DECISIONS) { DecisionsScreen(nav) }
            composable(Routes.INBOX) { InboxScreen(nav) }
            composable(Routes.ISSUES) { IssuesScreen(nav) }
            composable(Routes.AGENTS) { AgentsScreen(nav) }
            composable(Routes.MORE) { MoreScreen(nav) }
            composable(Routes.ISSUE, listOf(navArgument("id") { type = NavType.StringType })) {
                IssueDetailScreen(nav, it.arguments?.getString("id")!!)
            }
            composable(
                Routes.NEW_ISSUE,
                listOf(
                    navArgument("parentId") { type = NavType.StringType; defaultValue = "" },
                    navArgument("projectId") { type = NavType.StringType; defaultValue = "" },
                    navArgument("assigneeAgentId") { type = NavType.StringType; defaultValue = "" },
                ),
            ) {
                val a = it.arguments
                NewIssueScreen(
                    nav,
                    a?.getString("parentId")?.ifEmpty { null },
                    a?.getString("projectId")?.ifEmpty { null },
                    a?.getString("assigneeAgentId")?.ifEmpty { null },
                )
            }
            composable(Routes.DOCUMENT) { DocumentScreen(nav, it.arguments?.getString("issueId")!!, it.arguments?.getString("key")!!) }
            composable(Routes.AGENT) { AgentDetailScreen(nav, it.arguments?.getString("id")!!) }
            composable(Routes.RUN) { RunScreen(nav, it.arguments?.getString("id")!!) }
            composable(Routes.INSTRUCTIONS) { InstructionsScreen(nav, it.arguments?.getString("agentId")!!) }
            composable(Routes.APPROVALS) { ApprovalsScreen(nav) }
            composable(Routes.APPROVAL) { ApprovalDetailScreen(nav, it.arguments?.getString("id")!!) }
            composable(Routes.COSTS) { CostsScreen(nav) }
            composable(Routes.PROJECTS) { ProjectsScreen(nav) }
            composable(Routes.PROJECT) { ProjectDetailScreen(nav, it.arguments?.getString("id")!!) }
            composable(Routes.GOALS) { GoalsScreen(nav) }
            composable(Routes.ROUTINES) { RoutinesScreen(nav) }
            composable(Routes.ROUTINE) { RoutineEditScreen(nav, it.arguments?.getString("id")!!) }
            composable(Routes.ACTIVITY) { ActivityScreen(nav) }
            composable(Routes.SEARCH) { SearchScreen(nav) }
            composable(Routes.SETTINGS) { SettingsScreen(nav, onSignedOut) }
        }
    }
    }
}
