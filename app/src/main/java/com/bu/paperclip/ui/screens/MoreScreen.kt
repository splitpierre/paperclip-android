package com.bu.paperclip.ui.screens

import com.bu.paperclip.R
import androidx.compose.ui.res.stringResource
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bu.paperclip.data.AppState
import com.bu.paperclip.net.Api
import com.bu.paperclip.ui.Nav
import com.bu.paperclip.ui.components.Avatar
import com.bu.paperclip.ui.components.Card
import com.bu.paperclip.ui.components.Divider
import com.bu.paperclip.ui.components.SectionHeader
import com.bu.paperclip.util.formatCents

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(nav: Nav) {
    val ctx = LocalContext.current
    val company by AppState.company.collectAsState()
    val me by AppState.me.collectAsState()
    val badges by AppState.badges.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.more_title), style = MaterialTheme.typography.headlineSmall) }) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
            Card(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(company?.name ?: "?", company?.logoUrl, 44.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(company?.name ?: stringResource(R.string.more_company), style = MaterialTheme.typography.titleMedium)
                        val sub = listOfNotNull(
                            company?.issuePrefix?.ifEmpty { null },
                            company?.budgetMonthlyCents?.takeIf { it > 0 }?.let { stringResource(R.string.more_budget, formatCents(company?.spentMonthlyCents), formatCents(it)) },
                        ).joinToString(" · ")
                        if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val who = me?.name?.ifBlank { null } ?: me?.email
                        if (who != null) Text(stringResource(R.string.more_signed_in_as, who), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            SectionHeader(stringResource(R.string.more_section_work))
            MoreRow(Icons.Outlined.Inbox, stringResource(R.string.more_inbox), badges.inbox) { nav.inbox() }
            MoreRow(Icons.Outlined.Gavel, stringResource(R.string.more_approvals), badges.approvals) { nav.approvals() }
            MoreRow(Icons.Outlined.FolderOpen, stringResource(R.string.more_projects)) { nav.projects() }
            MoreRow(Icons.Outlined.Flag, stringResource(R.string.more_goals)) { nav.goals() }
            MoreRow(Icons.Outlined.Repeat, stringResource(R.string.more_routines)) { nav.routines() }

            SectionHeader(stringResource(R.string.more_section_insight))
            MoreRow(Icons.Outlined.AttachMoney, stringResource(R.string.more_costs)) { nav.costs() }
            MoreRow(Icons.Outlined.History, stringResource(R.string.more_activity)) { nav.activity() }
            MoreRow(Icons.Outlined.Search, stringResource(R.string.common_search)) { nav.search() }

            SectionHeader(stringResource(R.string.more_section_app))
            MoreRow(Icons.Outlined.Settings, stringResource(R.string.more_settings)) { nav.settings() }
            MoreRow(Icons.AutoMirrored.Outlined.OpenInNew, stringResource(R.string.more_open_web_ui)) {
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Api.baseUrl + "/"))) }
            }
            Divider()
            Spacer(Modifier.size(24.dp))
        }
    }
}

@Composable
private fun MoreRow(icon: ImageVector, label: String, badge: Int = 0, onClick: () -> Unit) {
    AdminListRow(
        title = label,
        onClick = onClick,
        leading = { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailing = if (badge > 0) ({ Badge { Text(if (badge > 99) "99+" else "$badge") } }) else null,
    )
}
