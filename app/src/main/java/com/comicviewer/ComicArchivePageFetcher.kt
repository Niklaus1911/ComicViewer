package com.comicviewer

import android.content.Context
import androidx.core.net.toUri
import coil3.ImageLoader
import coil3.Uri as CoilUri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import java.io.FileNotFoundException
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import okio.buffer
import okio.source

private const val PAGE_READ_CHUNK_BYTES = 64 * 1024

/**
 * Reads a comic page out of its archive when Coil asks for a `comic-archive:` page URI.
 *
 * The page is read here and handed to Coil from memory rather than streamed: Coil decodes on a thread
 * it interrupts when a load is cancelled, and an interrupt during a read closes the archive's file
 * channel for every page of the comic. Fetching is never interrupted, so the archive stays open.
 */
internal class ComicArchivePageFetcher(
    private val context: Context,
    private val page: ComicArchivePageRef,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val coroutineContext = currentCoroutineContext()
        val shouldContinue = { coroutineContext.isActive }
        val pageBytes = ComicFileSessions.acquire(
            context = context,
            location = page.location,
            shouldContinue = shouldContinue,
        ).use { lease ->
            val reader = (lease.source as? OpenedComicFile.Zip)?.archive?.reader
                ?: throw FileNotFoundException("${page.entryName} is no longer in a ZIP archive.")
            val entry = reader.findEntry(page.entryIndex, page.entryName)
                ?: throw FileNotFoundException("Page ${page.entryName} is no longer in the archive.")
            if (entry.size > MAX_ARCHIVE_PAGE_BYTES) throw ComicLoadException(ComicLoadError.ArchivePageTooLarge)
            reader.openEntry(entry).use { input -> input.readPage(entry.size, shouldContinue) }
        }
        return SourceFetchResult(
            source = ImageSource(source = pageBytes.inputStream().source().buffer(), fileSystem = options.fileSystem),
            mimeType = null,
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<CoilUri> {
        override fun create(data: CoilUri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.scheme != COMIC_ARCHIVE_PAGE_SCHEME) return null
            val page = parseComicArchivePageUri(data.toString().toUri()) ?: return null
            return ComicArchivePageFetcher(options.context, page, options)
        }
    }
}

/** Reads a page of [size] bytes, giving up between chunks once its load has been cancelled. */
private fun InputStream.readPage(size: Long, shouldContinue: () -> Boolean): ByteArray {
    val bytes = ByteArray(size.toInt())
    var read = 0
    while (read < bytes.size) {
        if (!shouldContinue()) throw CancellationException()
        val count = read(bytes, read, minOf(PAGE_READ_CHUNK_BYTES, bytes.size - read))
        if (count < 0) break
        read += count
    }
    return if (read == bytes.size) bytes else bytes.copyOf(read)
}
