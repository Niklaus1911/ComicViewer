package com.comicviewer.ui.library

import com.comicviewer.isFinished
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comicviewer.LibraryComic
import com.comicviewer.LibraryComicDetails
import com.comicviewer.LibraryFilter
import com.comicviewer.R
import com.comicviewer.RecentComic
import com.comicviewer.libraryProgressFraction
import com.comicviewer.resolvedComicPageCount
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddComicsSheet(
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
internal fun ComicActionsSheet(
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
    val completed = recentComic?.isFinished() == true
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
internal fun LibraryFilterDialog(
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

private fun LibraryFilter.labelRes(): Int = when (this) {
    LibraryFilter.All -> R.string.library_filter_all
    LibraryFilter.Archives -> R.string.library_filter_archives
    LibraryFilter.Folders -> R.string.library_filter_folders
}
