package com.comicviewer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.Os
import androidx.core.graphics.createBitmap
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal const val COMIC_PDF_PAGE_SCHEME = "comic-pdf"
internal const val MAX_PDF_PAGES = 100_000
internal const val DEFAULT_PDF_RENDER_LONG_SIDE_PX = 2_048
internal const val MAX_PDF_RENDER_LONG_SIDE_PX = 4_096
internal const val MAX_PDF_RENDER_PIXELS = 12_000_000L
private const val COMIC_PDF_PAGE_AUTHORITY = "page"
private const val PDF_PAGE_URI_INDEX_PARAM = "index"
private const val PDF_HEADER_SEARCH_BYTES = 1_024
private val PDF_SIGNATURE = "%PDF-".toByteArray(Charsets.US_ASCII)
private val ZIP_SIGNATURES = listOf(
    byteArrayOf(0x50, 0x4B, 0x03, 0x04),
    byteArrayOf(0x50, 0x4B, 0x05, 0x06),
)

/**
 * All [PdfRenderer] instances share pdfium, which is not thread-safe and which older platform
 * versions do not lock across documents, so every open, render and close goes through this lock.
 */
private val pdfiumLock = Any()

internal data class PdfRenderSize(val width: Int, val height: Int)

/** An open PDF. [PdfRenderer] also allows only one open page at a time per document. */
internal class OpenedComicPdf(private val renderer: PdfRenderer) : Closeable {
    val pageCount: Int = renderer.pageCount

    /**
     * Renders page [index] onto white at the size [targetSize] picks from the page's size in points.
     * Renders queue on the shared lock, so one cancelled while waiting is skipped via [shouldContinue].
     */
    fun renderPage(
        index: Int,
        shouldContinue: () -> Boolean = { true },
        targetSize: (pageWidth: Int, pageHeight: Int) -> PdfRenderSize,
    ): Bitmap = synchronized(pdfiumLock) {
        if (!shouldContinue()) throw CancellationException()
        renderer.openPage(index).use { page ->
            val size = targetSize(page.width, page.height)
            val bitmap = createBitmap(size.width, size.height)
            try {
                // PDF pages are transparent wherever nothing is drawn.
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap.setHasAlpha(false)
            } catch (throwable: Throwable) {
                bitmap.recycle()
                throw throwable
            }
            bitmap
        }
    }

    override fun close() = synchronized(pdfiumLock) {
        renderer.close()
    }
}

/** [PdfRenderer] takes ownership of [descriptor] and closes it with the renderer. */
internal fun openComicPdf(descriptor: ParcelFileDescriptor): OpenedComicPdf {
    val renderer = try {
        synchronized(pdfiumLock) { PdfRenderer(descriptor) }
    } catch (throwable: Throwable) {
        descriptor.close()
        throw when (throwable) {
            is SecurityException -> ComicLoadException(ComicLoadError.PdfPasswordProtected)
            is IOException, is IllegalArgumentException -> ComicLoadException(ComicLoadError.InvalidArchive)
            else -> throwable
        }
    }
    if (renderer.pageCount > MAX_PDF_PAGES) {
        synchronized(pdfiumLock) { renderer.close() }
        throw ComicLoadException(ComicLoadError.PdfTooManyPages, MAX_PDF_PAGES)
    }
    return OpenedComicPdf(renderer)
}

internal class ComicPdfPageRef(
    val location: ComicArchiveLocation,
    val pageIndex: Int,
)

internal fun comicPdfPageUri(location: ComicArchiveLocation, pageIndex: Int): Uri =
    Uri.Builder()
        .scheme(COMIC_PDF_PAGE_SCHEME)
        .authority(COMIC_PDF_PAGE_AUTHORITY)
        .appendComicLocation(location)
        .appendQueryParameter(PDF_PAGE_URI_INDEX_PARAM, pageIndex.toString())
        .build()

internal fun parseComicPdfPageUri(uri: Uri): ComicPdfPageRef? {
    if (uri.scheme != COMIC_PDF_PAGE_SCHEME || uri.authority != COMIC_PDF_PAGE_AUTHORITY) return null
    val location = uri.comicLocation() ?: return null
    val pageIndex = uri.getQueryParameter(PDF_PAGE_URI_INDEX_PARAM)?.toIntOrNull()?.takeIf { it >= 0 } ?: return null
    return ComicPdfPageRef(location, pageIndex)
}

/** Page URIs for the first [maxPages] pages, for preview strips. */
internal fun OpenedComicPdf.previewPageUris(location: ComicArchiveLocation, maxPages: Int): List<Uri> =
    List(min(pageCount, maxPages).coerceAtLeast(0)) { index -> comicPdfPageUri(location, index) }

/**
 * Pixel size for rendering a [pageWidth]×[pageHeight] point page so it fits (or, with [fill], covers)
 * the requested box. Without a requested size the long side gets [defaultLongSidePx]. The result is
 * capped at [maxLongSidePx] and [maxPixels] so a single page bitmap stays a bounded size.
 */
internal fun pdfRenderSize(
    pageWidth: Int,
    pageHeight: Int,
    requestedWidth: Int?,
    requestedHeight: Int?,
    fill: Boolean = false,
    defaultLongSidePx: Int = DEFAULT_PDF_RENDER_LONG_SIDE_PX,
    maxLongSidePx: Int = MAX_PDF_RENDER_LONG_SIDE_PX,
    maxPixels: Long = MAX_PDF_RENDER_PIXELS,
): PdfRenderSize {
    val width = pageWidth.coerceAtLeast(1).toDouble()
    val height = pageHeight.coerceAtLeast(1).toDouble()
    val longSide = max(width, height)
    val widthScale = requestedWidth?.takeIf { it > 0 }?.let { it / width }
    val heightScale = requestedHeight?.takeIf { it > 0 }?.let { it / height }
    val requestedScale = when {
        widthScale != null && heightScale != null -> if (fill) max(widthScale, heightScale) else min(widthScale, heightScale)
        widthScale != null -> widthScale
        heightScale != null -> heightScale
        else -> defaultLongSidePx / longSide
    }
    val scale = minOf(
        requestedScale,
        maxLongSidePx / longSide,
        sqrt(maxPixels / (width * height)),
    )
    return PdfRenderSize(
        width = (width * scale).roundToInt().coerceIn(1, maxLongSidePx),
        height = (height * scale).roundToInt().coerceIn(1, maxLongSidePx),
    )
}

/**
 * Whether [bytes] start a PDF: readers accept the `%PDF-` signature anywhere in the first 1024
 * bytes. ZIP files are excluded first, since a CBZ can store a PDF uncompressed near its start.
 */
internal fun bytesLookLikePdf(bytes: ByteArray, length: Int = bytes.size): Boolean {
    val available = min(length, bytes.size)
    if (ZIP_SIGNATURES.any { signature -> bytes.startsWith(signature, available) }) return false
    val lastStart = min(available, PDF_HEADER_SEARCH_BYTES) - PDF_SIGNATURE.size
    return (0..lastStart).any { start -> PDF_SIGNATURE.indices.all { bytes[start + it] == PDF_SIGNATURE[it] } }
}

internal fun fileLooksLikePdf(file: File): Boolean =
    runCatching { file.inputStream().use(::streamLooksLikePdf) }.getOrDefault(false)

/** Reads the start of an open file in place, without moving its position or taking ownership. */
internal fun ParcelFileDescriptor.looksLikePdf(): Boolean {
    val header = ByteArray(PDF_HEADER_SEARCH_BYTES)
    var length = 0
    while (length < header.size) {
        val read = Os.pread(fileDescriptor, header, length, header.size - length, length.toLong())
        if (read <= 0) break
        length += read
    }
    return bytesLookLikePdf(header, length)
}

/** Whether a stream starts like a PDF, leaving it at its start so it can still be read as a ZIP. */
internal fun BufferedInputStream.startsLikePdf(): Boolean {
    mark(PDF_HEADER_SEARCH_BYTES)
    try {
        return streamLooksLikePdf(this)
    } finally {
        reset()
    }
}

private fun streamLooksLikePdf(input: InputStream): Boolean {
    val header = ByteArray(PDF_HEADER_SEARCH_BYTES)
    var length = 0
    while (length < header.size) {
        val read = input.read(header, length, header.size - length)
        if (read <= 0) break
        length += read
    }
    return bytesLookLikePdf(header, length)
}

private fun ByteArray.startsWith(prefix: ByteArray, available: Int): Boolean =
    available >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
