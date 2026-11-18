package com.najishab.one.openvpn

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.najishab.one.utils.GameSessionOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout

object OpenVpnRuntime {
    internal val mutable = MutableStateFlow(OpenVpnConnection())

    /** The "account" marker for profiles carrying inline/external credentials. */
    const val SELF = "@profile"
    val connection = mutable.asStateFlow()

    /** Connects a logical location with GitHub or manual credentials and auto-fallback across modes. */
    fun connectLocation(
        context: Context,
        locationId: String,
        transportMode: String,
        username: String,
        password: String,
        manualCredentials: Boolean = false,
        splitTcp: Boolean = true,
        blockIpv6: Boolean = true,
        owner: String? = null,
    ) {
        ContextCompat.startForegroundService(
            context,
            Intent(context, OpenVpnService::class.java)
                .setAction(OpenVpnService.CONNECT)
                .putExtra(OpenVpnService.EXTRA_LOCATION_ID, locationId)
                .putExtra(OpenVpnService.EXTRA_TRANSPORT_MODE, transportMode)
                .putExtra(OpenVpnService.EXTRA_USERNAME, username)
                .putExtra(OpenVpnService.EXTRA_PASSWORD, password)
                .putExtra(OpenVpnService.EXTRA_MANUAL_CREDS, manualCredentials)
                .putExtra(OpenVpnService.EXTRA_SPLIT_TCP, splitTcp)
                .putExtra(OpenVpnService.EXTRA_BLOCK_IPV6, blockIpv6)
                .putExtra(GameSessionOwner.EXTRA, owner),
        )
    }

    /** Kept for LastEngineReplay and WidgetConnect compatibility. */
    fun connect(context: Context, profile: String, account: String, owner: String? = null) {
        val splitTcp = runCatching { OpenVpnRepository.get(context).data.value.splitTcp }.getOrDefault(true)
        val blockIpv6 = runCatching { OpenVpnRepository.get(context).data.value.blockIpv6 }.getOrDefault(true)
        ContextCompat.startForegroundService(
            context,
            Intent(context, OpenVpnService::class.java)
                .setAction(OpenVpnService.CONNECT)
                .putExtra("profile", profile)
                .putExtra("account", account)
                .putExtra(OpenVpnService.EXTRA_SPLIT_TCP, splitTcp)
                .putExtra(OpenVpnService.EXTRA_BLOCK_IPV6, blockIpv6)
                .putExtra(GameSessionOwner.EXTRA, owner),
        )
    }

    fun disconnect(context: Context, owner: String? = null) {
        if (owner != null || connection.value.active) {
            com.najishab.one.utils.SafeStart.service(
                context,
                Intent(context, OpenVpnService::class.java)
                    .setAction(OpenVpnService.DISCONNECT)
                    .putExtra(GameSessionOwner.EXPECTED, owner),
            )
        }
    }

    suspend fun release(context: Context, owner: String? = null) {
        if (owner == null && !connection.value.active) return
        disconnect(context, owner)
        withTimeout(10_000) { while (connection.value.active) delay(50) }
    }
}