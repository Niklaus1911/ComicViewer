package com.comicviewer

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.net.toUri
import java.io.BufferedInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FilterInputStream
import java.io.InputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private const val LEGACY_ARCHIVE_PAGE_CACHE_DIR_NAME = "current_comic_pages"
private const val IMAGE_HEADER_BYTE_COUNT = 12
private const val STREAM_BUFFER_SIZE = 64 * 1024
private val legacyArchivePageCacheCleared = AtomicBoolean(false)

class ComicLoadException(
    val error: ComicLoadError,
    private vararg val formatArgs: Any,
) : IllegalArgumentException(error.defaultMessage(*formatArgs)) {
    fun localizedMessage(context: Context): String = error.localizedMessage(context, *formatArgs)
}

enum class ComicLoadError {
    FolderNotReadable,
    FolderReadPermissionUnavailable,
    ArchiveNotReadable,
    ArchiveTooLarge,
    ArchivePageTooLarge,
    InvalidArchive,
    PdfPasswordProtected,
    PdfTooManyPages,
    ArchiveExtractionFailed,
    NotEnoughArchiveSpace,
    NoSupportedImagesFound,
    NoValidImagesFound,
    NoValidImagesFoundWithExamples;

    fun defaultMessage(vararg args: Any): String = when (this) {
        FolderNotReadable -> "Folder not readable."
        FolderReadPermissionUnavailable -> "Folder read permission is not available."
        ArchiveNotReadable -> "Archive not readable."
        ArchiveTooLarge -> "Archive too large: it contains more than ${args[0]} files."
        ArchivePageTooLarge -> "An archive page exceeds the supported size limit."
        InvalidArchive -> "Select a valid .zip, .cbz or .pdf file."
        PdfPasswordProtected -> "This PDF is password protected."
        PdfTooManyPages -> "PDF too large: it has more than ${args[0]} pages."
        ArchiveExtractionFailed -> "Archive read failed or was interrupted."
        NotEnoughArchiveSpace -> "Not enough free space to open this archive."
        NoSupportedImagesFound -> "No supported images found."
        NoValidImagesFound -> "No valid images found. Scanned ${args[0]} files."
        NoValidImagesFoundWithExamples -> "No valid images found. Scanned ${args[0]} files. Rejected examples: ${args[1]}."
    }

    fun localizedMessage(context: Context, vararg args: Any): String = when (this) {
        FolderNotReadable -> context.getString(R.string.folder_not_readable)
        FolderReadPermissionUnavailable -> context.getString(R.string.folder_read_permission_unavailable)
        ArchiveNotReadable -> context.getString(R.string.archive_not_readable)
        ArchiveTooLarge -> (args[0] as Number).toInt().let { count ->
            context.resources.getQuantityString(R.plurals.archive_too_large, count, count)
        }
        ArchivePageTooLarge -> context.getString(R.string.archive_page_too_large)
        InvalidArchive -> context.getString(R.string.invalid_archive)
        PdfPasswordProtected -> context.getString(R.string.pdf_password_protected)
        PdfTooManyPages -> (args[0] as Number).toInt().let { count ->
            context.resources.getQuantityString(R.plurals.pdf_too_many_pages, count, count)
        }
        ArchiveExtractionFailed -> context.getString(R.string.archive_extraction_failed)
        NotEnoughArchiveSpace -> context.getString(R.string.not_enough_archive_space)
        NoSupportedImagesFound -> context.getString(R.string.no_supported_images_found)
        NoValidImagesFound -> (args[0] as Number).toInt().let { count ->
            context.resources.getQuantityString(R.plurals.no_valid_images_found, count, count)
        }
        NoValidImagesFoundWithExamples -> (args[0] as Number).toInt().let { count ->
            context.resources.getQuantityString(
                R.plurals.no_valid_images_found_with_examples,
                count,
                count,
                args[1].toString(),
            )
        }
    }
}

enum class ImageFormat(val extension: String) {
    JPEG("jpg"),
    PNG("png"),
    WEBP("webp"),
}

data class ImageInfo(
    val format: ImageFormat,
    val width: Int,
    val height: Int,
)

suspend fun loadArchiveComic(context: Context, uri: Uri): ComicBook = withContext(Dispatchers.IO) {
    val displayName = context.queryDocumentDisplayName(uri) ?: uri.lastPathSegment.orEmpty().substringAfterLast('/')
    val location = ComicArchiveLocation.Document(uri)
    val title = displayName.withoutComicExtension()
    val source = ComicSource.Archive(uri = uri, displayName = displayName)
    val fingerprint = context.documentArchiveFingerprint(uri)
    if (context.documentLooksLikePdf(uri)) {
        loadPdfComic(context, location, title, source, fingerprint)
    } else {
        loadZipComic(context, location, title, source, fingerprint)
    }
}

suspend fun loadLocalArchiveComic(context: Context, archiveFile: File): ComicBook = withContext(Dispatchers.IO) {
    if (!archiveFile.isFile || !archiveFile.canRead()) {
        throw ComicLoadException(ComicLoadError.ArchiveNotReadable)
    }
    val displayName = archiveFile.name
    val location = ComicArchiveLocation.LocalFile(archiveFile.absolutePath)
    val title = displayName.withoutComicExtension()
    val source = ComicSource.LocalArchive(filePath = archiveFile.absolutePath, displayName = displayName)
    val fingerprint = "${archiveFile.length()}:${archiveFile.lastModified()}"
    if (fileLooksLikePdf(archiveFile)) {
        loadPdfComic(context, location, title, source, fingerprint)
    } else {
        loadZipComic(context, location, title, source, fingerprint)
    }
}

/**
 * Builds the page list from the archive's central directory without copying or extracting anything;
 * each page is streamed from the archive by [ComicArchivePageFetcher] when it is displayed.
 */
private suspend fun loadZipComic(
    context: Context,
    location: ComicArchiveLocation,
    title: String,
    source: ComicSource,
    sourceFingerprint: String?,
): ComicBook {
    deleteLegacyArchivePageCache(context)
    val coroutineContext = currentCoroutineContext()
    val shouldContinue = { coroutineContext.isActive }
    val scan = try {
        ComicArchiveSessions.acquire(
            context = context,
            location = location,
            reopen = true,
            shouldContinue = shouldContinue,
        ).use { lease ->
            lease.source.reader.scanComicPages(shouldContinue = shouldContinue)
        }
    } catch (exception: Exception) {
        throw archiveReadFailureFor(exception)
    }
    if (scan.pages.isEmpty()) throw scan.emptyException()

    val pages = scan.pages.mapIndexed { index, entry ->
        ComicPage(
            index = index,
            displayName = entry.name.substringAfterLast('/'),
            relativePath = entry.name,
            imageUri = comicArchivePageUri(location, entry),
            width = 0,
            height = 0,
            cacheFingerprint = sourceFingerprint,
        )
    }
    return ComicBook(title = title, source = source, pages = pages)
}

/**
 * Lists a PDF's pages without rendering any of them; each page is rendered by [ComicPdfPageFetcher]
 * at the size it is displayed.
 */
private suspend fun loadPdfComic(
    context: Context,
    location: ComicArchiveLocation,
    title: String,
    source: ComicSource,
    sourceFingerprint: String?,
): ComicBook {
    val coroutineContext = currentCoroutineContext()
    val pageCount = try {
        ComicPdfSessions.acquire(
            context = context,
            location = location,
            reopen = true,
            shouldContinue = { coroutineContext.isActive },
        ).use { lease -> lease.source.pageCount }
    } catch (exception: Exception) {
        throw archiveReadFailureFor(exception)
    }
    comicLoadErrorForPageCount(pageCount)?.let { throw ComicLoadException(it) }

    val pages = List(pageCount) { index ->
        val pageNumber = index + 1
        ComicPage(
            index = index,
            displayName = context.getString(R.string.reader_page_number_description, pageNumber),
            relativePath = "page-$pageNumber",
            imageUri = comicPdfPageUri(location, index),
            width = 0,
            height = 0,
            cacheFingerprint = sourceFingerprint,
        )
    }
    return ComicBook(title = title, source = source, pages = pages)
}

suspend fun loadFolderComic(context: Context, uri: Uri): ComicBook = withContext(Dispatchers.IO) {
    if (!canReadDocumentDirectory(context, uri)) {
        throw ComicLoadException(ComicLoadError.FolderReadPermissionUnavailable)
    }

    val imageFiles = listDocumentChildren(context, uri)
        .filter { document ->
            document.isFile &&
                !isHiddenComicPath(document.displayName) &&
                isSupportedImageName(document.displayName)
        }
        .sortedWith { left, right ->
            compareComicPageNames(left.displayName, right.displayName)
        }

    comicLoadErrorForPageCount(imageFiles.size)?.let { throw ComicLoadException(it) }

    val pages = imageFiles.mapIndexed { index, document ->
        val displayName = document.displayName.ifBlank {
            context.getString(R.string.reader_page_number_description, index + 1)
        }
        ComicPage(
            index = index,
            displayName = displayName,
            relativePath = displayName,
            imageUri = document.uri,
            width = 0,
            height = 0,
            cacheFingerprint = document.cacheFingerprint(),
        )
    }

    val displayName = folderDocumentFor(context, uri).displayName
    ComicBook(
        title = displayName,
        source = ComicSource.Folder(uri = uri, displayName = displayName),
        pages = pages,
    )
}

suspend fun loadLocalFolderComic(folder: File): ComicBook = withContext(Dispatchers.IO) {
    if (!folder.isDirectory || !folder.canRead()) {
        throw ComicLoadException(ComicLoadError.FolderNotReadable)
    }

    val imageFiles = folder.listFiles().orEmpty()
        .mapNotNull { file ->
            val info = if (file.isFile && !isHiddenComicPath(file.name)) detectImageInfo(file) else null
            if (info != null) LocalImageFile(file = file, info = info) else null
        }
        .sortedWith { left, right ->
            compareComicPageNames(left.file.name, right.file.name)
        }

    comicLoadErrorForPageCount(imageFiles.size)?.let { throw ComicLoadException(it) }

    val pages = imageFiles.mapIndexed { index, imageFile ->
        ComicPage(
            index = index,
            displayName = imageFile.file.name,
            relativePath = imageFile.file.name,
            imageUri = imageFile.file.toUri(),
            width = imageFile.info.width,
            height = imageFile.info.height,
        )
    }

    val displayName = folder.name.ifBlank { folder.absolutePath }
    ComicBook(
        title = displayName,
        source = ComicSource.LocalFolder(folderPath = folder.absolutePath, displayName = displayName),
        pages = pages,
    )
}

suspend fun clearArchiveCache(context: Context) = withContext(Dispatchers.IO) {
    ComicArchiveSessions.closeAll()
    ComicPdfSessions.closeAll()
    File(context.cacheDir, LEGACY_ARCHIVE_PAGE_CACHE_DIR_NAME).deleteRecursively()
    deleteArchiveCopies(context)
}

suspend fun closeComicArchives() = withContext(Dispatchers.IO) {
    ComicArchiveSessions.closeAll()
    ComicPdfSessions.closeAll()
}

/** Older versions extracted every page into this folder; it can hold gigabytes, so drop it once. */
private fun deleteLegacyArchivePageCache(context: Context) {
    if (legacyArchivePageCacheCleared.compareAndSet(false, true)) {
        File(context.cacheDir, LEGACY_ARCHIVE_PAGE_CACHE_DIR_NAME).deleteRecursively()
    }
}

internal fun archiveReadFailureFor(exception: Exception): Exception = when (exception) {
    is CancellationException -> exception
    is ComicLoadException -> exception
    is ZipEntryLimitExceededException -> ComicLoadException(ComicLoadError.ArchiveTooLarge, exception.limit)
    is ZipException -> ComicLoadException(ComicLoadError.InvalidArchive)
    is FileNotFoundException,
    is SecurityException,
    -> ComicLoadException(ComicLoadError.ArchiveNotReadable)
    is IOException -> ComicLoadException(ComicLoadError.ArchiveExtractionFailed)
    else -> ComicLoadException(ComicLoadError.InvalidArchive)
}

fun isHiddenComicPath(path: String): Boolean =
    path.split('/').any { segment ->
        segment.isEmpty() || segment.startsWith(".") || segment == "__MACOSX"
    }

fun detectImageFormat(bytes: ByteArray): ImageFormat? = when {
    bytes.size >= 3 &&
        bytes[0] == 0xFF.toByte() &&
        bytes[1] == 0xD8.toByte() &&
        bytes[2] == 0xFF.toByte() -> ImageFormat.JPEG

    bytes.size >= 8 &&
        bytes[0] == 0x89.toByte() &&
        bytes[1] == 0x50.toByte() &&
        bytes[2] == 0x4E.toByte() &&
        bytes[3] == 0x47.toByte() &&
        bytes[4] == 0x0D.toByte() &&
        bytes[5] == 0x0A.toByte() &&
        bytes[6] == 0x1A.toByte() &&
        bytes[7] == 0x0A.toByte() -> ImageFormat.PNG

    bytes.size >= 12 &&
        bytes[0] == 0x52.toByte() &&
        bytes[1] == 0x49.toByte() &&
        bytes[2] == 0x46.toByte() &&
        bytes[3] == 0x46.toByte() &&
        bytes[8] == 0x57.toByte() &&
        bytes[9] == 0x45.toByte() &&
        bytes[10] == 0x42.toByte() &&
        bytes[11] == 0x50.toByte() -> ImageFormat.WEBP

    else -> null
}

fun readImageHeader(inputStream: InputStream): ByteArray {
    val header = ByteArray(IMAGE_HEADER_BYTE_COUNT)
    var offset = 0

    while (offset < header.size) {
        val bytesRead = inputStream.read(header, offset, header.size - offset)
        if (bytesRead < 0) break
        if (bytesRead == 0) {
            val value = inputStream.read()
            if (value < 0) break
            header[offset] = value.toByte()
            offset += 1
        } else {
            offset += bytesRead
        }
    }

    if (offset < header.size) {
        header.fill(0, fromIndex = offset)
    }

    return header
}

fun detectImageInfo(inputStream: InputStream): ImageInfo? {
    val bufferedInput = BufferedInputStream(inputStream, STREAM_BUFFER_SIZE)
    bufferedInput.mark(IMAGE_HEADER_BYTE_COUNT)
    val format = detectImageFormat(readImageHeader(bufferedInput)) ?: return null
    bufferedInput.reset()

    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeStream(bufferedInput, null, options)

    return if (options.outWidth > 0 && options.outHeight > 0) {
        ImageInfo(format = format, width = options.outWidth, height = options.outHeight)
    } else {
        null
    }
}

fun detectImageInfo(file: File): ImageInfo? {
    if (!file.isFile || !file.canRead()) return null

    val header = file.inputStream().use { input ->
        readImageHeader(input)
    }
    val format = detectImageFormat(header) ?: return null
    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeFile(file.absolutePath, options)

    return if (options.outWidth > 0 && options.outHeight > 0) {
        ImageInfo(format = format, width = options.outWidth, height = options.outHeight)
    } else {
        null
    }
}

internal fun detectDocumentImageInfo(context: Context, uri: Uri): ImageInfo? = try {
    context.contentResolver.openInputStream(uri)?.use(::detectImageInfo)
} catch (_: Exception) {
    null
}

fun copyToFileBounded(
    inputStream: InputStream,
    outputFile: File,
    maxBytes: Long,
    limitError: ComicLoadError = ComicLoadError.NotEnoughArchiveSpace,
    shouldContinue: () -> Boolean = { true },
): Long {
    if (maxBytes < 0L) {
        throw ComicLoadException(limitError)
    }

    val buffer = ByteArray(STREAM_BUFFER_SIZE)
    var copiedBytes = 0L

    try {
        outputFile.outputStream().use { output ->
            while (true) {
                if (!shouldContinue()) throw CancellationException()
                val bytesRead = inputStream.read(buffer)
                if (bytesRead < 0) break
                if (bytesRead == 0) {
                    val value = inputStream.read()
                    if (value < 0) break
                    if (copiedBytes >= maxBytes) throw ComicLoadException(limitError)
                    output.write(value)
                    copiedBytes += 1
                    continue
                }

                if (copiedBytes > maxBytes - bytesRead) {
                    throw ComicLoadException(limitError)
                }

                output.write(buffer, 0, bytesRead)
                copiedBytes += bytesRead
            }
        }
    } catch (throwable: Throwable) {
        outputFile.delete()
        throw throwable
    }

    return copiedBytes
}

/**
 * Reads at most [maxBytes] from [input]. [limitReached] tells a source that was cut off from one
 * that ended on its own, including one exactly [maxBytes] long.
 */
internal class BoundedInputStream(
    input: InputStream,
    maxBytes: Long,
) : FilterInputStream(input) {
    private var remaining = maxBytes.coerceAtLeast(0L)
    private var probedPastLimit = false

    var limitReached: Boolean = false
        private set

    override fun read(): Int {
        if (remaining == 0L) return endOfAllowedBytes()
        return super.read().also { if (it >= 0) remaining -= 1 }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return endOfAllowedBytes()
        val read = super.read(buffer, offset, minOf(length.toLong(), remaining).toInt())
        if (read > 0) remaining -= read
        return read
    }

    override fun skip(byteCount: Long): Long {
        if (byteCount <= 0L) return 0L
        if (remaining == 0L) {
            endOfAllowedBytes()
            return 0L
        }
        val skipped = super.skip(minOf(byteCount, remaining))
        if (skipped > 0L) remaining -= skipped
        return skipped
    }

    override fun available(): Int = minOf(super.available().toLong(), remaining).toInt()

    override fun markSupported(): Boolean = false

    /** Checks once whether the source had more bytes than allowed; the reader sees the end either way. */
    private fun endOfAllowedBytes(): Int {
        if (!probedPastLimit) {
            probedPastLimit = true
            limitReached = super.read() >= 0
        }
        return -1
    }
}

private fun String.withoutComicExtension(): String =
    substringBeforeLast('.', missingDelimiterValue = this).ifBlank { this }

private data class LocalImageFile(
    val file: File,
    val info: ImageInfo,
)

private fun ComicDocumentEntry.cacheFingerprint(): String? =
    if (sizeBytes != null || lastModifiedAt != null) {
        "${sizeBytes ?: -1L}:${lastModifiedAt ?: -1L}"
    } else {
        null
    }
