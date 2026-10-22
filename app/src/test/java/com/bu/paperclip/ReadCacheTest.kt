package com.bu.paperclip

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import android.database.DatabaseUtils
import com.bu.paperclip.data.ReadCache
import com.bu.paperclip.net.AppJson
import com.bu.paperclip.net.Api
import com.bu.paperclip.net.AuthState
import com.bu.paperclip.data.Repo
import com.bu.paperclip.util.SupersededResponse
import kotlinx.coroutines.*
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ReadCacheTest {
    @Before fun setup() = runBlocking {
        ReadCache.initialize(RuntimeEnvironment.getApplication())
        ReadCache.select("https://demo.test", "alice", null)
        ReadCache.invalidate(all = true)
    }
    @Test fun persistedReadSurvivesReinitializationAndIsAccountScoped() = runBlocking {
        ReadCache.fetch("tasks") { JsonPrimitive("alice-data") }
        ReadCache.detach()
        ReadCache.initialize(RuntimeEnvironment.getApplication())
        assertTrue(ReadCache.restore("https://demo.test", null))
        assertEquals(JsonPrimitive("alice-data"), ReadCache.read("tasks"))
        ReadCache.select("https://demo.test", "bob", null)
        assertTrue(runCatching { ReadCache.read("tasks") }.exceptionOrNull() is ReadCache.Miss)
        ReadCache.select("https://another.test", "alice", null)
        assertTrue(runCatching { ReadCache.read("tasks") }.exceptionOrNull() is ReadCache.Miss)
    }
    @Test fun slowConcurrentReadsShareOneHttpCallAndCacheWorksWithoutServer() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("{\"ok\":true}").setBodyDelay(400, TimeUnit.MILLISECONDS))
            val client = OkHttpClient()
            val url = server.url("/tasks").toString()
            val reads = List(12) {
                async { ReadCache.fetch(url) {
                    client.newCall(Request.Builder().url(url).build()).execute().use { AppJson.parseToJsonElement(it.body!!.string()) }
                } }
            }
            reads.awaitAll()
            assertEquals(1, server.requestCount)
            server.shutdown()
            assertEquals(AppJson.parseToJsonElement("{\"ok\":true}"), ReadCache.read(url))
        } finally { server.shutdown() }
    }
    @Test fun mutationInvalidationCannotBeUndoneByOlderInflightGet() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val old = async { ReadCache.fetch("tasks") { entered.complete(Unit); finish.await(); JsonPrimitive("old") } }
        entered.await()
        ReadCache.invalidate()
        finish.complete(Unit)
        old.await()
        assertTrue(runCatching { ReadCache.read("tasks") }.exceptionOrNull() is ReadCache.Miss)
        ReadCache.fetch("tasks") { JsonPrimitive("new") }
        assertEquals(JsonPrimitive("new"), ReadCache.read("tasks"))
    }
    @Test fun apiDecodesSavedIssueOfflineAndNeverCachesWrites() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val base = server.url("/").toString().trimEnd('/')
            Api.configure(base)
            ReadCache.select(base, "alice", null)
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("{\"id\":\"one\",\"title\":\"Cached task\"}"))
            val fresh = Repo.issue("one")
            assertEquals("Cached task", fresh.title)
            assertEquals(1, server.requestCount)
            server.shutdown()
            val saved = ReadCache.only { Repo.issue("one") }
            assertEquals(fresh, saved)
            val write = runCatching { ReadCache.only { Repo.markRead("one") } }
            assertTrue(write.exceptionOrNull() is ReadCache.Miss)
        } finally { server.shutdown() }
    }

    @Test fun responseFromPreviousServerCannotAuthenticateNewConnection() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val api = Api
            api.configure(server.url("/").toString().trimEnd('/'))
            server.enqueue(MockResponse().setBody("{\"id\":\"old\"}").setBodyDelay(300, TimeUnit.MILLISECONDS))
            val old = async(start = CoroutineStart.UNDISPATCHED) { runCatching { Repo.issue("old") } }
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            api.configure("https://different.test")
            assertTrue(old.await().exceptionOrNull() is SupersededResponse)
            assertEquals(AuthState.Unknown, api.auth.value)
            assertTrue(currentCoroutineContext().isActive)
        } finally { server.shutdown() }
    }

    @Test fun logoutForgetsPersistedIdentity() = runBlocking {
        ReadCache.fetch("tasks") { JsonPrimitive("private") }
        ReadCache.invalidate(all = true)
        ReadCache.forget()
        assertFalse(ReadCache.restore("https://demo.test", null))
        ReadCache.select("https://demo.test", "alice", null)
        assertTrue(runCatching { ReadCache.read("tasks") }.exceptionOrNull() is ReadCache.Miss)
    }
    @Test fun legacyCacheMigratesByteSizesAndReplacementRecountsTheEntry() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        ReadCache.fetch("legacy") { JsonPrimitive("é漢字") }
        // Close the active helper before creating a version-1 database as shipped previously.
        ReadCache.initialize(context)
        SQLiteDatabase.openDatabase(context.getDatabasePath("read-cache.db").path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("ALTER TABLE snapshots RENAME TO version_two")
            db.execSQL("CREATE TABLE snapshots (scope TEXT, url TEXT, body TEXT, saved INTEGER, PRIMARY KEY(scope,url))")
            db.execSQL("INSERT INTO snapshots SELECT scope,url,body,saved FROM version_two")
            db.execSQL("DROP TABLE version_two")
            db.version = 1
        }
        ReadCache.initialize(context)
        assertEquals(JsonPrimitive("é漢字"), ReadCache.read("legacy"))
        fun storedBytes(): Long = SQLiteDatabase.openDatabase(
            context.getDatabasePath("read-cache.db").path, null, SQLiteDatabase.OPEN_READONLY,
        ).use { DatabaseUtils.longForQuery(it, "SELECT SUM(size) FROM snapshots", null) }
        assertEquals(JsonPrimitive("é漢字").toString().toByteArray(Charsets.UTF_8).size.toLong(), storedBytes())
        ReadCache.fetch("legacy") { JsonPrimitive("short") }
        assertEquals(JsonPrimitive("short").toString().toByteArray(Charsets.UTF_8).size.toLong(), storedBytes())
    }

    @Test fun oversizedResponsesStillSucceedWithoutPersistingUnreadableRows() = runBlocking {
        val payload = JsonPrimitive("x".repeat(2_100_000))
        assertEquals(payload, ReadCache.fetch("oversized") { payload })
        assertTrue(runCatching { ReadCache.read("oversized") }.exceptionOrNull() is ReadCache.Miss)
    }

    @Test fun byteBudgetEvictsTheOldestPayloadWithoutScanningBodies() = runBlocking {
        val payload = JsonPrimitive("x".repeat(900_000))
        repeat(40) { index -> ReadCache.fetch("large-$index") { payload } }
        assertTrue(runCatching { ReadCache.read("large-0") }.exceptionOrNull() is ReadCache.Miss)
        assertEquals(payload, ReadCache.read("large-39"))
        val context = RuntimeEnvironment.getApplication()
        SQLiteDatabase.openDatabase(context.getDatabasePath("read-cache.db").path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val bytes = DatabaseUtils.longForQuery(db, "SELECT SUM(size) FROM snapshots", null)
            assertTrue(bytes <= 33_554_432)
            assertEquals(37L, DatabaseUtils.longForQuery(db, "SELECT COUNT(*) FROM snapshots", null))
        }
    }

}
