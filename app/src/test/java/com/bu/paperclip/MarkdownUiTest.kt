package com.bu.paperclip

import android.app.Application
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import com.bu.paperclip.ui.components.rememberRenderedMarkdown
import io.noties.markwon.Markwon
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class MarkdownUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun firstFrameIsFormattedAndEditsRetainItUntilBackgroundRenderingFinishes() {
        val renderer = Markwon.create(RuntimeEnvironment.getApplication())
        val pending = StandardTestDispatcher()
        var source by mutableStateOf("# First title\n\n**First body**")
        val frames = mutableListOf<String>()
        compose.setContent {
            val rendered = rememberRenderedMarkdown(source, renderer, pending)
            SideEffect { frames.add(rendered.toString()) }
        }
        assertEquals("First title\n\nFirst body", frames.first())
        compose.runOnIdle { source = "# Updated title\n\n**Updated body**" }
        assertEquals("First title\n\nFirst body", frames.last())
        compose.runOnIdle { pending.scheduler.advanceUntilIdle() }
        compose.waitForIdle()
        assertEquals("Updated title\n\nUpdated body", frames.last())
    }
}
