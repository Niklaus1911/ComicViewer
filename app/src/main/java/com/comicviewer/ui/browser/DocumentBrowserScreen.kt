package com.comicviewer.ui.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.comicviewer.R
import com.comicviewer.RecentComic
import com.comicviewer.ui.common.icon
import com.comicviewer.ui.common.progressText
import com.comicviewer.ui.common.rememberThrottledAction

@Composable
fun DocumentBrowserScreen(
    recentComics: List<RecentComic>,
    isAddingToLibrary: Boolean,
    onOpenArchivePicker: () -> Unit,
    onOpenFolderPicker: () -> Unit,
    onAddArchivesPicker: () -> Unit,
    onAddFolderPicker: () -> Unit,
    onOpenRecentComic: (RecentComic) -> Unit,
    modifier: Modifier = Modifier,
) {
    val archiveAction = rememberThrottledAction(
        action = if (isAddingToLibrary) onAddArchivesPicker else onOpenArchivePicker,
    )
    val folderAction = rememberThrottledAction(
        action = if (isAddingToLibrary) onAddFolderPicker else onOpenFolderPicker,
    )

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, top = 18.dp, end = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (isAddingToLibrary) {
                                R.string.browser_title_add_to_library
                            } else {
                                R.string.nav_browse
                            },
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = stringResource(R.string.document_browser_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = archiveAction,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Archive,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(
                                if (isAddingToLibrary) R.string.select_archives else R.string.open_archive,
                            ),
                        )
                    }
                    FilledTonalButton(
                        onClick = folderAction,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(
                                if (isAddingToLibrary) R.string.select_folder else R.string.open_folder,
                            ),
                        )
                    }
                }
            }

            if (!isAddingToLibrary && recentComics.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.recent_section),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 20.dp, top = 8.dp, end = 20.dp),
                    )
                }
                items(
                    items = recentComics,
                    key = { comic -> "${comic.sourceType.storageName}:${comic.path}" },
                ) { comic ->
                    val openRecent = rememberThrottledAction { onOpenRecentComic(comic) }
                    ListItem(
                        headlineContent = { Text(comic.title) },
                        supportingContent = { Text(comic.progressText()) },
                        leadingContent = {
                            Icon(
                                imageVector = comic.sourceType.icon(),
                                contentDescription = null,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = openRecent)
                            .padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}
