package com.bu.paperclip.ui.components

import android.content.Context
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
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
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.image.ImageProps
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.destination.ImageDestinationProcessor
import io.noties.markwon.image.network.OkHttpNetworkSchemeHandler
import io.noties.markwon.linkify.LinkifyPlugin
import org.commonmark.node.Image

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
                    override fun resolve(view: View, link: String) = openLink(view, link)
                })
            }

            // Tapping an image opens it like a link, so an attachment shown inline saves and opens in one tap.
            override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
                builder.appendFactory(Image::class.java) { _, props ->
                    val dest = ImageProps.DESTINATION.get(props) ?: return@appendFactory null
                    object : ClickableSpan() {
                        override fun onClick(widget: View) = openLink(widget, dest)
                        override fun updateDrawState(ds: TextPaint) {}
                    }
                }
            }
        })
        .build()
    cached = Triple(codeBg, Api.baseUrl, m)
    return m
}

private fun openLink(view: View, link: String) {
    if (Links.handler(link)) return
    runCatching {
        val i = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(Api.absolute(link)))
        view.context.startActivity(i)
    }
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
    val mw = remember(codeBg, Api.baseUrl) { markwon(context, codeBg) }
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
        update = { tv ->
            tv.setTextColor(color)
            tv.setLinkTextColor(link)
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
            mw.setMarkdown(tv, prepared)
        },
    )
}
