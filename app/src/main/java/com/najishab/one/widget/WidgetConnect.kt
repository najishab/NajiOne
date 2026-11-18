package com.najishab.one.widget

import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceManager
import com.najishab.core.tunnel.TunnelEngine
import com.najishab.core.tunnel.TunnelPreferences
import com.najishab.one.MyVpnService
import com.najishab.one.data.NodeManager
import com.najishab.one.data.VpnSwitch
import com.najishab.one.models.VpnNode
import com.najishab.one.store.tr
import com.najishab.one.ui.tunnel.Transport
import com.najishab.one.ui.tunnel.TunnelController
import com.najishab.one.ui.tunnel.TunnelStage
import com.najishab.one.utils.LocalPort
import com.najishab.one.utils.NetworkSettings
import com.najishab.one.utils.SafeStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference

/**
 * Connects and disconnects a method from outside its screen, for the widgets.
 *
 * Every method is started through exactly the call its own screen makes when its big button is
 * pressed -- the same engine function, the same extras -- so a widget connect is the app's own
 * connect, not a second implementation of it. What a screen would have asked the user first (an
 * account, a server list, a terms page) is answered by [readiness]: a method that is not ready opens
 * on its page instead.
 *
 * The callers have the VPN consent already, and check for a running scan; see WidgetActionActivity.
 * Everything here runs off the main thread except where it says otherwise.
 */
object WidgetConnect {

    sealed class Ready {
        object Yes : Ready()
        /** Something only the method's page can do first: [reason] says what, in the user's language. */
        data class Setup(val reason: String) : Ready()
        /** The method cannot run on this phone at all. */
        data class No(val reason: String) : Ready()
    }

    private val TRANSPORTS = setOf("masque", "wireguard", "warp_on_warp", "warp", "psiphon", "tor", "geph", "lantern", "urnetwork")

    // ---------------------------------------------------------------------------------------------
    // Readiness
    // ---------------------------------------------------------------------------------------------

    suspend fun readiness(context: Context, method: String): Ready = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        runCatching { readinessOf(app, method) }.getOrElse {
            android.util.Log.w("WidgetConnect", "readiness $method: ${it.message}")
            Ready.Setup(tr("برای ادامه، صفحهٔ این روش را باز کنید", "Open this method's page to go on"))
        }
    }

    private suspend fun readinessOf(app: Context, method: String): Ready {
        if (method in TRANSPORTS) {
            val t = Transport.byId(method) ?: return Ready.No(tr("این روش در این نسخه نیست", "Not in this version"))
            if (!t.supportedHere) return Ready.No(tr("این روش روی پردازندهٔ این گوشی اجرا نمی‌شود", "This method does not run on this phone's processor"))
            if (t.needsTunnelCore && !TunnelEngine.isAvailable) return Ready.No(tr("هستهٔ تونل روی این گوشی بارگذاری نشد", "The tunnel core did not load on this phone"))
            t.unavailableReason(app)?.let { return Ready.Setup(it) }
            val coreMissing = when (method) {
                "warp" -> !com.najishab.core.warp.CfWarpEngine.available(app)
                "geph" -> !com.najishab.core.geph.GephEngine.available(app)
                "lantern" -> !com.najishab.core.lantern.LanternEngine.available(app)
                else -> false
            }
            if (coreMissing) return Ready.Setup(tr("هستهٔ این روش هنوز نصب نشده؛ از صفحهٔ خودش نصبش کنید", "This method's core is not installed yet; install it from its page"))
            return Ready.Yes
        }
        return when (method) {
            "mae" -> {
                val mae = com.najishab.one.engines.mae.MaeEngine
                mae.init(app)
                when {
                    !mae.hasCloudAccount() -> Ready.Setup(tr("MAE به یک حساب کلادفلر نیاز دارد", "MAE needs a Cloudflare account"))
                    runCatching { !mae.store.current.onboarded }.getOrDefault(false) ->
                        Ready.Setup(tr("اول در صفحهٔ MAE برنامه‌هایتان را انتخاب کنید", "Pick your apps on MAE's page first"))
                    else -> Ready.Yes
                }
            }
            "emergency_2" ->
                if (com.najishab.one.engines.gst.GstConfigManager.getRelays(app).any { it.deploymentId.isNotBlank() }) Ready.Yes
                else Ready.Setup(tr("اول از صفحهٔ گوگل اسکریپت یک رله بسازید", "Build a relay on Google Script's page first"))
            "emergency_3" -> {
                NodeManager(app).awaitLoaded(6_000)
                if (com.najishab.one.engines.rstaspoof.SniSession.configs(app).isNotEmpty()) Ready.Yes
                else Ready.Setup(tr("هنوز کانفیگ SNI ندارید؛ از صفحهٔ ضد فیلتر SNI بسازید", "No SNI config yet; build one on its page"))
            }
            "iran" -> if (lastIranNode(app) != null) Ready.Yes
                else Ready.Setup(tr("یک بار از صفحهٔ «کانفیگ ایران» یکی را وصل کنید؛ ویجت همان را وصل می‌کند", "Connect one from «Iran configs» once; the widget then uses it"))
            "fronting" -> {
                NodeManager(app).awaitLoaded(6_000)
                if (com.najishab.one.mitm.MitmProfile.isReady(app)) Ready.Yes
                else Ready.Setup(tr("دامین فرانتینگ هنوز راه‌اندازی نشده", "Domain fronting is not set up yet"))
            }
            "fenix" -> {
                com.najishab.one.engines.fenix.FenixEngine.init(app)
                if (com.najishab.one.engines.fenix.FenixEngine.hasAccounts()) Ready.Yes
                else Ready.Setup(tr("اول حساب فایرفاکس را در صفحهٔ FENIX وارد کنید", "Sign in with a Firefox account on FENIX's page first"))
            }
            "amnezia" -> {
                com.najishab.one.engines.amnezia.AmzEngine.init(app)
                if (com.najishab.one.engines.amnezia.AmzEngine.store.current.servers.isNotEmpty()) Ready.Yes
                else Ready.Setup(tr("فهرست سرورها هنوز گرفته نشده", "The server list is not here yet"))
            }
            "openvpn" -> {
                val d = com.najishab.one.openvpn.OpenVpnRepository.get(app).data.value
				if (d.profiles.isNotEmpty()) Ready.Yes
				else Ready.Setup(tr("پروفایل OpenVPN پیدا نشد", "No OpenVPN profile found"))
            }
            "vpngate" -> Ready.Yes
            "flux", "outline" -> Ready.Yes
            "nodes" -> {
                NodeManager(app).awaitLoaded(6_000)
                if (ownNodes(app).isNotEmpty()) Ready.Yes
                else Ready.Setup(tr("هنوز کانفیگی اضافه نکرده‌اید", "You have not added a config yet"))
            }
            "npv" -> {
                val npv = com.najishab.one.engines.npv.NpvEngine
                npv.ensureSeed(app)
                if (npv.best(com.najishab.one.engines.npv.NpvLibrary.entries(app)) != null) Ready.Yes
                else Ready.Setup(tr("هنوز فایل NPV اجراشدنی ندارید", "No runnable NPV file yet"))
            }
            else -> Ready.Setup(tr("برای ادامه، صفحهٔ این روش را باز کنید", "Open this method's page to go on"))
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Connect
    // ---------------------------------------------------------------------------------------------

    /**
     * Starts [method], with [country] applied first when the design asks for one and the method
     * can honour it. True once the engine has the connect in hand; whether it then comes up is the
     * engine's own business, watched by the caller (WidgetConnectService).
     */
    suspend fun connect(app: Context, method: String, country: String?): Boolean = withContext(Dispatchers.IO) {
        val cc = country?.trim()?.uppercase()?.takeIf { it.length == 2 }
        if (method in TRANSPORTS) {
            val t = Transport.byId(method) ?: return@withContext false
            if (cc != null) applyTransportCountry(app, t, cc)
            withContext(Dispatchers.Main) { TunnelController.connect(app, t) }
            return@withContext true
        }
        when (method) {
            "mae" -> {
                com.najishab.one.engines.mae.MaeEngine.startTunnelAsync(app)
                true
            }
            "emergency_2" -> {
                startXray(app, "{\"type\":\"gst\"}", "GST_EMERGENCY", NetworkSettings.Method.GST.id)
                true
            }
            "emergency_3" -> {
                NodeManager(app).awaitLoaded(6_000)
                val sni = com.najishab.one.engines.rstaspoof.SniSession
                val configs = sni.configs(app)
                val chosenId = PreferenceManager.getDefaultSharedPreferences(app).getString("sni_selected_config_id", null)
                val node = configs.firstOrNull { it.id == chosenId } ?: configs.firstOrNull() ?: return@withContext false
                val (ip, port, fake) = com.najishab.one.engines.rstaspoof.RstaSpoofManager.storedRoute(app)
                sni.connect(app, node, ip, port, fake)
                true
            }
            "iran" -> {
                val node = lastIranNode(app) ?: return@withContext false
                startXray(app, node.uri, node.id, null)
                true
            }
            "fronting" -> {
                NodeManager(app).awaitLoaded(6_000)
                val node = nodesOf(app).firstOrNull { it.id == com.najishab.one.mitm.MitmProfile.NODE_ID }
                    ?: return@withContext false
                startXray(app, node.uri, node.id, null)
                true
            }
            "flux" -> {
                val flux = com.najishab.one.engines.flux.FluxEngine
                flux.init(app)
                if (cc != null) flux.setCountry(cc)
                withContext(Dispatchers.Main) { flux.connectAsync(app) }
                true
            }
            "fenix" -> {
                val fenix = com.najishab.one.engines.fenix.FenixEngine
                fenix.init(app)
                if (cc != null && fenix.servers.value.any { it.country.equals(cc, ignoreCase = true) }) fenix.setCountry(app, cc)
                fenix.connectAsync(app)
                true
            }
            "amnezia" -> {
                val amz = com.najishab.one.engines.amnezia.AmzEngine
                amz.init(app)
                if (cc == null) {
                    amz.connectTarget(app)
                } else {
                    // Only Amnezia's own servers.
                    val own = amneziaOwnIds(app)
                    val net = com.najishab.one.engines.flux.FluxNet.current(app).key
                    val best = com.najishab.one.engines.amnezia.AmzBrain.best(amz.store.current, net, cc, exclude = amz.store.current.servers.map { it.id }.toSet() - own)
                    if (best != null) amz.connectAsync(app, serverId = best.id, only = own)
                    else amz.connectAsync(app, country = cc, only = own)
                }
                true
            }
            "outline" -> {
                val outline = com.najishab.one.engines.outline.OutlineEngine
                outline.init(app)
                if (outline.store.current.servers.isEmpty()) {
                    // A fresh install whose Outline page was never opened: fetch the lists first,
                    // which is what the page does when it opens.
                    outline.refresh()
                    withTimeoutOrNull(60_000) {
                        delay(300)
                        outline.refreshing.first { !it }
                    }
                }
                val id = cc?.let {
                    com.najishab.one.engines.outline.OutlineBrain.best(outline.store.current, com.najishab.one.engines.flux.FluxNet.current(app).key, it)?.id
                }
                outline.connectAsync(app, id)
                true
            }
            "openvpn" -> {
                val plan = openVpnPlan(app, cc) ?: openVpnPlan(app, null) ?: return@withContext false
                com.najishab.one.openvpn.OpenVpnRuntime.connect(app, plan.first, plan.second)
                true
            }
            "vpngate" -> connectVpnGate(app, cc)
            "nodes" -> connectOwnNodes(app)
            "npv" -> {
                val npv = com.najishab.one.engines.npv.NpvEngine
                npv.ensureSeed(app)
                val best = npv.best(com.najishab.one.engines.npv.NpvLibrary.entries(app)) ?: return@withContext false
                // A native or remote NPV entry has no config of its own to run.
                val uri = best.uri ?: return@withContext false
                npv.connect(app, uri)
                true
            }
            else -> false
        }
    }

    /**
     * The country of a transport, written where its own screen writes it -- these are the method's
     * settings, so the app's page shows the same choice afterwards.
     *
     * WARP, MASQUE and WireGuard leave the country through a NAT64 gateway on a network with IPv6,
     * or through a measured free proxy on one without. Neither is invented here: NAT64 only for a
     * country it has a gateway for, the proxy only for a country whose pool the user already
     * measured on the method's page. Otherwise nothing is changed and the method leaves where it
     * would have anyway -- the widget then shows that real country.
     */
    private fun applyTransportCountry(app: Context, t: Transport, cc: String) {
        runCatching {
            when (t) {
                Transport.PSIPHON -> if (cc in com.najishab.core.tunnel.PsiphonRegions.options(app)) TunnelPreferences(app).egressRegion = cc
                Transport.TOR -> if (cc in com.najishab.core.tunnel.TorRegions.options()) TunnelPreferences(app).torExitRegion = cc
                Transport.GEPH -> com.najishab.core.geph.GephSettings.setExit(app, com.najishab.core.geph.GephExitChoice(cc, null))
                Transport.MASQUE, Transport.WIREGUARD, Transport.CFWG -> {
                    val nat64 = com.najishab.core.warp.Nat64Exits
                    if (cc in nat64.countries(app) && com.najishab.one.data.CfFamily.hasIpv6Route()) {
                        nat64.setSelection(app, cc)
                    } else {
                        useIpv4Exit(app, cc)
                    }
                }
                Transport.GOOL -> useIpv4Exit(app, cc)
                else -> Unit
            }
        }.onFailure { android.util.Log.w("WidgetConnect", "country $cc for ${t.id}: ${it.message}") }
    }

    private fun useIpv4Exit(app: Context, cc: String) {
        val v4 = com.najishab.core.warp.Ipv4Exits
        val s = v4.load(app)
        if (s.ack && s.poolOf(cc).isNotEmpty()) v4.setEnabled(app, on = true, country = cc)
    }

    /** MyVpnService started the way a screen starts it, with the lamp claimed before the core is up. */
    private suspend fun startXray(app: Context, uri: String, nodeId: String, mtu: String?) {
        val intent = Intent(app, MyVpnService::class.java).apply {
            putExtra("NODE_URI", uri)
            putExtra("NODE_ID", nodeId)
            if (mtu != null) putExtra("MTU_PROFILE", mtu)
            putExtra("PROXY_MODE", NetworkSettings.proxyMode(app))
            putExtra("LOCAL_PORT", LocalPort.getString(app))
        }
        withContext(Dispatchers.Main) { SafeStart.service(app, intent) }
    }

    /** The Iran profile last connected (the widget connects that one), when it is still there. */
    private fun lastIranNode(app: Context): VpnNode? {
        NodeManager(app).awaitLoaded(6_000)
        val last = com.najishab.one.data.LastEngine.read(app) as? com.najishab.one.data.LastEngine.Record.Xray ?: return null
        val id = last.nodeId?.takeIf { it.startsWith("default_najione_") } ?: return null
        return nodesOf(app).firstOrNull { it.id == id }
    }

    /** A copy of the config list, taken under its own lock (it is filled from disk on another thread). */
    private fun nodesOf(app: Context): List<VpnNode> {
        val nm = NodeManager(app)
        return synchronized(nm.nodes) { nm.nodes.toList() }
    }

    /**
     * The user's own configs: not the app's built-in Iran profiles, not SNI configs (their own tile),
     * not domain fronting. The Quick Settings tile races "the newest N" of the whole list, and the
     * built-in profiles are re-made with a fresh date on every load -- so they were always the
     * newest, and the user's own were left out of the race. Here they are kept apart.
     */
    private fun ownNodes(app: Context): List<VpnNode> =
        nodesOf(app).filter { node ->
            !NodeManager.isProtected(node) &&
                node.id != com.najishab.one.mitm.MitmProfile.NODE_ID &&
                !com.najishab.one.engines.rstaspoof.SniSession.isSniUri(node.uri)
        }

    /** The V2Ray tile: the user's own configs raced, the fastest connected (the tile's own rule). */
    private suspend fun connectOwnNodes(app: Context): Boolean {
        NodeManager(app).awaitLoaded(6_000)
        val count = PreferenceManager.getDefaultSharedPreferences(app).getInt("quick_tile_count", 20).coerceIn(1, 200)
        val nodes = ownNodes(app).sortedByDescending { it.addedAt }.take(count)
        if (nodes.isEmpty()) return false
        val scored = coroutineScope {
            nodes.map { node ->
                async(Dispatchers.IO) {
                    val cfg = com.najishab.one.utils.VpnConfig.parseUri(node.uri)
                    val ms = if (cfg != null && cfg.address.isNotBlank()) {
                        runCatching { com.najishab.one.ui.httpDelay(cfg.address, cfg.port) }.getOrDefault(-1)
                    } else -1
                    node to ms
                }
            }.map { it.await() }
        }
        val chosen = scored.filter { it.second in 1..8000 }.minByOrNull { it.second }?.first ?: nodes.first()
        startXray(app, chosen.uri, chosen.id, null)
        withContext(Dispatchers.Main) {
            MyVpnService.isRunning = true
            MyVpnService.connectedNodeId = chosen.id
        }
        return true
    }

    /** «گیت‌وی NJI»: the server chosen on its page, or for a country the best-rated one there. */
    private suspend fun connectVpnGate(app: Context, cc: String?): Boolean {
        val store = com.najishab.one.engines.vpngate.VpnGateStore
        val pool = com.najishab.one.engines.vpngate.VpnGatePool
        store.load(app)
        pool.load(app)
        val repo = com.najishab.one.engines.vpngate.VpnGateRepository(app)
        runCatching { repo.refresh() }
        val hidden = pool.hiddenFlow.value
        val servers = (repo.serversFlow.value + pool.keptServers()).distinctBy { it.hostName }.filter { it.hostName !in hidden }
        if (servers.isEmpty()) return false
        val inCountry = cc?.let { c -> servers.filter { it.countryShort.equals(c, ignoreCase = true) }.maxByOrNull { it.score } }
        val server = inCountry
            ?: servers.firstOrNull { it.hostName == store.selectedHostFlow.value }
            ?: servers.maxByOrNull { it.score }
            ?: return false
        com.najishab.one.engines.vpngate.VpnGateController.connect(app, server, "")
        return true
    }

    /** OpenVPN: (profile id, account id) as its dial would pick them, for [cc] when given. */
    private fun openVpnPlan(app: Context, cc: String?): Pair<String, String>? {
		val d = com.najishab.one.openvpn.OpenVpnRepository.get(app).data.value
		val profiles = d.profiles
		val fa = com.najishab.one.utils.AppLocaleManager.isFarsi()
		val profile = if (cc != null) {
             profiles.filter { com.najishab.one.openvpn.OpenVpnPlaces.of(it, fa).code.equals(cc, ignoreCase = true) }
                 .sortedWith(compareBy({ if (it.id == d.selectedProfile) 0 else 1 }, { if (it.favorite) 0 else 1 }))
                 .firstOrNull()
         } else {
             profiles.firstOrNull { it.id == d.selectedProfile } ?: profiles.firstOrNull()
         } ?: return null
		 return profile.id to com.najishab.one.openvpn.OpenVpnRuntime.SELF
    }

    /** Amnezia's own servers. */
    private fun amneziaOwnIds(app: Context): Set<String> =
        com.najishab.one.engines.amnezia.AmzEngine.store.current.servers.map { it.id }.toSet()

    // ---------------------------------------------------------------------------------------------
    // Disconnect
    // ---------------------------------------------------------------------------------------------

    /**
     * Stops whatever is up -- or on its way up -- from a widget.
     *
     * [VpnSwitch.stopAll] stops every engine and service, but it reaches the transport stack only
     * when its native core reports itself running, and «گف», «وارپ», «لنترن» and «URnetwork» run on
     * engines of their own that never set that flag: for them the transport stack is told directly.
     * The engines with a watchdog of their own (MAE, GitHub Tunnel, SNI's local front) are told too,
     * so nothing brings the tunnel straight back.
     */
    fun disconnect(context: Context) {
        val app = context.applicationContext
        val conn = WidgetLive.conn.value
        val tunnel = TunnelController.state.value
        if (tunnel.stage == TunnelStage.STARTING || tunnel.stage == TunnelStage.RUNNING) TunnelController.disconnect(app)
        when (conn.method) {
            "mae" -> runCatching { com.najishab.one.engines.mae.MaeEngine.stopTunnelAsync(app) }
            "vpngate" -> runCatching { com.najishab.one.engines.vpngate.VpnGateStore.markDisconnected(app) }
            "emergency_3" -> Thread({ runCatching { com.najishab.one.engines.rstaspoof.SniSession.disconnect(app) } }, "widget-sni-stop").start()
        }
        VpnSwitch.stopAll(app)
        WidgetLive.clearRequested()
    }

    /** Everything is down, the transport stack included (see [disconnect] for why it is asked apart). */
    fun allDown(context: Context): Boolean {
        val t = TunnelController.state.value.stage
        return !VpnSwitch.isUp(context) && !VpnSwitch.engineBusy() && t != TunnelStage.STARTING && t != TunnelStage.RUNNING
    }

    // ---------------------------------------------------------------------------------------------
    // Countries, for the studio
    // ---------------------------------------------------------------------------------------------

    /**
     * The countries a design for [method] can be pinned to, as ISO-2 codes in the method's own order
     * (best first where it has one). Null for a method that has no country to choose.
     */
    suspend fun countries(context: Context, method: String): List<String>? = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        runCatching { countriesOf(app, method) }.getOrNull()?.map { it.uppercase() }
            ?.filter { com.najishab.core.tunnel.IpFormatter.isRealCountry(it) }?.distinct()
    }

    private suspend fun countriesOf(app: Context, method: String): List<String>? = when (method) {
        "psiphon" -> com.najishab.core.tunnel.PsiphonRegions.options(app)
        "tor" -> com.najishab.core.tunnel.TorRegions.options()
        "geph" -> {
            val level = com.najishab.core.geph.GephAccount.cachedInfo(app)?.level ?: com.najishab.core.geph.GephAccount.Level.FREE
            com.najishab.core.geph.GephExits.countries(com.najishab.core.geph.GephExits.cached(app), level)
                .filter { it.usable(level) > 0 }.map { it.code }
        }
        "masque", "wireguard", "warp" -> com.najishab.core.warp.Nat64Exits.countries(app) + measuredIpv4(app)
        "warp_on_warp" -> measuredIpv4(app)
        "flux" -> {
            com.najishab.one.engines.flux.FluxEngine.init(app)
            com.najishab.one.engines.flux.FluxEngine.countries().map { it.code }
        }
        "fenix" -> {
            com.najishab.one.engines.fenix.FenixEngine.init(app)
            com.najishab.one.engines.fenix.FenixEngine.servers.value.map { it.country }
        }
        "amnezia" -> {
            val amz = com.najishab.one.engines.amnezia.AmzEngine
            amz.init(app)
            val own = amneziaOwnIds(app)
            val net = com.najishab.one.engines.flux.FluxNet.current(app).key
            val state = amz.store.current.let { it.copy(servers = it.servers.filter { s -> s.id in own }) }
            com.najishab.one.engines.amnezia.AmzBrain.countryOrder(state, net).map { it.first }
        }
        "outline" -> {
            val outline = com.najishab.one.engines.outline.OutlineEngine
            outline.init(app)
            outline.store.current.servers.mapNotNull { it.country }
                .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
        }
        "openvpn" -> {
            val fa = com.najishab.one.utils.AppLocaleManager.isFarsi()
            com.najishab.one.openvpn.OpenVpnRepository.get(app).data.value.profiles
                .mapNotNull { com.najishab.one.openvpn.OpenVpnPlaces.of(it, fa).code }
        }
        "vpngate" -> {
            val repo = com.najishab.one.engines.vpngate.VpnGateRepository(app)
            com.najishab.one.engines.vpngate.VpnGatePool.load(app)
            runCatching { repo.refresh() }
            (repo.serversFlow.value + com.najishab.one.engines.vpngate.VpnGatePool.keptServers())
                .groupingBy { it.countryShort }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
        }
        else -> null
    }

    /** Countries whose free-proxy exit the user has already measured on the method's page. */
    private fun measuredIpv4(app: Context): List<String> {
        val s = com.najishab.core.warp.Ipv4Exits.load(app)
        if (!s.ack) return emptyList()
        return s.pools.filterValues { it.isNotEmpty() }.keys.toList()
    }

    /** What a country choice means for [method], when it has a condition worth saying. */
    fun countryNote(method: String): String? = when (method) {
        "masque", "wireguard", "warp" -> tr(
            "روی اینترنت دارای IPv6 از دروازهٔ NAT64 و روی بقیه از «خروج IPv4» که در صفحهٔ همین روش آماده کرده‌اید خارج می‌شود. اگر هیچ‌کدام برای این کشور آماده نباشد، روش از همان جایی خارج می‌شود که خودش انتخاب کند و ویجت کشور واقعی را نشان می‌دهد.",
            "On an internet with IPv6 it leaves through a NAT64 gateway, elsewhere through the IPv4 exit you prepared on this method's page. If neither is ready for this country, the method leaves wherever it would anyway and the widget shows the real country.",
        )
        "warp_on_warp" -> tr(
            "فقط کشورهایی که «خروج IPv4» آن‌ها را در صفحهٔ وارپ در وارپ آماده کرده‌اید.",
            "Only the countries whose IPv4 exit you prepared on WARP-on-WARP's page.",
        )
        "psiphon", "tor" -> tr(
            "این کشور اول امتحان می‌شود؛ اگر در آن لحظه در دسترس نبود، کشور دیگری.",
            "This country is tried first; if it is not available at that moment, another one.",
        )
        "flux" -> tr("FLUX فقط مسیری را می‌پذیرد که واقعاً از همین کشور خارج شود.", "FLUX only accepts a route that really leaves from this country.")
        "amnezia", "outline", "vpngate", "openvpn" -> tr(
            "بهترین سرور همین کشور که روی اینترنت شما جواب داده انتخاب می‌شود.",
            "The best server in this country that answered on your internet is used.",
        )
        else -> null
    }

    /** Whether a design for [method] can carry a country at all (the studio hides the row otherwise). */
    fun hasCountries(method: String): Boolean = method in setOf(
        "psiphon", "tor", "geph", "masque", "wireguard", "warp", "warp_on_warp", "flux", "fenix",
        "amnezia", "outline", "openvpn", "vpngate",
    )
}
