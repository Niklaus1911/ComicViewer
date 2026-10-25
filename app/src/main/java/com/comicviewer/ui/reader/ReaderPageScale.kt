package com.comicviewer.ui.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import com.comicviewer.ReaderPageFitMode
import com.comicviewer.ReaderReadingDirection
import kotlin.math.max
import kotlin.math.roundToInt

internal const val MIN_PAGE_SCALE = 1f
internal const val MAX_PAGE_SCALE = 5f

internal fun readerFitWidthScaleMultiplier(
    pageWidth: Int,
    pageHeight: Int,
    viewportWidth: Int,
    viewportHeight: Int,
): Float {
    if (pageWidth <= 0 || pageHeight <= 0 || viewportWidth <= 0 || viewportHeight <= 0) return 1f
    val fitScreenSize = fitPageSizeInViewport(
        pageWidth = pageWidth,
        pageHeight = pageHeight,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
    )
    if (fitScreenSize.width <= 0f) return 1f
    return (viewportWidth.toFloat() / fitScreenSize.width).coerceAtLeast(1f)
}

internal fun readerFitHeightScaleMultiplier(
    pageWidth: Int,
    pageHeight: Int,
    viewportWidth: Int,
    viewportHeight: Int,
): Float {
    if (pageWidth <= 0 || pageHeight <= 0 || viewportWidth <= 0 || viewportHeight <= 0) return 1f
    val fitScreenSize = fitPageSizeInViewport(
        pageWidth = pageWidth,
        pageHeight = pageHeight,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
    )
    if (fitScreenSize.height <= 0f) return 1f
    return (viewportHeight.toFloat() / fitScreenSize.height).coerceAtLeast(1f)
}

internal fun readerOriginalScaleMultiplier(
    pageWidth: Int,
    pageHeight: Int,
    viewportWidth: Int,
    viewportHeight: Int,
): Float {
    if (pageWidth <= 0 || pageHeight <= 0 || viewportWidth <= 0 || viewportHeight <= 0) return 1f
    val fitScreenSize = fitPageSizeInViewport(
        pageWidth = pageWidth,
        pageHeight = pageHeight,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
    )
    if (fitScreenSize.width <= 0f || fitScreenSize.height <= 0f) return 1f
    return minOf(
        pageWidth.toFloat() / fitScreenSize.width,
        pageHeight.toFloat() / fitScreenSize.height,
    ).coerceAtLeast(1f)
}

internal fun readerEffectivePageScale(
    pageFitMode: ReaderPageFitMode,
    pageWidth: Int,
    pageHeight: Int,
    viewportWidth: Int,
    viewportHeight: Int,
    userScale: Float,
): Float {
    val baseScale = when (pageFitMode) {
        ReaderPageFitMode.FitScreen -> 1f
        ReaderPageFitMode.FitWidth -> readerFitWidthScaleMultiplier(
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
        )
        ReaderPageFitMode.FitHeight -> readerFitHeightScaleMultiplier(
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
        )
        ReaderPageFitMode.Original -> readerOriginalScaleMultiplier(
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
        )
    }
    return baseScale * userScale.coerceIn(MIN_PAGE_SCALE, MAX_PAGE_SCALE)
}

internal fun readerHorizontalSwipeEnabled(
    pageWidth: Int,
    pageHeight: Int,
    viewportWidth: Int,
    viewportHeight: Int,
    effectiveScale: Float,
    userScale: Float,
): Boolean {
    if (viewportWidth <= 0 || viewportHeight <= 0 || userScale > MIN_PAGE_SCALE) return false
    val fitSize = fitPageSizeInViewport(
        pageWidth = pageWidth,
        pageHeight = pageHeight,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
    )
    return fitSize.width * effectiveScale <= viewportWidth + 0.5f
}

internal fun readerResolvedPageDimension(
    storedDimension: Int,
    painterIntrinsicDimension: Float,
): Int {
    if (storedDimension > 0) return storedDimension
    if (!painterIntrinsicDimension.isFinite() || painterIntrinsicDimension <= 0f) return 0
    return painterIntrinsicDimension
        .coerceAtMost(Int.MAX_VALUE.toFloat())
        .roundToInt()
        .coerceAtLeast(1)
}

internal fun Offset.clampedFor(
    pageWidth: Int,
    pageHeight: Int,
    scale: Float,
    viewportSize: IntSize,
): Offset {
    if (viewportSize.width <= 0 || viewportSize.height <= 0) return Offset.Zero

    val fitSize = fitPageSizeInViewport(
        pageWidth = pageWidth,
        pageHeight = pageHeight,
        viewportWidth = viewportSize.width,
        viewportHeight = viewportSize.height,
    )
    val maxX = max(0f, (fitSize.width * scale - viewportSize.width) / 2f)
    val maxY = max(0f, (fitSize.height * scale - viewportSize.height) / 2f)

    return Offset(
        x = x.coerceIn(-maxX, maxX),
        y = y.coerceIn(-maxY, maxY),
    )
}

/**
 * Where a page larger than the viewport starts: at its top edge, and at its left edge (right edge
 * when reading right to left), so the first panel is in view rather than the middle of the page.
 */
internal fun readerPageStartOffset(
    pageWidth: Int,
    pageHeight: Int,
    scale: Float,
    viewportSize: IntSize,
    readingDirection: ReaderReadingDirection,
): Offset {
    if (viewportSize.width <= 0 || viewportSize.height <= 0) return Offset.Zero
    val fitSize = fitPageSizeInViewport(
        pageWidth = pageWidth,
        pageHeight = pageHeight,
        viewportWidth = viewportSize.width,
        viewportHeight = viewportSize.height,
    )
    val maxX = max(0f, (fitSize.width * scale - viewportSize.width) / 2f)
    val maxY = max(0f, (fitSize.height * scale - viewportSize.height) / 2f)
    return Offset(
        x = if (readingDirection == ReaderReadingDirection.RightToLeft) -maxX else maxX,
        y = maxY,
    )
}

internal fun fitPageSizeInViewport(
    pageWidth: Int,
    pageHeight: Int,
    viewportWidth: Int,
    viewportHeight: Int,
): Size {
    val viewportWidthFloat = viewportWidth.toFloat()
    val viewportHeightFloat = viewportHeight.toFloat()
    if (viewportWidthFloat <= 0f || viewportHeightFloat <= 0f || pageWidth <= 0 || pageHeight <= 0) {
        return Size(viewportWidthFloat, viewportHeightFloat)
    }

    val imageAspectRatio = pageWidth.toFloat() / pageHeight.toFloat()
    val viewportAspectRatio = viewportWidthFloat / viewportHeightFloat
    return if (imageAspectRatio > viewportAspectRatio) {
        Size(width = viewportWidthFloat, height = viewportWidthFloat / imageAspectRatio)
    } else {
        Size(width = viewportHeightFloat * imageAspectRatio, height = viewportHeightFloat)
    }
}
