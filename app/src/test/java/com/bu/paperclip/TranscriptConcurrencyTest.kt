package com.bu.paperclip

import com.bu.paperclip.ui.screens.RunTranscriptState
import com.bu.paperclip.ui.screens.RunLogCatchUp
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class TranscriptConcurrencyTest {
    @Test fun longTranscriptHasBoundedVisibleHistory() = runBlocking {
        val state = RunTranscriptState(null)
        repeat(5100) { state.feedLive(null, "stderr", "line-$it", it.toLong()) }
        val snapshot = state.snapshot()!!
        assertEquals(5000, snapshot.entries.size)
        assertEquals(5000, snapshot.rawLines.size)
        assertTrue(state.trimmed)
        assertEquals("line-100", snapshot.rawLines.first().text)
        assertEquals(100L, snapshot.entryStart)
        assertEquals(100L, snapshot.rawStart)
        val anchoredEntry = snapshot.entries[200]
        val anchoredRaw = snapshot.rawLines[200]
        val entryKey = snapshot.entryStart + 200
        val rawKey = snapshot.rawStart + 200
        state.feedLive(null, "stderr", "line-5100", 5100)
        val updated = state.snapshot()!!
        // Front trimming keeps a retained row's key attached to the same content.
        assertEquals(anchoredEntry, updated.entries[(entryKey - updated.entryStart).toInt()])
        assertEquals(anchoredRaw, updated.rawLines[(rawKey - updated.rawStart).toInt()])
    }

    @Test fun liveChunksAndSnapshotsRemainConsistent() = runBlocking {
        val state = RunTranscriptState(null)
        coroutineScope {
            repeat(200) { seq -> launch { state.feedLive(null, "stderr", "line-$seq", seq.toLong()) } }
            launch { repeat(20) { state.snapshot(); yield() } }
        }
        state.feedLive(null, "stderr", "line-0", 0) // duplicate after reconnect
        val snapshot = state.snapshot()!!
        assertEquals(200, snapshot.entries.size)
        assertEquals(200, snapshot.rawLines.size)
        assertNull(state.snapshot()) // unchanged data does not allocate another snapshot
    }
    @Test fun liveArrivalsWaitUntilSuspendingReplayFinishes() = runTest {
        val catchUp = RunLogCatchUp()
        fun record(id: Int) = buildJsonObject { put("seq", id) }
        catchUp.offer(record(1)); catchUp.offer(record(2))
        val paused = CompletableDeferred<Unit>()
        val seen = mutableListOf<Int>()
        val replay = launch(start = CoroutineStart.UNDISPATCHED) {
            catchUp.complete(replay = {
                val id = it.getValue("seq").jsonPrimitive.int
                if (id == 1) paused.await()
                seen.add(id)
            }, recover = { fail("Unexpected overflow") })
        }
        assertTrue(catchUp.offer(record(3)))
        paused.complete(Unit)
        replay.join()
        assertEquals(listOf(1, 2, 3), seen)
        assertFalse(catchUp.offer(record(4)))
    }

    @Test fun overflowRecoversTheGapBeforeNewerLiveRecords() = runTest {
        val catchUp = RunLogCatchUp(capacity = 2)
        fun record(id: Int) = buildJsonObject { put("seq", id) }
        catchUp.offer(record(1)); catchUp.offer(record(2)); catchUp.offer(record(3))
        val paused = CompletableDeferred<Unit>()
        val seen = mutableListOf<Int>()
        var recoveries = 0
        val replay = launch(start = CoroutineStart.UNDISPATCHED) {
            catchUp.complete(replay = {
                val id = it.getValue("seq").jsonPrimitive.int
                if (id == 1) paused.await()
                seen.add(id)
            }, recover = {
                recoveries++
                seen.addAll(listOf(3, 4))
                assertTrue(catchUp.offer(record(5)))
            })
        }
        assertTrue(catchUp.offer(record(4)))
        paused.complete(Unit)
        replay.join()
        assertEquals(listOf(1, 2, 3, 4, 5), seen)
        assertEquals(1, recoveries)
    }

}
