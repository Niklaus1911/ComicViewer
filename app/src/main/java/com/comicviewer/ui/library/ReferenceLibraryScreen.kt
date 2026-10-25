package com.comicviewer.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewComfy
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import com.comicviewer.LibraryDisplayMode
import com.comicviewer.isNewLibraryComic
import com.comicviewer.loadLibraryDisplayMode
import com.comicviewer.saveLibraryDisplayMode
import com.comicviewer.ui.common.softPress
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.comicviewer.ui.common.ReferenceScreenHeader
import com.comicviewer.ui.common.ReferenceScreenHorizontalPadding
import com.comicviewer.ui.common.StatusContent
import com.comicviewer.ui.common.tabScreen
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private const val AUTO_SCAN_RESULT_VISIBLE_MS = 5_000L
private val CoverShape = RoundedCornerShape(12.dp)
private const val CoverAspectRatio = 2f / 3f
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
fun ReferenceLibraryScreen(
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
                ReferenceScreenHeader(title = stringResource(R.string.nav_library))
                if (libraryComics.isEmpty()) {
                    LibraryAutoScanStatus(
                        autoScanState = libraryAutoScanState,
                        modifier = Modifier.padding(horizontal = ReferenceScreenHorizontalPadding, vertical = 4.dp),
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
                            .padding(horizontal = ReferenceScreenHorizontalPadding),
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
                start = ReferenceScreenHorizontalPadding,
                end = ReferenceScreenHorizontalPadding,
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
            modifier = Modifier.bleedHorizontally(ReferenceScreenHorizontalPadding),
            contentPadding = PaddingValues(horizontal = ReferenceScreenHorizontalPadding),
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
            .bleedHorizontally(ReferenceScreenHorizontalPadding)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = ReferenceScreenHorizontalPadding),
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

private class ComicCardState(
    val comic: LibraryComic,
    val recentComic: RecentComic?,
    val favorite: Boolean,
    val isNew: Boolean,
    val pageCount: Int?,
    val progress: Float?,
) {
    val completed: Boolean
        get() = progress != null && progress >= 1f

    val inProgress: Boolean
        get() = progress != null && !completed
}

@Composable
private fun ComicCardState.metaText(): String {
    val recent = recentComic
    return when {
        completed -> stringResource(R.string.library_filter_completed)
        inProgress && recent != null -> stringResource(
            R.string.page_progress_spaced,
            coercePageIndex(recent.lastPageIndex, recent.pageCount) + 1,
            recent.pageCount,
        )
        pageCount != null -> pluralStringResource(R.plurals.comic_pages, pageCount, pageCount)
        else -> comic.displayName
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.comicClickable(
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
): Modifier {
    val haptics = LocalHapticFeedback.current
    return softPress(interactionSource).combinedClickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick,
        onLongClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onLongClick()
        },
        onLongClickLabel = stringResource(R.string.more_options),
    )
}

/** Cover at comic proportions with New, favorite, finished and progress overlays. */
@Composable
private fun ComicCoverArt(
    card: ComicCardState,
    modifier: Modifier = Modifier,
    showBadges: Boolean = true,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(CoverAspectRatio)
            .shadow(elevation = 3.dp, shape = CoverShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, CoverShape)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), CoverShape)
            .clip(CoverShape),
    ) {
        LibraryComicCover(libraryComic = card.comic)
        overlay()
        if (showBadges) {
            if (card.isNew) {
                NewBadge(modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
            }
            Column(
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (card.completed) {
                    CoverIconBadge(
                        icon = Icons.Filled.Check,
                        contentDescription = stringResource(R.string.library_filter_completed),
                        emphasized = true,
                    )
                }
                if (card.favorite) {
                    CoverIconBadge(
                        icon = Icons.Filled.Bookmark,
                        contentDescription = stringResource(R.string.library_filter_favorites),
                    )
                }
            }
        }
        if (card.inProgress) {
            LinearProgressIndicator(
                progress = { card.progress ?: 0f },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Black.copy(alpha = 0.45f),
                strokeCap = StrokeCap.Butt,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
private fun NewBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Text(
            text = stringResource(R.string.library_badge_new),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun CoverIconBadge(
    icon: ImageVector,
    contentDescription: String,
    emphasized: Boolean = false,
) {
    Surface(
        shape = CircleShape,
        color = if (emphasized) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.55f),
        contentColor = if (emphasized) MaterialTheme.colorScheme.onPrimary else Color.White,
    ) {
        Icon(icon, contentDescription, modifier = Modifier.padding(4.dp).size(14.dp))
    }
}

@Composable
private fun ComicGridCard(
    card: ComicCardState,
    onOpen: () -> Unit,
    onActions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(modifier = modifier.comicClickable(interactionSource, onOpen, onActions)) {
        ComicCoverArt(card = card)
        Spacer(Modifier.height(8.dp))
        Text(
            text = card.comic.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = card.metaText(),
            style = MaterialTheme.typography.bodySmall,
            color = if (card.completed || card.inProgress) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ComicCompactCard(
    card: ComicCardState,
    onOpen: () -> Unit,
    onActions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    ComicCoverArt(
        card = card,
        modifier = modifier.comicClickable(interactionSource, onOpen, onActions),
        overlay = {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))),
            )
            Text(
                text = card.comic.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 7.dp, end = 7.dp, bottom = 9.dp),
            )
        },
    )
}

@Composable
private fun ComicListRow(
    card: ComicCardState,
    onOpen: () -> Unit,
    onActions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier.comicClickable(interactionSource, onOpen, onActions),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ComicCoverArt(card = card, modifier = Modifier.width(58.dp), showBadges = false)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = card.comic.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (card.isNew) NewBadge()
                }
                Text(
                    text = card.metaText(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (card.inProgress) {
                    LibraryProgressLine(progress = card.progress ?: 0f)
                }
            }
            if (card.favorite) {
                Icon(
                    imageVector = Icons.Filled.Bookmark,
                    contentDescription = stringResource(R.string.library_filter_favorites),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            if (card.completed) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.library_filter_completed),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun LibraryProgressLine(progress: Float) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .weight(1f)
                .height(4.dp),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            drawStopIndicator = {},
        )
        Text(
            text = stringResource(R.string.comic_progress_percent, (progress * 100).roundToInt()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddComicsSheet(
    onDismiss: () -> Unit,
    onImportArchives: () -> Unit,
    onAutoScanLibrary: () -> Unit,
    onScanFolder: () -> Unit,
    onAddFolderAsComic: () -> Unit,
    onOpenArchiveWithoutAdding: () -> Unit,
    onOpenFolderWithoutAdding: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(
                text = stringResource(R.string.add_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            SheetActionRow(
                icon = Icons.Filled.Archive,
                title = stringResource(R.string.import_archive_title),
                description = stringResource(R.string.import_archive_description),
                onClick = { onDismiss(); onImportArchives() },
            )
            SheetActionRow(
                icon = Icons.Filled.Radar,
                title = stringResource(R.string.library_auto_scan),
                description = stringResource(R.string.add_auto_discover_description),
                onClick = { onDismiss(); onAutoScanLibrary() },
            )
            SheetActionRow(
                icon = Icons.Filled.Folder,
                title = stringResource(R.string.import_folder_title),
                description = stringResource(R.string.import_folder_description),
                onClick = { onDismiss(); onScanFolder() },
            )
            SheetActionRow(
                icon = Icons.Filled.CreateNewFolder,
                title = stringResource(R.string.add_folder_comic_title),
                description = stringResource(R.string.add_folder_comic_description),
                onClick = { onDismiss(); onAddFolderAsComic() },
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
            Text(
                text = stringResource(R.string.add_open_without_adding),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
            )
            SheetActionRow(
                icon = Icons.Filled.Unarchive,
                title = stringResource(R.string.open_archive),
                onClick = { onDismiss(); onOpenArchiveWithoutAdding() },
            )
            SheetActionRow(
                icon = Icons.Filled.FolderOpen,
                title = stringResource(R.string.open_folder),
                onClick = { onDismiss(); onOpenFolderWithoutAdding() },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComicActionsSheet(
    comic: LibraryComic,
    recentComic: RecentComic?,
    details: LibraryComicDetails?,
    favorite: Boolean,
    isProUnlocked: Boolean,
    onDismiss: () -> Unit,
    onRead: () -> Unit,
    onDetails: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEditTags: () -> Unit,
    onRemove: () -> Unit,
) {
    val progress = libraryProgressFraction(comic, listOfNotNull(recentComic))
    val completed = progress != null && progress >= 1f
    val pageCount = resolvedComicPageCount(details, recentComic)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier
                        .width(52.dp)
                        .aspectRatio(0.72f),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    LibraryComicCover(libraryComic = comic)
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = comic.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle = when {
                        completed -> stringResource(R.string.library_filter_completed)
                        progress != null -> stringResource(R.string.comic_progress_percent, (progress * 100).roundToInt())
                        pageCount != null -> pluralStringResource(R.plurals.comic_pages, pageCount, pageCount)
                        else -> comic.displayName
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
            SheetActionRow(
                icon = Icons.Filled.PlayArrow,
                title = stringResource(
                    if (progress != null && !completed) R.string.continue_reading else R.string.start_reading,
                ),
                onClick = { onDismiss(); onRead() },
            )
            SheetActionRow(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.comic_details_action),
                onClick = { onDismiss(); onDetails() },
            )
            if (!completed) {
                SheetActionRow(
                    icon = Icons.Filled.CheckCircle,
                    title = stringResource(R.string.mark_as_read),
                    onClick = { onDismiss(); onMarkRead() },
                )
            }
            if (recentComic != null) {
                SheetActionRow(
                    icon = Icons.Filled.RemoveDone,
                    title = stringResource(R.string.mark_as_unread),
                    onClick = { onDismiss(); onMarkUnread() },
                )
            }
            SheetActionRow(
                icon = Icons.Filled.Bookmark,
                title = stringResource(if (favorite) R.string.library_remove_favorite else R.string.library_add_favorite),
                locked = !isProUnlocked,
                onClick = { onDismiss(); onToggleFavorite() },
            )
            SheetActionRow(
                icon = Icons.AutoMirrored.Filled.Label,
                title = stringResource(R.string.library_edit_tags),
                locked = !isProUnlocked,
                onClick = { onDismiss(); onEditTags() },
            )
            SheetActionRow(
                icon = Icons.Filled.Delete,
                title = stringResource(R.string.remove_from_library_action),
                destructive = true,
                onClick = { onDismiss(); onRemove() },
            )
        }
    }
}

@Composable
private fun SheetActionRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    description: String? = null,
    locked: Boolean = false,
    destructive: Boolean = false,
) {
    val contentColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = if (description == null) 14.dp else 12.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = contentColor)
            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (locked) {
            Icon(
                imageVector = Icons.Filled.WorkspacePremium,
                contentDescription = stringResource(R.string.pro_badge),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun LibraryFilterDialog(
    sourceFilter: LibraryFilter,
    tagFilter: String?,
    tags: List<String>,
    isProUnlocked: Boolean,
    onSource: (LibraryFilter) -> Unit,
    onTag: (String?) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_filters_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.library_filter_type_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LibraryFilter.entries.forEach { item ->
                        FilterChip(
                            selected = item == sourceFilter,
                            onClick = { onSource(item) },
                            label = { Text(stringResource(item.labelRes())) },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.library_tags_input_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (tags.isEmpty()) {
                    Text(
                        text = stringResource(
                            if (isProUnlocked) R.string.library_no_tags_message else R.string.pro_benefit_tags_message,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        tags.forEach { tag ->
                            FilterChip(
                                selected = tagFilter == tag,
                                onClick = { onTag(if (tagFilter == tag) null else tag) },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.Label, null, modifier = Modifier.size(16.dp))
                                },
                                label = { Text(tag) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onClear) { Text(stringResource(R.string.library_clear_filters)) } },
    )
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

private fun LibraryFilter.labelRes(): Int = when (this) {
    LibraryFilter.All -> R.string.library_filter_all
    LibraryFilter.Archives -> R.string.library_filter_archives
    LibraryFilter.Folders -> R.string.library_filter_folders
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
