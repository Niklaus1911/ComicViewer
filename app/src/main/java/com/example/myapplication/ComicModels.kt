package com.example.myapplication

import android.net.Uri

sealed interface ComicSource {
    val displayName: String

    data class Archive(
        val uri: Uri,
        override val displayName: String,
    ) : ComicSource

    data class Folder(
        val uri: Uri,
        override val displayName: String,
    ) : ComicSource

    data class LocalArchive(
        val filePath: String,
        override val displayName: String,
    ) : ComicSource

    data class LocalFolder(
        val folderPath: String,
        override val displayName: String,
    ) : ComicSource
}

data class ComicPage(
    val index: Int,
    val displayName: String,
    val relativePath: String,
    val imageUri: Uri,
    val width: Int,
    val height: Int,
)

data class ComicBook(
    val title: String,
    val source: ComicSource,
    val pages: List<ComicPage>,
)

sealed interface ReaderUiState {
    data object Empty : ReaderUiState
    data object Loading : ReaderUiState
    data class Ready(
        val book: ComicBook,
        val pageIndex: Int,
    ) : ReaderUiState

    data class Error(val message: String) : ReaderUiState
}
