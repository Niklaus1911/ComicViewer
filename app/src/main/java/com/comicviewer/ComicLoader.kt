package com.comicviewer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.IOException
import java.util.Locale
import java.util.zip.ZipException
import java.util.zip.ZipFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ARCHIVE_CACHE_DIR_NAME = "current_comic_pages"
private const val MAX_REJECTION_EXAMPLES = 4
private const val IMAGE_HEADER_BYTE_COUNT = 12
private const val STREAM_BUFFER_SIZE = 64 * 1024
private const val MAX_ARCHIVE_ENTRY_COUNT = 10_000
private const val ARCHIVE_CACHE_RESERVE_BYTES = 128L * 1024L * 1024L
private const val MAX_ARCHIVE_SOURCE_BYTES = 1024L * 1024L * 1024L
private const val MAX_ARCHIVE_PAGE_BYTES = 64L * 1024L * 1024L
private const val MAX_ARCHIVE_CACHE_BYTES = 2L * 1024L * 1024L * 1024L

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
    ArchiveFileTooLarge,
    ArchivePageTooLarge,
    ArchiveContentsTooLarge,
    InvalidArchive,
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
        ArchiveFileTooLarge -> "Archive file exceeds the supported size limit."
        ArchivePageTooLarge -> "An archive page exceeds the supported size limit."
        ArchiveContentsTooLarge -> "Archive contents exceed the supported extraction limit."
        InvalidArchive -> "Select a valid .zip or .cbz archive."
        ArchiveExtractionFailed -> "Archive not readable or extraction interrupted."
        NotEnoughArchiveSpace -> "Not enough space to extract this archive."
        NoSupportedImagesFound -> "No supported images found."
        NoValidImagesFound -> "No valid images found. Scanned ${args[0]} files."
        NoValidImagesFoundWithExamples -> "No valid images found. Scanned ${args[0]} files. Rejected examples: ${args[1]}."
    }

    fun localizedMessage(context: Context, vararg args: Any): String = when (this) {
        FolderNotReadable -> context.getString(R.string.folder_not_readable)
        FolderReadPermissionUnavailable -> context.getString(R.string.folder_read_permission_unavailable)
        ArchiveNotReadable -> context.getString(R.string.archive_not_readable)
        ArchiveTooLarge -> context.getString(R.string.archive_too_large, args[0])
        ArchiveFileTooLarge -> context.getString(R.string.archive_file_too_large)
        ArchivePageTooLarge -> context.getString(R.string.archive_page_too_large)
        ArchiveContentsTooLarge -> context.getString(R.string.archive_contents_too_large)
        InvalidArchive -> context.getString(R.string.invalid_archive)
        ArchiveExtractionFailed -> context.getString(R.string.archive_extraction_failed)
        NotEnoughArchiveSpace -> context.getString(R.string.not_enough_archive_space)
        NoSupportedImagesFound -> context.getString(R.string.no_supported_images_found)
        NoValidImagesFound -> context.getString(R.string.no_valid_images_found, args[0])
        NoValidImagesFoundWithExamples -> context.getString(R.string.no_valid_images_found_with_examples, args[0], args[1])
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
    val displayName = context.displayNameFor(uri) ?: uri.lastPathSegment.orEmpty().substringAfterLast('/')

    val cacheDir = resetArchiveCache(context)
    val archiveFile = copyArchiveToCache(context, uri, displayName, cacheDir)
    val pages = try {
        archiveFile.extractComicPagesTo(
            cacheDir = cacheDir,
            maxExtractionBytes = MAX_ARCHIVE_CACHE_BYTES - archiveFile.length(),
        )
    } finally {
        archiveFile.delete()
    }

    ComicBook(
        title = displayName.withoutComicExtension(),
        source = ComicSource.Archive(uri = uri, displayName = displayName),
        pages = pages,
    )
}

suspend fun loadFolderComic(context: Context, uri: Uri): ComicBook = withContext(Dispatchers.IO) {
    val folder = DocumentFile.fromTreeUri(context, uri)
        ?: throw ComicLoadException(ComicLoadError.FolderNotReadable)

    if (!folder.canRead()) {
        throw ComicLoadException(ComicLoadError.FolderReadPermissionUnavailable)
    }

    val imageFiles = folder.listFiles()
        .filter { document ->
            val name = document.name
            document.isFile && name != null && !isHiddenComicPath(name) && isSupportedImageName(name)
        }
        .sortedWith { left, right ->
            compareComicPageNames(left.name.orEmpty(), right.name.orEmpty())
        }

    comicLoadErrorForPageCount(imageFiles.size)?.let { throw ComicLoadException(it) }

    val pages = imageFiles.mapIndexed { index, document ->
        val displayName = document.name ?: "Page ${index + 1}"
        ComicPage(
            index = index,
            displayName = displayName,
            relativePath = displayName,
            imageUri = document.uri,
            width = 0,
            height = 0,
        )
    }

    val displayName = folder.name ?: "Comic folder"
    ComicBook(
        title = displayName,
        source = ComicSource.Folder(uri = uri, displayName = displayName),
        pages = pages,
    )
}

suspend fun loadLocalArchiveComic(context: Context, archiveFile: File): ComicBook = withContext(Dispatchers.IO) {
    if (!archiveFile.isFile || !archiveFile.canRead()) {
        throw ComicLoadException(ComicLoadError.ArchiveNotReadable)
    }

    val displayName = archiveFile.name
    val cacheDir = resetArchiveCache(context)
    val pages = archiveFile.extractComicPagesTo(
        cacheDir = cacheDir,
        maxExtractionBytes = MAX_ARCHIVE_CACHE_BYTES,
    )

    ComicBook(
        title = displayName.withoutComicExtension(),
        source = ComicSource.LocalArchive(filePath = archiveFile.absolutePath, displayName = displayName),
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

fun clearArchiveCache(context: Context) {
    File(context.cacheDir, ARCHIVE_CACHE_DIR_NAME).deleteRecursively()
}

private fun File.extractComicPagesTo(
    cacheDir: File,
    maxExtractionBytes: Long,
): List<ComicPage> {
    if (maxExtractionBytes <= 0L) {
        cacheDir.deleteRecursively()
        throw ComicLoadException(ComicLoadError.ArchiveContentsTooLarge)
    }
    val extraction = try {
        extractArchivePages(this, cacheDir, maxExtractionBytes)
    } catch (exception: Exception) {
        cacheDir.deleteRecursively()
        throw archiveExtractionFailureFor(exception)
    }
    val extractedPages = extraction.pages
        .sortedWith { left, right -> compareComicPageNames(left.entryName, right.entryName) }

    if (extractedPages.isEmpty()) {
        cacheDir.deleteRecursively()
        throw extraction.emptyException()
    }

    return extractedPages.mapIndexed { index, extracted ->
        ComicPage(
            index = index,
            displayName = extracted.entryName.substringAfterLast('/'),
            relativePath = extracted.entryName,
            imageUri = extracted.file.toUri(),
            width = extracted.info.width,
            height = extracted.info.height,
        )
    }
}

private fun extractArchivePages(
    archiveFile: File,
    cacheDir: File,
    maxExtractionBytes: Long,
): ArchiveExtraction {
    val extractedPages = mutableListOf<ExtractedPage>()
    val rejectedEntries = mutableListOf<String>()
    var scannedEntries = 0
    var visitedEntries = 0
    var processedImageBytes = 0L

    ZipFile(archiveFile).use { zipFile ->
        val entries = zipFile.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            val entryName = entry.name.replace('\\', '/')
            visitedEntries += 1
            if (visitedEntries > MAX_ARCHIVE_ENTRY_COUNT) {
                throw ComicLoadException(ComicLoadError.ArchiveTooLarge, MAX_ARCHIVE_ENTRY_COUNT)
            }

            if (!entry.isDirectory && !isHiddenComicPath(entryName)) {
                scannedEntries += 1
                val imageFormat = zipFile.getInputStream(entry).use { input ->
                    detectImageFormat(readImageHeader(input))
                }

                if (entry.size == 0L || imageFormat == null) {
                    if (rejectedEntries.size < MAX_REJECTION_EXAMPLES) {
                        rejectedEntries += entryName.substringAfterLast('/')
                    }
                    continue
                }

                val copyLimit = archiveEntryCopyLimit(
                    remainingTotalBytes = maxExtractionBytes - processedImageBytes,
                    cacheWritableBytes = cacheWritableBytes(cacheDir),
                )
                if (entry.size > copyLimit.maxBytes) {
                    throw ComicLoadException(copyLimit.limitError)
                }
                val outputFile = File(
                    cacheDir,
                    safeCachePageName(extractedPages.size, imageFormat),
                )
                val copiedBytes = zipFile.getInputStream(entry).use { input ->
                    copyToFileBounded(
                        inputStream = input,
                        outputFile = outputFile,
                        maxBytes = copyLimit.maxBytes,
                        limitError = copyLimit.limitError,
                    )
                }
                processedImageBytes += copiedBytes
                val imageInfo = detectImageInfo(outputFile)
                if (imageInfo == null) {
                    outputFile.delete()
                    if (rejectedEntries.size < MAX_REJECTION_EXAMPLES) {
                        rejectedEntries += entryName.substringAfterLast('/')
                    }
                    continue
                }
                extractedPages += ExtractedPage(entryName = entryName, file = outputFile, info = imageInfo)
            }
        }
    }

    return ArchiveExtraction(
        scannedEntries = scannedEntries,
        pages = extractedPages,
        rejectedExamples = rejectedEntries,
    )
}

internal fun archiveExtractionFailureFor(exception: Exception): Exception = when (exception) {
    is CancellationException -> exception
    is ComicLoadException -> exception
    is ZipException -> ComicLoadException(ComicLoadError.InvalidArchive)
    is IOException,
    is SecurityException,
    -> ComicLoadException(ComicLoadError.ArchiveExtractionFailed)
    else -> ComicLoadException(ComicLoadError.InvalidArchive)
}

private fun copyArchiveToCache(
    context: Context,
    uri: Uri,
    displayName: String,
    cacheDir: File,
): File {
    val inputStream = context.contentResolver.openInputStream(uri)
        ?: throw ComicLoadException(ComicLoadError.ArchiveNotReadable)
    val archiveFile = File(cacheDir, "selected_comic.${displayName.archiveExtension()}")
    val copyLimit = resolvedCopyLimit(
        logicalLimitBytes = MAX_ARCHIVE_SOURCE_BYTES,
        cacheWritableBytes = cacheWritableBytes(cacheDir),
        logicalLimitError = ComicLoadError.ArchiveFileTooLarge,
    )

    inputStream.use { input ->
        copyToFileBounded(
            inputStream = input,
            outputFile = archiveFile,
            maxBytes = copyLimit.maxBytes,
            limitError = copyLimit.limitError,
        )
    }

    return archiveFile
}

private fun resetArchiveCache(context: Context): File =
    File(context.cacheDir, ARCHIVE_CACHE_DIR_NAME).apply {
        deleteRecursively()
        mkdirs()
    }

private fun Context.displayNameFor(uri: Uri): String? =
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameColumn >= 0 && cursor.moveToFirst()) cursor.getString(nameColumn) else null
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
        offset += bytesRead
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

fun copyToFileBounded(
    inputStream: InputStream,
    outputFile: File,
    maxBytes: Long,
    limitError: ComicLoadError = ComicLoadError.NotEnoughArchiveSpace,
): Long {
    if (maxBytes < 0L) {
        throw ComicLoadException(limitError)
    }

    val buffer = ByteArray(STREAM_BUFFER_SIZE)
    var copiedBytes = 0L

    try {
        outputFile.outputStream().use { output ->
            while (true) {
                val bytesRead = inputStream.read(buffer)
                if (bytesRead < 0) break

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

@SuppressLint("UsableSpace")
private fun cacheWritableBytes(cacheDir: File): Long =
    cacheDir.usableSpace - ARCHIVE_CACHE_RESERVE_BYTES

internal data class ArchiveCopyLimit(
    val maxBytes: Long,
    val limitError: ComicLoadError,
)

internal fun resolvedCopyLimit(
    logicalLimitBytes: Long,
    cacheWritableBytes: Long,
    logicalLimitError: ComicLoadError,
): ArchiveCopyLimit =
    if (cacheWritableBytes < logicalLimitBytes) {
        ArchiveCopyLimit(
            maxBytes = cacheWritableBytes,
            limitError = ComicLoadError.NotEnoughArchiveSpace,
        )
    } else {
        ArchiveCopyLimit(
            maxBytes = logicalLimitBytes,
            limitError = logicalLimitError,
        )
    }

internal fun archiveEntryCopyLimit(
    remainingTotalBytes: Long,
    cacheWritableBytes: Long,
    maxEntryBytes: Long = MAX_ARCHIVE_PAGE_BYTES,
): ArchiveCopyLimit {
    val logicalLimitBytes = minOf(remainingTotalBytes.coerceAtLeast(0L), maxEntryBytes)
    val logicalLimitError = if (remainingTotalBytes <= maxEntryBytes) {
        ComicLoadError.ArchiveContentsTooLarge
    } else {
        ComicLoadError.ArchivePageTooLarge
    }
    return resolvedCopyLimit(
        logicalLimitBytes = logicalLimitBytes,
        cacheWritableBytes = cacheWritableBytes,
        logicalLimitError = logicalLimitError,
    )
}

private fun safeCachePageName(index: Int, format: ImageFormat): String =
    "page_${index.toString().padStart(5, '0')}.${format.extension}"

private fun String.archiveExtension(): String =
    substringAfterLast('.', missingDelimiterValue = "zip")
        .lowercase(Locale.US)
        .takeIf { it == "zip" || it == "cbz" }
        ?: "zip"

private fun String.withoutComicExtension(): String =
    substringBeforeLast('.', missingDelimiterValue = this)

private data class ExtractedPage(
    val entryName: String,
    val file: File,
    val info: ImageInfo,
)

private data class LocalImageFile(
    val file: File,
    val info: ImageInfo,
)

private data class ArchiveExtraction(
    val scannedEntries: Int,
    val pages: List<ExtractedPage>,
    val rejectedExamples: List<String>,
) {
    fun emptyException(): ComicLoadException = if (rejectedExamples.isEmpty()) {
        ComicLoadException(ComicLoadError.NoValidImagesFound, scannedEntries)
    } else {
        ComicLoadException(
            ComicLoadError.NoValidImagesFoundWithExamples,
            scannedEntries,
            rejectedExamples.joinToString(),
        )
    }
}
