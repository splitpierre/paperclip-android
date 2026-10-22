package com.bu.paperclip.net

import android.net.Network
import android.content.Context
import android.net.ConnectivityManager
import com.bu.paperclip.util.catching

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

data class LiveEvent(val type: String, val payload: JsonObject) {
    fun s(key: String): String? = (payload[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
}

/** One company-scoped WebSocket; reconnects with backoff while the app is in the foreground. */
object LiveEvents {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _events = MutableSharedFlow<LiveEvent>(extraBufferCapacity = 512)
    val events: SharedFlow<LiveEvent> = _events
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected
    /** Bumped on every (re)connect so screens can refetch what they may have missed. */
    private val _epoch = MutableStateFlow(0)
    val epoch: StateFlow<Int> = _epoch

    fun observeNetwork(context: Context) {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = reconnectAfterNetworkChange()
        })
    }

    @Synchronized private fun reconnectAfterNetworkChange() {
        if (!wanted || connected.value) return
        loop?.cancel()
        stopSocket()
        attempt = 0
        connect()
    }

    @Volatile private var socket: WebSocket? = null
    private var companyId: String? = null
    @Volatile private var wanted = false
    private var loop: Job? = null
    private var attempt = 0

    @Synchronized
    fun start(cid: String) {
        if (wanted && cid == companyId && socket != null) return
        loop?.cancel()
        stopSocket()
        companyId = cid
        wanted = true
        attempt = 0
        connect()
    }

    @Synchronized
    fun stop() {
        wanted = false
        loop?.cancel()
        stopSocket()
    }

    private fun stopSocket() {
        socket?.close(1000, null)
        socket = null
        _connected.value = false
    }

    private fun connect() {
        val cid = companyId ?: return
        // The live socket only accepts cookie sessions; with an API key screens fall back to polling.
        if (Api.baseUrl.isEmpty() || Api.bearer != null) return
        val wsUrl = Api.baseUrl.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "/api/companies/$cid/events/ws"
        val req = Request.Builder().url(wsUrl.replaceFirst("wss://", "https://").replaceFirst("ws://", "http://")).build()
        socket = Api.client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (webSocket !== socket || !wanted) { webSocket.close(1000, null); return }
                attempt = 0
                _connected.value = true
                _epoch.value = _epoch.value + 1
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (webSocket !== socket || !wanted) return
                catching {
                    val o = AppJson.parseToJsonElement(text).jsonObject
                    val type = o["type"]?.jsonPrimitive?.contentOrNull ?: return
                    val payload = o["payload"] as? JsonObject ?: JsonObject(emptyMap())
                    if (!_events.tryEmit(LiveEvent(type, payload))) {
                        // Recover dropped events through the same reconciliation used on reconnect.
                        _epoch.value = _epoch.value + 1
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = scheduleReconnect(webSocket)
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = scheduleReconnect(webSocket)
        })
    }

    @Synchronized private fun scheduleReconnect(ws: WebSocket) {
        if (ws !== socket) return
        _connected.value = false
        socket = null
        if (!wanted) return
        attempt++
        val wait = minOf(15_000L, 1000L * (1L shl minOf(attempt - 1, 4)))
        loop = scope.launch {
            delay(wait + kotlin.random.Random.nextLong(0, 500))
            synchronized(this@LiveEvents) { if (wanted && socket == null) connect() }
        }
    }
}
