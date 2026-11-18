package com.najishab.one.ui.home

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import com.najishab.one.MyVpnService
import com.najishab.one.ui.tunnel.TunnelController
import com.najishab.one.ui.tunnel.TunnelStage

/**
 * Which home-screen icons are currently carrying something.
 *
 * The home grid is a launcher for a dozen different ways of getting online, and until now none of
 * them looked any different when it was the one that was up — so "am I connected, and through
 * what" could only be answered by opening icons one at a time. This is the single place that
 * answers it, and [AppIconTile] draws a lamp from it.
 *
 * The state comes from three independent places, because the engines genuinely are independent:
 *
 *  - the five-transport stack has its own service and its own controller;
 *  - everything built on Xray — nodes, Quick Connect, VPN Gate, the game booster, the
 *    anti-sanction resolver, the Google Script tunnel — shares [MyVpnService] and is told apart
 *    by the node id it was started with;
 *  - the SNI spoofer counts as up only while a config is riding it -- the front process on
 *    its own is a door nobody is walking through, and drawing that as a live engine is what
 *    put the SNI lamp on over unrelated tunnels.
 *
 * Only ONE of the Xray family can be live at a time (they share a service), and only one of them
 * and the transport stack (they share the device's tun — see `TunnelExclusion`). The set can
 * still hold more than one entry, because an SNI session is not a tunnel of its own and genuinely
 * coexists with one.
 */
object ActiveEngines {

    /**
     * The home ids that should show a lamp right now.
     *
     * Composable rather than a Flow: every input is already a Flow or a cheap read, and the only
     * consumer is the grid, which is composed anyway.
     */
    @Composable
    fun ids(): Set<String> {
        val context = LocalContext.current

        val tunnel by TunnelController.state.collectAsState()
        val openvpn by com.najishab.one.openvpn.OpenVpnRuntime.connection.collectAsState()
        val xrayRunning by MyVpnService.isRunningFlow.collectAsState()
        val xrayNode by MyVpnService.connectedNodeIdFlow.collectAsState()
        // Asked of the service that is carrying the config, not of the node list. See
        // [MyVpnService.sniSessionFlow] for why the list could not answer it reliably.
        val sniSession by MyVpnService.sniSessionFlow.collectAsState()
        val amz by com.najishab.one.engines.amnezia.AmzEngine.connect.collectAsState()
        val amzServer = amzServerOf(amz)

        // Keyed on every input, so the set is rebuilt when an engine changes and NOT on the many
        // other recompositions the board goes through -- a drag re-runs the grid on every touch
        // move, and `xrayHome` reads preferences and parses the saved-server list.
        val tunnelId = tunnel.active?.id?.takeIf { tunnel.stage == TunnelStage.RUNNING }
        return remember(tunnelId, xrayRunning, xrayNode, sniSession, openvpn.phase, openvpn.profileId, amzServer) {
            compute(context, tunnelId, xrayRunning, xrayNode, sniSession, openvpn.phase, amzServer, openvpn.profileId)
        }
    }

    /**
     * The same answer outside composition, from the engines' current values -- for NAJI Scout, which
     * has to say which connection it is about to pause, and name it on the button that brings it back.
     */
    fun idsNow(context: Context): Set<String> {
        val tunnel = TunnelController.state.value
        return compute(
            context,
            tunnel.active?.id?.takeIf { tunnel.stage == TunnelStage.RUNNING },
            MyVpnService.isRunningFlow.value,
            MyVpnService.connectedNodeIdFlow.value,
            MyVpnService.sniSessionFlow.value,
            com.najishab.one.openvpn.OpenVpnRuntime.connection.value.phase,
            amzServerOf(com.najishab.one.engines.amnezia.AmzEngine.connect.value),
            com.najishab.one.openvpn.OpenVpnRuntime.connection.value.profileId,
        )
    }

    /**
     * The icon that owns [nodeId] on MyVpnService, for a connect still in progress -- which [idsNow]
     * does not light, since it lights only what is up. The widgets show "connecting" on it.
     */
    internal fun homeOfNode(context: Context, nodeId: String?): String? = runCatching {
        xrayHome(
            context,
            nodeId,
            MyVpnService.sniSessionFlow.value,
            amzServerOf(com.najishab.one.engines.amnezia.AmzEngine.connect.value),
        )
    }.getOrNull()

    private fun amzServerOf(amz: com.najishab.one.engines.amnezia.AmzConnect): String? = when (val c = amz) {
        is com.najishab.one.engines.amnezia.AmzConnect.Connecting -> c.serverId
        is com.najishab.one.engines.amnezia.AmzConnect.Connected -> c.serverId
        is com.najishab.one.engines.amnezia.AmzConnect.Failed -> c.serverId
        else -> null
    }

    private fun compute(
        context: Context,
        tunnelId: String?,
        xrayRunning: Boolean,
        xrayNode: String?,
        sniSession: Boolean,
        openvpnPhase: com.najishab.one.openvpn.ConnectionPhase,
        amzServer: String?,
        openvpnProfile: String?,
    ): Set<String> = buildSet {
        if (openvpnPhase == com.najishab.one.openvpn.ConnectionPhase.CONNECTED)
            add("openvpn")
        tunnelId?.let { add(it) }
        if (xrayRunning) xrayHome(context, xrayNode, sniSession, amzServer)?.let { add(it) }
        // The SESSION, not the front process.
        //
        // This used to light on `RstaSpoofManager.isRunningFlow` as well, and that is a
        // process being alive -- which is not the same fact. The front carries nothing by
        // itself; it is a door, and it only means anything while a config is walking
        // through it. Measuring a config raises the same door, and so did every delay
        // test over a list with one SNI config in it, so the lamp came on over a MASQUE
        // or a WireGuard tunnel with no SNI anywhere near it. The user saw it and asked
        // exactly the right question: why is the SNI light on when I am on the tunnel?
        //
        // (The stray process was a real leak too, and is fixed at the source -- the front
        // is now claimed and released rather than switched on. This is the second half:
        // even if something did strand it, an open door is not a connection and must not
        // be drawn as one.)
        if (sniSession) add("emergency_3")
    }

    /**
     * Which icon owns the node id [MyVpnService] is currently running.
     *
     * A lookup rather than a flag on the service, because the id is what every caller already
     * passes and adding a parallel "who started me" field would be one more thing to keep in
     * step at eight call sites. The order matters: the specific engines claim their own ids
     * first, and anything left is an ordinary node from the connection tab.
     *
     * Returns null rather than guessing when the id belongs to no icon — the retired Aether
     * screen still sets `aether`, and lighting an unrelated tile would be worse than lighting
     * none.
     */
    private fun xrayHome(context: Context, nodeId: String?, sniSession: Boolean, amzServer: String? = null): String? {
        val id = nodeId?.trim().orEmpty()
        if (id.isEmpty()) return null
        return when {
            // An SNI session is Xray AND the local TLS front working as one thing, so it must
            // light one lamp, not two. Without this the V2Ray icon came on beside the SNI one and
            // the user was shown two engines for a single connection -- and tapping the V2Ray one
            // took them to a list where nothing looked connected.
            //
            // The test is the service's own flag rather than a lookup in the node list. The
            // lookup version is what shipped, and it kept failing: it ran during the home
            // screen's first composition, which is regularly BEFORE NodeManager has finished
            // loading its list off disk, so it found no node and answered "not SNI".
            sniSession -> "emergency_3"
            // The two built-in profiles have their own icons now, so their lamp belongs there.
            // Left to fall through, they lit the V2Ray tile -- which sent anyone who tapped it to
            // a list where the connected config is in a folder they have to go looking for.
            id.startsWith("default_najione_") -> "iran"
            id == com.najishab.one.mitm.MitmProfile.NODE_ID -> "fronting"
            id == "GST_EMERGENCY" -> "emergency_2"
            id == com.najishab.one.engines.mae.MaeEngine.NODE_ID -> "mae"
            id == com.najishab.one.engines.flux.FluxEngine.NODE_ID -> "flux"
            id == com.najishab.one.engines.fenix.FenixEngine.NODE_ID -> "fenix"
            id == com.najishab.one.engines.outline.OutlineEngine.NODE_ID -> "outline"
            id == com.najishab.one.engines.npv.NpvEngine.NODE_ID -> "npv"
            id == com.najishab.one.engines.amnezia.AmzEngine.NODE_ID -> "amnezia"
            // The booster's own ids, plus the two DNS-only modes it can run without a tunnel.
            id.startsWith("game_") || id.startsWith("dedicated_dns") ||
                id.startsWith("direct_dns") -> "game"
            id.startsWith("vpngate_") -> "vpngate"
            // A user's own node boosted for a game keeps ITS id, so the booster's own flag is the
            // only thing that can tell that case from an ordinary connect.
            gameModeActive(context) -> "game"
            id == "aether" -> null
            else -> "nodes"
        }
    }

    private fun gameModeActive(context: Context): Boolean =
        context.getSharedPreferences("game_booster_prefs", Context.MODE_PRIVATE)
            .getBoolean("game_mode_active", false)
}
