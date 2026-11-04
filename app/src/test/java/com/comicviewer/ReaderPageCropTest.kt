package com.comicviewer

import com.comicviewer.ui.reader.PageContentBounds
import com.comicviewer.ui.reader.detectPageContentBounds
import com.comicviewer.ui.reader.readerPageMemoryCacheKey
import com.comicviewer.ui.reader.scaledToPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderPageCropTest {
    @Test
    fun detectPageContentBounds_trimsWhiteMarginsAroundArt() {
        val page = testPage(width = 100, height = 150, background = WHITE)
        page.fillArt(left = 10, top = 15, right = 90, bottom = 135)

        assertEquals(PageContentBounds(10, 15, 90, 135), page.detect())
    }

    @Test
    fun detectPageContentBounds_trimsYellowedPaperAndBlackBorders() {
        val paper = testPage(width = 100, height = 150, background = 0xFFF0E6C8.toInt())
        paper.fillArt(left = 5, top = 8, right = 95, bottom = 140)
        val black = testPage(width = 100, height = 150, background = BLACK)
        black.fillArt(left = 0, top = 12, right = 100, bottom = 138)

        assertEquals(PageContentBounds(5, 8, 95, 140), paper.detect())
        assertEquals(PageContentBounds(0, 12, 100, 138), black.detect())
    }

    @Test
    fun detectPageContentBounds_leavesFullBleedAndBlankPagesAlone() {
        val fullBleed = testPage(width = 100, height = 150, background = WHITE)
        fullBleed.fillArt(left = 0, top = 0, right = 100, bottom = 150)
        val blank = testPage(width = 100, height = 150, background = WHITE)

        assertNull(fullBleed.detect())
        assertNull(blank.detect())
    }

    @Test
    fun detectPageContentBounds_leavesColoredEdgesAlone() {
        val page = testPage(width = 100, height = 150, background = 0xFF3366CC.toInt())
        page.fillArt(left = 10, top = 15, right = 90, bottom = 135)

        assertNull(page.detect())
    }

    @Test
    fun detectPageContentBounds_trimsScannerEdgeThenPaperMargin() {
        val page = testPage(width = 100, height = 150, background = WHITE)
        page.fill(left = 0, top = 0, right = 3, bottom = 150, color = BLACK)
        page.fillArt(left = 10, top = 15, right = 90, bottom = 135)

        assertEquals(PageContentBounds(10, 15, 90, 135), page.detect())
    }

    @Test
    fun detectPageContentBounds_keepsArtFrameInsidePaperMargin() {
        val page = testPage(width = 100, height = 150, background = WHITE)
        page.fill(left = 0, top = 10, right = 100, bottom = 12, color = BLACK)
        page.fillArt(left = 10, top = 12, right = 90, bottom = 135)

        assertEquals(PageContentBounds(10, 10, 90, 135), page.detect())
    }

    @Test
    fun detectPageContentBounds_ignoresSpecksInMargins() {
        val page = testPage(width = 100, height = 150, background = WHITE)
        page.fillArt(left = 10, top = 15, right = 90, bottom = 135)
        page.fill(left = 40, top = 4, right = 41, bottom = 5, color = BLACK)
        page.fill(left = 3, top = 70, right = 4, bottom = 71, color = BLACK)

        assertEquals(PageContentBounds(10, 15, 90, 135), page.detect())
    }

    @Test
    fun detectPageContentBounds_keepsSmallDrawingsAtTheirSize() {
        val page = testPage(width = 100, height = 100, background = WHITE)
        page.fillArt(left = 40, top = 40, right = 60, bottom = 60)

        assertNull(page.detect())
    }

    @Test
    fun detectPageContentBounds_cropsOnlyTheAxisWithBorders() {
        val page = testPage(width = 100, height = 150, background = WHITE)
        page.fillArt(left = 30, top = 10, right = 70, bottom = 140)

        assertEquals(PageContentBounds(0, 10, 100, 140), page.detect())
    }

    @Test
    fun scaledToPage_roundsOutwardAndKeepsASmallMargin() {
        val sampleBounds = PageContentBounds(10, 15, 90, 135)

        assertEquals(
            PageContentBounds(95, 142, 905, 1358),
            sampleBounds.scaledToPage(sampleWidth = 100, sampleHeight = 150, pageWidth = 1000, pageHeight = 1500),
        )
        assertEquals(
            PageContentBounds(0, 142, 1000, 1500),
            PageContentBounds(0, 15, 100, 150).scaledToPage(100, 150, 1000, 1500),
        )
    }

    @Test
    fun scaledToPage_skipsCropsTooSmallToMatter() {
        assertNull(PageContentBounds(0, 1, 100, 150).scaledToPage(100, 150, 1000, 1500))
    }

    @Test
    fun readerPageMemoryCacheKey_keepsCroppedPagesApart() {
        assertEquals("page", readerPageMemoryCacheKey("page", autoCrop = false))
        assertNotEquals("page", readerPageMemoryCacheKey("page", autoCrop = true))
    }

    private class TestPage(val width: Int, val height: Int, val pixels: IntArray) {
        fun fill(left: Int, top: Int, right: Int, bottom: Int, color: Int) {
            for (y in top until bottom) for (x in left until right) pixels[y * width + x] = color
        }

        /** Busy two-color art, which is never a plain border. */
        fun fillArt(left: Int, top: Int, right: Int, bottom: Int) {
            for (y in top until bottom) {
                for (x in left until right) pixels[y * width + x] = if ((x + y) % 2 == 0) RED else BLUE
            }
        }

        fun detect(): PageContentBounds? = detectPageContentBounds(pixels, width, height)
    }

    private fun testPage(width: Int, height: Int, background: Int) =
        TestPage(width, height, IntArray(width * height) { background })

    private companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
        const val BLACK = 0xFF000000.toInt()
        const val RED = 0xFFCC2222.toInt()
        const val BLUE = 0xFF2244CC.toInt()
    }
}
