package com.comicviewer

import android.content.Context
import java.net.URI
import java.net.URLDecoder

private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
private const val DOWNLOADS_AUTHORITY = "com.android.providers.downloads.documents"
// Each Android user has their own internal storage, /storage/emulated/0 for the first.
private val INTERNAL_STORAGE_PATH = Regex("^/storage/emulated/\\d+(/.*)?$")
private val SD_CARD_PATH = Regex("^/storage/[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}(/.*)?$")

/** What the places a comic can be stored are called, in the app's language. */
internal data class StorageNames(
    val internalStorage: String,
    val sdCard: String,
    val downloads: String,
)

/**
 * Where a comic's file or folder is, written for people, such as "Internal storage/Comics/Storm
 * Beacon.cbz". A document from an app that keeps its paths to itself, such as a cloud drive, is
 * named by that app ([providerName]) or, failing that, by its provider authority.
 */
internal fun readableComicLocation(
    sourceType: RecentComicSourceType,
    path: String,
    names: StorageNames,
    providerName: (authority: String) -> String? = { null },
): String = when (sourceType) {
    RecentComicSourceType.LocalArchive,
    RecentComicSourceType.LocalFolder,
    -> readableFilePath(path, names)

    RecentComicSourceType.DocumentArchive,
    RecentComicSourceType.DocumentFolder,
    -> readableDocumentLocation(path, names, providerName)
}

internal fun Context.comicLocationText(sourceType: RecentComicSourceType, path: String): String =
    readableComicLocation(
        sourceType = sourceType,
        path = path,
        names = StorageNames(
            internalStorage = getString(R.string.storage_internal),
            sdCard = getString(R.string.storage_sd_card),
            downloads = getString(R.string.storage_downloads),
        ),
        providerName = { authority ->
            runCatching {
                packageManager.resolveContentProvider(authority, 0)?.loadLabel(packageManager)?.toString()
            }.getOrNull()?.takeIf(String::isNotBlank)
        },
    )

private fun readableFilePath(path: String, names: StorageNames): String =
    INTERNAL_STORAGE_PATH.matchEntire(path)?.let { match -> names.internalStorage + match.groupValues[1] }
        ?: SD_CARD_PATH.matchEntire(path)?.let { match -> names.sdCard + match.groupValues[1] }
        ?: path

private fun readableDocumentLocation(
    uri: String,
    names: StorageNames,
    providerName: (authority: String) -> String?,
): String {
    val parsed = runCatching { URI(uri) }.getOrNull() ?: return uri
    val authority = parsed.rawAuthority ?: return uri
    val documentId = parsed.documentId() ?: return providerName(authority) ?: authority
    return when (authority) {
        // Document IDs here are "volume:relative/path", the volume being "primary" or an SD card's ID.
        EXTERNAL_STORAGE_AUTHORITY -> {
            val volume = documentId.substringBefore(':')
            val relativePath = documentId.substringAfter(':', missingDelimiterValue = "")
            val root = when (volume) {
                "primary" -> names.internalStorage
                "home" -> "${names.internalStorage}/Documents"
                else -> names.sdCard
            }
            if (relativePath.isEmpty()) root else "$root/$relativePath"
        }
        // Downloads gives a real path only for some files ("raw:/storage/..."); others are just numbered.
        DOWNLOADS_AUTHORITY ->
            if (documentId.startsWith("raw:")) readableFilePath(documentId.removePrefix("raw:"), names) else names.downloads
        else -> providerName(authority) ?: authority
    }
}

/** The document ID of a document URI, or of a tree URI's root, decoded. */
private fun URI.documentId(): String? {
    val segments = rawPath?.split('/')?.filter(String::isNotEmpty).orEmpty()
    val index = segments.lastIndexOf("document").takeIf { it >= 0 }
        ?: segments.indexOf("tree").takeIf { it >= 0 }
        ?: return null
    val encoded = segments.getOrNull(index + 1) ?: return null
    return runCatching { URLDecoder.decode(encoded.replace("+", "%2B"), "UTF-8") }.getOrNull()
}
