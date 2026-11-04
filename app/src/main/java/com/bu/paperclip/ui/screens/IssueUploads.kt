package com.bu.paperclip.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.bu.paperclip.R
import com.bu.paperclip.data.AppState
import com.bu.paperclip.data.Attachment
import com.bu.paperclip.data.Repo
import com.bu.paperclip.net.ServerFiles
import com.bu.paperclip.util.L10n
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
        ctx.contentResolver.openInputStream(file.uri)?.use { it.readBytes() } ?: error(L10n.str(R.string.upload_cant_read, file.name))
    }
    return Repo.uploadAttachment(AppState.cid, issueId, file.name, file.mime, bytes)
}

internal fun issueSize(b: Long): String = com.bu.paperclip.util.formatBytes(b, 1024)

/** Resolved sentences for [uploadFailureMessage]; [someFailed] gets (task, failed count, total, "name (reason); …"). */
internal class UploadMessages(
    val created: (task: String) -> String,
    val oneFailed: (task: String, file: String, reason: String) -> String,
    val someFailed: (task: String, failed: Int, total: Int, list: String) -> String,
)

internal fun uploadMessages() = UploadMessages(
    created = { L10n.str(R.string.upload_created, it) },
    oneFailed = { task, file, reason -> L10n.str(R.string.upload_one_failed, task, file, reason) },
    someFailed = { task, n, total, list -> L10n.plural(R.plurals.upload_some_failed, total, task, n, total, list) },
)

/** What to tell the user when the task was created but some of its files ([failed]: name to reason) did not upload. */
internal fun uploadFailureMessage(task: String, failed: List<Pair<String, String>>, total: Int, m: UploadMessages = uploadMessages()): String = when {
    failed.isEmpty() -> m.created(task)
    failed.size == 1 -> m.oneFailed(task, failed[0].first, failed[0].second)
    else -> m.someFailed(task, failed.size, total, failed.joinToString("; ") { (name, reason) -> "$name ($reason)" })
}
