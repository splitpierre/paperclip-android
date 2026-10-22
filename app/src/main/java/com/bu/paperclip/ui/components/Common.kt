package com.bu.paperclip.ui.components

import com.bu.paperclip.util.SupersededResponse
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.bu.paperclip.data.AppState
import com.bu.paperclip.net.Api
import com.bu.paperclip.net.ApiException
import com.bu.paperclip.net.LiveEvent
import com.bu.paperclip.net.LiveEvents
import com.bu.paperclip.ui.theme.LocalStatusColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.coroutineScope
import com.bu.paperclip.data.RefreshQueue
import com.bu.paperclip.data.ReadCache
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

// ---------- Loading ----------

sealed class Load<out T> {
    data object Loading : Load<Nothing>()
    data class Ok<T>(val value: T) : Load<T>()
    data class Err(val message: String) : Load<Nothing>()
}

class Loader<T>(
    val state: MutableState<Load<T>>,
    val refreshing: MutableState<Boolean>,
    private val queue: RefreshQueue,
    val fromCache: MutableState<Boolean>,
) {
    val value: T? get() = (state.value as? Load.Ok<T>)?.value
    /** Reload; keeps showing current data while it runs. */
    fun refresh() { queue.request() }
    fun set(v: T) { state.value = Load.Ok(v); fromCache.value = false }
}

fun errorMessage(t: Throwable): String = when (t) {
    is ApiException.AuthRequired -> t.message ?: "Sign-in required"
    is ApiException.Http -> t.message ?: "Request failed"
    is ApiException.Network -> "Can't reach the server: ${t.message}"
    else -> t.message ?: t.javaClass.simpleName
}

/** Loads `key`-scoped data and re-runs on refresh(); background reloads don't flash a spinner. */
@Composable
fun <T> rememberLoader(vararg keys: Any?, block: suspend () -> T): Loader<T> {
    val state = remember(*keys) { mutableStateOf<Load<T>>(Load.Loading) }
    val refreshing = remember(*keys) { mutableStateOf(false) }
    val queue = remember(*keys) { RefreshQueue() }
    val fromCache = remember(*keys) { mutableStateOf(false) }
    val latest by rememberUpdatedState(block)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(*keys, lifecycle) {
        if (state.value !is Load.Ok) {
            try { state.value = Load.Ok(ReadCache.only { latest() }); fromCache.value = true }
            catch (c: CancellationException) { throw c }
            catch (_: Exception) { /* A partial/missing cache falls through to network. */ }
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            queue.run {
                try {
                    state.value = Load.Ok(latest())
                    fromCache.value = false
                } catch (_: SupersededResponse) {
                    // Discard the old response without stopping future refreshes.
                } catch (c: CancellationException) {
                    throw c
                } catch (t: Throwable) {
                    if (state.value !is Load.Ok || t is ApiException.AuthRequired) state.value = Load.Err(errorMessage(t))
                    else AppState.toast(errorMessage(t))
                } finally {
                    refreshing.value = false
                }
            }
        }
    }
    return remember(*keys) { Loader(state, refreshing, queue, fromCache) }
}

/** One lifecycle-scoped fallback timer; event bursts coalesce without starving refresh. */
@Composable
fun LiveRefresh(debounceMs: Long = 600, filter: (LiveEvent) -> Boolean, onEvent: () -> Unit) {
    val cb by rememberUpdatedState(onEvent)
    val f by rememberUpdatedState(filter)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch {
                LiveEvents.events.filter { f(it) }.conflate().collect {
                    delay(debounceMs)
                    cb()
                }
            }
            launch {
                var first = true
                LiveEvents.epoch.collect { if (!first) cb(); first = false }
            }
            launch {
                while (true) {
                    delay(20_000)
                    if (!LiveEvents.connected.value) cb()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> LoadContent(
    loader: Loader<T>,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    when (val s = loader.state.value) {
        is Load.Loading -> SkeletonList(modifier)
        is Load.Err -> ErrorState(s.message, modifier) { loader.refresh() }
        is Load.Ok -> PullToRefreshBox(
            isRefreshing = loader.refreshing.value,
            onRefresh = { loader.refreshing.value = true; loader.refresh() },
            modifier = modifier.fillMaxSize(),
        ) {
            Column(Modifier.fillMaxSize()) {
                if (loader.fromCache.value) Text(
                    "Showing saved data",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Box(Modifier.weight(1f)) { content(s.value) }
            }
        }
    }
}

@Composable
fun ErrorState(message: String, modifier: Modifier = Modifier, onRetry: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StateIcon(Icons.Outlined.CloudOff)
        Spacer(Modifier.height(16.dp))
        Text("Something went wrong", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRetry) { Text("Try again") }
    }
}

@Composable
fun EmptyState(text: String, icon: ImageVector = Icons.Outlined.Inbox, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 56.dp, horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StateIcon(icon)
        Spacer(Modifier.height(14.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun StateIcon(icon: ImageVector) {
    Box(
        Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(26.dp))
    }
}

/** Runs a mutation, toasts failures, returns success. */
@Composable
fun rememberAction(): (suspend () -> Unit, String?) -> Unit {
    val scope = rememberCoroutineScope()
    return { block, okMessage ->
        scope.launch {
            try {
                block()
                if (okMessage != null) AppState.toast(okMessage)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                AppState.toast(errorMessage(t))
            }
        }
    }
}

// ---------- Visual atoms ----------

@Composable
fun issueStatusColor(status: String): Color {
    val c = LocalStatusColors.current
    return when (status) {
        "backlog" -> c.gray
        "todo" -> MaterialTheme.colorScheme.onSurfaceVariant
        "in_progress" -> c.yellow
        "in_review" -> c.violet
        "done" -> c.green
        "blocked" -> c.red
        "cancelled" -> c.gray
        else -> c.gray
    }
}

fun statusLabel(status: String): String = when (status) {
    "in_progress" -> "In progress"
    "in_review" -> "In review"
    "todo" -> "Todo"
    "pending_approval" -> "Pending approval"
    "scheduled_retry" -> "Retry scheduled"
    "timed_out" -> "Timed out"
    "revision_requested" -> "Revision requested"
    else -> status.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

val ISSUE_STATUSES = listOf("backlog", "todo", "in_progress", "in_review", "blocked", "done", "cancelled")
val PRIORITIES = listOf("critical", "high", "medium", "low")

/** Paperclip-style status circle: hollow for open, half for in-progress, filled+check-ish for done. */
@Composable
fun StatusIcon(status: String, size: Dp = 14.dp) {
    val color = issueStatusColor(status)
    Box(
        Modifier.size(size).clip(CircleShape).border(1.6.dp, color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        when (status) {
            "in_progress" -> Box(Modifier.size(size * 0.5f).clip(CircleShape).background(color))
            "in_review" -> Box(Modifier.size(size * 0.62f).clip(CircleShape).background(color.copy(alpha = 0.75f)))
            "done" -> Box(Modifier.size(size).clip(CircleShape).background(color))
            "blocked" -> Box(Modifier.width(size * 0.55f).height(1.8.dp).background(color))
            "cancelled" -> Box(Modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.4f)))
            else -> {}
        }
    }
}

@Composable
fun StatusChip(status: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusIcon(status, 11.dp)
        Spacer(Modifier.width(5.dp))
        Text(statusLabel(status), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun priorityColor(p: String): Color {
    val c = LocalStatusColors.current
    return when (p) {
        "critical" -> c.red
        "high" -> c.orange
        "medium" -> c.yellow
        else -> c.gray
    }
}

fun priorityIcon(p: String): ImageVector = when (p) {
    "critical" -> Icons.Filled.KeyboardDoubleArrowUp
    "high" -> Icons.Filled.KeyboardArrowUp
    "medium" -> Icons.Filled.Remove
    else -> Icons.Filled.KeyboardArrowDown
}

@Composable
fun PriorityIcon(p: String, size: Dp = 16.dp) {
    Icon(priorityIcon(p), contentDescription = p, tint = priorityColor(p), modifier = Modifier.size(size))
}

@Composable
fun runStatusColor(status: String): Color {
    val c = LocalStatusColors.current
    return when (status) {
        "running" -> c.blue
        "queued", "scheduled_retry" -> c.yellow
        "succeeded" -> c.green
        "failed", "timed_out" -> c.red
        "cancelled", "interrupted" -> c.gray
        else -> c.gray
    }
}

@Composable
fun agentStatusColor(status: String): Color {
    val c = LocalStatusColors.current
    return when (status) {
        "running" -> c.blue
        "active", "idle" -> c.green
        "paused" -> c.yellow
        "error" -> c.red
        "pending_approval" -> c.violet
        else -> c.gray
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, filled: Boolean = false) {
    Text(
        text,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (filled) color else color.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        color = if (filled) MaterialTheme.colorScheme.surface else color,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        maxLines = 1,
    )
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}

private val avatarPalette = listOf(
    Color(0xFF6366F1), Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFFEF4444), Color(0xFFF97316),
    Color(0xFFEAB308), Color(0xFF22C55E), Color(0xFF14B8A6), Color(0xFF06B6D4), Color(0xFF3B82F6),
)

@Composable
fun Avatar(name: String, imageUrl: String? = null, size: Dp = 28.dp, isAgent: Boolean = true) {
    val color = avatarPalette[(name.hashCode() and 0x7fffffff) % avatarPalette.size]
    Box(
        Modifier.size(size).clip(if (isAgent) RoundedCornerShape(size / 3.5f) else CircleShape).background(color.copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center,
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(model = Api.absolute(imageUrl), contentDescription = name, modifier = Modifier.fillMaxSize())
        } else {
            val initials = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
            Text(initials, color = color, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.38f).sp)
        }
    }
}

@Composable
fun AgentAvatar(agentId: String?, size: Dp = 28.dp) {
    val a = AppState.agent(agentId)
    Box {
        Avatar(a?.name ?: "Agent", a?.avatarUrl, size)
        if (a != null && a.status == "running") {
            Dot(LocalStatusColors.current.blue, size / 3.2f, Modifier.align(Alignment.BottomEnd).border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape))
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 40.dp).padding(start = 16.dp, end = 8.dp, top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.07.em),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(16.dp), content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) { Box(Modifier.padding(padding)) { content() } }
}

@Composable
fun Divider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

@Composable
fun KeyValue(key: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(key, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(120.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

/** Simple tick that forces relative timestamps to refresh. */
@Composable
fun rememberMinuteTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); tick++ } }
    return tick
}
