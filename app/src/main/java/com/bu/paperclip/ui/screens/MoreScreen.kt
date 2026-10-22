package com.bu.paperclip.ui.screens

import com.bu.paperclip.util.catching

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

    Scaffold(topBar = { TopAppBar(title = { Text("More", style = MaterialTheme.typography.headlineSmall) }) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
            Card(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(company?.name ?: "?", company?.logoUrl, 44.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(company?.name ?: "Company", style = MaterialTheme.typography.titleMedium)
                        val sub = listOfNotNull(
                            company?.issuePrefix?.ifEmpty { null },
                            company?.budgetMonthlyCents?.takeIf { it > 0 }?.let { "${formatCents(company?.spentMonthlyCents)} of ${formatCents(it)} this month" },
                        ).joinToString(" · ")
                        if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val who = me?.name?.ifBlank { null } ?: me?.email
                        if (who != null) Text("Signed in as $who", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            SectionHeader("Work")
            MoreRow(Icons.Outlined.Inbox, "Inbox", badges.inbox) { nav.inbox() }
            MoreRow(Icons.Outlined.Gavel, "Approvals", badges.approvals) { nav.approvals() }
            MoreRow(Icons.Outlined.FolderOpen, "Projects") { nav.projects() }
            MoreRow(Icons.Outlined.Flag, "Goals") { nav.goals() }
            MoreRow(Icons.Outlined.Repeat, "Routines") { nav.routines() }

            SectionHeader("Insight")
            MoreRow(Icons.Outlined.AttachMoney, "Costs & quotas") { nav.costs() }
            MoreRow(Icons.Outlined.History, "Activity") { nav.activity() }
            MoreRow(Icons.Outlined.Search, "Search") { nav.search() }

            SectionHeader("App")
            MoreRow(Icons.Outlined.Settings, "Settings") { nav.settings() }
            MoreRow(Icons.AutoMirrored.Outlined.OpenInNew, "Open web UI") {
                catching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Api.baseUrl + "/"))) }
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
