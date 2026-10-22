package com.bu.paperclip.data

/** Index one immutable lookup snapshot once, rather than scan the full directory in every row. */
internal class LookupIndex<T>(private val id: (T) -> String) {
    private var source: List<T>? = null
    private var index: Map<String, T> = emptyMap()

    @Synchronized
    fun get(items: List<T>, key: String?): T? {
        if (key == null) return null
        if (source !== items) {
            index = buildMap { items.forEach { putIfAbsent(id(it), it) } }
            source = items
        }
        return index[key]
    }
}
