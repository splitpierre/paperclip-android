package com.bu.paperclip

import com.bu.paperclip.data.RefreshQueue
import com.bu.paperclip.util.catching
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RefreshQueueTest {
    @Test fun slowRefreshFinishesDespiteRepeatedInvalidations() = runTest {
        val queue = RefreshQueue()
        var started = 0
        var completed = 0
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            queue.run { started++; delay(60_000); completed++ }
        }
        repeat(100) { queue.request() }
        advanceTimeBy(60_001)
        assertEquals(1, completed)
        assertEquals(2, started)
        advanceTimeBy(60_001)
        assertEquals(2, completed)
        assertEquals(2, started)
        job.cancelAndJoin()
    }

    @Test fun stoppingOwnerCancelsRefreshAndResumeRefreshesAgain() = runTest {
        val queue = RefreshQueue()
        var cancellations = 0
        var starts = 0
        fun worker() = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            queue.run { starts++; try { awaitCancellation() } finally { cancellations++ } }
        }
        worker().cancelAndJoin()
        val second = worker()
        assertEquals(2, starts)
        assertEquals(1, cancellations)
        second.cancelAndJoin()
    }

    @Test fun optionalLoadsNeverSwallowCancellation() {
        try {
            catching { throw CancellationException("screen closed") }
            fail("Cancellation was swallowed")
        } catch (_: CancellationException) { }
    }
}
