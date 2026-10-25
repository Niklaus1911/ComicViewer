package com.comicviewer.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comicviewer.HistoryFilter
import com.comicviewer.HistoryGroup
import com.comicviewer.LibraryComicDetails
import com.comicviewer.R
import com.comicviewer.RecentComic
import com.comicviewer.coercePageIndex
import com.comicviewer.groupedHistory
import com.comicviewer.isFinished
import com.comicviewer.readingProgress
import com.comicviewer.ui.browser.RecentComicThumbnail
import com.comicviewer.ui.common.ReferenceScreenHeader
import com.comicviewer.ui.common.ReferenceScreenHorizontalPadding
import com.comicviewer.ui.common.StatusContent
import com.comicviewer.ui.common.absoluteDateText
import com.comicviewer.ui.common.readableContentWidth
import com.comicviewer.ui.common.relativeTimeText
import com.comicviewer.ui.common.tabScreen
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(
    recentComics: List<RecentComic>,
    libraryDetails: List<LibraryComicDetails>,
    onOpenComic: (RecentComic) -> Unit,
    onRemoveFromHistory: (RecentComic) -> Unit,
    onRestoreToHistory: (RecentComic) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by rememberSaveable { mutableStateOf(HistoryFilter.All) }
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val nowMs = remember(recentComics) { System.currentTimeMillis() }
    val groups = remember(recentComics, filter, nowMs) { groupedHistory(recentComics, filter, nowMs) }
    val removedMessage = stringResource(R.string.history_removed)
    val undoLabel = stringResource(R.string.undo)

    fun remove(comic: RecentComic) {
        onRemoveFromHistory(comic)
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = removedMessage,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) onRestoreToHistory(comic)
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.tabScreen)
                    .readableContentWidth(),
            ) {
                ReferenceScreenHeader(
                    title = stringResource(R.string.nav_history),
                    actions = {
                        if (recentComics.isNotEmpty()) {
                            Box {
                                IconButton(onClick = { menuExpanded = true }) {
                                    Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options))
                                }
                                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.history_clear)) },
                                        leadingIcon = { Icon(Icons.Filled.DeleteSweep, null) },
                                        onClick = {
                                            menuExpanded = false
                                            confirmClear = true
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
                if (recentComics.isEmpty()) {
                    StatusContent(
                        icon = Icons.Filled.History,
                        title = stringResource(R.string.history_empty_title),
                        message = stringResource(R.string.history_empty_message),
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    HistoryFilterRow(selected = filter, onSelected = { filter = it })
                    if (groups.isEmpty()) {
                        StatusContent(
                            icon = if (filter == HistoryFilter.Finished) Icons.Filled.CheckCircle else Icons.Filled.History,
                            title = stringResource(R.string.history_filter_empty_title),
                            message = stringResource(
                                if (filter == HistoryFilter.Finished) {
                                    R.string.history_finished_empty_message
                                } else {
                                    R.string.history_reading_empty_message
                                },
                            ),
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(
                                start = ReferenceScreenHorizontalPadding,
                                end = ReferenceScreenHorizontalPadding,
                                bottom = 24.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            groups.forEach { (group, comics) ->
                                item(key = "group:${group.name}") {
                                    Text(
                                        text = stringResource(group.labelRes()),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .animateItem()
                                            .padding(start = 4.dp, top = 14.dp, bottom = 2.dp),
                                    )
                                }
                                items(comics, key = { "${it.sourceType.storageName}:${it.path}" }) { comic ->
                                    SwipeToRemoveHistoryRow(
                                        comic = comic,
                                        author = libraryDetails.authorFor(comic),
                                        onOpen = { onOpenComic(comic) },
                                        onRemove = { remove(comic) },
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.history_clear_title)) },
            text = { Text(stringResource(R.string.history_clear_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClearHistory()
                }) { Text(stringResource(R.string.history_clear_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun HistoryFilterRow(
    selected: HistoryFilter,
    onSelected: (HistoryFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = ReferenceScreenHorizontalPadding, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HistoryFilter.entries.forEach { item ->
            FilterChip(
                selected = item == selected,
                onClick = { onSelected(item) },
                label = { Text(stringResource(item.labelRes())) },
                leadingIcon = if (item == selected) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun SwipeToRemoveHistoryRow(
    comic: RecentComic,
    author: String?,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
        enableDismissFromStartToEnd = false,
        onDismiss = { onRemove() },
    ) {
        HistoryRow(comic = comic, author = author, onOpen = onOpen, onRemove = onRemove)
    }
}

@Composable
private fun HistoryRow(
    comic: RecentComic,
    author: String?,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    val finished = comic.isFinished()
    val progress = comic.readingProgress()
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                RecentComicThumbnail(recentComic = comic, modifier = Modifier.fillMaxSize())
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = comic.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                author?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (finished) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = stringResource(
                                R.string.history_finished_on,
                                absoluteDateText(comic.completedAt ?: comic.lastOpenedAt),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    Text(
                        text = stringResource(
                            R.string.history_progress_line,
                            coercePageIndex(comic.lastPageIndex, comic.pageCount) + 1,
                            comic.pageCount,
                            relativeTimeText(comic.lastOpenedAt),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LinearProgressIndicator(
                            progress = { progress },
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
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.more_options),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.history_remove)) },
                        leadingIcon = { Icon(Icons.Filled.Delete, null) },
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

private fun HistoryFilter.labelRes(): Int = when (this) {
    HistoryFilter.All -> R.string.library_filter_all
    HistoryFilter.Reading -> R.string.history_filter_reading
    HistoryFilter.Finished -> R.string.history_filter_finished
}

private fun HistoryGroup.labelRes(): Int = when (this) {
    HistoryGroup.Today -> R.string.history_group_today
    HistoryGroup.Yesterday -> R.string.history_group_yesterday
    HistoryGroup.ThisWeek -> R.string.history_group_this_week
    HistoryGroup.Earlier -> R.string.history_group_earlier
}

private fun List<LibraryComicDetails>.authorFor(comic: RecentComic): String? =
    firstOrNull { it.sourceType == comic.sourceType && it.path == comic.path }
        ?.metadata
        ?.writers
        ?.joinToString()
        ?.takeIf(String::isNotBlank)
