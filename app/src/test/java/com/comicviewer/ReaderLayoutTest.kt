package com.comicviewer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.comicviewer.ui.reader.MAX_PAGE_DETAIL_STEP
import com.comicviewer.ui.reader.ReaderSwipeDirection
import com.comicviewer.ui.reader.readerEffectivePageScale
import com.comicviewer.ui.reader.readerFitWidthScaleMultiplier
import com.comicviewer.ui.reader.readerPageDetailStep
import com.comicviewer.ui.reader.readerPageGridInitialItemIndex
import com.comicviewer.ui.reader.readerPageStartOffset
import com.comicviewer.ui.reader.readerProgressPercent
import com.comicviewer.ui.reader.readerReportedPageIndex
import com.comicviewer.ui.reader.readerResolvedPageDimension
import com.comicviewer.ui.reader.readerSpreadPageIndices
import com.comicviewer.ui.reader.readerSpreadStartIndex
import com.comicviewer.ui.reader.readerSpreadTargetIndex
import com.comicviewer.ui.reader.readerTargetPageIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderLayoutTest {
    @Test
    fun readerPageDetailStep_roundsZoomUpToWholeStepsWithinBounds() {
        assertEquals(1, readerPageDetailStep(1f))
        assertEquals(1, readerPageDetailStep(1.05f))
        assertEquals(2, readerPageDetailStep(1.5f))
        assertEquals(2, readerPageDetailStep(2f))
        assertEquals(3, readerPageDetailStep(2.6f))
        assertEquals(MAX_PAGE_DETAIL_STEP, readerPageDetailStep(5f))
        assertEquals(1, readerPageDetailStep(Float.NaN))
    }

    @Test
    fun readerPageStartOffset_startsLargePagesAtTheirTopAndReadingEdge() {
        val viewport = IntSize(1000, 1000)
        // A square page fits the viewport, then is shown at twice that size.
        val leftToRight = readerPageStartOffset(1000, 1000, 2f, viewport, ReaderReadingDirection.LeftToRight)
        val rightToLeft = readerPageStartOffset(1000, 1000, 2f, viewport, ReaderReadingDirection.RightToLeft)

        assertEquals(Offset(500f, 500f), leftToRight)
        assertEquals(Offset(-500f, 500f), rightToLeft)
        assertEquals(Offset.Zero, readerPageStartOffset(1000, 1000, 1f, viewport, ReaderReadingDirection.LeftToRight))
        assertEquals(Offset.Zero, readerPageStartOffset(1000, 1000, 2f, IntSize.Zero, ReaderReadingDirection.LeftToRight))
    }

    @Test
    fun readerFitWidthScaleMultiplier_scalesTallPagesToViewportWidth() {
        assertEquals(
            2f,
            readerFitWidthScaleMultiplier(
                pageWidth = 1000,
                pageHeight = 2000,
                viewportWidth = 1000,
                viewportHeight = 1000,
            ),
            0.0001f,
        )
        assertEquals(
            1f,
            readerFitWidthScaleMultiplier(
                pageWidth = 0,
                pageHeight = 0,
                viewportWidth = 1000,
                viewportHeight = 1000,
            ),
            0.0001f,
        )
    }

    @Test
    fun readerEffectivePageScale_combinesFitWidthBaseScaleAndUserScale() {
        assertEquals(
            1f,
            readerEffectivePageScale(
                pageFitMode = ReaderPageFitMode.FitScreen,
                pageWidth = 1000,
                pageHeight = 2000,
                viewportWidth = 1000,
                viewportHeight = 1000,
                userScale = 1f,
            ),
            0.0001f,
        )
        assertEquals(
            3f,
            readerEffectivePageScale(
                pageFitMode = ReaderPageFitMode.FitWidth,
                pageWidth = 1000,
                pageHeight = 2000,
                viewportWidth = 1000,
                viewportHeight = 1000,
                userScale = 1.5f,
            ),
            0.0001f,
        )
    }

    @Test
    fun readerResolvedPageDimension_usesPainterBoundsForDocumentPages() {
        assertEquals(1_200, readerResolvedPageDimension(storedDimension = 1_200, painterIntrinsicDimension = 900f))
        assertEquals(900, readerResolvedPageDimension(storedDimension = 0, painterIntrinsicDimension = 900.4f))
        assertEquals(0, readerResolvedPageDimension(storedDimension = 0, painterIntrinsicDimension = Float.NaN))
        assertEquals(0, readerResolvedPageDimension(storedDimension = 0, painterIntrinsicDimension = Float.POSITIVE_INFINITY))
    }

    @Test
    fun readerPageIndexHelpers_treatVerticalScrollLikeSinglePages() {
        assertEquals(4, readerReportedPageIndex(visualPageIndex = 4, pageCount = 6, pageMode = ReaderPageMode.Vertical))
        assertEquals(
            5,
            readerTargetPageIndex(
                currentPageIndex = 4,
                pageCount = 6,
                direction = ReaderSwipeDirection.Next,
                pageMode = ReaderPageMode.Vertical,
            ),
        )
        assertEquals(0, readerProgressPercent(progressPageIndex = 0, pageCount = 0))
        assertEquals(50, readerProgressPercent(progressPageIndex = 2, pageCount = 6))
    }

    @Test
    fun readerSpreadPairing_keepsCoverAloneAndPairsFollowingPages() {
        assertEquals(listOf(0), readerSpreadPageIndices(pageIndex = 0, pageCount = 6))
        assertEquals(listOf(1, 2), readerSpreadPageIndices(pageIndex = 1, pageCount = 6))
        assertEquals(listOf(1, 2), readerSpreadPageIndices(pageIndex = 2, pageCount = 6))
        assertEquals(listOf(3, 4), readerSpreadPageIndices(pageIndex = 4, pageCount = 6))
        assertEquals(listOf(5), readerSpreadPageIndices(pageIndex = 5, pageCount = 6))
        assertEquals(1, readerSpreadStartIndex(pageIndex = 2, pageCount = 6))
        assertEquals(5, readerSpreadStartIndex(pageIndex = 5, pageCount = 6))
    }

    @Test
    fun readerPageGridInitialItemIndex_resolvesCurrentPageAndSafeFallbacks() {
        assertEquals(
            2,
            readerPageGridInitialItemIndex(
                pageIndices = listOf(0, 1, 2, 3),
                currentPageIndex = 2,
            ),
        )
        assertEquals(
            1,
            readerPageGridInitialItemIndex(
                pageIndices = listOf(10, 30, 50),
                currentPageIndex = 30,
            ),
        )
        assertEquals(
            1,
            readerPageGridInitialItemIndex(
                pageIndices = listOf(10, 30, 50),
                currentPageIndex = 1,
            ),
        )
        assertEquals(
            0,
            readerPageGridInitialItemIndex(
                pageIndices = listOf(10, 30, 50),
                currentPageIndex = -1,
            ),
        )
        assertEquals(
            2,
            readerPageGridInitialItemIndex(
                pageIndices = listOf(10, 30, 50),
                currentPageIndex = 99,
            ),
        )
        assertEquals(
            0,
            readerPageGridInitialItemIndex(
                pageIndices = emptyList(),
                currentPageIndex = 99,
            ),
        )
    }

    @Test
    fun readerReportedPageIndex_reportsLastVisiblePageForSpreads() {
        assertEquals(
            0,
            readerReportedPageIndex(visualPageIndex = 0, pageCount = 6, pageMode = ReaderPageMode.TwoPage),
        )
        assertEquals(
            2,
            readerReportedPageIndex(visualPageIndex = 1, pageCount = 6, pageMode = ReaderPageMode.TwoPage),
        )
        assertEquals(
            5,
            readerReportedPageIndex(visualPageIndex = 5, pageCount = 6, pageMode = ReaderPageMode.TwoPage),
        )
    }

    @Test
    fun readerSpreadTargetIndex_movesBySpreadAndHandlesOddLastPage() {
        assertNull(readerSpreadTargetIndex(0, 6, ReaderSwipeDirection.Previous))
        assertEquals(1, readerSpreadTargetIndex(0, 6, ReaderSwipeDirection.Next))
        assertEquals(0, readerSpreadTargetIndex(1, 6, ReaderSwipeDirection.Previous))
        assertEquals(3, readerSpreadTargetIndex(1, 6, ReaderSwipeDirection.Next))
        assertEquals(5, readerSpreadTargetIndex(3, 6, ReaderSwipeDirection.Next))
        assertEquals(3, readerSpreadTargetIndex(5, 6, ReaderSwipeDirection.Previous))
        assertNull(readerSpreadTargetIndex(5, 6, ReaderSwipeDirection.Next))
    }
}
