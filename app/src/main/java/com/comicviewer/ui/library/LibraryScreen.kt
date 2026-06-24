package com.comicviewer.ui.library

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import com.comicviewer.R
import com.comicviewer.LibraryComic
import com.comicviewer.LibraryAutoScanState
import com.comicviewer.LibraryFilter
import com.comicviewer.LibrarySortOrder
import com.comicviewer.RecentComic
import com.comicviewer.RecentComicSourceType
import com.comicviewer.TABLET_LIBRARY_CARD_MIN_WIDTH_DP
import com.comicviewer.isTabletWidth
import com.comicviewer.libraryProgressFraction
import com.comicviewer.libraryView
import com.comicviewer.loadLibraryComicThumbnailUri
import com.comicviewer.matchingRecentComic
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

private const val AUTO_SCAN_RESULT_VISIBLE_MS = 5_000L
private const val MAX_STAGGER_STEPS = 8
private const val STAGGER_STEP_MS = 40

private enum class LibraryContentMode { EmptyLibrary, NoResults, Results }

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

    // Pure view-state derived from the lists already passed in — kept local (survives rotation via
    // rememberSaveable; enums save through the default Serializable path) so app state stays untouched.
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sortOrder by rememberSaveable { mutableStateOf(LibrarySortOrder.RecentlyAdded) }
    var sourceFilter by rememberSaveable { mutableStateOf(LibraryFilter.All) }

    LaunchedEffect(libraryAutoScanState) {
        if (libraryAutoScanState is LibraryAutoScanState.Complete) {
            delay(AUTO_SCAN_RESULT_VISIBLE_MS)
            onClearAutoScanResult()
        }
    }

    val visibleComics = remember(libraryComics, recentComics, searchQuery, sourceFilter, sortOrder) {
        libraryView(
            comics = libraryComics,
            recentComics = recentComics,
            query = searchQuery,
            filter = sourceFilter,
            order = sortOrder,
        )
    }
    val contentMode = when {
        libraryComics.isEmpty() -> LibraryContentMode.EmptyLibrary
        visibleComics.isEmpty() -> LibraryContentMode.NoResults
        else -> LibraryContentMode.Results
    }

    fun resetFilters() {
        searchQuery = ""
        sourceFilter = LibraryFilter.All
        sortOrder = LibrarySortOrder.RecentlyAdded
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding(),
    ) {
        LibraryHeader(
            itemCount = libraryComics.size,
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

        if (libraryComics.isNotEmpty()) {
            LibrarySearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            )
            LibraryControlChips(
                sortOrder = sortOrder,
                sourceFilter = sourceFilter,
                onSortSelected = { sortOrder = it },
                onFilterSelected = { sourceFilter = it },
            )
        }

        AnimatedContent(
            targetState = contentMode,
            transitionSpec = { softContentTransform() },
            label = "libraryContentTransition",
            modifier = Modifier.weight(1f),
        ) { mode ->
            when (mode) {
                LibraryContentMode.EmptyLibrary -> LibraryEmptyContent(
                    onStartAddToLibrary = throttledStartAdd,
                    modifier = Modifier.fillMaxSize(),
                )

                LibraryContentMode.NoResults -> LibraryNoResultsContent(
                    onClearFilters = ::resetFilters,
                    modifier = Modifier.fillMaxSize(),
                )

                LibraryContentMode.Results -> LibraryComicsGrid(
                    comics = visibleComics,
                    recentComics = recentComics,
                    onOpenLibraryComic = onOpenLibraryComic,
                    onRemoveLibraryComic = { comicPendingRemoval = it },
                    modifier = Modifier.fillMaxSize(),
                )
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
    itemCount: Int,
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.nav_library),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (itemCount > 0) {
                Text(
                    text = stringResource(R.string.library_item_count, itemCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
                    imageVector = Icons.Filled.Radar,
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
private fun LibrarySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        singleLine = true,
        leadingIcon = {
            Icon(imageVector = Icons.Filled.Search, contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.library_clear_search),
                    )
                }
            }
        },
        placeholder = { Text(stringResource(R.string.library_search_label)) },
        shape = RoundedCornerShape(16.dp),
    )
}

@Composable
private fun LibraryControlChips(
    sortOrder: LibrarySortOrder,
    sourceFilter: LibraryFilter,
    onSortSelected: (LibrarySortOrder) -> Unit,
    onFilterSelected: (LibraryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LibrarySortOrder.entries.forEach { order ->
            FilterChip(
                selected = sortOrder == order,
                onClick = { onSortSelected(order) },
                label = { Text(stringResource(order.labelRes())) },
            )
        }
        VerticalDivider(modifier = Modifier.height(24.dp))
        LibraryFilter.entries.forEach { filter ->
            FilterChip(
                selected = sourceFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = { Text(stringResource(filter.labelRes())) },
            )
        }
    }
}

@Composable
private fun LibraryComicsGrid(
    comics: List<LibraryComic>,
    recentComics: List<RecentComic>,
    onOpenLibraryComic: (LibraryComic) -> Unit,
    onRemoveLibraryComic: (LibraryComic) -> Unit,
    modifier: Modifier = Modifier,
) {
    // One-shot cascade: flips true on the first frame so the first screenful staggers in; items
    // scrolled into view later (visible already true) simply appear without re-animating.
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }

    BoxWithConstraints(modifier = modifier) {
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
            gridItemsIndexed(
                items = comics,
                key = { _, comic -> "${comic.sourceType.storageName}:${comic.path}" },
            ) { index, comic ->
                StaggeredReveal(
                    visible = revealed,
                    delayMillis = min(index, MAX_STAGGER_STEPS) * STAGGER_STEP_MS,
                ) {
                    LibraryComicCard(
                        libraryComic = comic,
                        progressLabel = comic.progressText(recentComics),
                        progressFraction = libraryProgressFraction(comic, recentComics),
                        onClick = rememberThrottledAction { onOpenLibraryComic(comic) },
                        onRemove = { onRemoveLibraryComic(comic) },
                    )
                }
            }
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
private fun LibraryNoResultsContent(
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StatusContent(
        icon = Icons.Filled.SearchOff,
        title = stringResource(R.string.library_no_results_title),
        message = stringResource(R.string.library_no_results_message),
        modifier = modifier,
        actionLabel = stringResource(R.string.library_clear_filters),
        onAction = onClearFilters,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryComicCard(
    libraryComic: LibraryComic,
    progressLabel: String,
    progressFraction: Float?,
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
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shadowElevation = 2.dp,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                LibraryComicCover(libraryComic = libraryComic)
                SourceTypeBadge(
                    sourceType = libraryComic.sourceType,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        LibraryProgressBar(
            fraction = progressFraction,
            modifier = Modifier.fillMaxWidth(),
        )
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
private fun SourceTypeBadge(
    sourceType: RecentComicSourceType,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(7.dp),
        color = Color.Black.copy(alpha = 0.42f),
    ) {
        Icon(
            imageVector = sourceType.icon(),
            contentDescription = stringResource(sourceType.badgeDescriptionRes()),
            tint = Color.White,
            modifier = Modifier
                .padding(5.dp)
                .size(14.dp),
        )
    }
}

@Composable
private fun LibraryProgressBar(
    fraction: Float?,
    modifier: Modifier = Modifier,
) {
    val barHeight = 4.dp
    val barShape = RoundedCornerShape(2.dp)
    if (fraction == null) {
        Spacer(modifier = modifier.height(barHeight))
        return
    }
    Box(
        modifier = modifier
            .height(barHeight)
            .clip(barShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .clip(barShape)
                .background(MaterialTheme.colorScheme.primary),
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

private fun LibrarySortOrder.labelRes(): Int = when (this) {
    LibrarySortOrder.RecentlyAdded -> R.string.library_sort_recently_added
    LibrarySortOrder.TitleAtoZ -> R.string.library_sort_title
    LibrarySortOrder.RecentlyRead -> R.string.library_sort_recently_read
}

private fun LibraryFilter.labelRes(): Int = when (this) {
    LibraryFilter.All -> R.string.library_filter_all
    LibraryFilter.Archives -> R.string.library_filter_archives
    LibraryFilter.Folders -> R.string.library_filter_folders
}

private fun RecentComicSourceType.badgeDescriptionRes(): Int = when (this) {
    RecentComicSourceType.LocalArchive -> R.string.library_source_archive
    RecentComicSourceType.LocalFolder -> R.string.library_source_folder
}

@Composable
private fun LibraryComic.progressText(recentComics: List<RecentComic>): String =
    matchingRecentComic(recentComics)?.progressText() ?: displayName
