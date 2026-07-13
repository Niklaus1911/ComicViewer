package com.comicviewer.ui.browser

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comicviewer.R
import com.comicviewer.BrowserDirectory
import com.comicviewer.BrowserEntry
import com.comicviewer.BrowserEntryType
import com.comicviewer.RecentComic
import com.comicviewer.TABLET_BROWSER_CONTENT_MAX_WIDTH_DP
import com.comicviewer.browserBreadcrumbs
import com.comicviewer.browserParentPathFor
import com.comicviewer.filterBrowserEntries
import com.comicviewer.isTabletWidth
import com.comicviewer.loadBrowserDirectory
import com.comicviewer.loadBrowserArchiveThumbnailUri
import com.comicviewer.loadRecentComicThumbnailUri
import com.comicviewer.ui.common.CoverThumbnail
import com.comicviewer.ui.common.StaggeredReveal
import com.comicviewer.ui.common.StatusContent
import com.comicviewer.ui.common.icon
import com.comicviewer.ui.common.labelText
import com.comicviewer.ui.common.rememberThrottledAction
import com.comicviewer.ui.common.softContentTransform
import com.comicviewer.ui.common.softPress
import com.comicviewer.ui.common.subtitleText
import com.comicviewer.ui.common.absoluteDateText
import com.comicviewer.ui.common.fileSizeText
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

private const val MAX_STAGGER_STEPS = 8
private const val STAGGER_STEP_MS = 40

sealed interface FileBrowserUiState {
    data object Loading : FileBrowserUiState
    data class Loaded(val directory: BrowserDirectory) : FileBrowserUiState
    data class Error(val message: String) : FileBrowserUiState
}

@Composable
fun FileBrowserScreen(
    currentDirectoryPath: String,
    browserRootPath: String,
    recentComics: List<RecentComic>,
    isAddingToLibrary: Boolean,
    onDirectoryChange: (String) -> Unit,
    onOpenLocalArchive: (File) -> Unit,
    onOpenLocalFolder: (File) -> Unit,
    onOpenRecentComic: (RecentComic) -> Unit,
    onAddLocalArchivesToLibrary: (List<File>) -> Unit,
    onAddLocalFolderToLibrary: (File) -> Unit,
    onCancelImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var searchQuery by remember(currentDirectoryPath) { mutableStateOf("") }
    var browserState by remember(currentDirectoryPath) {
        mutableStateOf<FileBrowserUiState>(FileBrowserUiState.Loading)
    }
    var selectedArchivePaths by remember(isAddingToLibrary) { mutableStateOf(emptySet<String>()) }

    fun reload() {
        browserState = FileBrowserUiState.Loading
    }

    fun toggleArchiveSelection(path: String) {
        selectedArchivePaths = if (path in selectedArchivePaths) {
            selectedArchivePaths - path
        } else {
            selectedArchivePaths + path
        }
    }

    LaunchedEffect(currentDirectoryPath, browserState is FileBrowserUiState.Loading) {
        if (browserState !is FileBrowserUiState.Loading) return@LaunchedEffect

        runCatching { loadBrowserDirectory(context.applicationContext, File(currentDirectoryPath)) }
            .onSuccess { directory -> browserState = FileBrowserUiState.Loaded(directory) }
            .onFailure { throwable ->
                browserState = FileBrowserUiState.Error(
                    throwable.message?.takeIf { it.isNotBlank() }
                        ?: resources.getString(R.string.folder_not_readable),
                )
            }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
        ) {
            FileBrowserHeader(
                currentDirectoryPath = currentDirectoryPath,
                browserRootPath = browserRootPath,
                isAddingToLibrary = isAddingToLibrary,
                parentPath = browserParentPathFor(
                    currentPath = currentDirectoryPath,
                    rootPath = browserRootPath,
                ),
                canOpenCurrentFolderAsComic = (browserState as? FileBrowserUiState.Loaded)
                    ?.directory
                    ?.hasDirectImageEntry()
                    ?: false,
                searchQuery = searchQuery,
                onSearchQueryChange = { query -> searchQuery = query },
                onDirectoryChange = onDirectoryChange,
                onBack = onCancelImport,
                onOpenCurrentFolderAsComic = {
                    if (isAddingToLibrary) {
                        onAddLocalFolderToLibrary(File(currentDirectoryPath))
                    } else {
                        onOpenLocalFolder(File(currentDirectoryPath))
                    }
                },
                onReload = ::reload,
            )

            AnimatedContent(
                targetState = browserState,
                transitionSpec = { softContentTransform() },
                label = "browserStateTransition",
                modifier = Modifier.weight(1f),
            ) { state ->
                when (state) {
                    FileBrowserUiState.Loading -> LoadingFileBrowserContent()
                    is FileBrowserUiState.Error -> FileBrowserErrorContent(
                        message = state.message,
                        onRetry = ::reload,
                    )

                    is FileBrowserUiState.Loaded -> FileBrowserContent(
                        directory = state.directory,
                        recentComics = recentComics,
                        searchQuery = searchQuery,
                        isAddingToLibrary = isAddingToLibrary,
                        selectedArchivePaths = selectedArchivePaths,
                        onRecentComicClick = onOpenRecentComic,
                        onClearSearch = { searchQuery = "" },
                        onEntryClick = { entry ->
                            when (entry.type) {
                                BrowserEntryType.Directory -> onDirectoryChange(entry.path)
                                BrowserEntryType.Archive -> {
                                    if (isAddingToLibrary) {
                                        toggleArchiveSelection(entry.path)
                                    } else {
                                        onOpenLocalArchive(File(entry.path))
                                    }
                                }

                                BrowserEntryType.ImageFile,
                                BrowserEntryType.UnsupportedFile,
                                -> Unit
                            }
                        },
                    )
                }
            }

            if (isAddingToLibrary) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onCancelImport,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        onClick = {
                            onAddLocalArchivesToLibrary(selectedArchivePaths.sorted().map(::File))
                            selectedArchivePaths = emptySet()
                        },
                        enabled = selectedArchivePaths.isNotEmpty(),
                        modifier = Modifier.weight(1.15f),
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        Text(stringResource(R.string.import_selected, selectedArchivePaths.size))
                    }
                }
            }
        }
    }
}

@Composable
fun FileBrowserHeader(
    currentDirectoryPath: String,
    browserRootPath: String,
    isAddingToLibrary: Boolean,
    parentPath: String?,
    canOpenCurrentFolderAsComic: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onDirectoryChange: (String) -> Unit,
    onBack: () -> Unit,
    onOpenCurrentFolderAsComic: () -> Unit,
    onReload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val throttledParentClick = rememberThrottledAction {
        if (parentPath != null) onDirectoryChange(parentPath) else onBack()
    }
    val throttledOpenFolder = rememberThrottledAction(action = onOpenCurrentFolderAsComic)
    val throttledReload = rememberThrottledAction(action = onReload)
    var menuExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(
                onClick = throttledParentClick,
                enabled = parentPath != null || isAddingToLibrary,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(
                        if (parentPath != null) R.string.browser_up_action else R.string.back,
                    ),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isAddingToLibrary) stringResource(R.string.browser_choose_file) else stringResource(R.string.browser_title_file),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (isAddingToLibrary) {
                    Text(
                        text = stringResource(R.string.browser_archive_only),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (isAddingToLibrary) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.browser_refresh_action)) },
                            leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                throttledReload()
                            },
                        )
                        if (canOpenCurrentFolderAsComic) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.browser_add_folder)) },
                                onClick = {
                                    menuExpanded = false
                                    throttledOpenFolder()
                                },
                            )
                        }
                    }
                }
            } else {
                IconButton(onClick = throttledReload) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.browser_refresh_action),
                    )
                }
            }
        }
        FileBrowserBreadcrumbs(
            currentDirectoryPath = currentDirectoryPath,
            browserRootPath = browserRootPath,
            onDirectoryChange = onDirectoryChange,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!isAddingToLibrary) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = throttledOpenFolder,
                    enabled = canOpenCurrentFolderAsComic,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.browser_open_as_comic))
                }
            }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.browser_clear_search),
                            )
                        }
                    }
                },
                label = { Text(stringResource(R.string.browser_search_label)) },
            )
        }
    }
}

@Composable
private fun FileBrowserBreadcrumbs(
    currentDirectoryPath: String,
    browserRootPath: String,
    onDirectoryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val crumbs = remember(currentDirectoryPath, browserRootPath) {
        browserBreadcrumbs(currentDirectoryPath, browserRootPath)
    }
    val scrollState = rememberScrollState()
    LaunchedEffect(crumbs, scrollState.maxValue) {
        scrollState.scrollTo(scrollState.maxValue)
    }
    Row(
        modifier = modifier.horizontalScroll(scrollState),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        crumbs.forEachIndexed { index, crumb ->
            if (index > 0) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            val isLast = index == crumbs.lastIndex
            val crumbColor = if (isLast) {
                MaterialTheme.colorScheme.onBackground
            } else {
                MaterialTheme.colorScheme.primary
            }
            // The current folder (last crumb) is shown but not interactive — only ancestors navigate.
            val crumbModifier = if (isLast) {
                Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            } else {
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onDirectoryChange(crumb.path) }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            }
            Row(
                modifier = crumbModifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (index == 0) {
                    Icon(
                        imageVector = Icons.Filled.Home,
                        contentDescription = stringResource(R.string.browser_breadcrumb_home),
                        tint = crumbColor,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Text(
                        text = crumb.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = crumbColor,
                        fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun LoadingFileBrowserContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun FileBrowserErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StatusContent(
        icon = Icons.Filled.ErrorOutline,
        title = stringResource(R.string.browser_error_title),
        message = message,
        modifier = modifier,
        actionLabel = stringResource(R.string.retry),
        onAction = onRetry,
    )
}

@Composable
fun FileBrowserContent(
    directory: BrowserDirectory,
    recentComics: List<RecentComic>,
    searchQuery: String,
    isAddingToLibrary: Boolean,
    selectedArchivePaths: Set<String>,
    onRecentComicClick: (RecentComic) -> Unit,
    onEntryClick: (BrowserEntry) -> Unit,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = filterBrowserEntries(directory.entries, searchQuery)
        .filter { entry ->
            !isAddingToLibrary || entry.type == BrowserEntryType.Directory || entry.type == BrowserEntryType.Archive
        }
    val showRecentComics = !isAddingToLibrary && searchQuery.isBlank() && recentComics.isNotEmpty()
    val recentsCount = if (showRecentComics) recentComics.size else 0

    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val tabletLayout = isTabletWidth(maxWidth.value.roundToInt())
        val listModifier = if (tabletLayout) {
            Modifier
                .fillMaxHeight()
                .widthIn(max = TABLET_BROWSER_CONTENT_MAX_WIDTH_DP.dp)
                .fillMaxWidth()
        } else {
            Modifier.fillMaxSize()
        }

        AnimatedContent(
            targetState = entries.isEmpty() && !showRecentComics,
            transitionSpec = { softContentTransform() },
            label = "browserContentTransition",
            modifier = Modifier.fillMaxSize(),
        ) { isEmpty ->
            if (isEmpty) {
                if (searchQuery.isBlank()) {
                    StatusContent(
                        icon = Icons.Filled.FolderOff,
                        title = stringResource(R.string.browser_empty_folder_title),
                        message = stringResource(R.string.empty_folder),
                    )
                } else {
                    StatusContent(
                        icon = Icons.Filled.SearchOff,
                        title = stringResource(R.string.browser_no_results_title),
                        message = stringResource(R.string.no_results),
                        actionLabel = stringResource(R.string.browser_clear_search),
                        onAction = onClearSearch,
                    )
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    LazyColumn(
                        modifier = listModifier,
                        contentPadding = PaddingValues(bottom = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (showRecentComics) {
                            item(key = "recent_comics_header") {
                                FileBrowserSectionHeader(title = stringResource(R.string.recent_section))
                            }
                            itemsIndexed(
                                items = recentComics,
                                key = { _, recentComic -> "recent:${recentComic.sourceType.storageName}:${recentComic.path}" },
                            ) { index, recentComic ->
                                StaggeredReveal(
                                    visible = revealed,
                                    delayMillis = min(index, MAX_STAGGER_STEPS) * STAGGER_STEP_MS,
                                ) {
                                    RecentComicRow(
                                        recentComic = recentComic,
                                        onClick = { onRecentComicClick(recentComic) },
                                    )
                                }
                            }
                            if (entries.isNotEmpty()) {
                                item(key = "browser_entries_header") {
                                    FileBrowserSectionHeader(title = stringResource(R.string.files_section))
                                }
                            }
                        }
                        itemsIndexed(
                            items = entries,
                            key = { _, entry -> entry.path },
                        ) { index, entry ->
                            StaggeredReveal(
                                visible = revealed,
                                delayMillis = min(recentsCount + index, MAX_STAGGER_STEPS) * STAGGER_STEP_MS,
                            ) {
                                FileBrowserRow(
                                    entry = entry,
                                    isAddingToLibrary = isAddingToLibrary,
                                    isSelected = entry.path in selectedArchivePaths,
                                    onClick = { onEntryClick(entry) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FileBrowserSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 6.dp),
    )
}

@Composable
fun RecentComicRow(
    recentComic: RecentComic,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val throttledRecentClick = rememberThrottledAction(action = onClick)
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .softPress(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = throttledRecentClick,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        RecentComicThumbnail(recentComic = recentComic)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = recentComic.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = recentComic.subtitleText(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = recentComic.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
fun RecentComicThumbnail(
    recentComic: RecentComic,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var thumbnailUri by remember(recentComic) { mutableStateOf<Uri?>(null) }

    LaunchedEffect(recentComic) {
        thumbnailUri = loadRecentComicThumbnailUri(context.applicationContext, recentComic)
    }

    CoverThumbnail(
        thumbnailUri = thumbnailUri,
        fallbackIcon = recentComic.sourceType.icon(),
        modifier = modifier,
    )
}

@Composable
fun FileBrowserRow(
    entry: BrowserEntry,
    isAddingToLibrary: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canClick = entry.canOpen || (isAddingToLibrary && entry.type == BrowserEntryType.Archive)
    val isSelectableArchive = isAddingToLibrary && entry.type == BrowserEntryType.Archive
    val checkboxContentDescription = if (isSelected) {
        stringResource(R.string.browser_deselect_comic)
    } else {
        stringResource(R.string.browser_select_comic)
    }
    val rowAlpha = if (canClick || entry.type == BrowserEntryType.ImageFile) 1f else 0.52f
    val throttledClick = rememberThrottledAction(action = onClick)
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .softPress(interactionSource, enabled = canClick)
            .clickable(
                enabled = canClick,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = throttledClick,
            )
            .alpha(rowAlpha)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (isAddingToLibrary) {
            Surface(
                shape = RoundedCornerShape(9.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(
                    imageVector = entry.type.icon(),
                    contentDescription = null,
                    modifier = Modifier.padding(9.dp).size(22.dp),
                )
            }
        } else {
            FileBrowserThumbnail(entry = entry)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val facts = listOfNotNull(
                entry.lastModifiedAt?.let { absoluteDateText(it) },
                fileSizeText(entry.sizeBytes),
            ).joinToString("  •  ")
            Text(
                text = when {
                    isSelected -> "${entry.type.labelText()} • ${stringResource(R.string.browser_selected)}"
                    facts.isNotEmpty() -> facts
                    else -> entry.type.labelText()
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (isSelectableArchive) {
            IconButton(
                onClick = onClick,
                modifier = Modifier.semantics { contentDescription = checkboxContentDescription },
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (canClick) {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
fun FileBrowserThumbnail(
    entry: BrowserEntry,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var thumbnailUri by remember(entry) { mutableStateOf(entry.thumbnailUri) }

    LaunchedEffect(entry) {
        if (entry.type == BrowserEntryType.Archive) {
            thumbnailUri = loadBrowserArchiveThumbnailUri(
                context = context.applicationContext,
                path = entry.path,
            )
        }
    }

    CoverThumbnail(
        thumbnailUri = thumbnailUri,
        fallbackIcon = entry.type.icon(),
        modifier = modifier,
    )
}


private fun BrowserDirectory.hasDirectImageEntry(): Boolean =
    entries.any { entry -> entry.type == BrowserEntryType.ImageFile }
