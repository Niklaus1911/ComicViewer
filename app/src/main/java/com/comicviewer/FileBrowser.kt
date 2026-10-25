package com.comicviewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.graphics.scale
import androidx.core.net.toUri
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

private const val BROWSER_THUMBNAIL_CACHE_DIR_NAME = "browser_thumbnails"
private const val LIBRARY_THUMBNAIL_CACHE_DIR_NAME = "library_thumbnails"
private const val MAX_ARCHIVE_THUMBNAIL_ENTRY_COUNT = 10_000
private const val MAX_ARCHIVE_THUMBNAIL_SOURCE_BYTES = 64L * 1024L * 1024L
private const val MAX_DOCUMENT_ARCHIVE_THUMBNAIL_BYTES = 256L * 1024L * 1024L
private const val MAX_THUMBNAIL_DIMENSION_PX = 1_024
private const val MAX_THUMBNAIL_OUTPUT_BYTES = 8L * 1024L * 1024L
private const val MAX_THUMBNAIL_CACHE_BYTES = 128L * 1024L * 1024L
private const val MAX_THUMBNAIL_CACHE_FILES = 512
private const val JPEG_THUMBNAIL_QUALITY = 92
private const val THUMBNAIL_CACHE_VERSION = 2
private const val MAX_CONCURRENT_THUMBNAIL_GENERATIONS = 2
private const val HEX_DIGITS = "0123456789abcdef"
private val thumbnailCacheLock = Any()
private val thumbnailGenerationSemaphore = Semaphore(MAX_CONCURRENT_THUMBNAIL_GENERATIONS)

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
    val sizeBytes: Long? = null,
    val lastModifiedAt: Long? = null,
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
    isSupportedComicFileName(name) -> BrowserEntryType.Archive
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

            RecentComicSourceType.DocumentArchive -> {
                val cacheDir = File(context.cacheDir, LIBRARY_THUMBNAIL_CACHE_DIR_NAME).apply { mkdirs() }
                context.extractDocumentArchiveThumbnailTo(path.toUri(), cacheDir)?.toUri()
            }

            RecentComicSourceType.DocumentFolder -> {
                val coroutineContext = currentCoroutineContext()
                listDocumentChildren(context, path.toUri())
                    .asSequence()
                    .filter { entry ->
                        entry.isFile &&
                            !isHiddenComicPath(entry.displayName) &&
                            isSupportedImageName(entry.displayName)
                    }
                    .sortedWith { left, right -> compareComicPageNames(left.displayName, right.displayName) }
                    .firstOrNull { entry ->
                        if (!coroutineContext.isActive) throw CancellationException()
                        detectDocumentImageInfo(context, entry.uri) != null
                    }
                    ?.uri
            }
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
        sizeBytes = length().takeIf { isFile && it >= 0L },
        lastModifiedAt = lastModified().takeIf { it > 0L },
    )
}

private suspend fun File.extractArchiveThumbnailTo(cacheDir: File): File? {
    val coroutineContext = currentCoroutineContext()
    val sourceLength = length()
    val sourceLastModified = lastModified()
    val cacheReusable = sourceLength >= 0L && sourceLastModified > 0L
    val cacheKey = thumbnailCacheIdentity(
        sourcePath = absolutePath,
        sourceLength = sourceLength,
        sourceLastModified = sourceLastModified,
        cacheVersion = THUMBNAIL_CACHE_VERSION,
    )
    if (cacheReusable) {
        findCachedThumbnailFile(cacheDir, cacheKey)?.let { cachedFile -> return cachedFile }
    } else {
        synchronized(thumbnailCacheLock) {
            cachedThumbnailFiles(cacheDir, cacheKey).forEach(File::delete)
        }
    }

    return thumbnailGenerationSemaphore.withPermit {
        (if (cacheReusable) findCachedThumbnailFile(cacheDir, cacheKey) else null) ?: runCatching {
            if (fileLooksLikePdf(this)) {
                openLocalComicPdf(this).use { pdf -> pdf.writeCoverThumbnail(cacheDir, cacheKey) }
            } else {
                openLocalComicArchive(this).use { archive ->
                    archive.reader.writeCoverThumbnail(cacheDir, cacheKey) { coroutineContext.isActive }
                }
            }
        }.getOrElse { throwable ->
            if (throwable is CancellationException) throw throwable
            null
        }
    }
}

private suspend fun Context.extractDocumentArchiveThumbnailTo(
    uri: Uri,
    cacheDir: File,
): File? {
    val coroutineContext = currentCoroutineContext()
    val sourceLength = queryDocumentLong(uri, OpenableColumns.SIZE)
    val sourceLastModified = queryDocumentLong(uri, DocumentsContract.Document.COLUMN_LAST_MODIFIED)
    val cacheReusable = sourceLength != null && sourceLastModified?.let { it > 0L } == true
    val cacheKey = thumbnailCacheIdentity(
        sourcePath = uri.toString(),
        sourceLength = sourceLength ?: -1L,
        sourceLastModified = sourceLastModified ?: -1L,
        cacheVersion = THUMBNAIL_CACHE_VERSION,
    )
    if (cacheReusable) {
        findCachedThumbnailFile(cacheDir, cacheKey)?.let { return it }
    } else {
        synchronized(thumbnailCacheLock) {
            cachedThumbnailFiles(cacheDir, cacheKey).forEach(File::delete)
        }
    }

    return thumbnailGenerationSemaphore.withPermit {
        (if (cacheReusable) findCachedThumbnailFile(cacheDir, cacheKey) else null) ?: runCatching {
            if (documentLooksLikePdf(uri)) {
                // Stream-only PDFs would need a full copy, which a thumbnail is not worth.
                return@runCatching openSeekableDocumentComicPdf(this, uri)?.use { pdf ->
                    pdf.writeCoverThumbnail(cacheDir, cacheKey)
                }
            }
            val seekableArchive = openSeekableDocumentComicArchive(this, uri)
            if (seekableArchive != null) {
                return@runCatching seekableArchive.use { archive ->
                    archive.reader.writeCoverThumbnail(cacheDir, cacheKey) { coroutineContext.isActive }
                }
            }
            // Stream-only providers: scan from the start, as far as the source limit allows.
            val source = contentResolver.openInputStream(uri) ?: return@runCatching null
            ZipInputStream(
                LimitedInputStream(
                    inputStream = source.buffered(),
                    maxBytes = MAX_DOCUMENT_ARCHIVE_THUMBNAIL_BYTES,
                ),
            ).use { archive ->
                var visitedEntries = 0
                var bestEntryName: String? = null
                var bestPageFile: File? = null
                try {
                    while (true) {
                        if (!coroutineContext.isActive) throw CancellationException()
                        val entry = archive.nextEntry ?: break
                        visitedEntries += 1
                        if (visitedEntries > MAX_ARCHIVE_THUMBNAIL_ENTRY_COUNT) return@use null
                        val entryName = entry.name.replace('\\', '/')
                        if (entry.isDirectory || isHiddenComicPath(entryName)) continue
                        if (entry.size > MAX_ARCHIVE_THUMBNAIL_SOURCE_BYTES) continue
                        val currentBestName = bestEntryName
                        if (currentBestName != null && compareComicPageNames(entryName, currentBestName) >= 0) {
                            continue
                        }

                        val header = readImageHeader(archive)
                        if (detectImageFormat(header) == null) continue
                        val pageFile = File.createTempFile("document_cover_", ".img", cacheDir)
                        val validPage = try {
                            copyDocumentArchiveEntry(
                                header = header,
                                input = archive,
                                outputFile = pageFile,
                                shouldContinue = { coroutineContext.isActive },
                            ) && detectImageInfo(pageFile) != null
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (_: Exception) {
                            false
                        }
                        if (validPage) {
                            bestPageFile?.delete()
                            bestPageFile = pageFile
                            bestEntryName = entryName
                        } else {
                            pageFile.delete()
                        }
                    }
                    bestPageFile?.writeThumbnail(cacheDir, cacheKey)
                } finally {
                    bestPageFile?.delete()
                }
            }
        }.getOrElse { throwable ->
            if (throwable is CancellationException) throw throwable
            null
        }
    }
}

private fun ZipArchiveReader.writeCoverThumbnail(
    cacheDir: File,
    cacheKey: String,
    shouldContinue: () -> Boolean,
): File? {
    val entry = firstComicPageEntry(MAX_ARCHIVE_THUMBNAIL_SOURCE_BYTES, shouldContinue) ?: return null
    return writeThumbnail(
        cacheDir = cacheDir,
        cacheKey = cacheKey,
        openInput = { openEntry(entry) },
    )
}

private fun OpenedComicPdf.writeCoverThumbnail(
    cacheDir: File,
    cacheKey: String,
): File? {
    if (pageCount <= 0) return null
    val cover = renderPage(0) { pageWidth, pageHeight ->
        pdfRenderSize(
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            requestedWidth = MAX_THUMBNAIL_DIMENSION_PX,
            requestedHeight = MAX_THUMBNAIL_DIMENSION_PX,
        )
    }
    return writeThumbnailBitmap(cacheDir, cacheKey, cover)
}

private fun File.writeThumbnail(
    cacheDir: File,
    cacheKey: String,
): File? = writeThumbnail(
    cacheDir = cacheDir,
    cacheKey = cacheKey,
    openInput = { inputStream() },
)

private fun writeThumbnail(
    cacheDir: File,
    cacheKey: String,
    openInput: () -> InputStream,
): File? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    openInput().use { input ->
        BitmapFactory.decodeStream(input, null, bounds)
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val options = BitmapFactory.Options().apply {
        inSampleSize = thumbnailSampleSize(
            width = bounds.outWidth,
            height = bounds.outHeight,
        )
    }
    val bitmap = openInput().use { input ->
        BitmapFactory.decodeStream(input, null, options)
    } ?: return null
    return writeThumbnailBitmap(cacheDir, cacheKey, bitmap)
}

/** Scales [bitmap] down to thumbnail size and stores it under [cacheKey]. Recycles [bitmap]. */
private fun writeThumbnailBitmap(
    cacheDir: File,
    cacheKey: String,
    bitmap: Bitmap,
): File? {
    val outputDimensions = thumbnailOutputDimensions(
        width = bitmap.width,
        height = bitmap.height,
    ) ?: run {
        bitmap.recycle()
        return null
    }
    val thumbnailBitmap = try {
        if (outputDimensions.width == bitmap.width && outputDimensions.height == bitmap.height) {
            bitmap
        } else {
            bitmap.scale(
                width = outputDimensions.width,
                height = outputDimensions.height,
                filter = true,
            )
        }
    } catch (throwable: Throwable) {
        bitmap.recycle()
        throw throwable
    }
    val hasAlpha = thumbnailBitmap.hasAlpha()
    val outputFile = File(cacheDir, "${cacheKey}_thumbnail.${if (hasAlpha) "png" else "jpg"}")
    var temporaryFile: File? = null

    return try {
        val pendingFile = File.createTempFile("thumbnail_", ".tmp", cacheDir)
        temporaryFile = pendingFile
        val compressed = pendingFile.outputStream().buffered().use { output ->
            thumbnailBitmap.compress(
                if (hasAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
                JPEG_THUMBNAIL_QUALITY,
                output,
            )
        }
        if (!compressed || pendingFile.length() > MAX_THUMBNAIL_OUTPUT_BYTES) {
            return null
        }

        synchronized(thumbnailCacheLock) {
            cachedThumbnailFiles(cacheDir, cacheKey).forEach(File::delete)
            outputFile.delete()
            if (!pendingFile.renameTo(outputFile)) {
                pendingFile.copyTo(outputFile, overwrite = true)
                pendingFile.delete()
            }
            outputFile.setLastModified(System.currentTimeMillis())
            trimThumbnailCache(cacheDir, protectedFile = outputFile)
        }
        outputFile.takeIf(File::isFile)
    } finally {
        if (thumbnailBitmap !== bitmap) thumbnailBitmap.recycle()
        bitmap.recycle()
        temporaryFile?.delete()
    }
}

private fun copyDocumentArchiveEntry(
    header: ByteArray,
    input: InputStream,
    outputFile: File,
    shouldContinue: () -> Boolean = { true },
): Boolean {
    val buffer = ByteArray(64 * 1024)
    var copied = header.size.toLong()
    var withinLimit = true
    val copiedSuccessfully = try {
        outputFile.outputStream().buffered().use { output ->
            output.write(header)
            while (true) {
                if (!shouldContinue()) throw CancellationException()
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) {
                    val value = input.read()
                    if (value < 0) break
                    if (copied >= MAX_ARCHIVE_THUMBNAIL_SOURCE_BYTES) {
                        withinLimit = false
                        break
                    }
                    output.write(value)
                    copied += 1
                    continue
                }
                if (copied > MAX_ARCHIVE_THUMBNAIL_SOURCE_BYTES - read) {
                    withinLimit = false
                    break
                }
                output.write(buffer, 0, read)
                copied += read
            }
        }
        withinLimit
    } catch (cancellation: CancellationException) {
        outputFile.delete()
        throw cancellation
    } catch (_: Exception) {
        false
    }
    if (!copiedSuccessfully) outputFile.delete()
    return copiedSuccessfully
}

private fun Context.queryDocumentLong(uri: Uri, column: String): Long? = runCatching {
    contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(column)
        if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) cursor.getLong(index) else null
    }
}.getOrNull()?.takeIf { it >= 0L }

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

internal data class ThumbnailDimensions(
    val width: Int,
    val height: Int,
)

internal fun thumbnailSampleSize(
    width: Int,
    height: Int,
    maxDimension: Int = MAX_THUMBNAIL_DIMENSION_PX,
): Int {
    if (width <= 0 || height <= 0 || maxDimension <= 0) return 1
    val largestDimension = maxOf(width, height)
    var sampleSize = 1
    while (sampleSize <= Int.MAX_VALUE / 2) {
        val nextSampleSize = sampleSize * 2
        if (largestDimension / nextSampleSize < maxDimension) break
        sampleSize = nextSampleSize
    }
    return sampleSize
}

internal fun thumbnailOutputDimensions(
    width: Int,
    height: Int,
    maxDimension: Int = MAX_THUMBNAIL_DIMENSION_PX,
): ThumbnailDimensions? {
    if (width <= 0 || height <= 0 || maxDimension <= 0) return null
    if (maxOf(width, height) <= maxDimension) return ThumbnailDimensions(width, height)

    return if (width >= height) {
        ThumbnailDimensions(
            width = maxDimension,
            height = (height.toDouble() * maxDimension / width).roundToInt().coerceAtLeast(1),
        )
    } else {
        ThumbnailDimensions(
            width = (width.toDouble() * maxDimension / height).roundToInt().coerceAtLeast(1),
            height = maxDimension,
        )
    }
}

internal fun thumbnailCacheIdentity(
    sourcePath: String,
    sourceLength: Long,
    sourceLastModified: Long,
    cacheVersion: Int,
): String {
    val identity = "$cacheVersion\u0000$sourcePath\u0000$sourceLength\u0000$sourceLastModified"
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(identity.toByteArray(StandardCharsets.UTF_8))
    return buildString(digest.size * 2) {
        digest.forEach { byte ->
            val unsignedByte = byte.toInt() and 0xFF
            append(HEX_DIGITS[unsignedByte ushr 4])
            append(HEX_DIGITS[unsignedByte and 0x0F])
        }
    }
}

private fun findCachedThumbnailFile(cacheDir: File, cacheKey: String): File? =
    synchronized(thumbnailCacheLock) {
        cachedThumbnailFiles(cacheDir, cacheKey)
            .firstOrNull { file -> file.isFile && file.length() in 1L..MAX_THUMBNAIL_OUTPUT_BYTES }
            ?.also { file -> file.setLastModified(System.currentTimeMillis()) }
    }

private fun cachedThumbnailFiles(cacheDir: File, cacheKey: String): List<File> =
    listOf(
        File(cacheDir, "${cacheKey}_thumbnail.jpg"),
        File(cacheDir, "${cacheKey}_thumbnail.png"),
    )

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
