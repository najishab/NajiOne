package com.najishab.one.utils

import org.json.JSONArray
import org.json.JSONObject

/**
 * Test shim for legacy test cases in [XrayGeminiExitTest] and [XrayGoogleFixTest]
 * that reference `generateAntiSanctionConfig` which was consolidated into `generateConfig`.
 */
fun XrayJsonGenerator.generateAntiSanctionConfig(
    config: VpnConfig,
    localPort: Int,
    sanctionedDomains: List<String> = emptyList(),
    proxyEverything: Boolean = false,
): String {
    val base = generateConfig(config, localPort)
    if (!googleFix) return base
    val json = JSONObject(base)
    val routing = json.optJSONObject("routing") ?: return base
    val rules = routing.optJSONArray("rules") ?: return base

    val googleDomains = if (proxyEverything) {
        JSONArray().apply {
            listOf("domain:google.com", "domain:googleapis.com", "domain:gstatic.com").forEach { put(it) }
        }
    } else {
        JSONArray().apply {
            sanctionedDomains.filter { it.contains("google") }.forEach { put(it) }
        }
    }

    for (i in 0 until rules.length()) {
        val r = rules.getJSONObject(i)
        if (r.optString("outboundTag") == XrayJsonGenerator.GOOGLE_V4_TAG) {
            r.put("domain", googleDomains)
        }
        if (proxyEverything && r.optString("outboundTag") == XrayJsonGenerator.QUIC_REFUSAL_TAG) {
            r.remove("ip")
        }
    }
    return json.toString()
}
