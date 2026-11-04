package com.bu.paperclip.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bu.paperclip.data.AppState

private val openFence = Regex("""^ {0,3}(`{3,}|~{3,})\s*([^`\s]*).*$""")

/** Languages that are really just text (captions, messages): shown in the body font and wrapped. */
private val plainLangs = setOf("", "text", "txt", "plain", "markdown", "md")

/**
 * Splits [text] into prose and top-level fenced code blocks, then pulls tables out of the prose
 * (see [splitMarkdownTables]). An unclosed fence runs to the end, as in GFM.
 */
fun splitMarkdownBlocks(text: String): List<MdBlock> {
    if (!text.contains("```") && !text.contains("~~~")) return splitMarkdownTables(text)
    val out = ArrayList<MdBlock>()
    val prose = StringBuilder()
    fun flushProse() {
        if (prose.isNotBlank()) out.addAll(splitMarkdownTables(prose.toString().trimEnd('\n')))
        prose.clear()
    }
    val lines = text.split('\n')
    var i = 0
    while (i < lines.size) {
        val open = openFence.find(lines[i])
        if (open == null) { prose.append(lines[i]).append('\n'); i++; continue }
        val marker = open.groupValues[1]
        val body = ArrayList<String>()
        var j = i + 1
        while (j < lines.size) {
            val close = lines[j].trim()
            if (close.length >= marker.length && close.all { it == marker[0] }) break
            body.add(lines[j])
            j++
        }
        flushProse()
        out.add(MdBlock.Code(body.joinToString("\n"), open.groupValues[2].lowercase()))
        i = j + 1
    }
    flushProse()
    return out
}

/** A code block with a Copy button, so a ready caption or text can be pasted elsewhere in one tap. */
@Composable
fun MarkdownCodeBlock(block: MdBlock.Code, fontSizeSp: Float, selectable: Boolean, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val plain = block.lang in plainLangs
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (plain) "" else block.lang,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { copyText(ctx, block.text) }) {
                Icon(Icons.Outlined.ContentCopy, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Copy")
            }
        }
        val text = @Composable {
            Text(
                block.text,
                fontSize = fontSizeSp.sp,
                fontFamily = if (plain) null else FontFamily.Monospace,
                softWrap = plain,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                    .let { if (plain) it else it.horizontalScroll(rememberScrollState()) },
            )
        }
        if (selectable) SelectionContainer { text() } else text()
    }
}

private fun copyText(ctx: Context, text: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("text", text))
    // Android 13+ shows its own confirmation.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) AppState.toast("Copied")
}
