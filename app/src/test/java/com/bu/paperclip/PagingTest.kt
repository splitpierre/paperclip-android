package com.bu.paperclip

import com.bu.paperclip.data.drainPages
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PagingTest {
    @Test fun drainsMoreThanOneThousandChanges() = runTest {
        val source = (0 until 1501).map { it.toString().padStart(5, '0') }
        val result = mutableListOf<String>()
        var requests = 0
        drainPages(500, { it }, { after -> requests++; source.filter { after == null || it > after }.take(500) }, { result.addAll(it) })
        assertEquals(source, result)
        assertEquals(4, requests)
    }
    @Test fun exactPageMultipleRequestsEmptyTerminator() = runTest {
        var requests = 0
        drainPages(2, { it }, { after -> requests++; if (after == null) listOf("a", "b") else emptyList() }, {})
        assertEquals(2, requests)
    }
    @Test fun stalledCursorDoesNotLoopForever() = runTest {
        try {
            drainPages(2, { it }, { listOf("a", "b") }, {})
            fail("Repeated cursor accepted")
        } catch (_: IllegalStateException) { }
    }
    @Test fun partialFailureDoesNotReportSuccess() = runTest {
        val observed = mutableListOf<String>()
        try {
            drainPages(2, { it }, { after -> if (after == null) listOf("a", "b") else error("offline") }, { observed.addAll(it) })
            fail("Partial sync reported success")
        } catch (_: IllegalStateException) { assertEquals(listOf("a", "b"), observed) }
    }
}
