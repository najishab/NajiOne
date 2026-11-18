package com.najishab.one.engines.amnezia

import android.content.Context
import com.najishab.one.engines.flux.FluxNet
import com.najishab.one.engines.mae.model.FamilyPolicy
import com.najishab.one.engines.mae.route.Capabilities
import com.najishab.one.engines.mae.route.RouteKind
import com.najishab.one.engines.mae.route.RouteProvider
import org.json.JSONObject

/**
 * A WireGuard server (kept in the Amnezia store) as a MAE
 * exit: a fixed server abroad, so a stable exit -- what Gemini and Google Flow need -- and MAE proves
 * it per app like every other one. Xray dials it itself, with junk packets in front exactly as the
 * Amnezia engine's own tests send them (a bare handshake is dropped on some carriers).
 *
 * One WireGuard session per key and server: the same route in MAE's probe core and in its tunnel
 * would take the session from each other, so it is probed only while MAE is not the tunnel (see
 * MaeEngine.probeProviders), the way WARP is without its second identity.
 */
class WgExitRoute(
    override val id: String,
    private val conf: WgConfig,
    val country: String?,
) : RouteProvider {
    override val kind = RouteKind.FOREIGN
    override val caps = Capabilities(udp = true)
    override val cost = 0.12
    override val families = listOf(FamilyPolicy.BOTH)

    // The same length for every one, so no tag is a prefix of another.
    override fun tag(family: FamilyPolicy) = "mae-$id-d"
    private val noiseTag = "mae-$id-n"

    override fun outbounds(): List<JSONObject> {
        val noise = conf.noiseOutbound(noiseTag, obfuscate = true)
        return listOfNotNull(conf.xrayOutbound(tag(FamilyPolicy.BOTH), noise?.let { noiseTag }), noise)
    }

    companion object {
        fun idFor(prefix: String, serverId: String): String {
            val d = java.security.MessageDigest.getInstance("SHA-256").digest(serverId.toByteArray())
            return prefix + d.take(3).joinToString("") { "%02x".format(it) }
        }

        fun isWgExitRoute(routeId: String?) = false
    }
}

/**
 * WireGuard exit routes for MAE.
 */
object AmzMae {
    const val PER_PROVIDER = 2
    const val US_PER_PROVIDER = 1

    fun routes(context: Context): List<WgExitRoute> = emptyList()

    fun counts(context: Context): Int = 0
}
