package com.najishab.one.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.najishab.one.store.tr
import com.najishab.one.ui.freedom.Freedom
import com.najishab.one.ui.home.frostedGlass
import com.najishab.one.ui.nexus.Nexus
import com.najishab.one.ui.skin.AppSkin
import com.najishab.one.ui.skin.SkinPrefs

/**
 * Settings > Display > App skin: two preview cards, one per skin. Selecting applies immediately
 * through [SkinPrefs.set] (no restart) and persists. The previews are static drawings of each
 * skin's home -- the caged bird on a night sky, and the power button with its rings.
 */
@Composable
internal fun SkinPickerPage(backLabel: String, onBack: () -> Unit) {
    val context = LocalContext.current
    IosScreen(title = tr("پوسته برنامه", "App skin"), onBack = onBack, backLabel = backLabel) {
        Spacer(Modifier.height(14.dp))
        SkinCard(
            name = "Freedom",
            desc = tr("پرنده‌ای در قفس؛ با اتصال، قفس می‌شکند و سپیده‌دم می‌رسد", "A caged bird; connect and the cage breaks open into dawn"),
            accent = Freedom.Ok,
            selected = SkinPrefs.skin == AppSkin.FREEDOM,
            onClick = { SkinPrefs.set(context, AppSkin.FREEDOM) },
            preview = { drawFreedomPreview() },
        )
        Spacer(Modifier.height(12.dp))
        SkinCard(
            name = "Nexus",
            desc = tr("دکمهٔ توان و حلقه‌های انرژی بر فضای عمیق — پوستهٔ پیشین برنامه", "The power button and energy rings over deep space — the previous look"),
            accent = Nexus.Blue,
            selected = SkinPrefs.skin == AppSkin.NEXUS,
            onClick = { SkinPrefs.set(context, AppSkin.NEXUS) },
            preview = { drawNexusPreview() },
        )
        Spacer(Modifier.height(12.dp))
        SkinCard(
            name = "Mosaic",
            desc = tr("کاشی‌های رنگی روی والپیپر با داک شیشه‌ای — چیدمان کلاسیک برنامه", "Colourful tiles on your wallpaper with a glass dock — the classic board"),
            accent = Color(0xFF007AFF),
            selected = SkinPrefs.skin == AppSkin.MOSAIC,
            onClick = { SkinPrefs.set(context, AppSkin.MOSAIC) },
            preview = { drawMosaicPreview() },
        )
        Text(
            tr("بلافاصله اعمال می‌شود و برای دفعات بعد ذخیره می‌ماند. هر سه پوسته یک منطق اتصال و یک فهرست روش‌ها دارند.",
               "Applies immediately and is kept for next time. All three skins share the same connect logic and the same list of methods."),
            color = Ios.SecondaryLabel, fontSize = 13.sp,
            modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 10.dp),
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun SkinCard(name: String, desc: String, accent: Color, selected: Boolean, onClick: () -> Unit, preview: DrawScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .frostedGlass(shape)
            .then(if (selected) Modifier.border(2.dp, accent, shape) else Modifier)
            .semantics { this.selected = selected; this.role = Role.RadioButton }
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(12.dp)
    ) {
        Canvas(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(14.dp))) { preview() }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(name, color = Ios.Label, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (selected) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF06231A), modifier = Modifier.size(14.dp))
                }
            }
        }
        Text(desc, color = Ios.SecondaryLabel, fontSize = 12.5.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

/** Freedom in miniature: night sky, a low sun, hills, the steel cage and the blue bird inside. */
private fun DrawScope.drawFreedomPreview() {
    drawRect(Brush.verticalGradient(0f to Color(0xFF070F1E), 0.55f to Color(0xFF0D1930), 1f to Color(0xFF1A2740)))
    val sunC = Offset(size.width * 0.5f, size.height + 6.dp.toPx())
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFFFFEDB9), Color(0xFFFFB35C).copy(alpha = 0.4f), Color.Transparent), center = sunC, radius = 46.dp.toPx()),
        radius = 46.dp.toPx(), center = sunC, alpha = 0.5f,
    )
    drawRect(Freedom.HillFar, topLeft = Offset(0f, size.height - 16.dp.toPx()), size = Size(size.width, 16.dp.toPx()))
    drawRect(Freedom.HillNear, topLeft = Offset(0f, size.height - 9.dp.toPx()), size = Size(size.width, 9.dp.toPx()))
    val cw = 52.dp.toPx()
    val ch = cw * 260f / 220f
    val cx = size.width * 0.5f
    val top = size.height * 0.46f - ch * 0.5f
    val steel = Freedom.Cage
    val sw = 1.6.dp.toPx()
    // dome + bars + base, a sketch of the real cage
    drawArc(steel, startAngle = 180f, sweepAngle = 180f, useCenter = false,
        topLeft = Offset(cx - cw / 2, top), size = Size(cw, cw), style = Stroke(sw, cap = StrokeCap.Round))
    for (i in 0..4) {
        val x = cx - cw / 2 + cw * i / 4f
        drawLine(steel, Offset(x, top + cw / 2), Offset(x, top + ch - 8.dp.toPx()), strokeWidth = sw, cap = StrokeCap.Round)
    }
    drawLine(steel, Offset(cx - cw / 2 - 3.dp.toPx(), top + ch - 8.dp.toPx()), Offset(cx + cw / 2 + 3.dp.toPx(), top + ch - 8.dp.toPx()), strokeWidth = sw, cap = StrokeCap.Round)
    drawCircle(steel, radius = 2.4.dp.toPx(), center = Offset(cx, top - 4.dp.toPx()), style = Stroke(sw))
    // the bird: a small origami triangle
    val b = Path().apply {
        moveTo(cx - 7.dp.toPx(), top + ch * 0.62f)
        lineTo(cx + 8.dp.toPx(), top + ch * 0.52f)
        lineTo(cx - 2.dp.toPx(), top + ch * 0.72f)
        close()
    }
    drawPath(b, Freedom.Bird)
    drawRoundRect(Freedom.Amber, topLeft = Offset(cx - 3.dp.toPx(), top + ch - 12.dp.toPx()), size = Size(6.dp.toPx(), 5.dp.toPx()), cornerRadius = CornerRadius(1.5.dp.toPx()))
}

/** Nexus in miniature: deep space, two rings and the power disc. */
private fun DrawScope.drawNexusPreview() {
    drawRect(Brush.verticalGradient(0f to Nexus.Space, 1f to Color(0xFF0A0620)))
    val c = Offset(size.width * 0.5f, size.height * 0.52f)
    val r = 22.dp.toPx()
    drawOval(Nexus.Cyan.copy(alpha = 0.5f), topLeft = Offset(c.x - r * 2.1f, c.y - r * 0.62f), size = Size(r * 4.2f, r * 1.24f), style = Stroke(1.4.dp.toPx()))
    drawOval(Nexus.Violet.copy(alpha = 0.5f), topLeft = Offset(c.x - r * 1.7f, c.y - r * 0.5f), size = Size(r * 3.4f, r * 1.0f), style = Stroke(1.4.dp.toPx()))
    drawCircle(
        Brush.radialGradient(listOf(Nexus.Blue, Nexus.Violet, Color(0xFF0A0A2E)), center = Offset(c.x - r * 0.3f, c.y - r * 0.35f), radius = r * 1.3f),
        radius = r, center = c,
    )
    drawCircle(Color.White.copy(alpha = 0.5f), radius = r, center = c, style = Stroke(1.4.dp.toPx()))
    val g = r * 0.32f
    drawArc(Color.White, startAngle = -60f, sweepAngle = 300f, useCenter = false,
        topLeft = Offset(c.x - g, c.y - g), size = Size(g * 2, g * 2), style = Stroke(r * 0.09f, cap = StrokeCap.Round))
    drawLine(Color.White, Offset(c.x, c.y - g * 1.2f), Offset(c.x, c.y - g * 0.1f), strokeWidth = r * 0.09f, cap = StrokeCap.Round)
}
/** Mosaic in miniature: the wallpaper, a 4x2 board of squircles, and the glass dock. */
private fun DrawScope.drawMosaicPreview() {
    drawRect(Brush.verticalGradient(0f to Color(0xFF24365C), 0.6f to Color(0xFF33456B), 1f to Color(0xFF121A2C)))
    val cols = 4; val rows = 2
    val pad = 12.dp.toPx(); val gap = 9.dp.toPx(); val dockH = 20.dp.toPx()
    val cellW = (size.width - pad * 2 - gap * (cols - 1)) / cols
    val cellH = (size.height - pad * 2 - gap * (rows - 1) - dockH - 8.dp.toPx()) / rows
    val cell = minOf(cellW, cellH).coerceAtLeast(6.dp.toPx())
    val gridW = cell * cols + gap * (cols - 1)
    val left = (size.width - gridW) / 2f
    val tints = listOf(
        Color(0xFF007AFF), Color(0xFF34C759), Color(0xFF5856D6), Color(0xFFFF9500),
        Color(0xFFFF2D55), Color(0xFF5AC8FA), Color(0xFFAF52DE), Color(0xFFFFCC00),
    )
    for (r in 0 until rows) for (c in 0 until cols) {
        val i = r * cols + c
        val x = left + c * (cell + gap); val y = pad + r * (cell + gap)
        val crr = CornerRadius(cell * 0.28f, cell * 0.28f)
        drawRoundRect(tints[i % tints.size], topLeft = Offset(x, y), size = Size(cell, cell), cornerRadius = crr)
        drawRoundRect(Color.White.copy(alpha = 0.16f), topLeft = Offset(x, y), size = Size(cell, cell * 0.42f), cornerRadius = crr)
    }
    val dockY = size.height - dockH - 8.dp.toPx()
    drawRoundRect(Color.White.copy(alpha = 0.16f), topLeft = Offset(pad, dockY), size = Size(size.width - pad * 2, dockH), cornerRadius = CornerRadius(dockH / 2f, dockH / 2f))
    for (c in 0 until 4) {
        val q = dockH * 0.6f
        val x = pad + (size.width - pad * 2) * (c + 0.5f) / 4f - q / 2f
        drawRoundRect(tints[c], topLeft = Offset(x, dockY + (dockH - q) / 2f), size = Size(q, q), cornerRadius = CornerRadius(q * 0.28f, q * 0.28f))
    }
}