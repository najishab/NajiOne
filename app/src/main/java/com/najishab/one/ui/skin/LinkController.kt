package com.najishab.one.ui.skin

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.najishab.one.store.tr
import com.najishab.one.ui.nexus.NexusSession
import com.najishab.one.widget.WidgetConnect
import com.najishab.one.widget.WidgetConnectService
import com.najishab.one.widget.WidgetLive
import com.najishab.one.widget.WidgetMethods
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What a skin's Home shows. ERROR is a failed attempt, never confused with a live link. */
internal enum class LinkState { OFF, CONNECTING, ON, ERROR }

/** The connect path both skins share, extracted from NexusHome unchanged. */
internal class LinkController(
    val state: LinkState,
    val conn: WidgetLive.Conn,
    /** The method a connect would use: the override when it is a real method, else the last one. */
    val last: String?,
    val onPower: () -> Unit,
)

/**
 * The real connect logic, one copy for both skins: ERROR derivation (CONNECTING -> IDLE without a
 * user stop, auto-cleared after 12 s), the VPN consent launcher, the ScanGuard check, and
 * connect/cancel through [WidgetConnectService] / [WidgetConnect]. Nothing here knows an engine.
 *
 * [methodOverride] is Freedom's favourite-protocol card; null (Nexus) keeps the old behaviour
 * byte-for-byte: connect the last method, or open the method picker when there is none.
 */
@Composable
internal fun rememberLinkController(
    session: NexusSession,
    onChooseMethod: () -> Unit,
    methodOverride: String? = null,
): LinkController {
    val context = LocalContext.current
    val conn = session.conn
    val last = methodOverride?.takeIf { WidgetMethods.isMethod(it) } ?: session.last

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
    return LinkController(state, conn, last, onPower)
}