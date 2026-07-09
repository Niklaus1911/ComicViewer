package com.comicviewer

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile

private const val MAX_DOCUMENT_DISPLAY_NAME_CHARS = 512

data class ComicDocument(
    val uri: Uri,
    val displayName: String,
)

fun archiveDocumentFor(context: Context, uri: Uri): ComicDocument =
    ComicDocument(
        uri = uri,
        displayName = context.queryDocumentDisplayName(uri)
            ?: uri.fallbackDocumentName()
            ?: context.getString(R.string.unnamed_comic_archive),
    )

fun folderDocumentFor(context: Context, uri: Uri): ComicDocument =
    ComicDocument(
        uri = uri,
        displayName = runCatching { DocumentFile.fromTreeUri(context, uri)?.name }
            .getOrNull()
            .normalizedDocumentName()
            ?: uri.fallbackDocumentName()
            ?: context.getString(R.string.unnamed_comic_folder),
    )

fun takePersistableReadPermission(context: Context, uri: Uri): Boolean {
    if (uri.scheme != ContentResolver.SCHEME_CONTENT) return false
    val alreadyPersisted = runCatching {
        context.contentResolver.persistedUriPermissions.any { permission ->
            permission.uri == uri && permission.isReadPermission
        }
    }.getOrDefault(false)
    if (alreadyPersisted) return true

    return runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }.isSuccess
}

fun releasePersistableReadPermission(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.releasePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
}

private fun Context.queryDocumentDisplayName(uri: Uri): String? =
    runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameColumn >= 0 && cursor.moveToFirst()) cursor.getString(nameColumn) else null
        }
    }.getOrNull().normalizedDocumentName()

private fun Uri.fallbackDocumentName(): String? =
    lastPathSegment
        ?.substringAfterLast('/')
        .normalizedDocumentName()

private fun String?.normalizedDocumentName(): String? =
    this
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.take(MAX_DOCUMENT_DISPLAY_NAME_CHARS)
