package com.najishab.one.ui.nexus

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.najishab.one.store.tr
import com.najishab.one.ui.LocalSystemBottomPadding
import com.najishab.one.ui.home.HomeApp
import com.najishab.one.ui.home.HomeDestinations

/**
 * The NEXUS shell: Home, VPN Methods and Tools as pages of its own, with the bottom bar that also
 * leads to Settings.
 *
 * Settings is not a page here. It is the app's existing hosted tab, with all its sub-pages and its
 * own back handling, so the bar opens it through [onOpen] exactly as the old dock did.
 *
 * Which page is showing is owned by the caller ([page]) so that coming back from a method's screen
 * lands on the page the user left, not on Home.
 */
@Composable
fun NexusShell(
    page: NexusPage,
    onPage: (NexusPage) -> Unit,
    onOpen: (HomeApp) -> Unit,
    /** Settings on this route, its row already armed (SettingsIndex.prepare): a search result. */
    onOpenSetting: (String) -> Unit,
) {
    val session = rememberNexusSession()
    val settingsApp = remember { HomeDestinations.DOCK.first { it.id == "settings" } }
    val methodGroups = remember { NexusCatalog.methodGroups() }
    val toolGroups = remember { NexusCatalog.toolGroups() }

    Box(Modifier.fillMaxSize().nexusBackdrop()) {
        Crossfade(targetState = page, animationSpec = tween(260), label = "nexus page") { p ->
            when (p) {
                NexusPage.METHODS -> NexusDirectory(
                    title = tr("روش‌های اتصال", "VPN Methods"),
                    subtitle = tr("همهٔ روش‌های اتصال، در یک‌جا", "Every way to connect, in one place"),
                    groups = methodGroups,
                    session = session,
                    showStatus = true,
                    onOpen = onOpen,
                    onOpenSetting = onOpenSetting,
                )
                NexusPage.TOOLS -> NexusDirectory(
                    title = tr("ابزارها", "Tools"),
                    subtitle = tr("ابزارهای شبکه و پیکربندی", "Network and configuration tools"),
                    groups = toolGroups,
                    session = session,
                    showStatus = false,
                    onOpen = onOpen,
                )
                else -> NexusHome(
                    session = session,
                    onOpen = onOpen,
                    onChooseMethod = { onPage(NexusPage.METHODS) },
                )
            }
        }
        NexusBottomBar(
            selected = page,
            onSelect = { target ->
                if (target == NexusPage.SETTINGS) onOpen(settingsApp) else onPage(target)
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

private val NAV_ITEMS = listOf(NexusPage.HOME, NexusPage.METHODS, NexusPage.TOOLS, NexusPage.SETTINGS)

@Composable
internal fun NexusBottomBar(selected: NexusPage, onSelect: (NexusPage) -> Unit, modifier: Modifier = Modifier) {
    val bottom = LocalSystemBottomPadding.current
    val shape = RoundedCornerShape(34.dp)
    val pos = animateFloatAsState(NAV_ITEMS.indexOf(selected).toFloat(), tween(380, easing = FastOutSlowInEasing), label = "nav position")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = bottom + 12.dp)
            .height(76.dp)
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xE60A1233), Color(0xE6120A33)),
                )
            )
            .border(1.dp, Brush.horizontalGradient(listOf(Nexus.Cyan.copy(alpha = 0.55f), Nexus.Violet.copy(alpha = 0.55f))), shape),
    ) {
        // The selection indicator: a pool of light and a bright tick that glide between items.
        Canvas(Modifier.fillMaxSize()) {
            val rtl = layoutDirection == LayoutDirection.Rtl
            val w = size.width / NAV_ITEMS.size
            val p = pos.value
            val cx = if (rtl) size.width - (p + 0.5f) * w else (p + 0.5f) * w
            val center = Offset(cx, size.height * 0.55f)
            drawCircle(
                Brush.radialGradient(
                    listOf(Nexus.Cyan.copy(alpha = 0.32f), Nexus.Violet.copy(alpha = 0.10f), Color.Transparent),
                    center = center, radius = w * 0.8f,
                ),
                radius = w * 0.8f, center = center,
            )
            drawLine(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, Nexus.Cyan, Color.Transparent),
                    startX = cx - w * 0.3f, endX = cx + w * 0.3f,
                ),
                Offset(cx - w * 0.3f, 2.dp.toPx()), Offset(cx + w * 0.3f, 2.dp.toPx()),
                strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round,
            )
        }
        Row(Modifier.fillMaxSize()) {
            NAV_ITEMS.forEach { item ->
                NavItem(
                    page = item,
                    selected = item == selected,
                    onClick = { onSelect(item) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun NavItem(page: NexusPage, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val tint = if (selected) Nexus.Text else Nexus.Muted
    val label = when (page) {
        NexusPage.HOME -> tr("خانه", "Home")
        NexusPage.METHODS -> tr("روش‌ها", "VPN Methods")
        NexusPage.TOOLS -> tr("ابزارها", "Tools")
        NexusPage.SETTINGS -> tr("تنظیمات", "Settings")
    }
    Column(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Tab,
            onClick = onClick,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        when (page) {
            NexusPage.HOME -> Icon(Icons.Default.Home, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
            NexusPage.SETTINGS -> Icon(Icons.Default.Settings, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
            NexusPage.METHODS -> Canvas(Modifier.size(26.dp)) { drawRingsIcon(tint) }
            NexusPage.TOOLS -> Canvas(Modifier.size(26.dp)) { drawPulseIcon(tint) }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = tint,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

private fun DrawScope.drawRingsIcon(tint: Color) {
    val c = center
    drawCircle(tint, radius = size.minDimension * 0.46f, center = c, style = Stroke(1.8.dp.toPx()))
    drawCircle(tint, radius = size.minDimension * 0.27f, center = c, style = Stroke(1.8.dp.toPx()))
    drawCircle(tint, radius = size.minDimension * 0.08f, center = c)
}

private fun DrawScope.drawPulseIcon(tint: Color) {
    val w = size.width
    val h = size.height
    val p = Path().apply {
        moveTo(w * 0.02f, h * 0.55f)
        lineTo(w * 0.28f, h * 0.55f)
        lineTo(w * 0.40f, h * 0.18f)
        lineTo(w * 0.58f, h * 0.86f)
        lineTo(w * 0.70f, h * 0.45f)
        lineTo(w * 0.98f, h * 0.45f)
    }
    drawPath(p, tint, style = Stroke(1.9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}
