package com.bu.paperclip.net

import com.bu.paperclip.util.L10n
import com.bu.paperclip.R
import android.webkit.CookieManager
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
    class AuthRequired(message: String = L10n.str(R.string.error_sign_in_required)) : ApiException(message)
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
    private val _auth = MutableStateFlow(AuthState.Unknown)
    val auth: StateFlow<AuthState> = _auth

    val origin: String
        get() = baseUrl.toHttpUrl().let { u ->
            val defaultPort = if (u.scheme == "https") 443 else 80
            if (u.port == defaultPort) "${u.scheme}://${u.host}" else "${u.scheme}://${u.host}:${u.port}"
        }

    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(WebViewCookieJar)
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(20, TimeUnit.SECONDS)
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
        baseUrl = url.trimEnd('/')
        bearer = token?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun markSignedIn() { _auth.value = AuthState.SignedIn }
    fun markSignedOut() { _auth.value = AuthState.SignedOut }

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
                if (cont.isActive) cont.resumeWithException(ApiException.Network(e.message ?: L10n.str(R.string.error_network)))
            }
            override fun onResponse(call: Call, response: Response) {
                cont.resume(response)
            }
        })
    }

    suspend fun requestJson(method: String, path: String, query: Map<String, Any?> = emptyMap(), body: JsonElement? = null): JsonElement {
        if (baseUrl.isEmpty()) throw ApiException.AuthRequired(L10n.str(R.string.error_no_server))
        val rb: RequestBody? = when {
            body != null -> AppJson.encodeToString(JsonElement.serializer(), body).toRequestBody(jsonType)
            method == "GET" || method == "HEAD" -> null
            method == "DELETE" -> null
            else -> "{}".toRequestBody(jsonType)
        }
        val req = Request.Builder().url(url(path, query)).method(method, rb).build()
        return handle(execute(req))
    }

    suspend fun upload(path: String, fileName: String, mime: String, bytes: ByteArray, extra: Map<String, String> = emptyMap()): JsonElement {
        val mb = MultipartBody.Builder().setType(MultipartBody.FORM)
        extra.forEach { (k, v) -> mb.addFormDataPart(k, v) }
        mb.addFormDataPart("file", fileName, bytes.toRequestBody(mime.toMediaType()))
        val req = Request.Builder().url(url(path)).post(mb.build()).build()
        return handle(execute(req))
    }

    private suspend fun handle(resp: Response): JsonElement = withContext(Dispatchers.IO) {
        resp.use { r ->
            val text = r.body?.string().orEmpty()
            val code = r.code
            val location = r.header("Location").orEmpty()
            if (code in 300..399) {
                _auth.value = AuthState.SignedOut
                throw ApiException.AuthRequired(
                    L10n.str(if (location.contains("cloudflareaccess")) R.string.error_access_session_expired else R.string.error_redirected_to_sign_in),
                )
            }
            val contentType = r.header("Content-Type").orEmpty()
            val parsed: JsonElement? = if (text.isBlank()) null else runCatching { AppJson.parseToJsonElement(text) }.getOrNull()
            if (code in 200..299) {
                if (parsed == null && text.isNotBlank() && contentType.contains("text/html")) {
                    _auth.value = AuthState.SignedOut
                    throw ApiException.AuthRequired(L10n.str(R.string.error_got_web_page))
                }
                if (_auth.value != AuthState.SignedIn) _auth.value = AuthState.SignedIn
                return@withContext parsed ?: JsonNull
            }
            val msg = (parsed as? JsonObject)?.let { o ->
                o["error"]?.jsonPrimitive?.contentOrNull ?: o["message"]?.jsonPrimitive?.contentOrNull
            } ?: L10n.str(R.string.error_request_failed_code, code)
            val authMsg = msg.contains("Board authentication required") || msg.contains("Board access required") ||
                msg.contains("Unauthorized", ignoreCase = true)
            if (code == 401 || (code == 403 && authMsg)) {
                _auth.value = AuthState.SignedOut
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

    fun <T> decode(ser: KSerializer<T>, el: JsonElement): T = AppJson.decodeFromJsonElement(ser, el)

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
        // Closing the response can touch the socket, so keep it off the main thread.
        return withContext(Dispatchers.IO) {
            r.use {
                when {
                    it.code in 300..399 -> it.header("Location")?.let { loc -> runCatching { loc.toHttpUrl().host }.getOrNull() } ?: L10n.str(R.string.error_gate_sign_in_page)
                    it.header("Content-Type").orEmpty().contains("text/html") -> L10n.str(R.string.error_gate_sign_in_page)
                    else -> null
                }
            }
        }
    }

    /** Native Paperclip (better-auth) email sign-in; the session cookie lands in the shared store. */
    suspend fun signIn(email: String, password: String) {
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
        runCatching { requestJson("POST", "/auth/sign-out", body = JsonObject(emptyMap())) }
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        markSignedOut()
    }
}

fun JsonElement?.obj(): JsonObject? = this as? JsonObject
fun JsonObject.str(key: String): String? = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
fun JsonElement.objOrEmpty(): JsonObject = runCatching { jsonObject }.getOrDefault(JsonObject(emptyMap()))
