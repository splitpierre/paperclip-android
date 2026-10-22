package com.bu.paperclip.net

import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Response
import java.io.IOException

/** Bounded, in-memory timings for diagnostics. Never retains URLs, headers, credentials or bodies. */
object NetworkMetrics {
    data class Sample(val totalMs: Long, val responseHeadersMs: Long?, val bytes: Long, val status: Int?, val failed: Boolean)
    private val samples = ArrayDeque<Sample>()
    @Synchronized fun snapshot(): List<Sample> = samples.toList()
    @Synchronized private fun record(sample: Sample) {
        if (samples.size == 100) samples.removeFirst()
        samples.addLast(sample)
    }
    fun listener() = object : EventListener() {
        private var start = 0L
        private var firstByte: Long? = null
        private var bytes = 0L
        private var status: Int? = null
        override fun callStart(call: Call) { start = System.nanoTime() }
        override fun responseHeadersEnd(call: Call, response: Response) { firstByte = elapsed(); status = response.code }
        override fun responseBodyEnd(call: Call, byteCount: Long) { bytes = byteCount }
        override fun callEnd(call: Call) { record(Sample(elapsed(), firstByte, bytes, status, false)) }
        override fun callFailed(call: Call, ioe: IOException) { record(Sample(elapsed(), firstByte, bytes, status, true)) }
        private fun elapsed() = (System.nanoTime() - start) / 1_000_000
    }
}
