package com.najishab.one.ui.freedom

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.najishab.one.ui.tunnel.TunnelUiState
import com.najishab.one.utils.LocalPort
import com.najishab.one.widget.WidgetLive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/** The real public IP (and country) the app currently presents to the internet. Never invented. */
data class FreedomIpInfo(val ip: String? = null, val country: String? = null, val loading: Boolean = false)

private val IP_RE = Regex("\"ip\"\\s*:\\s*\"([^\"]+)\"")
private val CC_RE = Regex("\"country_code\"\\s*:\\s*\"([A-Za-z]{2})\"")

private fun client(proxyPort: Int?): OkHttpClient {
    val b = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).callTimeout(12, TimeUnit.SECONDS)
    if (proxyPort != null && proxyPort > 0) b.proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", proxyPort)))
    return b.build()
}

private fun parse(body: String): FreedomIpInfo? {
    val ip = IP_RE.find(body)?.groupValues?.get(1)?.trim()
    if (ip.isNullOrBlank()) return null
    return FreedomIpInfo(ip, CC_RE.find(body)?.groupValues?.get(1)?.uppercase())
}

/** Through the app's own local SOCKS proxy -- the path into an Xray-family tunnel (app is out of its own tun). */
private fun geoViaProxy(port: Int): FreedomIpInfo? {
    if (port <= 0) return null
    return runCatching {
        client(port).newCall(Request.Builder().url("https://api.ip.sb/geoip").header("User-Agent", "Mozilla/5.0").build())
            .execute().use { r -> if (r.isSuccessful) parse(r.body?.string().orEmpty()) else null }
    }.getOrNull()
}

/** Direct (no tunnel): the device's real public IP. A couple of fallbacks for filtered lines. */
private fun geoDirect(): FreedomIpInfo? {
    runCatching {
        client(null).newCall(Request.Builder().url("https://api.ip.sb/geoip").header("User-Agent", "Mozilla/5.0").build())
            .execute().use { r -> if (r.isSuccessful) parse(r.body?.string().orEmpty()) else null }
    }.getOrNull()?.let { return it }
    runCatching {
        client(null).newCall(Request.Builder().url("https://api.ipify.org?format=json").build())
            .execute().use { r -> if (r.isSuccessful) parse(r.body?.string().orEmpty()) else null }
    }.getOrNull()?.let { return it }
    return runCatching {
        client(null).newCall(Request.Builder().url("https://checkip.amazonaws.com").build())
            .execute().use { r -> if (r.isSuccessful) FreedomIpInfo(r.body?.string()?.trim()?.takeIf { it.isNotBlank() }) else null }
    }.getOrNull()
}

/**
 * Resolves the IP the app presents right now: the engine-reported exit IP for the transports,
 * a geo lookup through the local proxy for the Xray family, and a direct lookup (the real IP)
 * when nothing is connected. Retries a few times; cancels with the composition.
 */
@Composable
fun rememberFreedomIp(conn: WidgetLive.Conn, tunnel: TunnelUiState): FreedomIpInfo {
    val context = LocalContext.current
    var info by remember { mutableStateOf(FreedomIpInfo()) }
    val engineIp = if (conn.up) tunnel.exitIp?.takeIf { it.isNotBlank() } else null
    val engineCountry = if (conn.up) tunnel.country.takeIf { it.isNotBlank() } else null
    LaunchedEffect(conn.up, conn.method, conn.since, engineIp) {
        if (engineIp != null) {
            info = FreedomIpInfo(engineIp, engineCountry ?: conn.country, loading = false)
            if (engineCountry != null || conn.country != null) return@LaunchedEffect
        } else {
            info = FreedomIpInfo(loading = true)
        }
        val app = context.applicationContext
        for (w in longArrayOf(300, 2_000, 4_000, 8_000)) {
            delay(w)
            val got = withContext(Dispatchers.IO) { if (conn.up) geoViaProxy(LocalPort.get(app)) else geoDirect() }
            if (got?.ip != null) {
                info = FreedomIpInfo(got.ip, got.country ?: engineCountry ?: conn.country, loading = false)
                return@LaunchedEffect
            }
        }
        info = FreedomIpInfo(engineIp, engineCountry ?: conn.country, loading = false)
    }
    return info
}