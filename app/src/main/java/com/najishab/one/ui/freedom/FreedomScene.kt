package com.najishab.one.ui.freedom

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.zIndex
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import com.najishab.one.store.tr
import com.najishab.one.ui.skin.LinkState
import kotlin.math.PI
import kotlin.math.cos

/**
 * The cage's width for a scene of [w] x [h]. It grows with the scene instead of staying at the
 * mockup's fixed 188dp, so the extra height Home now gives the scene is spent on the cage and the
 * bird rather than on empty sky. Two bounds keep it whole: it must fit above and below its centre
 * (48% of the height), and it must leave room either side for the bird's soar. 210dp is the ceiling.
 */
internal fun freedomCageWidth(w: Dp, h: Dp): Dp {
    val byHeight = (h * 0.96f - 8.dp) * (220f / 260f)
    val byWidth = w * 0.62f
    return minOf(210.dp, byHeight, byWidth).coerceAtLeast(120.dp)
}
/**
 * The Freedom scene: a caged bird under a night sky that breaks free into dawn when the link comes
 * up. Every value here is the mockup's (cage 188x222dp over a 220x260 viewBox, halves ±25° in
 * 1050 ms, lock drop 130dp/55° in 800 ms, dawn 1.5 s, sun 1.8 s, error snap 240 ms + a 550 ms red
 * flash). The state comes from the real link ([LinkState]); nothing runs on a timer.
 *
 * Infinite motion (sway, rattle, drift, motes) runs only while RESUMED and never under reduced
 * motion; the cage paths are parsed once and remembered.
 */
@Composable
internal fun FreedomScene(state: LinkState, modifier: Modifier = Modifier) {
    val reduced = rememberFreedomReducedMotion()
    val owner = LocalLifecycleOwner.current
    var resumed by remember(owner) { mutableStateOf(owner.lifecycle.currentState == Lifecycle.State.RESUMED) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = owner.lifecycle.currentState == Lifecycle.State.RESUMED
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val animate = !reduced && resumed
    val model = remember { CageModel() }

    val t = updateTransition(state, label = "freedom scene")
    val split by t.animateFloat(
        transitionSpec = {
            when {
                reduced -> snap()
                targetState == LinkState.ERROR -> tween(240)
                else -> tween(1050, easing = CubicBezierEasing(0.5f, 0.05f, 0.3f, 1f))
            }
        },
        label = "split",
    ) { if (it == LinkState.ON) 1f else 0f }
    val lockDrop by t.animateFloat(
        transitionSpec = { if (reduced) snap() else tween(800, easing = CubicBezierEasing(0.42f, 0f, 1f, 1f)) },
        label = "lock",
    ) { if (it == LinkState.ON) 1f else 0f }
    val dawn by t.animateFloat({ if (reduced) snap() else tween(1500) }, label = "dawn") { if (it == LinkState.ON) 1f else 0f }
    val sun by t.animateFloat({ if (reduced) snap() else tween(1800) }, label = "sun") { if (it == LinkState.ON) 1f else 0f }
    val warm by t.animateFloat({ if (reduced) snap() else tween(1400) }, label = "warm") { if (it == LinkState.ON) 1f else 0f }
    val far by t.animateFloat(
        transitionSpec = { if (reduced) snap() else tween(1600, delayMillis = if (targetState == LinkState.ON) 350 else 0) },
        label = "far birds",
    ) { if (it == LinkState.ON) 0.55f else 0f }

    val flash = remember { Animatable(0f) }
    LaunchedEffect(state, reduced) {
        if (state == LinkState.ERROR && !reduced) {
            flash.snapTo(0f)
            flash.animateTo(1f, tween(100))
            flash.animateTo(0f, tween(450))
        } else flash.snapTo(0f)
    }
    val motion = if (animate) rememberSceneMotion() else StillMotion
    val desc = when (state) {
        LinkState.OFF -> tr("صحنهٔ آزادی: پرنده در قفس است", "Freedom scene: the bird is in the cage")
        LinkState.CONNECTING -> tr("صحنهٔ آزادی: پرنده در حال گشودن قفس است", "Freedom scene: the bird is opening the cage")
        LinkState.ON -> tr("صحنهٔ آزادی: پرنده آزاد شده است", "Freedom scene: the bird is free")
        LinkState.ERROR -> tr("صحنهٔ آزادی: قفس بسته ماند — اتصال ناموفق", "Freedom scene: the cage held — connection failed")
    }
    val shape = RoundedCornerShape(26.dp)
    BoxWithConstraints(
        modifier
            .clip(shape)
            .background(Freedom.SceneBase)
            .border(1.dp, Freedom.Border, shape)
            .semantics { contentDescription = desc }
    ) {
        val birdSize = (freedomCageWidth(maxWidth, maxHeight) * 0.46f).coerceIn(60.dp, 100.dp)
        val birdX = maxWidth * 0.5f - birdSize / 2f
        val birdY = maxHeight * 0.575f - birdSize / 2f
        // Sky behind, cage in front: the bird sits BETWEEN them, so while it is inside, the
        // bars cross it and it reads as caged. Once the cage has swung open (split past half)
        // the bird moves in front, so nothing faint overlaps it while it soars.
        // zIndex, not child order -- reordering children would rebuild the Lottie and its
        // Animatables and the bird would jump.
        Canvas(Modifier.fillMaxSize().zIndex(0f)) {
            drawSky(model, dawn, sun, far, motion)
        }
        FreedomBird(
            state = state,
            animate = animate,
            reduced = reduced,
            areaW = maxWidth,
            areaH = maxHeight,
            size = birdSize,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = birdX, y = birdY)
                .zIndex(if (split < 0.5f) 1f else 3f),
        )
        Canvas(Modifier.fillMaxSize().zIndex(2f)) {
            drawCage(model, state, split, lockDrop, warm, motion)
        }
        if (flash.value > 0.01f) {
            Canvas(Modifier.fillMaxSize().zIndex(4f)) {
                val c = Offset(size.width * 0.5f, size.height * 0.58f)
                val r = size.width * 0.78f
                drawCircle(
                    Brush.radialGradient(
                        0f to Color(0xFFFF603C).copy(alpha = 0.55f),
                        0.58f to Color(0xFFFF3C28).copy(alpha = 0.14f),
                        0.78f to Color.Transparent,
                        center = c, radius = r,
                    ),
                    radius = r, center = c, alpha = flash.value,
                )
            }
        }
    }
}

// ───────────────────────────── the cage, from the mockup's SVG ─────────────────────────────

private class CagePath(val path: Path, val width: Float)

private fun cagePath(d: String, w: Float) = CagePath(PathParser().parsePathString(d).toPath(), w)

private fun svgPath(d: String): Path = PathParser().parsePathString(d).toPath()

/** The mockup's cage-svg, path for path. Coordinates stay in the 220x260 viewBox. */
private class CageModel {
    val topStem = listOf(cagePath("M110,18 V30", 3.5f))
    val left = listOf(
        cagePath("M36,124 C36,66 66,38 110,36", 4f),
        cagePath("M52,96 C50,132 50,168 52,204", 3f),
        cagePath("M68,66 C66,112 66,158 68,204", 3f),
        cagePath("M84,48 C83,100 83,152 84,204", 3f),
        cagePath("M98,39 C97,94 97,149 98,204", 3f),
        cagePath("M104,37 C104,92 104,148 104,204", 3f),
        cagePath("M36,124 C62,136 84,140 110,141", 3f),
        cagePath("M38,164 C64,174 86,177 110,178", 3f),
        cagePath("M64,152 H108", 3.5f),
        cagePath("M110,206 H34 C34,213 40,218 50,218 H110", 4f),
    )
    val right = listOf(
        cagePath("M110,36 C154,38 184,66 184,124", 4f),
        cagePath("M116,37 C116,92 116,148 116,204", 3f),
        cagePath("M122,39 C123,94 123,149 122,204", 3f),
        cagePath("M136,48 C137,100 137,152 136,204", 3f),
        cagePath("M152,66 C154,112 154,158 152,204", 3f),
        cagePath("M168,96 C170,132 170,168 168,204", 3f),
        cagePath("M110,141 C136,140 158,136 184,124", 3f),
        cagePath("M110,178 C134,177 156,174 182,164", 3f),
        cagePath("M112,152 H156", 3.5f),
        cagePath("M110,206 H186 C186,213 180,218 170,218 H110", 4f),
    )
    val shackle = cagePath("M102,197 v-5 a8,7.5 0 0 1 16,0 v5", 3.5f)
    val hillFar = svgPath("M0,34 C60,18 120,26 180,30 C240,34 300,22 360,30 L360,58 L0,58 Z")
    val hillNear = svgPath("M0,44 C70,32 150,40 220,42 C280,44 330,38 360,42 L360,58 L0,58 Z")
    val farBirds = listOf(
        svgPath("M40,20 q6,-6 12,0 q6,-6 12,0"),
        svgPath("M262,12 q5,-5 10,0 q5,-5 10,0"),
        svgPath("M306,32 q4,-4 8,0 q4,-4 8,0"),
    )
}

private class Star(val x: Float, val y: Float, val r: Float, val color: Color)

private val STARS = listOf(
    Star(0.12f, 0.20f, 1.5f, Color(0xFFD7E6FF)),
    Star(0.78f, 0.12f, 1.0f, Color(0xFFD7E6FF)),
    Star(0.88f, 0.46f, 1.2f, Color(0xFFA9C2E4)),
    Star(0.26f, 0.62f, 1.0f, Color(0xFFA9C2E4)),
    Star(0.56f, 0.08f, 1.4f, Color(0xFFEEF4FF)),
    Star(0.08f, 0.74f, 1.0f, Color(0xFFA9C2E4)),
    Star(0.40f, 0.30f, 1.0f, Color(0xFFD7E6FF)),
    Star(0.66f, 0.58f, 1.1f, Color(0xFFA9C2E4)),
)

private class Mote(val x: Float, val fromBottom: Float, val r: Float, val delay: Float)

private val MOTES = listOf(
    Mote(0.18f, 0.22f, 2.5f, 0f),
    Mote(0.30f, 0.13f, 2.0f, 0.8f),
    Mote(0.64f, 0.18f, 2.5f, 1.6f),
    Mote(0.79f, 0.27f, 2.0f, 0.4f),
    Mote(0.50f, 0.09f, 1.5f, 2.2f),
)

private class SceneMotion(val sway: Float, val rattle: Float, val lockShake: Float, val drift: Float, val mote: Float, val blink: Float)

private val StillMotion = SceneMotion(0f, 0f, 0f, 0f, 0f, 1f)

@Composable
private fun rememberSceneMotion(): SceneMotion {
    val inf = rememberInfiniteTransition(label = "freedom motion")
    val sway = inf.animateFloat(-1.25f, 1.25f, infiniteRepeatable(tween(7500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sway")
    val rattle = inf.animateFloat(0f, 1f, infiniteRepeatable(tween(130, easing = LinearEasing)), label = "rattle")
    val lock = inf.animateFloat(-7f, 7f, infiniteRepeatable(tween(300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "lock shake")
    val drift = inf.animateFloat(-7f, 9f, infiniteRepeatable(tween(15000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "drift")
    val mote = inf.animateFloat(0f, 1f, infiniteRepeatable(tween(5500, easing = LinearEasing)), label = "motes")
    val blink = inf.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "blink")
    return SceneMotion(sway.value, rattle.value, lock.value, drift.value, mote.value, blink.value)
}

/** The mockup's rattle keyframes, piecewise: 0 -> (.9,-.7,.55) -> (-.9,.5,-.55) -> (.5,.8,.3) -> 0. */
private fun lerpF(start: Float, stop: Float, fraction: Float): Float = start + (stop - start) * fraction

private fun rattleAt(p: Float, a: Float, b: Float, c: Float, d: Float): Float = when {
    p < 0.25f -> lerpF(0f, a, p / 0.25f)
    p < 0.5f -> lerpF(a, b, (p - 0.25f) / 0.25f)
    p < 0.75f -> lerpF(b, c, (p - 0.5f) / 0.25f)
    else -> lerpF(c, d, (p - 0.75f) / 0.25f)
}

private fun DrawScope.drawSky(m: CageModel, dawn: Float, sun: Float, far: Float, mo: SceneMotion) {
    val w = size.width
    val h = size.height
    // Sky: night under dawn, crossfaded.
    drawRect(
        Brush.verticalGradient(0f to Color(0xFF070F1E), 0.52f to Color(0xFF0D1930), 1f to Color(0xFF1A2740)),
        alpha = 1f - 0.88f * dawn,
    )
    if (dawn > 0.01f) drawRect(
        Brush.verticalGradient(0f to Color(0xFF2B4D68), 0.30f to Color(0xFF5F7F9C), 0.66f to Color(0xFFD98D5F), 1f to Color(0xFFFFC98A)),
        alpha = dawn,
    )
    val starA = 1f - dawn
    if (starA > 0.02f) for (s in STARS) {
        drawCircle(s.color.copy(alpha = starA * 0.9f), radius = s.r.dp.toPx(), center = Offset(w * s.x, h * s.y))
    }
    // Sun: 170dp, low and faint at night, risen at dawn.
    val sunR = 85.dp.toPx()
    val sunC = Offset(w * 0.5f, h - 27.dp.toPx() + 46.dp.toPx() * (1f - sun))
    drawCircle(
        Brush.radialGradient(0f to Color(0xFFFFEDB9), 0.52f to Color(0xFFFFB35C), 0.72f to Color.Transparent, center = sunC, radius = sunR),
        radius = sunR, center = sunC, alpha = 0.25f + 0.75f * sun,
    )
    // Hills.
    val hillH = 58.dp.toPx()
    withTransform({
        translate(left = 0f, top = h - hillH)
        scale(w / 360f, hillH / 58f, pivot = Offset.Zero)
    }) {
        drawPath(m.hillFar, Freedom.HillFar)
        drawPath(m.hillNear, Freedom.HillNear)
    }
    // Far birds, drifting once free.
    if (far > 0.01f) {
        val s = w / 360f
        val driftPx = mo.drift.dp.toPx()
        withTransform({
            translate(left = driftPx, top = h * 0.15f)
            scale(s, s, pivot = Offset.Zero)
        }) {
            for (p in m.farBirds) drawPath(p, Freedom.FarBird.copy(alpha = far), style = Stroke(2.4f, cap = StrokeCap.Round))
        }
    }
    // Motes, floating up in the dawn light.
    if (dawn > 0.02f) for (mt in MOTES) {
        val p = (mo.mote + mt.delay / 5.5f) % 1f
        val rise = 0.5f - 0.5f * cos(2.0 * PI * p).toFloat()
        drawCircle(
            Freedom.Mote.copy(alpha = 0.85f * dawn),
            radius = mt.r.dp.toPx(),
            center = Offset(w * mt.x, h * (1f - mt.fromBottom) - 17.dp.toPx() * rise),
        )
    }
    }

/** The cage alone, drawn OVER the bird so its bars read as being in front of it. */
private fun DrawScope.drawCage(
    m: CageModel, state: LinkState,
    split: Float, lockDrop: Float, warm: Float,
    mo: SceneMotion,
) {
    val w = size.width
    val h = size.height
    // The cage over the 220x260 viewBox, centred at (50%, 48%), sized to the scene.
    val cageW = freedomCageWidth(size.width.toDp(), size.height.toDp()).toPx()
    val s = cageW / 220f
    val cageH = 260f * s
    val ox = w * 0.5f - cageW * 0.5f
    val oy = h * 0.48f - cageH * 0.5f
    fun px(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)
    val tint = lerp(Freedom.Cage, Freedom.CageWarm, warm)
    val connecting = state == LinkState.CONNECTING
    val swayDeg = if (connecting || state == LinkState.ON) 0f else mo.sway
    val rx = if (connecting) rattleAt(mo.rattle, 0.9f, -0.9f, 0.5f, 0f) else 0f
    val ry = if (connecting) rattleAt(mo.rattle, -0.7f, 0.5f, 0.8f, 0f) else 0f
    val rr = if (connecting) rattleAt(mo.rattle, 0.55f, -0.55f, 0.3f, 0f) else 0f
    val shake = if (connecting) mo.lockShake else 0f
    val swayPivot = px(110f, 7.8f)
    val halfA = 1f - 0.8f * split
    val rxPx = rx.dp.toPx()
    val ryPx = ry.dp.toPx()
    val halfTx = 27.dp.toPx() * split
    val halfTy = 12.dp.toPx() * split
    val topLift = 15.dp.toPx() * split
    val lockFall = 130.dp.toPx() * lockDrop
    // Left half: translate(-27,12) rotate(-25) about (88%, 0%) of its own box.
    withTransform({
        translate(left = rxPx, top = ryPx)
        rotate(swayDeg + rr, pivot = swayPivot)
        translate(left = -halfTx, top = halfTy)
        rotate(-25f * split, pivot = px(100.9f, 36f))
        translate(left = ox, top = oy)
        scale(s, s, pivot = Offset.Zero)
    }) {
        for (p in m.left) drawPath(p.path, tint.copy(alpha = halfA), style = Stroke(p.width, cap = StrokeCap.Round))
    }
    // Right half, mirrored.
    withTransform({
        translate(left = rxPx, top = ryPx)
        rotate(swayDeg + rr, pivot = swayPivot)
        translate(left = halfTx, top = halfTy)
        rotate(25f * split, pivot = px(119.1f, 36f))
        translate(left = ox, top = oy)
        scale(s, s, pivot = Offset.Zero)
    }) {
        for (p in m.right) drawPath(p.path, tint.copy(alpha = halfA), style = Stroke(p.width, cap = StrokeCap.Round))
    }
    // Top ring: lifts 15dp and fades as the cage opens.
    withTransform({
        translate(left = rxPx, top = ryPx)
        rotate(swayDeg + rr, pivot = swayPivot)
        translate(left = 0f, top = -topLift)
        translate(left = ox, top = oy)
        scale(s, s, pivot = Offset.Zero)
    }) {
        val a = 1f - split
        drawCircle(tint.copy(alpha = a), radius = 6f, center = Offset(110f, 12f), style = Stroke(3.5f, cap = StrokeCap.Round))
        for (p in m.topStem) drawPath(p.path, tint.copy(alpha = a), style = Stroke(p.width, cap = StrokeCap.Round))
        drawCircle(tint.copy(alpha = a), radius = 4.5f, center = Offset(110f, 37f))
    }
    // Lock: shakes while connecting, falls 130dp turning 55° when the cage opens.
    withTransform({
        translate(left = rxPx, top = ryPx)
        rotate(swayDeg + rr, pivot = swayPivot)
        translate(left = 0f, top = lockFall)
        rotate(55f * lockDrop + shake, pivot = px(110f, 184.5f))
        translate(left = ox, top = oy)
        scale(s, s, pivot = Offset.Zero)
    }) {
        val a = 1f - lockDrop
        drawPath(m.shackle.path, tint.copy(alpha = a), style = Stroke(m.shackle.width, cap = StrokeCap.Round))
        drawRoundRect(tint.copy(alpha = a), topLeft = Offset(99f, 197f), size = Size(22f, 17f), cornerRadius = CornerRadius(4f, 4f))
        drawCircle(Freedom.Keyhole.copy(alpha = a), radius = 2.6f, center = Offset(110f, 205f))
    }
}