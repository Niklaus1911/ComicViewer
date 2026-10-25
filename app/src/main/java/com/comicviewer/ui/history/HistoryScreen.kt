package com.comicviewer.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comicviewer.ui.common.tabScreen
import com.comicviewer.LibraryComicDetails
import com.comicviewer.R
import com.comicviewer.RecentComic
import com.comicviewer.coercePageIndex
import com.comicviewer.ui.browser.RecentComicThumbnail
import com.comicviewer.ui.common.ReferenceScreenHeader
import com.comicviewer.ui.common.ReferenceSectionHeader
import com.comicviewer.ui.common.StatusContent
import com.comicviewer.ui.common.absoluteDateText
import kotlin.math.roundToInt

@Composable
fun HistoryScreen(
    recentComics: List<RecentComic>,
    libraryDetails: List<LibraryComicDetails>,
    onOpenComic: (RecentComic) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAllContinue by remember { mutableStateOf(false) }
    var showAllCompleted by remember { mutableStateOf(false) }
    val sorted = recentComics.sortedByDescending(RecentComic::lastOpenedAt)
    val unfinished = sorted.filterNot(RecentComic::isCompleted)
    val visibleContinue = if (showAllContinue) unfinished else unfinished.take(3)
    val overflowUnfinishedKeys = if (showAllContinue) {
        emptySet()
    } else {
        unfinished.drop(3).mapTo(mutableSetOf()) { "${it.sourceType.storageName}|${it.path}" }
    }
    val recentlyRead = sorted.filter { comic ->
        comic.isCompleted() || "${comic.sourceType.storageName}|${comic.path}" in overflowUnfinishedKeys
    }
    val visibleCompleted = if (showAllCompleted) recentlyRead else recentlyRead.take(4)

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.tabScreen),
        ) {
            ReferenceScreenHeader(
                title = stringResource(R.string.reading_history),
            )
            if (recentComics.isEmpty()) {
                StatusContent(
                    icon = Icons.Filled.History,
                    title = stringResource(R.string.history_empty_title),
                    message = stringResource(R.string.history_empty_message),
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (unfinished.isNotEmpty()) {
                        item {
                            ReferenceSectionHeader(
                                title = stringResource(R.string.continue_reading),
                                action = if (unfinished.size > 3) {
                                    {
                                        TextButton(onClick = { showAllContinue = !showAllContinue }) {
                                            Text(stringResource(if (showAllContinue) R.string.show_less else R.string.see_all))
                                        }
                                    }
                                } else null,
                            )
                        }
                        items(visibleContinue, key = { "continue:${it.sourceType.storageName}:${it.path}" }) { comic ->
                            HistoryProgressCard(
                                comic = comic,
                                author = libraryDetails.authorFor(comic),
                                onClick = { onOpenComic(comic) },
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                        }
                    }
                    if (recentlyRead.isNotEmpty()) {
                        item {
                            ReferenceSectionHeader(
                                title = stringResource(R.string.recently_read),
                                action = if (recentlyRead.size > 4) {
                                    {
                                        TextButton(onClick = { showAllCompleted = !showAllCompleted }) {
                                            Text(stringResource(if (showAllCompleted) R.string.show_less else R.string.see_all))
                                        }
                                    }
                                } else null,
                            )
                        }
                        items(visibleCompleted, key = { "completed:${it.sourceType.storageName}:${it.path}" }) { comic ->
                            HistoryRecentCard(
                                comic = comic,
                                author = libraryDetails.authorFor(comic),
                                onClick = { onOpenComic(comic) },
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryProgressCard(
    comic: RecentComic,
    author: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = comic.progressFraction()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RecentComicThumbnail(
                recentComic = comic,
                modifier = Modifier.size(width = 68.dp, height = 94.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    comic.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                author?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${coercePageIndex(comic.lastPageIndex, comic.pageCount) + 1} / ${comic.pageCount}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(R.string.comic_progress_percent, (progress * 100).roundToInt()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(7.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }
        }
    }
}

@Composable
private fun HistoryRecentCard(
    comic: RecentComic,
    author: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val completed = comic.isCompleted()
    val progress = comic.progressFraction()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RecentComicThumbnail(
                recentComic = comic,
                modifier = Modifier.size(width = 58.dp, height = 76.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    comic.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                author?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(
                            if (completed) R.string.history_finished_on else R.string.history_read_on,
                            absoluteDateText(comic.completedAt ?: comic.lastOpenedAt),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (!completed) {
                        Text(
                            stringResource(R.string.comic_progress_percent, (progress * 100).roundToInt()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!completed) {
                    Spacer(Modifier.height(5.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                }
            }
            if (completed) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.padding(9.dp).size(18.dp),
                    )
                }
            }
        }
    }
}

private fun RecentComic.isCompleted(): Boolean =
    completedAt != null || (pageCount > 0 && coercePageIndex(lastPageIndex, pageCount) == pageCount - 1)

private fun RecentComic.progressFraction(): Float {
    if (pageCount <= 0) return 0f
    return ((coercePageIndex(lastPageIndex, pageCount) + 1).toFloat() / pageCount).coerceIn(0f, 1f)
}

private fun List<LibraryComicDetails>.authorFor(comic: RecentComic): String? =
    firstOrNull { it.sourceType == comic.sourceType && it.path == comic.path }
        ?.metadata
        ?.writers
        ?.joinToString()
        ?.takeIf(String::isNotBlank)
