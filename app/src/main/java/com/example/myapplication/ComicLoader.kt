package com.example.myapplication

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ARCHIVE_CACHE_DIR_NAME = "current_comic_pages"
private const val MAX_REJECTION_EXAMPLES = 4

class ComicLoadException(message: String) : IllegalArgumentException(message)

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
    val extraction = try {
        extractArchivePages(archiveFile, cacheDir)
    } catch (_: Exception) {
        throw ComicLoadException("Seleziona un archivio .zip o .cbz valido.")
    }
    val extractedPages = extraction.pages
        .sortedWith { left, right -> compareComicPageNames(left.entryName, right.entryName) }

    if (extractedPages.isEmpty()) {
        throw ComicLoadException(extraction.emptyMessage())
    }

    val pages = extractedPages.mapIndexed { index, extracted ->
        ComicPage(
            index = index,
            displayName = extracted.entryName.substringAfterLast('/'),
            relativePath = extracted.entryName,
            imageUri = extracted.file.toUri(),
            width = extracted.info.width,
            height = extracted.info.height,
        )
    }

    ComicBook(
        title = displayName.withoutComicExtension(),
        source = ComicSource.Archive(uri = uri, displayName = displayName),
        pages = pages,
    )
}

suspend fun loadFolderComic(context: Context, uri: Uri): ComicBook = withContext(Dispatchers.IO) {
    val folder = DocumentFile.fromTreeUri(context, uri)
        ?: throw ComicLoadException("Cartella non leggibile.")

    if (!folder.canRead()) {
        throw ComicLoadException("Permesso di lettura della cartella non disponibile.")
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
        val displayName = document.name ?: "Pagina ${index + 1}"
        ComicPage(
            index = index,
            displayName = displayName,
            relativePath = displayName,
            imageUri = document.uri,
            width = 0,
            height = 0,
        )
    }

    val displayName = folder.name ?: "Cartella comic"
    ComicBook(
        title = displayName,
        source = ComicSource.Folder(uri = uri, displayName = displayName),
        pages = pages,
    )
}

fun clearArchiveCache(context: Context) {
    File(context.cacheDir, ARCHIVE_CACHE_DIR_NAME).deleteRecursively()
}

private fun extractArchivePages(
    archiveFile: File,
    cacheDir: File,
): ArchiveExtraction {
    val extractedPages = mutableListOf<ExtractedPage>()
    val rejectedEntries = mutableListOf<String>()
    var scannedEntries = 0

    ZipFile(archiveFile).use { zipFile ->
        val entries = zipFile.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            val entryName = entry.name.replace('\\', '/')

            if (!entry.isDirectory && !isHiddenComicPath(entryName)) {
                scannedEntries += 1
                val imageInfo = zipFile.getInputStream(entry).use { input ->
                    detectImageInfo(input)
                }

                if (entry.size == 0L || imageInfo == null) {
                    if (rejectedEntries.size < MAX_REJECTION_EXAMPLES) {
                        rejectedEntries += entryName.substringAfterLast('/')
                    }
                    continue
                }

                val outputFile = File(
                    cacheDir,
                    safeCachePageName(extractedPages.size, imageInfo.format),
                )
                zipFile.getInputStream(entry).use { input ->
                    FileOutputStream(outputFile).use { output ->
                        input.copyTo(output)
                    }
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

private fun copyArchiveToCache(
    context: Context,
    uri: Uri,
    displayName: String,
    cacheDir: File,
): File {
    val inputStream = context.contentResolver.openInputStream(uri)
        ?: throw ComicLoadException("Archivio non leggibile.")
    val archiveFile = File(cacheDir, "selected_comic.${displayName.archiveExtension()}")

    inputStream.use { input ->
        archiveFile.outputStream().use { output ->
            input.copyTo(output)
        }
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

private fun detectImageInfo(inputStream: InputStream): ImageInfo? {
    val bytes = inputStream.readBytes()
    val format = detectImageFormat(bytes) ?: return null
    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

    return if (options.outWidth > 0 && options.outHeight > 0) {
        ImageInfo(format = format, width = options.outWidth, height = options.outHeight)
    } else {
        null
    }
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

private data class ArchiveExtraction(
    val scannedEntries: Int,
    val pages: List<ExtractedPage>,
    val rejectedExamples: List<String>,
) {
    fun emptyMessage(): String {
        val examples = if (rejectedExamples.isEmpty()) {
            ""
        } else {
            " Esempi rifiutati: ${rejectedExamples.joinToString()}."
        }
        return "Nessuna immagine valida trovata. Scansionati $scannedEntries file.$examples"
    }
}
