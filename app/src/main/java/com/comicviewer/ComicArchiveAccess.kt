package com.comicviewer

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.net.toUri
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val MAX_COMIC_ARCHIVE_ENTRIES = 100_000
internal const val MAX_ARCHIVE_PAGE_BYTES = 256L * 1024L * 1024L
internal const val COMIC_ARCHIVE_PAGE_SCHEME = "comic-archive"
private const val COMIC_ARCHIVE_PAGE_AUTHORITY = "page"
private const val PAGE_URI_DOCUMENT_PARAM = "document"
private const val PAGE_URI_FILE_PARAM = "file"
private const val PAGE_URI_ENTRY_PARAM = "entry"
private const val PAGE_URI_NAME_PARAM = "name"
private const val ARCHIVE_COPY_DIR_NAME = "archive_copies"
private const val ARCHIVE_COPY_RESERVE_BYTES = 128L * 1024L * 1024L
private const val HEADER_SNIFF_BUFFER_BYTES = 512
private const val MAX_PAGE_REJECTION_EXAMPLES = 4
private const val COMIC_INFO_FILE_NAME = "ComicInfo.xml"
private val archiveCopyLock = Any()

/** Where an archive lives; enough to reopen it at any time, including from a page URI. */
internal sealed interface ComicArchiveLocation {
    val key: String

    data class Document(val uri: Uri) : ComicArchiveLocation {
        override val key: String
            get() = "document:$uri"
    }

    data class LocalFile(val path: String) : ComicArchiveLocation {
        override val key: String
            get() = "file:$path"
    }
}

internal class OpenedComicArchive(
    val reader: ZipArchiveReader,
    private val stream: FileInputStream,
) : Closeable {
    /** False once the file channel was closed, including by an interrupted read on another thread. */
    val isOpen: Boolean
        get() = stream.channel.isOpen

    override fun close() = stream.close()
}

internal fun openLocalComicArchive(
    file: File,
    maxEntries: Int = MAX_COMIC_ARCHIVE_ENTRIES,
    shouldContinue: () -> Boolean = { true },
): OpenedComicArchive = openArchiveStream(FileInputStream(file), maxEntries, shouldContinue)

/**
 * A random-access descriptor for a picked document, or null when the provider only offers a one-way
 * stream (a pipe) or a section of a larger file ("Not a whole file"), which cannot be read without a
 * copy. A document that is really missing fails again, with a clear error, when it is streamed.
 */
internal fun openSeekableDocumentDescriptor(context: Context, uri: Uri): ParcelFileDescriptor? {
    val descriptor = try {
        context.contentResolver.openFileDescriptor(uri, "r")
    } catch (_: UnsupportedOperationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: FileNotFoundException) {
        null
    } ?: return null
    if (descriptor.statSize <= 0L) {
        descriptor.close()
        return null
    }
    return descriptor
}

/** Opens an archive through [descriptor], which it takes ownership of. */
internal fun openComicArchive(
    descriptor: ParcelFileDescriptor,
    maxEntries: Int = MAX_COMIC_ARCHIVE_ENTRIES,
    shouldContinue: () -> Boolean = { true },
): OpenedComicArchive =
    openArchiveStream(ParcelFileDescriptor.AutoCloseInputStream(descriptor), maxEntries, shouldContinue)

private fun openArchiveStream(
    stream: FileInputStream,
    maxEntries: Int,
    shouldContinue: () -> Boolean,
): OpenedComicArchive = try {
    OpenedComicArchive(ZipArchiveReader.open(stream.channel, maxEntries, shouldContinue), stream)
} catch (throwable: Throwable) {
    stream.close()
    throw throwable
}

/**
 * Fallback for stream-only providers: copies the document once, limited only by free space, and keeps
 * a single copy that is reused while the source is unchanged.
 */
internal fun copyDocumentToCache(
    context: Context,
    uri: Uri,
    shouldContinue: () -> Boolean,
): File = synchronized(archiveCopyLock) {
    val copyDir = archiveCopyDir(context).apply { mkdirs() }
    val identity = context.documentArchiveIdentity(uri)
    val copyFile = File(copyDir, "${sha256Hex(identity ?: uri.toString())}.cbz")
    if (identity != null && copyFile.isFile && copyFile.length() > 0L) return@synchronized copyFile

    copyDir.listFiles().orEmpty().forEach(File::delete)
    val pendingFile = File(copyDir, "${copyFile.name}.part")
    val input = context.contentResolver.openInputStream(uri)
        ?: throw ComicLoadException(ComicLoadError.ArchiveNotReadable)
    input.use { source ->
        copyToFileBounded(
            inputStream = source,
            outputFile = pendingFile,
            maxBytes = copyDir.usableSpace - ARCHIVE_COPY_RESERVE_BYTES,
            limitError = ComicLoadError.NotEnoughArchiveSpace,
            shouldContinue = shouldContinue,
        )
    }
    if (!pendingFile.renameTo(copyFile)) {
        pendingFile.delete()
        throw IOException("Could not store the archive copy.")
    }
    copyFile
}

private fun archiveCopyDir(context: Context): File = File(context.cacheDir, ARCHIVE_COPY_DIR_NAME)

internal fun deleteArchiveCopies(context: Context) {
    synchronized(archiveCopyLock) {
        archiveCopyDir(context).deleteRecursively()
    }
}

internal fun Context.documentArchiveIdentity(uri: Uri): String? {
    val size = queryDocumentLong(uri, OpenableColumns.SIZE) ?: return null
    val lastModified = queryDocumentLong(uri, DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        ?.takeIf { it > 0L }
        ?: return null
    return "document|$uri|$size|$lastModified"
}

internal fun Context.documentArchiveFingerprint(uri: Uri): String? {
    val size = queryDocumentLong(uri, OpenableColumns.SIZE)
    val lastModified = queryDocumentLong(uri, DocumentsContract.Document.COLUMN_LAST_MODIFIED)
    return if (size != null || lastModified != null) "${size ?: -1L}:${lastModified ?: -1L}" else null
}

private fun sha256Hex(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(Locale.ROOT, byte.toInt() and 0xff) }

/**
 * Keeps the reader's open sources (parsed archives, PDF renderers) so page requests reuse them.
 * Leases keep a source open while a page is read, even if it is evicted or the comic closes meanwhile.
 */
internal open class ComicSourceSessions<T : Closeable>(
    private val maxOpenSources: Int,
    private val openSource: (Context, ComicArchiveLocation, () -> Boolean) -> T,
    /** A session whose source can no longer be read is replaced instead of failing every later page. */
    private val isUsable: (T) -> Boolean = { true },
) {
    private val sessions = LinkedHashMap<String, Session<T>>(maxOpenSources + 1, 0.75f, true)

    class Session<T : Closeable> internal constructor(val key: String, val source: T) {
        var leases = 0
        var retired = false
    }

    inner class Lease internal constructor(private val session: Session<T>) : Closeable {
        private var released = false

        val source: T
            get() = session.source

        override fun close() {
            synchronized(this@ComicSourceSessions) {
                if (released) return
                released = true
                session.leases -= 1
                if (session.retired && session.leases == 0) session.source.close()
            }
        }
    }

    /** With [reopen], discards any open copy first so a changed file is read fresh. */
    fun acquire(
        context: Context,
        location: ComicArchiveLocation,
        reopen: Boolean = false,
        shouldContinue: () -> Boolean = { true },
    ): Lease {
        synchronized(this) {
            val existing = sessions[location.key]
            if (existing != null) {
                if (!reopen && isUsable(existing.source)) return leaseLocked(existing)
                retireLocked(existing)
            }
        }
        val source = openSource(context, location, shouldContinue)
        synchronized(this) {
            val existing = sessions[location.key]
            if (existing != null && !reopen && isUsable(existing.source)) {
                source.close()
                return leaseLocked(existing)
            }
            return registerLocked(location, source)
        }
    }

    /**
     * Like [acquire], for a [source] the caller already opened: it becomes the session unless one is
     * open for [location] already, in which case [source] is closed and the open session is used.
     */
    fun adopt(location: ComicArchiveLocation, source: T): Lease = synchronized(this) {
        val existing = sessions[location.key]
        if (existing != null && isUsable(existing.source)) {
            source.close()
            return leaseLocked(existing)
        }
        registerLocked(location, source)
    }

    fun closeAll() = synchronized(this) {
        sessions.values.toList().forEach { retireLocked(it) }
    }

    private fun registerLocked(location: ComicArchiveLocation, source: T): Lease {
        sessions[location.key]?.let { retireLocked(it) }
        val session = Session(location.key, source)
        sessions[location.key] = session
        val lease = leaseLocked(session)
        while (sessions.size > maxOpenSources) {
            retireLocked(sessions.values.first())
        }
        return lease
    }

    private fun leaseLocked(session: Session<T>): Lease {
        session.leases += 1
        return Lease(session)
    }

    private fun retireLocked(session: Session<T>) {
        if (sessions[session.key] === session) sessions.remove(session.key)
        if (session.retired) return
        session.retired = true
        if (session.leases == 0) session.source.close()
    }
}

internal class ComicArchivePageRef(
    val location: ComicArchiveLocation,
    val entryIndex: Int,
    val entryName: String,
)

internal fun comicArchivePageUri(location: ComicArchiveLocation, entry: ZipArchiveEntry): Uri =
    Uri.Builder()
        .scheme(COMIC_ARCHIVE_PAGE_SCHEME)
        .authority(COMIC_ARCHIVE_PAGE_AUTHORITY)
        .appendComicLocation(location)
        .appendQueryParameter(PAGE_URI_ENTRY_PARAM, entry.index.toString())
        .appendQueryParameter(PAGE_URI_NAME_PARAM, entry.name)
        .build()

internal fun parseComicArchivePageUri(uri: Uri): ComicArchivePageRef? {
    if (uri.scheme != COMIC_ARCHIVE_PAGE_SCHEME || uri.authority != COMIC_ARCHIVE_PAGE_AUTHORITY) return null
    val location = uri.comicLocation() ?: return null
    val entryIndex = uri.getQueryParameter(PAGE_URI_ENTRY_PARAM)?.toIntOrNull()?.takeIf { it >= 0 } ?: return null
    val entryName = uri.getQueryParameter(PAGE_URI_NAME_PARAM) ?: return null
    return ComicArchivePageRef(location, entryIndex, entryName)
}

/** Encodes where a page's source file lives, for archive and PDF page URIs alike. */
internal fun Uri.Builder.appendComicLocation(location: ComicArchiveLocation): Uri.Builder = when (location) {
    is ComicArchiveLocation.Document -> appendQueryParameter(PAGE_URI_DOCUMENT_PARAM, location.uri.toString())
    is ComicArchiveLocation.LocalFile -> appendQueryParameter(PAGE_URI_FILE_PARAM, location.path)
}

internal fun Uri.comicLocation(): ComicArchiveLocation? =
    getQueryParameter(PAGE_URI_DOCUMENT_PARAM)?.let { ComicArchiveLocation.Document(it.toUri()) }
        ?: getQueryParameter(PAGE_URI_FILE_PARAM)?.let(ComicArchiveLocation::LocalFile)

internal fun ZipArchiveReader.findEntry(index: Int, name: String): ZipArchiveEntry? =
    entries.getOrNull(index)?.takeIf { it.name == name } ?: entries.firstOrNull { it.name == name }

internal class ComicArchivePageScan(
    val pages: List<ZipArchiveEntry>,
    val scannedEntries: Int,
    val rejectedExamples: List<String>,
    val comicInfoEntry: ZipArchiveEntry?,
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

/**
 * Lists the archive's image pages in reading order. Only each entry's first bytes are read to check
 * that it really is an image; pages are decoded later, when shown. A page over [maxPageBytes] is left
 * out like any other unreadable entry, so one oversized scan does not make the whole comic unreadable.
 */
internal fun ZipArchiveReader.scanComicPages(
    maxPageBytes: Long = MAX_ARCHIVE_PAGE_BYTES,
    shouldContinue: () -> Boolean = { true },
): ComicArchivePageScan {
    val pages = ArrayList<ZipArchiveEntry>()
    val rejectedExamples = ArrayList<String>()
    var scannedEntries = 0
    var comicInfoEntry: ZipArchiveEntry? = null

    for (entry in entries) {
        if (!shouldContinue()) throw CancellationException()
        if (entry.isDirectory || isHiddenComicPath(entry.name)) continue
        scannedEntries += 1
        if (entry.isComicInfo()) {
            if (comicInfoEntry == null) comicInfoEntry = entry
            continue
        }
        if (entry.size in 1L..maxPageBytes && sniffImageFormat(entry) != null) {
            pages += entry
        } else if (rejectedExamples.size < MAX_PAGE_REJECTION_EXAMPLES) {
            rejectedExamples += entry.name.substringAfterLast('/')
        }
    }
    pages.sortWith { left, right -> compareComicPageNames(left.name, right.name) }

    return ComicArchivePageScan(
        pages = pages,
        scannedEntries = scannedEntries,
        rejectedExamples = rejectedExamples,
        comicInfoEntry = comicInfoEntry,
    )
}

/** The cover: the first image in reading order, checking entries only until one is found. */
internal fun ZipArchiveReader.firstComicPageEntry(
    maxPageBytes: Long,
    shouldContinue: () -> Boolean = { true },
): ZipArchiveEntry? = firstComicPages(count = 1, maxPageBytes = maxPageBytes, shouldContinue = shouldContinue)
    .firstOrNull()

/**
 * The first [count] pages in reading order, in the same order [scanComicPages] produces, but only
 * checking entries until enough valid images are found.
 */
internal fun ZipArchiveReader.firstComicPages(
    count: Int,
    maxPageBytes: Long = MAX_ARCHIVE_PAGE_BYTES,
    shouldContinue: () -> Boolean = { true },
): List<ZipArchiveEntry> {
    if (count <= 0) return emptyList()
    val pages = ArrayList<ZipArchiveEntry>(count)
    val candidates = entries
        .filter { entry ->
            !entry.isDirectory &&
                entry.size in 1L..maxPageBytes &&
                !isHiddenComicPath(entry.name) &&
                !entry.isComicInfo()
        }
        .sortedWith { left, right -> compareComicPageNames(left.name, right.name) }
    for (entry in candidates) {
        if (!shouldContinue()) throw CancellationException()
        if (sniffImageFormat(entry) != null) pages += entry
        if (pages.size == count) break
    }
    return pages
}

/**
 * Image URIs for the first [maxPages] pages of a comic, for preview strips. Archives that can only
 * be streamed return nothing rather than being copied just for previews.
 */
suspend fun loadComicPagePreviews(
    context: Context,
    sourceType: RecentComicSourceType,
    path: String,
    maxPages: Int,
): List<Uri> = withContext(Dispatchers.IO) {
    try {
        when (sourceType) {
            // The preview pages are then fetched through the same open file.
            RecentComicSourceType.LocalArchive -> {
                val location = ComicArchiveLocation.LocalFile(path)
                ComicFileSessions.acquire(context, location).use { lease ->
                    lease.source.previewPageUris(location, maxPages)
                }
            }
            RecentComicSourceType.DocumentArchive -> {
                val uri = path.toUri()
                val location = ComicArchiveLocation.Document(uri)
                openSeekableDocumentComicFile(context, uri)?.let { file ->
                    ComicFileSessions.adopt(location, file).use { lease ->
                        lease.source.previewPageUris(location, maxPages)
                    }
                }.orEmpty()
            }
            RecentComicSourceType.LocalFolder -> firstLocalFolderImages(File(path), maxPages).map { it.toUri() }
            RecentComicSourceType.DocumentFolder ->
                loadFolderComic(context, path.toUri()).pages.take(maxPages).map { it.imageUri }
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        emptyList()
    }
}

/** Whether any of the first [maxVisitedEntries] entries, in archive order, is a supported image. */
internal fun ZipArchiveReader.containsComicImage(maxVisitedEntries: Int): Boolean {
    entries.forEachIndexed { index, entry ->
        if (index >= maxVisitedEntries) return false
        if (!entry.isDirectory && !isHiddenComicPath(entry.name) && sniffImageFormat(entry) != null) return true
    }
    return false
}

internal fun ZipArchiveReader.sniffImageFormat(entry: ZipArchiveEntry): ImageFormat? = try {
    openEntry(entry, HEADER_SNIFF_BUFFER_BYTES).use { input -> detectImageFormat(readImageHeader(input)) }
} catch (_: IOException) {
    null
}

private fun ZipArchiveEntry.isComicInfo(): Boolean =
    name.substringAfterLast('/').equals(COMIC_INFO_FILE_NAME, ignoreCase = true)
