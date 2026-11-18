package com.najishab.one.engines.npv

import android.util.Log
import com.najishab.one.utils.VpnConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * The public subscription lists the NPV library refresh also reads from: four plain GitHub lists,
 * plus the "verified" and "fast" categories of the 0xRadikal aggregator (already tested by it,
 * so they are kept as the preferred shelf).
 */
internal object NpvPublicLists {

    private const val TAG = "NpvPublicLists"

    /** (url, isBase64Encoded) */
    private val SOURCES = listOf(
        "https://raw.githubusercontent.com/hello-world-1989/cn-news/main/end-gfw-together" to true,
        "https://raw.githubusercontent.com/V2RayRoot/V2RayConfig/refs/heads/main/Config/vless.txt" to false,
        "https://raw.githubusercontent.com/4n0nymou3/multi-proxy-config-fetcher/refs/heads/main/configs/proxy_configs_tested.txt" to false,
        "https://raw.githubusercontent.com/kingowow/Kingo-vpn/refs/heads/main/server/KingoVpn.txt" to false
    )

    private const val INDEX_URL = "https://raw.githubusercontent.com/0xRadikal/Free-v2ray-Configs/main/index.json"
    private const val INDEX_MIRROR = "https://cdn.jsdelivr.net/gh/0xRadikal/Free-v2ray-Configs@main/index.json"

    /** id -> path of the category file, relative to the aggregator's base. */
    private val PREFERRED_POOLS = listOf(
        "verified" to "verified/configs.txt",
        "fast" to "fast/configs.txt",
    )

    private val URI_PREFIXES = listOf("vless://", "vmess://", "trojan://", "ss://")

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun linksIn(text: String): List<String> =
        text.lines()
            .map { it.trim() }
            .filter { line -> URI_PREFIXES.any { prefix -> line.startsWith(prefix, ignoreCase = true) } }

    /** Same server appearing in several lists differs only by its remark, so identity is the connection itself. */
    private fun dedupeKey(uri: String): String {
        val config = VpnConfig.parseUri(uri) ?: return uri
        val secret = if (config.protocol == "ss") "${config.method}:${config.password}" else config.uuid
        return "${config.protocol}|${config.address}|${config.port}|$secret"
    }

    /** Fetch text, primary first then the jsDelivr mirror -- raw.githubusercontent is filtered. */
    private suspend fun fetchText(url: String, mirror: String?): String = withContext(Dispatchers.IO) {
        var lastErr: String? = null
        for (target in listOfNotNull(url, mirror)) {
            try {
                client.newCall(Request.Builder().url(target).get().build()).execute().use { res ->
                    if (!res.isSuccessful) {
                        lastErr = "HTTP ${res.code}"
                        return@use
                    }
                    return@withContext res.body?.string().orEmpty()
                }
            } catch (e: Exception) {
                lastErr = e.message
            }
        }
        throw IllegalStateException(lastErr ?: "not fetched")
    }

    /** The four plain lists, deduplicated by real server identity. */
    suspend fun fetchLists(): List<String> = withContext(Dispatchers.IO) {
        val jobs = SOURCES.map { (url, isBase64) ->
            async {
                try {
                    client.newCall(Request.Builder().url(url).get().build()).execute().use { res ->
                        if (!res.isSuccessful) return@async emptyList<String>()
                        val raw = res.body?.string() ?: return@async emptyList<String>()
                        val text = if (isBase64) {
                            try {
                                String(android.util.Base64.decode(raw.trim(), android.util.Base64.DEFAULT))
                            } catch (e: Exception) {
                                raw
                            }
                        } else raw
                        linksIn(text)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "source failed: $url (${e.message})")
                    emptyList()
                }
            }
        }
        jobs.awaitAll().flatten().distinctBy { dedupeKey(it) }
    }

    /** The aggregator's "verified" and "fast" categories, for those the index reports as non-empty. */
    suspend fun fetchPreferred(): List<String> = withContext(Dispatchers.IO) {
        val idx = org.json.JSONObject(fetchText(INDEX_URL, INDEX_MIRROR))
        val base = idx.optString("raw_base").ifBlank { "https://raw.githubusercontent.com/0xRadikal/Free-v2ray-Configs/main" }
        val mirrorBase = idx.optString("cdn_base").ifBlank { "https://cdn.jsdelivr.net/gh/0xRadikal/Free-v2ray-Configs@main" }

        // Counts live in two objects: the cascade ones (verified/fast/secure) and the plain ones.
        val counts = HashMap<String, Int>()
        for (key in listOf("cascade_categories", "categories")) {
            val obj = idx.optJSONObject(key) ?: continue
            for (name in obj.keys()) {
                obj.optJSONObject(name)?.optInt("unique")?.takeIf { it > 0 }?.let { counts[name] = it }
            }
        }

        val out = ArrayList<String>()
        for ((id, path) in PREFERRED_POOLS) {
            if (counts[id] == null) continue
            val text = runCatching { fetchText("$base/$path", "$mirrorBase/$path") }.getOrNull() ?: continue
            out += withContext(Dispatchers.Default) { linksIn(text).distinctBy { dedupeKey(it) } }
        }
        out
    }
}
