package com.najishab.one.openvpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.annotation.Keep
import androidx.core.app.NotificationCompat
import com.najishab.one.MainActivity
import com.najishab.one.R
import com.najishab.one.store.tr
import com.najishab.one.ui.tunnel.TunnelExclusion
import com.najishab.one.utils.GameSessionOwner
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class OpenVpnService : VpnService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private var running: Job? = null
    @Volatile private var session: Session? = null
    @Volatile private var shuttingDown = false
    private var latestStartId = 0
    private var currentOwner: String? = null
    private val repo by lazy { OpenVpnRepository.get(this) }

    private data class Command(
        val locationId: String? = null,
        val transportMode: String = OpenVpnLocation.AUTO_MODE,
        val username: String? = null,
        val password: String? = null,
        val manualCreds: Boolean = false,
        val splitTcp: Boolean = true,
        val blockIpv6: Boolean = true,
        val profile: String? = null,
        val account: String? = null,
        val startId: Int = 0,
        val finished: Session? = null,
        val owner: String? = null,
        val expectedOwner: String? = null,
    )

    override fun onCreate() {
        super.onCreate()
        com.najishab.one.utils.SafeStart.attach(this)
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "OpenVPN", NotificationManager.IMPORTANCE_LOW)
            )
        }

        scope.launch {
            for (command in commands) {
                val finished = command.finished
                if (finished != null) {
                    if (session !== finished) continue
                    running?.join()
                    running = null
                    session = null
                    if (latestStartId == finished.requestId) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelfResult(finished.requestId)
                    }
                    continue
                }

                if (command.expectedOwner != null && command.expectedOwner != currentOwner) continue
                val hasTarget = command.locationId != null || command.profile != null
                if (hasTarget && !GameSessionOwner.accepts(command.owner)) {
                    if (session == null && latestStartId == command.startId) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelfResult(command.startId)
                    }
                    continue
                }

                session?.stop()
                val stopped = withTimeoutOrNull(STOP_GRACE_MS) { running?.join(); true } == true
                if (!stopped) {
                    session?.abandon()
                    OpenVpnRuntime.mutable.value = OpenVpnConnection()
                }
                running = null
                session = null

                if (!hasTarget) {
                    OpenVpnRuntime.mutable.value = OpenVpnConnection()
                    if (latestStartId == command.startId) stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelfResult(command.startId)
                    continue
                }

                startSession(command)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val owner = intent?.getStringExtra(GameSessionOwner.EXTRA)
        val expected = intent?.getStringExtra(GameSessionOwner.EXPECTED)
        if (intent?.action == CONNECT && !GameSessionOwner.accepts(owner)) {
            if (session == null && !OpenVpnRuntime.connection.value.active) {
                startForeground(NOTIFICATION, notification("DISCONNECTED"))
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelfResult(startId)
            }
            return START_NOT_STICKY
        }
        if (expected != null && expected != currentOwner) return START_NOT_STICKY

        latestStartId = startId
        if (intent?.action == CONNECT) {
            currentOwner = owner
            startForeground(NOTIFICATION, notification(tr("در حال اتصال…", "Connecting…")))

            val locId = intent.getStringExtra(EXTRA_LOCATION_ID)
            val mode = intent.getStringExtra(EXTRA_TRANSPORT_MODE) ?: OpenVpnLocation.AUTO_MODE
            val user = intent.getStringExtra(EXTRA_USERNAME)
            val pass = intent.getStringExtra(EXTRA_PASSWORD)
            val manual = intent.getBooleanExtra(EXTRA_MANUAL_CREDS, false)
            val splitTcp = intent.getBooleanExtra(EXTRA_SPLIT_TCP, true)
            val blockIpv6 = intent.getBooleanExtra(EXTRA_BLOCK_IPV6, true)
            val p = intent.getStringExtra("profile")
            val a = intent.getStringExtra("account")

            if (locId != null) {
                OpenVpnRuntime.mutable.value = OpenVpnConnection(
                    phase = ConnectionPhase.CONNECTING,
                    locationId = locId,
                    accountId = OpenVpnRuntime.SELF,
                    usingManualCredentials = manual,
                )
                commands.trySend(
                    Command(
                        locationId = locId,
                        transportMode = mode,
                        username = user,
                        password = pass,
                        manualCreds = manual,
                        splitTcp = splitTcp,
                        blockIpv6 = blockIpv6,
                        startId = startId,
                        owner = owner,
                    )
                )
            } else if (p != null) {
                if (!OpenVpnRuntime.connection.value.active) {
                    OpenVpnRuntime.mutable.value = OpenVpnConnection(ConnectionPhase.CONNECTING, p, a ?: OpenVpnRuntime.SELF)
                }
                commands.trySend(Command(profile = p, account = a ?: OpenVpnRuntime.SELF, splitTcp = splitTcp, blockIpv6 = blockIpv6, startId = startId, owner = owner))
            } else {
                commands.trySend(Command(startId = startId))
            }
        } else {
            session?.stop()
            commands.trySend(Command(startId = startId, expectedOwner = expected))
        }
        return START_NOT_STICKY
    }

    override fun onRevoke() {
        session?.stop()
        commands.trySend(Command(startId = latestStartId))
        super.onRevoke()
    }

    private fun startSession(command: Command) {
        if (!GameSessionOwner.accepts(command.owner)) return
        val newSession = Session(command)
        session = newSession
        OpenVpnRuntime.mutable.value = OpenVpnConnection(
            phase = ConnectionPhase.CONNECTING,
            profileId = command.profile,
            accountId = command.account ?: OpenVpnRuntime.SELF,
            locationId = command.locationId,
            usingManualCredentials = command.manualCreds,
        )
        running = scope.launch(Dispatchers.IO) { newSession.run() }

        // Overall safety deadline across all fallback attempts
        scope.launch {
            delay(OVERALL_DEADLINE_MS)
            if (session === newSession &&
                OpenVpnRuntime.connection.value.phase != ConnectionPhase.CONNECTED &&
                OpenVpnRuntime.connection.value.connectedAt == null
            ) {
                newSession.timeOut()
                delay(STOP_GRACE_MS)
                if (session === newSession) {
                    newSession.abandon()
                    running = null
                    session = null
                    OpenVpnRuntime.mutable.value = OpenVpnConnection(
                        phase = ConnectionPhase.ERROR,
                        profileId = OpenVpnRuntime.connection.value.profileId,
                        locationId = command.locationId,
                        error = "CONNECT_DEADLINE",
                    )
                    if (latestStartId == newSession.requestId) stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelfResult(newSession.requestId)
                }
            }
        }
    }

    override fun onDestroy() {
        com.najishab.one.utils.SafeStart.detach(this)
        shuttingDown = true
        session?.stop()
        commands.close()
        scope.cancel()
        super.onDestroy()
    }

    @Keep
    private inner class Session(private val cmd: Command) {
        val requestId: Int = cmd.startId
        private val stopped = AtomicBoolean(false)
        private val handle = AtomicLong(0)
        private val attemptCounter = AtomicInteger(0)
        private val verifyGeneration = AtomicInteger(0)
        private val currentAttemptVerified = AtomicBoolean(false)
        private val dnsServersAdded = AtomicInteger(0)
        private val v4RouteAdded = AtomicBoolean(false)
        private var builder: Builder? = null
        private var retainedTun: ParcelFileDescriptor? = null
        @Volatile private var terminal: String? = null
        @Volatile private var timedOut = false
        @Volatile private var relay: OpenVpnSplitRelay? = null
        private var attemptTimeoutJob: Job? = null

        fun timeOut() {
            timedOut = true
            if (terminal == null) terminal = "CONNECT_DEADLINE"
            stop()
        }

        fun abandon() {
            stopped.set(true)
            attemptTimeoutJob?.cancel()
            handle.get().takeIf { it != 0L }?.let { runCatching { OpenVpnNative.stop(it) } }
            relay?.let { r -> relay = null; runCatching { r.close() } }
            synchronized(this) { runCatching { retainedTun?.close() }; retainedTun = null }
        }

        fun stop() {
            stopped.set(true)
            attemptTimeoutJob?.cancel()
            if (session === this && OpenVpnRuntime.connection.value.active) {
                OpenVpnRuntime.mutable.value = OpenVpnRuntime.connection.value.copy(phase = ConnectionPhase.DISCONNECTING)
            }
            handle.get().takeIf { it != 0L }?.let { OpenVpnNative.stop(it) }
        }

        suspend fun run() {
            try {
                if (!OpenVpnNative.available(this@OpenVpnService)) {
                    terminal = "CORE_UNAVAILABLE"
                    return
                }

                // Build list of (modeLabel, Profile) attempts
                val attempts: List<Pair<String?, Profile>> = if (cmd.locationId != null) {
                    val loc = repo.locationById(cmd.locationId) ?: error("location")
                    val ordered = loc.orderedAttempts(cmd.transportMode)
                    // If AUTO and latency probe found healthy TCP modes, prioritize them while keeping all fallbacks
                    if (cmd.transportMode == OpenVpnLocation.AUTO_MODE && !loc.probe?.healthy.isNullOrEmpty()) {
                        val healthySet = loc.probe!!.healthy.toSet()
                        val prioritized = ordered.filter { it.first in healthySet } + ordered.filterNot { it.first in healthySet }
                        prioritized
                    } else {
                        ordered
                    }
                } else {
                    val p = repo.findProfileById(cmd.profile!!) ?: error("profile")
                    listOf(null to p)
                }

                if (attempts.isEmpty()) {
                    terminal = "PROFILE_UNSUPPORTED"
                    return
                }

                // Resolve credentials
                var resolvedUser = cmd.username.orEmpty()
                var resolvedPass = cmd.password.orEmpty()
                val firstProfile = attempts.first().second

                if (resolvedUser.isEmpty() && !firstProfile.selfContained) {
                    val own = firstProfile.ownCredentials
                    if (own != null) {
                        resolvedUser = own.first
                        resolvedPass = own.second
                    } else {
                        // Fallback for Quick Tile / Widget invocation
                        when (val fetched = OpenVpnCredentials.fetchFromGitHub()) {
                            is CredentialsFetchResult.Ok -> {
                                resolvedUser = fetched.credentials.username
                                resolvedPass = fetched.credentials.password
                            }
                            is CredentialsFetchResult.Failed -> {
                                terminal = "AUTH_FAILED"
                                return
                            }
                        }
                    }
                }

                if (stopped.get()) return
                TunnelExclusion.releaseForOpenVpn(this@OpenVpnService)
                if (stopped.get()) return

                var prevMode: String? = null
                for ((index, attempt) in attempts.withIndex()) {
                    if (stopped.get()) break
                    val (modeLabel, profile) = attempt
                    val attemptId = attemptCounter.incrementAndGet()
                    currentAttemptVerified.set(false)
                    terminal = null

                    val switchText = if (index > 0 && prevMode != null && modeLabel != null) {
                        tr(
                            "پروتکل $prevMode متصل نشد؛ تغییر خودکار به $modeLabel انجام شد",
                            "$prevMode could not connect; switched to $modeLabel"
                        )
                    } else null

                    OpenVpnRuntime.mutable.value = OpenVpnRuntime.mutable.value.copy(
                        phase = ConnectionPhase.CONNECTING,
                        profileId = profile.id,
                        locationId = cmd.locationId ?: OpenVpnRuntime.mutable.value.locationId,
                        activeProtocol = modeLabel,
                        switchedFrom = if (index > 0) prevMode else null,
                        switchNotice = switchText ?: OpenVpnRuntime.mutable.value.switchNotice,
                        detail = null,
                        error = null,
                        connectedAt = null,
                        exitIp = null,
                        exitCountry = null,
                    )
                    if (modeLabel != null) {
                        notifyStatus(
                            switchText ?: tr("در حال اتصال ($modeLabel)…", "Connecting ($modeLabel)…")
                        )
                    }

                    val config = prepareEffectiveConfig(profile)
                    val validation = OpenVpnNative.evaluate(config)
                    if (validation.isNotEmpty()) {
                        terminal = validation
                        prevMode = modeLabel
                        continue
                    }

                    val own = profile.ownCredentials
                    val username = if (resolvedUser.isNotEmpty()) resolvedUser else own?.first.orEmpty()
                    val password = if (resolvedUser.isNotEmpty()) resolvedPass else own?.second.orEmpty()

                    val coreId = OpenVpnNative.create()
                    check(coreId != 0L)
                    handle.set(coreId)
                    if (stopped.get()) OpenVpnNative.stop(coreId)

                    // Per-attempt deadline when we have fallback protocols left (or single location attempt)
                    val hasMoreAttempts = index + 1 < attempts.size
                    attemptTimeoutJob?.cancel()
                    val attemptBudget = if (hasMoreAttempts) ATTEMPT_DEADLINE_MS else LAST_ATTEMPT_DEADLINE_MS
                    attemptTimeoutJob = scope.launch {
                        delay(attemptBudget)
                        if (!stopped.get() && session === this@Session &&
                            attemptCounter.get() == attemptId && !currentAttemptVerified.get()
                        ) {
                            terminal = "CONNECTION_TIMEOUT"
                            OpenVpnNative.stop(coreId)
                        }
                    }

                    val runError = OpenVpnNative.run(coreId, config, username, password, this)
                    attemptTimeoutJob?.cancel()

                    // Cleanup per-attempt resources
                    if (session === this) OpenVpnSocks.stop()
                    relay?.let { r -> relay = null; runCatching { r.close() } }
                    synchronized(this) { runCatching { retainedTun?.close() }; retainedTun = null }
                    handle.getAndSet(0).takeIf { it != 0L }?.let { OpenVpnNative.release(it) }

                    if (terminal == null && runError.isNotEmpty()) terminal = runError

                    // Stop immediately if user cancelled, auth failed, or session was actually connected and later ended
                    if (stopped.get() || terminal == "AUTH_FAILED" || terminal == "CORE_UNAVAILABLE" || currentAttemptVerified.get()) {
                        break
                    }

                    prevMode = modeLabel
                    delay(250)
                }
            } catch (_: CancellationException) {
                stop()
            } catch (_: Exception) {
                if (terminal == null) terminal = "CONNECTION_FAILED"
            } catch (_: LinkageError) {
                terminal = "CORE_UNAVAILABLE"
            } finally {
                attemptTimeoutJob?.cancel()
                if (session === this) OpenVpnSocks.stop()
                relay?.let { r -> relay = null; runCatching { r.close() } }
                synchronized(this) { runCatching { retainedTun?.close() }; retainedTun = null }
                handle.getAndSet(0).takeIf { it != 0L }?.let { OpenVpnNative.release(it) }

                if (session === this) {
                    val state = OpenVpnRuntime.connection.value
                    val isError = terminal != null && (!stopped.get() || timedOut)
                    OpenVpnRuntime.mutable.value = state.copy(
                        phase = if (isError) ConnectionPhase.ERROR else ConnectionPhase.DISCONNECTED,
                        error = if (isError) terminal else null,
                        detail = null,
                    )
                    if (!shuttingDown) notifyStatus(terminal ?: "DISCONNECTED")
                }
                commands.trySend(Command(finished = this))
            }
        }

        private fun prepareEffectiveConfig(profile: Profile): String {
            val isTcp = profile.remotes.firstOrNull()?.protocol?.startsWith("tcp") == true || ProfileRuntime.isTunnelBear(profile)
            return if ((profile.splitTcp || cmd.splitTcp) && isTcp) {
                val remote = profile.remotes.first()
                val targetPort = if (ProfileRuntime.isTunnelBear(profile) && !remote.protocol.startsWith("tcp")) {
                    ProfileRuntime.TUNNELBEAR_TCP_PORT
                } else {
                    remote.port
                }
                val r = OpenVpnSplitRelay(this@OpenVpnService, listOf(remote.host), targetPort)
                relay = r
                r.start()
                ProfileRuntime.effective(profile, tcp = true, relayPort = r.localPort)
            } else {
                ProfileRuntime.effective(profile, tcp = false)
            }
        }

        @Keep
        fun configureTun(operation: String, value: Int): Boolean = runCatching {
            when (operation) {
                "new" -> {
                    builder = Builder().setSession("NAJIONE OpenVPN").setBlocking(false)
                    dnsServersAdded.set(0)
                    v4RouteAdded.set(false)
                    true
                }
                "mtu" -> {
                    val ns = com.najishab.one.utils.NetworkSettings
                    val own = ns.methodMtu(this@OpenVpnService, com.najishab.one.utils.NetworkSettings.Method.OPENVPN).takeIf { it > 0 }
                        ?: ns.globalMtu(this@OpenVpnService).takeIf { it > 0 }
                    builder!!.setMtu(own ?: value.coerceIn(576, 9000))
                    true
                }
                "protect" -> protect(value)
                else -> false
            }
        }.getOrDefault(false)

        @Keep
        fun addAddress(address: String, prefix: Int): Boolean = runCatching {
            if (cmd.blockIpv6 && address.contains(':')) {
                return@runCatching true
            }
            builder!!.addAddress(address, prefix)
            true
        }.getOrDefault(false)

        @Keep
        fun addRoute(address: String, prefix: Int): Boolean = runCatching {
            if (cmd.blockIpv6 && (address.contains(':') || address == "::")) {
                return@runCatching true
            }
            OpenVpnBypass.split(address, prefix).forEach { (a, p) -> builder!!.addRoute(a, p) }
            if (address == "0.0.0.0" && prefix == 0) {
                v4RouteAdded.set(true)
            }
            true
        }.getOrDefault(false)

        @Keep
        fun addDns(address: String, ignored: Int): Boolean = runCatching {
            if (cmd.blockIpv6 && address.contains(':')) {
                return@runCatching true
            }
            dnsServersAdded.incrementAndGet()
            builder!!.addDnsServer(address)
            true
        }.getOrDefault(false)

        @Keep
        fun addSearchDomain(domain: String, ignored: Int) = runCatching { builder!!.addSearchDomain(domain); true }.getOrDefault(false)

        @Keep
        fun establishTun(): Int {
            if (stopped.get()) return -1
            if (dnsServersAdded.get() == 0) {
                val configuredDns = com.najishab.one.utils.NetworkSettings.dnsList(this@OpenVpnService)
                val defaultList = (configuredDns + listOf("1.1.1.1", "8.8.8.8"))
                    .filter { !cmd.blockIpv6 || !it.contains(':') }
                    .distinct()
                defaultList.forEach { dns ->
                    runCatching { builder?.addDnsServer(dns) }
                }
            }
            // Always ensure IPv4 default route is added so all IPv4 traffic enters the tunnel:
            if (!v4RouteAdded.get()) {
                OpenVpnBypass.split("0.0.0.0", 0).forEach { (a, p) -> builder?.addRoute(a, p) }
                v4RouteAdded.set(true)
            }
            // Only add IPv6 default route if Block IPv6 is disabled:
            if (!cmd.blockIpv6) {
                OpenVpnBypass.split("::", 0).forEach { (a, p) -> builder?.addRoute(a, p) }
            }
            builder?.let { applySplitTunnel(it) }
            builder?.let { com.najishab.core.tunnel.SmartIranRoute.excludeIranRoutes(this@OpenVpnService, it) }
            val tun = builder?.establish() ?: return -1
            retainedTun?.close()
            retainedTun = tun
            return ParcelFileDescriptor.dup(tun.fileDescriptor).detachFd()
        }

        private fun applySplitTunnel(b: Builder) {
            val split = com.najishab.core.tunnel.SplitTunnelSettings(this@OpenVpnService)
            val (apps, iranian) = com.najishab.core.tunnel.SmartIranRoute.appsFor(this@OpenVpnService, split.mode(), split.packages() - packageName)
            when (split.mode()) {
                com.najishab.core.tunnel.SplitTunnelSettings.Mode.INCLUDE -> if (apps.isNotEmpty()) {
                    (apps + packageName).forEach { runCatching { b.addAllowedApplication(it) } }
                }
                com.najishab.core.tunnel.SplitTunnelSettings.Mode.EXCLUDE -> apps.forEach { runCatching { b.addDisallowedApplication(it) } }
                com.najishab.core.tunnel.SplitTunnelSettings.Mode.ALL -> Unit
            }
            if (apps.isEmpty() || split.mode() != com.najishab.core.tunnel.SplitTunnelSettings.Mode.INCLUDE) {
                iranian.forEach { runCatching { b.addDisallowedApplication(it) } }
            }
        }

        @Keep
        fun onCoreEvent(name: String, error: Boolean) {
            if (session !== this || stopped.get()) return
            val phase = when (name) {
                "CONNECTED" -> null
                "RECONNECTING", "PAUSE", "RESOLVE", "WAIT", "CONNECTING" ->
                    if (OpenVpnRuntime.connection.value.connectedAt != null) ConnectionPhase.RECONNECTING else ConnectionPhase.CONNECTING
                "DISCONNECTED" -> ConnectionPhase.DISCONNECTING
                else -> null
            }

            if (name == "AUTH_FAILED") {
                terminal = "AUTH_FAILED"
                handle.get().takeIf { it != 0L }?.let { OpenVpnNative.stop(it) }
            } else if (error) {
                terminal = when (name) {
                    "CERT_VERIFY_FAIL", "TLS_VERSION_MIN", "CONNECTION_TIMEOUT", "NETWORK_UNREACHABLE" -> name
                    else -> "CONNECTION_FAILED"
                }
            }

            if (phase != null) {
                val old = OpenVpnRuntime.connection.value
                OpenVpnRuntime.mutable.value = old.copy(
                    phase = phase,
                    connectedAt = if (name == "CONNECTED") old.connectedAt ?: System.currentTimeMillis() else old.connectedAt,
                )
            }

            if (name == "CONNECTED") {
                terminal = null
                currentAttemptVerified.set(true)
                attemptTimeoutJob?.cancel()
                val old = OpenVpnRuntime.connection.value
                OpenVpnRuntime.mutable.value = old.copy(
                    phase = ConnectionPhase.CONNECTED,
                    detail = null,
                    connectedAt = old.connectedAt ?: System.currentTimeMillis(),
                )
                val modeTag = old.activeProtocol?.let { " ($it)" }.orEmpty()
                notifyStatus(tr("متصل$modeTag", "Connected$modeTag"))
                com.najishab.one.data.LastEngine.recordOpenVpn(this@OpenVpnService, old.profileId, OpenVpnRuntime.SELF)
                OpenVpnSocks.start(this@OpenVpnService)

                val generation = verifyGeneration.incrementAndGet()
                val currentAttempt = attemptCounter.get()
                Thread({ probeExitInfo(generation, currentAttempt) }, "ovpn-probe").start()
            } else {
                notifyStatus(terminal ?: name)
            }
        }

        private fun probeExitInfo(generation: Int, attemptId: Int) {
            repeat(2) { attempt ->
                if (stopped.get() || session !== this || verifyGeneration.get() != generation || attemptCounter.get() != attemptId) return
                val exit = runCatching { OpenVpnExit.probe(5_000) }.getOrNull()
                if (exit != null) {
                    if (stopped.get() || session !== this || verifyGeneration.get() != generation || attemptCounter.get() != attemptId) return
                    val old = OpenVpnRuntime.connection.value
                    if (old.phase == ConnectionPhase.CONNECTED) {
                        OpenVpnRuntime.mutable.value = old.copy(
                            exitIp = exit.first ?: old.exitIp,
                            exitCountry = exit.second ?: old.exitCountry,
                        )
                    }
                    return
                }
                if (attempt == 0) Thread.sleep(1_200)
            }
        }

        @Keep
        fun onCoreTraffic(bytesOut: Long, bytesIn: Long) {
            if (session === this && !stopped.get() && currentAttemptVerified.get()) {
                OpenVpnRuntime.mutable.value = OpenVpnRuntime.connection.value.copy(
                    received = bytesOut.coerceAtLeast(0),
                    sent = bytesIn.coerceAtLeast(0),
                )
            }
        }
    }

    private fun notification(status: String): android.app.Notification {
        val open = PendingIntent.getActivity(this, 610, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 611, Intent(this, OpenVpnService::class.java).setAction(DISCONNECT), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("NAJIONE · OpenVPN")
            .setContentText(status)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(OpenVpnRuntime.connection.value.active)
            .addAction(0, "Disconnect", stop)
            .build()
    }

    private fun notifyStatus(status: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification(status))
    }

    companion object {
        const val CONNECT = "com.najishab.openvpn.CONNECT"
        const val DISCONNECT = "com.najishab.openvpn.DISCONNECT"
        const val EXTRA_LOCATION_ID = "locationId"
        const val EXTRA_TRANSPORT_MODE = "transportMode"
        const val EXTRA_USERNAME = "username"
        const val EXTRA_PASSWORD = "password"
        const val EXTRA_MANUAL_CREDS = "manualCreds"
        const val EXTRA_SPLIT_TCP = "splitTcp"
        const val EXTRA_BLOCK_IPV6 = "blockIpv6"

        private const val CHANNEL = "najione_openvpn"
        private const val NOTIFICATION = 610
        private const val STOP_GRACE_MS = 5_000L
        private const val ATTEMPT_DEADLINE_MS = 20_000L
        private const val LAST_ATTEMPT_DEADLINE_MS = 30_000L
        private const val OVERALL_DEADLINE_MS = 140_000L
    }
}