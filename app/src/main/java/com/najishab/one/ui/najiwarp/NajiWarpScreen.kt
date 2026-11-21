package com.najishab.one.ui.najiwarp

import com.najishab.one.R
import com.najishab.one.utils.S

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.najishab.one.engines.najiwarp.BbrAlgorithm
import com.najishab.one.engines.najiwarp.DnsPolicy
import com.najishab.one.engines.najiwarp.EndpointMode
import com.najishab.one.engines.najiwarp.MasqueProfile
import com.najishab.one.engines.najiwarp.NajiWarpConfig
import com.najishab.one.engines.najiwarp.NajiWarpEngine
import com.najishab.one.engines.najiwarp.NajiWarpIdentityVault
import com.najishab.one.engines.najiwarp.NajiWarpPhase
import com.najishab.one.engines.najiwarp.NajiWarpPreferences
import com.najishab.one.engines.najiwarp.RoutingMode
import com.najishab.one.engines.najiwarp.TransportMode
import com.najishab.one.ui.settings.Ios
import com.najishab.one.ui.settings.IosOption
import com.najishab.one.ui.settings.IosPickerScreen
import com.najishab.one.ui.settings.IosScreen
import com.najishab.one.ui.settings.IosTextScreen
import com.najishab.one.ui.settings.SettingsGroup
import com.najishab.one.ui.settings.SettingsRow
import com.najishab.one.ui.settings.SettingsSectionHeader
import com.najishab.one.ui.settings.SettingsToggle

@Composable
fun NajiWarpScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { NajiWarpPreferences(context) }
    var isOnboardingDone by remember {
        mutableStateOf(prefs.isOnboardingCompleted && NajiWarpIdentityVault.hasIdentity(context))
    }

    if (!isOnboardingDone) {
        NajiWarpWizardScreen(
            onFinished = { isOnboardingDone = true },
            onCancel = onBack
        )
        return
    }

    var config by remember { mutableStateOf(prefs.loadConfig()) }
    var subPage by remember { mutableStateOf<String?>(null) }
    var showLogs by remember { mutableStateOf(false) }

    val phase by NajiWarpEngine.phase.collectAsState()
    val logs by NajiWarpEngine.logs.collectAsState()

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            NajiWarpEngine.start(context, config)
        }
    }

    fun startWithPermissionCheck() {
        if (config.routingMode == RoutingMode.GLOBAL) {
            val prep = VpnService.prepare(context)
            if (prep != null) {
                vpnPermissionLauncher.launch(prep)
                return
            }
        }
        NajiWarpEngine.start(context, config)
    }

    fun updateConfig(next: NajiWarpConfig) {
        config = next
        prefs.saveConfig(next)
    }

    if (subPage != null) {
        BackHandler { subPage = null }
        when (subPage) {
            "transport" -> IosPickerScreen(
                title = S(R.string.nw_transport_protocol),
                options = TransportMode.entries.map { IosOption(it.name, S(it.titleRes)) },
                selectedKey = config.transportMode.name,
                onSelect = { k -> updateConfig(config.copy(transportMode = TransportMode.valueOf(k))) },
                onBack = { subPage = null }
            )
            "fingerprint" -> IosPickerScreen(
                title = S(R.string.nw_tls_profile),
                options = MasqueProfile.entries.map { IosOption(it.name, S(it.titleRes)) },
                selectedKey = config.masqueProfile.name,
                onSelect = { k -> updateConfig(config.copy(masqueProfile = MasqueProfile.valueOf(k))) },
                onBack = { subPage = null }
            )
            "bbr" -> IosPickerScreen(
                title = S(R.string.nw_http3_cc),
                options = BbrAlgorithm.entries.map { IosOption(it.name, S(it.titleRes)) },
                selectedKey = config.bbrAlgorithm.name,
                onSelect = { k -> updateConfig(config.copy(bbrAlgorithm = BbrAlgorithm.valueOf(k))) },
                onBack = { subPage = null }
            )
            "dns" -> IosPickerScreen(
                title = S(R.string.nw_dns_policy),
                options = DnsPolicy.entries.map { IosOption(it.name, S(it.titleRes)) },
                selectedKey = config.dnsPolicy.name,
                onSelect = { k -> updateConfig(config.copy(dnsPolicy = DnsPolicy.valueOf(k))) },
                onBack = { subPage = null }
            )
            "routing" -> IosPickerScreen(
                title = S(R.string.nw_routing_mode),
                options = RoutingMode.entries.map { IosOption(it.name, S(it.titleRes)) },
                selectedKey = config.routingMode.name,
                onSelect = { k -> updateConfig(config.copy(routingMode = RoutingMode.valueOf(k))) },
                onBack = { subPage = null }
            )
            "endpoint_mode" -> IosPickerScreen(
                title = S(R.string.nw_endpoint_selection),
                options = EndpointMode.entries.map { IosOption(it.name, S(it.titleRes)) },
                selectedKey = config.endpointMode.name,
                onSelect = { k -> updateConfig(config.copy(endpointMode = EndpointMode.valueOf(k))) },
                onBack = { subPage = null }
            )
            "endpoint_v4" -> IosTextScreen(
                title = S(R.string.nw_manual_ipv4),
                value = config.customEndpointV4,
                onValueChange = { updateConfig(config.copy(customEndpointV4 = it)) },
                placeholder = "162.159.198.2",
                onBack = { subPage = null }
            )
            "endpoint_port" -> IosTextScreen(
                title = S(R.string.nw_manual_port),
                value = config.customEndpointPort.toString(),
                numeric = true,
                onValueChange = { raw ->
                    raw.toIntOrNull()?.takeIf { it in 1..65535 }?.let {
                        updateConfig(config.copy(customEndpointPort = it))
                    }
                },
                placeholder = "443",
                onBack = { subPage = null }
            )
        }
        return
    }

    val accentColor = Color(0xFFFFB26B)
    val isConnected = phase is NajiWarpPhase.CONNECTED
    val isConnecting = phase is NajiWarpPhase.CONNECTING ||
        phase is NajiWarpPhase.PREPARING ||
        phase is NajiWarpPhase.HANDSHAKE

    val buttonColor by animateColorAsState(
        targetValue = when {
            isConnected -> Color(0xFF34C759)
            isConnecting -> Color(0xFFFF9500)
            phase is NajiWarpPhase.FAILED -> Color(0xFFFF453A)
            else -> Color(0xFF2C2C2E)
        },
        label = "NajiWarpButtonAnim"
    )

    BackHandler { onBack() }

    IosScreen(title = "NajiWarp", onBack = onBack) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        when {
                            isConnected -> Color(0xFF34C759).copy(alpha = 0.16f)
                            isConnecting -> accentColor.copy(alpha = 0.16f)
                            phase is NajiWarpPhase.FAILED -> Color(0xFFFF453A).copy(alpha = 0.16f)
                            else -> Color.White.copy(alpha = 0.08f)
                        }
                    )
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isConnected -> Color(0xFF34C759)
                                    isConnecting -> accentColor
                                    phase is NajiWarpPhase.FAILED -> Color(0xFFFF453A)
                                    else -> Color.Gray
                                }
                            )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = when (val p = phase) {
                            is NajiWarpPhase.IDLE -> S(R.string.nw_ready)
                            is NajiWarpPhase.PREPARING -> S(R.string.nw_preparing)
                            is NajiWarpPhase.CONNECTING -> S(R.string.nw_connecting_masque)
                            is NajiWarpPhase.HANDSHAKE -> S(R.string.nw_handshaking)
                            is NajiWarpPhase.CONNECTED -> S(R.string.nw_connected_cloudflare) + " (${p.transport ?: "H3"})"
                            is NajiWarpPhase.DISCONNECTING -> S(R.string.nw_disconnecting)
                            is NajiWarpPhase.FAILED -> S(R.string.nw_failed)
                        },
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.height(26.dp))

            Box(
                modifier = Modifier
                    .size(136.dp)
                    .clip(CircleShape)
                    .background(buttonColor.copy(alpha = 0.2f))
                    .clickable {
                        if (isConnected || isConnecting) {
                            NajiWarpEngine.stop(context)
                        } else {
                            startWithPermissionCheck()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(108.dp)
                        .clip(CircleShape)
                        .background(buttonColor),
                    contentAlignment = Alignment.Center
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(42.dp), strokeWidth = 3.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Connect",
                            tint = Color.White,
                            modifier = Modifier.size(50.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = if (isConnected || isConnecting) S(R.string.nw_tap_disconnect) else S(R.string.nw_tap_connect),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp
            )

            if (phase is NajiWarpPhase.FAILED) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = (phase as NajiWarpPhase.FAILED).error,
                    color = Color(0xFFFF453A),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            if (phase is NajiWarpPhase.CONNECTED) {
                val conn = phase as NajiWarpPhase.CONNECTED
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                        .padding(vertical = 14.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MetricBadge(S(R.string.nw_location_ip), "${conn.colo ?: "CF"}\n${conn.exitIp ?: "-"}")
                    MetricBadge(S(R.string.nw_ping), if (conn.pingMs > 0) "${conn.pingMs} ms" else "...")
                    MetricBadge(S(R.string.nw_traffic), "↓ ${formatBytes(conn.rxBytes)}\n↑ ${formatBytes(conn.txBytes)}")
                }
            }

            Spacer(Modifier.height(14.dp))

            SettingsSectionHeader(text = S(R.string.nw_settings))
            SettingsGroup {
                SettingsRow(
                    title = S(R.string.nw_routing_mode),
                    value = if (config.routingMode == RoutingMode.GLOBAL) "VPN" else "Proxy",
                    icon = Icons.Default.Router,
                    tint = Ios.Blue,
                    onClick = { subPage = "routing" }
                )
                Separator()
                SettingsRow(
                    title = S(R.string.nw_transport_protocol),
                    value = config.transportMode.wireValue,
                    icon = Icons.Default.Speed,
                    tint = accentColor,
                    onClick = { subPage = "transport" }
                )
                Separator()
                SettingsRow(
                    title = S(R.string.nw_tls_profile),
                    value = config.masqueProfile.wireValue,
                    icon = Icons.Default.Public,
                    tint = Ios.Green,
                    onClick = { subPage = "fingerprint" }
                )
                Separator()
                SettingsRow(
                    title = S(R.string.nw_cc_algorithm),
                    value = config.bbrAlgorithm.wireValue,
                    icon = Icons.Default.Bolt,
                    tint = Ios.Indigo,
                    onClick = { subPage = "bbr" }
                )
                Separator()
                SettingsRow(
                    title = S(R.string.nw_endpoint),
                    value = config.endpointMode.wireValue,
                    icon = Icons.Default.Tune,
                    tint = Ios.Teal,
                    onClick = { subPage = "endpoint_mode" }
                )
                if (config.endpointMode == EndpointMode.CUSTOM) {
                    Separator()
                    SettingsRow(
                        title = S(R.string.nw_manual_ipv4),
                        value = config.customEndpointV4,
                        onClick = { subPage = "endpoint_v4" }
                    )
                    Separator()
                    SettingsRow(
                        title = S(R.string.nw_manual_port),
                        value = config.customEndpointPort.toString(),
                        onClick = { subPage = "endpoint_port" }
                    )
                }
                Separator()
                SettingsRow(
                    title = S(R.string.nw_dns_policy),
                    value = config.dnsPolicy.ipv4,
                    icon = Icons.Default.Dns,
                    tint = Ios.Blue,
                    onClick = { subPage = "dns" }
                )
                Separator()
                SettingsToggle(
                    title = S(R.string.nw_adblock),
                    checked = config.enableAdblock,
                    onCheckedChange = { updateConfig(config.copy(enableAdblock = it)) },
                    icon = Icons.Default.Shield,
                    tint = Ios.Purple
                )
            }

            SettingsSectionHeader(text = S(R.string.nw_account_logs))
            SettingsGroup {
                SettingsRow(
                    title = S(R.string.nw_reset_account),
                    subtitle = S(config.accountType.titleRes),
                    icon = Icons.Default.RestartAlt,
                    tint = Ios.Orange,
                    onClick = {
                        NajiWarpEngine.resetAccount(context)
                        isOnboardingDone = false
                    }
                )
                Separator()
                SettingsRow(
                    title = if (showLogs) S(R.string.nw_close_logs) else S(R.string.nw_view_logs),
                    icon = Icons.Default.Terminal,
                    tint = Ios.Gray,
                    onClick = { showLogs = !showLogs }
                )
            }

            AnimatedVisibility(visible = showLogs) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(12.dp)
                ) {
                    if (logs.isEmpty()) {
                        Text(S(R.string.nw_no_logs), color = Color.Gray, fontSize = 11.sp)
                    } else {
                        logs.takeLast(15).forEach { line ->
                            Text(line, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, lineHeight = 16.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(36.dp))
        }
    }
}

@Composable
private fun MetricBadge(title: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format("%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}