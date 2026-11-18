package com.najishab.one.ui.nexus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * True under the screens that wear the NEXUS skin (Settings). Surfaces that normally draw frosted
 * glass over the wallpaper ([com.najishab.one.ui.home.frostedGlass]) and the Ios palette read it
 * and answer with NEXUS glass and colours instead. Everywhere else it stays false, so no other
 * screen changes.
 */
val LocalNexusSkin = compositionLocalOf { false }

/** A card of NEXUS glass: a faint lift, a hairline that fades from blue to violet. */
fun Modifier.nexusGlass(shape: Shape): Modifier = this
    .clip(shape)
    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.07f), Nexus.Violet.copy(alpha = 0.10f))))
    .border(1.dp, Brush.verticalGradient(listOf(Nexus.Blue.copy(alpha = 0.45f), Nexus.Violet.copy(alpha = 0.22f))), shape)

/** The four root destinations of the NEXUS shell. */
enum class NexusPage { HOME, METHODS, TOOLS, SETTINGS }

/** NEXUS palette: deep space, electric blue, violet, and a colour for each link state. */
object Nexus {
    val Space = Color(0xFF02030A)
    val Blue = Color(0xFF2D7BFF)
    val Cyan = Color(0xFF37C8FF)
    val Violet = Color(0xFF8A4DFF)
    val Indigo = Color(0xFF4C3DFF)
    val Mint = Color(0xFF35E0A1)
    val Amber = Color(0xFFFFB020)
    val Danger = Color(0xFFFF4D5E)
    val Text = Color(0xFFF2F5FF)
    val Muted = Color(0xFF8E9AC0)
}

/** The shared backdrop of every NEXUS page. Opaque, so it also hides the parked tabs beneath it. */
fun Modifier.nexusBackdrop(): Modifier = this.background(
    Brush.verticalGradient(
        0f to Color(0xFF02030A),
        0.55f to Color(0xFF040716),
        1f to Color(0xFF0A0620),
    )
)
