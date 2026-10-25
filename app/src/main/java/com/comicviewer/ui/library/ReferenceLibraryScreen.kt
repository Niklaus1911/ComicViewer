package com.comicviewer.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comicviewer.ui.common.tabScreen
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
import com.comicviewer.detailsFor
import com.comicviewer.isTabletWidth
import com.comicviewer.libraryProgressFraction
import com.comicviewer.libraryView
import com.comicviewer.matchingRecentComic
import com.comicviewer.metadataFor
import com.comicviewer.resolvedLibraryMetadata
import com.comicviewer.resolvedLibraryProgressFilter
import com.comicviewer.resolvedLibraryTagFilter
import com.comicviewer.resolvedComicPageCount
import com.comicviewer.ui.common.ReferenceScreenHeader
import com.comicviewer.ui.common.StatusContent
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private enum class ReferenceLibraryCategory { All, Recent, Favorites }
private const val AUTO_SCAN_RESULT_VISIBLE_MS = 5_000L

@Composable
fun ReferenceLibraryScreen(
    libraryComics: List<LibraryComic>,
    libraryMetadata: List<LibraryComicMetadata>,
    libraryDetails: List<LibraryComicDetails>,
    libraryAutoScanState: LibraryAutoScanState,
    recentComics: List<RecentComic>,
    proBillingState: ProBillingState,
    onOpenSearch: () -> Unit,
    onStartAddToLibrary: () -> Unit,
    onAutoScanLibrary: () -> Unit,
    onClearAutoScanResult: () -> Unit,
    onBuyPro: () -> Unit,
    onRestorePro: () -> Unit,
    isDebugBuild: Boolean,
    debugProUnlocked: Boolean,
    onDebugProUnlockedChanged: (Boolean) -> Unit,
    onOpenLibraryComic: (LibraryComic) -> Unit,
    onRemoveLibraryComic: (LibraryComic) -> Unit,
    onToggleFavorite: (LibraryComic) -> Unit,
    onSetTags: (LibraryComic, List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedCategory by rememberSaveable { mutableStateOf(ReferenceLibraryCategory.All) }
    var sortOrder by rememberSaveable { mutableStateOf(LibrarySortOrder.RecentlyAdded) }
    var sourceFilter by rememberSaveable { mutableStateOf(LibraryFilter.All) }
    var progressFilter by rememberSaveable { mutableStateOf(LibraryProgressFilter.All) }
    var tagFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
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
    val base = remember(
        libraryComics,
        recentComics,
        effectiveMetadata,
        sortOrder,
        sourceFilter,
        effectiveProgress,
        effectiveTag,
    ) {
        libraryView(
            comics = libraryComics,
            recentComics = recentComics,
            query = "",
            filter = sourceFilter,
            order = sortOrder,
            metadata = effectiveMetadata,
            progressFilter = effectiveProgress,
            tagFilter = effectiveTag,
        )
    }
    val visible = remember(base, selectedCategory, recentComics, effectiveMetadata) {
        when (selectedCategory) {
            ReferenceLibraryCategory.All -> base
            ReferenceLibraryCategory.Recent -> base
                .filter { it.matchingRecentComic(recentComics) != null }
                .sortedByDescending { it.matchingRecentComic(recentComics)?.lastOpenedAt ?: Long.MIN_VALUE }
            ReferenceLibraryCategory.Favorites -> base.filter { metadataFor(it, effectiveMetadata).favorite }
        }
    }

    LaunchedEffect(isProUnlocked) {
        if (!isProUnlocked && selectedCategory == ReferenceLibraryCategory.Favorites) {
            selectedCategory = ReferenceLibraryCategory.All
        }
    }

    LaunchedEffect(libraryAutoScanState) {
        if (libraryAutoScanState is LibraryAutoScanState.Complete) {
            delay(AUTO_SCAN_RESULT_VISIBLE_MS)
            onClearAutoScanResult()
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.tabScreen),
        ) {
            ReferenceScreenHeader(
                title = stringResource(R.string.nav_library),
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Filled.Search, stringResource(R.string.nav_search))
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options))
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.add_comic)) },
                                leadingIcon = { Icon(Icons.Filled.Add, null) },
                                onClick = {
                                    menuExpanded = false
                                    onStartAddToLibrary()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.library_auto_scan)) },
                                leadingIcon = { Icon(Icons.Filled.Radar, null) },
                                onClick = {
                                    menuExpanded = false
                                    onAutoScanLibrary()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.library_filters)) },
                                leadingIcon = { Icon(Icons.Filled.FilterList, null) },
                                onClick = {
                                    menuExpanded = false
                                    showFilters = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(if (isProUnlocked) R.string.pro_unlocked else R.string.pro_upgrade)) },
                                leadingIcon = { Icon(Icons.Filled.WorkspacePremium, null) },
                                onClick = {
                                    menuExpanded = false
                                    showProDialog = true
                                },
                            )
                        }
                    }
                },
            )
            ReferenceLibraryTabs(
                selected = selectedCategory,
                onSelected = { category ->
                    if (category == ReferenceLibraryCategory.Favorites && !isProUnlocked) {
                        showProDialog = true
                    } else {
                        selectedCategory = category
                    }
                },
            )
            LibraryAutoScanStatus(
                autoScanState = libraryAutoScanState,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            when {
                libraryComics.isEmpty() -> LibraryEmptyContent(
                    onStartAddToLibrary = onStartAddToLibrary,
                    modifier = Modifier.weight(1f),
                )
                visible.isEmpty() -> StatusContent(
                    icon = Icons.Filled.FilterList,
                    title = stringResource(R.string.library_no_results_title),
                    message = stringResource(R.string.library_no_results_message),
                    modifier = Modifier.weight(1f),
                )
                else -> ReferenceLibraryGrid(
                    comics = visible,
                    libraryMetadata = effectiveMetadata,
                    libraryDetails = libraryDetails,
                    recentComics = recentComics,
                    onOpen = onOpenLibraryComic,
                    onToggleFavorite = { comic ->
                        if (isProUnlocked) onToggleFavorite(comic) else showProDialog = true
                    },
                    onActions = { actionComic = it },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (showFilters) {
        ReferenceLibraryFilterDialog(
            sortOrder = sortOrder,
            sourceFilter = sourceFilter,
            progressFilter = effectiveProgress,
            tagFilter = effectiveTag,
            tags = tags,
            isProUnlocked = isProUnlocked,
            onSort = { sortOrder = it },
            onSource = { sourceFilter = it },
            onProgress = { if (isProUnlocked || it == LibraryProgressFilter.All) progressFilter = it else showProDialog = true },
            onTag = { if (isProUnlocked) tagFilter = it else showProDialog = true },
            onDismiss = { showFilters = false },
        )
    }

    actionComic?.let { comic ->
        AlertDialog(
            onDismissRequest = { actionComic = null },
            title = { Text(comic.title) },
            text = {
                Column {
                    TextButton(onClick = {
                        actionComic = null
                        if (isProUnlocked) onToggleFavorite(comic) else showProDialog = true
                    }) {
                        Icon(Icons.Filled.Bookmark, null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(if (metadataFor(comic, effectiveMetadata).favorite) R.string.library_remove_favorite else R.string.library_add_favorite))
                    }
                    TextButton(onClick = {
                        actionComic = null
                        if (isProUnlocked) tagComic = comic else showProDialog = true
                    }) {
                        Icon(Icons.Filled.Edit, null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.library_edit_tags))
                    }
                    TextButton(onClick = {
                        actionComic = null
                        removeComic = comic
                    }) {
                        Icon(Icons.Filled.Delete, null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.remove))
                    }
                }
            },
            confirmButton = {},
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
private fun ReferenceLibraryTabs(
    selected: ReferenceLibraryCategory,
    onSelected: (ReferenceLibraryCategory) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ReferenceLibraryCategory.entries.forEach { category ->
            val label = when (category) {
                ReferenceLibraryCategory.All -> R.string.library_filter_all
                ReferenceLibraryCategory.Recent -> R.string.recent_section
                ReferenceLibraryCategory.Favorites -> R.string.library_filter_favorites
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    modifier = Modifier.selectable(
                        selected = selected == category,
                        role = Role.Tab,
                        onClick = { onSelected(category) },
                    ),
                    shape = RoundedCornerShape(20.dp),
                    color = if (selected == category) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                    } else {
                        MaterialTheme.colorScheme.background
                    },
                ) {
                    Text(
                        text = stringResource(label),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (selected == category) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
                Spacer(Modifier.height(5.dp))
                Surface(
                    modifier = Modifier.size(width = 54.dp, height = 3.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = if (selected == category) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background,
                ) {}
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
}

@Composable
private fun ReferenceLibraryGrid(
    comics: List<LibraryComic>,
    libraryMetadata: List<LibraryComicMetadata>,
    libraryDetails: List<LibraryComicDetails>,
    recentComics: List<RecentComic>,
    onOpen: (LibraryComic) -> Unit,
    onToggleFavorite: (LibraryComic) -> Unit,
    onActions: (LibraryComic) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val tablet = isTabletWidth(maxWidth.value.roundToInt())
        LazyVerticalGrid(
            columns = if (tablet) GridCells.Adaptive(160.dp) else GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            items(comics, key = { "${it.sourceType.storageName}:${it.path}" }) { comic ->
                ReferenceLibraryCard(
                    comic = comic,
                    metadata = metadataFor(comic, libraryMetadata),
                    details = detailsFor(comic, libraryDetails),
                    recentComic = comic.matchingRecentComic(recentComics),
                    onOpen = { onOpen(comic) },
                    onToggleFavorite = { onToggleFavorite(comic) },
                    onActions = { onActions(comic) },
                )
            }
        }
    }
}

@Composable
private fun ReferenceLibraryCard(
    comic: LibraryComic,
    metadata: LibraryComicMetadata,
    details: LibraryComicDetails?,
    recentComic: RecentComic?,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onActions: () -> Unit,
) {
    val progress = libraryProgressFraction(comic, listOfNotNull(recentComic))
    val pageCount = resolvedComicPageCount(details, recentComic)
    Column(modifier = Modifier.clickable(onClick = onOpen)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f),
            shape = RoundedCornerShape(9.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        ) {
            Box {
                LibraryComicCover(libraryComic = comic)
                if (metadata.favorite) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .clickable(onClick = onToggleFavorite),
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Icon(
                            Icons.Filled.Bookmark,
                            stringResource(R.string.library_remove_favorite),
                            modifier = Modifier.padding(6.dp).size(14.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = comic.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onActions, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options), modifier = Modifier.size(18.dp))
            }
        }
        Text(
            text = pageCount?.let { pluralStringResource(R.plurals.comic_pages, it, it) } ?: comic.displayName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(7.dp))
        if (progress != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                Text(
                    stringResource(R.string.comic_progress_percent, (progress * 100).roundToInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            Spacer(Modifier.height(17.dp))
        }
    }
}

@Composable
private fun ReferenceLibraryFilterDialog(
    sortOrder: LibrarySortOrder,
    sourceFilter: LibraryFilter,
    progressFilter: LibraryProgressFilter,
    tagFilter: String?,
    tags: List<String>,
    isProUnlocked: Boolean,
    onSort: (LibrarySortOrder) -> Unit,
    onSource: (LibraryFilter) -> Unit,
    onProgress: (LibraryProgressFilter) -> Unit,
    onTag: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_filters)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterRow {
                    LibrarySortOrder.entries.forEach { item ->
                        FilterChip(
                            selected = item == sortOrder,
                            onClick = { onSort(item) },
                            label = { Text(stringResource(item.labelRes())) },
                        )
                    }
                }
                FilterRow {
                    LibraryFilter.entries.forEach { item ->
                        FilterChip(
                            selected = item == sourceFilter,
                            onClick = { onSource(item) },
                            label = { Text(stringResource(item.labelRes())) },
                        )
                    }
                }
                FilterRow {
                    LibraryProgressFilter.entries.forEach { item ->
                        FilterChip(
                            selected = item == progressFilter,
                            onClick = { onProgress(item) },
                            leadingIcon = if (!isProUnlocked && item != LibraryProgressFilter.All) {
                                { Icon(Icons.Filled.WorkspacePremium, null, modifier = Modifier.size(14.dp)) }
                            } else null,
                            label = { Text(stringResource(item.labelRes())) },
                        )
                    }
                }
                if (tags.isNotEmpty()) {
                    FilterRow {
                        tags.forEach { tag ->
                            FilterChip(
                                selected = tagFilter == tag,
                                onClick = { onTag(if (tagFilter == tag) null else tag) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, null, modifier = Modifier.size(14.dp)) },
                                label = { Text(tag) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
    )
}

@Composable
private fun FilterRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
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

private fun LibraryProgressFilter.labelRes(): Int = when (this) {
    LibraryProgressFilter.All -> R.string.library_filter_all_progress
    LibraryProgressFilter.Favorites -> R.string.library_filter_favorites
    LibraryProgressFilter.Unread -> R.string.library_filter_unread
    LibraryProgressFilter.InProgress -> R.string.library_filter_in_progress
    LibraryProgressFilter.Completed -> R.string.library_filter_completed
}
