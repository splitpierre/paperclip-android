package com.bu.paperclip.ui.components

import com.bu.paperclip.util.catching

import android.content.Context
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.text.Spanned
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import com.bu.paperclip.R
import com.bu.paperclip.net.Api
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.LinkResolver
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.destination.ImageDestinationProcessor
import io.noties.markwon.image.network.OkHttpNetworkSchemeHandler
import io.noties.markwon.linkify.LinkifyPlugin

/** App-wide link router; set by the navigation host. Returns true when the link was handled in-app. */
object Links {
    var handler: (String) -> Boolean = { false }
}

private var cached: Triple<Int, String, Markwon>? = null

private fun markwon(context: Context, codeBg: Int): Markwon {
    cached?.let { (bg, base, m) -> if (bg == codeBg && base == Api.baseUrl) return m }
    val m = Markwon.builder(context)
        .usePlugin(StrikethroughPlugin.create())
        .usePlugin(TablePlugin.create(context))
        .usePlugin(TaskListPlugin.create(context))
        .usePlugin(LinkifyPlugin.create())
        .usePlugin(ImagesPlugin.create { plugin ->
            plugin.addSchemeHandler(OkHttpNetworkSchemeHandler.create(Api.mediaClient))
        })
        .usePlugin(object : AbstractMarkwonPlugin() {
            override fun configureTheme(builder: MarkwonTheme.Builder) {
                val mono = ResourcesCompat.getFont(context, R.font.jetbrains_mono_regular)
                val heading = ResourcesCompat.getFont(context, R.font.inter_semibold)
                builder.codeBackgroundColor(codeBg)
                    .codeBlockBackgroundColor(codeBg)
                    .codeTextSize(spToPx(context, 12.5f))
                    .codeBlockMargin(spToPx(context, 12f))
                    .blockQuoteWidth(spToPx(context, 3f))
                    .bulletWidth(spToPx(context, 5f))
                    .apply { if (mono != null) codeTypeface(mono).codeBlockTypeface(mono) }
                    .apply { if (heading != null) headingTypeface(heading) }
                    .headingBreakHeight(0)
                    .headingTextSizeMultipliers(floatArrayOf(1.3f, 1.18f, 1.08f, 1f, 1f, 1f))
            }

            override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                builder.imageDestinationProcessor(object : ImageDestinationProcessor() {
                    override fun process(destination: String): String = Api.absolute(destination)
                })
                builder.linkResolver(object : LinkResolver {
                    override fun resolve(view: View, link: String) {
                        if (Links.handler(link)) return
                        catching {
                            val i = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(Api.absolute(link)))
                            view.context.startActivity(i)
                        }
                    }
                })
            }
        })
        .build()
    cached = Triple(codeBg, Api.baseUrl, m)
    return m
}

private fun spToPx(context: Context, sp: Float): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.resources.displayMetrics).toInt()

/** Paperclip mentions are markdown links with custom schemes; show them as "@Name". */
private val mentionRe = Regex("""\[(@?[^\]]+)]\((agent|user|project|skill|routine|pipeline)://[^)\s]*\)""")

fun prepareMarkdown(text: String): String = mentionRe.replace(text) { m ->
    val label = m.groupValues[1]
    val scheme = m.groupValues[2]
    val href = m.value.substringAfter("](").dropLast(1)
    if (scheme == "agent" || scheme == "project") "[$label]($href)" else "**$label**"
}

/**
 * Renders GitHub-flavoured markdown with selectable text, images and in-app links. Tables are drawn
 * natively (see [MarkdownTable]) so they keep readable columns and scroll sideways on a phone.
 */
@Composable
fun Markdown(text: String, modifier: Modifier = Modifier, fontSizeSp: Float = 15f, selectable: Boolean = true) {
    val blocks = remember(text) { splitMarkdownTables(prepareMarkdown(text)) }
    val single = blocks.singleOrNull() as? MdBlock.Prose
    if (single != null) {
        MarkdownText(single.text, modifier, fontSizeSp, selectable)
        return
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { b ->
            when (b) {
                is MdBlock.Prose -> MarkdownText(b.text, Modifier.fillMaxWidth(), fontSizeSp, selectable)
                is MdBlock.Table -> MarkdownTable(b, fontSizeSp, selectable, Modifier.fillMaxWidth().padding(vertical = 2.dp))
            }
        }
    }
}

/** Markwon in a TextView, for [prepared] markdown (mentions already rewritten). */
@Composable
private fun MarkdownText(prepared: String, modifier: Modifier, fontSizeSp: Float, selectable: Boolean) {
    val context = LocalContext.current
    val color = LocalContentColor.current.toArgb()
    val link = MaterialTheme.colorScheme.tertiary.toArgb()
    val codeBg = MaterialTheme.colorScheme.surfaceContainerHighest.toArgb()
    val mw = remember(codeBg, Api.baseUrl) { markwon(context.applicationContext, codeBg) }
    val rendered = rememberRenderedMarkdown(prepared, mw)
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                setTextIsSelectable(selectable)
                movementMethod = LinkMovementMethod.getInstance()
                setLineSpacing(0f, 1.2f)
                typeface = ResourcesCompat.getFont(ctx, R.font.inter_regular)
                // Inter's tracking, as in the Compose type scale.
                letterSpacing = -0.004f
            }
        },
        // Opt in to LazyColumn's AndroidView pool. A row entering view can reuse its TextView.
        onReset = { tv ->
            // Markwon clears image subscriptions before replacing text. Never carry selection,
            // image spans or a previous row's render identity into another comment.
            mw.setParsedMarkdown(tv, android.text.SpannedString(""))
            tv.tag = null
            tv.clearFocus()
        },
        onRelease = { tv -> mw.setParsedMarkdown(tv, android.text.SpannedString("")) },
        update = { tv ->
            tv.setTextIsSelectable(selectable)
            tv.movementMethod = LinkMovementMethod.getInstance()
            tv.setTextColor(color)
            tv.setLinkTextColor(link)
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
            val content = rendered
            val identity: Any = content
            if (tv.tag !== identity) {
                mw.setParsedMarkdown(tv, content)
                tv.tag = identity
            }
        },
    )
}

/** First display has final formatting/height; subsequent edits retain it while rendering off Main. */
@Composable
internal fun rememberRenderedMarkdown(
    prepared: String,
    renderer: Markwon,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
): Spanned {
    var rendered by remember(renderer) { mutableStateOf(renderer.toMarkdown(prepared)) }
    var displayedSource by remember(renderer) { mutableStateOf(prepared) }
    LaunchedEffect(prepared, renderer) {
        if (prepared != displayedSource) {
            rendered = withContext(dispatcher) { renderer.toMarkdown(prepared) }
            displayedSource = prepared
        }
    }
    return rendered
}
