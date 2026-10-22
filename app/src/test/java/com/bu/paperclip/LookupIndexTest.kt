package com.bu.paperclip

import com.bu.paperclip.data.LookupIndex
import org.junit.Assert.*
import org.junit.Test

class LookupIndexTest {
    private data class Entry(val id: String, val name: String)

    @Test fun visibleRowsShareOneIndexAndRefreshesReplaceIt() {
        var visits = 0
        val index = LookupIndex<Entry> { visits++; it.id }
        val directory = List(1000) { Entry("$it", "Name $it") }
        repeat(1000) { assertEquals(directory[it], index.get(directory, "$it")) }
        assertEquals(1000, visits) // 1,000 rows do one directory pass, rather than 1,000 scans.
        val updated = listOf(Entry("0", "Renamed"))
        assertEquals("Renamed", index.get(updated, "0")?.name)
        assertNull(index.get(updated, "999"))
        assertNull(index.get(emptyList(), "0")) // Company change/sign-out cannot retain old entries.
        assertNull(index.get(directory, null))
    }
}
