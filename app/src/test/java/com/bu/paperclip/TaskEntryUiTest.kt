package com.bu.paperclip

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.bu.paperclip.data.Issue
import com.bu.paperclip.data.TaskFilter
import com.bu.paperclip.ui.Nav
import com.bu.paperclip.ui.screens.TaskTreeView
import com.bu.paperclip.ui.screens.rememberTasksModel
import com.bu.paperclip.ui.theme.PaperclipTheme
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class TaskEntryUiTest {
    @get:Rule val compose = createComposeRule()
    private val tasks = (0..199).associate { index ->
        val id = index.toString().padStart(3, '0')
        id to Issue(id = id, identifier = "DEMO-$id", title = "Task $id", status = "todo")
    }

    @Test fun populatedTasksAreVisibleOnTheFirstFrameAndStayWhileUpdatesWait() {
        val pending = StandardTestDispatcher()
        val counts = mutableListOf<Int>()
        var source by mutableStateOf(tasks)
        compose.setContent {
            PaperclipTheme {
                val model = rememberTasksModel("demo", source, emptySet(), null, TaskFilter(), pending)
                SideEffect { counts.add(model.matched.size) }
                Box(Modifier.size(360.dp, 480.dp)) {
                    TaskTreeView(model, Nav(rememberNavController()), null, {},
                        remember { mutableStateMapOf() }, dispatcher = pending, onLongPress = {})
                }
            }
        }
        assertEquals(200, counts.first())
        compose.onNode(hasScrollAction()).performScrollToIndex(80)
        val viewport = compose.onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot
        val anchor = compose.onAllNodes(hasText("Task ", substring = true)).fetchSemanticsNodes()
            .filter { it.boundsInRoot.top >= viewport.top && it.boundsInRoot.bottom <= viewport.bottom }
            .minBy { it.boundsInRoot.top }
        val text = anchor.config[SemanticsProperties.Text].first { it.text.startsWith("Task ") }.text
        val top = anchor.boundsInRoot.top
        compose.runOnIdle { source = tasks + ("new" to Issue(id = "new", title = "New task", status = "todo")) }
        compose.onNodeWithText(text).assertIsDisplayed()
        assertEquals(top, compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top, 0f)
        assertEquals(200, counts.last())
    }

    @Test fun restoredTreeRetainsTheVisibleTaskWithoutAnEmptyIntermediateFrame() {
        val pending = StandardTestDispatcher()
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            PaperclipTheme {
                val model = rememberTasksModel("demo", tasks, emptySet(), null, TaskFilter(), pending)
                Box(Modifier.size(360.dp, 480.dp)) {
                    TaskTreeView(model, Nav(rememberNavController()), null, {},
                        remember { mutableStateMapOf() }, dispatcher = pending, onLongPress = {})
                }
            }
        }
        compose.onNode(hasScrollAction()).performScrollToIndex(80)
        val viewport = compose.onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot
        val anchor = compose.onAllNodes(hasText("Task ", substring = true)).fetchSemanticsNodes()
            .filter { it.boundsInRoot.top >= viewport.top && it.boundsInRoot.bottom <= viewport.bottom }
            .minBy { it.boundsInRoot.top }
        val text = anchor.config[SemanticsProperties.Text].first { it.text.startsWith("Task ") }.text
        val top = anchor.boundsInRoot.top
        restoration.emulateSavedInstanceStateRestore()
        assertEquals(top, compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top, 0f)
    }
    @Test fun switchingCompanySeedsItsOwnTreeWhileBackgroundUpdatesWait() {
        val pending = StandardTestDispatcher()
        var company by mutableStateOf("first")
        val first = mapOf("first" to Issue(id = "first", title = "First company task", status = "todo"))
        val second = mapOf("second" to Issue(id = "second", title = "Second company task", status = "todo"))
        compose.setContent {
            PaperclipTheme {
                val model = rememberTasksModel(company, if (company == "first") first else second,
                    emptySet(), null, TaskFilter(), pending)
                Box(Modifier.size(360.dp, 480.dp)) {
                    TaskTreeView(model, Nav(rememberNavController()), null, {},
                        remember(company) { mutableStateMapOf() }, dispatcher = pending, onLongPress = {})
                }
            }
        }
        compose.onAllNodes(hasText("First company task"))[0].assertIsDisplayed()
        compose.runOnIdle { company = "second" }
        compose.onAllNodes(hasText("Second company task"))[0].assertIsDisplayed()
        compose.onNodeWithText("First company task").assertDoesNotExist()
    }

}
