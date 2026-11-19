package com.najishab.one.ui.nexus

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.najishab.one.store.tr
import com.najishab.one.ui.LocalSystemBottomPadding
import com.najishab.one.ui.LocalSystemTopPadding
import com.najishab.one.ui.home.HomeApp
import com.najishab.one.ui.home.HomeDestinations
import com.najishab.one.ui.home.SearchMatcher
import com.najishab.one.ui.settings.SettingsEntry
import com.najishab.one.ui.settings.SettingsIndex
import com.najishab.one.widget.WidgetLive

/** One section of a NEXUS directory page. */
class NexusGroup(val title: String, val apps: List<HomeApp>)

/**
 * Where every destination of the old home board lives now.
 *
 * The registry is [HomeDestinations] (dock + grid), unchanged: nothing is re-declared here, so an
 * id cannot be lost. Tools and the named method groups are explicit lists; anything the lists do
 * not name -- including an entry added to the registry later -- lands in "Additional Methods"
 * automatically. Settings is the fourth root destination and is deliberately not listed.
 */
object NexusCatalog {
    private val TOOLS = listOf("scanner", "lan", "fixed_ip", "usage", "cloud", "sublink", "tutorial")
    private val TOOLS_NETWORK = listOf("scanner", "lan", "fixed_ip", "usage")
    private val TOOLS_CLOUD = listOf("cloud", "sublink", "configstudio")
    private val TOOLS_HELP = listOf("tutorial")
    private val TOOLS_MORE = listOf("scout", "store", "widgetstudio")

    private val PROTOCOLS = listOf(
        "masque", "wireguard", "warp", "warp_on_warp", "psiphon", "tor", "lantern", "geph",
        "urnetwork", "flux", "outline", "npv", "amnezia", "vpngate" , "openvpn"
    )
    private val PLATFORMS = listOf("fenix", "mae")
    private val SERVICES = listOf(
        "game", "iran", "fronting", "emergency_2", "emergency_3", "nodes"
    )

    private val all: List<HomeApp>
        get() = (HomeDestinations.DOCK + HomeDestinations.GRID_DEFAULT).distinctBy { it.id }.filter { it.id != "settings" }

    private fun pick(ids: List<String>): List<HomeApp> = ids.mapNotNull { id -> all.firstOrNull { it.id == id } }

    fun methodGroups(): List<NexusGroup> {
        val named = (PROTOCOLS + PLATFORMS + SERVICES + TOOLS).toSet()
        val rest = all.filter { it.id !in named }
        return listOf(
            NexusGroup(tr("پروتکل‌های رایگان وی پی ان", "Free VPN Protocols"), pick(PROTOCOLS)),
            NexusGroup(tr("پلتفرم‌های نیازمند به عضویت", "Join to use VPN"), pick(PLATFORMS)),
            NexusGroup(tr("سرویس‌های اتصال", "Connection Services"), pick(SERVICES)),
            NexusGroup(tr("روش‌های دیگر", "Additional Methods"), rest),
        ).filter { it.apps.isNotEmpty() }
    }

    fun toolGroups(): List<NexusGroup> = listOf(
        NexusGroup(tr("ابزارهای شبکه", "Network Tools"), pick(TOOLS_NETWORK)),
        NexusGroup(tr("ابر و کانفیگ", "Cloud & Configs"), pick(TOOLS_CLOUD)),
        NexusGroup(tr("راهنما", "Help"), pick(TOOLS_HELP)),
        NexusGroup(tr("سایر ابزارها", "Another Tools"), pick(TOOLS_MORE)),
    ).filter { it.apps.isNotEmpty() }
}

/** Short, factual one-liners taken from what each destination already is. */
internal fun describe(id: String): String? = when (id) {
    "scout" -> tr("کدام روش روی این خط کار می‌کند", "Which method works on this line")
    "masque" -> tr("تونل MASQUE از لبهٔ Cloudflare", "MASQUE tunnel to Cloudflare's edge")
    "wireguard" -> tr("WireGuard تا لبهٔ Cloudflare", "WireGuard to Cloudflare's edge")
    "warp_on_warp" -> tr("WARP داخل WARP", "WARP stacked inside WARP")
    "warp" -> tr("WARP روی موتور مستقل", "Plain WARP on its own engine")
    "psiphon" -> tr("شبکهٔ Psiphon", "The Psiphon network")
    "tor" -> tr("شبکهٔ Tor", "The Tor network")
    "lantern" -> tr("هستهٔ اختصاصی Lantern", "Lantern's own core")
    "urnetwork" -> tr("شبکهٔ دستگاه‌های URnetwork", "URnetwork's provider network")
    "geph" -> tr("شبکهٔ اختصاصی Geph", "Geph's own network")
    "flux" -> tr("کشور، نسخهٔ IP، اتصال", "Country, IP version, connect")
    "fenix" -> tr("VPN رایگان حساب Firefox", "Free VPN from your Firefox account")
    "amnezia" -> tr("سرورهای رایگان و فایل‌های .conf", "Free servers and your .conf files")
    "outline" -> tr("کلیدهای Outline، تست‌شده روی این خط", "Outline keys, tested on this line")
    "npv" -> tr("فایل‌ها و کانفیگ‌های NPV Tunnel", "NPV Tunnel files and configs")
    "fixed_ip" -> tr("آدرس خروجی ثابت", "A fixed exit address")
    "sublink" -> tr("لینک‌های اشتراک", "Subscription links")
    "lan" -> tr("اشتراک تونل با دستگاه دیگر", "Share the tunnel with another device")
    "usage" -> tr("مصرف", "Usage meter")
    "tutorial" -> tr("آموزش‌ها", "Tutorials")
    "widgetstudio" -> tr("ویجت برای صفحهٔ اصلی گوشی", "Widgets for your phone's home screen")
    "store" -> tr("برنامه، موتورها و فایل‌ها در یک‌جا", "App, engines and data files in one place")
    else -> null
}

private enum class EntryStatus { LIVE, BUSY, LAST, NONE }

@Composable
fun NexusDirectory(
    title: String,
    subtitle: String,
    groups: List<NexusGroup>,
    session: NexusSession,
    showStatus: Boolean,
    onOpen: (HomeApp) -> Unit,
    modifier: Modifier = Modifier,
    /** When set, a search also finds rows of Settings and opens Settings on that row's page. */
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
    val foundSettings: List<SettingsEntry> = remember(query, settingRows, onOpenSetting) {
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
        modifier = modifier.fillMaxSize().nexusBackdrop(),
        contentPadding = PaddingValues(
            start = 24.dp, end = 24.dp,
            top = top + 14.dp, bottom = bottom + NEXUS_NAV_RESERVE + 20.dp,
        ),
    ) {
        item(key = "title") {
            Column(Modifier.padding(bottom = 6.dp)) {
                Text(title, color = Nexus.Text, fontSize = 38.sp, fontWeight = FontWeight.ExtraLight)
                Text(subtitle, color = Nexus.Muted, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                EnergyLine(Modifier.fillMaxWidth().height(2.dp))
            }
        }
        item(key = "search") {
            SearchLine(query = query, onChange = { query = it })
        }
        shownGroups.forEach { group ->
            item(key = "group_" + group.title) { GroupHeader(group.title, group.apps.size) }
            itemsIndexed(group.apps, key = { _, app -> group.title + "/" + app.id }) { index, app ->
                val live = session.conn.method == app.id
                val status = when {
                    !showStatus -> EntryStatus.NONE
                    live && session.conn.up -> EntryStatus.LIVE
                    live && session.conn.busy -> EntryStatus.BUSY
                    app.id == session.last -> EntryStatus.LAST
                    else -> EntryStatus.NONE
                }
                NexusEntry(index + 1, app, status) { onOpen(app) }
            }
        }
        if (foundSettings.isNotEmpty() && onOpenSetting != null) {
            item(key = "group_settings") { GroupHeader(tr("تنظیمات", "Settings"), foundSettings.size) }
            itemsIndexed(foundSettings, key = { i, _ -> "setting_$i" }) { _, entry ->
                val rowTitle = entry.title.current(context)
                val where = (listOf(tr("تنظیمات", "Settings")) + entry.path.map { it.current(context) }).joinToString("  ›  ")
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onOpenSetting(SettingsIndex.prepare(context, entry)) }
                        .padding(start = 18.dp, top = 12.dp, bottom = 12.dp),
                ) {
                    Text(rowTitle, color = Nexus.Text, fontSize = 20.sp, fontWeight = FontWeight.Light, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(where, color = Nexus.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (shownGroups.isEmpty() && foundSettings.isEmpty()) {
            item(key = "empty") {
                Text(
                    tr("چیزی پیدا نشد", "Nothing found"),
                    color = Nexus.Muted, fontSize = 16.sp, modifier = Modifier.padding(top = 36.dp),
                )
            }
        }
    }
}

/** The energy element of the page: a hairline with a light that travels along it. */
@Composable
private fun EnergyLine(modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "energy line")
    val t = inf.animateFloat(0f, 1f, infiniteRepeatable(tween(3_400, easing = LinearEasing)), label = "t")
    Canvas(modifier) {
        drawRect(Brush.horizontalGradient(listOf(Nexus.Blue.copy(alpha = 0.55f), Nexus.Violet.copy(alpha = 0.10f), Color.Transparent)))
        val x = size.width * t.value
        val w = size.width * 0.22f
        drawRect(
            Brush.horizontalGradient(
                listOf(Color.Transparent, Nexus.Cyan, Color.Transparent),
                startX = x - w, endX = x + w,
            ),
        )
    }
}

@Composable
private fun SearchLine(query: String, onChange: (String) -> Unit) {
    Column(Modifier.padding(top = 18.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, contentDescription = null, tint = Nexus.Muted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text(tr("جستجو", "Search"), color = Nexus.Muted.copy(alpha = 0.7f), fontSize = 16.sp)
                BasicTextField(
                    value = query,
                    onValueChange = onChange,
                    singleLine = true,
                    textStyle = TextStyle(color = Nexus.Text, fontSize = 16.sp),
                    cursorBrush = SolidColor(Nexus.Cyan),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Nexus.Muted.copy(alpha = 0.25f)))
    }
}

@Composable
private fun GroupHeader(title: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 30.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(Nexus.Cyan, androidx.compose.foundation.shape.CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(title, color = Nexus.Cyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.weight(1f).height(1.dp).background(
                Brush.horizontalGradient(listOf(Nexus.Cyan.copy(alpha = 0.35f), Color.Transparent))
            )
        )
        Spacer(Modifier.width(10.dp))
        Text(count.toString().padStart(2, '0'), color = Nexus.Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun NexusEntry(index: Int, app: HomeApp, status: EntryStatus, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reach = animateFloatAsState(if (pressed) 1f else 0f, tween(260, easing = FastOutSlowInEasing), label = "reach")
    // Only an entry that is live or connecting animates; the rest of a long list stays still.
    val active = status == EntryStatus.LIVE || status == EntryStatus.BUSY
    val glow: State<Float> = if (active) {
        rememberInfiniteTransition(label = "entry glow")
            .animateFloat(0.35f, 1f, infiniteRepeatable(tween(1_500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow")
    } else remember { mutableStateOf(0f) }

    val accent = when (status) {
        EntryStatus.LIVE -> Nexus.Mint
        EntryStatus.BUSY -> Nexus.Amber
        EntryStatus.LAST -> Nexus.Violet
        EntryStatus.NONE -> Nexus.Muted
    }
    val name = stringResourceSafe(app)
    val line: String? = when (status) {
        EntryStatus.LIVE -> tr("متصل", "Connected")
        EntryStatus.BUSY -> tr("در حال اتصال", "Connecting")
        EntryStatus.LAST -> tr("آخرین روش استفاده‌شده", "Last used")
        EntryStatus.NONE -> describe(app.id)
    }

    Box(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
    ) {
        Canvas(Modifier.matchParentSize()) {
            val rtl = layoutDirection == LayoutDirection.Rtl
            val railX = if (rtl) size.width - 1.dp.toPx() else 1.dp.toPx()
            val railAlpha = when (status) {
                EntryStatus.LIVE, EntryStatus.BUSY -> 0.5f + 0.5f * glow.value
                EntryStatus.LAST -> 0.65f
                EntryStatus.NONE -> 0.14f + 0.5f * reach.value
            }
            val railTop = size.height * 0.18f
            val railBottom = size.height * 0.82f
            if (active) {
                // The glow around a live rail.
                drawLine(accent.copy(alpha = 0.18f * glow.value), Offset(railX, railTop), Offset(railX, railBottom), strokeWidth = 8.dp.toPx())
            }
            drawLine(accent.copy(alpha = railAlpha), Offset(railX, railTop), Offset(railX, railBottom), strokeWidth = 2.dp.toPx())
            // The hairline between methods, which a press (or a live link) fills with energy.
            val y = size.height - 0.5.dp.toPx()
            drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            val fill = if (active) 1f else reach.value
            if (fill > 0.01f) {
                val from = if (rtl) size.width else 0f
                val to = if (rtl) size.width * (1f - fill) else size.width * fill
                drawLine(
                    Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.9f), Nexus.Violet.copy(alpha = 0.4f)),
                        startX = from, endX = to,
                    ),
                    Offset(from, y), Offset(to, y), strokeWidth = 1.6.dp.toPx(),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, top = 15.dp, bottom = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                index.toString().padStart(2, '0'),
                color = Nexus.Muted.copy(alpha = 0.55f), fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(30.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    color = if (status == EntryStatus.LIVE) lerp(Nexus.Text, Nexus.Mint, 0.35f) else Nexus.Text,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Light,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (line != null) {
                    Text(
                        line,
                        color = if (status == EntryStatus.NONE) Nexus.Muted else accent,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun stringResourceSafe(app: HomeApp): String {
    val res = LocalContext.current.resources
    return remember(app.id) { runCatching { res.getString(app.labelRes) }.getOrDefault(app.id) }
}
