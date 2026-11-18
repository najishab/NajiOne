package com.najishab.one.models

data class VpnNode(
    val id: String,
    val name: String,
    val uri: String,
    val type: String, // 'vless', 'trojan', etc.
    var ping: String = "Test",
    var delay: String = "Test",
    var speed: String = "Test",
    val addedAt: Long = System.currentTimeMillis(),
    val engineType: String = "BPB", // "BPB" or "EDG"
    var countryCode: String? = null,
    var groupTitle: String? = null
) {
    companion object {
        /** "MLM" is the NAJI panel's code as saved before the rename; nodes restored from old
         * backups and exports still carry it. */
        fun normalizeEngineType(raw: String): String = if (raw == "MLM") "NJI" else raw
    }
}
