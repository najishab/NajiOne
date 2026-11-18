package com.najishab.one.ui

import com.najishab.one.ui.home.HomeWallpaper
import com.najishab.one.ui.home.Wallpapers
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.najishab.one.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import com.najishab.one.R
import com.najishab.one.utils.S

/**
 * Opening animation: a dove is set free from its cage.
 */
object IntroState {
    @Volatile var played: Boolean = false
}

/** Slow start, strong acceleration: a bird pushes off, then it is simply gone. */
private val TakeoffEasing = CubicBezierEasing(0.35f, 0f, 0.75f, 0.75f)

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val cageIn = remember { Animatable(0f) }        // cage + dove fade/scale in
    val doorOpen = remember { Animatable(0f) }      // door swings open
    val wingOpen = remember { Animatable(0f) }      // wings spread, feet tuck
    val flight = remember { Animatable(0f) }        // 0 = on the perch, 1 = gone
    val settle = remember { Animatable(0f) }        // feathers keep drifting after the dove is gone
    val taglineAlpha = remember { Animatable(0f) }
    val exitAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        cageIn.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
        delay(300)                                   // let the caged dove be seen first

        doorOpen.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
        wingOpen.animateTo(1f, tween(320, easing = FastOutSlowInEasing))

        launch { taglineAlpha.animateTo(1f, tween(700)) }
        flight.animateTo(1f, tween(1900, easing = TakeoffEasing))

        settle.animateTo(1f, tween(600, easing = LinearEasing))
        delay(250)
        exitAlpha.animateTo(0f, tween(340))
        onFinished()
    }

    val loop = rememberInfiniteTransition(label = "splashLoop")
    val slow by loop.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "slow",
    )
    val flapClock by loop.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(380, easing = LinearEasing), RepeatMode.Restart),
        label = "flap",
    )

    val feathers = remember {
        val r = Random(11)
        List(7) { i ->
            Feather(
                t = 0.04f + i * 0.065f + r.nextFloat() * 0.03f,
                drift = r.nextFloat() * 2f - 1f,
                spin = r.nextFloat() * 6.28f,
                size = 0.55f + r.nextFloat() * 0.45f,
            )
        }
    }

    val cageColor = Primary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(exitAlpha.value),
        contentAlignment = Alignment.Center,
    ) {
        HomeWallpaper(
            wallpaperId = Wallpapers.current,
            blurPercent = Wallpapers.blur,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF0A101C).copy(alpha = 0.90f),
                            Color(0xFF0A101C).copy(alpha = 0.72f),
                            Color(0xFF0A101C).copy(alpha = 0.90f),
                        )
                    )
                )
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val m = 300.dp.toPx().coerceAtMost(w * 0.9f)
            val cx = w / 2f
            val cy = h / 2f - 40.dp.toPx()
            val perchY = cy + m * 0.16f
            val u0 = m * 0.065f

            val sway = 1.3f * sin(2.0 * PI * slow).toFloat()
            val cs = 0.9f + 0.1f * cageIn.value
            val pivot = Offset(cx, 0f)

            fun toWorld(p: Offset): Offset {
                val rad = sway * PI / 180.0
                val dx = (p.x - pivot.x).toDouble()
                val dy = (p.y - pivot.y).toDouble()
                val rx = (dx * cos(rad) - dy * sin(rad)).toFloat() + pivot.x
                val ry = (dx * sin(rad) + dy * cos(rad)).toFloat() + pivot.y
                return Offset(cx + (rx - cx) * cs, cy + (ry - cy) * cs)
            }

            val t = flight.value
            val mixFly = (t * 8f).coerceAtMost(1f)

            val perch = Offset(cx + 0.24f * u0, perchY - 0.64f * u0)
            val bob = sin(2.0 * PI * slow * 2.0).toFloat() * u0 * 0.04f
            val start = toWorld(perch)
            val ctrl = Offset(cx + 0.30f * w, start.y - 0.04f * h)
            val end = Offset(w * 0.98f, h * 0.05f)

            val pos = bez(start, ctrl, end, t) + Offset(0f, bob * (1f - mixFly))

            val tangent = bezTangent(start, ctrl, end, t)
            val ang = Math.toDegrees(atan2(tangent.y.toDouble(), tangent.x.toDouble()))
                .toFloat().coerceIn(-55f, 10f) * 0.85f
            val tilt = mix(sway, ang, mixFly)

            val osc = 0.5f + 0.5f * sin(2.0 * PI * flapClock).toFloat()
            val flapTheta = mix(-32f, 72f, osc)
            val theta = mix(mix(-8f, 62f, wingOpen.value), flapTheta, mixFly)
            val wingLen = mix(0.8f, 1f, wingOpen.value)

            val bump = sin(PI * (t / 0.3f).coerceAtMost(1f).toDouble()).toFloat()
            val sc = if (t <= 0f) 1f else mix(1f, 0.28f, t) + 0.30f * bump
            val uDove = u0 * cs * sc
            val doveAlpha = cageIn.value * (1f - ((t - 0.82f) / 0.18f).coerceIn(0f, 1f))

            fun drawTheDove() {
                translate(pos.x, pos.y) {
                    rotate(tilt, Offset.Zero) {
                        drawDove(uDove, theta, wingLen, doveAlpha, 1f - wingOpen.value)
                    }
                }
            }

            val glow = doorOpen.value * (1f - flight.value * 0.6f)
            if (glow > 0.01f) {
                val gc = Offset(cx, cy - m * 0.02f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(cageColor.copy(alpha = 0.30f * glow), Color.Transparent),
                        center = gc,
                        radius = m * 0.75f,
                    ),
                    radius = m * 0.75f,
                    center = gc,
                )
            }

            if (t <= 0f) drawTheDove()

            withTransform({
                scale(cs, cs, Offset(cx, cy))
                rotate(sway, pivot)
            }) {
                drawCage(
                    cx = cx, cy = cy, m = m,
                    color = cageColor,
                    alpha = cageIn.value * (1f - 0.35f * flight.value),
                    door = doorOpen.value,
                )
            }

            if (t > 0f) {
                feathers.forEach { f ->
                    if (t > f.t) {
                        val age = (t - f.t) + 0.6f * settle.value
                        val base = bez(start, ctrl, end, f.t)
                        val px = base.x + f.drift * u0 * 5f * age +
                            sin(age * 9f + f.spin) * u0 * 0.9f
                        val py = base.y + age * h * 0.22f
                        val a = (1f - age * 1.1f).coerceIn(0f, 1f) * 0.85f
                        if (a > 0.01f) {
                            translate(px, py) {
                                rotate(
                                    f.spin * 57.3f + age * 160f * (if (f.drift >= 0f) 1f else -1f),
                                    Offset.Zero
                                ) {
                                    drawFeather(u0 * f.size, Color(0xFFF4F7FB).copy(alpha = a))
                                }
                            }
                        }
                    }
                }

                val g = (1f - t) * 0.16f * cageIn.value
                if (g > 0.005f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = g), Color.Transparent),
                            center = pos,
                            radius = uDove * 4f,
                        ),
                        radius = uDove * 4f,
                        center = pos,
                    )
                }
                drawTheDove()
            }
        }

        Text(
            text = S(R.string.with_najione_stay_connected_always_whatever_the),
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 24.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .offset(y = 135.dp)
                .padding(horizontal = 36.dp)
                .alpha(taglineAlpha.value),
        )
    }
}

private data class Feather(val t: Float, val drift: Float, val spin: Float, val size: Float)

private fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private fun mixColor(a: Color, b: Color, t: Float): Color {
    val k = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * k,
        green = a.green + (b.green - a.green) * k,
        blue = a.blue + (b.blue - a.blue) * k,
        alpha = a.alpha + (b.alpha - a.alpha) * k,
    )
}

/** Quadratic Bezier point; the dove's flight path. */
private fun bez(a: Offset, c: Offset, e: Offset, t: Float): Offset {
    val k = 1f - t
    return Offset(
        k * k * a.x + 2f * k * t * c.x + t * t * e.x,
        k * k * a.y + 2f * k * t * c.y + t * t * e.y,
    )
}

/** Direction of travel along [bez]; the dove tilts its nose along it. */
private fun bezTangent(a: Offset, c: Offset, e: Offset, t: Float): Offset {
    val k = 1f - t
    return Offset(
        2f * k * (c.x - a.x) + 2f * t * (e.x - c.x),
        2f * k * (c.y - a.y) + 2f * t * (e.y - c.y),
    )
}

private fun Path.quad(x0: Float, y0: Float, qx: Float, qy: Float, x1: Float, y1: Float) {
    cubicTo(
        x0 + (qx - x0) * 2f / 3f, y0 + (qy - y0) * 2f / 3f,
        x1 + (qx - x1) * 2f / 3f, y1 + (qy - y1) * 2f / 3f,
        x1, y1,
    )
}

/**
 * Ornate 3D-shaded vintage birdcage.
 * Keeps the exact anchor coordinates (perchY, halfW, shoulderY, baseY, door hinge)
 * so the dove remains aligned while gaining metallic depth, back bars, filigree, and wood perch.
 */
private fun DrawScope.drawCage(
    cx: Float, cy: Float, m: Float,
    color: Color, alpha: Float, door: Float,
) {
    if (alpha <= 0f) return
    val halfW = m * 0.20f
    val baseY = cy + m * 0.30f
    val shoulderY = cy - m * 0.10f
    val apexY = cy - m * 0.34f
    val ringR = m * 0.032f
    val hookY = apexY - m * 0.075f
    val perchY = cy + m * 0.16f
    val stroke = m * 0.015f

    val c = color.copy(alpha = alpha)
    val hi = mixColor(color, Color.White, 0.45f).copy(alpha = alpha)
    val sh = mixColor(color, Color(0xFF0A101C), 0.52f).copy(alpha = alpha)
    val backC = mixColor(color, Color(0xFF0A101C), 0.30f).copy(alpha = alpha * 0.26f)
    val metalBrush = Brush.linearGradient(
        colors = listOf(hi, c, sh),
        start = Offset(cx - halfW, apexY),
        end = Offset(cx + halfW, baseY),
    )

    val ellipseH = m * 0.052f

    // 1) Hanging chain links from above the screen down to the ring.
    val chainTop = -m * 0.55f
    val chainBottom = hookY - ringR * 0.9f
    val linkStep = m * 0.036f
    var ly = chainTop
    var idx = 0
    while (ly < chainBottom) {
        val linkH = linkStep * 0.85f
        val linkW = if (idx % 2 == 0) stroke * 1.3f else stroke * 0.55f
        drawOval(
            color = c.copy(alpha = alpha * 0.58f),
            topLeft = Offset(cx - linkW, ly),
            size = Size(linkW * 2f, linkH),
            style = Stroke(width = stroke * 0.48f),
        )
        ly += linkStep * 0.68f
        idx++
    }

    // Double hanging ring & sculpted crown finial at apex.
    drawCircle(
        brush = metalBrush,
        radius = ringR,
        center = Offset(cx, hookY),
        style = Stroke(width = stroke * 0.95f),
    )
    drawCircle(
        color = hi.copy(alpha = alpha * 0.55f),
        radius = ringR * 0.72f,
        center = Offset(cx, hookY),
        style = Stroke(width = stroke * 0.32f),
    )
    drawLine(
        brush = metalBrush,
        start = Offset(cx, hookY + ringR),
        end = Offset(cx, apexY),
        strokeWidth = stroke * 1.15f,
        cap = StrokeCap.Round,
    )
    // Crown collar & finial cap.
    drawOval(
        brush = metalBrush,
        topLeft = Offset(cx - m * 0.026f, apexY - m * 0.018f),
        size = Size(m * 0.052f, m * 0.030f),
    )
    drawRoundRect(
        brush = metalBrush,
        topLeft = Offset(cx - m * 0.036f, apexY + m * 0.008f),
        size = Size(m * 0.072f, m * 0.014f),
        cornerRadius = CornerRadius(m * 0.007f),
    )

    // 2) Back perspective arcs & back bars (for 3D cylindrical depth).
    drawArc(
        color = backC,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(cx - halfW, shoulderY - ellipseH / 2f),
        size = Size(halfW * 2f, ellipseH),
        style = Stroke(width = stroke * 0.7f),
    )
    drawArc(
        color = backC,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(cx - halfW, baseY - ellipseH / 2f),
        size = Size(halfW * 2f, ellipseH),
        style = Stroke(width = stroke * 0.7f),
    )
    for (b in floatArrayOf(-0.76f, -0.28f, 0.28f, 0.76f)) {
        val x = cx + halfW * b
        val backDome = Path().apply {
            moveTo(x, shoulderY - ellipseH * 0.25f)
            quad(
                x, shoulderY - ellipseH * 0.25f,
                x, apexY + (shoulderY - apexY) * 0.24f,
                cx, apexY + m * 0.01f
            )
        }
        drawPath(backDome, backC, style = Stroke(width = stroke * 0.65f, cap = StrokeCap.Round))
        drawLine(
            color = backC,
            start = Offset(x, shoulderY - ellipseH * 0.25f),
            end = Offset(x, baseY - ellipseH * 0.25f),
            strokeWidth = stroke * 0.65f,
            cap = StrokeCap.Round,
        )
    }

    // 3) Wooden perch (behind front bars, softly shaded like natural warm wood).
    val woodBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFC59B76).copy(alpha = alpha * 0.92f),
            Color(0xFF7D573B).copy(alpha = alpha * 0.95f),
        ),
        startY = perchY - stroke * 0.8f,
        endY = perchY + stroke * 0.8f,
    )
    drawRoundRect(
        brush = woodBrush,
        topLeft = Offset(cx - halfW, perchY - stroke * 0.62f),
        size = Size(halfW * 2f, stroke * 1.24f),
        cornerRadius = CornerRadius(stroke * 0.6f),
    )
    // Perch brass end-caps.
    drawCircle(color = hi, radius = stroke * 0.75f, center = Offset(cx - halfW, perchY))
    drawCircle(color = sh, radius = stroke * 0.75f, center = Offset(cx + halfW, perchY))

    // 4) Front dome bars with metallic gradient & specular edge.
    val domeLine = Stroke(width = stroke * 0.95f, cap = StrokeCap.Round)
    for (b in floatArrayOf(-1f, -0.5f, 0f, 0.5f, 1f)) {
        val x = cx + halfW * b
        val p = Path().apply {
            moveTo(x, shoulderY)
            quad(x, shoulderY, x, apexY + (shoulderY - apexY) * 0.28f, cx, apexY + m * 0.012f)
        }
        drawPath(p, metalBrush, style = domeLine)
    }

    // Front shoulder ring + ornamental filigree wave band.
    val bandY = shoulderY + m * 0.026f
    drawArc(
        brush = metalBrush,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(cx - halfW, shoulderY - ellipseH / 2f),
        size = Size(halfW * 2f, ellipseH),
        style = Stroke(width = stroke * 1.05f, cap = StrokeCap.Round),
    )
    drawArc(
        brush = metalBrush,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(cx - halfW, bandY - ellipseH * 0.45f),
        size = Size(halfW * 2f, ellipseH * 0.9f),
        style = Stroke(width = stroke * 0.75f, cap = StrokeCap.Round),
    )
    // Delicate filigree loops between shoulder ring and band.
    val loopCount = 8
    val segW = (halfW * 2f) / loopCount
    for (i in 0 until loopCount) {
        val lx0 = cx - halfW + i * segW
        drawArc(
            color = c.copy(alpha = alpha * 0.65f),
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(lx0, shoulderY + m * 0.004f),
            size = Size(segW, m * 0.024f),
            style = Stroke(width = stroke * 0.48f),
        )
    }

    // 5) Fixed front body bars (middle bar belongs to the door).
    for (b in floatArrayOf(-1f, -0.5f, 0.5f, 1f)) {
        val x = cx + halfW * b
        drawLine(
            brush = metalBrush,
            start = Offset(x, shoulderY),
            end = Offset(x, baseY),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        // Subtle sunlit wire edge on left-facing side.
        drawLine(
            color = hi.copy(alpha = alpha * 0.40f),
            start = Offset(x - stroke * 0.25f, shoulderY + m * 0.03f),
            end = Offset(x - stroke * 0.25f, baseY - m * 0.01f),
            strokeWidth = stroke * 0.22f,
            cap = StrokeCap.Round,
        )
    }

    // 6) Stepped 3D base tray with rim, bevel shadow, and foot ring.
    drawArc(
        brush = metalBrush,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(cx - halfW, baseY - ellipseH / 2f),
        size = Size(halfW * 2f, ellipseH),
        style = Stroke(width = stroke * 0.95f, cap = StrokeCap.Round),
    )
    val trayBrush = Brush.verticalGradient(
        colors = listOf(hi, c, sh),
        startY = baseY,
        endY = baseY + m * 0.048f,
    )
    drawRoundRect(
        brush = trayBrush,
        topLeft = Offset(cx - halfW * 1.12f, baseY),
        size = Size(halfW * 2.24f, m * 0.036f),
        cornerRadius = CornerRadius(m * 0.018f),
    )
    // Lower tray foot lip.
    drawRoundRect(
        color = sh,
        topLeft = Offset(cx - halfW * 1.02f, baseY + m * 0.032f),
        size = Size(halfW * 2.04f, m * 0.014f),
        cornerRadius = CornerRadius(m * 0.007f),
    )
    // Specular highlight along top front of the tray.
    drawLine(
        color = hi.copy(alpha = alpha * 0.55f),
        start = Offset(cx - halfW * 0.98f, baseY + m * 0.007f),
        end = Offset(cx + halfW * 0.98f, baseY + m * 0.007f),
        strokeWidth = stroke * 0.35f,
        cap = StrokeCap.Round,
    )

    // 7) Arched vintage door with hinges, center bar, and latch ring.
    val hingeX = cx - halfW * 0.5f
    val sx = cos(Math.toRadians(door * 86.0)).toFloat()
    val doorAlpha = alpha * (1f - 0.22f * door)
    val dc = color.copy(alpha = doorAlpha)
    val dHi = mixColor(color, Color.White, 0.45f).copy(alpha = doorAlpha)
    val top = shoulderY + m * 0.038f
    val bot = baseY - m * 0.012f
    val archR = (halfW - stroke) * 0.5f

    scale(scaleX = sx, scaleY = 1f, pivot = Offset(hingeX, (shoulderY + baseY) / 2f)) {
        val framePath = Path().apply {
            moveTo(hingeX + stroke * 0.45f, bot)
            lineTo(hingeX + stroke * 0.45f, top + archR)
            quad(
                hingeX + stroke * 0.45f, top,
                cx, top,
                hingeX + halfW - stroke * 0.45f, top + archR,
            )
            lineTo(hingeX + halfW - stroke * 0.45f, bot)
            close()
        }
        drawPath(
            framePath,
            dc,
            style = Stroke(width = stroke * 0.85f, join = StrokeJoin.Round),
        )
        // Center bar & decorative horizontal cross-wire.
        drawLine(dc, Offset(cx, top + stroke * 0.5f), Offset(cx, bot), strokeWidth = stroke * 0.85f)
        drawLine(
            dc.copy(alpha = doorAlpha * 0.75f),
            Offset(hingeX + stroke * 0.5f, (top + bot) * 0.58f),
            Offset(hingeX + halfW - stroke * 0.5f, (top + bot) * 0.58f),
            strokeWidth = stroke * 0.55f,
        )
        // Latch knob & tiny latch loop.
        drawCircle(
            color = dHi,
            radius = stroke * 0.95f,
            center = Offset(hingeX + halfW - stroke * 1.9f, (top + bot) * 0.5f),
            style = Stroke(width = stroke * 0.45f),
        )
        drawCircle(
            color = dc,
            radius = stroke * 0.45f,
            center = Offset(hingeX + halfW - stroke * 1.9f, (top + bot) * 0.5f),
        )
    }

    // Hinge barrels fixed on the left doorpost.
    for (hy in floatArrayOf(top + archR + m * 0.04f, bot - m * 0.05f)) {
        drawRoundRect(
            color = hi,
            topLeft = Offset(hingeX - stroke * 0.65f, hy - stroke * 0.8f),
            size = Size(stroke * 1.3f, stroke * 1.6f),
            cornerRadius = CornerRadius(stroke * 0.4f),
        )
    }
}

/**
 * Lifelike white dove facing right, centred on its own origin.
 * Keeps exact [u], [theta], [wingLen], [alpha], and [feet] contract, with feet touching y = +0.64u.
 */
private fun DrawScope.drawDove(u: Float, theta: Float, wingLen: Float, alpha: Float, feet: Float) {
    if (alpha <= 0f || u <= 0f) return
    val a = alpha.coerceIn(0f, 1f)

    val whitePure = Color(0xFFFFFFFF).copy(alpha = a)
    val ivory = Color(0xFFF6F9FD).copy(alpha = a)
    val softBlue = Color(0xFFD9E3F0).copy(alpha = a)
    val shadowPlumage = Color(0xFFB5C4D9).copy(alpha = a)
    val farWingTop = Color(0xFFD2DCEB).copy(alpha = a)
    val farWingTip = Color(0xFF9FB2CC).copy(alpha = a)
    val featherLine = Color(0xFF8FA4C2).copy(alpha = a * 0.42f)

    val cereColor = Color(0xFFF2C6BE).copy(alpha = a)
    val beakHi = Color(0xFFEFAC7A).copy(alpha = a)
    val beakDark = Color(0xFFC5794E).copy(alpha = a)
    val legColor = Color(0xFFE59B82).copy(alpha = a * feet)
    val legShadow = Color(0xFFB96E58).copy(alpha = a * feet)
    val eyeRing = Color(0xFFDCE4EF).copy(alpha = a)
    val eyeDark = Color(0xFF141B29).copy(alpha = a)

    // 1) Articulated legs & toes gripping the perch at y = +0.64u.
    if (feet > 0.01f) {
        // Far leg.
        val farLeg = Path().apply {
            moveTo(0.06f * u, 0.36f * u)
            lineTo(0.02f * u, 0.50f * u)
            lineTo(0.11f * u, 0.62f * u)
        }
        drawPath(farLeg, legShadow, style = Stroke(width = u * 0.055f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawLine(legShadow, Offset(0.11f * u, 0.62f * u), Offset(0.20f * u, 0.65f * u), strokeWidth = u * 0.042f, cap = StrokeCap.Round)

        // Near leg + front and back toes.
        val nearLeg = Path().apply {
            moveTo(-0.10f * u, 0.36f * u)
            lineTo(-0.15f * u, 0.51f * u)
            lineTo(-0.06f * u, 0.63f * u)
        }
        drawPath(nearLeg, legColor, style = Stroke(width = u * 0.062f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawLine(legColor, Offset(-0.06f * u, 0.63f * u), Offset(0.05f * u, 0.66f * u), strokeWidth = u * 0.044f, cap = StrokeCap.Round)
        drawLine(legColor, Offset(-0.06f * u, 0.63f * u), Offset(-0.14f * u, 0.65f * u), strokeWidth = u * 0.040f, cap = StrokeCap.Round)
    }

    val shoulderX = 0.08f * u
    val shoulderY = -0.18f * u

    // 2) Far wing (behind body, darker/cooler for depth).
    translate(shoulderX, shoulderY) {
        rotate(theta + 14f, Offset.Zero) {
            scale(scaleX = 0.92f * wingLen, scaleY = 0.92f, pivot = Offset.Zero) {
                drawDetailedWing(
                    u = u,
                    topColor = farWingTop,
                    midColor = shadowPlumage,
                    tipColor = farWingTip,
                    ribColor = featherLine,
                    isNear = false,
                )
            }
        }
    }

    // 3) Layered, fanned tail feathers.
    val tailBrush = Brush.linearGradient(
        colors = listOf(softBlue, shadowPlumage),
        start = Offset(-0.85f * u, 0.22f * u),
        end = Offset(-1.68f * u, 0.26f * u),
    )
    for (idx in 0..2) {
        val ySpread = (idx - 1) * 0.13f * u
        val lenBias = if (idx == 1) 1.06f else 0.95f
        val tailFeather = Path().apply {
            moveTo(-0.80f * u, 0.16f * u + ySpread * 0.35f)
            cubicTo(
                -1.15f * u, 0.12f * u + ySpread * 0.65f,
                -1.45f * u, 0.14f * u + ySpread * 0.95f,
                -1.64f * u * lenBias, 0.20f * u + ySpread,
            )
            cubicTo(
                -1.50f * u * lenBias, 0.31f * u + ySpread,
                -1.18f * u, 0.34f * u + ySpread * 0.6f,
                -0.82f * u, 0.32f * u + ySpread * 0.3f,
            )
            close()
        }
        drawPath(tailFeather, tailBrush)
        drawPath(tailFeather, featherLine, style = Stroke(width = u * 0.018f, join = StrokeJoin.Round))
    }

    // 4) Seamless head + neck + breast + torso contour with soft 3D plumage gradient.
    val bodyPath = Path().apply {
        moveTo(0.60f * u, -0.75f * u) // Crown top
        cubicTo(0.75f * u, -0.76f * u, 0.87f * u, -0.67f * u, 0.88f * u, -0.56f * u) // Forehead to cere
        cubicTo(0.86f * u, -0.45f * u, 0.77f * u, -0.39f * u, 0.75f * u, -0.26f * u) // Chin & throat
        cubicTo(0.82f * u, -0.02f * u, 0.65f * u, 0.38f * u, 0.24f * u, 0.49f * u) // Full proud breast
        cubicTo(-0.20f * u, 0.59f * u, -0.68f * u, 0.44f * u, -1.02f * u, 0.29f * u) // Belly to undertail
        lineTo(-0.98f * u, 0.12f * u) // Tail base
        cubicTo(-0.60f * u, -0.04f * u, -0.22f * u, -0.34f * u, 0.16f * u, -0.38f * u) // Back contour
        cubicTo(0.36f * u, -0.40f * u, 0.43f * u, -0.74f * u, 0.60f * u, -0.75f * u) // Nape to crown
        close()
    }
    val bodyBrush = Brush.radialGradient(
        colors = listOf(whitePure, ivory, softBlue, shadowPlumage),
        center = Offset(0.45f * u, -0.25f * u),
        radius = 1.65f * u,
    )
    drawPath(bodyPath, bodyBrush)

    // Subtle soft breast highlight for rounded chest volume.
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(whitePure.copy(alpha = a * 0.75f), Color.Transparent),
            center = Offset(0.46f * u, 0.04f * u),
            radius = 0.42f * u,
        ),
        topLeft = Offset(0.10f * u, -0.28f * u),
        size = Size(0.68f * u, 0.65f * u),
    )

    // 5) Realistic dove beak (upper & lower mandible), cere, and glossy eye with catchlight.
    // Upper & lower beak.
    val upperBeak = Path().apply {
        moveTo(0.85f * u, -0.58f * u)
        cubicTo(0.98f * u, -0.57f * u, 1.09f * u, -0.53f * u, 1.15f * u, -0.49f * u)
        lineTo(0.86f * u, -0.50f * u)
        close()
    }
    val lowerBeak = Path().apply {
        moveTo(0.85f * u, -0.50f * u)
        lineTo(1.11f * u, -0.49f * u)
        cubicTo(1.01f * u, -0.45f * u, 0.91f * u, -0.45f * u, 0.84f * u, -0.47f * u)
        close()
    }
    drawPath(
        upperBeak,
        Brush.linearGradient(
            listOf(beakHi, beakDark),
            Offset(0.85f * u, -0.58f * u),
            Offset(1.15f * u, -0.49f * u),
        ),
    )
    drawPath(lowerBeak, beakDark)

    // Soft fleshy cere at the base of the dove's beak.
    drawOval(
        color = cereColor,
        topLeft = Offset(0.81f * u, -0.60f * u),
        size = Size(0.11f * u, 0.08f * u),
    )

    // Eye: subtle pale eye-ring, dark iris, crisp white catchlight.
    val eyeCenter = Offset(0.71f * u, -0.60f * u)
    drawCircle(color = eyeRing, radius = 0.062f * u, center = eyeCenter)
    drawCircle(color = eyeDark, radius = 0.046f * u, center = eyeCenter)
    drawCircle(
        color = whitePure,
        radius = 0.017f * u,
        center = Offset(eyeCenter.x - 0.016f * u, eyeCenter.y - 0.016f * u),
    )

    // 6) Near wing (layered primaries + upper coverts + feather rib details).
    translate(shoulderX, shoulderY) {
        rotate(theta, Offset.Zero) {
            scale(scaleX = wingLen, scaleY = 1f, pivot = Offset.Zero) {
                drawDetailedWing(
                    u = u,
                    topColor = whitePure,
                    midColor = ivory,
                    tipColor = softBlue,
                    ribColor = featherLine,
                    isNear = true,
                )
            }
        }
    }
}

/**
 * Multi-layered feathered wing: primary flight feathers with scalloped tips,
 * delicate feather-vane lines, and an overlapping upper covert plumage layer.
 */
private fun DrawScope.drawDetailedWing(
    u: Float,
    topColor: Color,
    midColor: Color,
    tipColor: Color,
    ribColor: Color,
    isNear: Boolean,
) {
    val primaries = wingPath(u)
    val wingBrush = Brush.linearGradient(
        colors = listOf(topColor, midColor, tipColor),
        start = Offset(0.05f * u, -0.45f * u),
        end = Offset(-1.45f * u, 0.25f * u),
    )
    drawPath(primaries, wingBrush)

    // Subtle feather separation ribs radiating toward the scalloped primary tips.
    val ribTargets = arrayOf(
        Offset(-1.35f * u, -0.10f * u),
        Offset(-1.14f * u, 0.06f * u),
        Offset(-0.92f * u, 0.18f * u),
        Offset(-0.66f * u, 0.25f * u),
        Offset(-0.36f * u, 0.24f * u),
    )
    for (pt in ribTargets) {
        val rib = Path().apply {
            moveTo(-0.18f * u, -0.08f * u)
            quad(-0.18f * u, -0.08f * u, pt.x * 0.52f, pt.y * 0.35f - 0.10f * u, pt.x, pt.y)
        }
        drawPath(rib, ribColor, style = Stroke(width = u * 0.020f, cap = StrokeCap.Round))
    }

    if (isNear) {
        drawPath(primaries, ribColor, style = Stroke(width = u * 0.024f, join = StrokeJoin.Round))
    }

    // Upper covert feather layer (adds realistic plumage thickness near the shoulder).
    val coverts = Path().apply {
        moveTo(0.08f * u, -0.02f * u)
        cubicTo(0f, -0.42f * u, -0.58f * u, -0.48f * u, -1.04f * u, -0.18f * u)
        quad(-1.04f * u, -0.18f * u, -0.90f * u, -0.03f * u, -0.74f * u, -0.03f * u)
        quad(-0.74f * u, -0.03f * u, -0.60f * u, 0.08f * u, -0.44f * u, 0.07f * u)
        quad(-0.44f * u, 0.07f * u, -0.26f * u, 0.13f * u, -0.10f * u, 0.08f * u)
        quad(-0.10f * u, 0.08f * u, 0.04f * u, 0.06f * u, 0.08f * u, -0.02f * u)
        close()
    }
    drawPath(
        coverts,
        Brush.linearGradient(
            colors = listOf(topColor, midColor),
            start = Offset(0.05f * u, -0.35f * u),
            end = Offset(-0.95f * u, 0.05f * u),
        ),
    )
    drawPath(coverts, ribColor, style = Stroke(width = u * 0.022f, join = StrokeJoin.Round))
}

/** Wing silhouette in its own coordinates: root at origin, pointing back (-x). */
private fun wingPath(u: Float): Path = Path().apply {
    moveTo(0.10f * u, 0f)
    cubicTo(0f, -0.55f * u, -0.80f * u, -0.65f * u, -1.55f * u, -0.18f * u)
    quad(-1.55f * u, -0.18f * u, -1.45f * u, 0.02f * u, -1.22f * u, 0.04f * u)
    quad(-1.22f * u, 0.04f * u, -1.20f * u, 0.18f * u, -1.00f * u, 0.20f * u)
    quad(-1.00f * u, 0.20f * u, -0.95f * u, 0.30f * u, -0.72f * u, 0.28f * u)
    quad(-0.72f * u, 0.28f * u, -0.55f * u, 0.36f * u, -0.30f * u, 0.28f * u)
    quad(-0.30f * u, 0.28f * u, -0.10f * u, 0.22f * u, 0.10f * u, 0f)
    close()
}

/** A realistic loose plumage feather with central quill (rachis) and soft vane. */
private fun DrawScope.drawFeather(len: Float, color: Color) {
    val p = Path().apply {
        moveTo(-len * 0.55f, 0f)
        cubicTo(-len * 0.22f, -len * 0.26f, len * 0.22f, -len * 0.24f, len * 0.52f, -len * 0.02f)
        cubicTo(len * 0.26f, len * 0.20f, -len * 0.18f, len * 0.21f, -len * 0.55f, 0f)
        close()
    }
    drawPath(p, color)
    drawLine(
        color = Color(0xFF9FB3CF).copy(alpha = color.alpha * 0.55f),
        start = Offset(-len * 0.52f, 0f),
        end = Offset(len * 0.44f, -len * 0.01f),
        strokeWidth = len * 0.045f,
        cap = StrokeCap.Round,
    )
}