package com.bu.paperclip

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.bu.paperclip.data.ReadCache
import com.bu.paperclip.data.AppState
import com.bu.paperclip.util.SupersededResponse
import com.bu.paperclip.ui.components.LoadContent
import com.bu.paperclip.ui.components.Loader
import com.bu.paperclip.ui.components.rememberLoader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class LoaderUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun savedContentIsVisibleWhileNetworkIsStillWaiting() {
        compose.setContent {
            MaterialTheme {
                val loader = rememberLoader("cached-screen") {
                    if (ReadCache.isCacheOnly()) "Saved task" else awaitCancellation()
                }
                LoadContent(loader) { Text(it) }
            }
        }
        compose.onNodeWithText("Saved task").assertIsDisplayed()
        compose.onNodeWithText("Showing saved data").assertIsDisplayed()
    }

    @Test fun repeatedRefreshDoesNotCancelVisibleScreensInitialLoad() {
        val response = CompletableDeferred<Unit>()
        val starts = AtomicInteger()
        lateinit var loader: Loader<String>
        compose.setContent {
            MaterialTheme {
                loader = rememberLoader("slow-screen") {
                    if (ReadCache.isCacheOnly()) throw ReadCache.Miss()
                    starts.incrementAndGet()
                    response.await()
                    "Loaded task"
                }
                LoadContent(loader) { Text(it) }
            }
        }
        compose.runOnIdle {
            assertEquals(1, starts.get())
            repeat(100) { loader.refresh() }
        }
        compose.runOnIdle { assertEquals(1, starts.get()); response.complete(Unit) }
        compose.waitForIdle()
        compose.onNodeWithText("Loaded task").assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, starts.get()) }
    }
    @Test fun supersededResponseKeepsSavedContentAndFutureRefreshesAlive() {
        AppState.clearToast()
        val starts = AtomicInteger()
        lateinit var loader: Loader<String>
        compose.setContent {
            MaterialTheme {
                loader = rememberLoader("session-change") {
                    if (ReadCache.isCacheOnly()) "Saved task"
                    else if (starts.incrementAndGet() == 1) throw SupersededResponse()
                    else "Current session ${starts.get()}"
                }
                LoadContent(loader) { Text(it) }
            }
        }
        compose.onNodeWithText("Saved task").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, starts.get())
            assertEquals(null, AppState.snack.value)
            loader.refresh()
        }
        compose.onNodeWithText("Current session 2").assertIsDisplayed()
        compose.runOnIdle { loader.refresh() }
        compose.onNodeWithText("Current session 3").assertIsDisplayed()
    }

}
