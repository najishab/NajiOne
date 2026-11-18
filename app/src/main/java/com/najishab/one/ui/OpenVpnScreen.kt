
package com.najishab.one.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.najishab.one.openvpn.*
import com.najishab.one.utils.GeoLabel
import com.najishab.one.store.tr
import com.najishab.one.ui.settings.Ios
import com.najishab.one.ui.settings.IosScreen
import com.najishab.one.ui.settings.SettingsActionRow
import com.najishab.one.ui.settings.SettingsFooter
import com.najishab.one.ui.settings.SettingsGroup
import com.najishab.one.ui.settings.SettingsRow
import com.najishab.one.ui.settings.SettingsSectionHeader
import com.najishab.one.ui.settings.SettingsToggle
import com.najishab.one.ui.tunnel.DialCore
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * «اوپن‌وی‌پی‌ان»: TunnelBear's servers, connected directly, with the .ovpn files bundled in assets.
 *
 * Built like every other engine page: the dial, one status line, the live figures while
 * connected, then grouped rows that open their own pages. No tabs and no dialogs.
 *
 * Connecting: tapping the dial fetches the sign-in details from GitHub ([OpenVpnViewModel.connectWithGitHub]).
 * If they cannot be fetched, or the server refuses them, the view-model raises a credential prompt and
 * [CredentialPage] asks the user for their own email and password.
 */
@Composable
fun OpenVpnScreen(onBack: () -> Unit, onStore: () -> Unit, onIran: () -> Unit, visible: Boolean = true) {
    val context = LocalContext.current
    val vm = remember(context) {
        val owner = generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }
            .filterIsInstance<ViewModelStoreOwner>().first()
        ViewModelProvider(owner, ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as android.app.Application))[OpenVpnViewModel::class.java]
    }
    val loaded by vm.data.collectAsState()
    val connection by OpenVpnRuntime.connection.collectAsState()
    val testing by vm.testing.collectAsState()
    val testTotal by vm.testTotal.collectAsState()
    val message by vm.message.collectAsState()
    val fetching by vm.fetchingCredentials.collectAsState()
    val prompt by vm.credentialPrompt.collectAsState()
    var page by rememberSaveable { mutableStateOf(Page.MAIN) }
    // What to do once the system VPN-permission dialog has been answered "OK".
    var afterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }
    val fa = remember { tr("fa", "en") == "fa" }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val action = afterPermission
        afterPermission = null
        if (it.resultCode == Activity.RESULT_OK && action != null) action()
        else vm.message.value = "VPN_PERMISSION_DENIED"
    }
    fun withVpnPermission(action: () -> Unit) {
        val intent = VpnService.prepare(context)
        if (intent == null) action()
        else { afterPermission = action; permission.launch(intent) }
    }
    fun connect(locationId: String, mode: String) {
        vm.cancelTests()
        withVpnPermission { vm.connectWithGitHub(context, locationId, mode) }
    }

    BackHandler(enabled = page != Page.MAIN || prompt != null) {
        if (page != Page.MAIN) page = Page.MAIN else vm.dismissCredentialPrompt()
    }

    val data = loaded
    if (data == null || data.storageError) {
        IosScreen(title = "OpenVPN", onBack = onBack) {
            if (data == null) CircularProgressIndicator(Modifier.padding(32.dp).align(Alignment.CenterHorizontally))
            else SettingsFooter(tr("ذخیره‌سازی امن باز نشد. اطلاعات قبلی بازنویسی نشده است؛ برنامه را دوباره باز کنید.", "Secure storage could not be opened. Existing data was preserved; reopen the app."))
        }
        return
    }
    val location = data.locations.firstOrNull { it.id == data.selectedLocation }

    // The server asked for (or refused) the sign-in: the user's own email and password are needed.
    val currentPrompt = prompt
    if (page == Page.MAIN && currentPrompt != null) {
        CredentialPage(
            prompt = currentPrompt,
            fa = fa,
            message = message,
            onDismissMessage = { vm.message.value = null },
            onBack = { vm.dismissCredentialPrompt() },
            onGuide = { page = Page.GUIDE },
            onSubmit = { user, pass ->
                withVpnPermission { vm.connectWithManual(context, currentPrompt.locationId, currentPrompt.transportMode, user, pass) }
            },
        )
        return
    }

    when (page) {
        Page.MAIN -> MainPage(
            data = data, connection = connection, location = location, fetching = fetching, fa = fa,
            message = message, onDismissMessage = { vm.message.value = null },
            onBack = onBack,
            onDial = {
                when {
                    connection.active || fetching -> vm.cancelConnecting(context)
                    location == null -> page = Page.SERVERS
                    else -> connect(location.id, data.selectedMode)
                }
            },
            onServers = { page = Page.SERVERS },
            onProtocols = { page = Page.PROTOCOLS },
            onToggleSplitTcp = { vm.setSplitTcp(it) },
            onToggleBlockIpv6 = { vm.setBlockIpv6(it) },
            onManual = {
                if (location == null) page = Page.SERVERS
                else vm.openManualPrompt(location.id, data.selectedMode)
            },
            onGuide = { page = Page.GUIDE },
        )
        Page.SERVERS -> ServersPage(
            data = data, connection = connection, testing = testing, testTotal = testTotal, fa = fa,
            onBack = { page = Page.MAIN },
            onTestAll = { mode -> if (testing.isEmpty()) vm.testAll(data.locations, mode) else vm.cancelTests() },
            onPick = { loc, mode ->
                vm.selectLocation(loc.id)
                vm.selectMode(mode)
                val already = connection.active && connection.locationId == loc.id && connection.activeProtocol == mode
                if (!already) connect(loc.id, mode)
                page = Page.MAIN
            },
            onFavorite = { loc -> vm.toggleFavorite(loc.id) },
            onFastest = { mode ->
                val fastest = ServerPolicy.fastestLocation(data.locations, System.currentTimeMillis())
                if (fastest == null) vm.message.value = "TEST_FIRST"
                else {
                    vm.selectLocation(fastest.id)
                    vm.selectMode(mode)
                    connect(fastest.id, mode)
                }
                page = Page.MAIN
            },
            onSelectMode = { mode -> vm.selectMode(mode) },
        )
        Page.PROTOCOLS -> ProtocolsPage(
            data = data,
            onBack = { page = Page.MAIN },
            onPick = { mode -> vm.selectMode(mode) },
            onToggleSplitTcp = { vm.setSplitTcp(it) },
            onToggleBlockIpv6 = { vm.setBlockIpv6(it) },
        )
        Page.GUIDE -> GuidePage(
            onBack = { page = Page.MAIN },
            onIran = onIran,
            onStore = onStore,
            onSite = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.tunnelbear.com/account"))) } /* no browser installed: crash #576 */ },
        )
    }
}

private enum class Page { MAIN, SERVERS, PROTOCOLS, GUIDE }

private val OpenVpnOrange = Color(0xFFEA7E20)

/** The remotes of a location's preferred variant: enough to work out a country when the name has none. */
private fun remotesOf(l: OpenVpnLocation): List<Remote> = l.profileFor(OpenVpnLocation.AUTO_MODE)?.remotes.orEmpty()

private fun placeOf(l: OpenVpnLocation, fa: Boolean) = OpenVpnPlaces.ofLocation(l.id, l.name, remotesOf(l), fa)

// ── the main page ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun MainPage(
    data: OpenVpnData,
    connection: OpenVpnConnection,
    location: OpenVpnLocation?,
    fetching: Boolean,
    fa: Boolean,
    message: String?,
    onDismissMessage: () -> Unit,
    onBack: () -> Unit,
    onDial: () -> Unit,
    onServers: () -> Unit,
    onProtocols: () -> Unit,
    onToggleSplitTcp: (Boolean) -> Unit,
    onToggleBlockIpv6: (Boolean) -> Unit,
    onManual: () -> Unit,
    onGuide: () -> Unit,
) {
    val running = connection.phase == ConnectionPhase.CONNECTED
    val working = fetching || connection.phase in setOf(ConnectionPhase.CONNECTING, ConnectionPhase.RECONNECTING, ConnectionPhase.DISCONNECTING)
    val failed = connection.phase == ConnectionPhase.ERROR && !fetching

    IosScreen(title = "OpenVPN", onBack = onBack) {
        Spacer(Modifier.height(8.dp))
        message?.let { Banner(errorText(it), onDismissMessage) }

        Text(
            tr("سرورهای TunnelBear، مستقیم و بی‌واسطه: هیچ تونل دیگری زیر آن نیست.",
                "TunnelBear's servers, connected directly: no other tunnel underneath."),
            color = Ios.SecondaryLabel, fontSize = 13.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
        )
        Spacer(Modifier.height(18.dp))
        DialCore(
            accent = when { running -> Ios.Green; failed -> Ios.Red; else -> OpenVpnOrange },
            working = working,
            running = running,
            progress = -1,
            label = when {
                running -> tr("قطع", "Disconnect")
                working -> tr("لغو", "Cancel")
                else -> tr("اتصال", "Connect")
            },
            onClick = onDial,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // A plain glyph, no artwork on the dial: the user asked for this here as for «گف».
            Icon(Icons.Default.Power, null, tint = Color.White, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(
            headline(connection, fetching),
            color = when { running -> Ios.Green; failed -> Ios.Red; working -> OpenVpnOrange; else -> Ios.SecondaryLabel },
            fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        )
        com.najishab.one.ui.RouteChips()
        val sub = when {
            fetching -> tr("دریافت اطلاعات ورود…", "Getting the sign-in details…")
            failed -> connection.error?.let { errorText(it) }
            working && connection.detail == "VERIFYING" -> tr("بررسی عبور واقعی داده…", "Checking that data really passes…")
            working -> tr("اتصال مستقیم به سرور…", "Connecting straight to the server…")
            else -> null
        }
        if (sub != null) {
            Spacer(Modifier.height(4.dp))
            Text(sub, color = Ios.SecondaryLabel, fontSize = 13.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp))
        }
        Spacer(Modifier.height(18.dp))

        if (running) LiveGroup(connection, fa)

        SettingsSectionHeader(tr("سرور", "Server"))
        SettingsGroup {
            if (location != null) {
                ServerRow(location, fa, selected = false, busy = false, activeMode = data.selectedMode, onClick = onServers, onFavorite = null, chevron = true)
            } else {
                SettingsRow(tr("انتخاب سرور", "Choose a server"), Icons.Default.Public, Ios.Blue, onClick = onServers)
            }
        }
        SettingsFooter(tr("کشوری که سایت‌ها می‌بینند. تأخیرها از همان مسیری سنجیده می‌شوند که اتصال از آن می‌رود.",
            "The country sites will see. Delays are measured on the same route the connection takes."))

        SettingsSectionHeader(tr("پروتکل و ضد فیلترینگ", "Protocol & Anti-censorship"))
        SettingsGroup {
            SettingsRow(
                title = tr("پروتکل و پورت", "Protocol and port"),
                icon = Icons.Default.Tune,
                tint = Ios.Indigo,
                value = OpenVpnLocation.formatModeLabel(data.selectedMode),
                onClick = onProtocols,
            )
            Separator()
            SettingsToggle(
                title = tr("تکه‌تکه کردن TCP (ضد فیلترینگ)", "Split TCP (Anti-censorship)"),
                subtitle = tr("تکه‌تکه کردن بسته‌های اول Handshake روی TCP برای عبور از فیلترینگ DPI ایران",
                    "Fragment initial TCP handshake packets to bypass DPI censorship in Iran"),
                icon = Icons.Default.Security,
                tint = Ios.Blue,
                checked = data.splitTcp,
                onCheckedChange = onToggleSplitTcp,
            )
            Separator()
            SettingsToggle(
                title = tr("مسدودسازی IPv6", "Block IPv6"),
                subtitle = tr("غیرفعال‌سازی کامل IPv6 و عبور اجباری تمام ترافیک از طریق IPv4",
                    "Disable IPv6 completely and force all traffic through IPv4"),
                icon = Icons.Default.Block,
                tint = Ios.Orange,
                checked = data.blockIpv6,
                onCheckedChange = onToggleBlockIpv6,
            )
        }
        SettingsFooter(tr("قابلیت Split TCP بسته‌های آغازین اتصال TCP را خرد می‌کند. همچنین مسدودسازی IPv6 از نشت و سیاهچاله شدن ترافیک جلوگیری کرده و کل داده‌ها را از تونل IPv4 عبور می‌دهد.",
            "Split TCP fragments initial packets over TCP. Block IPv6 prevents IPv6 leaks and blackholes by forcing all internet traffic strictly through the IPv4 tunnel."))

        SettingsSectionHeader(tr("بیشتر", "More"))
        SettingsGroup {
            SettingsRow(tr("ورود با حساب خودم", "Sign in with my own account"), Icons.Default.VpnKey, Ios.Orange, onClick = onManual)
            Separator()
            SettingsRow(tr("راهنمای ساخت حساب", "How to get an account"), Icons.Default.MenuBook, Ios.Teal, onClick = onGuide)
        }
        SettingsFooter(tr("اتصال با اطلاعات ورودی که برنامه از اینترنت می‌گیرد انجام می‌شود. اگر دریافت نشد یا رد شد، ایمیل و رمز خودتان پرسیده می‌شود.",
            "The connection uses sign-in details the app fetches online. If they cannot be fetched or are refused, you are asked for your own email and password."))
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun LiveGroup(connection: OpenVpnConnection, fa: Boolean) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var rates by remember { mutableStateOf(0L to 0L) }
    // Only while on screen; the rates are worked out from the counters, so pausing loses nothing.
    com.najishab.one.ui.LaunchedWhileVisible(Unit) {
        var lastRx = OpenVpnRuntime.connection.value.received
        var lastTx = OpenVpnRuntime.connection.value.sent
        var lastAt = System.currentTimeMillis()
        while (true) {
            delay(1000)
            val c = OpenVpnRuntime.connection.value
            val t = System.currentTimeMillis()
            val dt = (t - lastAt).coerceAtLeast(1)
            rates = ((c.received - lastRx).coerceAtLeast(0) * 1000 / dt) to ((c.sent - lastTx).coerceAtLeast(0) * 1000 / dt)
            lastRx = c.received; lastTx = c.sent; lastAt = t; now = t
        }
    }
    val exit = connection.exitCountry?.let { GeoLabel.countryFromCode(it, if (fa) Locale.forLanguageTag("fa") else Locale.ENGLISH) }
    SettingsGroup {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Figure(tr("دریافت", "Download"), rate(rates.first))
            Figure(tr("ارسال", "Upload"), rate(rates.second))
            Figure(tr("مدت", "Time"), duration(((now - (connection.connectedAt ?: now)) / 1000).coerceAtLeast(0)))
        }
        Separator()
        SettingsRow(
            title = tr("خروج از", "Exit"),
            value = exit?.let { "${it.flag}  ${it.name}" } ?: "—",
            showChevron = false,
        )
        connection.exitIp?.let {
            Separator()
            SettingsRow(title = tr("آدرسی که سایت‌ها می‌بینند", "Address sites see"), value = it, showChevron = false)
        }
        Separator()
        SettingsRow(
            title = tr("مصرف این اتصال", "This session"),
            value = "↓ ${bytes(connection.received)}   ↑ ${bytes(connection.sent)}",
            showChevron = false,
        )
    }
    SettingsFooter(tr("اتصال مستقیم OpenVPN با پروتکل انتخابی شما برقرار است.",
        "Direct OpenVPN connection is active with your selected protocol."))
}

@Composable
private fun Figure(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Ios.Label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
            style = TextStyle(textDirection = TextDirection.Ltr))
        Text(label, color = Ios.SecondaryLabel, fontSize = 12.sp)
    }
}

@Composable
private fun Banner(text: String, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp)).background(Ios.Orange.copy(alpha = 0.16f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Info, null, tint = Ios.Orange, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = Ios.Label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Default.Close, tr("بستن", "Close"), tint = Ios.SecondaryLabel,
            modifier = Modifier.size(20.dp).clickable(onClick = onDismiss))
    }
}

// ── servers ────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun ServersPage(
    data: OpenVpnData,
    connection: OpenVpnConnection,
    testing: Set<String>,
    testTotal: Int,
    fa: Boolean,
    onBack: () -> Unit,
    onTestAll: (String) -> Unit,
    onPick: (OpenVpnLocation, String) -> Unit,
    onFavorite: (OpenVpnLocation) -> Unit,
    onFastest: (String) -> Unit,
    onSelectMode: (String) -> Unit,
) {
    val udpPorts = remember(data.availableModes) {
        data.availableModes.filter { it.startsWith("UDP-") }.map { it.substringAfter("UDP-") }
            .ifEmpty { listOf("444", "1194", "1195") }
    }
    val tcpPorts = remember(data.availableModes) {
        data.availableModes.filter { it.startsWith("TCP-") }.map { it.substringAfter("TCP-") }
            .ifEmpty { listOf("443", "1195", "53") }
    }

    var selectedProto by rememberSaveable(data.selectedMode) {
        mutableStateOf(if (data.selectedMode.startsWith("TCP")) "TCP" else "UDP")
    }
    val currentPorts = if (selectedProto == "TCP") tcpPorts else udpPorts
    var selectedPort by rememberSaveable(data.selectedMode, selectedProto) {
        val portFromMode = data.selectedMode.substringAfter('-', "")
        mutableStateOf(if (portFromMode in currentPorts) portFromMode else currentPorts.first())
    }

    val activeMode = "$selectedProto-$selectedPort"
    var search by rememberSaveable { mutableStateOf("") }

    IosScreen(title = tr("سرورها", "Servers"), onBack = onBack, backLabel = "OpenVPN") {
        Spacer(Modifier.height(10.dp))
        SettingsGroup {
            SettingsActionRow(
                if (testing.isEmpty()) tr("سنجش تأخیر همه ($activeMode)", "Measure all ($activeMode)")
                else tr("توقف سنجش", "Stop measuring"),
                Icons.Default.Speed, Ios.Blue, enabled = !connection.active,
                onClick = { onTestAll(activeMode) },
            )
            Separator()
            SettingsActionRow(
                tr("اتصال به سریع‌ترین", "Connect to the fastest"), Icons.Default.Bolt, Ios.Green,
                enabled = testing.isEmpty(),
                onClick = { onFastest(activeMode) },
            )
        }
        if (testing.isNotEmpty() && testTotal > 0) {
            // How far the measurement has got, as a bar and a count: "12 of 47".
            val done = (testTotal - testing.size).coerceIn(0, testTotal)
            val shown by androidx.compose.animation.core.animateFloatAsState(done.toFloat() / testTotal, label = "ovpn-progress")
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("در حال سنجش…", "Measuring…"), color = Ios.SecondaryLabel, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(
                        if (fa) "${faDigits(done)} از ${faDigits(testTotal)}" else "$done of $testTotal",
                        color = Ios.SecondaryLabel, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Ios.Gray.copy(alpha = 0.25f))) {
                    Box(Modifier.fillMaxWidth(shown).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(Ios.Blue))
                }
            }
        }
        SettingsFooter(
            if (connection.active) tr("در حین اتصال سنجش ممکن نیست: از داخل تونل عدد درستی نمی‌دهد.", "Measuring is off while connected: from inside the tunnel it would not be the real figure.")
            else tr("هر سرور مثل خود اتصال سنجیده می‌شود: تا جایی که سرور جواب رمزنگاری (TLS) را واقعاً بفرستد. سروری که فقط جواب اولیه بدهد و بعد قطع شود، «مسدود» نشان داده می‌شود.",
                "Each server is measured the way the connection runs: until it really sends its TLS answer. A server that answers only the first packet and is then cut shows as \"blocked\".")
        )

        SettingsSectionHeader(tr("پروتکل", "Protocol"))
        SegmentedPicker(
            options = listOf("UDP", "TCP"),
            selected = selectedProto,
            onSelect = { proto ->
                selectedProto = proto
                val ports = if (proto == "TCP") tcpPorts else udpPorts
                if (selectedPort !in ports) {
                    selectedPort = ports.first()
                }
                onSelectMode("$proto-$selectedPort")
            },
        )

        Spacer(Modifier.height(6.dp))
        SettingsSectionHeader(tr("پورت", "Port"))
        SegmentedPicker(
            options = currentPorts,
            selected = selectedPort,
            onSelect = { port ->
                selectedPort = port
                onSelectMode("$selectedProto-$port")
            },
        )

        Spacer(Modifier.height(10.dp))
        SearchBox(search) { search = it }

        val now = System.currentTimeMillis()
        val filtered = data.locations.filter { l ->
            l.variants.containsKey(activeMode) &&
                (search.isBlank() || placeOf(l, fa).name.contains(search, true) || l.name.contains(search, true))
        }
        fun order(list: List<OpenVpnLocation>) = list.sortedWith(
            compareBy<OpenVpnLocation> { l -> l.probe?.takeIf { it.millis != null && now - it.checkedAt < 3_600_000 }?.millis ?: Long.MAX_VALUE }
                .thenBy { placeOf(it, fa).name }
        )
        val favorites = order(filtered.filter { it.favorite })
        val rest = order(filtered.filterNot { it.favorite })
        if (favorites.isNotEmpty()) {
            SettingsSectionHeader(tr("علاقه‌مندی‌ها", "Favourites"))
            SettingsGroup {
                favorites.forEachIndexed { i, l ->
                    if (i > 0) Separator()
                    ServerRow(
                        l = l,
                        fa = fa,
                        selected = l.id == data.selectedLocation && (data.selectedMode == activeMode || data.selectedMode == OpenVpnLocation.AUTO_MODE),
                        busy = l.id in testing,
                        activeMode = activeMode,
                        onClick = { onPick(l, activeMode) },
                        onFavorite = { onFavorite(l) },
                    )
                }
            }
        }
        val serverHeader = if (fa) "همهٔ سرورها (${faDigits(rest.size)})" else "All servers (${rest.size})"
        SettingsSectionHeader(serverHeader)
        if (rest.isEmpty()) SettingsFooter(tr("سروری برای این پروتکل و پورت پیدا نشد.", "No server matches this protocol and port."))
        else SettingsGroup {
            rest.forEachIndexed { i, l ->
                if (i > 0) Separator()
                ServerRow(
                    l = l,
                    fa = fa,
                    selected = l.id == data.selectedLocation && (data.selectedMode == activeMode || data.selectedMode == OpenVpnLocation.AUTO_MODE),
                    busy = l.id in testing,
                    activeMode = activeMode,
                    onClick = { onPick(l, activeMode) },
                    onFavorite = { onFavorite(l) },
                )
            }
        }
        SettingsFooter(
            if (fa) "سرورهای پروتکل $selectedProto با پورت $selectedPort در حال نمایش هستند. لمس هر سرور، اتصال را با همین پورت و پروتکل برقرار می‌کند."
            else "Showing $selectedProto servers on port $selectedPort. Tapping a server connects with this protocol and port."
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ServerRow(
    l: OpenVpnLocation,
    fa: Boolean,
    selected: Boolean,
    busy: Boolean,
    activeMode: String = OpenVpnLocation.AUTO_MODE,
    onClick: () -> Unit,
    onFavorite: (() -> Unit)?,
    chevron: Boolean = false,
) {
    val place = placeOf(l, fa)
    val probe = l.probe
    val fresh = probe != null && System.currentTimeMillis() - probe.checkedAt < 3_600_000
    val millis = probe?.millis
    val variant = l.profileFor(activeMode) ?: l.profileFor(OpenVpnLocation.AUTO_MODE)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(place.flag, fontSize = 26.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(place.name, color = Ios.Label, fontSize = 16.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
            val remote = variant?.remotes?.firstOrNull()
            val host = if (variant != null && ProfileRuntime.isTunnelBear(variant)) "TunnelBear" else remote?.host.orEmpty()
            val sub = if (remote != null) "$host · ${remote.protocol.uppercase()}:${remote.port}" else host
            Text(
                sub,
                color = Ios.SecondaryLabel, fontSize = 12.sp, maxLines = 1,
            )
        }
        when {
            busy -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Ios.SecondaryLabel)
            fresh && millis != null -> Text(
                "$millis ms", fontSize = 14.sp, fontWeight = FontWeight.Medium,
                color = when { millis < 350 -> Ios.Green; millis < 800 -> Ios.Orange; else -> Ios.Red },
                style = TextStyle(textDirection = TextDirection.Ltr),
            )
            fresh && probe?.error == "TLS_BLOCKED" -> Text(tr("مسدود", "Blocked"), fontSize = 13.sp, color = Ios.Red)
            fresh -> Text(tr("پاسخ نداد", "No answer"), fontSize = 13.sp, color = Ios.Red)
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.CheckCircle, null, tint = Ios.Blue, modifier = Modifier.size(20.dp))
        }
        if (onFavorite != null) {
            Spacer(Modifier.width(6.dp))
            Icon(
                if (l.favorite) Icons.Default.Star else Icons.Default.StarBorder,
                tr("علاقه‌مندی", "Favourite"),
                tint = if (l.favorite) Ios.Yellow else Ios.Gray,
                modifier = Modifier.size(24.dp).clickable(onClick = onFavorite),
            )
        }
        if (chevron) {
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Default.ChevronLeft, null, tint = Ios.Chevron, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SegmentedPicker(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Ios.Gray.copy(alpha = 0.18f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { opt ->
            val isSelected = opt == selected
            val bg by animateColorAsState(
                targetValue = if (isSelected) Ios.Blue else Color.Transparent,
                label = "seg_bg",
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) Color.White else Ios.Label,
                label = "seg_text",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(bg)
                    .clickable { onSelect(opt) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = opt,
                    color = textColor,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SearchBox(value: String, onChange: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp)).background(Ios.Gray.copy(alpha = 0.18f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, null, tint = Ios.SecondaryLabel, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = TextStyle(color = Ios.Label, fontSize = 16.sp),
            cursorBrush = SolidColor(Ios.Blue),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(tr("جست‌وجوی کشور", "Search a country"), color = Ios.SecondaryLabel, fontSize = 16.sp)
                    inner()
                }
            },
        )
    }
}

// ── protocol ───────────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProtocolsPage(
    data: OpenVpnData,
    onBack: () -> Unit,
    onPick: (String) -> Unit,
    onToggleSplitTcp: (Boolean) -> Unit,
    onToggleBlockIpv6: (Boolean) -> Unit,
) {
    val modes = listOf(OpenVpnLocation.AUTO_MODE) + data.availableModes
    IosScreen(title = tr("پروتکل", "Protocol"), onBack = onBack, backLabel = "OpenVPN") {
        Spacer(Modifier.height(10.dp))
        SettingsSectionHeader(tr("ضد فیلترینگ", "Anti-censorship"))
        SettingsGroup {
            SettingsToggle(
                title = tr("تکه‌تکه کردن TCP (ضد فیلترینگ)", "Split TCP (Anti-censorship)"),
                subtitle = tr("تکه‌تکه کردن بسته‌های اول Handshake روی TCP برای عبور از فیلترینگ DPI ایران",
                    "Fragment initial TCP handshake packets to bypass DPI censorship in Iran"),
                icon = Icons.Default.Security,
                tint = Ios.Blue,
                checked = data.splitTcp,
                onCheckedChange = onToggleSplitTcp,
            )
            Separator()
            SettingsToggle(
                title = tr("مسدودسازی IPv6", "Block IPv6"),
                subtitle = tr("غیرفعال‌سازی کامل IPv6 و عبور اجباری تمام ترافیک از طریق IPv4",
                    "Disable IPv6 completely and force all traffic through IPv4"),
                icon = Icons.Default.Block,
                tint = Ios.Orange,
                checked = data.blockIpv6,
                onCheckedChange = onToggleBlockIpv6,
            )
        }
        SettingsFooter(tr("این بخش شامل خرد کردن بسته‌های اولیه اتصال‌های TCP و مسدودسازی کامل ترافیک IPv6 برای عبور امن و بدون اختلال کل داده‌ها از شبکهٔ IPv4 است.",
            "This section fragments initial TCP packets and completely blocks IPv6 traffic to route all data reliably through IPv4."))

        SettingsSectionHeader(tr("پروتکل و پورت", "Protocol and port"))
        SettingsGroup {
            modes.forEachIndexed { i, m ->
                if (i > 0) Separator()
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(m) }.padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(OpenVpnLocation.formatModeLabel(m), color = Ios.Label, fontSize = 16.sp,
                        style = TextStyle(textDirection = TextDirection.Ltr), modifier = Modifier.weight(1f))
                    if (m == data.selectedMode) Icon(Icons.Default.CheckCircle, null, tint = Ios.Blue, modifier = Modifier.size(20.dp))
                }
            }
        }
        SettingsFooter(tr("«خودکار» پروتکل‌ها را به ترتیب امتحان می‌کند. پروتکلِ انتخاب‌شده اول امتحان می‌شود و در اتصال بعدی اعمال می‌شود.",
            "\"Auto\" tries the protocols in order. A chosen protocol is tried first and applies from the next connection."))
        Spacer(Modifier.height(32.dp))
    }
}

// ── manual sign-in ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CredentialPage(
    prompt: CredentialPrompt,
    fa: Boolean,
    message: String?,
    onDismissMessage: () -> Unit,
    onBack: () -> Unit,
    onGuide: () -> Unit,
    onSubmit: (String, String) -> Unit,
) {
    val context = LocalContext.current
    // No screenshots or recents thumbnail while a password is on screen.
    DisposableEffect(Unit) {
        val window = generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>().firstOrNull()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    var username by remember(prompt.prefillUsername) { mutableStateOf(prompt.prefillUsername) }
    // Never rememberSaveable: a password must not enter saved state or backups.
    var password by remember { mutableStateOf("") }
    IosScreen(title = tr("ورود", "Sign in"), onBack = onBack, backLabel = "OpenVPN") {
        Spacer(Modifier.height(10.dp))
        message?.let { Banner(errorText(it), onDismissMessage) }
        SettingsGroup {
            Field(tr("ایمیل", "Email"), username, { username = it }, KeyboardType.Email, secret = false)
            Separator()
            Field(tr("رمز", "Password"), password, { password = it }, KeyboardType.Password, secret = true)
        }
        SettingsFooter(
            when (prompt.reason) {
                CredentialPromptReason.GITHUB_UNAVAILABLE -> tr(
                    "اطلاعات ورود آماده دریافت نشد. ایمیل و رمز حساب TunnelBear خودتان را وارد کنید.",
                    "The ready-made sign-in details could not be fetched. Enter your own TunnelBear email and password.")
                CredentialPromptReason.AUTH_FAILED -> tr(
                    "ورود رد شد. ایمیل و رمز حساب TunnelBear خودتان را وارد کنید.",
                    "The sign-in was refused. Enter your own TunnelBear email and password.")
                CredentialPromptReason.MANUAL_ENTRY -> tr(
                    "همان ایمیل و رمزی که در سایت TunnelBear ساخته‌اید.",
                    "The email and password of your TunnelBear account.")
            } + " " + tr("رمز در حافظهٔ صفحه می‌ماند و جایی ذخیره نمی‌شود.", "The password stays in memory only and is not saved.")
        )
        SettingsGroup {
            SettingsActionRow(tr("اتصال", "Connect"), Icons.Default.CheckCircle, Ios.Blue,
                enabled = username.isNotBlank() && password.isNotEmpty(),
                onClick = { onSubmit(username.trim(), password); password = "" })
        }
        SettingsGroup {
            SettingsRow(tr("راهنمای ساخت حساب", "How to get an account"), Icons.Default.MenuBook, Ios.Teal, onClick = onGuide)
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, type: KeyboardType, secret: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Ios.Label, fontSize = 16.sp, modifier = Modifier.width(72.dp))
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = TextStyle(color = Ios.Label, fontSize = 16.sp, textDirection = TextDirection.Ltr),
            cursorBrush = SolidColor(Ios.Blue),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = type, autoCorrect = false),
            modifier = Modifier.weight(1f),
        )
    }
}

// ── guide ──────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun GuidePage(onBack: () -> Unit, onIran: () -> Unit, onStore: () -> Unit, onSite: () -> Unit) {
    val steps = listOf(
        tr("یکی از کانفیگ‌های «کانفیگ ایران» را وصل کنید تا سایت TunnelBear باز شود.", "Connect one of the «Iran config» configs so TunnelBear's site opens."),
        tr("در سایت TunnelBear حساب بسازید یا وارد شوید و ایمیل را تأیید کنید.", "Create or sign in to a TunnelBear account and confirm the email."),
        tr("به اینجا برگردید و هنگام درخواست، ایمیل و رمز را وارد کنید.", "Come back and enter the email and password when asked."),
        tr("در «سرور» تأخیر همه را بسنجید و سریع‌ترین را بزنید.", "Under «Server», measure all and pick the fastest."),
    )
    val digits = if (tr("fa", "en") == "fa") "۱۲۳۴" else "1234"
    IosScreen(title = tr("راهنما", "Guide"), onBack = onBack, backLabel = "OpenVPN") {
        Spacer(Modifier.height(10.dp))
        SettingsGroup {
            steps.forEachIndexed { i, s ->
                if (i > 0) Separator()
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(26.dp).clip(RoundedCornerShape(13.dp)).background(OpenVpnOrange), contentAlignment = Alignment.Center) {
                        Text(digits[i].toString(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(s, color = Ios.Label, fontSize = 15.sp, modifier = Modifier.weight(1f))
                }
            }
        }
        SettingsFooter(tr("سهمیه و شرایط هر حساب را خود TunnelBear تعیین می‌کند. اندروید فقط یک VPN را هم‌زمان نگه می‌دارد؛ اتصال OpenVPN اتصال قبلی را جایگزین می‌کند.",
            "TunnelBear sets each account's quota and terms. Android keeps one VPN at a time; connecting OpenVPN replaces the previous one."))
        SettingsGroup {
            SettingsRow(tr("باز کردن «کانفیگ ایران»", "Open «Iran config»"), Icons.Default.Flag, Ios.Green, onClick = onIran)
            Separator()
            SettingsActionRow(tr("صفحهٔ حساب TunnelBear", "TunnelBear account page"), Icons.Default.OpenInNew, Ios.Teal, onClick = onSite)
            Separator()
            SettingsRow(tr("به‌روزرسانی هسته از استور", "Update the core from the Store"), Icons.Default.SystemUpdate, Ios.Blue, onClick = onStore)
        }
        Spacer(Modifier.height(32.dp))
    }
}

// ── words ──────────────────────────────────────────────────────────────────────────────────────

private fun headline(c: OpenVpnConnection, fetching: Boolean): String = when {
    fetching -> tr("در حال آماده‌سازی…", "Getting ready…")
    else -> when (c.phase) {
        ConnectionPhase.CONNECTED -> tr("متصل", "Connected")
        ConnectionPhase.CONNECTING -> tr("در حال اتصال…", "Connecting…")
        ConnectionPhase.RECONNECTING -> tr("اتصال دوباره…", "Reconnecting…")
        ConnectionPhase.DISCONNECTING -> tr("در حال قطع…", "Disconnecting…")
        ConnectionPhase.ERROR -> tr("وصل نشد", "Could not connect")
        else -> tr("آماده", "Ready")
    }
}

private fun rate(bytesPerSecond: Long): String = when {
    bytesPerSecond >= 1_000_000 -> "%.1f MB/s".format(Locale.US, bytesPerSecond / 1e6)
    bytesPerSecond >= 1_000 -> "%.0f KB/s".format(Locale.US, bytesPerSecond / 1e3)
    else -> "$bytesPerSecond B/s"
}

private fun duration(s: Long): String = if (s >= 3600) "%d:%02d:%02d".format(Locale.US, s / 3600, s / 60 % 60, s % 60)
    else "%02d:%02d".format(Locale.US, s / 60, s % 60)

private fun bytes(n: Long) = if (n >= 1_000_000_000) "%.2f GB".format(Locale.US, n / 1e9) else "%.1f MB".format(Locale.US, n / 1e6)

private fun errorText(code: String): String = when {
    code.startsWith("IMPORTED:") -> tr("پروفایل تازه: ", "New profiles: ") + code.removePrefix("IMPORTED:")
    code == "TEST_FIRST" -> tr("اول تأخیر سرورها را بسنجید؛ نتیجه‌ها باید مال ده دقیقهٔ اخیر باشند.", "Measure the servers first; results must be from the last ten minutes.")
    code == "TEST_FAILED" -> tr("سنجش تأخیر انجام نشد.", "The delay measurement failed.")
    code == "AUTH_FAILED" -> tr("ورود رد شد. ایمیل، رمز و تأیید ایمیل حساب را در سایت TunnelBear بررسی کنید.", "Sign-in refused. Check the email, password and email confirmation on TunnelBear's site.")
    code == "VPN_PERMISSION_DENIED" -> tr("اجازهٔ VPN داده نشد؛ اتصال قبلی دست نخورد.", "VPN permission was not given; the previous connection was left alone.")
    code == "NO_RESPONSE" -> tr("پاسخ نداد", "No answer")
    code == "NO_DATA" -> tr("دست‌دادن کامل شد ولی داده‌ای از تونل رد نشد؛ سرور دیگری را امتحان کنید.", "The handshake finished but no data crossed the tunnel; try another server.")
    code == "SAVE_FAILED" -> tr("ذخیره نشد؛ اگر این حساب وصل است، اول قطع کنید.", "Could not save; if this account is connected, disconnect first.")
    code.startsWith("IMPORT_FAILED") -> tr("فایل خوانده نشد: ", "Could not read the file: ") + code.removePrefix("IMPORT_FAILED").removePrefix(":")
    code == "PROFILE_UNSUPPORTED" -> tr("هسته این پروفایل را پشتیبانی نمی‌کند.", "The core does not support this profile.")
    code == "CORE_UNAVAILABLE" -> tr("هستهٔ OpenVPN بارگذاری نشد؛ استور را بررسی کنید.", "The OpenVPN core could not load; check the Store.")
    code == "CONNECT_DEADLINE" -> tr("این سرور در زمان مشخص وصل نشد و اتصال لغو شد. سرور دیگری را امتحان کنید.", "This server did not connect in time, so the attempt was stopped. Try another server.")
    code == "CONNECTION_TIMEOUT" -> tr("سرور جواب داد ولی دست‌دادن کامل نشد. سرور دیگری را امتحان کنید.", "The server answered but the handshake did not finish. Try another server.")
    code == "ACCOUNT_UNAVAILABLE" -> tr("این حساب الان قابل استفاده نیست.", "This account cannot be used right now.")
    else -> tr("وصل نشد: ", "Could not connect: ") + code
}

/** Persian digits for a count shown in Persian text. */
private fun faDigits(n: Int): String = n.toString().map { c -> if (c in '0'..'9') '۰' + (c - '0') else c }.joinToString("")
