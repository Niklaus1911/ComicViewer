package com.comicviewer.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comicviewer.LibraryComic
import com.comicviewer.LibraryComicDetails
import com.comicviewer.LibraryComicMetadata
import com.comicviewer.R
import com.comicviewer.RecentComic
import com.comicviewer.RecentComicSourceType
import com.comicviewer.billing.ProBillingState
import com.comicviewer.coercePageIndex
import com.comicviewer.detailsFor
import com.comicviewer.matchingRecentComic
import com.comicviewer.metadataFor
import com.comicviewer.resolvedComicPageCount
import com.comicviewer.ui.app.ComicDetailsTarget
import com.comicviewer.ui.browser.RecentComicThumbnail
import com.comicviewer.ui.common.fileSizeText
import kotlin.math.roundToInt

@Composable
fun ComicDetailsScreen(
    target: ComicDetailsTarget,
    libraryComics: List<LibraryComic>,
    libraryMetadata: List<LibraryComicMetadata>,
    libraryDetails: List<LibraryComicDetails>,
    recentComics: List<RecentComic>,
    proBillingState: ProBillingState,
    isDebugBuild: Boolean,
    debugProUnlocked: Boolean,
    onDebugProUnlockedChanged: (Boolean) -> Unit,
    onBuyPro: () -> Unit,
    onRestorePro: () -> Unit,
    onReadLibraryComic: (LibraryComic) -> Unit,
    onReadRecentComic: (RecentComic) -> Unit,
    onToggleFavorite: (LibraryComic) -> Unit,
    onSetTags: (LibraryComic, List<String>) -> Unit,
    onRemoveComic: (LibraryComic) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val libraryComic = when (target) {
        is ComicDetailsTarget.Library -> target.comic
        is ComicDetailsTarget.Recent -> libraryComics.firstOrNull {
            it.sourceType == target.comic.sourceType && it.path == target.comic.path
        }
    }
    val recentComic = when (target) {
        is ComicDetailsTarget.Library -> target.comic.matchingRecentComic(recentComics)
        is ComicDetailsTarget.Recent -> currentRecentComicForDetails(target.comic, recentComics)
    }
    val title = libraryComic?.title ?: recentComic?.title.orEmpty()
    val displayName = libraryComic?.displayName ?: recentComic?.displayName.orEmpty()
    val sourceType = libraryComic?.sourceType ?: recentComic?.sourceType
    val details = libraryComic?.let { detailsFor(it, libraryDetails) }
        ?: recentComic?.let { recent ->
            libraryDetails.firstOrNull { it.sourceType == recent.sourceType && it.path == recent.path }
        }
    val metadata = details?.metadata
    val userMetadata = libraryComic?.let { comic ->
        metadataFor(comic, if (proBillingState.isProUnlocked) libraryMetadata else emptyList())
    }
    val pageCount = resolvedComicPageCount(details, recentComic)
    val progress = recentComic?.progressFraction()
    val authors = metadata?.writers?.joinToString()?.takeIf(String::isNotBlank)
    val tags = (metadata?.genres.orEmpty() + userMetadata?.tags.orEmpty()).distinct()
    val archiveFormat = sourceType
        ?.takeIf {
            it == RecentComicSourceType.LocalArchive || it == RecentComicSourceType.DocumentArchive
        }
        ?.let { displayName.substringAfterLast('.', "").uppercase().takeIf(String::isNotBlank) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }
    var showProDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 12.dp, end = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                }
                Spacer(Modifier.weight(1f))
                if (libraryComic != null) {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options))
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.library_edit_tags)) },
                                leadingIcon = { Icon(Icons.Filled.Edit, null) },
                                onClick = {
                                    menuExpanded = false
                                    if (proBillingState.isProUnlocked) showTagDialog = true else showProDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.remove)) },
                                leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                onClick = {
                                    menuExpanded = false
                                    showRemoveDialog = true
                                },
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier
                        .width(154.dp)
                        .aspectRatio(0.72f),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shadowElevation = 4.dp,
                ) {
                    when (target) {
                        is ComicDetailsTarget.Library -> LibraryComicCover(libraryComic = target.comic)
                        is ComicDetailsTarget.Recent -> RecentComicThumbnail(
                            recentComic = target.comic,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = metadata?.title ?: title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                authors?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = listOfNotNull(
                        pageCount?.let { pluralStringResource(R.plurals.comic_pages, it, it) },
                        fileSizeText(details?.fileSizeBytes),
                        archiveFormat,
                    ).joinToString("  •  "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (tags.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        tags.forEach { tag -> DetailChip(tag) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            when (target) {
                                is ComicDetailsTarget.Library -> onReadLibraryComic(target.comic)
                                is ComicDetailsTarget.Recent -> recentComic?.let(onReadRecentComic)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(
                            stringResource(
                                if (recentComic != null && recentComic.lastPageIndex > 0) {
                                    R.string.continue_reading
                                } else {
                                    R.string.start_reading
                                },
                            ),
                        )
                    }
                    if (libraryComic != null) {
                        Surface(
                            modifier = Modifier
                                .size(52.dp)
                                .clickable {
                                    if (proBillingState.isProUnlocked) onToggleFavorite(libraryComic) else showProDialog = true
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (userMetadata?.favorite == true) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                    contentDescription = stringResource(
                                        if (userMetadata?.favorite == true) R.string.library_remove_favorite else R.string.library_add_favorite,
                                    ),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.comic_details),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                metadata?.publisher?.let { DetailRow(stringResource(R.string.comic_publisher), it) }
                metadata?.year?.let { DetailRow(stringResource(R.string.comic_published), it.toString()) }
                metadata?.language?.let { DetailRow(stringResource(R.string.comic_language), it) }
                pageCount?.let { DetailRow(stringResource(R.string.comic_pages_label), it.toString()) }
                if (metadata == null && details != null) {
                    Text(
                        stringResource(R.string.comic_metadata_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                progress?.let {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.comic_read_progress),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            stringResource(R.string.comic_progress_percent, (it * 100).roundToInt()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                }
                metadata?.summary?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showRemoveDialog && libraryComic != null) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text(stringResource(R.string.remove_from_library_title)) },
            text = { Text(stringResource(R.string.remove_from_library_message, libraryComic.title)) },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveDialog = false
                    onRemoveComic(libraryComic)
                }) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showTagDialog && libraryComic != null) {
        var tagsText by remember(libraryComic, showTagDialog) {
            mutableStateOf(userMetadata?.tags.orEmpty().joinToString(", "))
        }
        AlertDialog(
            onDismissRequest = { showTagDialog = false },
            title = { Text(stringResource(R.string.library_edit_tags)) },
            text = {
                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    label = { Text(stringResource(R.string.library_tags_input_label)) },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onSetTags(libraryComic, tagsText.split(','))
                    showTagDialog = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showTagDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
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
private fun DetailChip(text: String) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1.35f),
        )
    }
}

private fun RecentComic.progressFraction(): Float? {
    if (pageCount <= 0) return null
    return ((coercePageIndex(lastPageIndex, pageCount) + 1).toFloat() / pageCount).coerceIn(0f, 1f)
}

internal fun currentRecentComicForDetails(
    selectedComic: RecentComic,
    recentComics: List<RecentComic>,
): RecentComic = recentComics.firstOrNull { comic ->
    comic.sourceType == selectedComic.sourceType && comic.path == selectedComic.path
} ?: selectedComic
