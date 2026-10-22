package com.bu.paperclip.data

import android.database.DatabaseUtils
import android.content.SharedPreferences
import com.bu.paperclip.util.catching

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.bu.paperclip.net.AppJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import java.security.MessageDigest
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Private, bounded read snapshots. Network refresh remains authoritative; mutations are never cached. */
object ReadCache {
    private class CacheOnly : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<CacheOnly>
    }
    class Miss : Exception("No saved data")
    private class Database(context: Context) : SQLiteOpenHelper(context, "read-cache.db", null, 2) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE snapshots (scope TEXT, url TEXT, body TEXT, saved INTEGER, size INTEGER NOT NULL, PRIMARY KEY(scope,url))")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                db.execSQL("ALTER TABLE snapshots ADD COLUMN size INTEGER NOT NULL DEFAULT 0")
                // One migration scan; routine writes only sum the small integer column.
                db.execSQL("UPDATE snapshots SET size=length(CAST(body AS BLOB))")
                db.execSQL("DELETE FROM snapshots WHERE size > 1048576")
            }
        }
    }
    private var database: Database? = null
    private var identities: SharedPreferences? = null
    private var identityKey: String? = null
    @Volatile private var namespace: String? = null
    @Volatile private var revision = 0L
    private val lock = Any()
    private val diskLock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = mutableMapOf<String, Deferred<JsonElement>>()

    fun initialize(context: Context) {
        database?.close()
        database = Database(context.applicationContext)
        identities = context.getSharedPreferences("cache-identities", Context.MODE_PRIVATE)
    }
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    fun restore(server: String, credential: String?): Boolean {
        val key = hash("$server\n${credential.orEmpty()}")
        synchronized(lock) {
            identityKey = key
            namespace = identities?.getString(key, null)
            revision++
            return namespace != null
        }
    }
    fun forget() {
        identityKey?.let { identities?.edit()?.remove(it)?.apply() }
        detach()
    }
    fun select(server: String, account: String, credential: String?) {
        val key = hash("$server\n$account\n${credential.orEmpty()}")
        synchronized(lock) {
            if (namespace != key) { revision++; namespace = key }
            identityKey = hash("$server\n${credential.orEmpty()}")
            identities?.edit()?.putString(identityKey, key)?.apply()
        }
    }
    fun detach() { synchronized(lock) { namespace = null; revision++ } }
    suspend fun <T> only(block: suspend () -> T): T = withContext(CacheOnly()) { block() }
    suspend fun isCacheOnly(): Boolean = currentCoroutineContext()[CacheOnly] != null

    suspend fun read(url: String): JsonElement = withContext(Dispatchers.IO) {
        val account = namespace ?: throw Miss()
        val db = database?.readableDatabase ?: throw Miss()
        db.query("snapshots", arrayOf("body"), "scope=? AND url=? AND saved>?",
            arrayOf(account, url, (System.currentTimeMillis() - MAX_AGE).toString()), null, null, null).use {
            if (!it.moveToFirst()) throw Miss()
            AppJson.parseToJsonElement(it.getString(0))
        }
    }

    /** Share simultaneous GETs, including between different screens and the task mirror. */
    suspend fun fetch(url: String, block: suspend () -> JsonElement): JsonElement {
        val deferred = synchronized(lock) {
            val account = namespace
            val version = revision
            val key = "$account:$version:$url"
            inFlight[key] ?: scope.async {
                val value = block()
                // A failed/cleared disk cache must never turn a successful request into an error.
                diskLock.withLock {
                    if (account != null && account == namespace && version == revision) {
                        catching {
                            database?.writableDatabase?.let { db ->
                                val body = value.toString()
                                val size = body.toByteArray(Charsets.UTF_8).size
                                // Stay below Android's default CursorWindow row limit on cached reads.
                                if (size <= 1_048_576) {
                                    db.insertWithOnConflict("snapshots", null, ContentValues().apply {
                                        put("scope", account); put("url", url); put("body", body)
                                        put("saved", System.currentTimeMillis()); put("size", size)
                                    }, SQLiteDatabase.CONFLICT_REPLACE)
                                    db.execSQL("DELETE FROM snapshots WHERE saved < ?", arrayOf(System.currentTimeMillis() - MAX_AGE))
                                    // Bound stored payloads to 32 MB, across every account.
                                    db.execSQL("DELETE FROM snapshots WHERE rowid NOT IN (SELECT rowid FROM snapshots ORDER BY saved DESC, rowid DESC LIMIT 128)")
                                    var bytes = DatabaseUtils.longForQuery(db, "SELECT COALESCE(SUM(size),0) FROM snapshots", null)
                                    while (bytes > 33_554_432) {
                                        db.execSQL("DELETE FROM snapshots WHERE rowid = (SELECT rowid FROM snapshots ORDER BY saved ASC, rowid ASC LIMIT 1)")
                                        bytes = DatabaseUtils.longForQuery(db, "SELECT COALESCE(SUM(size),0) FROM snapshots", null)
                                    }
                                }
                            }
                        }
                    }
                }
                value
            }.also { request ->
                inFlight[key] = request
                request.invokeOnCompletion { synchronized(lock) { if (inFlight[key] === request) inFlight.remove(key) } }
            }
        }
        return deferred.await()
    }

    suspend fun invalidate(all: Boolean = false): Unit = withContext(Dispatchers.IO) {
        val account = synchronized(lock) { revision++; namespace }
        diskLock.withLock {
            account?.let {
                val predicate = if (all) "scope=?" else "scope=? AND url != 'local:session' AND url NOT LIKE '%/api/companies?%'"
                catching { database?.writableDatabase?.delete("snapshots", predicate, arrayOf(it)) }
            }
        }
        Unit
    }
    private const val MAX_AGE = 7 * 24 * 60 * 60 * 1000L
}
