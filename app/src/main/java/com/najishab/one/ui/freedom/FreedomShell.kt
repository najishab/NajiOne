package com.najishab.one.ui.freedom

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.najishab.one.store.tr
import com.najishab.one.ui.LocalSystemBottomPadding
import com.najishab.one.ui.home.HomeApp
import com.najishab.one.ui.home.HomeDestinations
import com.najishab.one.ui.nexus.NexusCatalog
import com.najishab.one.ui.nexus.rememberNexusSession
import com.najishab.one.widget.WidgetFormat

/** The four root destinations of the FREEDOM shell -- the mockup's four tabs. */
enum class FreedomPage { HOME, ENGINES, TOOLS, SETTINGS }

/**
 * The FREEDOM shell: Home, Engines and Cloud & Tools as pages of its own, with the docked bottom
 * bar that also leads to Settings. Settings is the app's existing hosted tab (all its sub-pages
 * and back handling), opened through [onOpen] exactly as Nexus does. Which page is showing is
 * owned by the caller, so coming back from a method's screen lands where the user left.
 */
@Composable
fun FreedomShell(
    page: FreedomPage,
    onPage: (FreedomPage) -> Unit,
    onOpen: (HomeApp) -> Unit,
    onOpenSetting: (String) -> Unit,
) {
    val session = rememberNexusSession()
    val settingsApp = remember { HomeDestinations.DOCK.first { it.id == "settings" } }
    val methodGroups = remember { NexusCatalog.methodGroups() }
    val toolGroups = remember { NexusCatalog.toolGroups() }
    val engineCount = remember { methodGroups.sumOf { it.apps.size } }

    Box(Modifier.fillMaxSize().freedomBackdrop()) {
        Crossfade(targetState = page, animationSpec = tween(260), label = "freedom page") { p ->
            when (p) {
                FreedomPage.ENGINES -> FreedomDirectory(
                    title = tr("موتورها", "Engines"),
                    subtitle = WidgetFormat.digits(tr("$engineCount موتور · گروه‌بندی بر اساس کار", "$engineCount engines · grouped by job")),
                    groups = methodGroups,
                    session = session,
                    showStatus = true,
                    onOpen = onOpen,
                    onOpenSetting = onOpenSetting,
                )
                FreedomPage.TOOLS -> FreedomDirectory(
                    title = tr("ابر و ابزارها", "Cloud & Tools"),
                    subtitle = tr("ابزارهای شبکه، ابر و راهنما", "Network, cloud and help tools"),
                    groups = toolGroups,
                    session = session,
                    showStatus = false,
                    onOpen = onOpen,
                )
                else -> FreedomHome(session = session, onOpen = onOpen, onPage = onPage)
            }
        }
        FreedomBottomBar(
            selected = page,
            onSelect = { target -> if (target == FreedomPage.SETTINGS) onOpen(settingsApp) else onPage(target) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** The mockup's docked tab bar: rgba(13,17,24,.96), a #232C3B hairline on top, four labelled tabs. */
@Composable
internal fun FreedomBottomBar(selected: FreedomPage, onSelect: (FreedomPage) -> Unit, modifier: Modifier = Modifier) {
    val bottom = LocalSystemBottomPadding.current
    Column(modifier.fillMaxWidth().background(Freedom.BarBg)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Freedom.Border))
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = bottom + 10.dp)) {
            FreedomPage.values().forEach { p ->
                val sel = p == selected
                val tint = if (sel) Freedom.Ok else Freedom.TabIdle
                Column(
                    Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(p) },
                        )
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        when (p) {
                            FreedomPage.HOME -> Icons.Default.Home
                            FreedomPage.ENGINES -> Icons.Default.Bolt
                            FreedomPage.TOOLS -> Icons.Default.Cloud
                            FreedomPage.SETTINGS -> Icons.Default.Settings
                        },
                        contentDescription = null, tint = tint, modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        when (p) {
                            FreedomPage.HOME -> tr("خانه", "Home")
                            FreedomPage.ENGINES -> tr("موتورها", "Engines")
                            FreedomPage.TOOLS -> tr("ابر و ابزار", "Cloud & Tools")
                            FreedomPage.SETTINGS -> tr("تنظیمات", "Settings")
                        },
                        color = tint, fontSize = 10.5.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )
                }
            }
        }
    }
}