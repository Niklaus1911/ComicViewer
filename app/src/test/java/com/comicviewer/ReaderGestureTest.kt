package com.comicviewer

import com.comicviewer.ui.reader.ReaderSwipeDirection
import com.comicviewer.ui.reader.readerHorizontalSwipeEnabled
import com.comicviewer.ui.reader.readerIncomingPageOffsetAfterCommit
import com.comicviewer.ui.reader.readerLogicalDirectionForPhysicalDirection
import com.comicviewer.ui.reader.readerNavigationDirectionForTap
import com.comicviewer.ui.reader.readerPhysicalSwipeCommitDirection
import com.comicviewer.ui.reader.readerSwipeCommitDirection
import com.comicviewer.ui.reader.readerSwipeDirectionForOffset
import com.comicviewer.ui.reader.readerSwipeStartOffset
import com.comicviewer.ui.reader.readerSwipeTargetIndex
import com.comicviewer.ui.reader.resistedReaderSwipeOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderGestureTest {
    @Test
    fun readerSwipeCommitDirection_commitsWhenDistancePassesThreshold() {
        assertEquals(
            ReaderSwipeDirection.Next,
            readerSwipeCommitDirection(
                offsetX = -150f,
                velocityX = 0f,
                viewportWidth = 1000f,
                currentPageIndex = 2,
                pageCount = 5,
                velocityThresholdPx = 700f,
            ),
        )
        assertEquals(
            ReaderSwipeDirection.Previous,
            readerSwipeCommitDirection(
                offsetX = 150f,
                velocityX = 0f,
                viewportWidth = 1000f,
                currentPageIndex = 2,
                pageCount = 5,
                velocityThresholdPx = 700f,
            ),
        )
    }

    @Test
    fun readerSwipeCommitDirection_commitsWhenVelocityPassesThreshold() {
        assertEquals(
            ReaderSwipeDirection.Next,
            readerSwipeCommitDirection(
                offsetX = -20f,
                velocityX = -720f,
                viewportWidth = 1000f,
                currentPageIndex = 2,
                pageCount = 5,
                velocityThresholdPx = 700f,
            ),
        )
        assertEquals(
            ReaderSwipeDirection.Previous,
            readerSwipeCommitDirection(
                offsetX = 20f,
                velocityX = 720f,
                viewportWidth = 1000f,
                currentPageIndex = 2,
                pageCount = 5,
                velocityThresholdPx = 700f,
            ),
        )
    }

    @Test
    fun readerSwipeCommitDirection_doesNotCommitOutOfBounds() {
        assertNull(
            readerSwipeCommitDirection(
                offsetX = 250f,
                velocityX = 900f,
                viewportWidth = 1000f,
                currentPageIndex = 0,
                pageCount = 5,
                velocityThresholdPx = 700f,
            ),
        )
        assertNull(
            readerSwipeCommitDirection(
                offsetX = -250f,
                velocityX = -900f,
                viewportWidth = 1000f,
                currentPageIndex = 4,
                pageCount = 5,
                velocityThresholdPx = 700f,
            ),
        )
    }

    @Test
    fun resistedReaderSwipeOffset_appliesResistanceOnlyAtEdges() {
        assertEquals(70f, resistedReaderSwipeOffset(250f, currentPageIndex = 0, pageCount = 5))
        assertEquals(-70f, resistedReaderSwipeOffset(-250f, currentPageIndex = 4, pageCount = 5))
        assertEquals(250f, resistedReaderSwipeOffset(250f, currentPageIndex = 2, pageCount = 5))
    }

    @Test
    fun resistedReaderSwipeOffset_continuesInterruptedSettleFromCurrentOffset() {
        val settleOffset = 650f
        val dragDelta = -200f

        assertEquals(
            450f,
            resistedReaderSwipeOffset(
                rawOffsetX = settleOffset + dragDelta,
                currentPageIndex = 2,
                pageCount = 5,
            ),
        )
    }

    @Test
    fun readerIncomingPageOffsetAfterCommit_keepsIncomingPageWithinViewport() {
        assertEquals(
            700f,
            readerIncomingPageOffsetAfterCommit(
                direction = ReaderSwipeDirection.Next,
                currentOffsetX = -300f,
                viewportWidth = 1000f,
            ),
        )
        assertEquals(
            -700f,
            readerIncomingPageOffsetAfterCommit(
                direction = ReaderSwipeDirection.Previous,
                currentOffsetX = 300f,
                viewportWidth = 1000f,
            ),
        )
        assertEquals(
            1000f,
            readerIncomingPageOffsetAfterCommit(
                direction = ReaderSwipeDirection.Next,
                currentOffsetX = 300f,
                viewportWidth = 1000f,
            ),
        )
    }

    @Test
    fun readerSwipeTargetIndex_returnsAdjacentPageOnlyWhenAvailable() {
        assertEquals(1, readerSwipeTargetIndex(2, pageCount = 5, ReaderSwipeDirection.Previous))
        assertEquals(3, readerSwipeTargetIndex(2, pageCount = 5, ReaderSwipeDirection.Next))
        assertNull(readerSwipeTargetIndex(0, pageCount = 5, ReaderSwipeDirection.Previous))
        assertNull(readerSwipeTargetIndex(4, pageCount = 5, ReaderSwipeDirection.Next))
    }

    @Test
    fun readerSwipeDirectionForOffset_mapsOffsetSignToPageDirection() {
        assertEquals(ReaderSwipeDirection.Previous, readerSwipeDirectionForOffset(10f))
        assertEquals(ReaderSwipeDirection.Next, readerSwipeDirectionForOffset(-10f))
        assertNull(readerSwipeDirectionForOffset(0f))
    }

    @Test
    fun readerLogicalDirectionForPhysicalDirection_reversesOnlyForRtl() {
        assertEquals(
            ReaderSwipeDirection.Previous,
            readerLogicalDirectionForPhysicalDirection(
                physicalDirection = ReaderSwipeDirection.Previous,
                readingDirection = ReaderReadingDirection.LeftToRight,
            ),
        )
        assertEquals(
            ReaderSwipeDirection.Next,
            readerLogicalDirectionForPhysicalDirection(
                physicalDirection = ReaderSwipeDirection.Previous,
                readingDirection = ReaderReadingDirection.RightToLeft,
            ),
        )
    }

    @Test
    fun readerNavigationDirectionForTap_usesReadingDirectionForOuterZones() {
        assertEquals(
            ReaderSwipeDirection.Previous,
            readerNavigationDirectionForTap(
                tapX = 100f,
                viewportWidth = 900f,
                readingDirection = ReaderReadingDirection.LeftToRight,
            ),
        )
        assertEquals(
            ReaderSwipeDirection.Next,
            readerNavigationDirectionForTap(
                tapX = 100f,
                viewportWidth = 900f,
                readingDirection = ReaderReadingDirection.RightToLeft,
            ),
        )
        assertNull(
            readerNavigationDirectionForTap(
                tapX = 450f,
                viewportWidth = 900f,
                readingDirection = ReaderReadingDirection.RightToLeft,
            ),
        )
    }

    @Test
    fun readerSwipeCommitDirection_usesRtlReadingDirectionForPhysicalSwipes() {
        assertEquals(
            ReaderSwipeDirection.Next,
            readerSwipeCommitDirection(
                offsetX = 150f,
                velocityX = 0f,
                viewportWidth = 1000f,
                currentPageIndex = 2,
                pageCount = 5,
                velocityThresholdPx = 700f,
                readingDirection = ReaderReadingDirection.RightToLeft,
            ),
        )
        assertEquals(
            ReaderSwipeDirection.Previous,
            readerSwipeCommitDirection(
                offsetX = -150f,
                velocityX = 0f,
                viewportWidth = 1000f,
                currentPageIndex = 2,
                pageCount = 5,
                velocityThresholdPx = 700f,
                readingDirection = ReaderReadingDirection.RightToLeft,
            ),
        )
    }

    @Test
    fun readerPhysicalSwipeCommitDirection_prefersVelocityOverOffsetForAnimationSide() {
        assertEquals(
            ReaderSwipeDirection.Next,
            readerPhysicalSwipeCommitDirection(
                offsetX = 80f,
                velocityX = -900f,
                velocityThresholdPx = 700f,
            ),
        )
        assertEquals(
            ReaderSwipeDirection.Previous,
            readerPhysicalSwipeCommitDirection(
                offsetX = -80f,
                velocityX = 900f,
                velocityThresholdPx = 700f,
            ),
        )
    }

    @Test
    fun readerIncomingPageOffsetAfterCommit_usesVelocityResolvedPhysicalDirection() {
        val physicalCommitDirection = readerPhysicalSwipeCommitDirection(
            offsetX = 80f,
            velocityX = -900f,
            velocityThresholdPx = 700f,
        )

        assertEquals(
            1000f,
            readerIncomingPageOffsetAfterCommit(
                direction = physicalCommitDirection!!,
                currentOffsetX = 80f,
                viewportWidth = 1000f,
            ),
        )
    }

    @Test
    fun readerHorizontalSwipeEnabled_allowsFitWidthButProtectsHorizontalPanning() {
        assertTrue(
            readerHorizontalSwipeEnabled(
                pageWidth = 1_000,
                pageHeight = 2_000,
                viewportWidth = 1_000,
                viewportHeight = 1_000,
                effectiveScale = 2f,
                userScale = 1f,
            ),
        )
        assertFalse(
            readerHorizontalSwipeEnabled(
                pageWidth = 2_000,
                pageHeight = 1_000,
                viewportWidth = 1_000,
                viewportHeight = 1_000,
                effectiveScale = 2f,
                userScale = 1f,
            ),
        )
    }

    @Test
    fun readerSwipeStartOffset_preservesCompatibleInterruptedSettleOffset() {
        assertEquals(
            320f,
            readerSwipeStartOffset(
                currentOffsetX = 320f,
                direction = ReaderSwipeDirection.Previous,
            ),
        )
        assertEquals(
            -320f,
            readerSwipeStartOffset(
                currentOffsetX = -320f,
                direction = ReaderSwipeDirection.Next,
            ),
        )
    }

    @Test
    fun readerSwipeStartOffset_snapsConflictingInterruptedSettleOffsetToCenter() {
        assertEquals(
            0f,
            readerSwipeStartOffset(
                currentOffsetX = 320f,
                direction = ReaderSwipeDirection.Next,
            ),
        )
        assertEquals(
            0f,
            readerSwipeStartOffset(
                currentOffsetX = -320f,
                direction = ReaderSwipeDirection.Previous,
            ),
        )
    }

    @Test
    fun twoPageSwipeCommitAndResistance_stopAtFinalSpread() {
        assertNull(
            readerSwipeCommitDirection(
                offsetX = -250f,
                velocityX = -900f,
                viewportWidth = 1_000f,
                currentPageIndex = 3,
                pageCount = 5,
                velocityThresholdPx = 700f,
                pageMode = ReaderPageMode.TwoPage,
            ),
        )
        assertEquals(
            -70f,
            resistedReaderSwipeOffset(
                rawOffsetX = -250f,
                currentPageIndex = 3,
                pageCount = 5,
                pageMode = ReaderPageMode.TwoPage,
            ),
        )
    }
}
