package com.najishab.one.openvpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.net.URL

/** adb-only test entry point, protected by DUMP permission and absent from release builds.
 * Credentials are read once from an app-private input file, removed before parsing,
 * and never passed through intents, command arguments, results or logs.
 */
class OpenVpnSmokeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val result = JSONObject()
            val secretFile = File(context.noBackupFilesDir, "openvpn-smoke-input")
            try {
                val repo = OpenVpnRepository.get(context)
                when (intent.getStringExtra("operation")) {
                    "connect" -> {
                        val text = secretFile.inputStream().use { it.readBytes().also { bytes -> require(bytes.size < 8192) }.toString(Charsets.UTF_8) }
                        check(secretFile.delete())
                        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
                        require(lines.size >= 2)
                        check(VpnService.prepare(context) == null) { "VPN_PERMISSION_REQUIRED" }
                        val data = repo.data.value
                        // "location" is a bundled location id (the .ovpn file name); without it the selected one is used.
                        val locationId = intent.getStringExtra("location") ?: data.selectedLocation
                        check(locationId != null && data.locations.any { it.id == locationId }) { "UNKNOWN_LOCATION" }
                        val mode = intent.getStringExtra("mode") ?: OpenVpnLocation.AUTO_MODE
                        // The credentials come from the input file, so this is a manual-credentials connection.
                        OpenVpnRuntime.connectLocation(
                            context = context,
                            locationId = locationId,
                            transportMode = mode,
                            username = lines[0],
                            password = lines[1],
                            manualCredentials = true,
                            splitTcp = data.splitTcp,
                        )
                        result.put("requested", true)
                    }
                    "disconnect" -> OpenVpnRuntime.disconnect(context)
                    "selftest" -> {
                        check(OpenVpnNative.available(context))
                        val profiles = repo.data.value.profiles
                        result.put("profiles", profiles.size)
                        result.put("acceptedByCore", profiles.count { OpenVpnNative.evaluate(it.config).isEmpty() })
                        val locations = repo.data.value.locations
                        result.put("locations", locations.size)
                        // Profile ids are derived from the asset folder and the location name.
                        result.put("stableIds", locations.all { loc -> loc.variants.all { (folder, p) -> p.id == OpenVpnRepository.profileIdFor(folder, loc.id) } })
                        result.put("storageOk", !repo.data.value.storageError)
                    }
                    "traffic" -> {
                        val request = URL("https://www.cloudflare.com/cdn-cgi/trace").openConnection() as javax.net.ssl.HttpsURLConnection
                        try {
                            request.connectTimeout = 3000; request.readTimeout = 3000
                            result.put("httpsStatus", request.responseCode)
                            val lines = request.inputStream.bufferedReader().use { it.readText().take(4096) }.lines()
                            result.put("exitCountry", lines.firstOrNull { it.startsWith("loc=") }?.removePrefix("loc="))
                        } finally { request.disconnect() }
                    }
                }
                val state = OpenVpnRuntime.connection.value
                result.put("phase", state.phase.name).put("error", state.error)
                    .put("received", state.received).put("sent", state.sent)
                    .put("vpnPermission", VpnService.prepare(context) == null)
                val cm = context.getSystemService(ConnectivityManager::class.java)
                result.put("defaultIsVpn", cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true)
            } catch (_: Exception) { result.put("testError", true) }
            finally {
                secretFile.delete()
                File(context.noBackupFilesDir, "openvpn-smoke-result.json").writeText(result.toString())
                pending.finish()
            }
        }
    }
}
