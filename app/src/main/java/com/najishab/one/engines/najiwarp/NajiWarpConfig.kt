package com.najishab.one.engines.najiwarp

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

enum class AccountType(val title: String) {
    CONSUMER_FREE("رایگان (Cloudflare WARP)"),
    WARP_PLUS("وارپ پلاس (WARP+ License)"),
    ZERO_TRUST("کلودفلر سازمانی (Zero Trust Team)")
}

enum class DnsPolicy(val title: String, val ipv4: String, val ipv6: String) {
    STANDARD("استاندارد (1.1.1.1)", "1.1.1.1", "2606:4700:4700::1111"),
    MALWARE_BLOCKING("محافظت بدافزار (1.1.1.2)", "1.1.1.2", "2606:4700:4700::1112"),
    FAMILY_PROTECTION("محافظت خانواده (1.1.1.3)", "1.1.1.3", "2606:4700:4700::1113"),
    CUSTOM_DOH("سفارشی (DNS over HTTPS)", "1.1.1.1", "2606:4700:4700::1111")
}

enum class RoutingMode(val title: String) {
    GLOBAL("تمام ترافیک دستگاه (VPN Full Tunnel)"),
    PROXY_ONLY("فقط پروکسی محلی (SOCKS5 / HTTP)")
}

enum class TransportMode(val wireValue: String, val title: String) {
    AUTOMATIC("automatic", "خودکار (HTTP/3 + Fallback HTTP/2)"),
    HTTP3("http3", "فقط HTTP/3 (QUIC)"),
    HTTP2("http2", "فقط HTTP/2 (TCP/TLS)")
}

enum class MasqueProfile(val wireValue: String, val title: String) {
    CHROME155("chrome155", "شبیه‌سازی Chrome 155 (پیش‌فرض)"),
    DEFAULT("default", "پروفایل استاندارد (Default TLS)")
}

enum class BbrAlgorithm(val wireValue: String, val title: String) {
    CUBIC("cubic", "CUBIC (پیش‌فرض استاندارد)"),
    BBR("bbr", "BBRv2 (بهینه‌شده)"),
    BBR3("bbr3", "BBRv3 (سریع)"),
    RENO("reno", "RENO (کلاسیک)")
}

enum class EndpointMode(val wireValue: String, val title: String) {
    AUTOMATIC("automatic", "انتخاب خودکار بهترین اندپوینت کلودفلر"),
    CUSTOM("custom", "آی‌پی و پورت دستی (Custom Endpoint)")
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