package com.najishab.one.openvpn

import com.najishab.one.MyVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

object OpenVpnLatency {

    private const val REPLY_MS = 3_000

    /** Measures a logical location's latency according to the chosen protocol/mode. */
    suspend fun measureLocation(location: OpenVpnLocation, selectedMode: String): ProbeResult =
        withContext(Dispatchers.IO) {
            currentCoroutineContext().ensureActive()
            val candidateProfiles = buildList {
                if (selectedMode.startsWith("TCP")) {
                    location.variants[selectedMode]?.let { add(selectedMode to it) }
                } else {
                    for (mode in listOf("TCP-443", "TCP-1195", "TCP-53")) {
                        location.variants[mode]?.let { add(mode to it) }
                    }
                    if (isEmpty()) {
                        location.variants.entries.firstOrNull { it.key.startsWith("TCP") }
                            ?.let { add(it.key to it.value) }
                    }
                }
            }

            if (candidateProfiles.isEmpty()) {
                return@withContext ProbeResult(null, System.currentTimeMillis(), "TCP_CONNECT", "NO_TCP_VARIANT")
            }

            var bestMs: Long? = null
            val healthyModes = mutableListOf<String>()

            for ((mode, profile) in candidateProfiles) {
                currentCoroutineContext().ensureActive()
                val remote = profile.remotes.firstOrNull() ?: continue
                val ms = measureTcpPort(remote.host, remote.port)
                if (ms != null) {
                    healthyModes += mode
                    if (bestMs == null || ms < bestMs) bestMs = ms
                    // If user picked a specific TCP mode or first one answered, no need to probe all unless Auto failed
                    if (selectedMode.startsWith("TCP")) break
                }
            }

            if (bestMs != null) {
                ProbeResult(bestMs, System.currentTimeMillis(), "TCP_CONNECT", healthy = healthyModes)
            } else {
                ProbeResult(null, System.currentTimeMillis(), "TCP_CONNECT", "NO_RESPONSE")
            }
        }

    /** Kept for Scout (OpenVpnAdapter) and legacy callers. */
    suspend fun measure(profile: Profile): ProbeResult = withContext(Dispatchers.IO) {
        val remote = profile.remotes.firstOrNull()
            ?: return@withContext ProbeResult(null, System.currentTimeMillis(), "TCP_CONNECT", "NO_REMOTE")
        val tcp = remote.protocol.startsWith("tcp")
        if (!tcp) {
            // Try TCP 443 on the same host for reachability if UDP profile is passed
            val ms443 = measureTcpPort(remote.host, 443)
            return@withContext if (ms443 != null) {
                ProbeResult(ms443, System.currentTimeMillis(), "TCP_CONNECT")
            } else {
                ProbeResult(null, System.currentTimeMillis(), "OPENVPN_UDP_RESET", "NO_RESPONSE")
            }
        }
        val ms = measureTcpPort(remote.host, remote.port)
        if (ms != null) ProbeResult(ms, System.currentTimeMillis(), "TCP_CONNECT")
        else ProbeResult(null, System.currentTimeMillis(), "TCP_CONNECT", "NO_RESPONSE")
    }

    private suspend fun measureTcpPort(host: String, port: Int): Long? {
        return try {
            currentCoroutineContext().ensureActive()
            val address = InetAddress.getAllByName(host).firstOrNull() ?: return null
            currentCoroutineContext().ensureActive()
            val start = System.nanoTime()
            Socket().use { s ->
                MyVpnService.instance?.protect(s)
                s.tcpNoDelay = true
                s.connect(InetSocketAddress(address, port), REPLY_MS)
            }
            ((System.nanoTime() - start) / 1_000_000).coerceAtLeast(1)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
}

object ServerPolicy {
    fun fastestLocation(locations: List<OpenVpnLocation>, now: Long): OpenVpnLocation? =
        locations.filter {
            it.probe?.let { p -> p.millis != null && p.error == null && now - p.checkedAt in 0..600_000L } == true
        }.minByOrNull { it.probe!!.millis!! }

    fun fastest(profiles: List<Profile>, now: Long): Profile? = profiles.filter {
        it.probe?.let { p -> p.millis != null && p.error == null && now - p.checkedAt in 0..600_000L } == true
    }.minByOrNull { it.probe!!.millis!! }
}