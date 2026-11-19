package com.najishab.one.ui.freedom

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.najishab.one.store.tr
import com.najishab.one.ui.LocalSystemBottomPadding
import com.najishab.one.ui.LocalSystemTopPadding
import com.najishab.one.ui.home.HomeApp
import com.najishab.one.ui.home.HomeDestinations
import com.najishab.one.ui.nexus.AppIconMark
import com.najishab.one.ui.nexus.NexusCatalog
import com.najishab.one.ui.nexus.NexusSession
import com.najishab.one.ui.skin.FreedomPrefs
import com.najishab.one.ui.skin.LinkState
import com.najishab.one.ui.skin.rememberLinkController
import com.najishab.one.ui.tunnel.TunnelController
import com.najishab.one.widget.WidgetFormat
import com.najishab.one.widget.WidgetLive
import com.najishab.one.widget.WidgetMethods
import com.najishab.one.widget.WidgetPainter
import kotlinx.coroutines.delay

/**
 * Freedom Home. The three protocol cards are the three MOST RECENTLY USED methods (live one first),
 * not a fixed trio; the tick marks the method actually live/connecting, else the chosen favourite.
 * The country row shows the REAL public IP when idle and the EXIT IP when connected. Data is real.
 */
@Composable
fun FreedomHome(
    session: NexusSession,
    onOpen: (HomeApp) -> Unit,
    onPage: (FreedomPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val conn = session.conn
    val traffic by WidgetLive.traffic.collectAsStateWithLifecycle()
    val tunnel by TunnelController.state.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        WidgetLive.wantCountry("freedom", true)
        onDispose { WidgetLive.wantCountry("freedom", false) }
    }
    val link = rememberLinkController(
        session,
        onChooseMethod = { onPage(FreedomPage.ENGINES) },
        methodOverride = FreedomPrefs.preferredMethod,
    )
    val state = link.state

    // Remember what actually connected, so the cards become the last three used.
    LaunchedEffect(conn.up, conn.method) {
        val m = conn.method
        if (conn.up && m != null && WidgetMethods.isMethod(m)) FreedomPrefs.noteUsed(context, m)
    }

    val activeId = conn.method?.takeIf { WidgetMethods.isMethod(it) }
    val engineName = WidgetMethods.app(activeId ?: link.last ?: session.shown)?.let { WidgetPainter.labelOf(it) }
    val engineCount = remember { NexusCatalog.methodGroups().sumOf { it.apps.size } }
    // The tick follows reality: the live/connecting method, else the favourite, else the last used.
    val selectedId = if (conn.busy) activeId else (FreedomPrefs.preferredMethod ?: session.last)
    val displayIds = remember(activeId, FreedomPrefs.recentMethods) {
        (listOfNotNull(activeId) + FreedomPrefs.recentMethods + WidgetMethods.IDS.filter { WidgetMethods.isMethod(it) })
            .distinct().take(3)
    }
    val ipInfo = rememberFreedomIp(conn, tunnel)

    val top = LocalSystemTopPadding.current
    val bottom = LocalSystemBottomPadding.current
    BoxWithConstraints(modifier.fillMaxSize().freedomBackdrop()) {
        val compact = maxHeight < 640.dp || LocalDensity.current.fontScale > 1.15f
        Column(
            Modifier
                .fillMaxSize()
                .then(if (compact) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(top = top, bottom = bottom + FREEDOM_NAV_RESERVE)
                .padding(horizontal = 18.dp)
        ) {
            FreedomHeader(state, engineName, onSupport = { HomeDestinations.gridById("tutorial")?.let(onOpen) })
            FreedomScene(
                state,
                Modifier.fillMaxWidth().then(
                    if (compact) Modifier.height(340.dp) else Modifier.weight(1f).heightIn(min = 260.dp)
                ),
            )
            FreedomStatus(state, conn, engineName)
            ConnectButton(state, link.onPower)
            ProtocolSection(
                displayIds = displayIds,
                selectedId = selectedId,
                count = engineCount,
                onAll = { onPage(FreedomPage.ENGINES) },
                onPick = { id ->
                    if (WidgetMethods.isMethod(id)) {
                        FreedomPrefs.set(context, id)
                        val nm = WidgetMethods.app(id)?.let { WidgetPainter.labelOf(it) } ?: id
                        android.widget.Toast.makeText(context, tr("پروتکل انتخاب شد: $nm", "Protocol selected: $nm"), android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        WidgetMethods.app(id)?.let(onOpen)
                    }
                },
            )
            SpeedCard(state, traffic)
            CountryRow(state, conn, ipInfo, tunnel.country)
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun FreedomHeader(state: LinkState, engine: String?, onSupport: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIconMark(40.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("NajiOne VPN", color = Freedom.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                if (state == LinkState.ON) {
                    if (engine != null) tr("محافظت‌شده · $engine", "Protected · $engine") else tr("محافظت‌شده", "Protected")
                } else tr("محافظت‌نشده", "Not protected"),
                color = if (state == LinkState.ON) Freedom.Ok else Freedom.Muted,
                fontSize = 11.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        val shape = RoundedCornerShape(50)
        Row(
            Modifier.height(44.dp).clip(shape).background(Freedom.Card).border(1.dp, Freedom.Border, shape)
                .clickable(role = Role.Button, onClick = onSupport).padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(tr("پشتیبانی", "Support"), color = Freedom.ChipText, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FreedomStatus(state: LinkState, conn: WidgetLive.Conn, engine: String?) {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(conn.up, conn.since) {
        while (conn.up) {
            now.longValue = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val title = buildAnnotatedString {
        when (state) {
            LinkState.OFF -> {
                append(tr("هنوز در ", "Still in the "))
                withStyle(SpanStyle(color = Freedom.Amber)) { append(tr("قفس", "cage")) }
            }
            LinkState.CONNECTING -> {
                append(tr("در حال شکستن ", "Breaking the "))
                withStyle(SpanStyle(color = Freedom.Amber)) { append(tr("قفس", "cage")) }
                append("…")
            }
            LinkState.ON -> {
                append(tr("آزاد — ", "Free — "))
                withStyle(SpanStyle(color = Freedom.Ok)) { append(tr("محافظت می‌شوید", "you are protected")) }
            }
            LinkState.ERROR -> withStyle(SpanStyle(color = Freedom.TitleDanger)) { append(tr("اتصال ناموفق", "Connection failed")) }
        }
    }
    val sub = when (state) {
        LinkState.OFF -> if (engine != null) tr("برای رهایی اتصال بزنید — با $engine", "Tap connect to break free — via $engine")
            else tr("یک پروتکل انتخاب کنید و اتصال بزنید", "Pick a protocol, then tap connect")
        LinkState.CONNECTING -> tr("پرنده به میله‌ها فشار می‌آورد", "The bird is pushing on the bars")
        LinkState.ON -> if (engine != null) tr("متصل با $engine — پرنده بیرون است", "Connected via $engine · the bird is out")
            else tr("متصل — پرنده بیرون است", "Connected · the bird is out")
        LinkState.ERROR -> tr("قفس دوام آورد — دوباره تلاش کنید یا پروتکل را عوض کنید", "The cage held — try again or switch protocol")
    }
    Column(Modifier.fillMaxWidth().padding(top = 9.dp)) {
        Text(title, color = Freedom.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2)
        Text(sub, color = Freedom.Muted, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, maxLines = 2, modifier = Modifier.padding(top = 2.dp))
        // Always occupy the timer line, so the scene above does not shrink the moment we connect.
        Text(
            if (state == LinkState.ON) WidgetFormat.clock((now.longValue - conn.since).coerceAtLeast(0L)) else "",
            color = Color(0xFFE7EDF5), fontSize = 13.sp, fontWeight = FontWeight.Bold,
            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
            modifier = Modifier.padding(top = 2.dp).heightIn(min = 18.dp),
        )
    }
}

@Composable
private fun ConnectButton(state: LinkState, onPower: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, tween(120), label = "press")
    val bg = when (state) {
        LinkState.ON -> Freedom.OnBg
        LinkState.ERROR -> Freedom.ErrorBg
        else -> Freedom.Primary
    }
    val fg = when (state) {
        LinkState.ON -> Freedom.OnText
        LinkState.ERROR -> Freedom.ErrorText
        else -> Freedom.ConnectText
    }
    val border = when (state) {
        LinkState.ON -> Freedom.OnBorder
        LinkState.ERROR -> Freedom.ErrorBorder
        else -> Color.Transparent
    }
    val label = when (state) {
        LinkState.OFF -> tr("اتصال", "Connect")
        LinkState.CONNECTING -> tr("در حال اتصال…", "Connecting…")
        LinkState.ON -> tr("قطع اتصال", "Disconnect")
        LinkState.ERROR -> tr("تلاش دوباره", "Try again")
    }
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier.fillMaxWidth().padding(top = 11.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape).background(if (state == LinkState.CONNECTING) bg.copy(alpha = 0.78f) else bg)
            .border(1.dp, border, shape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = label, onClick = onPower)
            .heightIn(min = 52.dp).padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = fg, fontSize = 16.5.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun ProtocolSection(displayIds: List<String>, selectedId: String?, count: Int, onAll: () -> Unit, onPick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("پروتکل اتصال", "Connection protocol"), color = Freedom.Text, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                Text(tr("سه روش آخرِ استفاده‌شده — برای تغییر، کارت را لمس کنید", "Your last three used — tap a card to switch"), color = Freedom.Muted, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                WidgetFormat.digits(tr("همهٔ $count موتور ›", "All $count engines ›")),
                color = Freedom.Ok, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onAll).padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            displayIds.forEach { id ->
                val app = WidgetMethods.app(id)
                if (app != null) ProtoCard(app, selected = id == selectedId, modifier = Modifier.weight(1f)) { onPick(id) }
            }
        }
    }
}

@Composable
private fun ProtoCard(app: HomeApp, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val name = WidgetPainter.labelOf(app)
    val tint = app.tint
    val iconBg = app.tint.copy(alpha = 0.16f)
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier.clip(shape).background(if (selected) Freedom.Card2 else Freedom.Card)
            .border(1.dp, if (selected) Freedom.Ok else Freedom.Border, shape)
            .semantics { this.selected = selected; this.role = Role.RadioButton }
            .clickable(role = Role.RadioButton, onClick = onClick).padding(10.dp)
    ) {
        Column {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(iconBg), contentAlignment = Alignment.Center) {
                Text(name.take(2).uppercase(), color = tint, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
            Text(name, color = Freedom.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
        }
        if (selected) {
            Box(Modifier.align(Alignment.TopEnd).size(17.dp).clip(CircleShape).background(Freedom.Ok), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF06231A), modifier = Modifier.size(12.dp))
            }
        }
    }
}

@Composable
private fun SpeedCard(state: LinkState, traffic: WidgetLive.Traffic) {
    val up = state == LinkState.ON
    val reduced = rememberFreedomReducedMotion()
    val blink = if (up && !reduced) {
        rememberInfiniteTransition(label = "speed dot")
            .animateFloat(0.35f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "blink").value
    } else 1f
    Column(Modifier.fillMaxWidth().padding(top = 9.dp).freedomCard(RoundedCornerShape(18.dp)).padding(horizontal = 13.dp, vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(if (up) Freedom.Ok.copy(alpha = blink) else Freedom.ChipDot))
            Spacer(Modifier.width(6.dp))
            Text(tr("سرعت زنده", "Live speed"), color = Freedom.Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            SpeedCol(tr("دانلود", "Download"), Icons.Default.Download, Freedom.Ok, Color(0xFF0D2B21), if (up) traffic.rxRate else 0L, if (up) traffic.down else LongArray(0), Modifier.weight(1f).padding(end = 12.dp))
            Box(Modifier.width(1.dp).height(50.dp).background(Freedom.Border))
            SpeedCol(tr("آپلود", "Upload"), Icons.Default.Upload, Freedom.Sky, Color(0xFF12283F), if (up) traffic.txRate else 0L, if (up) traffic.up else LongArray(0), Modifier.weight(1f).padding(start = 12.dp))
        }
    }
}

@Composable
private fun SpeedCol(label: String, icon: ImageVector, tint: Color, iconBg: Color, rate: Long, samples: LongArray, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(20.dp).clip(RoundedCornerShape(7.dp)).background(iconBg), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(11.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(label, color = Freedom.Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
        }
        Text(WidgetFormat.rate(rate), color = Freedom.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"), modifier = Modifier.padding(top = 2.dp))
        val path = remember { Path() }
        Canvas(Modifier.fillMaxWidth().padding(top = 2.dp).height(14.dp)) {
            val n = samples.size.coerceAtMost(40)
            if (n < 2) return@Canvas
            var max = 1L
            for (i in samples.size - n until samples.size) if (samples[i] > max) max = samples[i]
            path.rewind()
            for (i in 0 until n) {
                val v = samples[samples.size - n + i].toFloat()
                val x = i / 39f * size.width
                val y = size.height - 2.dp.toPx() - (v / max) * (size.height - 5.dp.toPx())
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, tint, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
private fun CountryRow(state: LinkState, conn: WidgetLive.Conn, ip: FreedomIpInfo, tunnelCountry: String) {
    val up = state == LinkState.ON
    val cc = conn.country ?: ip.country ?: tunnelCountry.takeIf { it.isNotBlank() }
    val left = when {
        cc != null -> WidgetFormat.flag(cc) + " " + WidgetFormat.countryName(cc)
        up -> tr("در حال یافتن کشور خروج…", "Finding exit country…")
        else -> tr("موقعیت فعلی شما", "Your current location")
    }
    // Real public IP when idle; the exit IP when connected. Never a fabricated address.
    val right = when {
        ip.ip != null -> ip.ip
        ip.loading -> "…"
        else -> tr("نامشخص", "Unknown")
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 9.dp).freedomCard(RoundedCornerShape(16.dp)).padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(left, color = Freedom.Text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(right, color = if (up) Freedom.Ok else Freedom.Text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"))
    }
}