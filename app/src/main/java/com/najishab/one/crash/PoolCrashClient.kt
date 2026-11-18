package com.najishab.one.crash

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The enrolled, signed channel to the NAJIONE worker that crash reports and diagnostics fall back
 * to when the dedicated crash collector does not take them.
 *
 * It keeps the same enrolment (preferences file and keys) the app has always used, so installs
 * that are already enrolled stay enrolled.
 */
object PoolCrashClient {

    private const val TAG = "NajiPool"
    private const val PREFS = "naji_pool"
    private const val KEY_ID = "install_id"
    private const val KEY_SECRET = "install_secret"
    private const val KEY_ENDPOINT = "endpoint"

    /**
     * Where the worker lives. A stored default rather than a constant so a second worker can be
     * swapped in without an app update.
     */
    private const val DEFAULT_ENDPOINT = "https://pool.redacted.invalid"

    /**
     * DNS that does not go through the operator's resolver.
     *
     * The operator poisons the whole `*.workers.dev` zone, so the system resolver cannot be
     * trusted to reach our own worker. [com.najishab.one.utils.DomainPreResolver] already solves
     * exactly this for config domains (DoH addressed to a resolver's IP); the system resolver is
     * kept as the fallback for networks where it works.
     */
    private val dohDns = object : okhttp3.Dns {
        override fun lookup(hostname: String): List<java.net.InetAddress> {
            val viaDoh = runCatching {
                kotlinx.coroutines.runBlocking {
                    com.najishab.one.utils.DomainPreResolver.resolve(hostname)
                }
            }.getOrDefault(emptyList())
            if (viaDoh.isNotEmpty()) {
                val addrs = viaDoh.mapNotNull {
                    runCatching { java.net.InetAddress.getByName(it) }.getOrNull()
                }
                if (addrs.isNotEmpty()) return addrs
            }
            return okhttp3.Dns.SYSTEM.lookup(hostname)
        }
    }

    private val client = OkHttpClient.Builder().protocols(com.najishab.one.engines.cloud.WorkerRoute.HTTP1).socketFactory(com.najishab.one.engines.cloud.WorkerRoute.cfSocketFactory())
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .dns(com.najishab.one.engines.cloud.WorkerRoute.dns(dohDns))
        .retryOnConnectionFailure(true)
        .build()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun endpoint(context: Context): String =
        prefs(context).getString(KEY_ENDPOINT, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_ENDPOINT

    // ---- enrolment -------------------------------------------------------------------------

    private data class Identity(val id: String, val secret: String)

    /**
     * The install's identity with the worker: a random value of our own rather than ANDROID_ID or
     * anything else that identifies the device.
     */
    private suspend fun identity(context: Context): Identity? = withContext(Dispatchers.IO) {
        val p = prefs(context)
        val id = p.getString(KEY_ID, null)
        val secret = p.getString(KEY_SECRET, null)
        if (!id.isNullOrBlank() && !secret.isNullOrBlank()) return@withContext Identity(id, secret)

        try {
            val req = Request.Builder()
                .url(endpoint(context) + "/enroll")
                .post(ByteArray(0).toRequestBody(null))
                .build()
            client.newCall(req).execute().use { res ->
                val body = res.body?.string() ?: return@withContext null
                if (!res.isSuccessful) {
                    Log.w(TAG, "enroll failed: HTTP ${res.code}")
                    return@withContext null
                }
                val json = JSONObject(body)
                val newId = json.optString("id")
                val newSecret = json.optString("secret")
                if (newId.isBlank() || newSecret.isBlank()) return@withContext null
                p.edit().putString(KEY_ID, newId).putString(KEY_SECRET, newSecret).apply()
                Identity(newId, newSecret)
            }
        } catch (e: Exception) {
            Log.w(TAG, "enroll failed: ${e.message}")
            null
        }
    }

    private fun sign(secret: String, message: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return mac.doFinal(message.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun signed(
        context: Context,
        who: Identity,
        path: String,
        body: String?,
    ): Request.Builder {
        val ts = System.currentTimeMillis().toString()
        val sig = sign(who.secret, "${who.id}.$ts.${body ?: ""}")
        return Request.Builder()
            .url(endpoint(context) + path)
            .header("X-Install", who.id)
            .header("X-Ts", ts)
            .header("X-Sig", sig)
    }

    private fun appVersion(context: Context): String = runCatching {
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        "${pi.versionName} (${pi.longVersionCode})"
    }.getOrDefault("?")

    // ---- reports ---------------------------------------------------------------------------

    /**
     * Send one crash report. [summary] is the crash's identity -- exception class plus the top
     * frames -- and the server groups by a hash of it.
     *
     * Returns true only when the server accepted it, because the caller shows the user a result
     * and "sent" has to mean sent.
     */
    suspend fun reportCrash(
        context: Context,
        summary: String,
        body: String,
    ): Boolean = post(context, summary, body, "crash report")

    /**
     * Send a diagnostic that is not a crash: something the app can see is wrong but cannot fix.
     * The same signed `/crash` endpoint; the worker groups reports by a hash of the summary.
     *
     * [summary] must name the failure class and nothing per-user. [body] must already be redacted;
     * nothing here inspects it.
     */
    suspend fun reportDiagnostic(
        context: Context,
        summary: String,
        body: String,
    ): Boolean = post(context, summary, body, "diagnostic")

    private suspend fun post(
        context: Context,
        summary: String,
        body: String,
        what: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val who = identity(context) ?: return@withContext false
        try {
            val payload = JSONObject().apply {
                put("summary", summary.take(300))
                put("body", body.take(20_000))
                put("app", appVersion(context))
                put("device", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                put("android", android.os.Build.VERSION.RELEASE)
            }.toString()
            val req = signed(context, who, "/crash", payload)
                .post(payload.toRequestBody("application/json".toMediaTypeOrNull()))
                .build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) Log.w(TAG, "$what failed: HTTP ${res.code}")
                res.isSuccessful
            }
        } catch (e: Exception) {
            Log.w(TAG, "$what failed: ${e.message}")
            false
        }
    }
}
