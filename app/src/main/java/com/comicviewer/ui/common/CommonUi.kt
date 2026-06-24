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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
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

/**
 * Draws a moving diagonal highlight band ("sheen") across the content, clipped to the
 * content's own pixels via [BlendMode.SrcAtop]. [progress] in 0f..1f sweeps the band from
 * the left edge to the right edge, so callers can drive it from any animation phase.
 *
 * Uses an offscreen [CompositingStrategy] so SrcAtop blends only against this node's content
 * (not whatever is painted behind it). This relies on no RenderEffect and works on all API
 * levels (minSdk 24).
 */
fun Modifier.sheen(
    progress: Float,
    alpha: Float = 0.22f,
    color: Color = Color.White,
    bandFraction: Float = 0.26f,
): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        // Sweep a little past both edges so the band fully enters and exits.
        val sweepX = (progress * 1.6f - 0.3f) * size.width
        val halfBand = size.width * bandFraction
        drawRect(
            brush = Brush.linearGradient(
                0f to Color.Transparent,
                0.5f to color.copy(alpha = alpha),
                1f to Color.Transparent,
                start = Offset(sweepX - halfBand, 0f),
                end = Offset(sweepX + halfBand, size.height),
            ),
            blendMode = BlendMode.SrcAtop,
        )
    }
