package com.bu.paperclip.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bu.paperclip.ui.theme.Mono
import com.bu.paperclip.data.TEntry
import com.bu.paperclip.data.TranscriptBuilder
import com.bu.paperclip.net.AppJson
import com.bu.paperclip.ui.components.Markdown
import com.bu.paperclip.ui.theme.LocalStatusColors
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import androidx.compose.ui.res.stringResource
import com.bu.paperclip.R
import com.bu.paperclip.util.appString

data class RunRawLine(val stream: String, val text: String)

/**
 * Accumulates a run's log from the paged /log endpoint and live WebSocket chunks.
 * All access happens on the main thread (composition coroutines), so no locking.
 */
class RunTranscriptState(adapterType: String?) {
    val builder = TranscriptBuilder(adapterType)
    val raw = ArrayList<RunRawLine>()
    private val rawSeqs = HashSet<Long>()
    private var partial = ""
    var offset: Long = 0
    var startOffset: Long = 0
    var loaded = false
    var dirty = true

    fun feedContent(content: String) {
        builder.feedLogContent(content)
        val data = partial + content
        val lines = data.split('\n')
        partial = lines.last()
        for (i in 0 until lines.size - 1) {
            val l = lines[i]
            if (l.isBlank()) continue
            val o = runCatching { AppJson.parseToJsonElement(l) }.getOrNull() as? JsonObject ?: continue
            val seq = (o["seq"] as? JsonPrimitive)?.longOrNull
            if (seq != null && !rawSeqs.add(seq)) continue
            raw.add(RunRawLine((o["stream"] as? JsonPrimitive)?.contentOrNull ?: "stdout", (o["chunk"] as? JsonPrimitive)?.contentOrNull ?: ""))
        }
        dirty = true
    }

    fun feedLive(ts: String?, stream: String, chunk: String, seq: Long?) {
        builder.feedRecord(ts, stream, chunk, seq)
        if (seq == null || rawSeqs.add(seq)) raw.add(RunRawLine(stream, chunk))
        dirty = true
    }
}

private val mono = Mono

@Composable
fun RunEntryView(e: TEntry) {
    when (e) {
        is TEntry.Assistant -> if (e.thinking) RunThinking(e.text) else {
            Markdown(e.text, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp))
        }
        is TEntry.User -> RunUserMessage(e.text)
        is TEntry.ToolCall -> RunToolCard(e)
        is TEntry.Init -> Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Text(
                    stringResource(R.string.session_started_x, e.model),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        is TEntry.Result -> RunResultCard(e)
        is TEntry.Line -> RunLogLine(e.stream, e.text)
    }
}

@Composable
private fun RunThinking(text: String) {
    var open by rememberSaveable(text.take(64)) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 16.dp, vertical = 6.dp).animateContentSize(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lightbulb, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(
                if (open) stringResource(R.string.thinking) else text.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: stringResource(R.string.thinking),
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (open) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (open) {
            Spacer(Modifier.height(4.dp))
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                Markdown(text, fontSizeSp = 13f)
            }
        }
    }
}

@Composable
private fun RunUserMessage(text: String) {
    val long = text.length > 400
    var open by rememberSaveable(text.take(64)) { mutableStateOf(!long) }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.clickable(enabled = long) { open = !open }.padding(12.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Person, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.prompt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (long) Text(if (open) stringResource(R.string.collapse) else stringResource(R.string.show_all), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            }
            Spacer(Modifier.height(4.dp))
            if (open) Markdown(text, fontSizeSp = 14f)
            else Text(text, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A short human summary of a tool's JSON input, e.g. the command or file path. */
internal fun runToolSummary(input: String): String {
    val el = runCatching { AppJson.parseToJsonElement(input) }.getOrNull()
    val o = el as? JsonObject
    if (o != null) {
        val keys = listOf("command", "CommandLine", "cmd", "file_path", "path", "AbsolutePath", "TargetFile", "pattern", "url", "Url", "query", "Query", "description", "prompt", "skill", "name")
        for (k in keys) {
            val v = o[k]
            when (v) {
                is JsonPrimitive -> v.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it.lineSequence().first().take(160) }
                is JsonArray -> if (v.isNotEmpty()) return v.joinToString(" ") { (it as? JsonPrimitive)?.contentOrNull ?: it.toString() }.take(160)
                else -> {}
            }
        }
        o.values.firstNotNullOfOrNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf { s -> s.isNotBlank() } }?.let { return it.lineSequence().first().take(160) }
        return ""
    }
    if (el is JsonPrimitive) return (el.contentOrNull ?: "").lineSequence().firstOrNull().orEmpty().take(160)
    return input.lineSequence().firstOrNull().orEmpty().take(160)
}

private const val RUN_RESULT_PREVIEW = 4000

@Composable
private fun RunToolCard(e: TEntry.ToolCall) {
    var open by rememberSaveable(e.id ?: (e.name + e.input.take(40))) { mutableStateOf(false) }
    val red = LocalStatusColors.current.red
    val green = LocalStatusColors.current.green
    val summary = remember(e.input) { runToolSummary(e.input) }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (e.isError) red.copy(alpha = 0.07f) else MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (e.isError) red.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(Modifier.animateContentSize()) {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    !e.done -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.6.dp)
                    e.isError -> Icon(Icons.Outlined.ErrorOutline, stringResource(R.string.failed), Modifier.size(16.dp), tint = red)
                    else -> Icon(Icons.Outlined.CheckCircle, stringResource(R.string.done), Modifier.size(16.dp), tint = green)
                }
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Outlined.Build, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text(e.name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                if (summary.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = mono, fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else Spacer(Modifier.weight(1f))
                Icon(
                    if (open) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (open) {
                if (e.input.isNotBlank()) RunCodeBlock(stringResource(R.string.input), e.input, null)
                if (e.result != null) RunCodeBlock(if (e.isError) stringResource(R.string.error) else stringResource(R.string.result), e.result.ifBlank { "(empty)" }, if (e.isError) red else null)
                else if (!e.done) Text(stringResource(R.string.running_2), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp, bottom = 10.dp))
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun RunCodeBlock(label: String, text: String, tint: Color?) {
    var full by rememberSaveable(text.length, label) { mutableStateOf(false) }
    val shown = if (full || text.length <= RUN_RESULT_PREVIEW) text else text.take(RUN_RESULT_PREVIEW)
    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint ?: MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(3.dp))
        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
            SelectionContainer {
                Text(
                    shown,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = mono, fontSize = 11.5.sp, lineHeight = 15.sp),
                    color = tint ?: MaterialTheme.colorScheme.onSurface,
                    softWrap = false,
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(8.dp),
                )
            }
        }
        if (!full && text.length > RUN_RESULT_PREVIEW) {
            TextButton(onClick = { full = true }) { Text(stringResource(R.string.show_all_x_k_chars, text.length / 1000)) }
        }
    }
}

@Composable
private fun RunResultCard(e: TEntry.Result) {
    val st = LocalStatusColors.current
    val color = if (e.isError) st.red else st.green
    var open by rememberSaveable(e.text.take(64)) { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(12.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (e.isError) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircle, null, Modifier.size(16.dp), tint = color)
                Spacer(Modifier.width(6.dp))
                Text(if (e.isError) stringResource(R.string.turn_failed) else stringResource(R.string.turn_complete), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                if (e.costUsd > 0) Text(String.format("$%.4f", e.costUsd), style = MaterialTheme.typography.labelMedium)
            }
            val tokens = listOfNotNull(
                e.inTok.takeIf { it > 0 }?.let { "${agentFmtTokens(it)} in" },
                e.cachedTok.takeIf { it > 0 }?.let { "${agentFmtTokens(it)} cached" },
                e.outTok.takeIf { it > 0 }?.let { "${agentFmtTokens(it)} out" },
            )
            if (tokens.isNotEmpty()) {
                Text(tokens.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            e.errors.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = st.red) }
            if (e.text.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                if (open) Markdown(e.text, fontSizeSp = 14f)
                else Text(e.text, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { open = true })
            }
        }
    }
}

@Composable
fun RunLogLine(stream: String, text: String) {
    val color = when (stream) {
        "stderr" -> LocalStatusColors.current.red
        "system" -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    Text(
        text.trimEnd(),
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = mono, fontSize = if (stream == "system") 11.sp else 11.5.sp, lineHeight = 15.sp),
        color = color,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)
            .then(if (stream == "stderr") Modifier.background(color.copy(alpha = 0.06f)) else Modifier),
    )
}
