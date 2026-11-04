package com.bu.paperclip.ui.components

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.bu.paperclip.data.AppState
import com.bu.paperclip.net.Api
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

/**
 * Attachment links in comments ("/api/attachments/{id}/content") need the session cookies, which the
 * browser doesn't have, so it would land on the sign-in page. Instead the app downloads the file itself,
 * saves it to the phone (images to Pictures/Paperclip and videos to Movies/Paperclip, so the gallery and
 * other apps see them; other files to Download/Paperclip) and opens it.
 */
object AttachmentLinks {
    private val re = Regex("""(?:^|/)api/attachments/([0-9a-f-]{36})/content""")
    private val saved = mutableMapOf<String, Pair<Uri, String>>()

    fun id(local: String): String? = re.find(local)?.groupValues?.get(1)

    /** Saves (once per app run) and opens the attachment; [onDone] runs when it's finished or failed. */
    fun open(ctx: Context, id: String, onDone: () -> Unit = {}) {
        val app = ctx.applicationContext
        AppState.scope.launch {
            try {
                val (uri, type) = saved[id] ?: run {
                    AppState.toast("Downloading…")
                    withContext(Dispatchers.IO) { download(app, id) }.also { saved[id] = it }
                }
                val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, type)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { app.startActivity(view) }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                AppState.toast("Couldn't open: ${errorMessage(t)}")
            } finally {
                onDone()
            }
        }
    }

    private fun download(ctx: Context, id: String): Pair<Uri, String> {
        val url = Api.absolute("/api/attachments/$id/content")
        Api.mediaClient.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            val type = r.header("Content-Type")?.substringBefore(';')?.trim()?.ifEmpty { null } ?: "application/octet-stream"
            val name = fileName(r.header("Content-Disposition"))
                ?: ("paperclip-${id.take(8)}" + (MimeTypeMap.getSingleton().getExtensionFromMimeType(type)?.let { ".$it" } ?: ""))
            val body = r.body ?: error("empty")
            val image = type.startsWith("image/")
            val video = type.startsWith("video/")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                // Before Android 10, saving to shared folders needs a storage permission; just open it.
                val dir = File(ctx.cacheDir, "attachments").apply { mkdirs() }
                val out = File(dir, "${id.take(8)}_" + name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
                out.outputStream().use { body.byteStream().copyTo(it) }
                return FileProvider.getUriForFile(ctx, ctx.packageName + ".files", out) to type
            }
            val folder = when {
                image -> Environment.DIRECTORY_PICTURES
                video -> Environment.DIRECTORY_MOVIES
                else -> Environment.DIRECTORY_DOWNLOADS
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, type)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/Paperclip")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val collection = when {
                image -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                video -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
            }
            val resolver = ctx.contentResolver
            val uri = resolver.insert(collection, values) ?: error("MediaStore")
            try {
                resolver.openOutputStream(uri)!!.use { body.byteStream().copyTo(it) }
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (t: Throwable) {
                resolver.delete(uri, null, null)
                throw t
            }
            AppState.toast("Saved to $folder/Paperclip/$name")
            return uri to type
        }
    }

    private fun fileName(disposition: String?): String? {
        if (disposition == null) return null
        Regex("""filename\*=UTF-8''([^;]+)""", RegexOption.IGNORE_CASE).find(disposition)?.let {
            return Uri.decode(it.groupValues[1]).trim().ifEmpty { null }
        }
        return Regex("""filename="?([^";]+)"?""", RegexOption.IGNORE_CASE).find(disposition)?.groupValues?.get(1)?.trim()?.ifEmpty { null }
    }
}
