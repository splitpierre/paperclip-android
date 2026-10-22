package com.bu.paperclip.data

import com.bu.paperclip.util.catching

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import com.bu.paperclip.net.Api

/** Process-wide cache of the selected company and the lookups every screen needs (names, avatars). */
object AppState {
    lateinit var settings: SettingsStore
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val companies = MutableStateFlow<List<Company>>(emptyList())
    val company = MutableStateFlow<Company?>(null)
    val me = MutableStateFlow<SessionUser?>(null)
    val agents = MutableStateFlow<List<Agent>>(emptyList())
    val users = MutableStateFlow<Map<String, SessionUser>>(emptyMap())
    val projects = MutableStateFlow<List<Project>>(emptyList())
    val badges = MutableStateFlow(SidebarBadges())
    /** Items on the Decisions desk that are new today or due now (the web sidebar's Decisions count). */
    val decisionsBadge = MutableStateFlow(0)

    private val _snack = MutableStateFlow<String?>(null)
    val snack: StateFlow<String?> = _snack
    fun toast(msg: String) { _snack.value = msg }
    fun clearToast() { _snack.value = null }

    val cid: String get() = company.value?.id ?: ""

    private val agentIndex = LookupIndex<Agent> { it.id }
    private val projectIndex = LookupIndex<Project> { it.id }
    fun agent(id: String?): Agent? = agentIndex.get(agents.value, id)
    fun agentName(id: String?): String = agent(id)?.name ?: "Agent"
    fun userName(id: String?): String {
        if (id == null) return "Someone"
        if (id == me.value?.id) return "You"
        val u = users.value[id]
        return u?.name?.takeIf { it.isNotBlank() } ?: u?.email ?: "User"
    }
    fun project(id: String?): Project? = projectIndex.get(projects.value, id)

    suspend fun loadSession() {
        me.value = catching { Repo.session().user }.getOrNull()
    }

    suspend fun loadCompanies(preferredId: String) {
        val list = Repo.companies().filter { it.status != "archived" }
        companies.value = list
        val pick = list.firstOrNull { it.id == preferredId } ?: list.firstOrNull()
        company.value = pick
        if (pick != null && pick.id != preferredId) settings.setCompanyId(pick.id)
    }

    fun selectCompany(c: Company) {
        company.value = c
        agents.value = emptyList(); users.value = emptyMap(); projects.value = emptyList()
        scope.launch { settings.setCompanyId(c.id); refreshLookups() }
    }

    suspend fun refreshLookups() = coroutineScope {
        val c = cid.ifEmpty { return@coroutineScope }
        launch { catching { val loaded = Repo.agents(c).filter { it.status != "terminated" }; if (cid == c) agents.value = loaded } }
        launch { catching { val loaded = Repo.userDirectory(c).users.mapNotNull { e -> e.user?.let { e.principalId to it } }.toMap(); if (cid == c) users.value = loaded } }
        launch { catching { val loaded = Repo.projects(c); if (cid == c) projects.value = loaded } }
        launch { refreshBadges() }
    }

    suspend fun refreshAgents() {
        val c = cid.ifEmpty { return }
        catching { val loaded = Repo.agents(c).filter { it.status != "terminated" }; if (cid == c) agents.value = loaded }
    }

    suspend fun refreshBadges() {
        val c = cid.ifEmpty { return }
        catching { val loaded = Repo.badges(c); if (cid == c) badges.value = loaded }
        catching { val loaded = Repo.attention(c, limit = 1).deskBadgeCount; if (cid == c) decisionsBadge.value = loaded }
    }
}
