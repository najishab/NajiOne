package com.najishab.one.ui.nexus

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.najishab.one.data.LastEngine
import com.najishab.one.ui.home.ActiveEngines
import com.najishab.one.widget.WidgetLive
import com.najishab.one.widget.WidgetMethods
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What every NEXUS page needs to know about the link: the live connection (from [WidgetLive], the
 * same source the old home widget and the placed widgets read) and the method last connected.
 */
class NexusSession(
    val conn: WidgetLive.Conn,
    /** The method last connected, as a home tile id; null until something has connected. */
    val last: String?,
) {
    /** The method NEXUS shows as "the" method: the live one, else the last used. */
    val shown: String? get() = conn.method?.takeIf { WidgetMethods.isMethod(it) } ?: last
}

@Composable
fun rememberNexusSession(): NexusSession {
    val context = androidx.compose.ui.platform.LocalContext.current
    val conn by WidgetLive.conn.collectAsState()
    var last by remember { mutableStateOf<String?>(null) }
    // Read off the main thread, exactly as the old connection widget did: the record lives in a
    // preferences file and, for a node, the node list behind it.
    LaunchedEffect(conn.method, conn.phase) {
        last = withContext(Dispatchers.IO) { lastMethodOf(context) }
    }
    return NexusSession(conn, last)
}

/** The method last connected, from the app's own record of it (what "connect" brings back). */
internal fun lastMethodOf(context: Context): String? = runCatching {
    when (val r = LastEngine.read(context)) {
        is LastEngine.Record.Tunnel -> r.transportId
        is LastEngine.Record.OpenVpn -> "openvpn"
        is LastEngine.Record.Xray -> ActiveEngines.homeOfNode(context, r.nodeId)
        is LastEngine.Record.NajiWarp -> "najiwarp"
        null -> null
    }
}.getOrNull()?.takeIf { WidgetMethods.isMethod(it) }
