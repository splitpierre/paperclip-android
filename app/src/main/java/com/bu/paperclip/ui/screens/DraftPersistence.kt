package com.bu.paperclip.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bu.paperclip.data.AppState
import com.bu.paperclip.data.Drafts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer

/** How long typing must pause before the draft is written; a process kill loses at most this much. */
internal const val DRAFT_SAVE_DELAY_MS = 400L

/**
 * Keeps the draft under [key] in step with [value] while [enabled] is true: written shortly after
 * every change, and again when the screen is left or the app goes to the background. An empty
 * draft ([isEmpty]) removes the saved one. [enabled] and [value] are read as lambdas so that a
 * "sent" flag set just before leaving the screen is seen by the final flush.
 */
@Composable
internal fun <T> PersistDraft(
    key: String,
    serializer: KSerializer<T>,
    isEmpty: (T) -> Boolean,
    enabled: () -> Boolean,
    value: () -> T,
) {
    val en by rememberUpdatedState(enabled)
    val current by rememberUpdatedState(value)
    fun write(v: T) = if (isEmpty(v)) Drafts.clear(key) else Drafts.save(key, v, serializer)
    fun flush() {
        if (!en()) return
        val v = current()
        AppState.scope.launch(Dispatchers.IO) { runCatching { write(v) } }
    }

    LaunchedEffect(key) {
        snapshotFlow { if (en()) current() else null }.collectLatest { v ->
            if (v == null) return@collectLatest
            delay(DRAFT_SAVE_DELAY_MS)
            withContext(Dispatchers.IO) { runCatching { write(v) } }
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(key, lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) flush() }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            flush()
        }
    }
}

/** Reads a saved draft off the main thread. */
internal suspend fun <T> loadDraft(key: String, serializer: KSerializer<T>): T? =
    withContext(Dispatchers.IO) { runCatching { Drafts.load(key, serializer) }.getOrNull() }

/** Removes a saved draft off the main thread. */
internal suspend fun clearDraft(key: String) =
    withContext(Dispatchers.IO) { runCatching { Drafts.clear(key) } }
