package com.najishab.one.engines.najiwarp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.najishab.one.MyVpnService
import com.najishab.one.ui.tunnel.TunnelController
import com.najishab.one.utils.SafeStart
import io.github.georgexie2333.usque.NativeEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

object NajiWarpZtCallbackHolder {
    val pendingCallbackUri = MutableStateFlow<String?>(null)
}

object NajiWarpEngine {
    private const val TAG = "NajiWarpEngine"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _phase = MutableStateFlow<NajiWarpPhase>(NajiWarpPhase.IDLE)
    val phase: StateFlow<NajiWarpPhase> = _phase.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    val isRunning: Boolean
        get() = _phase.value is NajiWarpPhase.PREPARING ||
            _phase.value is NajiWarpPhase.CONNECTING ||
            _phase.value is NajiWarpPhase.HANDSHAKE ||
            _phase.value is NajiWarpPhase.CONNECTED

    internal fun updatePhase(next: NajiWarpPhase) { _phase.value = next }

    internal fun appendLog(msg: String) {
        Log.i(TAG, msg)
        _logs.value = (_logs.value + msg).takeLast(200)
    }

    internal fun onServiceDestroyed() {
        if (_phase.value !is NajiWarpPhase.FAILED) _phase.value = NajiWarpPhase.IDLE
    }

    private fun configStorePath(context: Context): String {
        val dir = File(context.applicationContext.noBackupFilesDir, "usque_config")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "profiles-v2.json").absolutePath
    }

    suspend fun setupAccount(
        context: Context,
        config: NajiWarpConfig,
        zeroTrustCallbackUri: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        appendLog("در حال ثبت هویت در شبکه Cloudflare WARP...")
        try {
            if (!NativeEngine.isLibraryLoaded()) {
                return@withContext Result.failure(Exception("کتابخانه libusque_android.so بارگذاری نشد."))
            }

            val locale = Locale.getDefault().toString().ifBlank { "en_US" }.take(32)
            val prefs = NajiWarpPreferences(context)
            var ztV4: String? = null
            var ztV6: String? = null

            val secretBytes: ByteArray = when (config.accountType) {
                AccountType.CONSUMER_FREE -> {
                    NativeEngine.nativeRegisterConsumerWarp(locale)
                        ?: return@withContext Result.failure(Exception("ثبت‌نام رایگان WARP پاسخی برنگرداند."))
                }
                AccountType.WARP_PLUS -> {
                    val key = config.warpLicenseKey.trim()
                    if (key.isEmpty()) {
                        return@withContext Result.failure(Exception("کلید لایسنس WARP+ را وارد کنید."))
                    }
                    NativeEngine.nativeRegisterConsumerWarpWithLicense(locale, key)
                        ?: return@withContext Result.failure(Exception("کلید لایسنس WARP+ تایید نشد."))
                }
                AccountType.ZERO_TRUST -> {
                    val team = config.zeroTrustTeam.trim().lowercase(Locale.ROOT)
                    val cb = zeroTrustCallbackUri.trim()
                    if (team.isEmpty() || cb.isEmpty()) {
                        return@withContext Result.failure(Exception("نام تیم و لینک بازگشتی Zero Trust الزامی است."))
                    }
                    val envBytes = NativeEngine.nativeRegisterZeroTrustWarp(locale, team, cb)
                        ?: return@withContext Result.failure(Exception("ثبت‌نام Zero Trust انجام نشد."))
                    try {
                        val envelope = JSONObject(envBytes.toString(Charsets.UTF_8))
                        ztV4 = envelope.optString("endpoint_v4").takeIf { it.isNotBlank() }
                        ztV6 = envelope.optString("endpoint_v6").takeIf { it.isNotBlank() }
                        envelope.getString("warp_secret").toByteArray(Charsets.UTF_8)
                    } finally {
                        envBytes.fill(0)
                    }
                }
            }

            try {
                val valid = NativeEngine.nativeValidateWarpSecret(secretBytes)
                if (valid != NativeEngine.OK) {
                    return@withContext Result.failure(Exception("هویت دریافتی معتبر نیست ($valid)"))
                }
                NajiWarpIdentityVault.saveSecret(context, secretBytes)
            } finally {
                secretBytes.fill(0)
            }

            val catalogStr = NativeEngine.nativeApplyProfileCommand(
                configStorePath(context),
                "{\"command\":\"list_profiles\"}"
            )
            if (!catalogStr.isNullOrBlank()) {
                val catalog = JSONObject(catalogStr)
                val activeId = catalog.optString("active_profile_id")
                if (activeId.isNotBlank()) prefs.activeProfileId = activeId
            }
            prefs.zeroTrustEndpointV4 = ztV4
            prefs.zeroTrustEndpointV6 = ztV6
            prefs.saveConfig(config)
            appendLog("هویت WARP با موفقیت ساخته و ذخیره شد.")
            Result.success(Unit)
        } catch (e: Exception) {
            val msg = mapNativeError(e.message)
            appendLog("خطا در ثبت هویت: $msg")
            Result.failure(Exception(msg))
        }
    }

    private fun mapNativeError(raw: String?): String = when {
        raw == null -> "خطا در ارتباط با شبکه Cloudflare"
        raw.contains("INVALID_LICENSE_KEY") -> "کلید لایسنس WARP+ نامعتبر است."
        raw.contains("USQUE_CONSUMER_NETWORK") -> "عدم دسترسی به سرورهای Cloudflare (اینترنت را بررسی کنید)."
        raw.contains("USQUE_ZT_TEAM_INVALID") -> "نام تیم Zero Trust نامعتبر است."
        raw.contains("USQUE_ZT_CALLBACK_INVALID") -> "لینک بازگشتی Callback نامعتبر یا منقضی شده است."
        raw.contains("USQUE_ZT_LOGIN_EXPIRED") -> "مهلت ورود Zero Trust تمام شده است."
        else -> raw
    }

    private fun buildProfileJson(context: Context, config: NajiWarpConfig): String {
        val catalogStr = NativeEngine.nativeApplyProfileCommand(
            configStorePath(context),
            "{\"command\":\"list_profiles\"}"
        ) ?: error("Native profile store unavailable")
        val catalog = JSONObject(catalogStr)
        val profile = catalog.getJSONArray("profiles").getJSONObject(0)
        val prefs = NajiWarpPreferences(context)

        val isTunnel = config.routingMode == RoutingMode.GLOBAL
        profile.put("mode", if (isTunnel) "vpn" else "socks5")
        profile.put("frontends", JSONObject().apply {
            put("tunnel", isTunnel)
            put("socks5", !isTunnel)
            put("http", !isTunnel)
        })
        profile.put("transport", config.transportMode.wireValue)
        profile.put("congestion_control", config.bbrAlgorithm.wireValue)
        profile.put("tls_fingerprint", config.masqueProfile.wireValue)
        profile.put("dns_mode", "tunnel")
        profile.put("dns_v4", config.dnsPolicy.ipv4)
        profile.put("dns_v6", config.dnsPolicy.ipv6)

        val routing = profile.optJSONObject("routing") ?: JSONObject().put("rules", JSONArray())
        routing.put("ads_enabled", config.enableAdblock)
        profile.put("routing", routing)

        if (config.dnsPolicy == DnsPolicy.CUSTOM_DOH && config.customDohUrl.isNotBlank()) {
            runCatching {
                val uri = Uri.parse(config.customDohUrl.trim())
                val host = uri.host
                if (!host.isNullOrBlank()) {
                    val path = uri.encodedPath?.takeIf { it.isNotBlank() } ?: "/dns-query"
                    val port = if (uri.port in 1..65535) uri.port else 443
                    profile.put("warp_dns", JSONObject().apply {
                        put("mode", "doh")
                        put("server_name", host)
                        put("doh_path", path)
                        put("bootstrap_ips", JSONArray().put("1.1.1.1").put("8.8.8.8"))
                        put("port", port)
                    })
                }
            }
        } else {
            profile.put("warp_dns", JSONObject().apply {
                put("mode", "plain")
                put("server_name", "")
                put("doh_path", "")
                put("bootstrap_ips", JSONArray())
                put("port", 0)
            })
        }

        if (config.accountType == AccountType.ZERO_TRUST &&
            !prefs.zeroTrustEndpointV4.isNullOrBlank() &&
            !prefs.zeroTrustEndpointV6.isNullOrBlank()
        ) {
            profile.put("endpoint_selection", "custom")
            profile.put("endpoint_v4", prefs.zeroTrustEndpointV4)
            profile.put("endpoint_v6", prefs.zeroTrustEndpointV6)
        } else if (config.endpointMode == EndpointMode.CUSTOM) {
            profile.put("endpoint_selection", "custom")
            profile.put("endpoint_v4", config.customEndpointV4.trim().ifBlank { "162.159.198.2" })
            profile.put("endpoint_v6", config.customEndpointV6.trim().ifBlank { "2606:4700:103::2" })
            profile.put("endpoint_port", config.customEndpointPort.coerceIn(1, 65535))
        } else {
            profile.put("endpoint_selection", "automatic")
        }

        if (!isTunnel) {
            val proxy = profile.optJSONObject("proxy") ?: JSONObject()
            proxy.put("socks_ipv4", "127.0.0.1")
            proxy.put("socks_ipv6", "::1")
            proxy.put("socks_port", config.socksPort)
            proxy.put("http_ipv4", "127.0.0.1")
            proxy.put("http_ipv6", "::1")
            proxy.put("http_port", config.httpPort)
            proxy.put("socks5_listeners", JSONArray().put("127.0.0.1:${config.socksPort}"))
            proxy.put("http_listeners", JSONArray().put("127.0.0.1:${config.httpPort}"))
            profile.put("proxy", proxy)
        }

        return profile.toString()
    }

    fun start(context: Context, config: NajiWarpConfig) {
        if (isRunning) return
        val app = context.applicationContext
        _phase.value = NajiWarpPhase.PREPARING
        appendLog("شروع اتصال NajiWarp...")

        scope.launch {
            try {
                if (!NajiWarpIdentityVault.hasIdentity(app)) {
                    _phase.value = NajiWarpPhase.FAILED("اکانت WARP تنظیم نشده است.")
                    return@launch
                }

                if (config.routingMode == RoutingMode.GLOBAL) {
                    var releasedAny = false
                    if (MyVpnService.isRunning) {
                        SafeStart.service(app, Intent(app, MyVpnService::class.java).setAction("STOP"))
                        releasedAny = true
                    }
                    if (TunnelController.state.value.isBusy) {
                        TunnelController.disconnect(app)
                        releasedAny = true
                    }
                    if (releasedAny) delay(450)
                }

                val profileJson = buildProfileJson(app, config)
                val startIntent = Intent(app, NajiWarpVpnService::class.java).apply {
                    action = NajiWarpVpnService.ACTION_CONNECT
                    putExtra(NajiWarpVpnService.EXTRA_PROFILE_JSON, profileJson)
                    putExtra(NajiWarpVpnService.EXTRA_ACCOUNT_TYPE, config.accountType.name)
                }
                SafeStart.service(app, startIntent)
            } catch (e: Exception) {
                appendLog("خطا در شروع اتصال: ${e.message}")
                _phase.value = NajiWarpPhase.FAILED(e.message ?: "Start failed")
            }
        }
    }

    fun stop(context: Context? = null) {
        _phase.value = NajiWarpPhase.DISCONNECTING
        val app = context?.applicationContext
        if (app != null) {
            SafeStart.service(app, Intent(app, NajiWarpVpnService::class.java).setAction(NajiWarpVpnService.ACTION_DISCONNECT))
        } else {
            NativeEngine.nativeCancel()
            scope.launch {
                runCatching { NativeEngine.nativeStopConfirmed() }
                _phase.value = NajiWarpPhase.IDLE
            }
        }
    }

    fun resetAccount(context: Context) {
        stop(context)
        NajiWarpIdentityVault.clear(context)
        NajiWarpPreferences(context).resetOnboarding()
        _phase.value = NajiWarpPhase.IDLE
    }
}