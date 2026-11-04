package com.bu.paperclip.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.bu.paperclip.ui.components.BrandMark
import com.bu.paperclip.ui.components.Dot
import com.bu.paperclip.ui.theme.LocalStatusColors
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.bu.paperclip.BuildConfig
import com.bu.paperclip.R
import com.bu.paperclip.data.AppSettings
import com.bu.paperclip.data.AppState
import com.bu.paperclip.net.Api
import com.bu.paperclip.net.LiveEvents
import com.bu.paperclip.notify.NotifyWorker
import com.bu.paperclip.ui.Nav
import com.bu.paperclip.ui.components.Avatar
import com.bu.paperclip.ui.components.SectionHeader
import com.bu.paperclip.util.L10n
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: Nav, onSignedOut: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by AppState.settings.flow.collectAsState(AppSettings())
    val me by AppState.me.collectAsState()
    val company by AppState.company.collectAsState()
    val companies by AppState.companies.collectAsState()
    val connected by LiveEvents.connected.collectAsState()
    var confirmSignOut by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        scope.launch { AppState.settings.setNotifications(granted); NotifyWorker.schedule(ctx, granted) }
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }, navigationIcon = { BackIcon { nav.back() } }) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SectionHeader(stringResource(R.string.settings_section_account))
            SettingsGroup {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(me?.name ?: me?.email ?: "?", me?.image, 44.dp, isAgent = false)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(me?.name ?: stringResource(R.string.settings_signed_in), style = MaterialTheme.typography.titleMedium)
                        Text(me?.email ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                SettingsDivider()
                SettingsRow(
                    Icons.Outlined.Dns,
                    Api.baseUrl.removePrefix("https://").removePrefix("http://"),
                    stringResource(if (Api.bearer != null) R.string.settings_auth_api_key else R.string.settings_auth_browser_session),
                ) {
                    Dot(if (connected) LocalStatusColors.current.green else LocalStatusColors.current.yellow, 8.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(if (connected) R.string.settings_connection_live else if (Api.bearer != null) R.string.settings_connection_polling else R.string.settings_connection_reconnecting), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (companies.size > 1) {
                SectionHeader(stringResource(R.string.settings_section_company))
                SettingsGroup {
                    companies.forEachIndexed { i, c ->
                        if (i > 0) SettingsDivider()
                        SettingsRow(Icons.Outlined.Business, c.name, c.issuePrefix, onClick = { AppState.selectCompany(c) }) {
                            if (c.id == company?.id) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            SectionHeader(stringResource(R.string.settings_section_appearance))
            SettingsGroup {
                Row(Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        Triple("system", stringResource(R.string.settings_theme_system), Icons.Outlined.PhoneAndroid),
                        Triple("dark", stringResource(R.string.settings_theme_dark), Icons.Outlined.DarkMode),
                        Triple("light", stringResource(R.string.settings_theme_light), Icons.Outlined.LightMode),
                    ).forEach { (k, label, icon) ->
                        val sel = settings.theme == k
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(MaterialTheme.shapes.small)
                                .background(if (sel) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
                                .clickable { scope.launch { AppState.settings.setTheme(k) } }
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            val tint = if (sel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.height(6.dp))
                            Text(label, style = MaterialTheme.typography.labelLarge, color = tint, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium)
                        }
                    }
                }
            }

            SectionHeader(stringResource(R.string.settings_section_language))
            SettingsGroup {
                // Changing it recreates the activity, so reading it once per composition is enough.
                val current = remember { L10n.appLanguageTag() }
                val options = remember { listOf("") + L10n.shippedLanguageTags(ctx) }
                options.forEachIndexed { i, tag ->
                    if (i > 0) SettingsDivider()
                    val sel = if (tag.isEmpty()) current.isEmpty() else current.isNotEmpty() && Locale.forLanguageTag(current) == Locale.forLanguageTag(tag)
                    SettingsRow(
                        if (tag.isEmpty()) Icons.Outlined.PhoneAndroid else Icons.Outlined.Translate,
                        if (tag.isEmpty()) stringResource(R.string.settings_language_system) else L10n.nativeName(tag),
                        null,
                        onClick = { if (!sel) L10n.setAppLanguage(tag) },
                    ) {
                        if (sel) Icon(Icons.Default.Check, stringResource(R.string.settings_selected), tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                    }
                }
            }

            SectionHeader(stringResource(R.string.settings_section_notifications))
            SettingsGroup {
                SettingsRow(
                    Icons.Outlined.NotificationsActive,
                    stringResource(R.string.settings_notifications_title),
                    stringResource(R.string.settings_notifications_summary),
                ) {
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = settings.notifications,
                        onCheckedChange = { on ->
                            if (on && Build.VERSION.SDK_INT >= 33 &&
                                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) {
                                permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                scope.launch { AppState.settings.setNotifications(on); NotifyWorker.schedule(ctx, on) }
                            }
                        },
                    )
                }
                SettingsDivider()
                SettingsRow(Icons.Outlined.Refresh, stringResource(R.string.settings_check_now), null, onClick = { NotifyWorker.runNow(ctx) })
            }

            Spacer(Modifier.height(20.dp))
            SettingsGroup {
                Row(
                    Modifier.fillMaxWidth().clickable { confirmSignOut = true }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Logout, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(stringResource(R.string.settings_sign_out), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                }
            }
            Column(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                BrandMark(28.dp)
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.settings_sign_out_confirm_title)) },
            text = { Text(stringResource(R.string.settings_sign_out_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    scope.launch { LiveEvents.stop(); Api.signOut(); AppState.settings.setApiToken(""); Api.configure(Api.baseUrl, null); onSignedOut() }
                }) { Text(stringResource(R.string.settings_sign_out)) }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) { Column { content() } }
}

@Composable
private fun SettingsDivider() =
    HorizontalDivider(Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outlineVariant)

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}
