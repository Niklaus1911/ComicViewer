package com.comicviewer

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile

private const val MAX_DOCUMENT_DISPLAY_NAME_CHARS = 512

data class ComicDocument(
    val uri: Uri,
    val displayName: String,
)

data class ComicDocumentEntry(
    val uri: Uri,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val lastModifiedAt: Long?,
) {
    val isDirectory: Boolean
        get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR

    val isFile: Boolean
        get() = !isDirectory
}

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
        displayName = context.queryDocumentDisplayName(uri)
            ?: runCatching { DocumentFile.fromTreeUri(context, uri)?.name }
            .getOrNull()
            .normalizedDocumentName()
            ?: uri.fallbackDocumentName()
            ?: context.getString(R.string.unnamed_comic_folder),
    )

internal fun listDocumentChildren(
    context: Context,
    directoryUri: Uri,
): List<ComicDocumentEntry> {
    if (directoryUri.scheme != ContentResolver.SCHEME_CONTENT) return emptyList()
    val documentId = runCatching { DocumentsContract.getDocumentId(directoryUri) }.getOrNull()
        ?: runCatching { DocumentsContract.getTreeDocumentId(directoryUri) }.getOrNull()
        ?: return emptyList()
    val childrenUri = runCatching {
        DocumentsContract.buildChildDocumentsUriUsingTree(directoryUri, documentId)
    }.getOrNull() ?: return emptyList()
    val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    )

    return runCatching {
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            val modifiedColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            buildList {
                while (cursor.moveToNext()) {
                    if (idColumn < 0 || cursor.isNull(idColumn)) continue
                    val childUri = DocumentsContract.buildDocumentUriUsingTree(
                        directoryUri,
                        cursor.getString(idColumn),
                    )
                    add(
                        ComicDocumentEntry(
                            uri = childUri,
                            displayName = if (nameColumn >= 0 && !cursor.isNull(nameColumn)) {
                                cursor.getString(nameColumn).normalizedDocumentName()
                            } else {
                                null
                            } ?: childUri.fallbackDocumentName().orEmpty(),
                            mimeType = if (mimeColumn >= 0 && !cursor.isNull(mimeColumn)) {
                                cursor.getString(mimeColumn)
                            } else {
                                null
                            },
                            sizeBytes = if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) {
                                cursor.getLong(sizeColumn).takeIf { it >= 0L }
                            } else {
                                null
                            },
                            lastModifiedAt = if (modifiedColumn >= 0 && !cursor.isNull(modifiedColumn)) {
                                cursor.getLong(modifiedColumn).takeIf { it > 0L }
                            } else {
                                null
                            },
                        ),
                    )
                }
            }
        }.orEmpty()
    }.getOrDefault(emptyList())
}

internal fun canReadDocumentDirectory(context: Context, uri: Uri): Boolean =
    runCatching {
        val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
            ?: DocumentsContract.getTreeDocumentId(uri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, documentId)
        context.contentResolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
            null,
            null,
            null,
        )?.use { true } ?: false
    }.getOrDefault(false)

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
