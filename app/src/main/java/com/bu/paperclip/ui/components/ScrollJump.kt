package com.bu.paperclip.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowDown
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.bu.paperclip.R
import com.bu.paperclip.util.appString

/** Which end of a long list the jump button currently targets. */
enum class JumpTarget { Bottom, Top }

/**
 * Follows the reading direction: scrolling down offers a jump to the bottom (latest), scrolling up
 * flips it to a jump to the top. Small jitters within one row don't flip it.
 */
@Composable
fun rememberJumpTarget(state: LazyListState, initial: JumpTarget = JumpTarget.Bottom): JumpTarget {
    var target by remember(state) { mutableStateOf(initial) }
    LaunchedEffect(state) {
        var lastIndex = state.firstVisibleItemIndex
        var lastOffset = state.firstVisibleItemScrollOffset
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }.collect { (index, offset) ->
            val delta = if (index != lastIndex) (index - lastIndex) * 10_000 else offset - lastOffset
            // Only a deliberate scroll (not layout changes while idle) flips the direction.
            if (state.isScrollInProgress && kotlin.math.abs(delta) > 24) {
                target = if (delta > 0) JumpTarget.Bottom else JumpTarget.Top
            }
            lastIndex = index; lastOffset = offset
        }
    }
    return target
}

/** Scrolls far jumps instantly to near the target first, so long threads don't animate through every row. */
suspend fun LazyListState.jumpTo(target: JumpTarget) {
    val total = layoutInfo.totalItemsCount
    if (total == 0) return
    val index = if (target == JumpTarget.Bottom) total - 1 else 0
    if (kotlin.math.abs(index - firstVisibleItemIndex) > 20) scrollToItem(if (target == JumpTarget.Bottom) index - 3 else 3)
    animateScrollToItem(index)
}

/**
 * The jump button for thread-like screens. Shown when [enabled] and the list can still move toward
 * the current target. [label] makes it an extended button with text.
 */
@Composable
fun ScrollJumpButton(
    state: LazyListState,
    enabled: Boolean = true,
    label: ((JumpTarget) -> String)? = null,
    onJump: (JumpTarget) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val target = rememberJumpTarget(state)
    val canMove = if (target == JumpTarget.Bottom) state.canScrollForward else state.canScrollBackward
    val rotation by animateFloatAsState(if (target == JumpTarget.Top) 180f else 0f, label = "jump-rotation")
    val description = if (target == JumpTarget.Top) stringResource(R.string.jump_to_top) else stringResource(R.string.jump_to_latest)
    val click = {
        onJump(target)
        scope.launch { state.jumpTo(target) }
        Unit
    }
    AnimatedVisibility(enabled && canMove, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
        if (label != null) {
            ExtendedFloatingActionButton(
                onClick = click,
                icon = { Icon(Icons.Outlined.KeyboardDoubleArrowDown, null, Modifier.rotate(rotation)) },
                text = { Text(label(target)) },
            )
        } else {
            SmallFloatingActionButton(onClick = click) {
                Icon(Icons.Outlined.KeyboardDoubleArrowDown, description, Modifier.rotate(rotation))
            }
        }
    }
}
