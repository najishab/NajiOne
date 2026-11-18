package com.najishab.one.openvpn

enum class AuthState { UNKNOWN, ACCEPTED, FAILED, VERIFICATION_REQUIRED }
enum class AccountState { READY, USAGE_UNAVAILABLE, QUOTA_LOW, DEPLETED, AUTH_FAILED, VERIFICATION_REQUIRED, UNAVAILABLE }

data class ProviderUsage(val total: Long, val used: Long, val checkedAt: Long, val resetAt: Long? = null) {
    init { require(total >= 0 && used in 0..total && checkedAt > 0) }
    val remaining: Long get() = total - used
    fun resetPassed(now: Long) = resetAt?.let { now >= it && it > checkedAt } == true
    fun fresh(now: Long) = now - checkedAt in 0..900_000L && !resetPassed(now)
}

data class Account(
    val id: String,
    val username: String,
    val auth: AuthState = AuthState.UNKNOWN,
    val usage: ProviderUsage? = null,
    val lastChecked: Long? = null,
    val lastConnected: Long? = null,
    val unavailableUntil: Long = 0,
    val lastError: String? = null,
    val credentialRef: String = id,
) {
    fun usable(now: Long) = auth != AuthState.FAILED && auth != AuthState.VERIFICATION_REQUIRED &&
        (usage?.remaining != 0L || usage.resetPassed(now)) && unavailableUntil <= now

    fun state(now: Long): AccountState = when {
        auth == AuthState.FAILED -> AccountState.AUTH_FAILED
        auth == AuthState.VERIFICATION_REQUIRED -> AccountState.VERIFICATION_REQUIRED
        usage?.remaining == 0L && !usage.resetPassed(now) -> AccountState.DEPLETED
        unavailableUntil > now -> AccountState.UNAVAILABLE
        usage == null || !usage.fresh(now) -> AccountState.USAGE_UNAVAILABLE
        usage.total > 0 && usage.remaining.toDouble() / usage.total < .1 -> AccountState.QUOTA_LOW
        else -> AccountState.READY
    }
}

object AccountPolicy {
    fun confirmedUnusable(a: Account, now: Long) = a.auth == AuthState.FAILED ||
        a.auth == AuthState.VERIFICATION_REQUIRED || (a.usage?.let { it.fresh(now) && it.remaining == 0L } == true)

    fun best(accounts: List<Account>, now: Long, excluded: Set<String> = emptySet()): Account? =
        accounts.filter { it.id !in excluded && it.usable(now) }.maxWithOrNull(
            compareBy<Account> { if (it.usage?.fresh(now) == true) 1 else 0 }
                .thenBy { if (it.auth == AuthState.ACCEPTED && now - (it.lastConnected ?: 0) in 0..86_400_000L) 1 else 0 }
                .thenBy { if (it.usage?.fresh(now) == true) it.usage.remaining else -1L }
                .thenBy { it.lastConnected ?: 0L }
        )
}

data class Remote(val host: String, val port: Int, val protocol: String)

data class ProbeResult(
    val millis: Long?,
    val checkedAt: Long,
    val method: String,
    val error: String? = null,
    val healthy: List<String> = emptyList(),
)

class Profile(
    val id: String,
    val name: String,
    val config: String,
    val remotes: List<Remote>,
    val authenticatedControl: Boolean = false,
    val favorite: Boolean = false,
    val probe: ProbeResult? = null,
    val splitTcp: Boolean = false,
) {
    fun withFavorite(value: Boolean) = Profile(id, name, config, remotes, authenticatedControl, value, probe, splitTcp)
    fun withProbe(value: ProbeResult) = Profile(id, name, config, remotes, authenticatedControl, favorite, value, splitTcp)

    val ownCredentials: Pair<String, String>? get() = inlineCredentials(config)

    val selfContained: Boolean
        get() = ownCredentials != null || config.lineSequence().none { it.trim().lowercase().startsWith("auth-user-pass") }

    companion object {
        fun inlineCredentials(config: String): Pair<String, String>? {
            val lines = config.lineSequence().map { it.trim() }.toList()
            val start = lines.indexOf("<auth-user-pass>")
            if (start < 0) return null
            val end = lines.indexOf("</auth-user-pass>")
            if (end < start) return null
            val body = lines.subList(start + 1, end).filter { it.isNotEmpty() }
            return body.getOrNull(0)?.let { it to body.getOrElse(1) { "" } }
        }
    }

    override fun toString() = "OpenVPN profile $id"
}

/**
 * A logical server location (e.g. "Australia-Melbourne") backed by all its
 * protocol/port profiles inside assets/ovpn_server/<folder>/.
 */
data class OpenVpnLocation(
    val id: String,
    val name: String,
    val variants: Map<String, Profile>, // folderName ("TCP-443") -> Profile
    val favorite: Boolean = false,
    val probe: ProbeResult? = null,
) {
    fun profileFor(mode: String): Profile? {
        if (mode == AUTO_MODE) {
            return PRIORITY_MODES.firstNotNullOfOrNull { variants[it] } ?: variants.values.firstOrNull()
        }
        return variants[mode]
    }

    fun orderedAttempts(selectedMode: String): List<Pair<String, Profile>> {
        val availableKeys = variants.keys.toList()
        val orderedKeys = buildList {
            if (selectedMode != AUTO_MODE && selectedMode in variants) {
                add(selectedMode)
            }
            for (p in PRIORITY_MODES) {
                if (p in variants && p !in this) add(p)
            }
            for (k in availableKeys.sorted()) {
                if (k !in this) add(k)
            }
        }
        return orderedKeys.mapNotNull { key -> variants[key]?.let { key to it } }
    }

    companion object {
        const val AUTO_MODE = "AUTO"
        val PRIORITY_MODES = listOf("TCP-443", "UDP-444", "TCP-1195", "UDP-1194", "UDP-1195", "TCP-53")

        fun formatModeLabel(mode: String): String {
            if (mode == AUTO_MODE) return "Auto"
            val parts = mode.split('-', limit = 2)
            return if (parts.size == 2) "${parts[0]} · ${parts[1]}" else mode
        }
    }
}

enum class ConnectionPhase { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, DISCONNECTING, ERROR }

data class OpenVpnConnection(
    val phase: ConnectionPhase = ConnectionPhase.DISCONNECTED,
    val profileId: String? = null,
    val accountId: String? = null,
    val connectedAt: Long? = null,
    val received: Long = 0,
    val sent: Long = 0,
    val error: String? = null,
    val detail: String? = null,
    val exitIp: String? = null,
    val exitCountry: String? = null,
    val locationId: String? = null,
    val activeProtocol: String? = null,
    val switchedFrom: String? = null,
    val switchNotice: String? = null,
    val usingManualCredentials: Boolean = false,
) {
    val active get() = phase !in setOf(ConnectionPhase.DISCONNECTED, ConnectionPhase.ERROR)
}