package com.najishab.one.data

import android.util.Base64
import com.najishab.one.ui.createNodeFromUri
import com.najishab.one.utils.VpnConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * The subscriptions that ship with the app, and the "get 50 servers" buttons built on them.
 *
 * Deliberately NOT stored in [SubscriptionManager]: that list is what the settings screens show,
 * and these links are not meant to be seen there. Only the servers they produce are visible, filed
 * under a group per sub ([groupId]) and painted differently in the list.
 */
object DefaultSubs {

    /** A server from the Nth default sub is filed under `default_sub_N` (1-based). */
    const val GROUP_PREFIX = "default_sub_"

    /** How many servers one press adds, at most. */
    const val PICK_COUNT = 50

    /** Largest sub body read: well past any real list, far below something served by mistake. */
    private const val MAX_BYTES = 48L * 1024 * 1024

    private val URLS = listOf(
        "https://raw.githubusercontent.com/0xRadikal/Free-v2ray-Configs/main/top100.txt",
        "https://raw.githubusercontent.com/0xRadikal/Free-v2ray-Configs/main/google/configs.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-config/main/All_Configs_Sub.txt",
        "https://raw.githubusercontent.com/patterniha/Free-Configs/refs/heads/main/configs.txt",
        "https://v2freehub-sub.v2freehub.workers.dev",
    )

    /** The schemes the core can run and [VpnConfig.parseUri] can read. */
    private val SCHEMES = setOf("vless", "vmess", "trojan", "ss")

    val count: Int get() = URLS.size

    fun groupId(index: Int): String = GROUP_PREFIX + (index + 1)

    fun isDefaultGroup(group: String?): Boolean = group != null && group.startsWith(GROUP_PREFIX)

    /** "Sub 3" for `default_sub_3`; null for any other group. */
    fun labelOf(group: String?): String? {
        if (group == null || !isDefaultGroup(group)) return null
        val n = group.removePrefix(GROUP_PREFIX).toIntOrNull() ?: return null
        return "Sub $n"
    }

    enum class Mode { REPLACE, ADD }

    /**
     * What a press did.
     *
     * @property added servers that went into the list.
     * @property failed the 0-based subs that could not be fetched (or had nothing in them).
     * @property reachable whether at least one sub answered with something that looked like
     * configs. When it is false nothing was touched, in either mode.
     */
    class Outcome(val added: Int, val failed: List<Int>, val reachable: Boolean)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** One fetched sub: its candidate links in random order, or why it has none. */
    private class Source(val index: Int, val candidates: ArrayDeque<String>, val error: String?) {
        /** The next link that parses and is not in [seen]; it is added to [seen]. Null when none is left. */
        fun next(seen: MutableSet<String>): String? {
            while (candidates.isNotEmpty()) {
                val link = candidates.removeFirst()
                val key = keyOf(link)
                if (key in seen) continue
                if (!isUsable(link)) continue
                seen.add(key)
                return link
            }
            return null
        }
    }

    /** A link without its #remark: two links that differ only there are the same server. */
    private fun keyOf(link: String): String = link.substringBefore('#').trim()

    /**
     * Whether a link is complete enough to be worth adding. Broken or truncated lines (a list's
     * last line is often cut off) fail here instead of becoming rows that can never connect.
     */
    private fun isUsable(link: String): Boolean {
        val scheme = link.substringBefore("://", "").lowercase()
        if (scheme !in SCHEMES) return false
        val c = VpnConfig.parseUri(link) ?: return false
        if (c.address.isBlank() || c.port !in 1..65535) return false
        if ((scheme == "vless" || scheme == "trojan") && c.uuid.isBlank()) return false
        return true
    }

    private fun fetch(index: Int): Source {
        fun failed(why: String) = Source(index, ArrayDeque(), why)
        return try {
            val request = Request.Builder()
                .url(URLS[index])
                .header("User-Agent", "v2rayNG/1.8.31")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return failed("HTTP ${response.code}")
                val body = response.body ?: return failed("empty response")
                if (body.contentLength() > MAX_BYTES) return failed("response too large")
                var text = body.string()
                // Plain text is what these serve; base64 is the other thing a sub can be.
                if (!text.contains("://")) {
                    text = runCatching { String(Base64.decode(text.trim(), Base64.DEFAULT), Charsets.UTF_8) }
                        .getOrDefault(text)
                }
                // Only the cheap checks here. Parsing is done lazily, in random order, until enough
                // good ones are found -- a list of tens of thousands is never parsed whole.
                val lines = ArrayList<String>()
                for (raw in text.lineSequence()) {
                    val line = raw.trim()
                    if (line.length < 12 || line.length > 4096) continue
                    if (line.substringBefore("://", "").lowercase() !in SCHEMES) continue
                    lines.add(line)
                }
                if (lines.isEmpty()) return failed("no configs in the list")
                lines.shuffle()
                Source(index, ArrayDeque(lines), null)
            }
        } catch (e: Exception) {
            failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Get up to [PICK_COUNT] new servers and put them in the list.
     *
     * @param source the 0-based sub to take them from, or null for Random: all subs, taken in
     * turn so that one huge list cannot crowd out the small ones.
     *
     * Nothing already in the list is ever added again (a server in the user's own folders counts).
     * In [Mode.REPLACE] the servers from earlier presses go first -- the one sub's for a sub
     * button, every default sub's for Random -- but only once the new ones are in hand: a failed
     * download or an empty answer leaves the list exactly as it was.
     */
    suspend fun load(nodeManager: NodeManager, source: Int?, mode: Mode): Outcome =
        withContext(Dispatchers.IO) {
            nodeManager.awaitLoaded()

            val indices = if (source == null) URLS.indices.toList() else listOf(source)
            val sources = coroutineScope { indices.map { async { fetch(it) } }.awaitAll() }
            val failedSubs = sources.filter { it.error != null }.map { it.index }
            val live = sources.filter { it.error == null }
            if (live.isEmpty()) return@withContext Outcome(0, failedSubs, reachable = false)

            val doomed = indices.map { groupId(it) }.toSet()
            val seen = HashSet<String>()
            synchronized(nodeManager.nodes) {
                for (n in nodeManager.nodes) {
                    if (mode == Mode.REPLACE && n.engineType == "Manual" &&
                        n.groupTitle?.let { it in doomed } == true) continue
                    if (!n.uri.startsWith("{")) seen.add(keyOf(n.uri))
                }
            }

            // Round-robin over the subs until there are enough or every one is dry.
            val picked = LinkedHashMap<Int, MutableList<String>>()
            var total = 0
            var progressed = true
            while (total < PICK_COUNT && progressed) {
                progressed = false
                for (s in live) {
                    if (total >= PICK_COUNT) break
                    val link = s.next(seen) ?: continue
                    picked.getOrPut(s.index) { mutableListOf() }.add(link)
                    total++
                    progressed = true
                }
            }
            if (total == 0) return@withContext Outcome(0, failedSubs, reachable = true)

            if (mode == Mode.REPLACE) doomed.forEach { nodeManager.removeNodesByGroup(it) }

            var added = 0
            for ((index, links) in picked) {
                added += nodeManager.addNodes(links.map { createNodeFromUri(it) }, groupId(index))
            }
            Outcome(added, failedSubs, reachable = true)
        }
}
