package com.bu.paperclip

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.bu.paperclip.data.ActivityEvent
import com.bu.paperclip.data.DashboardSummary
import com.bu.paperclip.data.LiveRun
import com.bu.paperclip.data.RunActivityDay
import com.bu.paperclip.ui.Nav
import com.bu.paperclip.ui.screens.DashData
import com.bu.paperclip.ui.screens.DashRunActivityChart
import com.bu.paperclip.ui.screens.DashboardFeed
import com.bu.paperclip.ui.theme.PaperclipTheme
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class DashboardUiTest {
    @get:Rule val compose = createComposeRule()

    private fun event(id: Int) = ActivityEvent(
        id = id.toString(), actorType = "system", action = "issue.created",
        entityType = "issue", entityId = id.toString(),
        details = JsonObject(mapOf("identifier" to JsonPrimitive("DEMO-$id"))),
        createdAt = "2026-10-19T12:00:00Z",
    )

    @Test fun refreshKeepsTheVisibleActivityAnchoredWhileSectionsChange() {
        val summary = DashboardSummary(runActivity = (6..19).map {
            RunActivityDay("2026-10-${it.toString().padStart(2, '0')}", succeeded = it, failed = 2, recovered = 1)
        })
        var data by mutableStateOf(DashData(summary, emptyList(), (0..199).map(::event)))
        var pausedCount by mutableStateOf(0)
        lateinit var state: LazyListState
        compose.setContent {
            PaperclipTheme {
                state = rememberLazyListState()
                Box(Modifier.size(360.dp, 480.dp)) {
                    DashboardFeed(data, Nav(rememberNavController()), pausedCount = pausedCount,
                        state = state, modifier = Modifier.testTag("feed"))
                }
            }
        }
        compose.onNodeWithTag("feed").performScrollToIndex(45)
        compose.onNodeWithTag("feed").performTouchInput { swipeUp() }
        val viewport = compose.onNodeWithTag("feed").fetchSemanticsNode().boundsInRoot
        val anchorNode = compose.onAllNodes(hasText("Paperclip created DEMO-", substring = true))
            .fetchSemanticsNodes()
            .filter { it.boundsInRoot.top >= viewport.top && it.boundsInRoot.bottom <= viewport.bottom }
            .minBy { it.boundsInRoot.top }
        val anchorText = anchorNode.config[SemanticsProperties.Text]
            .first { it.text.startsWith("Paperclip created DEMO-") }.text
        val anchorTop = anchorNode.boundsInRoot.top
        compose.runOnIdle {
            // New events arrive at the head, a live run starts and the paused banner appears.
            // The same id in live/activity also checks the key namespaces.
            data = data.copy(live = listOf(LiveRun(id = "1", agentName = "Demo agent")),
                activity = listOf(event(201), event(202)) + data.activity.dropLast(2))
            pausedCount = 2
        }
        assertEquals(anchorTop, compose.onNodeWithText(anchorText).fetchSemanticsNode().boundsInRoot.top, 0f)
        compose.runOnIdle {
            data = data.copy(live = emptyList(), activity = data.activity.drop(1))
            pausedCount = 0
        }
        assertEquals(anchorTop, compose.onNodeWithText(anchorText).fetchSemanticsNode().boundsInRoot.top, 0f)
        // Exercise reuse in both directions, including the chart, metrics and empty live state.
        repeat(3) {
            compose.onNodeWithTag("feed").performTouchInput { swipeDown() }
            compose.onNodeWithTag("feed").performScrollToIndex(180)
            compose.onNodeWithTag("feed").performScrollToIndex(0)
            compose.onNodeWithTag("feed").performScrollToNode(hasText("No runs right now"))
            compose.onNodeWithText("No runs right now").assertIsDisplayed()
            compose.onNodeWithTag("feed").performScrollToNode(hasContentDescription("Run activity chart"))
            compose.onNodeWithContentDescription("Run activity chart").assertIsDisplayed()
        }
        compose.onNodeWithTag("feed").performScrollToNode(hasText("Paperclip created DEMO-150"))
        compose.onNodeWithText("Paperclip created DEMO-150").assertIsDisplayed()
    }

    @Test fun chartSelectionAndDataRefreshStillUpdateTheDetails() {
        var days by mutableStateOf(listOf(
            RunActivityDay("2026-10-17", succeeded = 3, failed = 2),
            RunActivityDay("2026-10-18", recovered = 4),
            RunActivityDay("2026-10-19", succeeded = 5),
        ))
        compose.setContent {
            PaperclipTheme {
                Box(Modifier.size(360.dp, 300.dp)) { DashRunActivityChart(days) }
            }
        }
        compose.onNodeWithText("19 Oct").assertIsDisplayed()
        compose.onNodeWithContentDescription("Run activity chart").performTouchInput { click(centerLeft) }
        compose.onNodeWithText("17 Oct").assertIsDisplayed()
        compose.onNodeWithText("3 ok · 2 failed · 0 other").assertIsDisplayed()
        compose.runOnIdle { days = listOf(RunActivityDay("2026-10-20", succeeded = 7, recovered = 2, other = 1)) }
        compose.onNodeWithText("20 Oct").assertIsDisplayed()
        compose.onNodeWithText("7 ok · 0 failed · 3 other").assertIsDisplayed()
        compose.runOnIdle { days = emptyList() }
        compose.onNodeWithText("No runs yet").assertIsDisplayed()
    }
}
