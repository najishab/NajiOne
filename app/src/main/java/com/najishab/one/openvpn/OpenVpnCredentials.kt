package com.najishab.one.openvpn

import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class OpenVpnAuthCredentials(
    val username: String,
    val password: String,
    val fromManual: Boolean = false,
)

sealed class CredentialsFetchResult {
    data class Ok(val credentials: OpenVpnAuthCredentials) : CredentialsFetchResult()
    data class Failed(val reason: String) : CredentialsFetchResult()
}

object OpenVpnCredentials {
    private const val GITHUB_CREDENTIALS_URL =
        "https://raw.githubusercontent.com/najishab/Aether/refs/heads/main/DATA.txt"

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    fun fetchFromGitHub(): CredentialsFetchResult {
        return try {
            val req = Request.Builder()
                .url(GITHUB_CREDENTIALS_URL)
                .cacheControl(CacheControl.FORCE_NETWORK)
                .header("User-Agent", "NAJIONE-App")
                .build()

            http.newCall(req).execute().use { res ->
                if (!res.isSuccessful) {
                    return CredentialsFetchResult.Failed("HTTP_${res.code}")
                }
                val body = res.body?.string().orEmpty()
                parseCredentials(body)
            }
        } catch (e: Exception) {
            CredentialsFetchResult.Failed(e.javaClass.simpleName)
        }
    }

    internal fun parseCredentials(raw: String): CredentialsFetchResult {
        val lines = raw
            .removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (lines.size < 2) {
            return CredentialsFetchResult.Failed("INVALID_FORMAT")
        }

        val user = lines[0]
        val pass = lines[1]
        if (user.isBlank() || pass.isEmpty() ||
            user.length > 320 || pass.length > 4096 ||
            '\u0000' in user || '\u0000' in pass
        ) {
            return CredentialsFetchResult.Failed("INVALID_CREDENTIALS")
        }

        return CredentialsFetchResult.Ok(OpenVpnAuthCredentials(user, pass, fromManual = false))
    }
}