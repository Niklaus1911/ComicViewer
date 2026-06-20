package com.example.myapplication

import android.content.Context
import androidx.core.net.toUri
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val BROWSER_THUMBNAIL_CACHE_DIR_NAME = "browser_thumbnails"

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
            val entries = zipFile.entries().asSequence()
                .filter { entry -> !entry.isDirectory && !isHiddenComicPath(entry.name.replace('\\', '/')) }
                .sortedWith { left, right -> compareComicPageNames(left.name, right.name) }
                .toList()

            entries.firstNotNullOfOrNull { entry ->
                val bytes = zipFile.getInputStream(entry).use { input -> input.readBytes() }
                val format = detectImageFormat(bytes) ?: return@firstNotNullOfOrNull null
                val outputFile = File(cacheDir, "${absolutePath.hashCode()}_thumbnail.${format.extension}")
                FileOutputStream(outputFile).use { output -> output.write(bytes) }
                outputFile
            }
        }
    }.getOrNull()

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
