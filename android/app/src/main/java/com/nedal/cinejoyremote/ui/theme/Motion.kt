package com.nedal.cinejoyremote.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

// Motion tokens: short, quiet transitions only — nothing heavy or laggy.
object Motion {
    const val fast = 140        // press feedback, small state flips
    const val standard = 220    // screen fades, content swaps
    const val slow = 350        // sheets / larger reveals (if needed)

    val standardEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    // Subtle cross-fades for the NavHost instead of the default slide.
    val screenEnter: EnterTransition get() = fadeIn(tween(standard, easing = standardEasing))
    val screenExit: ExitTransition get() = fadeOut(tween(fast, easing = standardEasing))
    val screenPopEnter: EnterTransition get() = fadeIn(tween(fast, easing = standardEasing))
    val screenPopExit: ExitTransition get() = fadeOut(tween(fast, easing = standardEasing))
}

// The design system's "scale on tap": a quiet press-down on posters, tiles
// and hero slides while the finger is on them. Pairs with Coil's image
// crossfade for the streaming-app feel without any heavy animation.
fun Modifier.pressScale(target: Float = 0.965f): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) target else 1f,
        animationSpec = tween(Motion.fast, easing = Motion.standardEasing),
        label = "pressScale",
    )
    this
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true
                try {
                    waitForUpOrCancellation()
                } finally {
                    pressed = false
                }
            }
        }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
}
