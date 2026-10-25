package com.comicviewer

import android.content.Context
import androidx.core.net.toUri
import coil3.ImageLoader
import coil3.Uri as CoilUri
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import coil3.size.Scale
import coil3.size.pxOrElse
import java.io.FileNotFoundException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/**
 * Renders a PDF page when Coil asks for a `comic-pdf:` page URI, at the size the request needs. The
 * result is marked as sampled so a later, larger request renders the page again instead of upscaling.
 */
internal class ComicPdfPageFetcher(
    private val context: Context,
    private val page: ComicPdfPageRef,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val coroutineContext = currentCoroutineContext()
        val bitmap = ComicPdfSessions.acquire(
            context = context,
            location = page.location,
            shouldContinue = { coroutineContext.isActive },
        ).use { lease ->
            if (page.pageIndex >= lease.source.pageCount) {
                throw FileNotFoundException("Page ${page.pageIndex + 1} is no longer in the PDF.")
            }
            lease.source.renderPage(page.pageIndex) { pageWidth, pageHeight ->
                pdfRenderSize(
                    pageWidth = pageWidth,
                    pageHeight = pageHeight,
                    requestedWidth = options.size.width.pxOrElse { 0 },
                    requestedHeight = options.size.height.pxOrElse { 0 },
                    fill = options.scale == Scale.FILL,
                )
            }
        }
        return ImageFetchResult(
            image = bitmap.asImage(),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<CoilUri> {
        override fun create(data: CoilUri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.scheme != COMIC_PDF_PAGE_SCHEME) return null
            val page = parseComicPdfPageUri(data.toString().toUri()) ?: return null
            return ComicPdfPageFetcher(options.context, page, options)
        }
    }
}
