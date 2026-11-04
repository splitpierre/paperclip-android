package com.bu.paperclip.ui.screens

import com.bu.paperclip.util.L10n
import com.bu.paperclip.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ripple
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalContext
import com.bu.paperclip.data.AppState
import com.bu.paperclip.util.SavedLogin
import com.bu.paperclip.util.SavedLoginResult
import com.bu.paperclip.ui.AppTabs
import com.bu.paperclip.ui.LocalNav
import com.bu.paperclip.ui.Routes
import androidx.navigation.compose.currentBackStackEntryAsState
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import com.bu.paperclip.ui.components.BrandMark
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.bu.paperclip.net.Api
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Centred, branded column shared by the setup, sign-in and connecting screens. */
@Composable
private fun AuthLayout(title: String, subtitle: String?, content: @Composable ColumnScope.() -> Unit) {
    // Weighted spacers centre the content; the min height keeps them working inside the scroll.
    BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.weight(1f).heightIn(min = 48.dp))
            BrandMark(52.dp)
            Spacer(Modifier.height(28.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium)
            if (subtitle != null) {
                Spacer(Modifier.height(8.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(28.dp))
            content()
            Spacer(Modifier.weight(1.3f).heightIn(min = 32.dp))
            Text(
                stringResource(R.string.login_unofficial_client),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    keyboard: KeyboardOptions,
    placeholder: String? = null,
    supporting: String? = null,
    secret: Boolean = false,
    onGo: (() -> Unit)? = null,
    contentType: ContentType? = null,
) {
    var reveal by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = supporting?.let { { Text(it) } },
        leadingIcon = { Icon(icon, null, Modifier.size(20.dp)) },
        trailingIcon = if (secret) {
            { IconButton(onClick = { reveal = !reveal }) { Icon(if (reveal) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, stringResource(if (reveal) R.string.login_hide_secret else R.string.login_show_secret), Modifier.size(20.dp)) } }
        } else null,
        visualTransformation = if (secret && !reveal) PasswordVisualTransformation() else VisualTransformation.None,
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        keyboardOptions = keyboard,
        keyboardActions = KeyboardActions(onGo = { onGo?.invoke() }),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedLeadingIconColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = Modifier.fillMaxWidth().then(if (contentType != null) Modifier.semantics { this.contentType = contentType } else Modifier),
    )
}

@Composable
private fun AuthButton(text: String, enabled: Boolean = true, busy: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        else Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
    }
}

@Composable
private fun AuthLink(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SetupScreen(initial: String, initialToken: String = "", onSave: (url: String, token: String) -> Unit) {
    var url by remember { mutableStateOf(initial.ifEmpty { "https://" }) }
    var token by remember { mutableStateOf(initialToken) }
    var showToken by remember { mutableStateOf(initialToken.isNotEmpty()) }
    val valid = url.trim().toHttpUrlOrNull()?.host?.isNotBlank() == true
    val save = { if (valid) onSave(url.trim().trimEnd('/'), token.trim()) }
    AuthLayout(
        stringResource(R.string.setup_title),
        stringResource(R.string.setup_subtitle),
    ) {
        AuthField(
            url, { url = it }, stringResource(R.string.setup_server_url), Icons.Outlined.Language,
            KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            placeholder = stringResource(R.string.setup_server_url_placeholder),
            onGo = save,
        )
        Spacer(Modifier.height(12.dp))
        if (showToken) {
            AuthField(
                token, { token = it }, stringResource(R.string.setup_api_key), Icons.Outlined.Key,
                KeyboardOptions(imeAction = ImeAction.Go),
                placeholder = stringResource(R.string.setup_api_key_placeholder),
                supporting = stringResource(R.string.setup_api_key_hint),
                secret = true,
                onGo = save,
            )
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(8.dp))
        AuthButton(stringResource(R.string.setup_continue), enabled = valid, onClick = save)
        if (!showToken) {
            Spacer(Modifier.height(4.dp))
            AuthLink(stringResource(R.string.setup_use_api_key)) { showToken = true }
        }
    }
}

/**
 * Signs in through the real web UI so any front door (Cloudflare Access, SSO, email code) works.
 * Cookies land in the shared WebView store that the API client reads.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(baseUrl: String, onSignedIn: () -> Unit, onChangeServer: () -> Unit) {
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf(0) }
    var checking by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var probe by remember { mutableStateOf<Job?>(null) }
    val host = remember(baseUrl) { baseUrl.toHttpUrlOrNull()?.host ?: baseUrl }

    fun check() {
        probe?.cancel()
        probe = scope.launch {
            delay(400)
            checking = true
            val ok = Api.checkSession()
            checking = false
            if (ok) onSignedIn()
        }
    }

    BackHandler(enabled = webView?.canGoBack() == true) { webView?.goBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.sign_in_title), style = MaterialTheme.typography.titleMedium)
                        Text(host, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    if (checking) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    IconButton(onClick = { webView?.reload() }) { Icon(Icons.Default.Refresh, stringResource(R.string.login_reload)) }
                    TextButton(onClick = onChangeServer) { Text(stringResource(R.string.login_server)) }
                },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        // Some identity providers refuse embedded browsers; present as regular Chrome.
                        settings.userAgentString = settings.userAgentString.replace("; wv", "").replace("Version/4.0 ", "")
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) { progress = 10 }
                            override fun onPageFinished(view: WebView, url: String?) {
                                progress = 100
                                CookieManager.getInstance().flush()
                                if (url != null && url.startsWith(baseUrl)) check()
                            }
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false
                        }
                        loadUrl("$baseUrl/")
                        webView = this
                    }
                },
            )
            if (progress in 1..99) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
}

@Composable
fun SplashScreen(message: String = stringResource(R.string.login_connecting), onRetry: (() -> Unit)? = null, onSignIn: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandMark(64.dp)
        Spacer(Modifier.height(28.dp))
        if (onRetry == null) {
            LinearProgressIndicator(
                Modifier.width(120.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.onSurface,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Spacer(Modifier.height(16.dp))
        }
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (onRetry != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRetry, shape = MaterialTheme.shapes.small, modifier = Modifier.widthIn(min = 160.dp)) { Text(stringResource(R.string.common_try_again)) }
        }
        if (onSignIn != null) TextButton(onClick = onSignIn) { Text(stringResource(R.string.login_sign_in_again), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/**
 * Back arrow for pushed screens. Two or more screens deep, a second button returns straight to the
 * tab the trail started from; long-pressing the arrow does the same.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BackIcon(onClick: () -> Unit) {
    val nav = LocalNav.current
    val controller = nav?.controller
    // Recompose on every navigation; the back-stack list itself is library-internal API.
    controller?.currentBackStackEntryAsState()?.value
    // Two or more pushed screens: the one underneath isn't a tab either.
    val deep = controller != null && controller.previousBackStackEntry?.destination?.route.let { it != null && it !in Routes.TABS }
    // Switching tabs pops back to Home first, so at most one other tab sits under the pushed screens.
    val origin = if (!deep) null else AppTabs.firstOrNull { t ->
        t.route != Routes.DASHBOARD && runCatching { controller!!.getBackStackEntry(t.route) }.isSuccess
    } ?: AppTabs.first()
    val haptics = LocalHapticFeedback.current
    val originLabel = origin?.let { stringResource(it.label) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(48.dp).combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 20.dp),
                role = Role.Button,
                onClickLabel = stringResource(R.string.common_back),
                onLongClickLabel = originLabel?.let { stringResource(R.string.login_back_to_tab, it) },
                onLongClick = if (deep) ({ haptics.performHapticFeedback(HapticFeedbackType.LongPress); nav!!.backToTab(origin!!.route) }) else null,
                onClick = onClick,
            ),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
        if (deep) {
            IconButton(onClick = { nav!!.backToTab(origin!!.route) }, modifier = Modifier.offset(x = (-6).dp)) {
                Icon(origin!!.icon, stringResource(R.string.login_back_to_tab, originLabel!!), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private sealed class Gate {
    data object Checking : Gate()
    data object Open : Gate()
    data class Required(val host: String) : Gate()
    data class Error(val message: String) : Gate()
}

/**
 * Native sign-in. If an auth proxy (Cloudflare Access) sits in front of the API, its login runs in a
 * browser pointed at a JSON endpoint, so the web UI's JavaScript never needs to load. Paperclip's own
 * sign-in is a native form; the full web page remains as a fallback for SSO-only setups.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInScreen(baseUrl: String, onSignedIn: () -> Unit, onChangeServer: () -> Unit) {
    val scope = rememberCoroutineScope()
    var gate by remember { mutableStateOf<Gate>(Gate.Checking) }
    var gateBrowser by remember { mutableStateOf(false) }
    var webFallback by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var probeKey by remember { mutableStateOf(0) }
    val host = remember(baseUrl) { baseUrl.toHttpUrlOrNull()?.host ?: baseUrl }

    androidx.compose.runtime.LaunchedEffect(probeKey) {
        gate = Gate.Checking
        gate = try {
            val g = Api.gateHost()
            if (g == null) {
                if (Api.checkSession()) { onSignedIn(); return@LaunchedEffect }
                Gate.Open
            } else Gate.Required(g)
        } catch (t: Throwable) {
            Gate.Error(com.bu.paperclip.ui.components.errorMessage(t))
        }
    }

    if (webFallback) {
        LoginScreen(baseUrl, onSignedIn, onChangeServer = { webFallback = false })
        BackHandler { webFallback = false }
        return
    }
    if (gateBrowser) {
        GateBrowser(baseUrl, onPassed = { gateBrowser = false; probeKey++ }, onClose = { gateBrowser = false })
        return
    }

    // Pre-fill the last email; the password comes from the system password manager only.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val last = AppState.settings.current().lastEmail
        if (email.isEmpty()) email = last
    }

    fun signInWith(mail: String, pass: String, alreadySaved: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                Api.signIn(mail, pass)
                if (Api.checkSession()) {
                    AppState.settings.setLastEmail(mail.trim())
                    if (!alreadySaved) SavedLogin.offerToSave(context, mail.trim(), pass)
                    onSignedIn()
                } else error = L10n.str(R.string.sign_in_session_rejected)
            } catch (t: Throwable) {
                error = com.bu.paperclip.ui.components.errorMessage(t)
            } finally { busy = false }
        }
    }

    fun pickSaved() {
        scope.launch {
            when (val r = SavedLogin.pick(context)) {
                is SavedLoginResult.Found -> { email = r.email; password = r.password; error = null; signInWith(r.email, r.password, alreadySaved = true) }
                else -> {}
            }
        }
    }

    // Offer saved logins once when the form first appears; dismissing the sheet just leaves the form.
    androidx.compose.runtime.LaunchedEffect(gate) {
        if (gate == Gate.Open && password.isEmpty()) pickSaved()
    }

    val submit = {
        if (!busy && email.isNotBlank() && password.isNotEmpty()) signInWith(email, password, alreadySaved = false)
    }
    AuthLayout(stringResource(R.string.sign_in_title), host) {
        when (val g = gate) {
            Gate.Checking -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp, color = MaterialTheme.colorScheme.onSurface)
            }
            is Gate.Error -> {
                Notice(g.message, error = true)
                Spacer(Modifier.height(16.dp))
                AuthButton(stringResource(R.string.common_try_again)) { probeKey++ }
            }
            is Gate.Required -> {
                Notice(stringResource(R.string.sign_in_gate_notice, g.host))
                Spacer(Modifier.height(16.dp))
                AuthButton(stringResource(R.string.sign_in_continue_to, g.host)) { gateBrowser = true }
            }
            Gate.Open -> {
                AuthField(
                    email, { email = it; error = null }, stringResource(R.string.sign_in_email), Icons.Outlined.Mail,
                    KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    contentType = ContentType.EmailAddress + ContentType.Username,
                )
                Spacer(Modifier.height(12.dp))
                AuthField(
                    password, { password = it; error = null }, stringResource(R.string.sign_in_password), Icons.Outlined.Lock,
                    KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                    secret = true,
                    onGo = submit,
                    contentType = ContentType.Password,
                )
                error?.let { Spacer(Modifier.height(12.dp)); Notice(it, error = true) }
                Spacer(Modifier.height(20.dp))
                AuthButton(stringResource(R.string.sign_in_title), enabled = email.isNotBlank() && password.isNotEmpty(), busy = busy, onClick = submit)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (gate == Gate.Open) AuthLink(stringResource(R.string.sign_in_use_saved)) { pickSaved() }
        AuthLink(stringResource(R.string.sign_in_web_instead)) { webFallback = true }
        AuthLink(stringResource(R.string.sign_in_change_server), onChangeServer)
    }
}

@Composable
private fun Notice(text: String, error: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (error) cs.onErrorContainer else cs.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(if (error) cs.errorContainer else cs.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GateBrowser(baseUrl: String, onPassed: () -> Unit, onClose: () -> Unit) {
    var progress by remember { mutableStateOf(0) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    BackHandler { if (webView?.canGoBack() == true) webView?.goBack() else onClose() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sign_in_title), style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { BackIcon(onClose) },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = settings.userAgentString.replace("; wv", "").replace("Version/4.0 ", "")
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) { progress = 10 }
                            override fun onPageFinished(view: WebView, url: String?) {
                                progress = 100
                                CookieManager.getInstance().flush()
                                if (url != null && url.startsWith("$baseUrl/api/")) onPassed()
                            }
                        }
                        loadUrl("$baseUrl/api/health")
                        webView = this
                    }
                },
            )
            if (progress in 1..99) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
}
