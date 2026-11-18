package com.najishab.one.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.runtime.Composable
import com.najishab.one.R
import com.najishab.one.data.SubscriptionUsage
import com.najishab.one.data.VpnSubscription
import com.najishab.one.ui.configstudio.TimeLeftUnit
import com.najishab.one.ui.configstudio.bytesFa
import com.najishab.one.ui.configstudio.faNum
import com.najishab.one.ui.configstudio.timeLeftParts
import com.najishab.one.ui.configstudio.whenText
import com.najishab.one.ui.settings.Ios
import com.najishab.one.ui.settings.SettingsFooter
import com.najishab.one.ui.settings.SettingsGroup
import com.najishab.one.ui.settings.SettingsRow
import com.najishab.one.utils.S

/**
 * What is left on a subscription, above its servers: the volume and the time, as its server stated
 * them at the last update.
 *
 * The same figures other apps show as the subscription's first entries (04c-studio-sub.js ›
 * studioSubInfoLines), in the same rounding -- bytes by [bytesFa], time by [timeLeftParts] -- so the
 * person reads one number in every app. The volume is as of the last update; the time is counted
 * down from the end date as the card is drawn, so it stays true between updates.
 */
@Composable
fun SubscriptionUsageCard(sub: VpnSubscription, usage: SubscriptionUsage) {
    val total = usage.totalBytes
    val left = usage.leftBytes
    val volume = when {
        total == null -> S(R.string.nodes_sub_unlimited)
        left == 0L -> S(R.string.nodes_sub_used_up)
        else -> S(R.string.nodes_sub_of).replace("%1\$s", bytesFa(left ?: 0L)).replace("%2\$s", bytesFa(total))
    }
    val volumeTint = when {
        total == null -> Ios.Green
        left == 0L -> Ios.Red
        (left ?: 0L) * 10 < total -> Ios.Orange
        else -> Ios.Green
    }

    val expireAt = usage.expireAt
    val parts = expireAt?.let { timeLeftParts(it) }
    val time = when {
        expireAt == null -> S(R.string.nodes_sub_no_end)
        parts == null -> S(R.string.nodes_sub_ended)
        else -> S(
            when (parts.second) {
                TimeLeftUnit.DAYS -> R.string.nodes_sub_days
                TimeLeftUnit.HOURS -> R.string.nodes_sub_hours
                TimeLeftUnit.MINUTES -> R.string.nodes_sub_minutes
            }
        ).replace("%1\$s", faNum(parts.first))
    }
    val timeTint = when {
        expireAt == null -> Ios.Green
        parts == null -> Ios.Red
        parts.second != TimeLeftUnit.DAYS || parts.first <= 3 -> Ios.Orange
        else -> Ios.Green
    }

    SettingsGroup {
        SettingsRow(
            title = S(R.string.nodes_sub_volume_left),
            value = volume,
            icon = Icons.Outlined.DataUsage,
            tint = volumeTint,
            showChevron = false,
        )
        Separator()
        SettingsRow(
            title = S(R.string.nodes_sub_time_left),
            value = time,
            icon = Icons.Outlined.Schedule,
            tint = timeTint,
            showChevron = false,
        )
    }
    if (sub.lastUpdated > 0) {
        SettingsFooter(S(R.string.nodes_sub_updated).replace("%1\$s", whenText(sub.lastUpdated)))
    }
}
