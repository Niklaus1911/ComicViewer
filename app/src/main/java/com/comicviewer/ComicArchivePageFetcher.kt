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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import okio.ForwardingSource
import okio.buffer
import okio.source

/** Streams a comic page straight out of its archive when Coil asks for a `comic-archive:` page URI. */
internal class ComicArchivePageFetcher(
    private val context: Context,
    private val page: ComicArchivePageRef,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val coroutineContext = currentCoroutineContext()
        val lease = ComicFileSessions.acquire(
            context = context,
            location = page.location,
            shouldContinue = { coroutineContext.isActive },
        )
        try {
            val reader = (lease.source as? OpenedComicFile.Zip)?.archive?.reader
                ?: throw FileNotFoundException("${page.entryName} is no longer in a ZIP archive.")
            val entry = reader.findEntry(page.entryIndex, page.entryName)
                ?: throw FileNotFoundException("Page ${page.entryName} is no longer in the archive.")
            if (entry.size > MAX_ARCHIVE_PAGE_BYTES) throw ComicLoadException(ComicLoadError.ArchivePageTooLarge)
            val pageSource = object : ForwardingSource(reader.openEntry(entry).source()) {
                override fun close() {
                    try {
                        super.close()
                    } finally {
                        lease.close()
                    }
                }
            }
            return SourceFetchResult(
                source = ImageSource(source = pageSource.buffer(), fileSystem = options.fileSystem),
                mimeType = null,
                dataSource = DataSource.DISK,
            )
        } catch (throwable: Throwable) {
            lease.close()
            throw throwable
        }
    }

    class Factory : Fetcher.Factory<CoilUri> {
        override fun create(data: CoilUri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.scheme != COMIC_ARCHIVE_PAGE_SCHEME) return null
            val page = parseComicArchivePageUri(data.toString().toUri()) ?: return null
            return ComicArchivePageFetcher(options.context, page, options)
        }
    }
}
