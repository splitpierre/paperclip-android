package com.bu.paperclip.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Drain a keyset result, including exact multiples of pageSize; reject stalled/cyclic cursors. */
suspend fun <T> drainPages(
    pageSize: Int,
    id: (T) -> String,
    fetch: suspend (after: String?) -> List<T>,
    onPage: suspend (List<T>) -> Unit,
) {
    require(pageSize > 0)
    var after: String? = null
    val seen = HashSet<String>()
    while (true) {
        currentCoroutineContext().ensureActive()
        val page = fetch(after)
        onPage(page)
        if (page.size < pageSize) return
        val next = id(page.last())
        check(seen.add(next)) { "Server returned a repeated task page" }
        after = next
    }
}
