package com.comicviewer.ui.app

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.core.net.toUri
import com.comicviewer.ComicSource
import com.comicviewer.ReaderUiState

private const val MAX_SAVED_ERROR_MESSAGE_CHARS = 2_000
private const val MAX_SAVED_SOURCE_LOCATION_CHARS = 8_192
private const val MAX_SAVED_SOURCE_DISPLAY_NAME_CHARS = 512

val ReaderUiStateSaver = listSaver<ReaderUiState, String>(
    save = { state -> saveReaderUiState(state) },
    restore = ::restoreReaderUiState,
)

internal fun saveReaderUiState(state: ReaderUiState): List<String> = when (state) {
    ReaderUiState.Empty -> listOf("empty")
    is ReaderUiState.Error -> listOf("error", state.message.take(MAX_SAVED_ERROR_MESSAGE_CHARS))
    is ReaderUiState.Loading -> savedSourceValues(
        source = state.source,
        pageIndex = state.pageIndex,
    )
    is ReaderUiState.Ready -> savedSourceValues(
        source = state.book.source,
        pageIndex = state.pageIndex,
    )
}

internal fun restoreReaderUiState(savedValues: List<String>): ReaderUiState =
    when (savedValues.firstOrNull()) {
        "error" -> ReaderUiState.Error(savedValues.getOrNull(1).orEmpty())
        "source" -> restoreLoadingState(savedValues)
        else -> ReaderUiState.Empty
    }

private fun savedSourceValues(
    source: ComicSource,
    pageIndex: Int,
): List<String> {
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
    if (
        sourceLocation.length > MAX_SAVED_SOURCE_LOCATION_CHARS ||
        source.displayName.length > MAX_SAVED_SOURCE_DISPLAY_NAME_CHARS
    ) {
        return listOf("empty")
    }
    return listOf(
        "source",
        sourceType,
        sourceLocation,
        source.displayName,
        pageIndex.coerceAtLeast(0).toString(),
    )
}

private fun restoreLoadingState(savedValues: List<String>): ReaderUiState {
    val sourceType = savedValues.getOrNull(1) ?: return ReaderUiState.Empty
    val sourceLocation = savedValues.getOrNull(2)?.takeIf(String::isNotBlank)
        ?: return ReaderUiState.Empty
    val sourceDisplayName = savedValues.getOrNull(3) ?: return ReaderUiState.Empty
    val pageIndex = savedValues.getOrNull(4)?.toIntOrNull()?.coerceAtLeast(0) ?: 0

    val source = when (sourceType) {
        "archive" -> ComicSource.Archive(uri = sourceLocation.toUri(), displayName = sourceDisplayName)
        "folder" -> ComicSource.Folder(uri = sourceLocation.toUri(), displayName = sourceDisplayName)
        "local_archive" -> ComicSource.LocalArchive(filePath = sourceLocation, displayName = sourceDisplayName)
        "local_folder" -> ComicSource.LocalFolder(folderPath = sourceLocation, displayName = sourceDisplayName)
        else -> return ReaderUiState.Empty
    }

    return ReaderUiState.Loading(source = source, pageIndex = pageIndex)
}
