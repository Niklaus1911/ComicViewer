package com.comicviewer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.net.toUri
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipException
import java.util.zip.ZipFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val ARCHIVE_CACHE_DIR_NAME = "current_comic_pages"
private const val ARCHIVE_CACHE_MANIFEST_NAME = "pages.json"
private const val ARCHIVE_CACHE_MANIFEST_VERSION = 1
private const val MAX_ARCHIVE_CACHE_MANIFEST_BYTES = 8L * 1024L * 1024L
private const val MAX_REJECTION_EXAMPLES = 4
private const val IMAGE_HEADER_BYTE_COUNT = 12
private const val STREAM_BUFFER_SIZE = 64 * 1024
private const val MAX_ARCHIVE_ENTRY_COUNT = 10_000
private const val ARCHIVE_CACHE_RESERVE_BYTES = 128L * 1024L * 1024L
private const val MAX_ARCHIVE_SOURCE_BYTES = 1024L * 1024L * 1024L
private const val MAX_ARCHIVE_PAGE_BYTES = 64L * 1024L * 1024L
private const val MAX_ARCHIVE_CACHE_BYTES = 2L * 1024L * 1024L * 1024L
private val archiveCacheMutex = Mutex()

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
        ArchiveTooLarge -> (args[0] as Number).toInt().let { count ->
            context.resources.getQuantityString(R.plurals.archive_too_large, count, count)
        }
        ArchiveFileTooLarge -> context.getString(R.string.archive_file_too_large)
        ArchivePageTooLarge -> context.getString(R.string.archive_page_too_large)
        ArchiveContentsTooLarge -> context.getString(R.string.archive_contents_too_large)
        InvalidArchive -> context.getString(R.string.invalid_archive)
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

suspend fun loadArchiveComic(
    context: Context,
    uri: Uri,
    reuseExtractedPages: Boolean = false,
): ComicBook = withContext(Dispatchers.IO) {
    archiveCacheMutex.withLock {
        val coroutineContext = currentCoroutineContext()
        val shouldContinue = { coroutineContext.isActive }
        val displayName = context.displayNameFor(uri) ?: uri.lastPathSegment.orEmpty().substringAfterLast('/')
        val sourceKey = if (reuseExtractedPages) context.documentArchiveCacheKey(uri) else null
        val existingCacheDir = File(context.cacheDir, ARCHIVE_CACHE_DIR_NAME)
        readCachedArchivePages(existingCacheDir, sourceKey)?.let { pages ->
            return@withLock ComicBook(
                title = displayName.withoutComicExtension(),
                source = ComicSource.Archive(uri = uri, displayName = displayName),
                pages = pages,
            )
        }
        val cacheDir = resetArchiveCache(context)

        val archiveFile = copyArchiveToCache(
            context = context,
            uri = uri,
            displayName = displayName,
            cacheDir = cacheDir,
            shouldContinue = shouldContinue,
        )
        val pages = try {
            archiveFile.extractComicPagesTo(
                cacheDir = cacheDir,
                maxExtractionBytes = MAX_ARCHIVE_CACHE_BYTES - archiveFile.length(),
                shouldContinue = shouldContinue,
            )
        } finally {
            archiveFile.delete()
        }
        if (sourceKey != null) writeArchiveCacheManifest(cacheDir, sourceKey, pages)

        ComicBook(
            title = displayName.withoutComicExtension(),
            source = ComicSource.Archive(uri = uri, displayName = displayName),
            pages = pages,
        )
    }
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
        val displayName = document.displayName.ifBlank { "Page ${index + 1}" }
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

suspend fun loadLocalArchiveComic(
    context: Context,
    archiveFile: File,
    reuseExtractedPages: Boolean = false,
): ComicBook = withContext(Dispatchers.IO) {
    archiveCacheMutex.withLock {
        if (!archiveFile.isFile || !archiveFile.canRead()) {
            throw ComicLoadException(ComicLoadError.ArchiveNotReadable)
        }
        val coroutineContext = currentCoroutineContext()
        val shouldContinue = { coroutineContext.isActive }
        val displayName = archiveFile.name
        val sourceKey = if (reuseExtractedPages) archiveFile.localArchiveCacheKey() else null
        val existingCacheDir = File(context.cacheDir, ARCHIVE_CACHE_DIR_NAME)
        readCachedArchivePages(existingCacheDir, sourceKey)?.let { pages ->
            return@withLock ComicBook(
                title = displayName.withoutComicExtension(),
                source = ComicSource.LocalArchive(filePath = archiveFile.absolutePath, displayName = displayName),
                pages = pages,
            )
        }
        val cacheDir = resetArchiveCache(context)
        val pages = archiveFile.extractComicPagesTo(
            cacheDir = cacheDir,
            maxExtractionBytes = MAX_ARCHIVE_CACHE_BYTES,
            shouldContinue = shouldContinue,
        )
        if (sourceKey != null) writeArchiveCacheManifest(cacheDir, sourceKey, pages)

        ComicBook(
            title = displayName.withoutComicExtension(),
            source = ComicSource.LocalArchive(filePath = archiveFile.absolutePath, displayName = displayName),
            pages = pages,
        )
    }
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
    archiveCacheMutex.withLock {
        File(context.cacheDir, ARCHIVE_CACHE_DIR_NAME).deleteRecursively()
    }
}

private fun File.extractComicPagesTo(
    cacheDir: File,
    maxExtractionBytes: Long,
    shouldContinue: () -> Boolean = { true },
): List<ComicPage> {
    if (maxExtractionBytes <= 0L) {
        cacheDir.deleteRecursively()
        throw ComicLoadException(ComicLoadError.ArchiveContentsTooLarge)
    }
    val extraction = try {
        extractArchivePages(this, cacheDir, maxExtractionBytes, shouldContinue)
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
    shouldContinue: () -> Boolean,
): ArchiveExtraction {
    val extractedPages = mutableListOf<ExtractedPage>()
    val rejectedEntries = mutableListOf<String>()
    var scannedEntries = 0
    var visitedEntries = 0
    var processedImageBytes = 0L

    ZipFile(archiveFile).use { zipFile ->
        val entries = zipFile.entries()
        while (entries.hasMoreElements()) {
            if (!shouldContinue()) throw CancellationException()
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
                        shouldContinue = shouldContinue,
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
    shouldContinue: () -> Boolean,
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
            shouldContinue = shouldContinue,
        )
    }

    return archiveFile
}

private fun resetArchiveCache(context: Context): File =
    File(context.cacheDir, ARCHIVE_CACHE_DIR_NAME).apply {
        deleteRecursively()
        mkdirs()
    }

private fun readCachedArchivePages(
    cacheDir: File,
    sourceKey: String?,
): List<ComicPage>? {
    if (sourceKey == null || !cacheDir.isDirectory) return null
    val manifestFile = File(cacheDir, ARCHIVE_CACHE_MANIFEST_NAME)
    if (!manifestFile.isFile || manifestFile.length() !in 1..MAX_ARCHIVE_CACHE_MANIFEST_BYTES) return null
    return runCatching {
        val root = JSONObject(manifestFile.readText(Charsets.UTF_8))
        if (root.optInt("version", -1) != ARCHIVE_CACHE_MANIFEST_VERSION) return@runCatching null
        if (root.optString("sourceKey") != sourceKey) return@runCatching null
        val storedPages = root.optJSONArray("pages") ?: return@runCatching null
        if (storedPages.length() !in 1..MAX_ARCHIVE_ENTRY_COUNT) return@runCatching null

        (0 until storedPages.length()).map { index ->
            val item = storedPages.optJSONObject(index) ?: return@runCatching null
            val fileName = item.optString("file")
                .takeIf { it.isNotBlank() && '/' !in it && '\\' !in it }
                ?: return@runCatching null
            val pageFile = File(cacheDir, fileName)
            if (!pageFile.isFile || pageFile.length() !in 1..MAX_ARCHIVE_PAGE_BYTES) return@runCatching null
            val imageInfo = detectImageInfo(pageFile) ?: return@runCatching null
            val entryName = item.optString("entry")
                .takeIf(String::isNotBlank)
                ?.take(4_096)
                ?: return@runCatching null
            ComicPage(
                index = index,
                displayName = entryName.substringAfterLast('/'),
                relativePath = entryName,
                imageUri = pageFile.toUri(),
                width = imageInfo.width,
                height = imageInfo.height,
            )
        }
    }.getOrNull()
}

private fun writeArchiveCacheManifest(
    cacheDir: File,
    sourceKey: String,
    pages: List<ComicPage>,
) {
    val pageItems = JSONArray().apply {
        pages.forEach { page ->
            val pageFile = File(requireNotNull(page.imageUri.path))
            put(
                JSONObject()
                    .put("entry", page.relativePath)
                    .put("file", pageFile.name),
            )
        }
    }
    val bytes = JSONObject()
        .put("version", ARCHIVE_CACHE_MANIFEST_VERSION)
        .put("sourceKey", sourceKey)
        .put("pages", pageItems)
        .toString()
        .toByteArray(Charsets.UTF_8)
    if (bytes.size > MAX_ARCHIVE_CACHE_MANIFEST_BYTES) return

    val manifestFile = File(cacheDir, ARCHIVE_CACHE_MANIFEST_NAME)
    val pendingFile = File(cacheDir, "$ARCHIVE_CACHE_MANIFEST_NAME.tmp")
    runCatching {
        pendingFile.writeBytes(bytes)
        manifestFile.delete()
        if (!pendingFile.renameTo(manifestFile)) {
            pendingFile.copyTo(manifestFile, overwrite = true)
            pendingFile.delete()
        }
    }.onFailure {
        pendingFile.delete()
        manifestFile.delete()
    }
}

private fun File.localArchiveCacheKey(): String? {
    val sourceLength = length().takeIf { it >= 0L } ?: return null
    val sourceLastModified = lastModified().takeIf { it > 0L } ?: return null
    return archiveCacheKey("local|${canonicalPathOrAbsolute()}|$sourceLength|$sourceLastModified")
}

private fun Context.documentArchiveCacheKey(uri: Uri): String? {
    val size = queryArchiveLong(uri, OpenableColumns.SIZE) ?: return null
    val lastModified = queryArchiveLong(uri, DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        ?.takeIf { it > 0L }
        ?: return null
    return archiveCacheKey("document|$uri|$size|$lastModified")
}

private fun archiveCacheKey(sourceIdentity: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(sourceIdentity.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(Locale.ROOT, byte.toInt() and 0xff) }

private fun Context.queryArchiveLong(uri: Uri, column: String): Long? = runCatching {
    contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(column)
        if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) cursor.getLong(index) else null
    }
}.getOrNull()?.takeIf { it >= 0L }

private fun File.canonicalPathOrAbsolute(): String =
    runCatching { canonicalPath }.getOrDefault(absolutePath)

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
    substringBeforeLast('.', missingDelimiterValue = this).ifBlank { this }

private data class ExtractedPage(
    val entryName: String,
    val file: File,
    val info: ImageInfo,
)

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
