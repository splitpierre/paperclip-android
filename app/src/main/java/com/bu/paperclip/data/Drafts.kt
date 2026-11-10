package com.bu.paperclip.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/** A file waiting to go with a draft: a picked document on the device, or an attachment already uploaded. */
@Serializable
data class DraftFile(
    val uri: String,
    val name: String,
    val size: Long? = null,
    val mime: String = "application/octet-stream",
    /** Set once the file is on the server (thread replies upload as soon as they are picked). */
    val attachmentId: String? = null,
    val contentPath: String? = null,
)

/** Everything typed or picked on the New task screen. */
@Serializable
data class IssueDraft(
    val title: String = "",
    val description: String = "",
    val status: String = "todo",
    val priority: String = "medium",
    val workMode: String = "standard",
    val assigneeAgentId: String? = null,
    val assigneeUserId: String? = null,
    val projectId: String? = null,
    val parentId: String? = null,
    val files: List<DraftFile> = emptyList(),
) {
    /** Choosing chips alone is not worth keeping; text or files are. */
    val isEmpty: Boolean get() = title.isBlank() && description.isBlank() && files.isEmpty()
}

/** An unsent reply in a task's thread. */
@Serializable
data class CommentDraft(val body: String = "", val files: List<DraftFile> = emptyList()) {
    val isEmpty: Boolean get() = body.isBlank() && files.none { it.attachmentId != null }
}

/** Unsubmitted answers to an ask_user_questions card. */
@Serializable
data class AnswerDraft(
    val selected: Map<String, List<String>> = emptyMap(),
    val otherActive: Map<String, Boolean> = emptyMap(),
    val otherText: Map<String, String> = emptyMap(),
) {
    val isEmpty: Boolean get() = otherText.values.all { it.isBlank() } && selected.values.all { it.isEmpty() }
}

/**
 * Unsent drafts on the device, one JSON file each under `root/<scope>/`, where the scope is a hash of
 * the server URL and company. Nothing here is ever sent anywhere, and sign-out does not touch it, so
 * a draft comes back after signing in to the same server and company again.
 */
class DraftStore(private val root: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val lock = Any()

    /** Directory name for [server] + [company]; trailing slashes and letter case of the host do not matter. */
    fun scope(server: String, company: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest((normalizeServer(server) + "\n" + company).toByteArray())
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }

    fun <T> load(server: String, company: String, key: String, serializer: KSerializer<T>): T? = synchronized(lock) {
        val f = file(server, company, key) ?: return null
        if (!f.isFile) return null
        runCatching { json.decodeFromString(serializer, f.readText()) }.getOrNull()
    }

    /** Writes [value] atomically (temp file + rename), so a kill mid-write keeps the previous draft. */
    fun <T> save(server: String, company: String, key: String, value: T, serializer: KSerializer<T>) = synchronized(lock) {
        val f = file(server, company, key) ?: return
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(json.encodeToString(serializer, value))
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    fun clear(server: String, company: String, key: String) = synchronized(lock) {
        file(server, company, key)?.delete()
        Unit
    }

    /** Keys with a saved draft in this server + company. */
    fun keys(server: String, company: String): Set<String> = synchronized(lock) {
        if (server.isBlank() || company.isBlank()) return emptySet()
        File(root, scope(server, company)).listFiles()
            ?.filter { it.isFile && it.name.endsWith(".json") }
            ?.map { it.name.removeSuffix(".json") }
            ?.toSet()
            .orEmpty()
    }

    private fun file(server: String, company: String, key: String): File? {
        if (server.isBlank() || company.isBlank() || key.isBlank()) return null
        return File(File(root, scope(server, company)), safeKey(key) + ".json")
    }

    companion object {
        fun normalizeServer(url: String): String {
            val t = url.trim().trimEnd('/')
            val schemeEnd = t.indexOf("://")
            if (schemeEnd < 0) return t.lowercase()
            val hostEnd = t.indexOf('/', schemeEnd + 3).let { if (it < 0) t.length else it }
            return t.substring(0, hostEnd).lowercase() + t.substring(hostEnd)
        }

        /** Keys become file names: anything outside `[A-Za-z0-9._-]` is replaced. */
        fun safeKey(key: String): String = key.replace(Regex("[^A-Za-z0-9._-]"), "_")

        fun newIssueKey(parentId: String?): String = if (parentId.isNullOrBlank()) "new-issue" else "new-issue-$parentId"
        fun commentKey(issueId: String): String = "comment-$issueId"
        fun answerKey(interactionId: String): String = "answer-$interactionId"

        /** Issue ids that have an unsent reply, from [keys]. */
        fun commentIssueIds(keys: Set<String>): Set<String> =
            keys.filter { it.startsWith("comment-") }.map { it.removePrefix("comment-") }.toSet()
    }
}

/** App-wide access to the [DraftStore] for the signed-in server and selected company. */
object Drafts {
    lateinit var store: DraftStore
    private val _commentIssueIds = MutableStateFlow<Set<String>>(emptySet())
    /** Tasks with an unsent reply in the current server + company (for the "Draft" marker). */
    val commentIssueIds: StateFlow<Set<String>> = _commentIssueIds

    private fun server() = com.bu.paperclip.net.Api.baseUrl
    private fun company() = AppState.cid
    private val ready get() = ::store.isInitialized

    fun <T> load(key: String, serializer: KSerializer<T>): T? =
        if (ready) store.load(server(), company(), key, serializer) else null

    fun <T> save(key: String, value: T, serializer: KSerializer<T>) {
        if (!ready) return
        store.save(server(), company(), key, value, serializer)
        refresh()
    }

    fun clear(key: String) {
        if (!ready) return
        store.clear(server(), company(), key)
        refresh()
    }

    /** Re-reads which tasks have drafts; call when the server or company changes. */
    fun refresh() {
        if (!ready) return
        _commentIssueIds.value = DraftStore.commentIssueIds(store.keys(server(), company()))
    }
}
