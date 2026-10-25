package com.comicviewer.ui.library

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.comicviewer.R
import com.comicviewer.LibraryAutoScanState
import com.comicviewer.LibraryComic
import com.comicviewer.LibraryComicMetadata
import com.comicviewer.LibraryFilter
import com.comicviewer.LibraryProgressFilter
import com.comicviewer.LibrarySortOrder
import com.comicviewer.RecentComic
import com.comicviewer.RecentComicSourceType
import com.comicviewer.TABLET_LIBRARY_CARD_MIN_WIDTH_DP
import com.comicviewer.availableLibraryTags
import com.comicviewer.isTabletWidth
import com.comicviewer.libraryProgressFraction
import com.comicviewer.libraryView
import com.comicviewer.loadLibraryComicThumbnailUri
import com.comicviewer.matchingRecentComic
import com.comicviewer.metadataFor
import com.comicviewer.resolvedLibraryMetadata
import com.comicviewer.resolvedLibraryProgressFilter
import com.comicviewer.resolvedLibraryTagFilter
import com.comicviewer.billing.ProBillingMessage
import com.comicviewer.billing.ProBillingState
import com.comicviewer.billing.ProBillingStatus
import com.comicviewer.ui.common.StaggeredReveal
import com.comicviewer.ui.common.StatusContent
import com.comicviewer.ui.common.icon
import com.comicviewer.ui.common.progressText
import com.comicviewer.ui.common.rememberThrottledAction
import com.comicviewer.ui.common.softContentTransform
import com.comicviewer.ui.common.softPress
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay


@Composable
fun LibraryAutoScanStatus(
    autoScanState: LibraryAutoScanState,
    modifier: Modifier = Modifier,
) {
    val message = when (autoScanState) {
        LibraryAutoScanState.Idle -> null
        LibraryAutoScanState.Scanning -> stringResource(R.string.library_auto_scan_scanning)
        is LibraryAutoScanState.Complete -> when {
            autoScanState.directoryLimitReached -> pluralStringResource(
                R.plurals.library_auto_scan_directory_limit,
                autoScanState.addedCount,
                autoScanState.addedCount,
                autoScanState.foundCount,
            )

            autoScanState.libraryLimitReached -> pluralStringResource(
                R.plurals.library_auto_scan_library_limit,
                autoScanState.addedCount,
                autoScanState.addedCount,
                autoScanState.foundCount,
            )

            autoScanState.addedCount == 0 -> pluralStringResource(
                R.plurals.library_auto_scan_no_new,
                autoScanState.foundCount,
                autoScanState.foundCount,
            )

            else -> pluralStringResource(
                R.plurals.library_auto_scan_complete,
                autoScanState.addedCount,
                autoScanState.addedCount,
                autoScanState.foundCount,
            )
        }

        is LibraryAutoScanState.Failed -> autoScanState.message
    } ?: return

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (autoScanState is LibraryAutoScanState.Scanning) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
            )
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = if (autoScanState is LibraryAutoScanState.Failed) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
fun ProUpgradeDialog(
    billingState: ProBillingState,
    onBuyPro: () -> Unit,
    onRestorePro: () -> Unit,
    isDebugBuild: Boolean,
    debugProUnlocked: Boolean,
    onDebugProUnlockedChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(
                        imageVector = Icons.Filled.WorkspacePremium,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(22.dp),
                    )
                }
                Text(
                    text = if (billingState.isProUnlocked) {
                        stringResource(R.string.pro_unlocked)
                    } else {
                        stringResource(R.string.pro_upgrade)
                    },
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = if (billingState.isProUnlocked) {
                        stringResource(R.string.pro_unlocked_message)
                    } else {
                        stringResource(R.string.pro_upgrade_message)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (!billingState.isProUnlocked) {
                    ProBenefitRow(
                        icon = Icons.Filled.AutoStories,
                        title = stringResource(R.string.pro_benefit_rtl_title),
                        message = stringResource(R.string.pro_benefit_rtl_message),
                    )
                    ProBenefitRow(
                        icon = Icons.Filled.AspectRatio,
                        title = stringResource(R.string.pro_benefit_fit_width_title),
                        message = stringResource(R.string.pro_benefit_fit_width_message),
                    )
                    ProBenefitRow(
                        icon = Icons.Filled.Palette,
                        title = stringResource(R.string.pro_benefit_background_title),
                        message = stringResource(R.string.pro_benefit_background_message),
                    )
                    ProBenefitRow(
                        icon = Icons.Filled.Palette,
                        title = stringResource(R.string.pro_benefit_theme_title),
                        message = stringResource(R.string.pro_benefit_theme_message),
                    )
                    ProBenefitRow(
                        icon = Icons.Filled.Favorite,
                        title = stringResource(R.string.pro_benefit_library_title),
                        message = stringResource(R.string.pro_benefit_library_message),
                    )
                    ProBenefitRow(
                        icon = Icons.AutoMirrored.Filled.Label,
                        title = stringResource(R.string.pro_benefit_tags_title),
                        message = stringResource(R.string.pro_benefit_tags_message),
                    )
                }
                billingState.formattedPrice?.let { price ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            text = stringResource(R.string.pro_price, price),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
                if (
                    billingState.status is ProBillingStatus.Unavailable &&
                    !billingState.isProUnlocked &&
                    billingState.message != ProBillingMessage.PurchaseUnavailable
                ) {
                    Text(
                        text = stringResource(R.string.pro_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                billingState.message?.let { message ->
                    Text(
                        text = proBillingMessageText(message),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (message == ProBillingMessage.CheckingPurchase) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
                if (isDebugBuild) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.pro_debug_unlock),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = stringResource(R.string.pro_debug_unlock_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = debugProUnlocked,
                            onCheckedChange = onDebugProUnlockedChanged,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!billingState.isProUnlocked) {
                TextButton(
                    onClick = onBuyPro,
                    enabled = billingState.status is ProBillingStatus.Available,
                ) {
                    Text(stringResource(R.string.pro_buy))
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.ok))
                }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onRestorePro) {
                    Text(stringResource(R.string.pro_restore))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
    )
}

@Composable
private fun proBillingMessageText(message: ProBillingMessage): String =
    stringResource(
        when (message) {
            ProBillingMessage.CheckingPurchase -> R.string.pro_purchase_checking
            ProBillingMessage.PurchaseUnavailable -> R.string.pro_unavailable
            ProBillingMessage.PurchaseFailed -> R.string.pro_purchase_failed
        },
    )

@Composable
private fun ProBenefitRow(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun LibraryEmptyContent(
    onStartAddToLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StatusContent(
        icon = Icons.AutoMirrored.Filled.LibraryBooks,
        title = stringResource(R.string.library_empty_title),
        message = stringResource(R.string.library_empty_message),
        modifier = modifier,
        actionLabel = stringResource(R.string.add_comic),
        onAction = onStartAddToLibrary,
    )
}

@Composable
fun LibraryComicCover(
    libraryComic: LibraryComic,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var thumbnailUri by remember(libraryComic) { mutableStateOf<Uri?>(null) }
    var loading by remember(libraryComic) { mutableStateOf(true) }

    LaunchedEffect(libraryComic) {
        thumbnailUri = loadLibraryComicThumbnailUri(context.applicationContext, libraryComic)
        loading = false
    }

    val uri = thumbnailUri
    when {
        uri != null -> Image(
            painter = rememberAsyncImagePainter(
                model = remember(uri) {
                    ImageRequest.Builder(context).data(uri).crossfade(true).build()
                },
            ),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize(),
        )

        loading -> CoverLoadingPlaceholder(modifier = modifier.fillMaxSize())

        else -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = libraryComic.sourceType.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(42.dp),
            )
        }
    }
}

/** Gentle pulse while a cover thumbnail is generated, so empty tiles read as loading. */
@Composable
private fun CoverLoadingPlaceholder(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "coverPlaceholder")
    val alpha by transition.animateFloat(
        initialValue = 0.04f,
        targetValue = 0.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "coverPlaceholderAlpha",
    )
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)),
    )
}
