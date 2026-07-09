package com.comicviewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.net.toUri
import java.io.FilterInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val BROWSER_THUMBNAIL_CACHE_DIR_NAME = "browser_thumbnails"
private const val LIBRARY_THUMBNAIL_CACHE_DIR_NAME = "library_thumbnails"
private const val MAX_ARCHIVE_THUMBNAIL_ENTRY_COUNT = 10_000
private const val MAX_ARCHIVE_THUMBNAIL_SOURCE_BYTES = 64L * 1024L * 1024L
private const val MAX_THUMBNAIL_DIMENSION_PX = 512
private const val MAX_THUMBNAIL_OUTPUT_BYTES = 2L * 1024L * 1024L
private const val MAX_THUMBNAIL_CACHE_BYTES = 64L * 1024L * 1024L
private const val MAX_THUMBNAIL_CACHE_FILES = 512
private const val JPEG_THUMBNAIL_QUALITY = 82
private val thumbnailCacheLock = Any()

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
        throw ComicLoadException(ComicLoadError.FolderNotReadable)
    }

    resetBrowserThumbnailCache(context)
    val entries = directory.listFiles().orEmpty()
        .filter { file -> file.canRead() }
        .sortedWith(BrowserFileComparator)
        .map(File::toBrowserEntry)

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
    loadComicThumbnailUri(context, libraryComic.sourceType, libraryComic.path)

suspend fun loadRecentComicThumbnailUri(context: Context, recentComic: RecentComic): android.net.Uri? =
    loadComicThumbnailUri(context, recentComic.sourceType, recentComic.path)

suspend fun loadBrowserArchiveThumbnailUri(context: Context, path: String): android.net.Uri? =
    withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, BROWSER_THUMBNAIL_CACHE_DIR_NAME).apply { mkdirs() }
        File(path).extractArchiveThumbnailTo(cacheDir)?.toUri()
    }

private suspend fun loadComicThumbnailUri(
    context: Context,
    sourceType: RecentComicSourceType,
    path: String,
): android.net.Uri? =
    withContext(Dispatchers.IO) {
        when (sourceType) {
            RecentComicSourceType.LocalArchive -> {
                val cacheDir = File(context.cacheDir, LIBRARY_THUMBNAIL_CACHE_DIR_NAME).apply { mkdirs() }
                synchronized(thumbnailCacheLock) {
                    trimThumbnailCache(cacheDir)
                }
                File(path).extractArchiveThumbnailTo(cacheDir)?.toUri()
            }

            RecentComicSourceType.LocalFolder -> firstLocalFolderCoverFile(File(path))?.toUri()
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

private fun File.toBrowserEntry(): BrowserEntry {
    val fileImageInfo = if (isFile) detectImageInfo(this) else null
    val type = browserEntryTypeFor(
        name = name,
        isDirectory = isDirectory,
        isImageFile = fileImageInfo != null,
    )
    val thumbnailFile = when (type) {
        BrowserEntryType.ImageFile -> this
        BrowserEntryType.Archive -> null
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
            zipFile.writeThumbnail(candidate.entry, cacheDir, absolutePath.hashCode())
        }
    }.getOrNull()

private fun ZipFile.firstSupportedImageEntry(): ArchiveThumbnailCandidate? {
    var bestCandidate: ArchiveThumbnailCandidate? = null
    val entries = entries()
    var visitedEntries = 0

    while (entries.hasMoreElements()) {
        val entry = entries.nextElement()
        visitedEntries += 1
        if (visitedEntries > MAX_ARCHIVE_THUMBNAIL_ENTRY_COUNT) return null
        val entryName = entry.name.replace('\\', '/')
        if (entry.isDirectory || isHiddenComicPath(entryName)) continue
        if (entry.size > MAX_ARCHIVE_THUMBNAIL_SOURCE_BYTES) continue

        val supported = getThumbnailInputStream(entry).use { input ->
            detectImageFormat(readImageHeader(input))
        } != null
        if (!supported) continue

        val candidate = ArchiveThumbnailCandidate(
            entry = entry,
            entryName = entryName,
        )
        val currentBest = bestCandidate
        if (currentBest == null || compareComicPageNames(candidate.entryName, currentBest.entryName) < 0) {
            bestCandidate = candidate
        }
    }

    return bestCandidate
}

private fun ZipFile.writeThumbnail(
    entry: ZipEntry,
    cacheDir: File,
    cacheKey: Int,
): File? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    getThumbnailInputStream(entry).use { input ->
        BitmapFactory.decodeStream(input, null, bounds)
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val options = BitmapFactory.Options().apply {
        inSampleSize = thumbnailSampleSize(
            width = bounds.outWidth,
            height = bounds.outHeight,
        )
    }
    val bitmap = getThumbnailInputStream(entry).use { input ->
        BitmapFactory.decodeStream(input, null, options)
    } ?: return null
    val hasAlpha = bitmap.hasAlpha()
    val outputFile = File(cacheDir, "${cacheKey}_thumbnail.${if (hasAlpha) "png" else "jpg"}")
    val temporaryFile = File.createTempFile("thumbnail_", ".tmp", cacheDir)

    return try {
        val compressed = temporaryFile.outputStream().buffered().use { output ->
            bitmap.compress(
                if (hasAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
                JPEG_THUMBNAIL_QUALITY,
                output,
            )
        }
        if (!compressed || temporaryFile.length() > MAX_THUMBNAIL_OUTPUT_BYTES) {
            return null
        }

        synchronized(thumbnailCacheLock) {
            outputFile.delete()
            if (!temporaryFile.renameTo(outputFile)) {
                temporaryFile.copyTo(outputFile, overwrite = true)
                temporaryFile.delete()
            }
            outputFile.setLastModified(System.currentTimeMillis())
            trimThumbnailCache(cacheDir, protectedFile = outputFile)
        }
        outputFile.takeIf(File::isFile)
    } finally {
        bitmap.recycle()
        temporaryFile.delete()
    }
}

private fun ZipFile.getThumbnailInputStream(entry: ZipEntry): InputStream =
    LimitedInputStream(
        inputStream = getInputStream(entry),
        maxBytes = MAX_ARCHIVE_THUMBNAIL_SOURCE_BYTES,
    )

private class LimitedInputStream(
    inputStream: InputStream,
    maxBytes: Long,
) : FilterInputStream(inputStream) {
    private var remainingBytes = maxBytes.coerceAtLeast(0L)

    override fun read(): Int {
        if (remainingBytes == 0L) return -1
        val value = super.read()
        if (value >= 0) remainingBytes -= 1
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (remainingBytes == 0L) return -1
        val boundedLength = minOf(length.toLong(), remainingBytes).toInt()
        val bytesRead = super.read(buffer, offset, boundedLength)
        if (bytesRead > 0) remainingBytes -= bytesRead
        return bytesRead
    }

    override fun skip(byteCount: Long): Long {
        if (remainingBytes == 0L) return 0L
        val skippedBytes = super.skip(minOf(byteCount, remainingBytes))
        remainingBytes -= skippedBytes
        return skippedBytes
    }
}

internal fun thumbnailSampleSize(
    width: Int,
    height: Int,
    maxDimension: Int = MAX_THUMBNAIL_DIMENSION_PX,
): Int {
    if (width <= 0 || height <= 0 || maxDimension <= 0) return 1
    val largestDimension = maxOf(width, height)
    var sampleSize = 1
    while (largestDimension / sampleSize > maxDimension && sampleSize <= Int.MAX_VALUE / 2) {
        sampleSize *= 2
    }
    return sampleSize
}

internal fun trimThumbnailCache(
    cacheDir: File,
    maxBytes: Long = MAX_THUMBNAIL_CACHE_BYTES,
    maxFiles: Int = MAX_THUMBNAIL_CACHE_FILES,
    protectedFile: File? = null,
) {
    require(maxBytes >= 0L)
    require(maxFiles >= 0)
    val files = cacheDir.listFiles().orEmpty()
        .filter { file -> file.isFile && "_thumbnail." in file.name }
        .sortedBy(File::lastModified)
        .toMutableList()
    var totalBytes = files.sumOf(File::length)

    val iterator = files.iterator()
    while ((totalBytes > maxBytes || files.size > maxFiles) && iterator.hasNext()) {
        val file = iterator.next()
        if (file == protectedFile) continue
        val fileLength = file.length()
        if (file.delete()) {
            totalBytes -= fileLength
            iterator.remove()
        }
    }
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
)
