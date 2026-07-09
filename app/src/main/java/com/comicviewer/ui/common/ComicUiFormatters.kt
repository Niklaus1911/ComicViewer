package com.comicviewer.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.comicviewer.BrowserEntryType
import com.comicviewer.R
import com.comicviewer.RecentComic
import com.comicviewer.RecentComicSourceType
import com.comicviewer.coercePageIndex

@Composable
internal fun BrowserEntryType.labelText(): String = when (this) {
    BrowserEntryType.Directory -> stringResource(R.string.entry_type_directory)
    BrowserEntryType.Archive -> stringResource(R.string.entry_type_archive)
    BrowserEntryType.ImageFile -> stringResource(R.string.entry_type_image)
    BrowserEntryType.UnsupportedFile -> stringResource(R.string.entry_type_unsupported)
}

internal fun BrowserEntryType.icon(): ImageVector = when (this) {
    BrowserEntryType.Directory -> Icons.Filled.Folder
    BrowserEntryType.Archive -> Icons.Filled.Archive
    BrowserEntryType.ImageFile -> Icons.Filled.Image
    BrowserEntryType.UnsupportedFile -> Icons.Filled.Description
}

internal fun RecentComicSourceType.icon(): ImageVector = when (this) {
    RecentComicSourceType.LocalArchive -> Icons.Filled.Archive
    RecentComicSourceType.LocalFolder -> Icons.Filled.Folder
    RecentComicSourceType.DocumentArchive -> Icons.Filled.Archive
    RecentComicSourceType.DocumentFolder -> Icons.Filled.Folder
}

@Composable
internal fun RecentComic.progressText(): String = pageProgressText(
    lastPageIndex = lastPageIndex,
    pageCount = pageCount,
)

@Composable
internal fun RecentComic.subtitleText(): String = subtitleText(
    progressText = progressText(),
    sourceType = sourceType,
)

@Composable
private fun pageProgressText(lastPageIndex: Int, pageCount: Int): String {
    if (pageCount <= 0) return stringResource(R.string.page_one)
    return stringResource(R.string.page_progress, coercePageIndex(lastPageIndex, pageCount) + 1, pageCount)
}

private fun subtitleText(progressText: String, sourceType: RecentComicSourceType): String =
    "$progressText • ${sourceType.storageName.replace('_', ' ')}"
