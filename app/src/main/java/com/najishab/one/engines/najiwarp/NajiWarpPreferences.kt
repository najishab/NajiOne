package com.najishab.one.engines.najiwarp

import android.content.Context
import android.content.SharedPreferences

class NajiWarpPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "najiwarp_preferences"
        private const val KEY_ONBOARDING_DONE = "onboarding_done_v2"
        private const val KEY_ACCOUNT_TYPE = "account_type"
        private const val KEY_WARP_LICENSE_KEY = "warp_license_key"
        private const val KEY_ZERO_TRUST_TEAM = "zero_trust_team"
        private const val KEY_ZT_EP_V4 = "zt_endpoint_v4"
        private const val KEY_ZT_EP_V6 = "zt_endpoint_v6"
        private const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
        private const val KEY_DNS_POLICY = "dns_policy"
        private const val KEY_CUSTOM_DOH_URL = "custom_doh_url"
        private const val KEY_ENABLE_ADBLOCK = "enable_adblock"
        private const val KEY_ROUTING_MODE = "routing_mode"
        private const val KEY_TRANSPORT_MODE = "transport_mode"
        private const val KEY_MASQUE_PROFILE = "masque_profile"
        private const val KEY_BBR_ALGO = "bbr_algo"
        private const val KEY_ENDPOINT_MODE = "endpoint_mode"
        private const val KEY_CUSTOM_EP_V4 = "custom_ep_v4"
        private const val KEY_CUSTOM_EP_V6 = "custom_ep_v6"
        private const val KEY_CUSTOM_EP_PORT = "custom_ep_port"
    }

    var isOnboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply()

    var activeProfileId: String
        get() = prefs.getString(KEY_ACTIVE_PROFILE_ID, "8c30b771-9ebd-457a-b67b-bbc74a1ddba6").orEmpty()
        set(value) = prefs.edit().putString(KEY_ACTIVE_PROFILE_ID, value).apply()

    var zeroTrustEndpointV4: String?
        get() = prefs.getString(KEY_ZT_EP_V4, null)
        set(value) = prefs.edit().putString(KEY_ZT_EP_V4, value).apply()

    var zeroTrustEndpointV6: String?
        get() = prefs.getString(KEY_ZT_EP_V6, null)
        set(value) = prefs.edit().putString(KEY_ZT_EP_V6, value).apply()

    fun resetOnboarding() {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, false).apply()
    }

    fun loadConfig(): NajiWarpConfig {
        return NajiWarpConfig(
            accountType = runCatching {
                AccountType.valueOf(prefs.getString(KEY_ACCOUNT_TYPE, AccountType.CONSUMER_FREE.name)!!)
            }.getOrDefault(AccountType.CONSUMER_FREE),
            warpLicenseKey = prefs.getString(KEY_WARP_LICENSE_KEY, "").orEmpty(),
            zeroTrustTeam = prefs.getString(KEY_ZERO_TRUST_TEAM, "").orEmpty(),
            dnsPolicy = runCatching {
                DnsPolicy.valueOf(prefs.getString(KEY_DNS_POLICY, DnsPolicy.STANDARD.name)!!)
            }.getOrDefault(DnsPolicy.STANDARD),
            customDohUrl = prefs.getString(KEY_CUSTOM_DOH_URL, "").orEmpty(),
            enableAdblock = prefs.getBoolean(KEY_ENABLE_ADBLOCK, false),
            routingMode = runCatching {
                RoutingMode.valueOf(prefs.getString(KEY_ROUTING_MODE, RoutingMode.GLOBAL.name)!!)
            }.getOrDefault(RoutingMode.GLOBAL),
            transportMode = runCatching {
                TransportMode.valueOf(prefs.getString(KEY_TRANSPORT_MODE, TransportMode.AUTOMATIC.name)!!)
            }.getOrDefault(TransportMode.AUTOMATIC),
            masqueProfile = runCatching {
                MasqueProfile.valueOf(prefs.getString(KEY_MASQUE_PROFILE, MasqueProfile.CHROME155.name)!!)
            }.getOrDefault(MasqueProfile.CHROME155),
            bbrAlgorithm = runCatching {
                BbrAlgorithm.valueOf(prefs.getString(KEY_BBR_ALGO, BbrAlgorithm.CUBIC.name)!!)
            }.getOrDefault(BbrAlgorithm.CUBIC),
            endpointMode = runCatching {
                EndpointMode.valueOf(prefs.getString(KEY_ENDPOINT_MODE, EndpointMode.AUTOMATIC.name)!!)
            }.getOrDefault(EndpointMode.AUTOMATIC),
            customEndpointV4 = prefs.getString(KEY_CUSTOM_EP_V4, "162.159.198.2").orEmpty(),
            customEndpointV6 = prefs.getString(KEY_CUSTOM_EP_V6, "2606:4700:103::2").orEmpty(),
            customEndpointPort = prefs.getInt(KEY_CUSTOM_EP_PORT, 443)
        )
    }

    fun saveConfig(config: NajiWarpConfig) {
        prefs.edit().apply {
            putString(KEY_ACCOUNT_TYPE, config.accountType.name)
            putString(KEY_WARP_LICENSE_KEY, config.warpLicenseKey)
            putString(KEY_ZERO_TRUST_TEAM, config.zeroTrustTeam)
            putString(KEY_DNS_POLICY, config.dnsPolicy.name)
            putString(KEY_CUSTOM_DOH_URL, config.customDohUrl)
            putBoolean(KEY_ENABLE_ADBLOCK, config.enableAdblock)
            putString(KEY_ROUTING_MODE, config.routingMode.name)
            putString(KEY_TRANSPORT_MODE, config.transportMode.name)
            putString(KEY_MASQUE_PROFILE, config.masqueProfile.name)
            putString(KEY_BBR_ALGO, config.bbrAlgorithm.name)
            putString(KEY_ENDPOINT_MODE, config.endpointMode.name)
            putString(KEY_CUSTOM_EP_V4, config.customEndpointV4)
            putString(KEY_CUSTOM_EP_V6, config.customEndpointV6)
            putInt(KEY_CUSTOM_EP_PORT, config.customEndpointPort)
        }.apply()
    }
}