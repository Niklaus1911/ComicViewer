package com.comicviewer.ui.importer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comicviewer.ui.common.tabScreen
import com.comicviewer.LibraryComic
import com.comicviewer.LibraryComicDetails
import com.comicviewer.R
import com.comicviewer.RecentComic
import com.comicviewer.detailsFor
import com.comicviewer.matchingRecentComic
import com.comicviewer.resolvedComicPageCount
import com.comicviewer.ui.common.ReferenceLargeCardShape
import com.comicviewer.ui.common.ReferenceScreenHeader
import com.comicviewer.ui.common.ReferenceSectionHeader
import com.comicviewer.ui.common.fileSizeText
import com.comicviewer.ui.common.relativeTimeText
import com.comicviewer.ui.library.LibraryComicCover

@Composable
fun ImportScreen(
    libraryComics: List<LibraryComic>,
    libraryDetails: List<LibraryComicDetails>,
    recentComics: List<RecentComic>,
    onImportArchives: () -> Unit,
    onScanFolder: () -> Unit,
    onOpenArchiveWithoutImporting: () -> Unit,
    onOpenFolderWithoutImporting: () -> Unit,
    onAddFolderAsComic: () -> Unit,
    onOpenComic: (LibraryComic) -> Unit,
    onRemoveComic: (LibraryComic) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAll by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    val recentImports = libraryComics.sortedByDescending(LibraryComic::addedAt)
    val visibleImports = if (showAll) recentImports else recentImports.take(3)

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.tabScreen),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ReferenceScreenHeader(
                    title = stringResource(R.string.nav_import),
                    subtitle = stringResource(R.string.import_subtitle),
                    actions = {
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options))
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.open_archive)) },
                                    onClick = {
                                        menuExpanded = false
                                        onOpenArchiveWithoutImporting()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.open_folder)) },
                                    onClick = {
                                        menuExpanded = false
                                        onOpenFolderWithoutImporting()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.browser_add_folder)) },
                                    onClick = {
                                        menuExpanded = false
                                        onAddFolderAsComic()
                                    },
                                )
                            }
                        }
                    },
                )
            }
            item {
                ImportActionCard(
                    icon = Icons.Filled.Archive,
                    title = stringResource(R.string.import_archive_title),
                    description = stringResource(R.string.import_archive_description),
                    onClick = onImportArchives,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            item {
                ImportActionCard(
                    icon = Icons.Filled.Folder,
                    title = stringResource(R.string.import_folder_title),
                    description = stringResource(R.string.import_folder_description),
                    onClick = onScanFolder,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            item {
                ReferenceSectionHeader(
                    title = stringResource(R.string.recent_imports),
                    action = if (recentImports.size > 3) {
                        {
                            TextButton(onClick = { showAll = !showAll }) {
                                Text(stringResource(if (showAll) R.string.show_less else R.string.see_all))
                            }
                        }
                    } else {
                        null
                    },
                )
            }
            if (visibleImports.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.import_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.import_empty_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(
                    items = visibleImports,
                    key = { "${it.sourceType.storageName}:${it.path}" },
                ) { comic ->
                    ImportComicRow(
                        comic = comic,
                        details = detailsFor(comic, libraryDetails),
                        recentComic = comic.matchingRecentComic(recentComics),
                        onClick = { onOpenComic(comic) },
                        onRemove = { onRemoveComic(comic) },
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportActionCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = ReferenceLargeCardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp).size(24.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun ImportComicRow(
    comic: LibraryComic,
    details: LibraryComicDetails?,
    recentComic: RecentComic?,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val pageCount = resolvedComicPageCount(details, recentComic)
    val size = fileSizeText(details?.fileSizeBytes)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(width = 58.dp, height = 76.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                LibraryComicCover(libraryComic = comic)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = comic.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val facts = listOfNotNull(
                    pageCount?.let { pluralStringResource(R.plurals.comic_pages, it, it) },
                    size,
                ).joinToString("  •  ")
                if (facts.isNotEmpty()) {
                    Text(
                        facts,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    relativeTimeText(comic.addedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.remove)) },
                        onClick = {
                            menuExpanded = false
                            onRemove()
                        },
                    )
                }
            }
        }
    }
}
