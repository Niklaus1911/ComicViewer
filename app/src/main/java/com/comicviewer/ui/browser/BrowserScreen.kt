package com.comicviewer.ui.browser

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import com.comicviewer.R
import com.comicviewer.BrowserDirectory
import com.comicviewer.BrowserEntry
import com.comicviewer.BrowserEntryType
import com.comicviewer.RecentComic
import com.comicviewer.TABLET_BROWSER_CONTENT_MAX_WIDTH_DP
import com.comicviewer.browserParentPathFor
import com.comicviewer.filterBrowserEntries
import com.comicviewer.firstLocalFolderCoverFile
import com.comicviewer.isTabletWidth
import com.comicviewer.loadBrowserDirectory
import com.comicviewer.ui.common.icon
import com.comicviewer.ui.common.labelText
import com.comicviewer.ui.common.rememberThrottledAction
import com.comicviewer.ui.common.softContentTransform
import com.comicviewer.ui.common.softPress
import com.comicviewer.ui.common.subtitleText
import java.io.File
import kotlin.math.roundToInt

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
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
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
                    throwable.message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.folder_not_readable),
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
                selectedArchiveCount = selectedArchivePaths.size,
                onSearchQueryChange = { query -> searchQuery = query },
                onDirectoryChange = onDirectoryChange,
                onOpenCurrentFolderAsComic = {
                    if (isAddingToLibrary) {
                        onAddLocalFolderToLibrary(File(currentDirectoryPath))
                    } else {
                        onOpenLocalFolder(File(currentDirectoryPath))
                    }
                },
                onAddSelectedArchives = {
                    onAddLocalArchivesToLibrary(selectedArchivePaths.sorted().map(::File))
                    selectedArchivePaths = emptySet()
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
        }
    }
}

@Composable
fun FileBrowserHeader(
    currentDirectoryPath: String,
    isAddingToLibrary: Boolean,
    parentPath: String?,
    canOpenCurrentFolderAsComic: Boolean,
    searchQuery: String,
    selectedArchiveCount: Int,
    onSearchQueryChange: (String) -> Unit,
    onDirectoryChange: (String) -> Unit,
    onOpenCurrentFolderAsComic: () -> Unit,
    onAddSelectedArchives: () -> Unit,
    onReload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val throttledParentClick = rememberThrottledAction { if (parentPath != null) onDirectoryChange(parentPath) }
    val throttledOpenFolder = rememberThrottledAction(action = onOpenCurrentFolderAsComic)
    val throttledReload = rememberThrottledAction(action = onReload)
    val throttledAddSelected = rememberThrottledAction(action = onAddSelectedArchives)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isAddingToLibrary) stringResource(R.string.browser_title_add_to_library) else stringResource(R.string.browser_title_file),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = currentDirectoryPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(
                onClick = throttledParentClick,
                enabled = parentPath != null,
            ) {
                Text(stringResource(R.string.browser_up))
            }
            TextButton(onClick = throttledReload) {
                Text(stringResource(R.string.browser_refresh))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = throttledOpenFolder,
                enabled = canOpenCurrentFolderAsComic,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (isAddingToLibrary) stringResource(R.string.browser_add_folder) else stringResource(R.string.browser_open_as_comic))
            }
            if (isAddingToLibrary && selectedArchiveCount > 0) {
                Button(
                    onClick = throttledAddSelected,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.browser_add_selected, selectedArchiveCount))
                }
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
        HorizontalDivider()
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text(stringResource(R.string.retry))
        }
    }
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
    modifier: Modifier = Modifier,
) {
    val entries = filterBrowserEntries(directory.entries, searchQuery)
    val showRecentComics = !isAddingToLibrary && searchQuery.isBlank() && recentComics.isNotEmpty()

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
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (searchQuery.isBlank()) stringResource(R.string.empty_folder) else stringResource(R.string.no_results),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    ) {
                        if (showRecentComics) {
                            item(key = "recent_comics_header") {
                                FileBrowserSectionHeader(title = stringResource(R.string.recent_section))
                            }
                            items(
                                items = recentComics,
                                key = { recentComic -> "recent:${recentComic.sourceType.storageName}:${recentComic.path}" },
                            ) { recentComic ->
                                RecentComicRow(
                                    recentComic = recentComic,
                                    onClick = { onRecentComicClick(recentComic) },
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 88.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f),
                                )
                            }
                            if (entries.isNotEmpty()) {
                                item(key = "browser_entries_header") {
                                    FileBrowserSectionHeader(title = stringResource(R.string.files_section))
                                }
                            }
                        }
                        items(
                            items = entries,
                            key = { entry -> entry.path },
                        ) { entry ->
                            FileBrowserRow(
                                entry = entry,
                                isAddingToLibrary = isAddingToLibrary,
                                isSelected = entry.path in selectedArchivePaths,
                                onClick = { onEntryClick(entry) },
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 88.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f),
                            )
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
    Surface(
        modifier = modifier.size(58.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = recentComic.sourceType.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }
    }
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
        FileBrowserThumbnail(entry = entry)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (isSelected) {
                    "${entry.type.labelText()} • ${stringResource(R.string.browser_selected)}"
                } else {
                    entry.type.labelText()
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (isSelectableArchive) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                modifier = Modifier.semantics {
                    contentDescription = checkboxContentDescription
                },
            )
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
    Surface(
        modifier = modifier.size(58.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        val thumbnailUri = entry.thumbnailUri
        if (thumbnailUri != null) {
            Image(
                painter = rememberAsyncImagePainter(model = thumbnailUri),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp)),
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = entry.type.icon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}


private fun BrowserDirectory.hasDirectImageEntry(): Boolean =
    entries.any { entry -> entry.type == BrowserEntryType.ImageFile }
