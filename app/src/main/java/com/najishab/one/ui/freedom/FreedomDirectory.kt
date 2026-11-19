package com.najishab.one.ui.freedom

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.najishab.one.store.tr
import com.najishab.one.ui.LocalSystemBottomPadding
import com.najishab.one.ui.LocalSystemTopPadding
import com.najishab.one.ui.home.HomeApp
import com.najishab.one.ui.home.SearchMatcher
import com.najishab.one.ui.nexus.NexusGroup
import com.najishab.one.ui.nexus.NexusSession
import com.najishab.one.ui.nexus.describe
import com.najishab.one.ui.settings.SettingsIndex
import com.najishab.one.widget.WidgetFormat

/**
 * Freedom's Engines and Cloud & Tools pages: the SAME catalog Nexus shows (NexusCatalog over the
 * untouched HomeDestinations registry), restyled as the mockup's card rows. A row opens that
 * destination's page -- it does not select it. Search also finds Settings rows, as Nexus does.
 * Nothing is filtered out of the catalog; only the query narrows what is shown.
 */
@Composable
fun FreedomDirectory(
    title: String,
    subtitle: String,
    groups: List<NexusGroup>,
    session: NexusSession,
    showStatus: Boolean,
    onOpen: (HomeApp) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSetting: ((String) -> Unit)? = null,
) {
    val top = LocalSystemTopPadding.current
    val bottom = LocalSystemBottomPadding.current
    val context = LocalContext.current
    val res = context.resources
    var query by remember { mutableStateOf("") }
    val persian = remember(context) { SettingsIndex.localized(context, "fa") }
    val english = remember(context) { SettingsIndex.localized(context, "en") }
    val settingRows = remember(persian, english) {
        SettingsIndex.entries.map { it to it.fields { t -> t.both(persian, english) } }
    }
    val foundSettings = remember(query, settingRows, onOpenSetting) {
        if (onOpenSetting == null || query.isBlank()) emptyList()
        else SearchMatcher.rank(query, settingRows) { it.second }.map { it.first }.take(8)
    }
    val q = query.trim().lowercase()
    fun matches(app: HomeApp): Boolean {
        if (q.isEmpty()) return true
        val name = runCatching { res.getString(app.labelRes) }.getOrDefault(app.id).lowercase()
        return name.contains(q) || app.id.contains(q) || (describe(app.id)?.lowercase()?.contains(q) == true)
    }
    val shownGroups = groups.map { g -> NexusGroup(g.title, g.apps.filter(::matches)) }.filter { it.apps.isNotEmpty() }

    LazyColumn(
        modifier = modifier.fillMaxSize().freedomBackdrop(),
        contentPadding = PaddingValues(
            start = 18.dp, end = 18.dp,
            top = top + 14.dp, bottom = bottom + FREEDOM_NAV_RESERVE + 20.dp,
        ),
    ) {
        item(key = "title") {
            Column(Modifier.padding(bottom = 10.dp)) {
                Text(title, color = Freedom.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Freedom.Muted, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
            }
        }
        item(key = "search") {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp).freedomCard(RoundedCornerShape(16.dp))
                    .padding(horizontal = 13.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = Freedom.Muted, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(tr("جستجو…", "Search…"), color = Freedom.Muted, fontSize = 14.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Freedom.Text, fontSize = 14.sp),
                        cursorBrush = SolidColor(Freedom.Ok),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        shownGroups.forEach { group ->
            item(key = "g_" + group.title) {
                Text(
                    group.title.uppercase(), color = Freedom.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 0.9.sp, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp, start = 2.dp),
                )
            }
            itemsIndexed(group.apps, key = { _, app -> group.title + "/" + app.id }) { _, app ->
                FreedomRow(app, session.conn.method == app.id, session, showStatus) { onOpen(app) }
            }
        }
        if (foundSettings.isNotEmpty() && onOpenSetting != null) {
            item(key = "g_settings") {
                Text(
                    tr("تنظیمات", "Settings").uppercase(), color = Freedom.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 0.9.sp, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp, start = 2.dp),
                )
            }
            itemsIndexed(foundSettings, key = { i, _ -> "s_$i" }) { _, entry ->
                val rowTitle = entry.title.current(context)
                val where = (listOf(tr("تنظیمات", "Settings")) + entry.path.map { it.current(context) }).joinToString("  ›  ")
                Column(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp).freedomCard(RoundedCornerShape(18.dp))
                        .clickable(role = Role.Button) { onOpenSetting(SettingsIndex.prepare(context, entry)) }
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                ) {
                    Text(rowTitle, color = Freedom.Text, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(where, color = Freedom.Muted, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (shownGroups.isEmpty() && foundSettings.isEmpty()) {
            item(key = "empty") {
                Text(tr("چیزی پیدا نشد", "Nothing found"), color = Freedom.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 30.dp))
            }
        }
    }
}

@Composable
private fun FreedomRow(app: HomeApp, live: Boolean, session: NexusSession, showStatus: Boolean, onClick: () -> Unit) {
    val res = LocalContext.current.resources
    val name = remember(app.id) { runCatching { res.getString(app.labelRes) }.getOrDefault(app.id) }
    // 0 none, 1 live, 2 busy, 3 last -- the same derivation NexusDirectory uses.
    val status = when {
        !showStatus -> 0
        live && session.conn.up -> 1
        live && session.conn.busy -> 2
        app.id == session.last -> 3
        else -> 0
    }
    val accent = when (status) {
        1 -> Freedom.Ok
        2 -> Freedom.Amber
        3 -> Freedom.Cage
        else -> Freedom.Muted
    }
    val line = when (status) {
        1 -> tr("متصل", "Connected")
        2 -> tr("در حال اتصال", "Connecting")
        3 -> tr("آخرین روش استفاده‌شده", "Last used")
        else -> describe(app.id)
    }
    val shape = RoundedCornerShape(18.dp)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).freedomCard(shape)
            .then(if (status == 1) Modifier.border(1.dp, Freedom.Ok, shape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(app.tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Text(name.take(2).uppercase(), color = app.tint, fontSize = 13.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = Freedom.Text, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (line != null) {
                Text(line, color = if (status == 0) Freedom.Muted else accent, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (status != 0) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
        }
        Spacer(Modifier.width(6.dp))
        Icon(
            Icons.Default.ChevronRight, contentDescription = null, tint = Freedom.Muted,
            modifier = Modifier.size(18.dp).graphicsLayer { scaleX = if (rtl) -1f else 1f },
        )
    }
}