package com.bu.paperclip.net

import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong
import com.bu.paperclip.data.IssueStore
import com.bu.paperclip.util.SupersededResponse
import com.bu.paperclip.util.catching

import android.webkit.CookieManager
import com.bu.paperclip.data.ReadCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

val AppJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    isLenient = true
    encodeDefaults = false
}

/** Shares the WebView cookie store, so the Cloudflare Access + Paperclip cookies from the in-app sign-in are reused. */
object WebViewCookieJar : CookieJar {
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val raw = CookieManager.getInstance().getCookie(url.toString()) ?: return emptyList()
        return raw.split(";").mapNotNull { part ->
            val kv = part.trim()
            if (kv.isEmpty()) null else Cookie.parse(url, kv)
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val cm = CookieManager.getInstance()
        cookies.forEach { cm.setCookie(url.toString(), it.toString()) }
        cm.flush()
    }
}

sealed class ApiException(message: String) : Exception(message) {
    class AuthRequired(message: String = "Sign-in required") : ApiException(message)
    class Http(val status: Int, message: String, val body: JsonElement?) : ApiException(message)
    class Network(message: String) : ApiException(message)
}

enum class AuthState { Unknown, SignedOut, SignedIn }

object Api {
    @Volatile var baseUrl: String = ""
        private set
    /** Optional board API key; when set it replaces the cookie session for REST calls. */
    @Volatile var bearer: String? = null
        private set
    private val sessionVersion = AtomicLong()
    private val _auth = MutableStateFlow(AuthState.Unknown)
    val auth: StateFlow<AuthState> = _auth

    @Volatile var serverTime: Instant? = null
        private set

    val origin: String
        get() = baseUrl.toHttpUrl().let { u ->
            val defaultPort = if (u.scheme == "https") 443 else 80
            if (u.port == defaultPort) "${u.scheme}://${u.host}" else "${u.scheme}://${u.host}:${u.port}"
        }

    val client: OkHttpClient = OkHttpClient.Builder()
        .eventListenerFactory { NetworkMetrics.listener() }
        .cookieJar(WebViewCookieJar)
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(75, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request()
            val b = req.newBuilder().header("Accept", "application/json")
            if (req.method != "GET" && req.method != "HEAD" && baseUrl.isNotEmpty()) b.header("Origin", origin)
            val token = bearer
            if (token != null && baseUrl.isNotEmpty() && req.url.host == baseUrl.toHttpUrl().host) b.header("Authorization", "Bearer $token")
            chain.proceed(b.build())
        }
        .build()

    /** For images and downloads: follows redirects but still carries the session cookies. */
    val mediaClient: OkHttpClient = client.newBuilder().followRedirects(true).followSslRedirects(true).build()

    fun configure(url: String, token: String? = null) {
        if (baseUrl != url.trimEnd('/') || bearer != token?.trim()?.takeIf { it.isNotEmpty() }) {
            sessionVersion.incrementAndGet()
            ReadCache.detach()
            serverTime = null
            _auth.value = AuthState.Unknown
        }
        baseUrl = url.trimEnd('/')
        bearer = token?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun markSignedIn() { _auth.value = AuthState.SignedIn }
    fun markSignedOut() { sessionVersion.incrementAndGet(); _auth.value = AuthState.SignedOut }

    fun absolute(pathOrUrl: String): String = when {
        pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://") -> pathOrUrl
        pathOrUrl.startsWith("/") -> baseUrl + pathOrUrl
        else -> "$baseUrl/$pathOrUrl"
    }

    fun url(path: String, query: Map<String, Any?> = emptyMap()): HttpUrl {
        val b = (baseUrl + "/api" + path).toHttpUrl().newBuilder()
        query.forEach { (k, v) ->
            when (v) {
                null -> {}
                is Collection<*> -> v.forEach { item -> if (item != null) b.addQueryParameter(k, item.toString()) }
                else -> b.addQueryParameter(k, v.toString())
            }
        }
        return b.build()
    }

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun execute(request: Request): Response = suspendCancellableCoroutine { cont ->
        val call = client.newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(ApiException.Network(e.message ?: "Network error"))
            }
            override fun onResponse(call: Call, response: Response) {
                cont.resume(response) { _, value, _ -> value.close() }
            }
        })
    }

    suspend fun requestJson(method: String, path: String, query: Map<String, Any?> = emptyMap(), body: JsonElement? = null): JsonElement {
        if (baseUrl.isEmpty()) throw ApiException.AuthRequired("No server configured")
        val rb: RequestBody? = when {
            body != null -> AppJson.encodeToString(JsonElement.serializer(), body).toRequestBody(jsonType)
            method == "GET" || method == "HEAD" -> null
            method == "DELETE" -> null
            else -> "{}".toRequestBody(jsonType)
        }
        val requestVersion = sessionVersion.get()
        val req = Request.Builder().url(url(path, query)).method(method, rb).build()
        val cacheable = method == "GET" && !path.startsWith("/auth/") && path != "/health" && !path.endsWith("/log")
        if (ReadCache.isCacheOnly()) {
            if (!cacheable) throw ReadCache.Miss()
            return ReadCache.read(req.url.toString())
        }
        if (cacheable) return ReadCache.fetch(req.url.toString()) { handle(execute(req), requestVersion) }
        val result = handle(execute(req), requestVersion)
        if (method != "GET" && method != "HEAD" && !path.endsWith("/read")) ReadCache.invalidate()
        return result
    }

    suspend fun upload(path: String, fileName: String, mime: String, bytes: ByteArray, extra: Map<String, String> = emptyMap()): JsonElement {
        val mb = MultipartBody.Builder().setType(MultipartBody.FORM)
        extra.forEach { (k, v) -> mb.addFormDataPart(k, v) }
        mb.addFormDataPart("file", fileName, bytes.toRequestBody(mime.toMediaType()))
        val requestVersion = sessionVersion.get()
        val req = Request.Builder().url(url(path)).post(mb.build()).build()
        return handle(execute(req), requestVersion).also { ReadCache.invalidate() }
    }

    private suspend fun handle(resp: Response, requestVersion: Long): JsonElement = resp.use { r ->
        withContext(Dispatchers.IO) {
            if (requestVersion != sessionVersion.get()) throw SupersededResponse()
            val text = r.body?.string().orEmpty()
            if (requestVersion != sessionVersion.get()) throw SupersededResponse()
            r.header("Date")?.let { date -> catching { ZonedDateTime.parse(date, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }.getOrNull()?.let { serverTime = it } }
            val code = r.code
            val location = r.header("Location").orEmpty()
            if (code in 300..399) {
                markSignedOut()
                throw ApiException.AuthRequired(
                    if (location.contains("cloudflareaccess")) "Cloudflare Access session expired" else "Redirected to sign-in",
                )
            }
            val contentType = r.header("Content-Type").orEmpty()
            val parsed: JsonElement? = if (text.isBlank()) null else catching { AppJson.parseToJsonElement(text) }.getOrNull()
            if (code in 200..299) {
                if (parsed == null && text.isNotBlank() && contentType.contains("text/html")) {
                    markSignedOut()
                    throw ApiException.AuthRequired("Got a web page instead of data — sign in again")
                }
                if (_auth.value != AuthState.SignedIn) _auth.value = AuthState.SignedIn
                return@withContext parsed ?: JsonNull
            }
            val msg = (parsed as? JsonObject)?.let { o ->
                o["error"]?.jsonPrimitive?.contentOrNull ?: o["message"]?.jsonPrimitive?.contentOrNull
            } ?: "Request failed ($code)"
            val authMsg = msg.contains("Board authentication required") || msg.contains("Board access required") ||
                msg.contains("Unauthorized", ignoreCase = true)
            if (code == 401 || (code == 403 && authMsg)) {
                markSignedOut()
                throw ApiException.AuthRequired(msg)
            }
            throw ApiException.Http(code, msg, parsed)
        }
    }

    suspend inline fun <reified T> get(path: String, query: Map<String, Any?> = emptyMap()): T =
        decode(serializer(), requestJson("GET", path, query))

    suspend inline fun <reified T> post(path: String, body: JsonElement? = null, query: Map<String, Any?> = emptyMap()): T =
        decode(serializer(), requestJson("POST", path, query, body))

    suspend inline fun <reified T> patch(path: String, body: JsonElement): T =
        decode(serializer(), requestJson("PATCH", path, body = body))

    suspend inline fun <reified T> put(path: String, body: JsonElement): T =
        decode(serializer(), requestJson("PUT", path, body = body))

    suspend fun delete(path: String, body: JsonElement? = null): JsonElement = requestJson("DELETE", path, body = body)

    suspend fun <T> decode(ser: KSerializer<T>, el: JsonElement): T = withContext(Dispatchers.Default) { AppJson.decodeFromJsonElement(ser, el) }

    /** Probes the session; true when both Cloudflare (if any) and Paperclip accept the cookies. */
    suspend fun checkSession(): Boolean = try {
        val el = requestJson("GET", "/auth/get-session")
        val ok = (el as? JsonObject)?.get("user") != null
        if (ok) markSignedIn() else markSignedOut()
        ok
    } catch (e: ApiException.AuthRequired) {
        markSignedOut(); false
    } catch (e: ApiException.Http) {
        markSignedOut(); false
    }

    /**
     * Host of an auth proxy (e.g. Cloudflare Access) that intercepts API calls before Paperclip, or null
     * when requests reach Paperclip directly.
     */
    suspend fun gateHost(): String? {
        val r = execute(Request.Builder().url(url("/health")).get().build())
        return r.use {
            when {
                it.code in 300..399 -> it.header("Location")?.let { loc -> catching { loc.toHttpUrl().host }.getOrNull() } ?: "a sign-in page"
                it.header("Content-Type").orEmpty().contains("text/html") -> "a sign-in page"
                else -> null
            }
        }
    }

    /** Native Paperclip (better-auth) email sign-in; the session cookie lands in the shared store. */
    suspend fun signIn(email: String, password: String) {
        sessionVersion.incrementAndGet()
        requestJson(
            "POST", "/auth/sign-in/email",
            body = JsonObject(
                mapOf(
                    "email" to kotlinx.serialization.json.JsonPrimitive(email.trim()),
                    "password" to kotlinx.serialization.json.JsonPrimitive(password),
                    "rememberMe" to kotlinx.serialization.json.JsonPrimitive(true),
                ),
            ),
        )
        CookieManager.getInstance().flush()
    }

    suspend fun signOut() {
        sessionVersion.incrementAndGet()
        catching { requestJson("POST", "/auth/sign-out", body = JsonObject(emptyMap())) }
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        ReadCache.invalidate(all = true)
        ReadCache.forget()
        IssueStore.clear()
        markSignedOut()
    }
}

fun JsonElement?.obj(): JsonObject? = this as? JsonObject
fun JsonObject.str(key: String): String? = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
fun JsonElement.objOrEmpty(): JsonObject = catching { jsonObject }.getOrDefault(JsonObject(emptyMap()))
