package com.najishab.one.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Two cheap reachability probes shared by the engines that pre-filter a server before spending
 * a whole Xray instance on it.
 */
object NetProbe {

    private const val TLS_TIMEOUT_MS = 3_000

    /** Opens a raw TCP socket and returns how long that took in ms, or -1 when it did not open. */
    internal suspend fun tcpOpen(host: String, port: Int, timeoutMs: Int): Int = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        try {
            Socket().use { s ->
                s.connect(InetSocketAddress(host, port), timeoutMs)
                (System.currentTimeMillis() - started).toInt()
            }
        } catch (e: Exception) {
            -1
        }
    }

    /**
     * Does anything actually answer TLS for this config's server name?
     *
     * Deliberately does NOT verify the certificate. The question is "is there a live endpoint for
     * this name", not "is it trusted" -- a self-signed or mismatched certificate is completely
     * normal for these servers and says nothing about whether the proxy works.
     *
     * Returns true when TLS is not expected at all, so plain configs pass straight through.
     */
    internal fun tlsAnswers(config: VpnConfig): Boolean {
        val security = config.tls.lowercase()
        // REALITY does not present an ordinary certificate to an ordinary client, so a plain
        // handshake proves nothing either way; let those through untouched.
        if (security != "tls") return true
        val serverName = config.sni.ifEmpty { config.wsHost }.ifEmpty { config.address }
        if (serverName.isEmpty()) return true

        return try {
            Socket().use { raw ->
                raw.connect(InetSocketAddress(config.address, config.port), TLS_TIMEOUT_MS)
                raw.soTimeout = TLS_TIMEOUT_MS
                val ctx = javax.net.ssl.SSLContext.getInstance("TLS")
                ctx.init(null, arrayOf<javax.net.ssl.TrustManager>(TrustEverything), java.security.SecureRandom())
                val ssl = ctx.socketFactory.createSocket(raw, serverName, config.port, false)
                        as javax.net.ssl.SSLSocket
                // The name has to travel as SNI, which is the whole point of this check.
                runCatching {
                    val params = ssl.sslParameters
                    params.serverNames = listOf(javax.net.ssl.SNIHostName(serverName))
                    ssl.sslParameters = params
                }
                ssl.use { it.startHandshake() }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Certificates are irrelevant here -- see [tlsAnswers]. */
    private object TrustEverything : javax.net.ssl.X509TrustManager {
        override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
    }
}
