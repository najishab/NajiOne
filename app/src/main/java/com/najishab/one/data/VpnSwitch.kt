package com.najishab.one.data

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.najishab.core.tunnel.TunnelStatus
import com.najishab.core.tunnel.TunnelVpnService
import com.najishab.one.MyVpnService
import com.najishab.one.utils.SafeStart

/**
 * One on/off for every tunnel the app can raise, for the Quick Settings tile.
 *
 * The tile knew two of the app's VPN services, MyVpnService and TunnelVpnService. OpenVPN
 * read as "off", so a tap tried to connect something else instead of disconnecting it
 * (user report 2026-10-11). And stopping a service is not enough for an engine that watches
 * its tunnel -- FLUX's standbys, the Amnezia
 * engine's connect loop -- which brought it straight back: each is told through its own disconnect.
 */
object VpnSwitch {

    /** A tunnel of this app is up (or an engine is on its way to one), whichever service holds it. */
    fun isUp(context: Context): Boolean =
        MyVpnService.isRunning || TunnelStatus.isActive() ||
            com.najishab.one.openvpn.OpenVpnRuntime.connection.value.active || ownVpnUp(context)

    /** An engine is connecting or connected, even before its service reports in. */
    fun engineBusy(): Boolean = runCatching {
        (com.najishab.one.engines.amnezia.AmzEngine.isInitialized() && com.najishab.one.engines.amnezia.AmzEngine.connect.value.let {
            it is com.najishab.one.engines.amnezia.AmzConnect.Connecting || it is com.najishab.one.engines.amnezia.AmzConnect.Connected
        })
    }.getOrDefault(false)

    /** Stops whatever is up, through each engine's own disconnect first, then the services themselves. */
    fun stopAll(context: Context) {
        val app = context.applicationContext
        runCatching {
            val amz = com.najishab.one.engines.amnezia.AmzEngine
            if (amz.isInitialized() && amz.connect.value !is com.najishab.one.engines.amnezia.AmzConnect.Idle) amz.disconnectAsync(app)
        }
        runCatching {
            val flux = com.najishab.one.engines.flux.FluxEngine
            if (flux.ui.value !is com.najishab.one.engines.flux.core.model.FluxUiState.Idle) flux.disconnectAsync(app)
        }
        runCatching {
            val outline = com.najishab.one.engines.outline.OutlineEngine
            if (outline.connect.value !is com.najishab.one.engines.outline.OutlineConnect.Idle) outline.disconnectAsync(app)
        }
        runCatching {
            val fenix = com.najishab.one.engines.fenix.FenixEngine
            if (fenix.isInitialized() && fenix.conn.value !is com.najishab.one.engines.fenix.FenixEngine.Conn.Idle) fenix.disconnectAsync(app)
        }
        runCatching { com.najishab.one.openvpn.OpenVpnRuntime.disconnect(app) }
        if (TunnelStatus.isActive()) {
            SafeStart.service(app, Intent(app, TunnelVpnService::class.java).setAction(TunnelVpnService.ACTION_DISCONNECT))
        }
        if (MyVpnService.isRunning || ownVpnUp(app)) {
            SafeStart.service(app, Intent(app, MyVpnService::class.java).apply { action = "STOP" })
            MyVpnService.isRunning = false
            MyVpnService.connectedNodeId = null
        }
    }

    /** The system has a VPN network up that this app owns (the AmneziaWG backend, SoftEther: their own services). */
    private fun ownVpnUp(context: Context): Boolean = runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        @Suppress("DEPRECATION")
        cm.allNetworks.any { n ->
            val caps = cm.getNetworkCapabilities(n) ?: return@any false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                Build.VERSION.SDK_INT >= 29 && caps.ownerUid == android.os.Process.myUid()
        }
    }.getOrDefault(false)
}
