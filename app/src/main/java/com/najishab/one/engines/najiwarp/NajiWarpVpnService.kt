package com.najishab.one.engines.najiwarp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.IpPrefix
import android.net.Network
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.annotation.Keep
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.najishab.one.R
import io.github.georgexie2333.usque.NativeEngine
import org.json.JSONObject
import java.io.File
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class NajiWarpVpnService : VpnService() {

    companion object {
        private const val TAG = "NajiWarpVpnService"
        const val ACTION_CONNECT = "com.najishab.one.najiwarp.CONNECT"
        const val ACTION_DISCONNECT = "com.najishab.one.najiwarp.DISCONNECT"
        const val EXTRA_PROFILE_JSON = "profile_json"
        const val EXTRA_ACCOUNT_TYPE = "account_type"

        private const val CHANNEL_ID = "najiwarp_vpn_channel"
        private const val NOTIF_ID = 2091
        private const val SPLIT_DNS_IPV4 = "198.18.0.1"
        private const val SPLIT_DNS_IPV6 = "fd00::1"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val engineExecutor = Executors.newSingleThreadExecutor { Thread(it, "najiwarp-engine").apply { isDaemon = true } }
    private val stopExecutor = Executors.newSingleThreadExecutor { Thread(it, "najiwarp-stop").apply { isDaemon = true } }
    private val statusExecutor = Executors.newSingleThreadScheduledExecutor { Thread(it, "najiwarp-status").apply { isDaemon = true } }

    private val generation = AtomicLong(0L)
    private val tunnel = AtomicReference<ParcelFileDescriptor?>(null)
    private val nativeActive = AtomicBoolean(false)
    @Volatile private var destroyed = false
    @Volatile private var currentAccountType: String = "CONSUMER_FREE"
    private var statusTask: ScheduledFuture<*>? = null

    private val networkMonitor = PhysicalNetworkMonitor(
        mainHandler = mainHandler,
        listener = object : PhysicalNetworkMonitor.Listener {
            override fun onUnderlyingNetworkChanged(selectedNetwork: Network?, selectedFamilyMask: Int, generation: Long) {
                NativeEngine.nativeNotifyNetworkChanged(generation)
                if (tunnel.get() != null) {
                    runCatching {
                        setUnderlyingNetworks(selectedNetwork?.let { arrayOf(it) } ?: emptyArray())
                    }
                }
            }
        }
    )

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val cm = getSystemService(ConnectivityManager::class.java)
        if (cm != null) {
            networkMonitor.register(cm)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> disconnect(stopService = true)
            ACTION_CONNECT -> {
                val profileJson = intent.getStringExtra(EXTRA_PROFILE_JSON) ?: "{}"
                currentAccountType = intent.getStringExtra(EXTRA_ACCOUNT_TYPE) ?: "CONSUMER_FREE"
                beginConnection(profileJson)
            }
            else -> {
                if (!nativeActive.get()) stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onRevoke() {
        NajiWarpEngine.appendLog("دسترسی VPN توسط سیستم لغو شد.")
        disconnect(stopService = true)
        super.onRevoke()
    }

    override fun onDestroy() {
        destroyed = true
        generation.incrementAndGet()
        nativeActive.set(false)
        stopStatusTask()
        NativeEngine.nativeCancel()
        closeQuietly(tunnel.getAndSet(null))
        stopExecutor.execute { runCatching { NativeEngine.nativeStopConfirmed() } }
        runCatching {
            getSystemService(ConnectivityManager::class.java)?.let(networkMonitor::unregister)
        }
        engineExecutor.shutdownNow()
        statusExecutor.shutdownNow()
        stopExecutor.shutdown()
        NajiWarpEngine.onServiceDestroyed()
        super.onDestroy()
    }

    private fun beginConnection(profileJson: String) {
        val gen = generation.incrementAndGet()
        nativeActive.set(false)
        stopStatusTask()
        networkMonitor.bumpGeneration()
        promoteForeground("در حال آماده‌سازی NajiWarp...")
        NajiWarpEngine.updatePhase(NajiWarpPhase.PREPARING)

        engineExecutor.execute {
            try {
                NativeEngine.nativeCancel()
                NativeEngine.nativeStopConfirmed()
                if (!isCurrent(gen)) return@execute

                if (!NativeEngine.isReady()) {
                    fail(gen, "هسته libusque_android.so آماده نیست.")
                    return@execute
                }

                val secret = NajiWarpIdentityVault.loadSecret(this@NajiWarpVpnService)
                if (secret == null || secret.isEmpty()) {
                    fail(gen, "هویت WARP یافت نشد. لطفا ویزارد را مجددا اجرا کنید.")
                    return@execute
                }

                try {
                    if (NativeEngine.nativeValidateWarpSecret(secret) != NativeEngine.OK) {
                        fail(gen, "هویت WARP ذخیره شده نامعتبر است.")
                        return@execute
                    }

                    val profileObj = JSONObject(profileJson)
                    val frontends = profileObj.optJSONObject("frontends")
                    val tunnelEnabled = frontends?.optBoolean("tunnel", true) ?: (profileObj.optString("mode") == "vpn")

                    if (!networkMonitor.awaitPhysicalNetwork({ isCurrent(gen) }, 8_000L)) {
                        fail(gen, "شبکه اینترنت پایه در دسترس نیست.")
                        return@execute
                    }
                    if (!isCurrent(gen)) return@execute

                    val geoDir = File(noBackupFilesDir, "usque_config").apply { mkdirs() }.absolutePath

                    val result = if (tunnelEnabled) {
                        if (prepare(this@NajiWarpVpnService) != null) {
                            fail(gen, "مجوز VPN داده نشده است.")
                            return@execute
                        }
                        val inspected = NativeEngine.nativeInspectWarpSecret(secret)
                            ?: run {
                                fail(gen, "خطا در خواندن آی‌پی‌های هویت WARP.")
                                return@execute
                            }
                        val info = JSONObject(inspected)
                        val assignV4 = info.getString("ipv4")
                        val assignV6 = info.getString("ipv6")
                        val mtu = profileObj.optInt("mtu", 1280).coerceIn(1280, 9000)
                        val allowLan = profileObj.optBoolean("allow_lan", false)
                        val pfd = establishVpn(assignV4, assignV6, mtu, allowLan)
                            ?: run {
                                fail(gen, "اندروید اجازه ساخت رابط TUN را نداد.")
                                return@execute
                            }
                        val prev = tunnel.getAndSet(pfd)
                        if (prev != null && prev !== pfd) closeQuietly(prev)

                        if (!isCurrent(gen)) {
                            closeQuietly(tunnel.getAndSet(null))
                            return@execute
                        }
                        NajiWarpEngine.updatePhase(NajiWarpPhase.CONNECTING)
                        NajiWarpEngine.appendLog("در حال برقراری تانل MASQUE (HTTP/3)...")
                        NativeEngine.nativeStart(pfd.fd, profileJson, secret, ByteArray(0), geoDir, this@NajiWarpVpnService)
                    } else {
                        closeQuietly(tunnel.getAndSet(null))
                        NajiWarpEngine.updatePhase(NajiWarpPhase.CONNECTING)
                        NajiWarpEngine.appendLog("در حال راه‌اندازی پروکسی محلی MASQUE...")
                        NativeEngine.nativeStartProxy(profileJson, secret, ByteArray(0), geoDir, this@NajiWarpVpnService)
                    }

                    if (!isCurrent(gen)) {
                        stopExecutor.execute { runCatching { NativeEngine.nativeStopConfirmed() } }
                        return@execute
                    }

                    if (result != NativeEngine.OK) {
                        val snap = runCatching { NativeEngine.nativeSnapshot()?.let(::JSONObject) }.getOrNull()
                        val msg = snap?.optString("warning")?.takeIf { it.isNotBlank() }
                            ?: snap?.optString("error_code")?.takeIf { it.isNotBlank() }
                            ?: "Native start error ($result)"
                        fail(gen, msg)
                        return@execute
                    }

                    nativeActive.set(true)
                    startStatusPoller(gen)
                } finally {
                    secret.fill(0)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Start failed", e)
                fail(gen, e.message ?: "Unknown native start error")
            }
        }
    }

    private fun establishVpn(ipv4: String, ipv6: String, mtu: Int, allowLan: Boolean): ParcelFileDescriptor? {
        val routePlan = VpnRoutePlanner.plan(
            allowLan = allowLan,
            supportsRouteExclusion = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        )
        val builder = Builder()
            .setSession("NajiWarp")
            .setMtu(mtu)
            .setBlocking(false)
        networkMonitor.underlyingNetwork()?.let { builder.setUnderlyingNetworks(arrayOf(it)) }
        builder.addAddress(InetAddress.getByName(ipv4), 32)
        builder.addAddress(InetAddress.getByName(ipv6), 128)
        builder.addDnsServer(InetAddress.getByName(SPLIT_DNS_IPV4))
        builder.addDnsServer(InetAddress.getByName(SPLIT_DNS_IPV6))
        routePlan.included.forEach { builder.addRoute(it.address, it.prefixLength) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            routePlan.excluded.forEach { builder.excludeRoute(IpPrefix(it.address, it.prefixLength)) }
        }
        builder.addRoute(InetAddress.getByName(SPLIT_DNS_IPV4), 32)
        builder.addRoute(InetAddress.getByName(SPLIT_DNS_IPV6), 128)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }
        return builder.establish()
    }

    private fun startStatusPoller(gen: Long) {
        stopStatusTask()
        statusTask = statusExecutor.scheduleAtFixedRate({
            if (!isCurrent(gen) || !nativeActive.get()) return@scheduleAtFixedRate
            val raw = runCatching { NativeEngine.nativeSnapshot() }.getOrNull() ?: return@scheduleAtFixedRate
            val json = runCatching { JSONObject(raw) }.getOrNull() ?: return@scheduleAtFixedRate
            val phaseStr = json.optString("phase", "disconnected")
            val transport = json.optString("transport").takeIf { it.isNotBlank() && it != "null" }
            val warning = json.optString("warning").takeIf { it.isNotBlank() && it != "null" }
            val errorCode = json.optString("error_code").takeIf { it.isNotBlank() && it != "null" }

            when (phaseStr) {
                "preparing" -> NajiWarpEngine.updatePhase(NajiWarpPhase.PREPARING)
                "connectingH3", "connectingH2", "reconnecting" -> {
                    NajiWarpEngine.updatePhase(NajiWarpPhase.HANDSHAKE)
                    updateNotification(if (phaseStr == "connectingH2") "در حال اتصال با HTTP/2..." else "در حال اتصال با HTTP/3...")
                }
                "connected", "degraded" -> {
                    val exitIp = json.optString("exit_ipv4").takeIf { it.isNotBlank() && it != "null" }
                        ?: json.optString("exit_ipv6").takeIf { it.isNotBlank() && it != "null" }
                    val colo = json.optString("exit_country_code").takeIf { it.isNotBlank() && it != "null" }
                        ?: json.optString("exit_country").takeIf { it.isNotBlank() && it != "null" }
                        ?: "CF"
                    val nq = json.optJSONObject("network_quality")?.optJSONObject("metrics")
                    val rtt = nq?.optLong("smoothed_rtt_milliseconds", 0L)?.takeIf { it > 0L }
                        ?: nq?.optLong("latest_rtt_milliseconds", 0L) ?: 0L
                    val rx = json.optLong("downloaded_bytes", 0L)
                    val tx = json.optLong("uploaded_bytes", 0L)
                    val sRx = json.optLong("download_bytes_per_second", 0L)
                    val sTx = json.optLong("upload_bytes_per_second", 0L)
                    val quota = when (currentAccountType) {
                        AccountType.WARP_PLUS.name -> "WARP+"
                        AccountType.ZERO_TRUST.name -> "Zero Trust"
                        else -> "رایگان"
                    }
                    NajiWarpEngine.updatePhase(
                        NajiWarpPhase.CONNECTED(
                            transport = transport?.uppercase() ?: "H3",
                            colo = colo,
                            exitIp = exitIp ?: "WARP",
                            pingMs = rtt,
                            rxBytes = rx,
                            txBytes = tx,
                            speedRx = sRx,
                            speedTx = sTx,
                            quotaRemaining = quota
                        )
                    )
                    updateNotification("متصل (${transport?.uppercase() ?: "H3"})")
                }
                "error" -> fail(gen, warning ?: errorCode ?: "خطا در اتصال هسته MASQUE")
            }
        }, 0L, 1_000L, TimeUnit.MILLISECONDS)
    }

    private fun stopStatusTask() {
        statusTask?.cancel(false)
        statusTask = null
    }

    private fun fail(gen: Long, message: String) {
        mainHandler.post {
            if (!isCurrent(gen)) return@post
            generation.incrementAndGet()
            nativeActive.set(false)
            stopStatusTask()
            NativeEngine.nativeCancel()
            closeQuietly(tunnel.getAndSet(null))
            NajiWarpEngine.appendLog("خطا: $message")
            NajiWarpEngine.updatePhase(NajiWarpPhase.FAILED(message))
            stopExecutor.execute { runCatching { NativeEngine.nativeStopConfirmed() } }
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun disconnect(stopService: Boolean) {
        generation.incrementAndGet()
        nativeActive.set(false)
        stopStatusTask()
        NajiWarpEngine.updatePhase(NajiWarpPhase.DISCONNECTING)
        NativeEngine.nativeCancel()
        closeQuietly(tunnel.getAndSet(null))
        stopExecutor.execute {
            runCatching { NativeEngine.nativeStopConfirmed() }
            mainHandler.post {
                NajiWarpEngine.updatePhase(NajiWarpPhase.IDLE)
                NajiWarpEngine.appendLog("اتصال NajiWarp قطع شد.")
                ServiceCompat.stopForeground(this@NajiWarpVpnService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                if (stopService) stopSelf()
            }
        }
    }

    private fun isCurrent(gen: Long): Boolean = !destroyed && generation.get() == gen
    private fun closeQuietly(pfd: ParcelFileDescriptor?) { try { pfd?.close() } catch (_: Exception) {} }

    // -- JNI Callbacks invoked directly by libusque_android.so --

    @Keep fun getUnderlyingNetworkHandle(): Long = networkMonitor.underlyingNetwork()?.networkHandle ?: 0L
    @Keep fun getUnderlyingFamilyMask(): Int = networkMonitor.underlyingFamilyMask()
    @Keep fun getUnderlyingNetworkGeneration(): Long = networkMonitor.generation()
    @Keep fun getUnderlyingRecoverySnapshot(): LongArray = networkMonitor.recoverySnapshot()

    @Keep
    fun bindSocketToUnderlyingGeneration(descriptor: Int, expectedGeneration: Long, requireVpnProtection: Boolean): Int =
        UnderlyingSocketBinding<Network, ParcelFileDescriptor>(
            destroyed = { destroyed },
            currentGeneration = networkMonitor::generation,
            networkForGeneration = networkMonitor::networkForGeneration,
            networkHandle = { it.networkHandle },
            protect = { protect(it) },
            duplicate = ParcelFileDescriptor::fromFd,
            bindToNetwork = { network, copy -> network.bindSocket(copy.fileDescriptor) },
            close = { closeQuietly(it) }
        ).bind(descriptor, expectedGeneration, requireVpnProtection)

    @Keep
    fun getUnderlyingDnsServers(): Array<String> =
        networkMonitor.underlyingDnsServers().mapNotNull { addr ->
            val host = addr.hostAddress?.substringBefore('%') ?: return@mapNotNull null
            val scope = if (addr is Inet6Address) addr.scopeId else 0
            "$host|$scope"
        }.distinct().take(8).toTypedArray()

    @Keep
    fun resolveUnderlyingHost(host: String): Array<String> {
        val network = networkMonitor.underlyingNetwork() ?: return emptyArray()
        return runCatching {
            network.getAllByName(host)
                .mapNotNull { it.hostAddress?.substringBefore('%') }
                .distinct()
                .take(16)
                .toTypedArray()
        }.getOrDefault(emptyArray())
    }

    @Keep
    fun persistRefreshedWarpIdentity(profileId: String, secret: ByteArray): Boolean = try {
        NajiWarpIdentityVault.saveSecret(this, secret)
        true
    } catch (_: Exception) {
        false
    } finally {
        secret.fill(0)
    }

    @Keep fun cataloguePhysicalAllowed(): Boolean = !destroyed && !nativeActive.get() && tunnel.get() == null
    @Keep fun protonWarpSecret(id: String, profileJson: String): ByteArray = ByteArray(0)

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            mgr?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "NajiWarp VPN", NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                }
            )
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val piFlags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val contentPi = PendingIntent.getActivity(this, 0, launchIntent, piFlags)
        val stopIntent = Intent(this, NajiWarpVpnService::class.java).setAction(ACTION_DISCONNECT)
        val stopPi = PendingIntent.getService(this, 1, stopIntent, piFlags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_vpn)
            .setContentTitle("NajiWarp")
            .setContentText(statusText)
            .setContentIntent(contentPi)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "قطع اتصال", stopPi)
            .build()
    }

    private fun promoteForeground(statusText: String) {
        val notif = buildNotification(statusText)
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(this, NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun updateNotification(statusText: String) {
        getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, buildNotification(statusText))
    }
}