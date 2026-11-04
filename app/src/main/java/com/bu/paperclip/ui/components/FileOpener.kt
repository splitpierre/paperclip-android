package com.bu.paperclip.ui.components

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.bu.paperclip.data.AppState
import com.bu.paperclip.net.Api
import com.bu.paperclip.net.ServerFiles
import com.bu.paperclip.ui.theme.Mono
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** A server file downloaded into the app's cache, ready to show or hand to another app. */
data class LocalFile(val file: File, val name: String, val mime: String)

sealed interface FileView {
    data class Loading(val url: String, val name: String?) : FileView
    data class Image(val f: LocalFile) : FileView
    data class Text(val f: LocalFile, val text: String, val markdown: Boolean) : FileView
    /** Waiting for [FileOpenerHost] to hand the file to another app. */
    data class External(val f: LocalFile) : FileView
    /** No installed app takes this type; offer share and save instead. */
    data class NoApp(val f: LocalFile) : FileView
}

/**
 * Opens Paperclip attachments and assets with the app's session. The external browser has neither the
 * session cookie nor the board key, so a plain ACTION_VIEW on these URLs gets a 401. Every in-app link
 * goes through [handle] (via [Links]); the Files tab calls [open] directly.
 *
 * Images and text open in the app; everything else is downloaded and handed to another app, with share
 * and save as the way out when nothing can open it.
 */
object FileOpener {
    private const val MAX_INLINE_TEXT = 1L shl 20

    private val _view = MutableStateFlow<FileView?>(null)
    val view: StateFlow<FileView?> = _view
    private var job: Job? = null
    private lateinit var cacheRoot: File

    fun init(context: Context) { cacheRoot = File(context.cacheDir, "attachments") }

    /** Opens [link] when it is a file on the Paperclip server; false for any other link. */
    fun handle(link: String): Boolean {
        val url = ServerFiles.contentUrl(link, Api.baseUrl) ?: return false
        open(url.toString())
        return true
    }

    /** Downloads [url] with the session and shows it. [name] and [type] are hints from the attachment record. */
    fun open(url: String, name: String? = null, type: String? = null) {
        job?.cancel()
        _view.value = FileView.Loading(url, name)
        job = AppState.scope.launch {
            try {
                val f = download(url, name, type)
                _view.value = when {
                    ServerFiles.isImage(f.mime) -> FileView.Image(f)
                    (ServerFiles.isText(f.mime) || ServerFiles.isMarkdown(f.mime, f.name)) && f.file.length() <= MAX_INLINE_TEXT ->
                        FileView.Text(f, withContext(Dispatchers.IO) { f.file.readText() }, ServerFiles.isMarkdown(f.mime, f.name))
                    else -> FileView.External(f)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                _view.value = null
                AppState.toast(failure(t))
            }
        }
    }

    fun cancel() { job?.cancel(); _view.value = null }
    fun close() { _view.value = null }
    internal fun noApp(f: LocalFile) { _view.value = FileView.NoApp(f) }

    private fun failure(t: Throwable): String = when (t) {
        is HttpFailure -> when (t.code) {
            401, 403 -> "You don't have access to this file. Try signing in again."
            404 -> "This file no longer exists."
            else -> "Couldn't download the file (HTTP ${t.code})."
        }
        is IOException -> "Couldn't download the file: ${t.message ?: "network error"}"
        else -> "Couldn't open the file: ${errorMessage(t)}"
    }

    private class HttpFailure(val code: Int) : IOException("HTTP $code")

    /**
     * Downloads into `cache/attachments/<hash of url>/<file name>` so the receiving app sees the real
     * name. Files are immutable on the server, so a finished download is reused.
     */
    private suspend fun download(url: String, nameHint: String?, typeHint: String?): LocalFile = withContext(Dispatchers.IO) {
        val dir = File(cacheRoot, sha1(url).take(16)).apply { mkdirs() }
        val meta = File(dir, ".meta")
        if (meta.exists()) {
            val (name, mime) = meta.readLines().let { (it.getOrNull(0) ?: "") to (it.getOrNull(1) ?: "") }
            val file = File(dir, name)
            if (name.isNotEmpty() && file.exists()) return@withContext LocalFile(file, name, mime)
        }
        Api.mediaClient.newCall(Request.Builder().url(url).header("Accept", "*/*").build()).execute().use { r ->
            if (!r.isSuccessful) throw HttpFailure(r.code)
            val name = ServerFiles.safeFilename(nameHint ?: ServerFiles.filenameFromDisposition(r.header("Content-Disposition")) ?: url.toHttpName())
            val mime = ServerFiles.mimeType(name, typeHint?.takeIf { it.isNotBlank() } ?: r.header("Content-Type")) {
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(it)
            }
            val part = File(dir, "$name.part")
            part.outputStream().use { os -> r.body!!.byteStream().copyTo(os) }
            val file = File(dir, name)
            if (!part.renameTo(file)) { part.copyTo(file, overwrite = true); part.delete() }
            meta.writeText("$name\n$mime\n")
            LocalFile(file, name, mime)
        }
    }

    private fun String.toHttpName(): String = substringBefore('?').trimEnd('/').removeSuffix("/content").substringAfterLast('/')

    private fun sha1(s: String): String = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    fun uriFor(ctx: Context, f: LocalFile): Uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f.file)

    fun share(ctx: Context, f: LocalFile) {
        val uri = uriFor(ctx, f)
        val send = Intent(Intent.ACTION_SEND).setType(f.mime).putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData = ClipData.newRawUri(f.name, uri) }
        try {
            ctx.startActivity(Intent.createChooser(send, f.name))
        } catch (_: ActivityNotFoundException) {
            AppState.toast("No app can share this file")
        }
    }

    /** Hands the file to another app; false when none is installed for its type. */
    fun openExternally(ctx: Context, f: LocalFile): Boolean {
        val uri = uriFor(ctx, f)
        // Apps rarely register text/markdown and friends; text/plain reaches any text viewer.
        val types = listOf(f.mime) + if (ServerFiles.isText(f.mime) || ServerFiles.isMarkdown(f.mime, f.name)) listOf("text/plain") else emptyList()
        for (type in types.distinct()) {
            val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            try {
                ctx.startActivity(view)
                return true
            } catch (_: ActivityNotFoundException) {
            }
        }
        return false
    }
}

/** Shows whatever [FileOpener] is opening. Lives once at the root of the signed-in UI. */
@Composable
fun FileOpenerHost() {
    val ctx = LocalContext.current
    val view by FileOpener.view.collectAsState()
    var saving by remember { mutableStateOf<LocalFile?>(null) }
    val save = rememberLauncherForActivityResult(object : ActivityResultContracts.CreateDocument("application/octet-stream") {
        override fun createIntent(context: Context, input: String): Intent =
            super.createIntent(context, input).setType(saving?.mime ?: "application/octet-stream")
    }) { dest ->
        val f = saving
        saving = null
        if (dest == null || f == null) return@rememberLauncherForActivityResult
        AppState.scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openOutputStream(dest)!!.use { os -> f.file.inputStream().use { it.copyTo(os) } } }.isSuccess
            }
            AppState.toast(if (ok) "Saved ${f.name}" else "Couldn't save ${f.name}")
        }
    }
    val onSave: (LocalFile) -> Unit = { f -> saving = f; save.launch(f.name) }
    val onShare: (LocalFile) -> Unit = { f -> FileOpener.share(ctx, f) }

    when (val v = view) {
        null -> {}
        is FileView.Loading -> AlertDialog(
            onDismissRequest = { FileOpener.cancel() },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { FileOpener.cancel() }) { Text("Cancel") } },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(16.dp))
                    Text("Opening ${v.name ?: "file"}…", maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            },
        )
        is FileView.External -> LaunchedEffect(v) {
            if (FileOpener.openExternally(ctx, v.f)) FileOpener.close() else FileOpener.noApp(v.f)
        }
        is FileView.NoApp -> AlertDialog(
            onDismissRequest = { FileOpener.close() },
            title = { Text("Can't open ${v.f.name}") },
            text = { Text("No app on this device opens ${v.f.mime} files. You can share it to another app or save it to your files.") },
            confirmButton = { TextButton(onClick = { FileOpener.close(); onSave(v.f) }) { Text("Save") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { FileOpener.close() }) { Text("Close") }
                    TextButton(onClick = { FileOpener.close(); onShare(v.f) }) { Text("Share") }
                }
            },
        )
        is FileView.Image -> FileDialog(v.f, onShare, onSave, dark = true) {
            var scale by remember { mutableFloatStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            AsyncImage(
                model = v.f.file,
                contentDescription = v.f.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offset = if (scale == 1f) Offset.Zero else offset + pan
                        }
                    }
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            )
        }
        is FileView.Text -> FileDialog(v.f, onShare, onSave, dark = false) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding()) {
                if (v.markdown) {
                    Markdown(v.text, Modifier.fillMaxWidth())
                } else {
                    SelectionContainer(Modifier.horizontalScroll(rememberScrollState())) {
                        Text(v.text, fontFamily = Mono, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                }
            }
        }
    }
}

/** Full-screen viewer with the file name, share, save and close. */
@Composable
private fun FileDialog(f: LocalFile, onShare: (LocalFile) -> Unit, onSave: (LocalFile) -> Unit, dark: Boolean, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = { FileOpener.close() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val bg = if (dark) Color.Black else MaterialTheme.colorScheme.surface
        val fg = if (dark) Color.White else MaterialTheme.colorScheme.onSurface
        Surface(Modifier.fillMaxSize(), color = bg, contentColor = fg) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { FileOpener.close() }) { Icon(Icons.Default.Close, "Close", tint = fg) }
                    Text(f.name, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(horizontal = 4.dp))
                    IconButton(onClick = { onShare(f) }) { Icon(Icons.Outlined.Share, "Share", tint = fg) }
                    IconButton(onClick = { onSave(f) }) { Icon(Icons.Outlined.Download, "Save", tint = fg) }
                }
                Box(Modifier.weight(1f).fillMaxWidth().background(bg)) { content() }
            }
        }
    }
}
