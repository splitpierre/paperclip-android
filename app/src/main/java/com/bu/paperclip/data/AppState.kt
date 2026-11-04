package com.bu.paperclip.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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

    /** A snackbar message; [sticky] ones stay until dismissed, for things the user must not miss. */
    data class Snack(val message: String, val sticky: Boolean = false)

    private val _snack = MutableStateFlow<Snack?>(null)
    val snack: StateFlow<Snack?> = _snack
    fun toast(msg: String, sticky: Boolean = false) { _snack.value = Snack(msg, sticky) }
    fun clearToast() { _snack.value = null }

    val cid: String get() = company.value?.id ?: ""

    fun agent(id: String?): Agent? = id?.let { i -> agents.value.firstOrNull { it.id == i } }
    fun agentName(id: String?): String = agent(id)?.name ?: "Agent"
    fun userName(id: String?): String {
        if (id == null) return "Someone"
        if (id == me.value?.id) return "You"
        val u = users.value[id]
        return u?.name?.takeIf { it.isNotBlank() } ?: u?.email ?: "User"
    }
    fun project(id: String?): Project? = id?.let { i -> projects.value.firstOrNull { it.id == i } }

    suspend fun loadSession() {
        me.value = runCatching { Repo.session().user }.getOrNull()
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

    suspend fun refreshLookups() {
        val c = cid.ifEmpty { return }
        runCatching { agents.value = Repo.agents(c).filter { it.status != "terminated" } }
        runCatching { users.value = Repo.userDirectory(c).users.mapNotNull { e -> e.user?.let { e.principalId to it } }.toMap() }
        runCatching { projects.value = Repo.projects(c) }
        refreshBadges()
    }

    suspend fun refreshAgents() {
        val c = cid.ifEmpty { return }
        runCatching { agents.value = Repo.agents(c).filter { it.status != "terminated" } }
    }

    suspend fun refreshBadges() {
        val c = cid.ifEmpty { return }
        runCatching { badges.value = Repo.badges(c) }
        runCatching { decisionsBadge.value = Repo.attention(c, limit = 1).deskBadgeCount }
    }
}
