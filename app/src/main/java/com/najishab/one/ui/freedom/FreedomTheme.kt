package com.najishab.one.ui.freedom

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * True on the hosted screens that wear the FREEDOM skin (Settings, VPN settings), the counterpart
 * of [com.najishab.one.ui.nexus.LocalNexusSkin]. frostedGlass and the Ios palette read it and answer
 * with Freedom cards and colours. Everywhere else it stays false, so no other screen changes.
 */
val LocalFreedomSkin = compositionLocalOf { false }

/** Height the docked Freedom bottom bar takes, so a page's content can clear it. */
internal val FREEDOM_NAV_RESERVE = 84.dp

/** FREEDOM palette, copied from the mockup's «copy palette» button and CSS. Do not eyeball. */
object Freedom {
    val Night = Color(0xFF0A1120)
    val Card = Color(0xFF141922)
    val Card2 = Color(0xFF1A212E)
    val Border = Color(0xFF232C3B)
    val Cage = Color(0xFF93A7C4)
    val CageWarm = Color(0xFFC8B299)
    val Primary = Color(0xFFD97706)
    val Sunrise = Color(0xFFFFB35C)
    val Ok = Color(0xFF2FD6A3)
    val Bird = Color(0xFF1996D2)
    val Sky = Color(0xFF5DB4F5)
    val Text = Color(0xFFF2F5F9)
    val Muted = Color(0xFF8B96A8)
    val Amber = Color(0xFFF5B94A)
    val Error = Color(0xFFFF6B5E)
    val ErrorBg = Color(0xFF4A2320)
    val ErrorText = Color(0xFFFFB4AB)
    val ErrorBorder = Color(0xFF7A3530)
    val OnBg = Color(0xFF17352B)
    val OnText = Color(0xFF7FE7C6)
    val OnBorder = Color(0xFF1F7A5C)
    val SceneBase = Color(0xFF0E1A2E)
    val ChipBg = Color(0xDB080C14)
    val ChipText = Color(0xFFDBE4EF)
    val ChipDot = Color(0xFF77839A)
    val BarBg = Color(0xF50D1118)
    val TabIdle = Color(0xFF77839A)
    val HillFar = Color(0xFF101C30)
    val HillNear = Color(0xFF080F1C)
    val FarBird = Color(0xFF16283F)
    val Mote = Color(0xFFFFD9A0)
    val Keyhole = Color(0xFF0E1A2E)
    val ConnectText = Color(0xFFFFF8EC)
    val TitleDanger = Color(0xFFFF9D94)
}

/** The shared backdrop of every FREEDOM page. Opaque, so it hides the parked tabs beneath it. */
fun Modifier.freedomBackdrop(): Modifier = this.background(
    Brush.verticalGradient(
        0f to Color(0xFF070F1E),
        0.55f to Freedom.Night,
        1f to Color(0xFF0D1930),
    )
)

/** A card of FREEDOM glass: the mockup's flat card with its hairline, not a wallpaper slice. */
fun Modifier.freedomCard(shape: Shape = RoundedCornerShape(18.dp)): Modifier = this
    .clip(shape)
    .background(Freedom.Card)
    .border(1.dp, Freedom.Border, shape)

/**
 * Reduced motion: the system animator scale at 0 (developer options, and the accessibility
 * «remove animations» toggle). The scene then snaps between states and draws a static bird.
 */
@Composable
internal fun rememberFreedomReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        runCatching {
            Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}