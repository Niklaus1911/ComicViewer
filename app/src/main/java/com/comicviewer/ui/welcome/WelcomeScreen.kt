package com.comicviewer.ui.welcome

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Folder
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.comicviewer.R
import com.comicviewer.ui.common.rememberThrottledAction
import com.comicviewer.ui.theme.ComicViewerTheme

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

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            val wideLayout = maxWidth >= 720.dp
            val scrollState = rememberScrollState()

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
                        WelcomeHero()
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
                        WelcomeHero()
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

@Composable
private fun WelcomeContent(
    visible: Boolean,
    onAutoScan: () -> Unit,
    onBrowseManually: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StaggeredReveal(visible = visible, delayMillis = 140) {
            WelcomeBadge()
        }
        Spacer(modifier = Modifier.height(18.dp))
        StaggeredReveal(visible = visible, delayMillis = 210) {
            Text(
                text = stringResource(R.string.welcome_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        StaggeredReveal(visible = visible, delayMillis = 280) {
            Text(
                text = stringResource(R.string.welcome_message),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        StaggeredReveal(visible = visible, delayMillis = 350) {
            WelcomeReassurance()
        }
        Spacer(modifier = Modifier.height(18.dp))
        StaggeredReveal(visible = visible, delayMillis = 420) {
            WelcomeFormatRow()
        }
        Spacer(modifier = Modifier.height(28.dp))
        StaggeredReveal(
            visible = visible,
            delayMillis = 500,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(
                onClick = onAutoScan,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.welcome_auto_scan))
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        StaggeredReveal(
            visible = visible,
            delayMillis = 570,
            modifier = Modifier.fillMaxWidth(),
        ) {
            FilledTonalButton(
                onClick = onBrowseManually,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.welcome_browse_manually))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        StaggeredReveal(visible = visible, delayMillis = 640) {
            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.welcome_skip))
            }
        }
    }
}

@Composable
private fun WelcomeBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Archive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.welcome_archive_only),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun WelcomeReassurance(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.welcome_local_files),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun WelcomeFormatRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WelcomeFormatChip(text = stringResource(R.string.welcome_format_zip))
        Spacer(modifier = Modifier.width(8.dp))
        WelcomeFormatChip(text = stringResource(R.string.welcome_format_cbz))
    }
}

@Composable
private fun WelcomeFormatChip(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 1.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WelcomeHero(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "welcomeHeroMotion")
    val floatPhase by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "welcomeHeroFloat",
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

    Box(
        modifier = modifier.height(292.dp),
        contentAlignment = Alignment.Center,
    ) {
        WelcomeCoverCard(
            icon = Icons.Filled.Folder,
            modifier = Modifier
                .size(width = 142.dp, height = 210.dp)
                .graphicsLayer {
                    rotationZ = -10f + floatPhase
                    translationX = -78f + (floatPhase * 3f)
                    translationY = 12f - (floatPhase * 4f)
                },
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            iconColor = MaterialTheme.colorScheme.onTertiaryContainer,
            iconSize = 54,
        )
        WelcomeCoverCard(
            icon = Icons.Filled.Archive,
            modifier = Modifier
                .size(width = 132.dp, height = 198.dp)
                .graphicsLayer {
                    rotationZ = 9f - (floatPhase * 0.8f)
                    translationX = 82f - (floatPhase * 3f)
                    translationY = 18f + (floatPhase * 4f)
                },
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
            iconSize = 50,
        )
        WelcomeCoverCard(
            icon = Icons.AutoMirrored.Filled.LibraryBooks,
            modifier = Modifier
                .size(width = 154.dp, height = 230.dp)
                .shadow(20.dp, RoundedCornerShape(8.dp), clip = false)
                .graphicsLayer {
                    scaleX = 1f + (floatPhase * 0.01f)
                    scaleY = 1f + (floatPhase * 0.01f)
                    translationY = -(floatPhase * 3f)
                },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            iconSize = 68,
        )
        WelcomeScanBeam(scanProgress = scanProgress)
    }
}

@Composable
private fun WelcomeCoverCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: androidx.compose.ui.graphics.Color,
    iconColor: androidx.compose.ui.graphics.Color,
    iconSize: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 14.dp, top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                WelcomeCoverLine(width = 42.dp, color = iconColor.copy(alpha = 0.32f))
                WelcomeCoverLine(width = 64.dp, color = iconColor.copy(alpha = 0.22f))
                WelcomeCoverLine(width = 50.dp, color = iconColor.copy(alpha = 0.22f))
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(iconSize.dp),
            )
        }
    }
}

@Composable
private fun WelcomeCoverLine(
    width: androidx.compose.ui.unit.Dp,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(width = width, height = 5.dp),
        shape = RoundedCornerShape(8.dp),
        color = color,
    ) {}
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

@Composable
private fun StaggeredReveal(
    visible: Boolean,
    delayMillis: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(durationMillis = 360, delayMillis = delayMillis)) +
            slideInVertically(
                animationSpec = tween(
                    durationMillis = 460,
                    delayMillis = delayMillis,
                    easing = FastOutSlowInEasing,
                ),
                initialOffsetY = { height -> height / 5 },
            ) +
            scaleIn(
                animationSpec = tween(durationMillis = 460, delayMillis = delayMillis),
                initialScale = 0.98f,
            ),
    ) {
        content()
    }
}

@Preview(showBackground = true)
@Composable
private fun WelcomeScreenPreview() {
    ComicViewerTheme {
        WelcomeScreen(
            onAutoScan = {},
            onBrowseManually = {},
            onSkip = {},
        )
    }
}
