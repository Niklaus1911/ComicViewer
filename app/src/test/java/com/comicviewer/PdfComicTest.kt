package com.comicviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfComicTest {
    @Test
    fun isPdfComicName_matchesPdfExtensionOnly() {
        assertTrue(isPdfComicName("comic.pdf"))
        assertTrue(isPdfComicName("Comic.PDF"))
        assertFalse(isPdfComicName("comic.cbz"))
        assertFalse(isPdfComicName("pdf"))

        assertTrue(isSupportedComicFileName("comic.pdf"))
        assertTrue(isSupportedComicFileName("comic.cbz"))
        assertFalse(isSupportedComicFileName("comic.rar"))
    }

    @Test
    fun bytesLookLikePdf_findsSignatureWithinFirstKilobyte() {
        assertTrue(bytesLookLikePdf("%PDF-1.7\n%âãÏÓ".toByteArray()))
        assertTrue(bytesLookLikePdf(ByteArray(100) { ' '.code.toByte() } + "%PDF-1.4".toByteArray()))

        assertFalse(bytesLookLikePdf(ByteArray(1_024) + "%PDF-1.4".toByteArray()))
        assertFalse(bytesLookLikePdf("%PDF".toByteArray()))
        assertFalse(bytesLookLikePdf(ByteArray(0)))
        // Only the first [length] bytes were read from the source.
        assertFalse(bytesLookLikePdf("%PDF-1.7".toByteArray(), length = 4))
    }

    @Test
    fun bytesLookLikePdf_rejectsZipArchivesStoringAPdf() {
        val zipHeader = byteArrayOf(0x50, 0x4B, 0x03, 0x04) + ByteArray(26) + "inner.pdf%PDF-1.7".toByteArray()
        val emptyZip = byteArrayOf(0x50, 0x4B, 0x05, 0x06) + "%PDF-".toByteArray()

        assertFalse(bytesLookLikePdf(zipHeader))
        assertFalse(bytesLookLikePdf(emptyZip))
    }

    @Test
    fun pdfRenderSize_fitsOrFillsTheRequestedBox() {
        // A US Letter page, 612 x 792 points, shown in a 1080 x 2160 viewport.
        assertEquals(PdfRenderSize(1080, 1398), pdfRenderSize(612, 792, requestedWidth = 1080, requestedHeight = 2160))
        assertEquals(PdfRenderSize(1669, 2160), pdfRenderSize(612, 792, 1080, 2160, fill = true))
        assertEquals(PdfRenderSize(300, 388), pdfRenderSize(612, 792, requestedWidth = 300, requestedHeight = null))
        assertEquals(PdfRenderSize(309, 400), pdfRenderSize(612, 792, requestedWidth = 0, requestedHeight = 400))
    }

    @Test
    fun pdfRenderSize_usesDefaultSizeWhenNothingIsRequested() {
        assertEquals(
            PdfRenderSize(1583, DEFAULT_PDF_RENDER_LONG_SIDE_PX),
            pdfRenderSize(612, 792, requestedWidth = null, requestedHeight = null),
        )
    }

    @Test
    fun pdfRenderSize_capsLongSideAndPixelCount() {
        val wide = pdfRenderSize(1_000, 100, requestedWidth = 20_000, requestedHeight = 2_000)
        assertEquals(MAX_PDF_RENDER_LONG_SIDE_PX, wide.width)

        val square = pdfRenderSize(500, 500, requestedWidth = 4_000, requestedHeight = 4_000)
        assertTrue(square.width.toLong() * square.height <= MAX_PDF_RENDER_PIXELS + square.width + square.height)
        assertTrue(square.width < 4_000)
    }

    @Test
    fun pdfRenderSize_keepsDegeneratePagesRenderable() {
        val size = pdfRenderSize(0, 0, requestedWidth = 100, requestedHeight = 100)
        assertTrue(size.width >= 1 && size.height >= 1)
    }
}
