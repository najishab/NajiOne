package com.najishab.one.ui.nexus

import android.app.Activity
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.core.content.ContextCompat
import com.najishab.one.R
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.najishab.one.store.tr
import com.najishab.one.ui.LocalSystemBottomPadding
import com.najishab.one.ui.LocalSystemTopPadding
import com.najishab.one.ui.home.HomeApp
import com.najishab.one.widget.WidgetConnect
import com.najishab.one.widget.WidgetConnectService
import com.najishab.one.widget.WidgetFormat
import com.najishab.one.widget.WidgetLive
import com.najishab.one.widget.WidgetMethods
import com.najishab.one.widget.WidgetPainter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** What the NEXUS Home shows. ERROR is a failed attempt, never confused with a live link. */
internal enum class LinkState { OFF, CONNECTING, ON, ERROR }

/** Height the shell's floating bottom bar takes, so a page's content can clear it. */
internal val NEXUS_NAV_RESERVE = 104.dp

@Composable
fun NexusHome(
    session: NexusSession,
    onOpen: (HomeApp) -> Unit,
    onChooseMethod: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val conn = session.conn
    val last = session.last
    // Collected only while this page is on screen: collecting is what keeps the 1 s sampler going.
    val traffic by WidgetLive.traffic.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        WidgetLive.wantCountry("nexus", true)
        onDispose { WidgetLive.wantCountry("nexus", false) }
    }
    val app = WidgetMethods.app(session.shown)

    // ERROR: a connect that ended without ever reaching CONNECTED and that the user did not cancel.
    // Derived from the real phase flow; cleared on the next attempt, or after a few seconds.
    var failed by remember { mutableStateOf(false) }
    var userStopped by remember { mutableStateOf(false) }
    var prevPhase by remember { mutableStateOf(conn.phase) }
    LaunchedEffect(conn.phase) {
        if (prevPhase == WidgetLive.Phase.CONNECTING && conn.phase == WidgetLive.Phase.IDLE && !userStopped) failed = true
        if (conn.phase != WidgetLive.Phase.IDLE) failed = false
        prevPhase = conn.phase
    }
    LaunchedEffect(failed) {
        if (failed) {
            delay(12_000)
            failed = false
        }
    }
    val state = when {
        conn.up -> LinkState.ON
        conn.busy -> LinkState.CONNECTING
        failed -> LinkState.ERROR
        else -> LinkState.OFF
    }

    // The existing connect path, unchanged: VPN consent if needed, then the widget service connects
    // the last method. Nothing here knows about any engine.
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val m = last
        if (res.resultCode == Activity.RESULT_OK && m != null) WidgetConnectService.start(context, m, null)
    }
    val scope = rememberCoroutineScope()
    fun connectLast() {
        val m = last ?: return
        if (com.najishab.one.data.ScanGuard.isScanBusy()) {
            android.widget.Toast.makeText(
                context,
                tr("یک اسکن در حال اجراست؛ اول آن را متوقف کنید", "A scan is running; stop it first"),
                android.widget.Toast.LENGTH_LONG,
            ).show()
            return
        }
        userStopped = false
        failed = false
        scope.launch {
            val prep = withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { VpnService.prepare(context) }.getOrNull() }
            if (prep != null) runCatching { consent.launch(prep) } else WidgetConnectService.start(context, m, null)
        }
    }
    val onPower: () -> Unit = {
        when {
            conn.busy -> {
                userStopped = true
                WidgetConnect.disconnect(context)
            }
            last != null -> connectLast()
            else -> onChooseMethod()
        }
    }

    val top = LocalSystemTopPadding.current
    val bottom = LocalSystemBottomPadding.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .nexusBackdrop()
            .padding(top = top, bottom = bottom + NEXUS_NAV_RESERVE)
            .padding(horizontal = 16.dp),
    ) {
        NexusHeader(state)
        NexusHero(
            state = state,
            hasMethod = last != null || conn.method != null,
            onPower = onPower,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        Spacer(Modifier.height(10.dp))
        NexusStatusCapsule(
            state = state,
            conn = conn,
            app = app,
            onClick = { if (app != null) onOpen(app) else onChooseMethod() },
        )
        Spacer(Modifier.height(10.dp))
        NexusNetworkCard(state = state, conn = conn, traffic = traffic)
        Spacer(Modifier.height(6.dp))
    }
}

// ───────────────────────────── header ─────────────────────────────

@Composable
private fun NexusHeader(state: LinkState) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The app's own launcher icon. Its size derives from sp, so it grows with the system font
        // size like the two lines of text beside it, then is bounded so it neither dominates the row
        // on large-text settings nor looks lost on small ones.
        val rawIcon = with(LocalDensity.current) { 46.sp.toDp() }
        val iconSize = if (rawIcon < 42.dp) 42.dp else if (rawIcon > 58.dp) 58.dp else rawIcon
        AppIconMark(iconSize)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.width(1.dp).height(iconSize).background(Nexus.Muted.copy(alpha = 0.35f)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Nexus.Text)) { append("NAJI ") }
                    withStyle(SpanStyle(color = Nexus.Blue)) { append("VPN") }
                },
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                maxLines = 1,
            )
            Text(
                "Secure • Fast • Free",
                color = Nexus.Muted,
                fontSize = 13.sp,
                maxLines = 1,
            )
        }
        val dot = stateColor(state)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, dot.copy(alpha = 0.35f), RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(7.dp))
            Text(stateWord(state), color = Nexus.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

private fun stateColor(state: LinkState): Color = when (state) {
    LinkState.OFF -> Nexus.Muted
    LinkState.CONNECTING -> Nexus.Amber
    LinkState.ON -> Nexus.Mint
    LinkState.ERROR -> Nexus.Danger
}

private fun stateWord(state: LinkState): String = when (state) {
    LinkState.OFF -> tr("آفلاین", "Offline")
    LinkState.CONNECTING -> tr("در حال اتصال", "Connecting")
    LinkState.ON -> tr("امن", "Protected")
    LinkState.ERROR -> tr("خطا", "Error")
}

/**
 * The app's launcher icon (@mipmap/ic_launcher), drawn at exactly [size].
 *
 * It is rasterised through the Drawable API rather than painterResource: on API 26+ the launcher
 * icon is an adaptive icon, which painterResource cannot load (it only accepts vector and bitmap
 * resources) and would crash. The bitmap is built at the real pixel size of [size] on this screen,
 * so it is sharp at every density and is rebuilt only when that pixel size changes.
 */
@Composable
private fun AppIconMark(size: Dp) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(1)
    val bitmap = remember(px) {
        runCatching {
            val d = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)?.mutate()
                ?: return@runCatching null
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, px, px)
            d.draw(android.graphics.Canvas(bmp))
            bmp.asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(size))
    } else {
        Spacer(Modifier.size(size))
    }
}

// ───────────────────────────── hero ─────────────────────────────

@Composable
private fun NexusHero(state: LinkState, hasMethod: Boolean, onPower: () -> Unit, modifier: Modifier = Modifier) {
    val target = when (state) {
        LinkState.OFF -> 0f
        LinkState.CONNECTING -> 0.55f
        LinkState.ON -> 1f
        LinkState.ERROR -> 0.22f
    }
    val energy = animateFloatAsState(target, tween(900, easing = FastOutSlowInEasing), label = "nexus energy")
    val connectingW = animateFloatAsState(if (state == LinkState.CONNECTING) 1f else 0f, tween(600), label = "nexus connecting")
    val inf = rememberInfiniteTransition(label = "nexus world")
    val slow = inf.animateFloat(0f, 360f, infiniteRepeatable(tween(36_000, easing = LinearEasing)), label = "slow")
    val fast = inf.animateFloat(0f, 360f, infiniteRepeatable(tween(3_200, easing = LinearEasing)), label = "fast")
    val pulse = inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2_600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val twinkle = inf.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(5_000, easing = LinearEasing)), label = "twinkle")
    val lights = remember { buildLights() }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press = animateFloatAsState(if (pressed) 0.94f else 1f, tween(120), label = "press")

    BoxWithConstraints(modifier = modifier) {
        val rDp = minOf(maxWidth * 0.20f, maxHeight * 0.26f)
        Canvas(Modifier.fillMaxSize().clipToBounds()) {
            val w = size.width
            val h = size.height
            val c = Offset(w / 2f, h * 0.52f)
            val r = rDp.toPx()
            val e = energy.value
            val gr = min(w * 0.50f, h * 0.60f)
            val tint = ringTint(state)
            drawGlobe(Offset(w * 0.64f, h * 0.30f), gr, slow.value, e, tint, lights, twinkle.value)
            val angle = slow.value * 6f + connectingW.value * fast.value
            drawRings(c, r, w, angle, e, state)
            drawPowerButton(c, r, e, state, pulse.value, press.value)
        }
        // The slogan, with the accent rule of the reference.
        Row(Modifier.align(Alignment.TopStart).padding(top = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(2.dp).height(40.dp).background(
                    Brush.verticalGradient(listOf(Nexus.Cyan, Nexus.Blue.copy(alpha = 0.2f)))
                )
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Your Privacy", color = Nexus.Muted, fontSize = 16.sp)
                Text("Our Priority", color = Nexus.Muted, fontSize = 16.sp)
            }
        }
        // The real, tappable button sits exactly on the drawn one.
        val label = when (state) {
            LinkState.OFF -> if (hasMethod) tr("برای اتصال لمس کنید", "Tap to Connect") else tr("یک روش انتخاب کنید", "Choose a Method")
            LinkState.CONNECTING -> tr("در حال اتصال… لمس برای لغو", "Connecting… tap to cancel")
            LinkState.ON -> tr("متصل — لمس برای قطع", "Connected — tap to disconnect")
            LinkState.ERROR -> tr("اتصال ناموفق — دوباره تلاش کنید", "Connection failed — tap to retry")
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = maxHeight * 0.52f - rDp)
                .size(rDp * 2f)
                .clip(CircleShape)
                .clickable(interactionSource = interaction, indication = null, onClickLabel = label, role = Role.Button, onClick = onPower),
        )
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, color = Nexus.Text, fontSize = 20.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier.width(150.dp).height(2.dp).background(
                    Brush.horizontalGradient(listOf(Color.Transparent, stateColor(state).let { if (state == LinkState.OFF) Nexus.Blue else it }, Color.Transparent))
                )
            )
        }
    }
}

private fun ringTint(state: LinkState): Color = when (state) {
    LinkState.ERROR -> Nexus.Danger
    LinkState.ON -> Nexus.Cyan
    else -> Nexus.Blue
}

private class Light(val lat: Float, val lon: Float, val size: Float, val phase: Float)

private fun buildLights(): List<Light> {
    val rnd = java.util.Random(11)
    val hubs = arrayOf(
        floatArrayOf(50f, 10f), floatArrayOf(40f, -90f), floatArrayOf(25f, 80f),
        floatArrayOf(35f, 120f), floatArrayOf(-15f, -55f), floatArrayOf(5f, 20f),
    )
    val out = ArrayList<Light>(260)
    for (i in 0 until 260) {
        val hub = hubs[rnd.nextInt(hubs.size)]
        val lat = (hub[0] + rnd.nextGaussian().toFloat() * 11f).coerceIn(-70f, 75f)
        val lon = hub[1] + rnd.nextGaussian().toFloat() * 16f
        out.add(Light(lat, lon, 0.6f + rnd.nextFloat() * 1.4f, rnd.nextFloat() * 6.28f))
    }
    return out
}

private fun DrawScope.drawGlobe(gc: Offset, gr: Float, rotDeg: Float, energy: Float, tint: Color, lights: List<Light>, twinkle: Float) {
    val rot = (rotDeg * PI / 180.0).toFloat()
    // The body: deep blue, lit from the upper left.
    drawCircle(
        brush = Brush.radialGradient(
            listOf(
                Color(0xFF16408F).copy(alpha = 0.55f + 0.25f * energy),
                Color(0xFF071236).copy(alpha = 0.90f),
                Color(0xFF02030A),
            ),
            center = Offset(gc.x - gr * 0.25f, gc.y - gr * 0.30f),
            radius = gr * 1.25f,
        ),
        radius = gr,
        center = gc,
    )
    // Latitude and meridian lines, faint; brighter as the link comes up.
    val line = tint.copy(alpha = 0.05f + 0.10f * energy)
    val thin = Stroke(1.dp.toPx())
    for (latDeg in intArrayOf(-40, -20, 0, 20, 40, 60)) {
        val lat = (latDeg * PI / 180.0).toFloat()
        val y = gc.y - gr * sin(lat)
        val rx = gr * cos(lat)
        val ry = rx * 0.10f
        drawOval(line, topLeft = Offset(gc.x - rx, y - ry), size = Size(rx * 2f, ry * 2f), style = thin)
    }
    for (k in 0 until 6) {
        val lon = rot + k * (PI.toFloat() / 6f)
        val rx = gr * kotlin.math.abs(sin(lon))
        drawOval(line, topLeft = Offset(gc.x - rx, gc.y - gr), size = Size(rx * 2f, gr * 2f), style = thin)
    }
    // City lights: only the facing hemisphere, twinkling, brighter when connected.
    for (p in lights) {
        val latR = (p.lat * PI / 180.0).toFloat()
        val lonR = (p.lon * PI / 180.0).toFloat() + rot
        val cz = cos(latR) * cos(lonR)
        if (cz <= 0.05f) continue
        val x = gc.x + gr * cos(latR) * sin(lonR)
        val y = gc.y - gr * sin(latR)
        val a = cz * (0.30f + 0.70f * energy) * (0.65f + 0.35f * sin(twinkle + p.phase))
        drawCircle(Color(0xFFFFD58A).copy(alpha = a.coerceIn(0f, 1f)), radius = p.size * 1.1.dp.toPx(), center = Offset(x, y))
    }
    // The rim light.
    drawCircle(
        brush = Brush.sweepGradient(
            listOf(tint.copy(alpha = 0f), tint.copy(alpha = 0.35f + 0.5f * energy), Color.White.copy(alpha = 0.55f), tint.copy(alpha = 0f)),
            center = gc,
        ),
        radius = gr,
        center = gc,
        style = Stroke(2.dp.toPx()),
    )
}

private fun DrawScope.drawRings(c: Offset, r: Float, w: Float, angle: Float, energy: Float, state: LinkState) {
    val colors = arrayOf(Nexus.Cyan, Nexus.Blue, Nexus.Violet)
    val tilts = floatArrayOf(-16f, 20f, 4f)
    val ks = floatArrayOf(2.15f, 1.85f, 2.5f)
    val dir = floatArrayOf(1f, -1.3f, 0.7f)
    for (i in 0..2) {
        val rx = min(r * ks[i], w * 0.485f)
        val ry = rx * (0.30f + 0.06f * i)
        val col = if (state == LinkState.ERROR) lerp(colors[i], Nexus.Danger, 0.8f) else colors[i]
        val tl = Offset(c.x - rx, c.y - ry)
        val sz = Size(rx * 2f, ry * 2f)
        rotate(tilts[i], pivot = c) {
            drawOval(col.copy(alpha = 0.10f + 0.20f * energy), topLeft = tl, size = sz, style = Stroke(1.dp.toPx()))
            val start = angle * dir[i] + i * 70f
            val sweep = 40f + 120f * energy
            for (half in 0..(if (energy > 0.5f) 1 else 0)) {
                val s = start + half * 180f
                drawArc(color = col.copy(alpha = 0.08f + 0.20f * energy), startAngle = s, sweepAngle = sweep, useCenter = false, topLeft = tl, size = sz, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
                drawArc(color = col.copy(alpha = 0.30f + 0.65f * energy), startAngle = s, sweepAngle = sweep * 0.7f, useCenter = false, topLeft = tl, size = sz, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            }
            // Light streams: particles riding the first two rings once the link is coming up.
            if (energy > 0.3f && i < 2) {
                for (k in 0 until 10) {
                    val t = (angle * dir[i] * PI / 180.0).toFloat() * 1.5f + k * (2f * PI.toFloat() / 10f)
                    val px = c.x + rx * cos(t)
                    val py = c.y + ry * sin(t)
                    drawCircle(Color.White.copy(alpha = 0.7f * energy), radius = 1.6.dp.toPx(), center = Offset(px, py))
                }
            }
        }
    }
}

private fun DrawScope.drawPowerButton(c: Offset, r: Float, energy: Float, state: LinkState, pulse: Float, press: Float) {
    val rr = r * press
    val main = when (state) {
        LinkState.ERROR -> Nexus.Danger
        LinkState.ON -> Nexus.Cyan
        else -> Nexus.Blue
    }
    val second = when (state) {
        LinkState.ERROR -> Color(0xFF8A1030)
        else -> Nexus.Violet
    }
    // Halo and the two thin guide rings of the reference.
    drawCircle(
        Brush.radialGradient(listOf(main.copy(alpha = 0.08f + 0.28f * energy), Color.Transparent), center = c, radius = rr * 1.7f),
        radius = rr * 1.7f, center = c,
    )
    drawCircle(main.copy(alpha = 0.22f), radius = rr * 1.18f, center = c, style = Stroke(1.dp.toPx()))
    drawCircle(second.copy(alpha = 0.18f), radius = rr * 1.34f, center = c, style = Stroke(1.dp.toPx()))
    // The ripple that leaves the button while the link is coming up or up.
    if (state == LinkState.ON || state == LinkState.CONNECTING) {
        drawCircle(main.copy(alpha = (1f - pulse) * 0.30f * energy), radius = rr * (1.12f + 0.5f * pulse), center = c, style = Stroke(2.dp.toPx()))
    }
    // The glass disc.
    drawCircle(
        brush = Brush.radialGradient(
            listOf(main.copy(alpha = 0.95f), second.copy(alpha = 0.85f), Color(0xFF0A0A2E)),
            center = Offset(c.x - rr * 0.30f, c.y - rr * 0.35f),
            radius = rr * 1.3f,
        ),
        radius = rr, center = c,
    )
    drawCircle(
        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), center = Offset(c.x - rr * 0.32f, c.y - rr * 0.42f), radius = rr * 0.55f),
        radius = rr * 0.55f, center = Offset(c.x - rr * 0.32f, c.y - rr * 0.42f),
    )
    drawCircle(Color.White.copy(alpha = 0.50f), radius = rr, center = c, style = Stroke(2.dp.toPx()))
    drawCircle(main.copy(alpha = 0.25f), radius = rr * 1.03f, center = c, style = Stroke(4.dp.toPx()))
    // The power glyph.
    val g = rr * 0.30f
    val a = when (state) {
        LinkState.CONNECTING -> 0.6f + 0.4f * pulse
        else -> 0.95f
    }
    val sw = rr * 0.075f
    drawArc(
        color = Color.White.copy(alpha = a), startAngle = -60f, sweepAngle = 300f, useCenter = false,
        topLeft = Offset(c.x - g, c.y - g), size = Size(g * 2f, g * 2f), style = Stroke(sw, cap = StrokeCap.Round),
    )
    drawLine(Color.White.copy(alpha = a), Offset(c.x, c.y - g * 1.18f), Offset(c.x, c.y - g * 0.10f), strokeWidth = sw, cap = StrokeCap.Round)
}

// ───────────────────────────── status capsule ─────────────────────────────

@Composable
private fun NexusStatusCapsule(state: LinkState, conn: WidgetLive.Conn, app: HomeApp?, onClick: () -> Unit) {
    val name = app?.let { WidgetPainter.labelOf(it) }
    val title = when (state) {
        LinkState.ON -> name ?: tr("متصل", "Connected")
        LinkState.CONNECTING -> tr("در حال اتصال", "Connecting")
        LinkState.ERROR -> tr("اتصال برقرار نشد", "Connection failed")
        LinkState.OFF -> tr("متصل نیست", "Not Connected")
    }
    val sub = when (state) {
        LinkState.ON -> conn.country?.let { WidgetFormat.flag(it) + " " + WidgetFormat.countryName(it) }
            ?: tr("در حال یافتن کشور خروج…", "Finding the exit country…")
        LinkState.CONNECTING -> name
        LinkState.ERROR -> name?.let { tr("روش: ", "Method: ") + it }
        LinkState.OFF -> name?.let { tr("آخرین روش: ", "Last method: ") + it }
            ?: tr("یک روش اتصال انتخاب کنید", "Pick a connection method")
    }
    val accent = stateColor(state).let { if (state == LinkState.OFF) Nexus.Blue else it }
    val shape = RoundedCornerShape(30.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Nexus.Blue.copy(alpha = 0.16f), Nexus.Violet.copy(alpha = 0.16f))))
            .border(1.dp, Brush.horizontalGradient(listOf(accent.copy(alpha = 0.55f), Nexus.Violet.copy(alpha = 0.30f))), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(30.dp)) { drawStatusShield(state == LinkState.ON, accent) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Nexus.Text, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            if (sub != null) Text(sub, color = Nexus.Muted, fontSize = 12.5.sp, maxLines = 1)
        }
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        Canvas(Modifier.size(18.dp)) {
            val p = Path().apply {
                moveTo(size.width * 0.30f, size.height * 0.12f)
                lineTo(size.width * 0.72f, size.height * 0.50f)
                lineTo(size.width * 0.30f, size.height * 0.88f)
            }
            scale(if (rtl) -1f else 1f, 1f, pivot = center) {
                drawPath(p, Nexus.Text.copy(alpha = 0.85f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
        }
    }
}

private fun DrawScope.drawStatusShield(on: Boolean, accent: Color) {
    val w = size.width
    val h = size.height
    val col = if (on) Nexus.Mint else Nexus.Mint.copy(alpha = 0.85f)
    val p = Path().apply {
        moveTo(w * 0.5f, h * 0.04f)
        lineTo(w * 0.92f, h * 0.20f)
        lineTo(w * 0.92f, h * 0.52f)
        cubicTo(w * 0.92f, h * 0.76f, w * 0.70f, h * 0.90f, w * 0.5f, h * 0.98f)
        cubicTo(w * 0.30f, h * 0.90f, w * 0.08f, h * 0.76f, w * 0.08f, h * 0.52f)
        lineTo(w * 0.08f, h * 0.20f)
        close()
    }
    drawPath(p, (if (on) col else accent).copy(alpha = 0.18f))
    drawPath(p, if (on) col else Nexus.Muted, style = Stroke(2.dp.toPx(), join = androidx.compose.ui.graphics.StrokeJoin.Round))
    val tick = Path().apply {
        moveTo(w * 0.30f, h * 0.52f)
        lineTo(w * 0.45f, h * 0.67f)
        lineTo(w * 0.72f, h * 0.38f)
    }
    drawPath(tick, if (on) col else Nexus.Muted, style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
}

// ───────────────────────────── network card ─────────────────────────────

@Composable
private fun NexusNetworkCard(state: LinkState, conn: WidgetLive.Conn, traffic: WidgetLive.Traffic) {
    // The session clock. Ticks only while the link is up, and only recomposes this card.
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(conn.up, conn.since) {
        while (conn.up) {
            now.longValue = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val up = state == LinkState.ON
    val duration = if (up) WidgetFormat.clock((now.longValue - conn.since).coerceAtLeast(0L)) else "00:00:00"
    val down = if (up) WidgetFormat.rate(traffic.rxRate) else WidgetFormat.rate(0L)
    val upl = if (up) WidgetFormat.rate(traffic.txRate) else WidgetFormat.rate(0L)

    val energy = animateFloatAsState(if (up) 1f else if (state == LinkState.CONNECTING) 0.4f else 0f, tween(800), label = "map energy")
    val inf = rememberInfiniteTransition(label = "nexus map")
    val phase = inf.animateFloat(0f, 1f, infiniteRepeatable(tween(4_000, easing = LinearEasing)), label = "map phase")
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(138.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.05f), Nexus.Violet.copy(alpha = 0.08f))))
            .border(1.dp, Brush.verticalGradient(listOf(Nexus.Blue.copy(alpha = 0.35f), Nexus.Violet.copy(alpha = 0.20f))), shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.weight(1f).fillMaxHeight()) { drawWorldMap(energy.value, phase.value) }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.width(1.dp).fillMaxHeight(0.75f).background(Nexus.Muted.copy(alpha = 0.3f)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.width(118.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatRow(Icons.Default.Download, Nexus.Cyan, down)
            StatRow(Icons.Default.Upload, Nexus.Violet, upl)
            Box(Modifier.fillMaxWidth().height(1.dp).background(Nexus.Muted.copy(alpha = 0.3f)))
            StatRow(Icons.Default.Schedule, Nexus.Muted, duration)
        }
    }
}

@Composable
private fun StatRow(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            value,
            color = Nexus.Text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
        )
    }
}

/** Land as dot-matrix rows: (row, firstColumn, lastColumn) on a 60 x 24 grid, 6 degrees a cell. */
private val LAND = intArrayOf(
    0, 22, 26, 1, 21, 26, 2, 21, 25, 3, 22, 25, 4, 23, 24,
    1, 9, 14, 2, 4, 18, 3, 3, 19, 4, 3, 20, 5, 5, 21, 6, 6, 21, 7, 7, 20, 8, 8, 19, 9, 9, 18, 10, 10, 15, 11, 14, 17,
    12, 16, 23, 13, 15, 24, 14, 15, 25, 15, 16, 25, 16, 17, 24, 17, 17, 23, 18, 17, 22, 19, 18, 21, 20, 18, 21, 21, 18, 20, 22, 18, 19, 23, 18, 19,
    2, 32, 35, 3, 30, 36, 4, 29, 37, 5, 29, 36, 6, 28, 37, 7, 28, 36, 8, 28, 35,
    9, 27, 38, 10, 27, 39, 11, 28, 39, 12, 28, 38, 13, 29, 37, 14, 30, 36, 15, 30, 36, 16, 31, 36, 17, 31, 35, 18, 32, 35, 19, 32, 34, 20, 32, 34,
    1, 39, 53, 2, 37, 54, 3, 37, 56, 4, 36, 58, 5, 36, 57, 6, 36, 55, 7, 37, 54, 8, 36, 53, 9, 36, 52, 10, 37, 50,
    10, 41, 44, 11, 41, 44, 12, 42, 43, 13, 42, 42, 11, 48, 51, 12, 49, 51, 13, 50, 50, 14, 48, 54, 15, 50, 52,
    6, 55, 56, 7, 55, 56, 8, 54, 55,
    16, 49, 56, 17, 48, 56, 18, 48, 55, 19, 49, 54, 20, 50, 53, 21, 58, 59, 22, 58, 58,
)

/** Hub cells (row, column) the link arcs run between. */
private val HUBS = arrayOf(
    intArrayOf(8, 13), intArrayOf(5, 31), intArrayOf(9, 43), intArrayOf(7, 54), intArrayOf(17, 19), intArrayOf(15, 33),
)
private val ROUTES = arrayOf(intArrayOf(0, 1), intArrayOf(1, 2), intArrayOf(2, 3), intArrayOf(4, 0), intArrayOf(1, 5))

private fun DrawScope.drawWorldMap(energy: Float, phase: Float) {
    val cols = 60
    val rows = 24
    val cell = min(size.width / cols, size.height / rows)
    val ox = (size.width - cell * cols) / 2f
    val oy = (size.height - cell * rows) / 2f
    var i = 0
    while (i < LAND.size) {
        val row = LAND[i]
        val a = LAND[i + 1]
        val b = LAND[i + 2]
        i += 3
        for (col in a..b) {
            val f = col / (cols - 1f)
            val colr = lerp(Nexus.Blue, Nexus.Violet, f)
            drawCircle(
                colr.copy(alpha = 0.30f + 0.45f * energy),
                radius = cell * 0.34f,
                center = Offset(ox + (col + 0.5f) * cell, oy + (row + 0.5f) * cell),
            )
        }
    }
    fun hub(k: Int) = Offset(ox + (HUBS[k][1] + 0.5f) * cell, oy + (HUBS[k][0] + 0.5f) * cell)
    for ((n, rt) in ROUTES.withIndex()) {
        val p0 = hub(rt[0])
        val p1 = hub(rt[1])
        val ctrl = Offset((p0.x + p1.x) / 2f, minOf(p0.y, p1.y) - cell * 5f)
        val path = Path().apply {
            moveTo(p0.x, p0.y)
            quadraticBezierTo(ctrl.x, ctrl.y, p1.x, p1.y)
        }
        drawPath(path, Nexus.Cyan.copy(alpha = 0.12f + 0.45f * energy), style = Stroke(1.dp.toPx(), cap = StrokeCap.Round))
        if (energy > 0.05f) {
            val t = (phase + n * 0.2f) % 1f
            val mt = 1f - t
            val x = mt * mt * p0.x + 2f * mt * t * ctrl.x + t * t * p1.x
            val y = mt * mt * p0.y + 2f * mt * t * ctrl.y + t * t * p1.y
            drawCircle(Color.White.copy(alpha = 0.9f * energy), radius = 2.dp.toPx(), center = Offset(x, y))
        }
    }
    for (k in HUBS.indices) {
        val p = hub(k)
        drawCircle(Nexus.Cyan.copy(alpha = 0.25f + 0.6f * energy), radius = cell * 0.9f, center = p, style = Stroke(1.dp.toPx()))
        drawCircle(Color.White.copy(alpha = 0.55f + 0.4f * energy), radius = cell * 0.40f, center = p)
    }
}
