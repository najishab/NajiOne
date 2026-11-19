package com.najishab.one.ui.freedom

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.najishab.one.R
import com.najishab.one.ui.skin.LinkState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private val RiseEasing = CubicBezierEasing(0.45f, 0.02f, 0.3f, 1f)

/**
 * The origami bird. Its flight is scaled to the scene ([areaW] x [areaH]) so the whole rise-and-soar
 * always stays INSIDE the frame -- on a short scene the bird simply flies a little lower, instead of
 * sailing out of the clipped box and disappearing. Speed by state; static frame under reduced motion.
 */
@Composable
internal fun FreedomBird(
    state: LinkState,
    animate: Boolean,
    reduced: Boolean,
    areaW: Dp,
    areaH: Dp,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    // The cage slot is at (50%, 57.5%); half the bird plus a 4dp margin must stay inside.
    val inset = size / 2f + 4.dp
    val headroom = (areaH * 0.575f - inset).coerceAtLeast(24.dp)
    val rise = minOf(80.dp, headroom * 0.72f)
    val extra = minOf(24.dp, headroom * 0.23f)
    val xAmp = minOf(90.dp, (areaW * 0.5f - inset).coerceAtLeast(24.dp))
    val riseY = -rise
    val peakY = -(rise + extra)

    val x = remember { Animatable(0f) }
    val y = remember { Animatable(0f) }
    val rot = remember { Animatable(0f) }
    LaunchedEffect(state, reduced, riseY.value, peakY.value, xAmp.value) {
        when (state) {
            LinkState.ON -> {
                if (reduced) {
                    x.snapTo(0f); y.snapTo(riseY.value); rot.snapTo(0f)
                    return@LaunchedEffect
                }
                coroutineScope {
                    launch { x.animateTo(0f, tween(1150, easing = RiseEasing)) }
                    launch { rot.animateTo(0f, tween(1150, easing = RiseEasing)) }
                    launch { y.animateTo(riseY.value, tween(1150, easing = RiseEasing)) }
                }
                while (true) {
                    coroutineScope {
                        launch { x.animateTo(-xAmp.value, tween(2420, easing = FastOutSlowInEasing)) }
                        launch { y.animateTo(riseY.value - extra.value * 0.58f, tween(2420, easing = FastOutSlowInEasing)) }
                        launch { rot.animateTo(-5f, tween(2420, easing = FastOutSlowInEasing)) }
                    }
                    coroutineScope {
                        launch { x.animateTo(-xAmp.value * 0.16f, tween(2860, easing = FastOutSlowInEasing)) }
                        launch { y.animateTo(peakY.value, tween(2860, easing = FastOutSlowInEasing)) }
                        launch { rot.animateTo(2f, tween(2860, easing = FastOutSlowInEasing)) }
                    }
                    coroutineScope {
                        launch { x.animateTo(xAmp.value * 0.96f, tween(2860, easing = FastOutSlowInEasing)) }
                        launch { y.animateTo(riseY.value - extra.value * 0.5f, tween(2860, easing = FastOutSlowInEasing)) }
                        launch { rot.animateTo(5f, tween(2860, easing = FastOutSlowInEasing)) }
                    }
                    coroutineScope {
                        launch { x.animateTo(0f, tween(2860, easing = FastOutSlowInEasing)) }
                        launch { y.animateTo(riseY.value, tween(2860, easing = FastOutSlowInEasing)) }
                        launch { rot.animateTo(0f, tween(2860, easing = FastOutSlowInEasing)) }
                    }
                }
            }
            LinkState.ERROR -> {
                coroutineScope {
                    launch { x.animateTo(0f, tween(450, easing = FastOutSlowInEasing)) }
                    launch { rot.animateTo(0f, tween(450, easing = FastOutSlowInEasing)) }
                    launch { y.animateTo(0f, tween(450, easing = FastOutSlowInEasing)) }
                }
            }
            else -> {
                if (reduced) {
                    x.snapTo(0f); y.snapTo(0f); rot.snapTo(0f)
                } else {
                    coroutineScope {
                        launch { x.animateTo(0f, tween(1150, easing = RiseEasing)) }
                        launch { rot.animateTo(0f, tween(1150, easing = RiseEasing)) }
                        launch { y.animateTo(0f, tween(1150, easing = RiseEasing)) }
                    }
                }
            }
        }
    }
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.freedom_bird))
    val speed = when (state) {
        LinkState.CONNECTING -> 2.3f
        LinkState.ON -> 1.15f
        else -> 0.55f
    }
    val progress by animateLottieCompositionAsState(
        composition, iterations = LottieConstants.IterateForever, speed = speed, isPlaying = animate,
    )
    Box(
        modifier.size(size).graphicsLayer {
            translationX = x.value.dp.toPx()
            translationY = y.value.dp.toPx()
            rotationZ = rot.value
        }
    ) {
        LottieAnimation(composition, progress = { if (reduced) 0.33f else progress }, modifier = Modifier.fillMaxSize())
    }
}