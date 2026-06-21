package com.comicviewer

import android.content.Context
import androidx.core.net.toUri
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val BROWSER_THUMBNAIL_CACHE_DIR_NAME = "browser_thumbnails"
private const val LIBRARY_THUMBNAIL_CACHE_DIR_NAME = "library_thumbnails"
private const val MAX_ARCHIVE_THUMBNAIL_BYTES = 32L * 1024L * 1024L

enum class BrowserEntryType {
    Directory,
    Archive,
    ImageFile,
    UnsupportedFile,
}

data class BrowserEntry(
    val name: String,
    val path: String,
    val type: BrowserEntryType,
    val thumbnailUri: android.net.Uri?,
) {
    val canOpen: Boolean
        get() = type == BrowserEntryType.Directory ||
            type == BrowserEntryType.Archive
}

data class BrowserDirectory(
    val path: String,
    val displayPath: String,
    val parentPath: String?,
    val entries: List<BrowserEntry>,
)

suspend fun loadBrowserDirectory(context: Context, directory: File): BrowserDirectory = withContext(Dispatchers.IO) {
    if (!directory.isDirectory || !directory.canRead()) {
        throw ComicLoadException("Cartella non leggibile.")
    }

    val thumbnailCacheDir = resetBrowserThumbnailCache(context)
    val entries = directory.listFiles().orEmpty()
        .filter { file -> file.canRead() }
        .sortedWith(BrowserFileComparator)
        .map { file -> file.toBrowserEntry(thumbnailCacheDir) }

    BrowserDirectory(
        path = directory.absolutePath,
        displayPath = directory.absolutePath,
        parentPath = directory.parentFile?.takeIf { it.canRead() }?.absolutePath,
        entries = entries,
    )
}

fun browserEntryTypeFor(
    name: String,
    isDirectory: Boolean,
    isImageFile: Boolean,
): BrowserEntryType = when {
    isDirectory -> BrowserEntryType.Directory
    isSupportedArchiveName(name) -> BrowserEntryType.Archive
    isImageFile -> BrowserEntryType.ImageFile
    else -> BrowserEntryType.UnsupportedFile
}

fun compareBrowserEntries(
    leftName: String,
    leftIsDirectory: Boolean,
    rightName: String,
    rightIsDirectory: Boolean,
): Int {
    if (leftIsDirectory != rightIsDirectory) {
        return if (leftIsDirectory) -1 else 1
    }
    return compareComicPageNames(leftName, rightName)
}

fun filterBrowserEntries(
    entries: List<BrowserEntry>,
    query: String,
): List<BrowserEntry> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return entries

    return entries.filter { entry ->
        entry.name.contains(normalizedQuery, ignoreCase = true)
    }
}

suspend fun loadLibraryComicThumbnailUri(context: Context, libraryComic: LibraryComic): android.net.Uri? =
    withContext(Dispatchers.IO) {
        when (libraryComic.sourceType) {
            RecentComicSourceType.LocalArchive -> {
                val cacheDir = File(context.cacheDir, LIBRARY_THUMBNAIL_CACHE_DIR_NAME).apply { mkdirs() }
                File(libraryComic.path).extractArchiveThumbnailTo(cacheDir)?.toUri()
            }

            RecentComicSourceType.LocalFolder -> firstLocalFolderCoverFile(File(libraryComic.path))?.toUri()
        }
    }

fun firstLocalFolderCoverFile(folder: File): File? =
    if (!folder.isDirectory || !folder.canRead()) {
        null
    } else {
        folder.listFiles().orEmpty()
            .filter { file -> file.isFile && !isHiddenComicPath(file.name) && detectImageInfo(file) != null }
            .sortedWith { left, right -> compareComicPageNames(left.name, right.name) }
            .firstOrNull()
    }

private fun File.toBrowserEntry(thumbnailCacheDir: File): BrowserEntry {
    val fileImageInfo = if (isFile) detectImageInfo(this) else null
    val type = browserEntryTypeFor(
        name = name,
        isDirectory = isDirectory,
        isImageFile = fileImageInfo != null,
    )
    val thumbnailFile = when (type) {
        BrowserEntryType.ImageFile -> this
        BrowserEntryType.Archive -> extractArchiveThumbnailTo(thumbnailCacheDir)
        BrowserEntryType.Directory,
        BrowserEntryType.UnsupportedFile,
        -> null
    }

    return BrowserEntry(
        name = name.ifBlank { absolutePath },
        path = absolutePath,
        type = type,
        thumbnailUri = thumbnailFile?.toUri(),
    )
}

private fun File.extractArchiveThumbnailTo(cacheDir: File): File? =
    runCatching {
        ZipFile(this).use { zipFile ->
            val candidate = zipFile.firstSupportedImageEntry() ?: return@use null
            val outputFile = File(cacheDir, "${absolutePath.hashCode()}_thumbnail.${candidate.format.extension}")

            zipFile.getInputStream(candidate.entry).use { input ->
                copyToFileBounded(
                    inputStream = input,
                    outputFile = outputFile,
                    maxBytes = MAX_ARCHIVE_THUMBNAIL_BYTES,
                )
            }
            outputFile
        }
    }.getOrNull()

private fun ZipFile.firstSupportedImageEntry(): ArchiveThumbnailCandidate? {
    var bestCandidate: ArchiveThumbnailCandidate? = null
    val entries = entries()

    while (entries.hasMoreElements()) {
        val entry = entries.nextElement()
        val entryName = entry.name.replace('\\', '/')
        if (entry.isDirectory || isHiddenComicPath(entryName)) continue

        val format = getInputStream(entry).use { input ->
            detectImageFormat(readImageHeader(input))
        } ?: continue

        val candidate = ArchiveThumbnailCandidate(
            entry = entry,
            entryName = entryName,
            format = format,
        )
        val currentBest = bestCandidate
        if (currentBest == null || compareComicPageNames(candidate.entryName, currentBest.entryName) < 0) {
            bestCandidate = candidate
        }
    }

    return bestCandidate
}

private fun resetBrowserThumbnailCache(context: Context): File =
    File(context.cacheDir, BROWSER_THUMBNAIL_CACHE_DIR_NAME).apply {
        deleteRecursively()
        mkdirs()
    }

private object BrowserFileComparator : Comparator<File> {
    override fun compare(left: File, right: File): Int =
        compareBrowserEntries(
            leftName = left.name,
            leftIsDirectory = left.isDirectory,
            rightName = right.name,
            rightIsDirectory = right.isDirectory,
        )
}

private data class ArchiveThumbnailCandidate(
    val entry: ZipEntry,
    val entryName: String,
    val format: ImageFormat,
)
