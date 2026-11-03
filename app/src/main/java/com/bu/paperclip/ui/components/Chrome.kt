package com.bu.paperclip.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bu.paperclip.ui.theme.LocalStatusColors
import androidx.compose.ui.res.stringResource
import com.bu.paperclip.R
import com.bu.paperclip.util.appString

/**
 * The app's filter/choice chip: a pill that inverts when selected. A selected chip swaps its leading
 * icon for a check unless [keepIcon] is set (avatars and label swatches stay readable on either fill).
 */
@Composable
fun AppFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    keepIcon: Boolean = false,
    enabled: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) cs.onSurface else cs.surfaceContainerHigh, label = "chipBg")
    val fg by animateColorAsState(if (selected) cs.surface else cs.onSurface, label = "chipFg")
    Row(
        modifier
            .heightIn(min = 32.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(enabled = enabled, role = Role.Checkbox, onClick = onClick)
            .padding(start = if (leadingIcon != null || selected) 10.dp else 14.dp, end = 14.dp)
            .alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) {
            when {
                selected && !keepIcon -> { Icon(Icons.Default.Check, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
                leadingIcon != null -> { leadingIcon(); Spacer(Modifier.width(7.dp)) }
            }
            androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)) {
                label()
            }
        }
    }
}

data class BarItem(val key: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector, val badge: Int = 0)

/** Bottom navigation: compact, hairline on top, a soft pill behind the selected icon. */
@Composable
fun AppBottomBar(items: List<BarItem>, selected: String?, onSelect: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().background(cs.surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(cs.outlineVariant))
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).height(64.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val isSel = item.key == selected
                val tint by animateColorAsState(if (isSel) cs.onSurface else cs.onSurfaceVariant, label = "tabTint")
                val pill by animateColorAsState(if (isSel) cs.secondaryContainer else Color.Transparent, label = "tabPill")
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false, radius = 36.dp),
                            role = Role.Tab,
                        ) { onSelect(item.key) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box {
                        Box(Modifier.clip(CircleShape).background(pill).padding(horizontal = 18.dp, vertical = 4.dp)) {
                            Icon(if (isSel) item.selectedIcon else item.icon, item.label, tint = tint, modifier = Modifier.size(22.dp))
                        }
                        if (item.badge > 0) {
                            Text(
                                if (item.badge > 99) "99+" else "${item.badge}",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 10.sp,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-4).dp, y = (-6).dp)
                                    .clip(CircleShape)
                                    .background(cs.surface)
                                    .padding(2.dp)
                                    .clip(CircleShape)
                                    .background(LocalStatusColors.current.red)
                                    .widthIn(min = 16.dp)
                                    .padding(horizontal = 4.dp, vertical = 3.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        item.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Medium,
                        color = tint,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Placeholder rows shown while a list loads, shaped like the content that's coming. */
@Composable
fun SkeletonList(modifier: Modifier = Modifier, rows: Int = 8) {
    val t = rememberInfiniteTransition(label = "skeleton")
    val a by t.animateFloat(0.45f, 0.9f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "skeletonAlpha")
    val c = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(modifier.fillMaxSize().padding(top = 8.dp).alpha(a)) {
        repeat(rows) { i ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(c))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Box(Modifier.width(72.dp).height(10.dp).clip(CircleShape).background(c))
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth(if (i % 3 == 0) 0.85f else if (i % 3 == 1) 0.65f else 0.75f).height(13.dp).clip(CircleShape).background(c))
                }
            }
        }
    }
}

/** A title and an optional one-line subtitle, for top app bars. */
@Composable
fun BarTitle(title: String, subtitle: String? = null, large: Boolean = false) {
    Column {
        Text(
            title,
            style = if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The Paperclip mark: a paperclip glyph on a rounded tile. */
@Composable
fun BrandMark(size: androidx.compose.ui.unit.Dp = 56.dp) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(cs.onSurface),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            androidx.compose.ui.res.painterResource(com.bu.paperclip.R.drawable.ic_paperclip),
            stringResource(R.string.paperclip),
            tint = cs.surface,
            modifier = Modifier.size(size * 0.56f),
        )
    }
}

/** The small "See all ›" link at the end of a section header. */
@Composable
fun SectionAction(text: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(CircleShape).clickable(onClick = onClick).padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

/** A tappable property (status, priority, assignee, project): a soft pill with an icon. */
@Composable
fun PropertyChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier
            .heightIn(min = 34.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = if (leadingIcon != null) 10.dp else 14.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) { leadingIcon() }
            Spacer(Modifier.width(7.dp))
        }
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.labelLarge) { label() }
        }
    }
}
