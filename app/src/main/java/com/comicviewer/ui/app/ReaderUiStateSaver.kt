package com.comicviewer.ui.app

import android.net.Uri
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.comicviewer.ComicBook
import com.comicviewer.ComicPage
import com.comicviewer.ComicSource
import com.comicviewer.ReaderUiState
import com.comicviewer.coercePageIndex

val ReaderUiStateSaver = listSaver<ReaderUiState, String>(
    save = { state ->
        when (state) {
            ReaderUiState.Empty,
            ReaderUiState.Loading,
            -> listOf("empty")

            is ReaderUiState.Error -> listOf("error", state.message)

            is ReaderUiState.Ready -> buildList {
                val source = state.book.source
                val sourceType = when (source) {
                    is ComicSource.Archive -> "archive"
                    is ComicSource.Folder -> "folder"
                    is ComicSource.LocalArchive -> "local_archive"
                    is ComicSource.LocalFolder -> "local_folder"
                }
                val sourceLocation = when (source) {
                    is ComicSource.Archive -> source.uri.toString()
                    is ComicSource.Folder -> source.uri.toString()
                    is ComicSource.LocalArchive -> source.filePath
                    is ComicSource.LocalFolder -> source.folderPath
                }
                add("ready")
                add(sourceType)
                add(sourceLocation)
                add(source.displayName)
                add(state.book.title)
                add(state.pageIndex.toString())
                add(state.book.pages.size.toString())
                state.book.pages.forEach { page ->
                    add(page.index.toString())
                    add(page.displayName)
                    add(page.relativePath)
                    add(page.imageUri.toString())
                    add(page.width.toString())
                    add(page.height.toString())
                }
            }
        }
    },
    restore = { savedValues ->
        when (savedValues.firstOrNull()) {
            "error" -> ReaderUiState.Error(savedValues.getOrNull(1).orEmpty())
            "ready" -> restoreReadyState(savedValues)
            else -> ReaderUiState.Empty
        }
    },
)

private fun restoreReadyState(savedValues: List<String>): ReaderUiState {
    val sourceType = savedValues.getOrNull(1) ?: return ReaderUiState.Empty
    val sourceLocation = savedValues.getOrNull(2) ?: return ReaderUiState.Empty
    val sourceDisplayName = savedValues.getOrNull(3) ?: return ReaderUiState.Empty
    val title = savedValues.getOrNull(4) ?: sourceDisplayName
    val pageIndex = savedValues.getOrNull(5)?.toIntOrNull() ?: 0
    val pageCount = savedValues.getOrNull(6)?.toIntOrNull() ?: 0
    val pageValues = savedValues.drop(7)

    val pages = (0 until pageCount).mapNotNull { pageOffset ->
        val valueOffset = pageOffset * 6
        val index = pageValues.getOrNull(valueOffset)?.toIntOrNull()
        val displayName = pageValues.getOrNull(valueOffset + 1)
        val relativePath = pageValues.getOrNull(valueOffset + 2)
        val imageUri = pageValues.getOrNull(valueOffset + 3)?.let(Uri::parse)
        val width = pageValues.getOrNull(valueOffset + 4)?.toIntOrNull() ?: 0
        val height = pageValues.getOrNull(valueOffset + 5)?.toIntOrNull() ?: 0
        if (index != null && displayName != null && imageUri != null) {
            ComicPage(
                index = index,
                displayName = displayName,
                relativePath = relativePath ?: displayName,
                imageUri = imageUri,
                width = width,
                height = height,
            )
        } else {
            null
        }
    }

    if (pages.isEmpty()) return ReaderUiState.Empty

    val source = when (sourceType) {
        "archive" -> ComicSource.Archive(uri = Uri.parse(sourceLocation), displayName = sourceDisplayName)
        "folder" -> ComicSource.Folder(uri = Uri.parse(sourceLocation), displayName = sourceDisplayName)
        "local_archive" -> ComicSource.LocalArchive(filePath = sourceLocation, displayName = sourceDisplayName)
        "local_folder" -> ComicSource.LocalFolder(folderPath = sourceLocation, displayName = sourceDisplayName)
        else -> return ReaderUiState.Empty
    }

    return ReaderUiState.Ready(
        book = ComicBook(title = title, source = source, pages = pages),
        pageIndex = coercePageIndex(pageIndex, pages.size),
    )
}
