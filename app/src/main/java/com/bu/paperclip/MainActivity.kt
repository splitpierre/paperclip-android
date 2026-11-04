package com.bu.paperclip

import android.content.Intent
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.bu.paperclip.data.AppSettings
import com.bu.paperclip.data.AppState
import com.bu.paperclip.ui.AppRoot
import com.bu.paperclip.ui.theme.PaperclipTheme

/** AppCompat so the in-app language (AppCompatDelegate.setApplicationLocales) also applies below Android 13. */
class MainActivity : AppCompatActivity() {
    private val pendingLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingLink.value = intent?.getStringExtra(EXTRA_LINK)
        if (BuildConfig.DEBUG) {
            val url = intent?.getStringExtra("setupUrl")
            val key = intent?.getStringExtra("setupKey")
            if (url != null) kotlinx.coroutines.runBlocking {
                AppState.settings.setBaseUrl(url)
                AppState.settings.setApiToken(key ?: "")
            }
        }
        setContent {
            val settings by AppState.settings.flow.collectAsState(AppSettings(theme = "system"))
            val dark = when (settings.theme) { "dark" -> true; "light" -> false; else -> null }
            val resolvedDark = dark ?: isSystemInDarkTheme()
            // Status and navigation bar icons follow the app's theme, not just the system's.
            LaunchedEffect(resolvedDark) {
                val style = if (resolvedDark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            PaperclipTheme(darkOverride = dark) {
                // A Surface (not a plain Box) so screens without a Scaffold get the theme's text colour too.
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot(pendingLink.value) { pendingLink.value = null }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_LINK)?.let { pendingLink.value = it }
    }

    companion object {
        const val EXTRA_LINK = "link"
    }
}
