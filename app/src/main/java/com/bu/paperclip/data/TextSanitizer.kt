package com.bu.paperclip.data

/**
 * Keeps huge inline payloads (base64 images, binary blobs) out of Compose. Run once while parsing the log,
 * never during recomposition.
 */
object TextSanitizer {
    const val MAX_BLOCK_CHARS = 20_000
    const val MIN_BINARY_RUN = 2_000

    private val dataUriHeader = Regex("data:([A-Za-z0-9][A-Za-z0-9.+-]*/[A-Za-z0-9][A-Za-z0-9.+-]*)(?:;[A-Za-z0-9=._-]+)*;base64,")

    private fun isB64(c: Char) = c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '+' || c == '/' || c == '='
    private fun isB64Url(c: Char) = isB64(c) || c == '-' || c == '_'

    fun kb(base64Chars: Int): Int = maxOf(1, ((base64Chars.toLong() * 3 / 4 + 512) / 1024).toInt())

    fun imagePlaceholder(mime: String, base64Chars: Int) = "[image $mime, ~${kb(base64Chars)} KB]"

    /** Replaces data URIs and long base64 runs, then caps the length. */
    fun sanitize(text: String): String {
        if (text.length < 64) return text
        return cap(stripBinary(text))
    }

    fun stripBinary(text: String): String = stripRuns(stripDataUris(text))

    private fun stripDataUris(text: String): String {
        if (!text.contains("base64,")) return text
        val out = StringBuilder(text.length)
        var copied = 0
        var m = dataUriHeader.find(text, 0)
        while (m != null) {
            val start = m.range.first
            val payloadStart = m.range.last + 1
            var j = payloadStart
            while (j < text.length && isB64Url(text[j])) j++
            out.append(text, copied, start).append(imagePlaceholder(m.groupValues[1], j - payloadStart))
            copied = j
            m = if (j < text.length) dataUriHeader.find(text, j) else null
        }
        out.append(text, copied, text.length)
        return out.toString()
    }

    private fun stripRuns(text: String): String {
        var out: StringBuilder? = null
        var copied = 0
        var i = 0
        val n = text.length
        while (i < n) {
            if (!isB64(text[i])) { i++; continue }
            var j = i
            var alnum = 0
            while (j < n && isB64(text[j])) { if (text[j] != '=' && text[j] != '/' && text[j] != '+') alnum++; j++ }
            val len = j - i
            if (len > MIN_BINARY_RUN && alnum * 10 >= len * 8) {
                if (out == null) out = StringBuilder(n)
                out.append(text, copied, i).append("[binary data, ~${kb(len)} KB]")
                copied = j
            }
            i = j
        }
        if (out == null) return text
        out.append(text, copied, n)
        return out.toString()
    }

    fun cap(text: String, max: Int = MAX_BLOCK_CHARS): String {
        if (text.length <= max) return text
        var end = max
        if (Character.isHighSurrogate(text[end - 1])) end--
        return text.substring(0, end) + "\n… [truncated, ${text.length - end} more characters]"
    }
}
