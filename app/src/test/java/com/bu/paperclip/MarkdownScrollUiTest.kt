package com.bu.paperclip

import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import com.bu.paperclip.ui.components.Markdown
import com.bu.paperclip.ui.theme.PaperclipTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.IdentityHashMap

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class MarkdownScrollUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun scrollingReusesTextViewsWithTheNewRowsFormattingAndSelection() {
        lateinit var root: View
        compose.setContent {
            val view = LocalView.current
            SideEffect { root = view.rootView }
            PaperclipTheme {
                Box(Modifier.size(360.dp, 400.dp)) {
                    LazyColumn(Modifier.testTag("markdown-list")) {
                        items((0..150).toList(), key = { it }, contentType = { "comment" }) { row ->
                            Markdown("## Row $row\n\n**Formatted body $row**", selectable = row % 2 == 0)
                        }
                    }
                }
            }
        }
        fun textViews(view: View): List<TextView> = when (view) {
            is TextView -> listOf(view)
            is ViewGroup -> (0 until view.childCount).flatMap { textViews(view.getChildAt(it)) }
            else -> emptyList()
        }
        val seen = IdentityHashMap<TextView, String>()
        var reused = false
        fun inspect() = compose.runOnIdle {
            val visible = textViews(root).filter { it.text.startsWith("Row ") }
            assertTrue("Expected formatted Markdown TextViews", visible.isNotEmpty())
            visible.forEach { tv ->
                val text = tv.text.toString()
                val row = text.substringAfter("Row ").substringBefore('\n').toInt()
                assertEquals("Row $row\n\nFormatted body $row", text)
                assertEquals(row % 2 == 0, tv.isTextSelectable)
                if (seen.containsKey(tv) && seen[tv] != text) reused = true
                seen[tv] = text
            }
        }
        inspect()
        for (row in listOf(30, 60, 90, 120, 0)) {
            compose.onNodeWithTag("markdown-list").performScrollToIndex(row)
            inspect()
        }
        assertTrue("Scrolling should reuse native text views instead of allocating every row again", reused)
    }
}
