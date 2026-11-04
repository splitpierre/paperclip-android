package com.bu.paperclip.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.bu.paperclip.data.AppState
import com.bu.paperclip.data.Attachment
import com.bu.paperclip.data.Repo
import com.bu.paperclip.net.ServerFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A file picked on the device that has not been uploaded yet. */
internal data class PickedFile(val uri: Uri, val name: String, val size: Long?, val mime: String)

/** Reads the display name, size and type the document provider reports for [uri]. Call off the main thread. */
internal fun pickedFile(ctx: Context, uri: Uri): PickedFile {
    var name: String? = null
    var size: Long? = null
    runCatching {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                name = c.getString(0)
                size = if (c.isNull(1)) null else c.getLong(1)
            }
        }
    }
    val display = name?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file-${System.currentTimeMillis()}"
    val mime = ServerFiles.mimeType(display, ctx.contentResolver.getType(uri)) { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
    return PickedFile(uri, display, size, mime)
}

/** Uploads [file] as an attachment of [issueId], with the same multipart route the web UI uses. */
internal suspend fun uploadPickedFile(ctx: Context, issueId: String, file: PickedFile): Attachment {
    val bytes = withContext(Dispatchers.IO) {
        ctx.contentResolver.openInputStream(file.uri)?.use { it.readBytes() } ?: error("Can't read ${file.name}")
    }
    return Repo.uploadAttachment(AppState.cid, issueId, file.name, file.mime, bytes)
}

internal fun issueSize(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "${b / 1024} KB"
    else -> String.format("%.1f MB", b / 1048576.0)
}

/** What to tell the user when the task was created but some of its files ([failed]: name to reason) did not upload. */
internal fun uploadFailureMessage(task: String, failed: List<Pair<String, String>>, total: Int): String = when {
    failed.isEmpty() -> "Created $task"
    failed.size == 1 -> "Created $task, but ${failed[0].first} didn't upload: ${failed[0].second}"
    else -> "Created $task, but ${failed.size} of $total files didn't upload: " +
        failed.joinToString("; ") { (name, reason) -> "$name ($reason)" }
}
