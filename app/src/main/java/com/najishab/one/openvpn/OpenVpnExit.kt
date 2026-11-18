package com.najishab.one.openvpn

import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket

object OpenVpnExit {

    /** (exitIpOrNull, countryCodeOrNull) or null if tunnel carries no traffic. */
    fun probe(timeoutMs: Int): Pair<String?, String?>? {
        probeCloudflareTrace(timeoutMs)?.let { return it }
        probeIpApi(timeoutMs)?.let { return it }
        if (probeGenerate204(timeoutMs)) return null to null
        return null
    }

    private fun probeCloudflareTrace(timeoutMs: Int): Pair<String?, String?>? = runCatching {
        // Direct probe to 1.1.1.1 does not require DNS resolution
        probeCloudflareHost("1.1.1.1", timeoutMs) ?: probeCloudflareHost("www.cloudflare.com", timeoutMs)
    }.getOrNull()

    private fun probeCloudflareHost(host: String, timeoutMs: Int): Pair<String?, String?>? = runCatching {
        Socket().use { s ->
            s.connect(InetSocketAddress(host, 80), timeoutMs)
            s.soTimeout = timeoutMs
            s.getOutputStream().apply {
                write("GET /cdn-cgi/trace HTTP/1.0\r\nHost: $host\r\nConnection: close\r\n\r\n".toByteArray())
                flush()
            }
            val text = s.getInputStream().readBytes().toString(Charsets.UTF_8)
            val body = text.substringAfter("\r\n\r\n", "")
            var ip: String? = null
            var loc: String? = null
            for (line in body.lineSequence()) {
                if (line.startsWith("ip=")) ip = line.substringAfter('=').trim().takeIf { it.isNotBlank() }
                else if (line.startsWith("loc=")) loc = line.substringAfter('=').trim().uppercase().takeIf { it.length == 2 && it != "XX" && it != "T1" }
            }
            if (ip != null || loc != null) ip to loc else null
        }
    }.getOrNull()

    private fun probeIpApi(timeoutMs: Int): Pair<String?, String?>? = runCatching {
        val host = "ip-api.com"
        Socket().use { s ->
            s.connect(InetSocketAddress(host, 80), timeoutMs)
            s.soTimeout = timeoutMs
            s.getOutputStream().apply {
                write("GET /json/?fields=status,countryCode,query HTTP/1.1\r\nHost: $host\r\nConnection: close\r\n\r\n".toByteArray())
                flush()
            }
            val text = s.getInputStream().readBytes().toString(Charsets.UTF_8)
            val body = text.substringAfter("\r\n\r\n", "")
            val start = body.indexOf('{')
            if (start < 0) return null
            val o = JSONObject(body.substring(start, body.lastIndexOf('}') + 1))
            if (o.optString("status") != "success") return null
            val ip = o.optString("query").takeIf { it.isNotBlank() } ?: return null
            val cc = o.optString("countryCode").takeIf { it.length == 2 }
            ip to cc
        }
    }.getOrNull()

    private fun probeGenerate204(timeoutMs: Int): Boolean = runCatching {
        val host = "connectivitycheck.gstatic.com"
        Socket().use { s ->
            s.connect(InetSocketAddress(host, 80), timeoutMs)
            s.soTimeout = timeoutMs
            s.getOutputStream().apply {
                write("GET /generate_204 HTTP/1.0\r\nHost: $host\r\nConnection: close\r\n\r\n".toByteArray())
                flush()
            }
            val line = s.getInputStream().bufferedReader().readLine().orEmpty()
            line.contains(" 204") || line.contains(" 200")
        }
    }.getOrDefault(false)
}