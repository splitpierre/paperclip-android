package com.bu.paperclip

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.bu.paperclip.net.Api
import com.bu.paperclip.net.LiveEvents
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class LiveEventsTest {
    @Test fun availabilityNotificationDoesNotReplaceAHealthySocket() = runBlocking {
        val server = MockWebServer()
        server.start()
        LiveEvents.stop()
        try {
            server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, reason) }
            }))
            Api.configure(server.url("/").toString().trimEnd('/'))
            val context = RuntimeEnvironment.getApplication()
            LiveEvents.observeNetwork(context)
            LiveEvents.start("demo")
            withTimeout(5_000) { LiveEvents.connected.first { it } }
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = Shadow.newInstanceOf(Network::class.java)
            shadowOf(manager).networkCallbacks.forEach { it.onAvailable(network) }
            assertTrue(LiveEvents.connected.value)
            assertEquals(1, server.requestCount)
        } finally {
            LiveEvents.stop()
            server.shutdown()
        }
    }
}
