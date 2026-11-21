package com.najishab.one.engines.najiwarp

import androidx.annotation.StringRes
import com.najishab.one.R

data class NajiWarpConfig(
    val accountType: AccountType = AccountType.CONSUMER_FREE,
    val warpLicenseKey: String = "",
    val zeroTrustTeam: String = "",
    val dnsPolicy: DnsPolicy = DnsPolicy.STANDARD,
    val customDohUrl: String = "",
    val enableAdblock: Boolean = false,
    val routingMode: RoutingMode = RoutingMode.GLOBAL,
    val transportMode: TransportMode = TransportMode.AUTOMATIC,
    val masqueProfile: MasqueProfile = MasqueProfile.CHROME155,
    val bbrAlgorithm: BbrAlgorithm = BbrAlgorithm.CUBIC,
    val endpointMode: EndpointMode = EndpointMode.AUTOMATIC,
    val customEndpointV4: String = "162.159.198.2",
    val customEndpointV6: String = "2606:4700:103::2",
    val customEndpointPort: Int = 443,
    val socksPort: Int = 20890,
    val httpPort: Int = 20891
)

enum class AccountType(@StringRes val titleRes: Int) {
    CONSUMER_FREE(R.string.nw_account_free),
    WARP_PLUS(R.string.nw_account_plus),
    ZERO_TRUST(R.string.nw_account_zt)
}

enum class DnsPolicy(@StringRes val titleRes: Int, val ipv4: String, val ipv6: String) {
    STANDARD(R.string.nw_dns_standard, "1.1.1.1", "2606:4700:4700::1111"),
    MALWARE_BLOCKING(R.string.nw_dns_malware, "1.1.1.2", "2606:4700:4700::1112"),
    FAMILY_PROTECTION(R.string.nw_dns_family, "1.1.1.3", "2606:4700:4700::1113"),
    CUSTOM_DOH(R.string.nw_dns_custom, "1.1.1.1", "2606:4700:4700::1111")
}

enum class RoutingMode(@StringRes val titleRes: Int) {
    GLOBAL(R.string.nw_routing_global),
    PROXY_ONLY(R.string.nw_routing_proxy)
}

enum class TransportMode(val wireValue: String, @StringRes val titleRes: Int) {
    AUTOMATIC("automatic", R.string.nw_transport_auto),
    HTTP3("http3", R.string.nw_transport_h3),
    HTTP2("http2", R.string.nw_transport_h2)
}

enum class MasqueProfile(val wireValue: String, @StringRes val titleRes: Int) {
    CHROME155("chrome155", R.string.nw_tls_chrome),
    DEFAULT("default", R.string.nw_tls_default)
}

enum class BbrAlgorithm(val wireValue: String, @StringRes val titleRes: Int) {
    CUBIC("cubic", R.string.nw_cc_cubic),
    BBR("bbr", R.string.nw_cc_bbr),
    BBR3("bbr3", R.string.nw_cc_bbr3),
    RENO("reno", R.string.nw_cc_reno)
}

enum class EndpointMode(val wireValue: String, @StringRes val titleRes: Int) {
    AUTOMATIC("automatic", R.string.nw_endpoint_auto),
    CUSTOM("custom", R.string.nw_endpoint_custom)
}

sealed class NajiWarpPhase {
    object IDLE : NajiWarpPhase()
    object PREPARING : NajiWarpPhase()
    object CONNECTING : NajiWarpPhase()
    object HANDSHAKE : NajiWarpPhase()
    data class CONNECTED(
        val transport: String? = null,
        val colo: String? = null,
        val exitIp: String? = null,
        val pingMs: Long = 0L,
        val rxBytes: Long = 0L,
        val txBytes: Long = 0L,
        val speedRx: Long = 0L,
        val speedTx: Long = 0L,
        val quotaRemaining: String? = null
    ) : NajiWarpPhase()
    object DISCONNECTING : NajiWarpPhase()
    data class FAILED(val error: String) : NajiWarpPhase()
}