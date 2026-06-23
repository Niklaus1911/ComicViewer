package com.comicviewer.ui.library

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import com.comicviewer.R
import com.comicviewer.LibraryComic
import com.comicviewer.LibraryAutoScanState
import com.comicviewer.RecentComic
import com.comicviewer.TABLET_LIBRARY_CARD_MIN_WIDTH_DP
import com.comicviewer.isTabletWidth
import com.comicviewer.loadLibraryComicThumbnailUri
import com.comicviewer.coercePageIndex
import com.comicviewer.matchingRecentComic
import com.comicviewer.billing.ProBillingState
import com.comicviewer.billing.ProBillingStatus
import com.comicviewer.ui.common.icon
import com.comicviewer.ui.common.rememberThrottledAction
import com.comicviewer.ui.common.softContentTransform
import com.comicviewer.ui.common.softPress
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private const val AUTO_SCAN_RESULT_VISIBLE_MS = 5_000L

@Composable
fun LibraryScreen(
    libraryComics: List<LibraryComic>,
    libraryAutoScanState: LibraryAutoScanState,
    recentComics: List<RecentComic>,
    proBillingState: ProBillingState,
    onStartAddToLibrary: () -> Unit,
    onAutoScanLibrary: () -> Unit,
    onClearAutoScanResult: () -> Unit,
    onBuyPro: () -> Unit,
    onRestorePro: () -> Unit,
    onOpenLibraryComic: (LibraryComic) -> Unit,
    onRemoveLibraryComic: (LibraryComic) -> Unit,
    modifier: Modifier = Modifier,
) {
    val throttledStartAdd = rememberThrottledAction(action = onStartAddToLibrary)
    val throttledAutoScan = rememberThrottledAction(action = onAutoScanLibrary)
    var comicPendingRemoval by remember { mutableStateOf<LibraryComic?>(null) }
    var showProDialog by remember { mutableStateOf(false) }
    val isAutoScanning = libraryAutoScanState is LibraryAutoScanState.Scanning

    LaunchedEffect(libraryAutoScanState) {
        if (libraryAutoScanState is LibraryAutoScanState.Complete) {
            delay(AUTO_SCAN_RESULT_VISIBLE_MS)
            onClearAutoScanResult()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding(),
    ) {
        LibraryHeader(
            isProUnlocked = proBillingState.isProUnlocked,
            isAutoScanning = isAutoScanning,
            onStartAddToLibrary = throttledStartAdd,
            onAutoScanLibrary = throttledAutoScan,
            onShowPro = { showProDialog = true },
        )
        LibraryAutoScanStatus(
            autoScanState = libraryAutoScanState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
        )

        AnimatedContent(
            targetState = libraryComics.isEmpty(),
            transitionSpec = { softContentTransform() },
            label = "libraryContentTransition",
            modifier = Modifier.weight(1f),
        ) { isEmpty ->
            if (isEmpty) {
                LibraryEmptyContent(onStartAddToLibrary = throttledStartAdd)
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val tabletLayout = isTabletWidth(maxWidth.value.roundToInt())
                    LazyVerticalGrid(
                        columns = if (tabletLayout) {
                            GridCells.Adaptive(TABLET_LIBRARY_CARD_MIN_WIDTH_DP.dp)
                        } else {
                            GridCells.Fixed(2)
                        },
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        gridItems(
                            items = libraryComics,
                            key = { comic -> "${comic.sourceType.storageName}:${comic.path}" },
                        ) { comic ->
                            LibraryComicCard(
                                libraryComic = comic,
                                progressLabel = comic.progressText(recentComics),
                                onClick = rememberThrottledAction { onOpenLibraryComic(comic) },
                                onRemove = { comicPendingRemoval = comic },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showProDialog) {
        ProUpgradeDialog(
            billingState = proBillingState,
            onBuyPro = onBuyPro,
            onRestorePro = onRestorePro,
            onDismiss = { showProDialog = false },
        )
    }

    comicPendingRemoval?.let { pendingComic ->
        AlertDialog(
            onDismissRequest = { comicPendingRemoval = null },
            title = {
                Text(text = stringResource(R.string.remove_from_library_title))
            },
            text = {
                Text(
                    text = stringResource(R.string.remove_from_library_message, pendingComic.title),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveLibraryComic(pendingComic)
                        comicPendingRemoval = null
                    },
                ) {
                    Text(stringResource(R.string.remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { comicPendingRemoval = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
fun LibraryHeader(
    isProUnlocked: Boolean,
    isAutoScanning: Boolean,
    onStartAddToLibrary: () -> Unit,
    onAutoScanLibrary: () -> Unit,
    onShowPro: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 18.dp, end = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.nav_library),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onShowPro) {
            Icon(
                imageVector = Icons.Filled.WorkspacePremium,
                contentDescription = stringResource(
                    if (isProUnlocked) R.string.pro_unlocked else R.string.pro_upgrade,
                ),
                tint = if (isProUnlocked) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        IconButton(
            onClick = onAutoScanLibrary,
            enabled = !isAutoScanning,
        ) {
            if (isAutoScanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = stringResource(R.string.library_auto_scan),
                )
            }
        }
        IconButton(onClick = onStartAddToLibrary) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.add_comic_to_library),
            )
        }
    }
}

@Composable
fun LibraryAutoScanStatus(
    autoScanState: LibraryAutoScanState,
    modifier: Modifier = Modifier,
) {
    val message = when (autoScanState) {
        LibraryAutoScanState.Idle -> null
        LibraryAutoScanState.Scanning -> stringResource(R.string.library_auto_scan_scanning)
        is LibraryAutoScanState.Complete -> when {
            autoScanState.directoryLimitReached -> stringResource(
                R.string.library_auto_scan_directory_limit,
                autoScanState.addedCount,
                autoScanState.foundCount,
            )

            autoScanState.libraryLimitReached -> stringResource(
                R.string.library_auto_scan_library_limit,
                autoScanState.addedCount,
                autoScanState.foundCount,
            )

            autoScanState.addedCount == 0 -> stringResource(
                R.string.library_auto_scan_no_new,
                autoScanState.foundCount,
            )

            else -> stringResource(
                R.string.library_auto_scan_complete,
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
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (billingState.isProUnlocked) {
                    stringResource(R.string.pro_unlocked)
                } else {
                    stringResource(R.string.pro_upgrade)
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (billingState.isProUnlocked) {
                        stringResource(R.string.pro_unlocked_message)
                    } else {
                        stringResource(R.string.pro_upgrade_message)
                    },
                )
                billingState.formattedPrice?.let { price ->
                    Text(
                        text = stringResource(R.string.pro_price, price),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (billingState.status is ProBillingStatus.Unavailable && !billingState.isProUnlocked) {
                    Text(
                        text = stringResource(R.string.pro_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                billingState.message?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
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
fun LibraryEmptyContent(
    onStartAddToLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.LibraryBooks,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(52.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.library_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.library_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onStartAddToLibrary) {
            Text(stringResource(R.string.add_comic))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryComicCard(
    libraryComic: LibraryComic,
    progressLabel: String,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .softPress(interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onRemove,
            ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            LibraryComicCover(libraryComic = libraryComic)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = libraryComic.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = progressLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun LibraryComicCover(
    libraryComic: LibraryComic,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var thumbnailUri by remember(libraryComic) { mutableStateOf<Uri?>(null) }

    LaunchedEffect(libraryComic) {
        thumbnailUri = loadLibraryComicThumbnailUri(context.applicationContext, libraryComic)
    }

    if (thumbnailUri != null) {
        Image(
            painter = rememberAsyncImagePainter(model = thumbnailUri),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize(),
        )
    } else {
        Box(
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


@Composable
private fun LibraryComic.progressText(recentComics: List<RecentComic>): String {
    val recentComic = matchingRecentComic(recentComics) ?: return displayName
    if (recentComic.pageCount <= 0) return stringResource(R.string.page_one)
    return stringResource(
        R.string.page_progress,
        coercePageIndex(recentComic.lastPageIndex, recentComic.pageCount) + 1,
        recentComic.pageCount,
    )
}
