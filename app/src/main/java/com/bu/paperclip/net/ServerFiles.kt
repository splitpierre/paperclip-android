package com.bu.paperclip.net

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Rules for files served by Paperclip (issue attachments and company assets). Their `/content` routes
 * need the session, so they must be fetched in-app: the external browser has no cookie or board key.
 */
object ServerFiles {
    private val contentPath = Regex("""^/api/(attachments|assets)/[^/]+/content/?$""")

    /**
     * The absolute URL when [link] points at an attachment or asset on the server at [baseUrl]
     * (relative, or absolute on the same origin); null for anything else.
     */
    fun contentUrl(link: String, baseUrl: String): HttpUrl? {
        if (baseUrl.isEmpty()) return null
        val base = baseUrl.toHttpUrlOrNull() ?: return null
        val url = when {
            link.startsWith("//") -> "${base.scheme}:$link".toHttpUrlOrNull()
            link.startsWith("/") -> base.resolve(link)
            else -> link.toHttpUrlOrNull()
        } ?: return null
        if (url.host != base.host || url.port != base.port) return null
        val prefix = base.encodedPath.trimEnd('/')
        val path = url.encodedPath
        if (prefix.isNotEmpty() && !path.startsWith("$prefix/")) return null
        return if (contentPath.matches(path.removePrefix(prefix))) url else null
    }

    /** Types that say nothing about the file, so the extension decides instead. */
    private val vague = setOf("application/octet-stream", "binary/octet-stream", "application/unknown", "*/*", "application/x-download")

    private val byExtension = mapOf(
        "png" to "image/png", "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "gif" to "image/gif",
        "webp" to "image/webp", "bmp" to "image/bmp", "heic" to "image/heic", "svg" to "image/svg+xml",
        "pdf" to "application/pdf",
        "md" to "text/markdown", "markdown" to "text/markdown", "txt" to "text/plain", "log" to "text/plain",
        "csv" to "text/csv", "tsv" to "text/tab-separated-values", "html" to "text/html", "htm" to "text/html",
        "json" to "application/json", "xml" to "application/xml", "yaml" to "application/yaml", "yml" to "application/yaml",
        "diff" to "text/x-diff", "patch" to "text/x-diff",
        "zip" to "application/zip", "mp4" to "video/mp4", "mov" to "video/quicktime", "webm" to "video/webm",
        "mp3" to "audio/mpeg", "wav" to "audio/wav", "ogg" to "audio/ogg",
        "doc" to "application/msword", "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls" to "application/vnd.ms-excel", "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "ppt" to "application/vnd.ms-powerpoint", "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    )

    /**
     * The MIME type to open a file with: the declared type when it says something, otherwise one picked
     * from the file name's extension ([fallback] gets the extension, e.g. Android's MimeTypeMap), otherwise
     * `application/octet-stream`.
     */
    fun mimeType(filename: String?, declared: String?, fallback: (String) -> String? = { null }): String {
        val d = declared?.substringBefore(';')?.trim()?.lowercase()
        if (!d.isNullOrEmpty() && d !in vague && '/' in d) return d
        val ext = filename?.substringAfterLast('.', "")?.lowercase().orEmpty()
        if (ext.isNotEmpty()) (byExtension[ext] ?: fallback(ext))?.let { return it }
        return "application/octet-stream"
    }

    fun isImage(mime: String): Boolean = mime.startsWith("image/") && mime != "image/svg+xml"

    fun isMarkdown(mime: String, filename: String?): Boolean =
        mime == "text/markdown" || mime == "text/x-markdown" ||
            filename?.substringAfterLast('.', "")?.lowercase() in setOf("md", "markdown")

    /** Plain text we can show in the app instead of hoping another app takes it. */
    fun isText(mime: String): Boolean = mime.startsWith("text/") && mime != "text/html" ||
        mime in setOf("application/json", "application/xml", "application/yaml", "application/x-yaml", "application/x-ndjson")

    /** The file name from a Content-Disposition header (prefers the RFC 5987 `filename*` form). */
    fun filenameFromDisposition(header: String?): String? {
        if (header.isNullOrBlank()) return null
        Regex("""filename\*\s*=\s*([^';]*)'[^']*'([^;]+)""", RegexOption.IGNORE_CASE).find(header)?.let { m ->
            val decoded = runCatching { java.net.URLDecoder.decode(m.groupValues[2].trim().replace("+", "%2B"), m.groupValues[1].ifBlank { "UTF-8" }) }.getOrNull()
            if (!decoded.isNullOrBlank()) return decoded
        }
        Regex("""filename\s*=\s*"((?:[^"\\]|\\.)*)"""", RegexOption.IGNORE_CASE).find(header)?.let { return it.groupValues[1].replace("\\\"", "\"").ifBlank { null } }
        Regex("""filename\s*=\s*([^;]+)""", RegexOption.IGNORE_CASE).find(header)?.let { return it.groupValues[1].trim().ifBlank { null } }
        return null
    }

    /** A name that is safe as a single path segment but keeps the original as readable as possible. */
    fun safeFilename(name: String?): String {
        val cleaned = (name ?: "").substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("""[\u0000-\u001f:*?"<>|]"""), "_").trim().trimStart('.')
        return cleaned.take(120).ifEmpty { "file" }
    }
}
