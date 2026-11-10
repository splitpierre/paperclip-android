package com.bu.paperclip.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bu.paperclip.net.Api

/** A Markdown document split into prose (rendered by Markwon) GFM tables and code blocks (rendered natively). */
sealed class MdBlock {
    data class Prose(val text: String) : MdBlock()
    data class Table(val header: List<String>, val align: List<TextAlign>, val rows: List<List<String>>) : MdBlock()
    data class Code(val text: String, val lang: String) : MdBlock()
}

private val delimiterCell = Regex("""^\s*:?-{1,}:?\s*$""")

/** Splits cells on unescaped pipes outside inline code; drops the optional outer pipes. */
internal fun splitTableRow(line: String): List<String> {
    var s = line.trim()
    if (s.startsWith("|")) s = s.substring(1)
    if (s.endsWith("|") && !s.endsWith("\\|")) s = s.dropLast(1)
    val cells = ArrayList<String>()
    val cur = StringBuilder()
    var inCode = false
    var i = 0
    while (i < s.length) {
        val c = s[i]
        when {
            c == '\\' && i + 1 < s.length && s[i + 1] == '|' -> { cur.append('|'); i++ }
            c == '`' -> { inCode = !inCode; cur.append(c) }
            c == '|' && !inCode -> { cells.add(cur.toString().trim()); cur.clear() }
            else -> cur.append(c)
        }
        i++
    }
    cells.add(cur.toString().trim())
    return cells
}

private fun isDelimiterRow(line: String): Boolean {
    if (!line.contains('-')) return false
    val cells = splitTableRow(line)
    return cells.isNotEmpty() && cells.all { delimiterCell.matches(it) }
}

private fun alignOf(cell: String): TextAlign {
    val t = cell.trim()
    return when {
        t.startsWith(":") && t.endsWith(":") -> TextAlign.Center
        t.endsWith(":") -> TextAlign.End
        else -> TextAlign.Start
    }
}

private val fence = Regex("""^ {0,3}(`{3,}|~{3,})""")

/** Pulls top-level GFM tables out of [text]; tables inside code fences stay as prose. */
fun splitMarkdownTables(text: String): List<MdBlock> {
    if (!text.contains('|')) return listOf(MdBlock.Prose(text))
    val lines = text.split('\n')
    val out = ArrayList<MdBlock>()
    val prose = StringBuilder()
    var fenceMarker: String? = null
    var i = 0
    fun flushProse() {
        if (prose.isNotBlank()) out.add(MdBlock.Prose(prose.toString().trimEnd('\n')))
        prose.clear()
    }
    while (i < lines.size) {
        val line = lines[i]
        val f = fence.find(line)?.groupValues?.get(1)
        if (fenceMarker != null) {
            if (f != null && f[0] == fenceMarker!![0] && f.length >= fenceMarker!!.length) fenceMarker = null
            prose.append(line).append('\n'); i++; continue
        }
        if (f != null) { fenceMarker = f; prose.append(line).append('\n'); i++; continue }
        val indent = line.length - line.trimStart().length
        if (indent <= 3 && line.contains('|') && i + 1 < lines.size && isDelimiterRow(lines[i + 1])) {
            val header = splitTableRow(line)
            val delims = splitTableRow(lines[i + 1])
            if (header.size == delims.size) {
                val rows = ArrayList<List<String>>()
                var j = i + 2
                while (j < lines.size && lines[j].isNotBlank() && lines[j].contains('|') && fence.find(lines[j]) == null) {
                    val cells = splitTableRow(lines[j])
                    rows.add(List(header.size) { k -> cells.getOrElse(k) { "" } })
                    j++
                }
                flushProse()
                out.add(MdBlock.Table(header, delims.map(::alignOf), rows))
                i = j
                continue
            }
        }
        prose.append(line).append('\n')
        i++
    }
    flushProse()
    return out
}

private val inlineRe = Regex(
    """`([^`]+)`|\*\*(.+?)\*\*|__(.+?)__|~~(.+?)~~|\[([^\]]+)]\(([^)\s]+)\)|(?<![\w*])\*(?!\s)(.+?)(?<!\s)\*(?!\w)|(?<![\w_])_(?!\s)(.+?)(?<!\s)_(?!\w)|<br\s*/?>|(https?://[^\s<>()]+)""",
    RegexOption.IGNORE_CASE,
)

/** Inline GFM (code, bold, italic, strike, links, autolinks, <br>) for one table cell. */
internal fun inlineMarkdown(
    text: String,
    codeBg: Color,
    linkColor: Color,
    onLink: (String) -> Unit,
): AnnotatedString = buildAnnotatedString {
    fun link(url: String, label: String) {
        pushLink(LinkAnnotation.Clickable(url) { onLink(url) })
        withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) { append(label) }
        pop()
    }
    fun walk(s: String) {
        var last = 0
        for (m in inlineRe.findAll(s)) {
            append(s.substring(last, m.range.first))
            val g = m.groupValues
            when {
                g[1].isNotEmpty() -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg, fontSize = 0.92.em())) { append(g[1]) }
                g[2].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { walk(g[2]) }
                g[3].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { walk(g[3]) }
                g[4].isNotEmpty() -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { walk(g[4]) }
                g[5].isNotEmpty() -> link(g[6], g[5])
                g[7].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { walk(g[7]) }
                g[8].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { walk(g[8]) }
                g[9].isNotEmpty() -> link(g[9], g[9])
                else -> append('\n')
            }
            last = m.range.last + 1
        }
        append(s.substring(last))
    }
    walk(text)
}

private fun Double.em() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Em)

private val MIN_COL = 56.dp
private val MAX_COL = 260.dp
private val CELL_PAD_H = 10.dp
private val CELL_PAD_V = 7.dp

/**
 * A GFM table that keeps readable column widths: each column fits its content up to [MAX_COL]
 * (longer cells wrap), and the table scrolls sideways instead of squeezing to the screen width.
 */
@Composable
fun MarkdownTable(table: MdBlock.Table, fontSizeSp: Float, selectable: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scheme = MaterialTheme.colorScheme
    val codeBg = scheme.surfaceContainerHighest
    val linkColor = scheme.tertiary
    val border = scheme.outlineVariant
    val onLink: (String) -> Unit = { url ->
        if (!Links.handler(url)) runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Api.absolute(url))))
        }
    }
    val bodyStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = (fontSizeSp - 1f).coerceAtLeast(11f).sp, lineHeight = ((fontSizeSp - 1f) * 1.35f).sp)
    val headStyle = bodyStyle.copy(fontWeight = FontWeight.SemiBold)
    val header = remember(table, codeBg, linkColor) { table.header.map { inlineMarkdown(it, codeBg, linkColor, onLink) } }
    val rows = remember(table, codeBg, linkColor) { table.rows.map { r -> r.map { inlineMarkdown(it, codeBg, linkColor, onLink) } } }
    val measurer = rememberTextMeasurer()
    val natural: List<Dp> = remember(table, bodyStyle, density) {
        table.header.indices.map { col ->
            val widest = (listOf(header[col] to headStyle) + rows.map { it[col] to bodyStyle }).maxOf { (t, st) ->
                measurer.measure(t, st, softWrap = false, maxLines = 1).size.width
            }
            with(density) { (widest.toDp() + CELL_PAD_H * 2 + 1.dp).coerceIn(MIN_COL, MAX_COL) }
        }
    }

    BoxWithConstraints(modifier) {
        // Narrow tables stretch to the full width; wide ones keep their natural widths and scroll.
        val total = natural.fold(0.dp) { a, b -> a + b }
        val widths = if (maxWidth != Dp.Infinity && total < maxWidth) natural.map { it * (maxWidth / total) } else natural
        val scroll = rememberScrollState()
        val fade = scheme.surface
        Box(
            // Edge fades show there is more table to either side.
            Modifier.drawWithContent {
                drawContent()
                val w = 20.dp.toPx()
                if (scroll.canScrollForward) drawRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, fade.copy(alpha = 0.9f)), startX = size.width - w, endX = size.width),
                    topLeft = Offset(size.width - w, 0f), size = Size(w, size.height),
                )
                if (scroll.canScrollBackward) drawRect(
                    Brush.horizontalGradient(listOf(fade.copy(alpha = 0.9f), Color.Transparent), startX = 0f, endX = w),
                    size = Size(w, size.height),
                )
            },
        ) {
            val grid: @Composable () -> Unit = {
                Column(
                    Modifier.horizontalScroll(scroll)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, border, RoundedCornerShape(8.dp)),
                ) {
                    TableRow(header, widths, table.align, headStyle, scheme.surfaceContainerHigh, border, last = rows.isEmpty())
                    rows.forEachIndexed { i, r ->
                        TableRow(r, widths, table.align, bodyStyle, if (i % 2 == 1) scheme.surfaceContainerLow else Color.Transparent, border, last = i == rows.lastIndex)
                    }
                }
            }
            if (selectable) SelectionContainer { grid() } else grid()
        }
    }
}

@Composable
private fun TableRow(
    cells: List<AnnotatedString>,
    widths: List<Dp>,
    align: List<TextAlign>,
    style: TextStyle,
    bg: Color,
    border: Color,
    last: Boolean,
) {
    Row(
        Modifier.background(bg).height(IntrinsicSize.Min).drawBehind {
            if (!last) drawLine(border, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1.dp.toPx())
        },
    ) {
        cells.forEachIndexed { i, cell ->
            if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(border))
            Text(
                cell,
                style = style,
                textAlign = align.getOrElse(i) { TextAlign.Start },
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(widths[i] - if (i > 0) 1.dp else 0.dp).padding(horizontal = CELL_PAD_H, vertical = CELL_PAD_V),
            )
        }
    }
}
