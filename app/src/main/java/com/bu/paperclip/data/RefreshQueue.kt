package com.bu.paperclip.data

import kotlinx.coroutines.channels.Channel

/** One active refresh, and at most one follow-up. Invalidations never cancel active work. */
class RefreshQueue {
    private val requests = Channel<Unit>(Channel.CONFLATED)
    fun request() { requests.trySend(Unit) }
    suspend fun run(refresh: suspend () -> Unit) {
        request()
        for (ignored in requests) refresh()
    }
}
