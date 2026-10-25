package com.comicviewer.ui.browser

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comicviewer.ui.common.tabScreen
import com.comicviewer.BrowserEntryType
import com.comicviewer.ComicDocumentEntry
import com.comicviewer.R
import com.comicviewer.browserEntryTypeFor
import com.comicviewer.canReadDocumentDirectory
import com.comicviewer.compareBrowserEntries
import com.comicviewer.folderDocumentFor
import com.comicviewer.listDocumentChildren
import com.comicviewer.ui.common.StatusContent
import com.comicviewer.ui.common.absoluteDateText
import com.comicviewer.ui.common.fileSizeText
import com.comicviewer.ui.common.softContentTransform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class DocumentBrowserLocation(
    val uri: Uri,
    val name: String,
)

private sealed interface DocumentBrowserUiState {
    data object Loading : DocumentBrowserUiState
    data class Loaded(val entries: List<ComicDocumentEntry>) : DocumentBrowserUiState
    data class Error(val message: String) : DocumentBrowserUiState
}

@Composable
fun DocumentBrowserScreen(
    rootUri: Uri,
    onAddArchives: (List<Uri>) -> Unit,
    onCancelImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val rootName = remember(rootUri, resources) {
        rootUri.lastPathSegment
            ?.substringAfterLast('/')
            ?.takeIf(String::isNotBlank)
            ?: resources.getString(R.string.unnamed_comic_folder)
    }
    var locations by remember(rootUri) {
        mutableStateOf(listOf(DocumentBrowserLocation(rootUri, rootName)))
    }
    var selectedUris by remember(rootUri) { mutableStateOf(emptySet<String>()) }
    var reloadRevision by remember { mutableIntStateOf(0) }
    var browserState by remember(rootUri) {
        mutableStateOf<DocumentBrowserUiState>(DocumentBrowserUiState.Loading)
    }
    val current = locations.last()

    LaunchedEffect(rootUri) {
        val resolvedRootName = withContext(Dispatchers.IO) {
            folderDocumentFor(context.applicationContext, rootUri).displayName
        }
        locations = locations.mapIndexed { index, location ->
            if (index == 0 && location.uri == rootUri) location.copy(name = resolvedRootName) else location
        }
    }

    fun navigateBack() {
        if (locations.size > 1) {
            locations = locations.dropLast(1)
        } else {
            onCancelImport()
        }
    }

    BackHandler(enabled = locations.size > 1, onBack = ::navigateBack)

    LaunchedEffect(current.uri, reloadRevision) {
        browserState = DocumentBrowserUiState.Loading
        try {
            val entries = withContext(Dispatchers.IO) {
                check(canReadDocumentDirectory(context.applicationContext, current.uri)) {
                    resources.getString(R.string.folder_not_readable)
                }
                listDocumentChildren(context.applicationContext, current.uri)
                    .filter { entry ->
                        entry.isDirectory || browserEntryTypeFor(
                            name = entry.displayName,
                            isDirectory = false,
                            isImageFile = entry.mimeType?.startsWith("image/") == true,
                        ) == BrowserEntryType.Archive
                    }
                    .sortedWith { left, right ->
                        compareBrowserEntries(
                            leftName = left.displayName,
                            leftIsDirectory = left.isDirectory,
                            rightName = right.displayName,
                            rightIsDirectory = right.isDirectory,
                        )
                    }
            }
            browserState = DocumentBrowserUiState.Loaded(entries)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Exception) {
            browserState = DocumentBrowserUiState.Error(
                throwable.message?.takeIf(String::isNotBlank)
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
                .windowInsetsPadding(WindowInsets.tabScreen),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 14.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = ::navigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.browser_choose_file),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.browser_archive_only),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { reloadRevision += 1 }) {
                    Icon(Icons.Filled.Refresh, stringResource(R.string.browser_refresh_action))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                locations.forEachIndexed { index, location ->
                    if (index > 0) {
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Text(
                        text = location.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (index == locations.lastIndex) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (index == locations.lastIndex) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(if (index == locations.lastIndex) 1f else 0.6f, fill = false)
                            .clickable(enabled = index < locations.lastIndex) {
                                locations = locations.take(index + 1)
                            },
                    )
                }
            }

            AnimatedContent(
                targetState = browserState,
                transitionSpec = { softContentTransform() },
                label = "documentBrowserState",
                modifier = Modifier.weight(1f),
            ) { state ->
                when (state) {
                    DocumentBrowserUiState.Loading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }

                    is DocumentBrowserUiState.Error -> StatusContent(
                        icon = Icons.Filled.ErrorOutline,
                        title = stringResource(R.string.browser_error_title),
                        message = state.message,
                        actionLabel = stringResource(R.string.retry),
                        onAction = { reloadRevision += 1 },
                    )

                    is DocumentBrowserUiState.Loaded -> if (state.entries.isEmpty()) {
                        StatusContent(
                            icon = Icons.Filled.FolderOff,
                            title = stringResource(R.string.browser_empty_folder_title),
                            message = stringResource(R.string.empty_folder),
                        )
                    } else {
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(state.entries, key = { it.uri.toString() }) { entry ->
                                    val selected = entry.uri.toString() in selectedUris
                                    Column {
                                        DocumentBrowserRow(
                                            entry = entry,
                                            selected = selected,
                                            onClick = {
                                                if (entry.isDirectory) {
                                                    locations = locations + DocumentBrowserLocation(
                                                        uri = entry.uri,
                                                        name = entry.displayName,
                                                    )
                                                } else {
                                                    selectedUris = if (selected) {
                                                        selectedUris - entry.uri.toString()
                                                    } else {
                                                        selectedUris + entry.uri.toString()
                                                    }
                                                }
                                            },
                                        )
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

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
                    onClick = { onAddArchives(selectedUris.sorted().map(Uri::parse)) },
                    enabled = selectedUris.isNotEmpty(),
                    modifier = Modifier.weight(1.15f),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Text(stringResource(R.string.import_selected, selectedUris.size))
                }
            }
        }
    }
}

@Composable
private fun DocumentBrowserRow(
    entry: ComicDocumentEntry,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                if (!entry.isDirectory) this.selected = selected
            }
            .clickable(
                role = if (entry.isDirectory) Role.Button else Role.Checkbox,
                onClick = onClick,
            ),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(9.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(
                    imageVector = if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.Archive,
                    contentDescription = null,
                    modifier = Modifier.padding(9.dp).size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!entry.isDirectory) {
                    val facts = listOfNotNull(
                        entry.lastModifiedAt?.let { absoluteDateText(it) }?.takeIf(String::isNotBlank),
                        fileSizeText(entry.sizeBytes),
                    ).joinToString("  •  ")
                    if (facts.isNotEmpty()) {
                        Text(
                            text = facts,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Icon(
                imageVector = when {
                    entry.isDirectory -> Icons.Filled.ChevronRight
                    selected -> Icons.Filled.CheckCircle
                    else -> Icons.Filled.RadioButtonUnchecked
                },
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
