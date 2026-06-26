package com.comicviewer.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.comicviewer.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.delay

private const val SUCCESS_AUTO_DISMISS_MS = 2_000L

@Composable
fun ProPurchaseSuccessOverlay(
    eventId: Long,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = remember(eventId) { Animatable(0f) }
    var readyToDismiss by remember(eventId) { mutableStateOf(false) }

    LaunchedEffect(eventId) {
        progress.snapTo(0f)
        readyToDismiss = false
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        )
        delay(SUCCESS_AUTO_DISMISS_MS)
        readyToDismiss = true
        onDismiss()
    }

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val surface = MaterialTheme.colorScheme.surface
    val progressValue = progress.value
    val cardScale = 0.86f + 0.14f * progressValue
    val cardAlpha = min(1f, progressValue * 1.4f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.42f * cardAlpha))
            .clickable(enabled = !readyToDismiss, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxDistance = min(size.width, size.height) * 0.34f
            val colors = listOf(primary, secondary, tertiary)
            repeat(20) { index ->
                val angle = (PI * 2.0 * index / 20.0).toFloat()
                val stagger = (index % 5) * 0.05f
                val particleProgress = ((progressValue - stagger) / (1f - stagger)).coerceIn(0f, 1f)
                val distance = maxDistance * particleProgress
                val alpha = (1f - particleProgress).coerceIn(0f, 1f) * 0.75f
                drawCircle(
                    color = colors[index % colors.size].copy(alpha = alpha),
                    radius = 4.dp.toPx() + (index % 3) * 2.dp.toPx(),
                    center = Offset(
                        x = center.x + cos(angle) * distance,
                        y = center.y + sin(angle) * distance * 0.72f,
                    ),
                )
            }
        }

        Surface(
            modifier = Modifier
                .padding(24.dp)
                .graphicsLayer {
                    scaleX = cardScale
                    scaleY = cardScale
                    alpha = cardAlpha
                },
            shape = RoundedCornerShape(28.dp),
            color = surface,
            tonalElevation = 8.dp,
            shadowElevation = 18.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Surface(
                        modifier = Modifier
                            .size(96.dp)
                            .graphicsLayer {
                                scaleX = 1f + 0.08f * progressValue
                                scaleY = 1f + 0.08f * progressValue
                            },
                        shape = CircleShape,
                        color = primary.copy(alpha = 0.16f),
                    ) {}
                    Icon(
                        imageVector = Icons.Filled.WorkspacePremium,
                        contentDescription = null,
                        tint = primary,
                        modifier = Modifier.size(54.dp),
                    )
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = tertiary,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(surface)
                            .size(32.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.pro_purchase_success_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.pro_purchase_success_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
