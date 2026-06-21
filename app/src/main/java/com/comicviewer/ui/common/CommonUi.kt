package com.comicviewer.ui.common

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import com.comicviewer.TAP_THROTTLE_MS

private const val SOFT_ANIMATION_DURATION_MS = 180

@Composable
fun rememberThrottledAction(
    throttleMs: Long = TAP_THROTTLE_MS,
    action: () -> Unit,
): () -> Unit {
    var lastClickAt by remember { mutableStateOf(0L) }
    return remember(throttleMs, action) {
        {
            val now = System.currentTimeMillis()
            if (now - lastClickAt >= throttleMs) {
                lastClickAt = now
                action()
            }
        }
    }
}

@Composable
fun rememberThrottledPageAction(
    throttleMs: Long = TAP_THROTTLE_MS,
    action: (Int) -> Unit,
): (Int) -> Unit {
    var lastClickAt by remember { mutableStateOf(0L) }
    return remember(throttleMs, action) {
        { value ->
            val now = System.currentTimeMillis()
            if (now - lastClickAt >= throttleMs) {
                lastClickAt = now
                action(value)
            }
        }
    }
}

fun Modifier.softPress(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val targetScale = if (enabled && pressed) 0.98f else 1f
    val targetAlpha = if (enabled && pressed) 0.82f else 1f
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(SOFT_ANIMATION_DURATION_MS),
        label = "softPressScale",
    )
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(SOFT_ANIMATION_DURATION_MS),
        label = "softPressAlpha",
    )

    graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.alpha = alpha
    }
}

fun softContentTransform(): ContentTransform =
    (fadeIn(tween(SOFT_ANIMATION_DURATION_MS)) + scaleIn(
        initialScale = 0.98f,
        animationSpec = tween(SOFT_ANIMATION_DURATION_MS),
    )).togetherWith(
        fadeOut(tween(SOFT_ANIMATION_DURATION_MS)) + scaleOut(
            targetScale = 1.02f,
            animationSpec = tween(SOFT_ANIMATION_DURATION_MS),
        ),
    )
