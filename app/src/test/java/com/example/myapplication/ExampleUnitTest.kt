package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun isSupportedArchiveName_acceptsZipAndCbzOnly() {
        assertTrue(isSupportedArchiveName("comic.zip"))
        assertTrue(isSupportedArchiveName("comic.CBZ"))

        assertFalse(isSupportedArchiveName("comic.rar"))
        assertFalse(isSupportedArchiveName("comic.pdf"))
        assertFalse(isSupportedArchiveName("comic"))
    }

    @Test
    fun detectImageFormat_acceptsImageBytesWithoutFileExtensions() {
        assertEquals(ImageFormat.JPEG, detectImageFormat(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())))
        assertEquals(
            ImageFormat.PNG,
            detectImageFormat(
                byteArrayOf(
                    0x89.toByte(),
                    0x50,
                    0x4E,
                    0x47,
                    0x0D,
                    0x0A,
                    0x1A,
                    0x0A,
                ),
            ),
        )
        assertEquals(
            ImageFormat.WEBP,
            detectImageFormat(
                byteArrayOf(
                    0x52,
                    0x49,
                    0x46,
                    0x46,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x57,
                    0x45,
                    0x42,
                    0x50,
                ),
            ),
        )
    }

    @Test
    fun detectImageFormat_rejectsNonImages() {
        assertNull(detectImageFormat(byteArrayOf()))
        assertNull(detectImageFormat("not an image".encodeToByteArray()))
    }

    @Test
    fun compareComicPageNames_sortsNumberedPagesNaturally() {
        val pages = listOf(
            "010.jpg",
            "002.jpg",
            "001.jpg",
            "page-11.png",
            "page-3.png",
        )

        assertEquals(
            listOf("001.jpg", "002.jpg", "010.jpg", "page-3.png", "page-11.png"),
            sortComicPaths(pages),
        )
    }

    @Test
    fun sortComicPaths_sortsArbitraryArchiveNamesNaturally() {
        val pages = listOf(
            "Chapter 2/scan",
            "Chapter 10/scan",
            "Chapter 1/pageB",
            "Chapter 1/pageA",
        )

        assertEquals(
            listOf("Chapter 1/pageA", "Chapter 1/pageB", "Chapter 2/scan", "Chapter 10/scan"),
            sortComicPaths(pages),
        )
    }

    @Test
    fun isHiddenComicPath_skipsHiddenAndMacOsMetadata() {
        assertTrue(isHiddenComicPath("__MACOSX/001"))
        assertTrue(isHiddenComicPath(".hidden/001"))
        assertTrue(isHiddenComicPath("pages/.DS_Store"))

        assertFalse(isHiddenComicPath("pages/001"))
        assertFalse(isHiddenComicPath("chapter 1/page"))
    }

    @Test
    fun coercePageIndex_clampsToAvailablePages() {
        assertEquals(0, coercePageIndex(-1, pageCount = 5))
        assertEquals(3, coercePageIndex(3, pageCount = 5))
        assertEquals(4, coercePageIndex(20, pageCount = 5))
        assertEquals(0, coercePageIndex(20, pageCount = 0))
    }

    @Test
    fun comicLoadErrorForPageCount_reportsEmptyComic() {
        assertEquals(EMPTY_COMIC_MESSAGE, comicLoadErrorForPageCount(0))
        assertNull(comicLoadErrorForPageCount(1))
    }
}
