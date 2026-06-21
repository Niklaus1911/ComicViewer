package com.comicviewer.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.ui.graphics.vector.ImageVector
import com.comicviewer.BrowserEntryType
import com.comicviewer.RecentComic
import com.comicviewer.RecentComicSourceType
import com.comicviewer.coercePageIndex

internal fun BrowserEntryType.label(): String = when (this) {
    BrowserEntryType.Directory -> "Cartella"
    BrowserEntryType.Archive -> "Archivio comic"
    BrowserEntryType.ImageFile -> "Immagine"
    BrowserEntryType.UnsupportedFile -> "File non supportato"
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
}

internal fun RecentComic.progressLabel(): String {
    if (pageCount <= 0) return "Pagina 1"
    return "Pagina ${coercePageIndex(lastPageIndex, pageCount) + 1}/$pageCount"
}

internal fun RecentComic.subtitleLabel(): String =
    "${progressLabel()} • ${sourceType.storageName.replace('_', ' ')}"
