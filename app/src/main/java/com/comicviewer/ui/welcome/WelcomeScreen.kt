package com.comicviewer.ui.welcome

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.comicviewer.R
import com.comicviewer.ui.common.StaggeredReveal
import com.comicviewer.ui.common.rememberThrottledAction
import com.comicviewer.ui.common.sheen
import com.comicviewer.ui.common.softPress
import com.comicviewer.ui.theme.ComicViewerTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun WelcomeScreen(
    onAutoScan: () -> Unit,
    onBrowseManually: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val throttledAutoScan = rememberThrottledAction(action = onAutoScan)
    val throttledBrowseManually = rememberThrottledAction(action = onBrowseManually)
    val throttledSkip = rememberThrottledAction(action = onSkip)
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
    }

    val colorScheme = MaterialTheme.colorScheme
    // AMOLED forces background + surfaces to pure black (see Theme.kt); detect it so the
    // ambient glow stays a whisper on true black instead of a smudge.
    val amoledLike = colorScheme.background == Color.Black && colorScheme.surface == Color.Black
    val darkTheme = colorScheme.background.luminance() < 0.5f

    Surface(
        modifier = modifier.fillMaxSize(),
        color = colorScheme.background,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // Full-bleed animated backdrop, fixed to the viewport so it never scrolls with content.
            WelcomeBackground(
                amoledLike = amoledLike,
                darkTheme = darkTheme,
                modifier = Modifier.fillMaxSize(),
            )

            val wideLayout = maxWidth >= 720.dp
            val scrollState = rememberScrollState()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (wideLayout) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 920.dp)
                            .verticalScroll(scrollState),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(40.dp),
                    ) {
                        StaggeredReveal(
                            visible = visible,
                            delayMillis = 60,
                            modifier = Modifier.weight(0.9f),
                        ) {
                            WelcomeHero(visible = visible)
                        }
                        WelcomeContent(
                            visible = visible,
                            onAutoScan = throttledAutoScan,
                            onBrowseManually = throttledBrowseManually,
                            onSkip = throttledSkip,
                            modifier = Modifier.weight(1.1f),
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 520.dp)
                            .verticalScroll(scrollState),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        StaggeredReveal(
                            visible = visible,
                            delayMillis = 40,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            WelcomeHero(visible = visible)
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        WelcomeContent(
                            visible = visible,
                            onAutoScan = throttledAutoScan,
                            onBrowseManually = throttledBrowseManually,
                            onSkip = throttledSkip,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Slowly drifting radial-gradient "aurora" blobs derived from the theme's primary/secondary/
 * tertiary colors. Soft falloff (transparent outer stop) stands in for a blur, so it works on
 * minSdk 24 without RenderEffect. Intensity is toned down for light themes and reduced to two
 * faint blobs on AMOLED.
 */
@Composable
private fun WelcomeBackground(
    amoledLike: Boolean,
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val blobColors = remember(colorScheme) {
        listOf(colorScheme.primary, colorScheme.secondary, colorScheme.tertiary)
    }
    val baseAlpha = when {
        amoledLike -> 0.06f
        darkTheme -> 0.18f
        else -> 0.12f
    }

    val transition = rememberInfiniteTransition(label = "welcomeBackground")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "welcomeBackgroundPhase",
    )

    Box(
        modifier = modifier.drawBehind {
            val w = size.width
            val h = size.height

            fun blob(cx: Float, cy: Float, radius: Float, color: Color) {
                drawRect(
                    brush = Brush.radialGradient(
                        0f to color.copy(alpha = baseAlpha),
                        0.55f to color.copy(alpha = baseAlpha * 0.45f),
                        1f to Color.Transparent,
                        center = Offset(cx, cy),
                        radius = radius,
                    ),
                )
            }

            blob(
                cx = w * (0.28f + 0.12f * cos(phase)),
                cy = h * (0.26f + 0.10f * sin(phase * 0.9f)),
                radius = w * 0.85f,
                color = blobColors[0],
            )
            blob(
                cx = w * (0.74f + 0.10f * cos(phase * 0.8f + 2f)),
                cy = h * (0.64f + 0.09f * sin(phase + 1.3f)),
                radius = w * 0.75f,
                color = blobColors[1],
            )
            if (!amoledLike) {
                blob(
                    cx = w * (0.55f + 0.09f * sin(phase * 1.1f)),
                    cy = h * (0.90f + 0.07f * cos(phase * 0.7f)),
                    radius = w * 0.70f,
                    color = blobColors[2],
                )
            }
        },
    )
}

@Composable
private fun WelcomeContent(
    visible: Boolean,
    onAutoScan: () -> Unit,
    onBrowseManually: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val autoScanInteraction = remember { MutableInteractionSource() }
    val browseInteraction = remember { MutableInteractionSource() }
    val skipInteraction = remember { MutableInteractionSource() }

    // Periodic glint that sweeps across the primary action to draw the eye.
    val sheenTransition = rememberInfiniteTransition(label = "welcomeButtonSheen")
    val sheenProgress by sheenTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2500, delayMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "welcomeButtonSheenProgress",
    )

    Column(
        modifier = modifier
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = 460.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StaggeredReveal(visible = visible, delayMillis = 200) {
            Text(
                text = stringResource(R.string.welcome_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        StaggeredReveal(visible = visible, delayMillis = 270) {
            Text(
                text = stringResource(R.string.welcome_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        StaggeredReveal(visible = visible, delayMillis = 340) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = stringResource(R.string.welcome_footnote),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        StaggeredReveal(
            visible = visible,
            delayMillis = 420,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(
                onClick = onAutoScan,
                interactionSource = autoScanInteraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .softPress(autoScanInteraction)
                    .sheen(progress = sheenProgress, alpha = 0.28f),
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.welcome_auto_scan), style = MaterialTheme.typography.titleSmall)
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        StaggeredReveal(
            visible = visible,
            delayMillis = 490,
            modifier = Modifier.fillMaxWidth(),
        ) {
            FilledTonalButton(
                onClick = onBrowseManually,
                interactionSource = browseInteraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .softPress(browseInteraction),
            ) {
                Icon(
                    imageVector = Icons.Filled.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.welcome_browse_manually), style = MaterialTheme.typography.titleSmall)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        StaggeredReveal(visible = visible, delayMillis = 560) {
            TextButton(
                onClick = onSkip,
                interactionSource = skipInteraction,
                modifier = Modifier.softPress(skipInteraction),
            ) {
                Text(stringResource(R.string.welcome_skip))
            }
        }
    }
}

@Composable
private fun WelcomeHero(visible: Boolean, modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    val amoledLike = colorScheme.background == Color.Black && colorScheme.surface == Color.Black
    val darkTheme = colorScheme.background.luminance() < 0.5f
    val haloColor = colorScheme.primary
    val haloAlpha = when {
        amoledLike -> 0.10f
        darkTheme -> 0.26f
        else -> 0.16f
    }
    val shimmerAlpha = if (amoledLike) 0.12f else 0.22f

    val infiniteTransition = rememberInfiniteTransition(label = "welcomeHeroMotion")
    // Continuous unbounded phase drives each card from a different sin/cos frequency, so they
    // orbit independently instead of swaying in lockstep.
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "welcomeHeroPhase",
    )
    val scanProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, delayMillis = 350, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "welcomeHeroScan",
    )
    // One-shot fan-out: cards start stacked at center (intro = 0) and settle into their resting
    // fanned layout (intro = 1). Resting offsets are multiplied by intro so the infinite float,
    // which is added on top, keeps running cleanly once the intro completes.
    val intro by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 700, delayMillis = 120, easing = FastOutSlowInEasing),
        label = "welcomeHeroIntro",
    )

    Box(
        modifier = modifier
            .height(292.dp)
            .drawBehind {
                val radius = size.minDimension * 0.62f
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to haloColor.copy(alpha = haloAlpha),
                        1f to Color.Transparent,
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        WelcomeComicCover(
            modifier = Modifier
                .size(width = 142.dp, height = 210.dp)
                .graphicsLayer {
                    rotationZ = (-10f * intro) + 3f * sin(phase + 0.5f)
                    translationX = (-78f * intro) + 8f * sin(phase)
                    translationY = (12f * intro) + 6f * cos(phase * 0.9f)
                    alpha = intro
                },
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            inkColor = MaterialTheme.colorScheme.onTertiaryContainer,
            accentColor = MaterialTheme.colorScheme.tertiary,
            layout = 1,
            sheenProgress = scanProgress,
            sheenAlpha = shimmerAlpha,
        )
        WelcomeComicCover(
            modifier = Modifier
                .size(width = 132.dp, height = 198.dp)
                .graphicsLayer {
                    rotationZ = (9f * intro) - 3f * sin(phase * 0.7f)
                    translationX = (82f * intro) - 7f * sin(phase * 0.8f + 1.2f)
                    translationY = (18f * intro) + 6f * cos(phase + 2f)
                    alpha = intro
                },
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            inkColor = MaterialTheme.colorScheme.onSecondaryContainer,
            accentColor = MaterialTheme.colorScheme.secondary,
            layout = 2,
            sheenProgress = scanProgress,
            sheenAlpha = shimmerAlpha,
        )
        WelcomeComicCover(
            modifier = Modifier
                .size(width = 154.dp, height = 230.dp)
                .graphicsLayer {
                    val introScale = 0.94f + 0.06f * intro
                    val breathe = 1f + 0.018f * sin(phase * 0.6f)
                    scaleX = breathe * introScale
                    scaleY = breathe * introScale
                    translationY = -9f * sin(phase * 0.6f) - 12f * (1f - intro)
                    alpha = intro
                }
                .shadow(20.dp, RoundedCornerShape(10.dp), clip = false),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            inkColor = MaterialTheme.colorScheme.onPrimaryContainer,
            accentColor = MaterialTheme.colorScheme.primary,
            layout = 0,
            sheenProgress = scanProgress,
            sheenAlpha = shimmerAlpha,
        )
        WelcomeScanBeam(scanProgress = scanProgress)
    }
}

/**
 * A drawn comic cover: masthead, a page of panels, a speech bubble and halftone dots, all in theme
 * colors so it follows light, dark, AMOLED and Pro themes.
 */
@Composable
private fun WelcomeComicCover(
    containerColor: Color,
    inkColor: Color,
    accentColor: Color,
    layout: Int,
    sheenProgress: Float,
    sheenAlpha: Float,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(9.dp)
                .sheen(progress = sheenProgress, alpha = sheenAlpha, color = inkColor),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Masthead
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(inkColor.copy(alpha = 0.85f)),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(width = 46.dp, height = 7.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(containerColor.copy(alpha = 0.9f)),
                )
            }
            when (layout) {
                0 -> {
                    Row(modifier = Modifier.weight(1.2f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ComicPanel(inkColor, accentColor, halftone = true, bubble = true, modifier = Modifier.weight(1.4f))
                        ComicPanel(inkColor, accentColor, modifier = Modifier.weight(1f))
                    }
                    ComicPanel(inkColor, accentColor, modifier = Modifier.weight(1f).fillMaxWidth())
                }
                1 -> {
                    ComicPanel(inkColor, accentColor, halftone = true, modifier = Modifier.weight(1f).fillMaxWidth())
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ComicPanel(inkColor, accentColor, modifier = Modifier.weight(1f))
                        ComicPanel(inkColor, accentColor, bubble = true, modifier = Modifier.weight(1f))
                    }
                }
                else -> {
                    ComicPanel(inkColor, accentColor, bubble = true, modifier = Modifier.weight(1.6f).fillMaxWidth())
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ComicPanel(inkColor, accentColor, halftone = true, modifier = Modifier.weight(1f))
                        ComicPanel(inkColor, accentColor, modifier = Modifier.weight(1f))
                        ComicPanel(inkColor, accentColor, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ComicPanel(
    inkColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier,
    halftone: Boolean = false,
    bubble: Boolean = false,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(4.dp))
            .background(accentColor.copy(alpha = 0.55f))
            .border(1.5.dp, inkColor.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
            .drawBehind {
                if (halftone) {
                    val step = 6.dp.toPx()
                    val dot = 1.3.dp.toPx()
                    var y = step / 2
                    var row = 0
                    while (y < size.height) {
                        var x = if (row % 2 == 0) step / 2 else step
                        while (x < size.width) {
                            drawCircle(inkColor.copy(alpha = 0.25f), radius = dot, center = Offset(x, y))
                            x += step
                        }
                        y += step
                        row += 1
                    }
                }
                if (bubble) {
                    val radius = size.minDimension * 0.24f
                    val center = Offset(size.width * 0.66f, size.height * 0.3f)
                    drawCircle(Color.White.copy(alpha = 0.92f), radius = radius, center = center)
                    drawCircle(
                        inkColor.copy(alpha = 0.7f),
                        radius = radius,
                        center = center,
                        style = Stroke(width = 1.2.dp.toPx()),
                    )
                }
            },
    )
}

@Composable
private fun WelcomeScanBeam(scanProgress: Float, modifier: Modifier = Modifier) {
    val alpha = when {
        scanProgress < 0.12f -> scanProgress / 0.12f
        scanProgress > 0.86f -> (1f - scanProgress) / 0.14f
        else -> 1f
    }.coerceIn(0f, 1f)

    Surface(
        modifier = modifier
            .size(width = 218.dp, height = 14.dp)
            .graphicsLayer {
                translationY = -106f + (scanProgress * 212f)
                rotationZ = -4f
                this.alpha = alpha * 0.55f
            },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.42f),
    ) {}
}

@Preview(showBackground = true, name = "Welcome – Dark")
@Composable
private fun WelcomeScreenPreview() {
    ComicViewerTheme(dynamicColor = false) {
        WelcomeScreen(
            onAutoScan = {},
            onBrowseManually = {},
            onSkip = {},
        )
    }
}

@Preview(showBackground = true, name = "Welcome – Light")
@Composable
private fun WelcomeScreenLightPreview() {
    ComicViewerTheme(darkTheme = false, dynamicColor = false) {
        WelcomeScreen(
            onAutoScan = {},
            onBrowseManually = {},
            onSkip = {},
        )
    }
}

@Preview(showBackground = true, name = "Welcome – AMOLED")
@Composable
private fun WelcomeScreenAmoledPreview() {
    ComicViewerTheme(darkTheme = true, dynamicColor = false, amoledTheme = true) {
        WelcomeScreen(
            onAutoScan = {},
            onBrowseManually = {},
            onSkip = {},
        )
    }
}
