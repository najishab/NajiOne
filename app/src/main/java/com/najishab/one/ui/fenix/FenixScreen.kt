package com.najishab.one.ui.fenix

import android.annotation.SuppressLint
import android.app.Activity
import android.net.VpnService
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.najishab.one.R
import com.najishab.one.engines.fenix.FenixAuth
import com.najishab.one.engines.fenix.FenixEngine
import com.najishab.one.engines.fenix.FenixEngine.AccountView
import com.najishab.one.engines.fenix.FenixEngine.Conn
import com.najishab.one.engines.fenix.FenixEngine.Failure
import com.najishab.one.engines.fenix.FenixEngine.SignIn
import com.najishab.one.engines.fenix.FenixHttp
import com.najishab.one.ui.emergency.faDigits
import com.najishab.one.ui.home.frostedGlass
import com.najishab.one.ui.settings.Ios
import com.najishab.one.ui.settings.IosScreen
import com.najishab.one.ui.settings.SettingsActionRow
import com.najishab.one.ui.settings.SettingsFooter
import com.najishab.one.ui.settings.SettingsGroup
import com.najishab.one.ui.settings.SettingsRow
import com.najishab.one.ui.settings.SettingsSectionHeader
import com.najishab.one.ui.settings.SettingsToggle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private enum class Page { MAIN, COUNTRY, ACCOUNTS, DIRECT_ADD, WEB_ADD, WEB_CHECK }

/** Mozilla's sync sign-in page: reports the signed-in session over WebChannel (fxaccounts:login). */
private const val ACCOUNTS_URL = "https://accounts.firefox.com/?context=fx_desktop_v3&service=sync&action=email"

/**
 * «FENIX»: add a Firefox account in one tap (Mozilla's own page, inside the app), pick a country
 * (or the fastest), connect. More accounts can be added; each connection takes the next one in
 * turn, and the tunnel moves on by itself when an account's monthly allowance runs out.
 */
@Composable
fun FenixScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var ready by remember { mutableStateOf(FenixEngine.isInitialized()) }
    LaunchedEffect(Unit) {
        if (!ready) { withContext(Dispatchers.IO) { FenixEngine.init(context) }; ready = true }
    }
    if (!ready) {
        IosScreen(title = stringResource(R.string.fenix_title), onBack = onBack, backLabel = stringResource(R.string.home)) {}
        return
    }
    var page by rememberSaveable { mutableStateOf(Page.MAIN) }
    var back by rememberSaveable { mutableStateOf(Page.MAIN) }
    fun open(p: Page) { back = if (page == Page.ACCOUNTS) Page.ACCOUNTS else Page.MAIN; page = p }

    val challenge by FenixEngine.challenge.collectAsState()
    val signIn by FenixEngine.signIn.collectAsState()
    // Mozilla asked for its browser check: its page opens, the user passes it, and back we come.
    LaunchedEffect(challenge) { if (challenge && page != Page.WEB_CHECK) open(Page.WEB_CHECK) }
    // An account was added: back to where the user came from.
    LaunchedEffect(signIn) {
        if (signIn is SignIn.Done) {
            FenixEngine.resetSignIn()
            if (page == Page.WEB_ADD || page == Page.DIRECT_ADD) page = back
        }
    }
    BackHandler(enabled = page != Page.MAIN) {
        if (page == Page.WEB_CHECK) FenixEngine.dismissChallenge()
        if (page == Page.DIRECT_ADD || page == Page.WEB_ADD) FenixEngine.resetSignIn()
        page = if (page == Page.WEB_ADD || page == Page.WEB_CHECK || page == Page.DIRECT_ADD) back else Page.MAIN
    }
    when (page) {
        Page.COUNTRY -> { FenixCountryPicker(onBack = { page = Page.MAIN }); return }
        Page.ACCOUNTS -> {
            AccountsPage(
                onBack = { page = Page.MAIN },
                onAddDirect = { open(Page.DIRECT_ADD) },
                onAddWeb = { open(Page.WEB_ADD) },
            )
            return
        }
        Page.DIRECT_ADD -> { MozillaDirectSignInPage(onBack = { FenixEngine.resetSignIn(); page = back }); return }
        Page.WEB_ADD -> { MozillaSignInPage(onBack = { FenixEngine.resetSignIn(); page = back }); return }
        Page.WEB_CHECK -> {
            val host by FenixEngine.challengeHost.collectAsState()
            MozillaCheckPage(host) { cookies, agent -> FenixEngine.challengePassed(host, cookies, agent); page = back }
            return
        }
        Page.MAIN -> Unit
    }

    val accounts by FenixEngine.accounts.collectAsState()
    IosScreen(onBack = onBack, backLabel = stringResource(R.string.home)) {
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_app_fenix), contentDescription = null, modifier = Modifier.size(84.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.fenix_title), color = Ios.Label, fontSize = 32.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.fenix_tagline), color = Ios.SecondaryLabel, fontSize = 13.sp,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(14.dp))
        if (accounts.isEmpty()) Welcome(onAddDirect = { open(Page.DIRECT_ADD) }, onAddWeb = { open(Page.WEB_ADD) })
        else Ready(accounts, onCountry = { page = Page.COUNTRY }, onAccounts = { page = Page.ACCOUNTS })
        Spacer(Modifier.height(40.dp))
    }
}

// ── no account yet ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun Welcome(onAddDirect: () -> Unit, onAddWeb: () -> Unit) {
    val error by FenixEngine.signInError.collectAsState()
    PrimaryButton(stringResource(R.string.fenix_add_direct), enabled = true, onClick = onAddDirect)
    SettingsFooter(stringResource(R.string.fenix_add_direct_footer))
    Spacer(Modifier.height(14.dp))
    SecondaryButton(stringResource(R.string.fenix_add_web), enabled = true, onClick = onAddWeb)
    SettingsFooter(stringResource(R.string.fenix_add_web_footer))
    error?.let { ErrorText(it) }
}

// ── signed in ───────────────────────────────────────────────────────────────────────────────────

@Composable
private fun Ready(accounts: List<AccountView>, onCountry: () -> Unit, onAccounts: () -> Unit) {
    val context = LocalContext.current
    val conn by FenixEngine.conn.collectAsState()
    val country by FenixEngine.country.collectAsState()
    val pings by FenixEngine.pings.collectAsState()
    var forMae by remember { mutableStateOf(FenixEngine.forMae) }
    val connected = conn is Conn.Connected
    val busy = conn is Conn.Connecting
    LaunchedEffect(Unit) { FenixEngine.refreshAll() }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) FenixEngine.connectAsync(context)
    }
    fun connect() = com.najishab.one.data.ScanGuard.run(com.najishab.one.data.ScanGuard.Reason.CONNECT_VPN) {
        val prep = try { VpnService.prepare(context) } catch (e: Exception) { null }
        if (prep != null) launcher.launch(prep) else FenixEngine.connectAsync(context)
    }

    val left = accounts.filter { !it.signedOut }.mapNotNull { it.remainingBytes }.sum()
    val total = accounts.filter { !it.signedOut }.mapNotNull { it.limitBytes }.sum()
    SettingsGroup {
        SettingsRow(
            title = stringResource(R.string.fenix_country),
            icon = Icons.Default.Public, tint = Ios.Blue,
            value = countryValue(country, pings),
            onClick = onCountry,
        )
        Separator()
        SettingsRow(
            title = stringResource(R.string.fenix_accounts_row),
            icon = Icons.Default.DataUsage, tint = Ios.Green,
            value = if (total > 0) stringResource(R.string.fenix_quota_value, gb(left), gb(total)) else faDigits(accounts.size.toString()),
            subtitle = stringResource(R.string.fenix_accounts_count, faDigits(accounts.size.toString())),
            onClick = onAccounts,
        )
    }
    SettingsFooter(stringResource(R.string.fenix_quota_footer))

    Spacer(Modifier.height(24.dp))
    ConnectButton(connected, busy) { if (connected || busy) FenixEngine.disconnectAsync(context) else connect() }
    Spacer(Modifier.height(14.dp))
    Text(
        statusText(conn),
        color = if (conn is Conn.Failed) Ios.Orange else Ios.SecondaryLabel,
        fontSize = 14.sp, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
    )
    com.najishab.one.ui.RouteChips()

    SettingsSectionHeader(stringResource(R.string.fenix_mae_section))
    SettingsGroup {
        SettingsToggle(
            title = stringResource(R.string.fenix_mae_toggle),
            checked = forMae,
            onCheckedChange = { forMae = it; FenixEngine.forMae = it },
            icon = Icons.Default.AutoAwesome, tint = Ios.Blue,
        )
    }
    SettingsFooter(stringResource(R.string.fenix_mae_footer))
}

// ── the accounts page ───────────────────────────────────────────────────────────────────────────

@Composable
private fun AccountsPage(onBack: () -> Unit, onAddDirect: () -> Unit, onAddWeb: () -> Unit) {
    val context = LocalContext.current
    val accounts by FenixEngine.accounts.collectAsState()
    val refreshing by FenixEngine.refreshing.collectAsState()
    var removing by remember { mutableStateOf<AccountView?>(null) }
    LaunchedEffect(Unit) { FenixEngine.refreshAll() }

    IosScreen(title = stringResource(R.string.fenix_accounts_title), onBack = onBack, backLabel = stringResource(R.string.fenix_title)) {
        Spacer(Modifier.height(10.dp))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            accounts.forEach { a -> AccountCard(a, onRemove = { removing = a }) }
        }
        Spacer(Modifier.height(18.dp))
        SettingsGroup {
            SettingsActionRow(label = stringResource(R.string.fenix_add_direct), icon = Icons.Default.PersonAdd, tint = Ios.Orange, onClick = onAddDirect)
            Separator()
            SettingsActionRow(label = stringResource(R.string.fenix_add_web), icon = Icons.Default.Public, tint = Ios.Blue, onClick = onAddWeb)
            Separator()
            SettingsActionRow(
                label = stringResource(if (refreshing) R.string.fenix_refreshing else R.string.fenix_refresh),
                icon = Icons.Default.Refresh, tint = Ios.Green, busy = refreshing,
                onClick = { FenixEngine.refreshAll(force = true) },
            )
        }
        SettingsFooter(stringResource(R.string.fenix_accounts_footer))
        Spacer(Modifier.height(28.dp))
    }

    removing?.let { a ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.fenix_remove)) },
            text = { Text(stringResource(R.string.fenix_remove_confirm, a.email)) },
            confirmButton = {
                TextButton(onClick = { FenixEngine.removeAccount(context, a.id); removing = null }) {
                    Text(stringResource(R.string.fenix_remove), color = Ios.Red)
                }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.geph_cancel)) } },
        )
    }
}

@Composable
private fun AccountCard(a: AccountView, onRemove: () -> Unit) {
    Column(Modifier.fillMaxWidth().frostedGlass(RoundedCornerShape(14.dp)).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(a.email.ifBlank { "—" }, color = Ios.Label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
            val badge = when {
                a.signedOut -> stringResource(R.string.fenix_badge_signed_out) to Ios.Red
                a.exhausted -> stringResource(R.string.fenix_badge_used_up) to Ios.Orange
                a.active -> stringResource(R.string.fenix_badge_active) to Ios.Green
                else -> null
            }
            badge?.let { (t, c) ->
                Text(t, color = c, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(c.copy(alpha = 0.15f)).padding(horizontal = 7.dp, vertical = 2.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        val left = a.remainingBytes
        val limit = a.limitBytes
        if (left != null && limit != null && limit > 0) {
            LinearProgressIndicator(
                progress = (left.toFloat() / limit).coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = if (a.exhausted) Ios.Orange else Ios.Green,
                trackColor = Ios.Gray.copy(alpha = 0.25f),
            )
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.fenix_card_left, gb(left), gb(limit)), color = Ios.Label, fontSize = 13.sp)
        } else {
            Text(stringResource(R.string.fenix_card_unknown), color = Ios.SecondaryLabel, fontSize = 13.sp)
        }
        a.resetAt?.let { r -> Text(resetLine(r), color = Ios.SecondaryLabel, fontSize = 12.sp) }
        if (a.quotaAt > 0) {
            Text(stringResource(R.string.fenix_card_checked, agoText(a.quotaAt)), color = Ios.SecondaryLabel, fontSize = 11.sp)
        }
        Text(
            stringResource(R.string.fenix_remove), color = Ios.Red, fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp).clickable(role = Role.Button, onClick = onRemove),
        )
    }
}

/** "refills on ۱۲ آبان (in 9 days)" -- the date in the Persian calendar on a Persian UI. */
@Composable
private fun resetLine(resetAt: Long): String {
    val days = ((resetAt - System.currentTimeMillis() / 1000) / 86_400).coerceAtLeast(0)
    val date = runCatching {
        val locale = Locale.getDefault()
        val uloc = if (locale.language == "fa") android.icu.util.ULocale("fa_IR@calendar=persian") else android.icu.util.ULocale.forLocale(locale)
        android.icu.text.SimpleDateFormat("d MMMM", uloc).format(java.util.Date(resetAt * 1000))
    }.getOrDefault("")
    return if (days == 0L) stringResource(R.string.fenix_reset_today, date)
    else stringResource(R.string.fenix_reset_on, date, faDigits(days.toString()))
}

@Composable
private fun agoText(at: Long): String {
    val min = ((System.currentTimeMillis() - at) / 60_000).coerceAtLeast(0)
    return when {
        min < 1 -> stringResource(R.string.fenix_ago_now)
        min < 60 -> stringResource(R.string.fenix_ago_min, faDigits(min.toString()))
        else -> stringResource(R.string.fenix_ago_hour, faDigits((min / 60).toString()))
    }
}

// ── adding an account: direct credentials ────────────────────────────────────────────────────────

@Composable
private fun MozillaDirectSignInPage(onBack: () -> Unit) {
    val signIn by FenixEngine.signIn.collectAsState()
    val error by FenixEngine.signInError.collectAsState()
    val busy = signIn is SignIn.Busy
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }

    val needCode = signIn as? SignIn.NeedCode

    IosScreen(
        title = stringResource(if (needCode != null) R.string.fenix_code_label else R.string.fenix_add_direct_title),
        onBack = onBack,
        backLabel = stringResource(R.string.fenix_title),
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            if (needCode != null) stringResource(R.string.fenix_code_hint, needCode.email)
            else stringResource(R.string.fenix_add_direct_hint),
            color = Ios.SecondaryLabel, fontSize = 13.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(16.dp))

        if (needCode != null) {
            SettingsGroup {
                Field(
                    label = stringResource(R.string.fenix_code_label),
                    value = code,
                    onChange = { code = it.trim() },
                    type = KeyboardType.Number,
                    secret = false,
                )
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                label = stringResource(R.string.fenix_code_submit),
                enabled = code.isNotBlank() && !busy,
                busy = busy,
                onClick = { FenixEngine.verifyCode(code) },
            )
        } else {
            SettingsGroup {
                Field(
                    label = stringResource(R.string.fenix_email_label),
                    value = email,
                    onChange = { email = it },
                    type = KeyboardType.Email,
                    secret = false,
                )
                Separator()
                Field(
                    label = stringResource(R.string.fenix_password_label),
                    value = password,
                    onChange = { password = it },
                    type = KeyboardType.Password,
                    secret = true,
                )
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                label = stringResource(R.string.fenix_add_direct_title),
                enabled = email.isNotBlank() && password.isNotBlank() && !busy,
                busy = busy,
                onClick = { FenixEngine.signIn(email, password) },
            )
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            ErrorText(it)
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    type: KeyboardType,
    secret: Boolean,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Ios.Label, fontSize = 15.sp, modifier = Modifier.width(80.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(
                color = Ios.Label,
                fontSize = 15.sp,
                textDirection = TextDirection.Ltr,
            ),
            cursorBrush = SolidColor(Ios.Blue),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = type, autoCorrect = false),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SecondaryButton(label: String, enabled: Boolean, busy: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp))
            .background(Ios.Gray.copy(alpha = 0.25f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (busy) stringResource(R.string.fenix_busy) else label, color = Ios.Label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ── adding an account: Mozilla's own page ───────────────────────────────────────────────────────

/**
 * Mozilla's sign-in page in the app. It asks for an email and then signs the user in -- or, for a
 * new email, makes the account -- and handles the emailed code and its browser check itself, like
 * in any browser. When it is done it keeps its Firefox Accounts session in the page's storage
 * (`__fxa_storage.accounts`, the keys Mozilla's own code uses); the app reads that session once
 * and turns it into the account's tokens. The password never passes through the app.
 *
 * The page's storage is wiped first, so "add another" never lands in the account added before.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MozillaSignInPage(onBack: () -> Unit) {
    val signIn by FenixEngine.signIn.collectAsState()
    val error by FenixEngine.signInError.collectAsState()
    val busy = signIn is SignIn.Busy
    var currentWebView by remember { mutableStateOf<WebView?>(null) }

    IosScreen(title = stringResource(R.string.fenix_add_web_title), onBack = onBack, backLabel = stringResource(R.string.fenix_title), scrollable = false) {
        Text(
            if (busy) stringResource(R.string.fenix_adding) else stringResource(R.string.fenix_add_web_hint),
            color = if (busy) Ios.Green else Ios.SecondaryLabel, fontSize = 13.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        )
        error?.let { ErrorText(it) }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx -> com.najishab.one.ui.webViewOrNotice(ctx) { ctx ->
                    WebStorage.getInstance().deleteAllData()
                    CookieManager.getInstance().removeSessionCookies(null)
                    WebView(ctx).apply {
                        currentWebView = this
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = FenixHttp.USER_AGENT
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        var done = false
                        val handler = android.os.Handler(android.os.Looper.getMainLooper())

                        class FenixBridge {
                            @android.webkit.JavascriptInterface
                            fun onWebChannelMessage(raw: String?) {
                                raw ?: return
                                runCatching {
                                    val json = JSONObject(raw)
                                    val id = json.optString("id")
                                    val msg = json.optJSONObject("message") ?: return
                                    val command = msg.optString("command")
                                    val messageId = msg.opt("messageId")
                                    when (command) {
                                        "fxaccounts:fxa_status" -> {
                                            val reply = "window.__fenixReply(${JSONObject.quote(id)}, ${JSONObject.quote(messageId?.toString().orEmpty())}, 'fxaccounts:fxa_status', { capabilities: {}, signedInUser: null });"
                                            handler.post { evaluateJavascript(reply, null) }
                                        }
                                        "fxaccounts:can_link_account" -> {
                                            val reply = "window.__fenixReply(${JSONObject.quote(id)}, ${JSONObject.quote(messageId?.toString().orEmpty())}, 'fxaccounts:can_link_account', { ok: true });"
                                            handler.post { evaluateJavascript(reply, null) }
                                        }
                                        "fxaccounts:login", "fxaccounts:oauth_login" -> {
                                            val data = msg.optJSONObject("data") ?: return
                                            val token = data.optString("sessionToken").ifBlank { data.optString("token") }
                                            val email = data.optString("email")
                                            if (token.matches(Regex("^[0-9a-fA-F]{64}$")) && !done) {
                                                done = true
                                                handler.post {
                                                    FenixEngine.challengePassed(FenixAuth.CHALLENGE_HOST, cookies(), settings.userAgentString)
                                                    FenixEngine.signInWithSession(email, token)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            @android.webkit.JavascriptInterface
                            fun onSession(email: String?, sessionToken: String?) {
                                if (!sessionToken.isNullOrBlank() && sessionToken.matches(Regex("^[0-9a-fA-F]{64}$")) && !done) {
                                    done = true
                                    handler.post {
                                        FenixEngine.challengePassed(FenixAuth.CHALLENGE_HOST, cookies(), settings.userAgentString)
                                        FenixEngine.signInWithSession(email.orEmpty(), sessionToken)
                                    }
                                }
                            }
                        }
                        addJavascriptInterface(FenixBridge(), "FenixBridge")

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                view.evaluateJavascript(WEBCHANNEL_HOOK_JS, null)
                            }
                            override fun onPageFinished(view: WebView, url: String?) {
                                super.onPageFinished(view, url)
                                view.evaluateJavascript(WEBCHANNEL_HOOK_JS, null)
                            }
                        }

                        // Fallback storage polling
                        val poll = object : Runnable {
                            override fun run() {
                                if (done || !isAttachedToWindow) return
                                evaluateJavascript(READ_SESSION_JS) { raw ->
                                    val s = decodeJsString(raw)
                                    if (s.isNotBlank() && !done) {
                                        runCatching {
                                            val o = JSONObject(s)
                                            val token = o.optString("s")
                                            if (token.matches(Regex("^[0-9a-fA-F]{64}$"))) {
                                                done = true
                                                FenixEngine.challengePassed(FenixAuth.CHALLENGE_HOST, cookies(), settings.userAgentString)
                                                FenixEngine.signInWithSession(o.optString("e"), token)
                                            }
                                        }
                                    }
                                }
                                if (!done) handler.postDelayed(this, 1_500)
                            }
                        }
                        loadUrl(ACCOUNTS_URL)
                        handler.postDelayed(poll, 2_000)
                    }
                } },
            )
        }
        Spacer(Modifier.height(8.dp))
        PrimaryButton(
            label = stringResource(R.string.fenix_check_status_btn),
            enabled = !busy,
            busy = busy,
        ) {
            currentWebView?.let { wv ->
                wv.evaluateJavascript(READ_SESSION_JS) { raw ->
                    val s = decodeJsString(raw)
                    if (s.isNotBlank()) {
                        runCatching {
                            val o = JSONObject(s)
                            val token = o.optString("s")
                            if (token.matches(Regex("^[0-9a-fA-F]{64}$"))) {
                                FenixEngine.challengePassed(FenixAuth.CHALLENGE_HOST, cookies(), wv.settings.userAgentString)
                                FenixEngine.signInWithSession(o.optString("e"), token)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

private const val WEBCHANNEL_HOOK_JS = """(function(){
try{
if(window.__fenixHooked)return;
window.__fenixHooked=true;
var send=function(o){
try{
if(window.FenixBridge&&window.FenixBridge.onWebChannelMessage){
window.FenixBridge.onWebChannelMessage(JSON.stringify(o));
}
}catch(e){}
};
var onChannel=function(e){
var d=e.detail;
if(typeof d==='string'){try{d=JSON.parse(d);}catch(e){return;}}
if(d&&d.message)send(d);
};
window.addEventListener('WebChannelMessageToChrome',onChannel);
document.addEventListener('WebChannelMessageToChrome',onChannel);
window.__fenixReply=function(id,messageId,command,data){
var detail={id:id,message:{messageId:messageId,command:command,data:data}};
window.dispatchEvent(new CustomEvent('WebChannelMessageToContent',{detail:detail}));
document.dispatchEvent(new CustomEvent('WebChannelMessageToContent',{detail:detail}));
};
}catch(e){}
})()"""

/**
 * The verified session of the account the page is signed in to, as `{"e":email,"s":token}`, or ""
 * while there is none.
 */
private const val READ_SESSION_JS = """(function(){try{
var stores=[localStorage,sessionStorage];
for(var si=0;si<stores.length;si++){
var st=stores[si];if(!st)continue;
for(var i=0;i<st.length;i++){
var k=st.key(i);var val=st.getItem(k);
if(val&&val.indexOf('sessionToken')!==-1){
try{
var obj=JSON.parse(val);
function findS(o){
if(!o||typeof o!=='object')return null;
if(o.sessionToken&&typeof o.sessionToken==='string'&&/^[0-9a-fA-F]{64}$/.test(o.sessionToken))return {e:o.email||'',s:o.sessionToken};
for(var p in o){if(o[p]&&typeof o[p]==='object'){var f=findS(o[p]);if(f)return f;}}
return null;
}
var found=findS(obj);
if(found)return JSON.stringify(found);
}catch(_){}
}
}
}
return '';}catch(e){return '';}})()"""

/** evaluateJavascript hands back a JSON-encoded string ("\"…\"" or "null"). */
private fun decodeJsString(raw: String?): String =
    if (raw.isNullOrBlank() || raw == "null") "" else runCatching { JSONArray("[$raw]").getString(0) }.getOrDefault("")

/** The WebView's cookies by host: Firefox Accounts', Guardian's, and the host that asked for a check. */
private fun cookies(host: String? = null): Map<String, String?> {
    val cm = CookieManager.getInstance()
    return (listOf("accounts.firefox.com", "api.accounts.firefox.com", "vpn.mozilla.org") + listOfNotNull(host))
        .distinct().associateWith { cm.getCookie("https://$it") }
}

/**
 * Mozilla's browser check, for when an API call was refused with it: the page is opened, the user
 * passes it as in any browser, and its cookies carry the app's calls from then on.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MozillaCheckPage(host: String, onDone: (Map<String, String?>, String?) -> Unit) {
    var done by remember { mutableStateOf(false) }
    val finish = { if (!done) { done = true; onDone(cookies(host), FenixHttp.USER_AGENT) } }
    IosScreen(title = stringResource(R.string.fenix_check_title), onBack = finish, backLabel = stringResource(R.string.fenix_title), scrollable = false) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx -> com.najishab.one.ui.webViewOrNotice(ctx) { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = FenixHttp.USER_AGENT
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, loaded: String?) {
                                view.evaluateJavascript("document.title") { t ->
                                    if (t != null && !t.contains("Challenge", ignoreCase = true) && t.length > 2) finish()
                                }
                            }
                        }
                        val targetUrl = if (host.endsWith("mozilla.org")) "https://vpn.mozilla.org/" else "https://accounts.firefox.com/"
                        loadUrl(targetUrl)
                    }
                } },
            )
        }
        Spacer(Modifier.height(8.dp))
        PrimaryButton(stringResource(R.string.fenix_check_done), enabled = true) { finish() }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun PrimaryButton(label: String, enabled: Boolean, busy: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp))
            .background(if (enabled) Ios.Orange else Ios.Gray.copy(alpha = 0.35f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (busy) stringResource(R.string.fenix_busy) else label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ErrorText(f: Failure) {
    Text(
        failureText(f), color = Ios.Orange, fontSize = 13.sp, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
    )
}

// ── connect ─────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun ConnectButton(connected: Boolean, busy: Boolean, onClick: () -> Unit) {
    val ring = when { connected -> Ios.Green; busy -> Ios.Orange; else -> Ios.Gray }
    val label = stringResource(if (connected || busy) R.string.fenix_disconnect else R.string.fenix_connect)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(132.dp).clip(CircleShape).background(ring.copy(alpha = 0.18f)).border(3.dp, ring, CircleShape)
                .semantics { contentDescription = label }
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = ring, modifier = Modifier.size(44.dp))
                Spacer(Modifier.height(4.dp))
                Text(label, color = Ios.Label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun statusText(c: Conn): String = when (c) {
    is Conn.Idle -> stringResource(R.string.fenix_status_idle)
    is Conn.Connecting -> stringResource(when (c.step) {
        FenixEngine.Step.PASS -> R.string.fenix_step_pass
        FenixEngine.Step.SERVER -> R.string.fenix_step_server
        FenixEngine.Step.TUNNEL -> R.string.fenix_step_tunnel
        FenixEngine.Step.CHECK -> R.string.fenix_step_check
        FenixEngine.Step.SWITCH -> R.string.fenix_step_switch
    })
    is Conn.Connected -> listOfNotNull(
        stringResource(R.string.fenix_status_connected),
        (c.exitCountry ?: c.country).let { "${flag(it)} ${countryName(it)}" },
        c.latencyMs?.let { stringResource(R.string.fenix_ms, faDigits(it.toString())) },
    ).joinToString(" · ") + (c.email.takeIf { it.isNotBlank() }?.let { "\n$it" } ?: "")
    is Conn.Failed -> failureText(c.why)
}

@Composable
private fun failureText(f: Failure): String = stringResource(when (f) {
    Failure.SIGNED_OUT -> R.string.fenix_fail_signed_out
    Failure.CHALLENGE -> R.string.fenix_fail_challenge
    Failure.QUOTA -> R.string.fenix_fail_quota
    Failure.OFFLINE -> R.string.fenix_fail_offline
    Failure.SERVER_DOWN -> R.string.fenix_fail_server
    Failure.NO_DATA -> R.string.fenix_fail_no_data
    Failure.VPN_REFUSED -> R.string.fenix_fail_vpn
    Failure.WRONG_PASSWORD -> R.string.fenix_fail_password
    Failure.WRONG_CODE -> R.string.fenix_fail_code
    Failure.UNKNOWN -> R.string.fenix_fail_unknown
})

private fun gb(b: Long) = faDigits(String.format(Locale.US, "%.1f", b / 1_073_741_824.0))

@Composable
private fun countryValue(code: String, pings: Map<String, Long>): String = when (code) {
    "fastest" -> "⚡ " + stringResource(R.string.fenix_fastest)
    "REC" -> "⭐ " + stringResource(R.string.fenix_recommended)
    else -> "${flag(code)} ${countryName(code)}" + (pings[code]?.takeIf { it >= 0 }?.let { " · " + stringResource(R.string.fenix_ms, faDigits(it.toString())) } ?: "")
}

// ── the country list ────────────────────────────────────────────────────────────────────────────

@Composable
private fun FenixCountryPicker(onBack: () -> Unit) {
    val context = LocalContext.current
    val servers by FenixEngine.servers.collectAsState()
    val pings by FenixEngine.pings.collectAsState()
    val pinging by FenixEngine.pinging.collectAsState()
    val current by FenixEngine.country.collectAsState()
    val seen = remember { FenixEngine.exitSeen() }
    LaunchedEffect(Unit) { if (servers.isEmpty()) withContext(Dispatchers.IO) { FenixEngine.refreshServers(force = true) } }

    val countries = servers.filter { it.country != "REC" }
        .sortedWith(compareBy<com.najishab.one.engines.fenix.FenixServers.Server> {
            val p = pings[it.country]
            when { p == null -> 1; p < 0 -> 2; else -> 0 }
        }.thenBy { pings[it.country]?.takeIf { p -> p >= 0 } ?: Long.MAX_VALUE }.thenBy { countryName(it.country) })

    fun choose(code: String) { FenixEngine.setCountry(context, code); onBack() }

    IosScreen(title = stringResource(R.string.fenix_country), onBack = onBack, backLabel = stringResource(R.string.fenix_title)) {
        Spacer(Modifier.height(10.dp))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CountryRow(
                if (pinging) "⏳" else "📶",
                stringResource(if (pinging) R.string.fenix_pinging else R.string.fenix_ping_all),
                stringResource(R.string.fenix_ping_detail), false,
            ) { FenixEngine.pingAll() }
            Spacer(Modifier.height(8.dp))
            CountryRow("⚡", stringResource(R.string.fenix_fastest), stringResource(R.string.fenix_fastest_detail), current == "fastest") { choose("fastest") }
            if (servers.any { it.country == "REC" }) {
                CountryRow("⭐", stringResource(R.string.fenix_recommended), stringResource(R.string.fenix_recommended_detail), current == "REC") { choose("REC") }
            }
            Spacer(Modifier.height(8.dp))
            countries.forEach { s ->
                val p = pings[s.country]
                val detail = listOfNotNull(
                    when { p == null -> null; p < 0 -> stringResource(R.string.fenix_no_answer); else -> stringResource(R.string.fenix_ms, faDigits(p.toString())) },
                    seen[s.country]?.takeIf { it != s.country }?.let { stringResource(R.string.fenix_exits_in, countryName(it)) },
                ).joinToString(" · ")
                CountryRow(flag(s.country), countryName(s.country), detail, current == s.country,
                    detailColor = when { p == null -> Ios.SecondaryLabel; p < 0 -> Ios.Red; else -> Ios.Green }) { choose(s.country) }
            }
        }
        SettingsFooter(stringResource(R.string.fenix_country_footer))
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun CountryRow(flag: String, name: String, detail: String, selected: Boolean, detailColor: Color = Ios.SecondaryLabel, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().frostedGlass(RoundedCornerShape(14.dp)).clickable(onClick = onClick)
            .heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(flag, fontSize = 22.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = Ios.Label, fontSize = 16.sp, maxLines = 1)
            if (detail.isNotBlank()) Text(detail, color = detailColor, fontSize = 11.sp, maxLines = 2)
        }
        if (selected) Text("✓", color = Ios.Blue, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

private fun countryName(cc: String): String =
    runCatching { Locale.Builder().setRegion(cc).build().getDisplayCountry(Locale.getDefault()) }.getOrDefault(cc).ifBlank { cc }

private fun flag(cc: String): String =
    if (cc.length != 2 || !cc.all { it.isLetter() }) "🌐"
    else cc.uppercase().map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
