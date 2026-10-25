package com.comicviewer.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewComfy
import androidx.compose.ui.platform.LocalContext
import com.comicviewer.LibraryDisplayMode
import com.comicviewer.isNewLibraryComic
import com.comicviewer.loadLibraryDisplayMode
import com.comicviewer.saveLibraryDisplayMode
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.comicviewer.LibraryAutoScanState
import com.comicviewer.LibraryComic
import com.comicviewer.LibraryComicDetails
import com.comicviewer.LibraryComicMetadata
import com.comicviewer.LibraryFilter
import com.comicviewer.LibraryProgressFilter
import com.comicviewer.LibrarySortOrder
import com.comicviewer.R
import com.comicviewer.RecentComic
import com.comicviewer.availableLibraryTags
import com.comicviewer.billing.ProBillingState
import com.comicviewer.continueReadingComics
import com.comicviewer.coercePageIndex
import com.comicviewer.detailsFor
import com.comicviewer.isTabletWidth
import com.comicviewer.libraryProgressFraction
import com.comicviewer.libraryView
import com.comicviewer.matchingRecentComic
import com.comicviewer.metadataFor
import com.comicviewer.resolvedComicPageCount
import com.comicviewer.resolvedLibraryMetadata
import com.comicviewer.resolvedLibraryProgressFilter
import com.comicviewer.resolvedLibraryTagFilter
import com.comicviewer.ui.common.ScreenHeader
import com.comicviewer.ui.common.ScreenHorizontalPadding
import com.comicviewer.ui.common.StatusContent
import com.comicviewer.ui.common.tabScreen
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private const val AUTO_SCAN_RESULT_VISIBLE_MS = 5_000L
private val ContinueReadingCardWidth = 272.dp
private val FabClearance = 96.dp
private val ProgressFilterChips = listOf(
    LibraryProgressFilter.All,
    LibraryProgressFilter.InProgress,
    LibraryProgressFilter.Unread,
    LibraryProgressFilter.Completed,
    LibraryProgressFilter.Favorites,
)

@Composable
fun LibraryScreen(
    libraryComics: List<LibraryComic>,
    libraryMetadata: List<LibraryComicMetadata>,
    libraryDetails: List<LibraryComicDetails>,
    libraryAutoScanState: LibraryAutoScanState,
    recentComics: List<RecentComic>,
    proBillingState: ProBillingState,
    onImportArchives: () -> Unit,
    onAutoScanLibrary: () -> Unit,
    onScanFolder: () -> Unit,
    onAddFolderAsComic: () -> Unit,
    onOpenArchiveWithoutAdding: () -> Unit,
    onOpenFolderWithoutAdding: () -> Unit,
    onClearAutoScanResult: () -> Unit,
    onBuyPro: () -> Unit,
    onRestorePro: () -> Unit,
    isDebugBuild: Boolean,
    debugProUnlocked: Boolean,
    onDebugProUnlockedChanged: (Boolean) -> Unit,
    onOpenLibraryComic: (LibraryComic) -> Unit,
    onReadLibraryComic: (LibraryComic) -> Unit,
    onMarkRead: (LibraryComic) -> Unit,
    onMarkUnread: (LibraryComic) -> Unit,
    onRemoveLibraryComic: (LibraryComic) -> Unit,
    onToggleFavorite: (LibraryComic) -> Unit,
    onSetTags: (LibraryComic, List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var displayMode by remember { mutableStateOf(loadLibraryDisplayMode(context)) }
    var query by rememberSaveable { mutableStateOf("") }
    var sortOrder by rememberSaveable { mutableStateOf(LibrarySortOrder.RecentlyAdded) }
    var sourceFilter by rememberSaveable { mutableStateOf(LibraryFilter.All) }
    var progressFilter by rememberSaveable { mutableStateOf(LibraryProgressFilter.All) }
    var tagFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var showProDialog by remember { mutableStateOf(false) }
    var actionComic by remember { mutableStateOf<LibraryComic?>(null) }
    var removeComic by remember { mutableStateOf<LibraryComic?>(null) }
    var tagComic by remember { mutableStateOf<LibraryComic?>(null) }
    val isProUnlocked = proBillingState.isProUnlocked
    val effectiveMetadata = resolvedLibraryMetadata(libraryMetadata, isProUnlocked)
    val effectiveProgress = resolvedLibraryProgressFilter(progressFilter, isProUnlocked)
    val effectiveTag = resolvedLibraryTagFilter(tagFilter, isProUnlocked)
    val tags = remember(effectiveMetadata) { availableLibraryTags(effectiveMetadata) }
    val visible = remember(
        libraryComics,
        recentComics,
        libraryDetails,
        effectiveMetadata,
        query,
        sortOrder,
        sourceFilter,
        effectiveProgress,
        effectiveTag,
    ) {
        libraryView(
            comics = libraryComics,
            recentComics = recentComics,
            query = query,
            filter = sourceFilter,
            order = sortOrder,
            metadata = effectiveMetadata,
            progressFilter = effectiveProgress,
            tagFilter = effectiveTag,
            details = libraryDetails,
        )
    }
    val continueReading = remember(libraryComics, recentComics) {
        continueReadingComics(libraryComics, recentComics)
    }
    val narrowed = query.isNotBlank() ||
        effectiveProgress != LibraryProgressFilter.All ||
        sourceFilter != LibraryFilter.All ||
        effectiveTag != null
    val gridState = rememberLazyGridState()

    fun clearFilters() {
        query = ""
        progressFilter = LibraryProgressFilter.All
        sourceFilter = LibraryFilter.All
        tagFilter = null
    }

    LaunchedEffect(libraryAutoScanState) {
        if (libraryAutoScanState is LibraryAutoScanState.Complete) {
            delay(AUTO_SCAN_RESULT_VISIBLE_MS)
            onClearAutoScanResult()
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.tabScreen),
            ) {
                ScreenHeader(title = stringResource(R.string.nav_library))
                if (libraryComics.isEmpty()) {
                    LibraryAutoScanStatus(
                        autoScanState = libraryAutoScanState,
                        modifier = Modifier.padding(horizontal = ScreenHorizontalPadding, vertical = 4.dp),
                    )
                    LibraryEmptyContent(
                        onStartAddToLibrary = { showAddSheet = true },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    LibrarySearchField(
                        query = query,
                        onQueryChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ScreenHorizontalPadding),
                    )
                    LibraryHomeGrid(
                        gridState = gridState,
                        comics = visible,
                        continueReading = if (narrowed) emptyList() else continueReading,
                        libraryMetadata = effectiveMetadata,
                        libraryDetails = libraryDetails,
                        recentComics = recentComics,
                        libraryAutoScanState = libraryAutoScanState,
                        progressFilter = effectiveProgress,
                        sortOrder = sortOrder,
                        displayMode = displayMode,
                        extraFiltersActive = sourceFilter != LibraryFilter.All || effectiveTag != null,
                        isProUnlocked = isProUnlocked,
                        onProgressFilter = { filter ->
                            if (isProUnlocked || filter == LibraryProgressFilter.All) {
                                progressFilter = filter
                            } else {
                                showProDialog = true
                            }
                        },
                        onSortOrder = { sortOrder = it },
                        onDisplayMode = { mode ->
                            displayMode = mode
                            saveLibraryDisplayMode(context, mode)
                        },
                        onShowFilters = { showFilters = true },
                        onClearFilters = ::clearFilters,
                        onOpen = onOpenLibraryComic,
                        onResume = onReadLibraryComic,
                        onActions = { actionComic = it },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (libraryComics.isNotEmpty()) {
                val expanded by remember(gridState) {
                    derivedStateOf { gridState.firstVisibleItemIndex == 0 }
                }
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.add_comic)) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    expanded = expanded,
                    onClick = { showAddSheet = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                )
            }
        }
    }

    if (showAddSheet) {
        AddComicsSheet(
            onDismiss = { showAddSheet = false },
            onImportArchives = onImportArchives,
            onAutoScanLibrary = onAutoScanLibrary,
            onScanFolder = onScanFolder,
            onAddFolderAsComic = onAddFolderAsComic,
            onOpenArchiveWithoutAdding = onOpenArchiveWithoutAdding,
            onOpenFolderWithoutAdding = onOpenFolderWithoutAdding,
        )
    }

    if (showFilters) {
        LibraryFilterDialog(
            sourceFilter = sourceFilter,
            tagFilter = effectiveTag,
            tags = tags,
            isProUnlocked = isProUnlocked,
            onSource = { sourceFilter = it },
            onTag = { if (isProUnlocked) tagFilter = it else showProDialog = true },
            onClear = {
                sourceFilter = LibraryFilter.All
                tagFilter = null
            },
            onDismiss = { showFilters = false },
        )
    }

    actionComic?.let { comic ->
        val recentComic = comic.matchingRecentComic(recentComics)
        ComicActionsSheet(
            comic = comic,
            recentComic = recentComic,
            details = detailsFor(comic, libraryDetails),
            favorite = metadataFor(comic, effectiveMetadata).favorite,
            isProUnlocked = isProUnlocked,
            onDismiss = { actionComic = null },
            onRead = { onReadLibraryComic(comic) },
            onDetails = { onOpenLibraryComic(comic) },
            onMarkRead = { onMarkRead(comic) },
            onMarkUnread = { onMarkUnread(comic) },
            onToggleFavorite = { if (isProUnlocked) onToggleFavorite(comic) else showProDialog = true },
            onEditTags = { if (isProUnlocked) tagComic = comic else showProDialog = true },
            onRemove = { removeComic = comic },
        )
    }

    tagComic?.let { comic ->
        var text by remember(comic) { mutableStateOf(metadataFor(comic, libraryMetadata).tags.joinToString(", ")) }
        AlertDialog(
            onDismissRequest = { tagComic = null },
            title = { Text(stringResource(R.string.library_edit_tags)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.library_tags_input_label)) },
                    supportingText = { Text(stringResource(R.string.library_tags_input_helper)) },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onSetTags(comic, text.split(','))
                    tagComic = null
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { tagComic = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    removeComic?.let { comic ->
        AlertDialog(
            onDismissRequest = { removeComic = null },
            title = { Text(stringResource(R.string.remove_from_library_title)) },
            text = { Text(stringResource(R.string.remove_from_library_message, comic.title)) },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveLibraryComic(comic)
                    removeComic = null
                }) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = { TextButton(onClick = { removeComic = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (showProDialog) {
        ProUpgradeDialog(
            billingState = proBillingState,
            onBuyPro = onBuyPro,
            onRestorePro = onRestorePro,
            isDebugBuild = isDebugBuild,
            debugProUnlocked = debugProUnlocked,
            onDebugProUnlockedChanged = onDebugProUnlockedChanged,
            onDismiss = { showProDialog = false },
        )
    }
}

@Composable
private fun LibrarySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.library_clear_search))
                }
            }
        },
        placeholder = { Text(stringResource(R.string.library_search_label)) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        shape = RoundedCornerShape(28.dp),
    )
}

@Composable
private fun LibraryHomeGrid(
    gridState: LazyGridState,
    comics: List<LibraryComic>,
    continueReading: List<LibraryComic>,
    libraryMetadata: List<LibraryComicMetadata>,
    libraryDetails: List<LibraryComicDetails>,
    recentComics: List<RecentComic>,
    libraryAutoScanState: LibraryAutoScanState,
    progressFilter: LibraryProgressFilter,
    sortOrder: LibrarySortOrder,
    displayMode: LibraryDisplayMode,
    extraFiltersActive: Boolean,
    isProUnlocked: Boolean,
    onProgressFilter: (LibraryProgressFilter) -> Unit,
    onSortOrder: (LibrarySortOrder) -> Unit,
    onDisplayMode: (LibraryDisplayMode) -> Unit,
    onShowFilters: () -> Unit,
    onClearFilters: () -> Unit,
    onOpen: (LibraryComic) -> Unit,
    onResume: (LibraryComic) -> Unit,
    onActions: (LibraryComic) -> Unit,
    modifier: Modifier = Modifier,
) {
    val nowMs = remember(comics, recentComics) { System.currentTimeMillis() }
    BoxWithConstraints(modifier = modifier) {
        val tablet = isTabletWidth(maxWidth.value.roundToInt())
        val fullSpan: (LazyGridItemSpanScope.() -> GridItemSpan) = { GridItemSpan(maxLineSpan) }
        val columns = when (displayMode) {
            LibraryDisplayMode.Grid -> GridCells.Adaptive(if (tablet) 150.dp else 104.dp)
            LibraryDisplayMode.CompactGrid -> GridCells.Adaptive(if (tablet) 120.dp else 84.dp)
            LibraryDisplayMode.List -> if (tablet) GridCells.Adaptive(360.dp) else GridCells.Fixed(1)
        }
        val columnSpacing = when (displayMode) {
            LibraryDisplayMode.Grid -> 14.dp
            LibraryDisplayMode.CompactGrid -> 8.dp
            LibraryDisplayMode.List -> 12.dp
        }
        val rowSpacing = when (displayMode) {
            LibraryDisplayMode.Grid -> 20.dp
            LibraryDisplayMode.CompactGrid -> 8.dp
            LibraryDisplayMode.List -> 10.dp
        }
        LazyVerticalGrid(
            state = gridState,
            columns = columns,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = ScreenHorizontalPadding,
                end = ScreenHorizontalPadding,
                top = 12.dp,
                bottom = FabClearance,
            ),
            horizontalArrangement = Arrangement.spacedBy(columnSpacing),
            verticalArrangement = Arrangement.spacedBy(rowSpacing),
        ) {
            if (libraryAutoScanState != LibraryAutoScanState.Idle) {
                item(key = "auto_scan", span = fullSpan) {
                    LibraryAutoScanStatus(autoScanState = libraryAutoScanState)
                }
            }
            if (continueReading.isNotEmpty()) {
                item(key = "continue_reading", span = fullSpan) {
                    ContinueReadingShelf(
                        comics = continueReading,
                        recentComics = recentComics,
                        onResume = onResume,
                        onActions = onActions,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
            }
            item(key = "controls", span = fullSpan) {
                Column(modifier = Modifier.padding(bottom = 6.dp)) {
                    ProgressFilterChipRow(
                        selected = progressFilter,
                        isProUnlocked = isProUnlocked,
                        onSelected = onProgressFilter,
                    )
                    LibrarySummaryRow(
                        count = comics.size,
                        sortOrder = sortOrder,
                        displayMode = displayMode,
                        extraFiltersActive = extraFiltersActive,
                        onSortOrder = onSortOrder,
                        onDisplayMode = onDisplayMode,
                        onShowFilters = onShowFilters,
                    )
                }
            }
            if (comics.isEmpty()) {
                item(key = "no_results", span = fullSpan) {
                    StatusContent(
                        icon = Icons.Filled.FilterList,
                        title = stringResource(R.string.library_no_results_title),
                        message = stringResource(R.string.library_no_results_message),
                        actionLabel = stringResource(R.string.library_clear_filters),
                        onAction = onClearFilters,
                        modifier = Modifier.padding(vertical = 32.dp),
                    )
                }
            } else {
                items(comics, key = { "${it.sourceType.storageName}:${it.path}" }) { comic ->
                    val recentComic = comic.matchingRecentComic(recentComics)
                    val card = ComicCardState(
                        comic = comic,
                        recentComic = recentComic,
                        favorite = metadataFor(comic, libraryMetadata).favorite,
                        isNew = isNewLibraryComic(comic, recentComics, nowMs),
                        pageCount = resolvedComicPageCount(detailsFor(comic, libraryDetails), recentComic),
                        progress = libraryProgressFraction(comic, listOfNotNull(recentComic)),
                    )
                    val itemModifier = Modifier.animateItem()
                    when (displayMode) {
                        LibraryDisplayMode.Grid -> ComicGridCard(
                            card = card,
                            onOpen = { onOpen(comic) },
                            onActions = { onActions(comic) },
                            modifier = itemModifier,
                        )
                        LibraryDisplayMode.CompactGrid -> ComicCompactCard(
                            card = card,
                            onOpen = { onOpen(comic) },
                            onActions = { onActions(comic) },
                            modifier = itemModifier,
                        )
                        LibraryDisplayMode.List -> ComicListRow(
                            card = card,
                            onOpen = { onOpen(comic) },
                            onActions = { onActions(comic) },
                            modifier = itemModifier,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinueReadingShelf(
    comics: List<LibraryComic>,
    recentComics: List<RecentComic>,
    onResume: (LibraryComic) -> Unit,
    onActions: (LibraryComic) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.continue_reading),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        LazyRow(
            modifier = Modifier.bleedHorizontally(ScreenHorizontalPadding),
            contentPadding = PaddingValues(horizontal = ScreenHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(comics, key = { "${it.sourceType.storageName}:${it.path}" }) { comic ->
                ContinueReadingCard(
                    comic = comic,
                    recentComic = comic.matchingRecentComic(recentComics),
                    onResume = { onResume(comic) },
                    onActions = { onActions(comic) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueReadingCard(
    comic: LibraryComic,
    recentComic: RecentComic?,
    onResume: () -> Unit,
    onActions: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val progress = libraryProgressFraction(comic, listOfNotNull(recentComic)) ?: 0f
    Surface(
        modifier = Modifier
            .width(ContinueReadingCardWidth)
            .combinedClickable(
                onClick = onResume,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onActions()
                },
                onLongClickLabel = stringResource(R.string.more_options),
            ),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier
                    .width(62.dp)
                    .aspectRatio(0.72f),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                LibraryComicCover(libraryComic = comic)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = comic.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                recentComic?.takeIf { it.pageCount > 0 }?.let { recent ->
                    Text(
                        text = stringResource(
                            R.string.page_progress_spaced,
                            coercePageIndex(recent.lastPageIndex, recent.pageCount) + 1,
                            recent.pageCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LibraryProgressLine(progress = progress)
            }
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.continue_reading),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ProgressFilterChipRow(
    selected: LibraryProgressFilter,
    isProUnlocked: Boolean,
    onSelected: (LibraryProgressFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .bleedHorizontally(ScreenHorizontalPadding)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = ScreenHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ProgressFilterChips.forEach { filter ->
            val locked = !isProUnlocked && filter != LibraryProgressFilter.All
            FilterChip(
                selected = selected == filter,
                onClick = { onSelected(filter) },
                label = { Text(stringResource(filter.chipLabelRes())) },
                leadingIcon = when {
                    selected == filter -> {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    }
                    locked -> {
                        {
                            Icon(
                                Icons.Filled.WorkspacePremium,
                                contentDescription = stringResource(R.string.pro_badge),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    else -> null
                },
            )
        }
    }
}

@Composable
private fun LibrarySummaryRow(
    count: Int,
    sortOrder: LibrarySortOrder,
    displayMode: LibraryDisplayMode,
    extraFiltersActive: Boolean,
    onSortOrder: (LibrarySortOrder) -> Unit,
    onDisplayMode: (LibraryDisplayMode) -> Unit,
    onShowFilters: () -> Unit,
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var viewMenuExpanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = pluralStringResource(R.plurals.library_comic_count, count, count),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Box {
            TextButton(onClick = { sortMenuExpanded = true }) {
                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(sortOrder.labelRes()))
            }
            DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                LibrarySortOrder.entries.forEach { order ->
                    DropdownMenuItem(
                        text = { Text(stringResource(order.labelRes())) },
                        trailingIcon = if (order == sortOrder) {
                            { Icon(Icons.Filled.Check, contentDescription = null) }
                        } else {
                            null
                        },
                        onClick = {
                            sortMenuExpanded = false
                            onSortOrder(order)
                        },
                    )
                }
            }
        }
        Box {
            IconButton(onClick = { viewMenuExpanded = true }) {
                Icon(displayMode.icon(), contentDescription = stringResource(R.string.library_view_title))
            }
            DropdownMenu(expanded = viewMenuExpanded, onDismissRequest = { viewMenuExpanded = false }) {
                LibraryDisplayMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(stringResource(mode.labelRes())) },
                        leadingIcon = { Icon(mode.icon(), contentDescription = null) },
                        trailingIcon = if (mode == displayMode) {
                            { Icon(Icons.Filled.Check, contentDescription = null) }
                        } else {
                            null
                        },
                        onClick = {
                            viewMenuExpanded = false
                            onDisplayMode(mode)
                        },
                    )
                }
            }
        }
        IconButton(onClick = onShowFilters) {
            BadgedBox(badge = { if (extraFiltersActive) Badge() }) {
                Icon(Icons.Filled.Tune, contentDescription = stringResource(R.string.library_filters_title))
            }
        }
    }
}

/** Lets a full-span row scroll edge to edge while its content still lines up with the grid padding. */
private fun Modifier.bleedHorizontally(bleed: Dp): Modifier = layout { measurable, constraints ->
    val bleedPx = bleed.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + bleedPx * 2,
            maxWidth = constraints.maxWidth + bleedPx * 2,
        ),
    )
    layout(constraints.maxWidth, placeable.height) {
        placeable.place(-bleedPx, 0)
    }
}

private fun LibraryProgressFilter.chipLabelRes(): Int = when (this) {
    LibraryProgressFilter.All -> R.string.library_filter_all
    LibraryProgressFilter.Favorites -> R.string.library_filter_favorites
    LibraryProgressFilter.Unread -> R.string.library_filter_unread
    LibraryProgressFilter.InProgress -> R.string.library_filter_in_progress
    LibraryProgressFilter.Completed -> R.string.library_filter_completed
}

private fun LibrarySortOrder.labelRes(): Int = when (this) {
    LibrarySortOrder.RecentlyAdded -> R.string.library_sort_recently_added
    LibrarySortOrder.TitleAtoZ -> R.string.library_sort_title
    LibrarySortOrder.RecentlyRead -> R.string.library_sort_recently_read
}

private fun LibraryDisplayMode.labelRes(): Int = when (this) {
    LibraryDisplayMode.Grid -> R.string.library_view_grid
    LibraryDisplayMode.CompactGrid -> R.string.library_view_compact
    LibraryDisplayMode.List -> R.string.library_view_list
}

private fun LibraryDisplayMode.icon(): ImageVector = when (this) {
    LibraryDisplayMode.Grid -> Icons.Filled.GridView
    LibraryDisplayMode.CompactGrid -> Icons.Filled.ViewComfy
    LibraryDisplayMode.List -> Icons.AutoMirrored.Filled.ViewList
}
