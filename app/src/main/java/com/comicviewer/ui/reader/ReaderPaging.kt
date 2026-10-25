package com.comicviewer.ui.reader

import com.comicviewer.ReaderPageMode
import com.comicviewer.ReaderReadingDirection
import com.comicviewer.coercePageIndex
import kotlin.math.abs

internal const val READER_SWIPE_DISTANCE_FRACTION = 0.14f
private const val EDGE_SWIPE_RESISTANCE = 0.28f

internal enum class ReaderSwipeDirection {
    Previous,
    Next,
}

internal fun readerSpreadStartIndex(pageIndex: Int, pageCount: Int): Int {
    val clamped = coercePageIndex(pageIndex, pageCount)
    if (clamped == 0 || pageCount <= 1) return 0
    return 1 + ((clamped - 1) / 2) * 2
}

internal fun readerSpreadPageIndices(pageIndex: Int, pageCount: Int): List<Int> {
    if (pageCount <= 0) return emptyList()
    val start = readerSpreadStartIndex(pageIndex, pageCount)
    if (start == 0) return listOf(0)
    return listOfNotNull(start, (start + 1).takeIf { it < pageCount })
}

internal fun readerSpreadTargetIndex(
    currentPageIndex: Int,
    pageCount: Int,
    direction: ReaderSwipeDirection,
): Int? {
    if (pageCount <= 0) return null
    val start = readerSpreadStartIndex(currentPageIndex, pageCount)
    return when (direction) {
        ReaderSwipeDirection.Previous -> when {
            start == 0 -> null
            start == 1 -> 0
            else -> (start - 2).coerceAtLeast(1)
        }
        ReaderSwipeDirection.Next -> when {
            start == 0 && pageCount > 1 -> 1
            start == 0 -> null
            start + 2 < pageCount -> start + 2
            else -> null
        }
    }
}

internal fun readerTargetPageIndex(
    currentPageIndex: Int,
    pageCount: Int,
    direction: ReaderSwipeDirection,
    pageMode: ReaderPageMode,
): Int? = when (pageMode) {
    ReaderPageMode.SinglePage,
    ReaderPageMode.Vertical,
    -> readerSwipeTargetIndex(currentPageIndex, pageCount, direction)
    ReaderPageMode.TwoPage -> readerSpreadTargetIndex(currentPageIndex, pageCount, direction)
}

internal fun readerReportedPageIndex(
    visualPageIndex: Int,
    pageCount: Int,
    pageMode: ReaderPageMode,
): Int = when (pageMode) {
    ReaderPageMode.SinglePage,
    ReaderPageMode.Vertical,
    -> coercePageIndex(visualPageIndex, pageCount)
    ReaderPageMode.TwoPage -> readerSpreadPageIndices(visualPageIndex, pageCount)
        .lastOrNull()
        ?: coercePageIndex(visualPageIndex, pageCount)
}

internal fun readerSwipeTargetIndex(
    currentPageIndex: Int,
    pageCount: Int,
    direction: ReaderSwipeDirection,
): Int? = when (direction) {
    ReaderSwipeDirection.Previous -> (currentPageIndex - 1).takeIf { currentPageIndex > 0 }
    ReaderSwipeDirection.Next -> (currentPageIndex + 1).takeIf { currentPageIndex < pageCount - 1 }
}

internal fun readerLogicalDirectionForPhysicalDirection(
    physicalDirection: ReaderSwipeDirection,
    readingDirection: ReaderReadingDirection,
): ReaderSwipeDirection = when (readingDirection) {
    ReaderReadingDirection.LeftToRight -> physicalDirection
    ReaderReadingDirection.RightToLeft -> when (physicalDirection) {
        ReaderSwipeDirection.Previous -> ReaderSwipeDirection.Next
        ReaderSwipeDirection.Next -> ReaderSwipeDirection.Previous
    }
}

internal fun readerNavigationDirectionForTap(
    tapX: Float,
    viewportWidth: Float,
    readingDirection: ReaderReadingDirection,
): ReaderSwipeDirection? {
    if (viewportWidth <= 0f) return null
    val tapZoneWidth = viewportWidth / 3f
    val physicalDirection = when {
        tapX < tapZoneWidth -> ReaderSwipeDirection.Previous
        tapX > tapZoneWidth * 2f -> ReaderSwipeDirection.Next
        else -> return null
    }
    return readerLogicalDirectionForPhysicalDirection(physicalDirection, readingDirection)
}

internal fun readerPhysicalSwipeCommitDirection(
    offsetX: Float,
    velocityX: Float,
    velocityThresholdPx: Float,
): ReaderSwipeDirection? {
    val velocityDirection = when {
        velocityX >= velocityThresholdPx -> ReaderSwipeDirection.Previous
        velocityX <= -velocityThresholdPx -> ReaderSwipeDirection.Next
        else -> null
    }
    val offsetDirection = when {
        offsetX > 0f -> ReaderSwipeDirection.Previous
        offsetX < 0f -> ReaderSwipeDirection.Next
        else -> null
    }
    return velocityDirection ?: offsetDirection
}

internal fun readerSwipeCommitDirection(
    offsetX: Float,
    velocityX: Float,
    viewportWidth: Float,
    currentPageIndex: Int,
    pageCount: Int,
    distanceFraction: Float = READER_SWIPE_DISTANCE_FRACTION,
    velocityThresholdPx: Float,
    readingDirection: ReaderReadingDirection = ReaderReadingDirection.LeftToRight,
    pageMode: ReaderPageMode = ReaderPageMode.SinglePage,
): ReaderSwipeDirection? {
    val physicalVelocityDirection = readerPhysicalSwipeCommitDirection(
        offsetX = 0f,
        velocityX = velocityX,
        velocityThresholdPx = velocityThresholdPx,
    )
    val physicalDirection = readerPhysicalSwipeCommitDirection(
        offsetX = offsetX,
        velocityX = velocityX,
        velocityThresholdPx = velocityThresholdPx,
    ) ?: return null
    val direction = readerLogicalDirectionForPhysicalDirection(physicalDirection, readingDirection)
    val targetIndex = readerTargetPageIndex(
        currentPageIndex = currentPageIndex,
        pageCount = pageCount,
        direction = direction,
        pageMode = pageMode,
    ) ?: return null
    val distanceThreshold = viewportWidth * distanceFraction
    val velocityCommits = physicalVelocityDirection != null
    val distanceCommits = abs(offsetX) >= distanceThreshold

    return if (targetIndex in 0 until pageCount && (velocityCommits || distanceCommits)) direction else null
}

internal fun resistedReaderSwipeOffset(
    rawOffsetX: Float,
    currentPageIndex: Int,
    pageCount: Int,
    edgeResistance: Float = EDGE_SWIPE_RESISTANCE,
    readingDirection: ReaderReadingDirection = ReaderReadingDirection.LeftToRight,
    pageMode: ReaderPageMode = ReaderPageMode.SinglePage,
): Float = when {
    rawOffsetX > 0f &&
        readerTargetPageIndex(
            currentPageIndex = currentPageIndex,
            pageCount = pageCount,
            direction = readerLogicalDirectionForPhysicalDirection(ReaderSwipeDirection.Previous, readingDirection),
            pageMode = pageMode,
        ) == null ->
        rawOffsetX * edgeResistance

    rawOffsetX < 0f &&
        readerTargetPageIndex(
            currentPageIndex = currentPageIndex,
            pageCount = pageCount,
            direction = readerLogicalDirectionForPhysicalDirection(ReaderSwipeDirection.Next, readingDirection),
            pageMode = pageMode,
        ) == null ->
        rawOffsetX * edgeResistance

    else -> rawOffsetX
}

internal fun readerIncomingPageOffsetAfterCommit(
    direction: ReaderSwipeDirection,
    currentOffsetX: Float,
    viewportWidth: Float,
): Float {
    if (viewportWidth <= 0f) return 0f
    return when (direction) {
        ReaderSwipeDirection.Previous -> (currentOffsetX - viewportWidth).coerceIn(-viewportWidth, 0f)
        ReaderSwipeDirection.Next -> (currentOffsetX + viewportWidth).coerceIn(0f, viewportWidth)
    }
}

internal fun readerSwipeDirectionForOffset(offsetX: Float): ReaderSwipeDirection? = when {
    offsetX > 0f -> ReaderSwipeDirection.Previous
    offsetX < 0f -> ReaderSwipeDirection.Next
    else -> null
}

internal fun readerSwipeStartOffset(
    currentOffsetX: Float,
    direction: ReaderSwipeDirection,
): Float =
    if (readerSwipeDirectionForOffset(currentOffsetX) == direction) currentOffsetX else 0f
