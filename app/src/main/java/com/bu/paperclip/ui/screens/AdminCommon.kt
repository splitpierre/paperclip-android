package com.bu.paperclip.ui.screens

import com.bu.paperclip.util.L10n
import com.bu.paperclip.R
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.bu.paperclip.ui.theme.LocalStatusColors

/** Colour for the many "status" enums on projects, goals, routines, approvals and budgets. */
@Composable
internal fun adminStatusColor(status: String?): Color {
    val c = LocalStatusColors.current
    return when (status) {
        "active", "in_progress", "running", "planned" -> c.blue
        "achieved", "completed", "approved", "ok", "succeeded", "issue_created", "done" -> c.green
        "pending", "revision_requested", "warning", "paused", "backlog", "queued", "received", "coalesced" -> c.yellow
        "rejected", "failed", "hard_stop", "cancelled", "error" -> c.red
        "archived", "skipped" -> c.gray
        else -> c.gray
    }
}

internal fun adminParseColor(hex: String?, fallback: Color): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)

internal fun adminCompact(n: Long): String = com.bu.paperclip.util.formatCompact(n)

/** Label for a server key (approval payload fields); known keys are translated, others humanized. */
internal fun adminHumanize(key: String): String = adminKeyLabelRes(key)?.let { L10n.str(it) }
    ?: key.replace(Regex("([a-z0-9])([A-Z])"), "$1 $2").replace('_', ' ').replaceFirstChar { it.uppercase() }

@StringRes
private fun adminKeyLabelRes(key: String): Int? = when (key) {
    "name" -> R.string.admin_key_name
    "title" -> R.string.admin_key_title
    "role" -> R.string.admin_key_role
    "reason" -> R.string.admin_key_reason
    "description" -> R.string.admin_key_description
    "summary" -> R.string.admin_key_summary
    "capabilities" -> R.string.admin_key_capabilities
    "adapterType" -> R.string.admin_key_adapter_type
    "adapterConfig" -> R.string.admin_key_adapter_config
    "model" -> R.string.admin_key_model
    "reportsTo" -> R.string.admin_key_reports_to
    "budgetMonthlyCents" -> R.string.admin_key_budget_monthly_cents
    "agentId" -> R.string.admin_key_agent_id
    "requestedByAgentId" -> R.string.admin_key_requested_by_agent_id
    else -> null
}

/** A labelled progress bar; [fraction] is clamped to 0..1, colour shifts as usage rises. */
@Composable
internal fun AdminUsageBar(fraction: Float, label: String, trailing: String, modifier: Modifier = Modifier) {
    val c = LocalStatusColors.current
    val f = fraction.coerceIn(0f, 1f)
    val color = when {
        fraction >= 0.95f -> c.red
        fraction >= 0.75f -> c.orange
        else -> MaterialTheme.colorScheme.primary
    }
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { f },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

/** Full-width tappable row with leading content, title/subtitle and optional trailing content. */
@Composable
internal fun AdminListRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(14.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) { Spacer(Modifier.width(8.dp)); trailing() }
    }
}
