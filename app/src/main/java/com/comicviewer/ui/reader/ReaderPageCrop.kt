package com.comicviewer.ui.reader

import android.graphics.Bitmap
import android.os.Build
import androidx.core.graphics.scale
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.size.Size
import coil3.transform.Transformation
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/** Longest side of the copy that borders are looked for in; a fixed size finds the same crop at any detail step. */
private const val CROP_SAMPLE_SIZE = 400
private const val MIN_CROP_INPUT_SIZE = 16

/** How far, per color channel, a pixel may be from the border color and still count as border. */
private const val BORDER_COLOR_TOLERANCE = 40

/** Share of a border line that may differ from the border color, for specks and scan dust. */
private const val BORDER_NOISE_FRACTION = 0.02f

/** Border colors are near white (paper, yellowed paper) or near black (scanner edges, black frames). */
private const val MIN_LIGHT_BORDER_LUMA = 170
private const val MAX_DARK_BORDER_LUMA = 70
private const val MAX_BORDER_CHANNEL_SPREAD = 48

/**
 * Each axis keeps at least this share of the page. More than that is not a border: a blank page,
 * a mostly white illustration, or a single small panel is shown whole rather than blown up.
 */
private const val MIN_CONTENT_FRACTION = 0.6f

/** The art keeps a sliver of its border, so its outer lines and antialiased edges are not cut. */
private const val CONTENT_MARGIN_FRACTION = 0.005f

private const val MAX_BORDER_PASSES = 3

/** Crops smaller than this share of the page on both axes are skipped, saving a bitmap copy. */
private const val MIN_USEFUL_CROP_FRACTION = 0.01f

/** The part of a page inside its borders; [right] and [bottom] are exclusive. */
internal data class PageContentBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Trims plain white or black borders from reader pages. Pages whose edges hold art are left as
 * they are, and so is any side that is not a plain border.
 */
internal object PageBorderCropTransformation : Transformation() {
    override val cacheKey: String = "page-border-crop-v1"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val bounds = input.pageContentBounds() ?: return input
        return Bitmap.createBitmap(input, bounds.left, bounds.top, bounds.width, bounds.height)
    }
}

/** Adds the border crop to a page request when [enabled]. */
internal fun ImageRequest.Builder.cropPageBorders(enabled: Boolean): ImageRequest.Builder =
    if (enabled) transformations(PageBorderCropTransformation) else this

/** A cropped page is a different image, so it is kept in the memory cache under its own key. */
internal fun readerPageMemoryCacheKey(baseKey: String, autoCrop: Boolean): String =
    if (autoCrop) "$baseKey|${PageBorderCropTransformation.cacheKey}" else baseKey

private fun Bitmap.pageContentBounds(): PageContentBounds? {
    if (width < MIN_CROP_INPUT_SIZE || height < MIN_CROP_INPUT_SIZE) return null
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && config == Bitmap.Config.HARDWARE) return null
    val sampleScale = (CROP_SAMPLE_SIZE.toFloat() / max(width, height)).coerceAtMost(1f)
    val sampleWidth = (width * sampleScale).roundToInt().coerceAtLeast(1)
    val sampleHeight = (height * sampleScale).roundToInt().coerceAtLeast(1)
    // Filtered downscaling averages away scan grain, so border lines read as one plain color.
    val sample = if (sampleScale < 1f) scale(sampleWidth, sampleHeight) else this
    val pixels = IntArray(sampleWidth * sampleHeight)
    try {
        sample.getPixels(pixels, 0, sampleWidth, 0, 0, sampleWidth, sampleHeight)
    } finally {
        if (sample !== this) sample.recycle()
    }
    return detectPageContentBounds(pixels, sampleWidth, sampleHeight)
        ?.scaledToPage(sampleWidth, sampleHeight, width, height)
}

/**
 * Finds the content of a page given as ARGB [pixels] in rows, by walking in from each side while
 * the lines are a plain near-white or near-black color. Returns null when there is nothing to trim.
 */
internal fun detectPageContentBounds(pixels: IntArray, width: Int, height: Int): PageContentBounds? {
    if (width <= 0 || height <= 0 || pixels.size < width * height) return null
    var top = 0
    var bottom = 0
    var left = 0
    var right = 0
    // A border on one side runs across the others, as a scanner edge down the left crosses the top
    // margin. Each pass looks at the lines only between the borders found so far, until none grows.
    for (pass in 0 until MAX_BORDER_PASSES) {
        // Borders meeting means a blank page, which the size check below leaves uncropped.
        if (top + bottom >= height || left + right >= width) break
        val rowStart = left
        val rowEnd = width - right
        val columnStart = top
        val columnEnd = height - bottom
        val newTop = borderDepth(pixels, width, height, PageSide.Top, rowStart, rowEnd)
        val newBottom = borderDepth(pixels, width, height, PageSide.Bottom, rowStart, rowEnd)
        val newLeft = borderDepth(pixels, width, height, PageSide.Left, columnStart, columnEnd)
        val newRight = borderDepth(pixels, width, height, PageSide.Right, columnStart, columnEnd)
        if (newTop == top && newBottom == bottom && newLeft == left && newRight == right) break
        top = newTop
        bottom = newBottom
        left = newLeft
        right = newRight
    }
    if (height - top - bottom < height * MIN_CONTENT_FRACTION) {
        top = 0
        bottom = 0
    }
    if (width - left - right < width * MIN_CONTENT_FRACTION) {
        left = 0
        right = 0
    }
    if (top == 0 && bottom == 0 && left == 0 && right == 0) return null
    return PageContentBounds(left = left, top = top, right = width - right, bottom = height - bottom)
}

/**
 * Maps [this], found on a [sampleWidth] × [sampleHeight] copy, onto the full page. Edges are rounded
 * outward and given a small margin, so the crop errs on keeping art. Returns null when the crop
 * would be too small to matter.
 */
internal fun PageContentBounds.scaledToPage(
    sampleWidth: Int,
    sampleHeight: Int,
    pageWidth: Int,
    pageHeight: Int,
): PageContentBounds? {
    if (sampleWidth <= 0 || sampleHeight <= 0 || pageWidth <= 0 || pageHeight <= 0) return null
    val scaleX = pageWidth.toDouble() / sampleWidth
    val scaleY = pageHeight.toDouble() / sampleHeight
    val marginX = ceil(pageWidth * CONTENT_MARGIN_FRACTION.toDouble()).toInt()
    val marginY = ceil(pageHeight * CONTENT_MARGIN_FRACTION.toDouble()).toInt()
    val scaled = PageContentBounds(
        left = if (left > 0) (floor(left * scaleX).toInt() - marginX).coerceAtLeast(0) else 0,
        top = if (top > 0) (floor(top * scaleY).toInt() - marginY).coerceAtLeast(0) else 0,
        right = if (right < sampleWidth) (ceil(right * scaleX).toInt() + marginX).coerceAtMost(pageWidth) else pageWidth,
        bottom = if (bottom < sampleHeight) (ceil(bottom * scaleY).toInt() + marginY).coerceAtMost(pageHeight) else pageHeight,
    )
    if (scaled.width <= 0 || scaled.height <= 0) return null
    val trimmedX = pageWidth - scaled.width
    val trimmedY = pageHeight - scaled.height
    if (trimmedX < pageWidth * MIN_USEFUL_CROP_FRACTION && trimmedY < pageHeight * MIN_USEFUL_CROP_FRACTION) return null
    return scaled
}

private enum class PageSide { Top, Bottom, Left, Right }

/**
 * How many lines in from [side] are plain border. A dark band (a scanner edge) may be followed by a
 * light one (the paper margin); a light band is never followed by a dark one, since past a paper
 * margin a dark line is the art's own frame.
 */
private fun borderDepth(
    pixels: IntArray,
    width: Int,
    height: Int,
    side: PageSide,
    lineStart: Int,
    lineEnd: Int,
): Int {
    val horizontal = side == PageSide.Top || side == PageSide.Bottom
    val lineCount = if (horizontal) height else width
    val lineLength = lineEnd - lineStart
    val allowedMisses = (lineLength * BORDER_NOISE_FRACTION).toInt()
    // Positions count from lineStart, so only the part of each line between the other borders is read.
    fun pixel(line: Int, position: Int): Int = when (side) {
        PageSide.Top -> pixels[line * width + lineStart + position]
        PageSide.Bottom -> pixels[(height - 1 - line) * width + lineStart + position]
        PageSide.Left -> pixels[(lineStart + position) * width + line]
        PageSide.Right -> pixels[(lineStart + position) * width + (width - 1 - line)]
    }
    fun lineMatches(line: Int, reference: Int): Boolean {
        var misses = 0
        for (position in 0 until lineLength) {
            if (!pixel(line, position).isNear(reference) && ++misses > allowedMisses) return false
        }
        return true
    }
    var depth = 0
    var previousBandDark: Boolean? = null
    while (depth < lineCount) {
        val reference = averageColor(lineLength) { position -> pixel(depth, position) }
        val dark = reference.borderTone() ?: break
        if (previousBandDark != null && (previousBandDark == false || dark)) break
        val bandStart = depth
        while (depth < lineCount && lineMatches(depth, reference)) depth++
        if (depth == bandStart) break
        previousBandDark = dark
    }
    return depth
}

private inline fun averageColor(count: Int, color: (Int) -> Int): Int {
    var red = 0L
    var green = 0L
    var blue = 0L
    for (index in 0 until count) {
        val value = color(index)
        red += value.red
        green += value.green
        blue += value.blue
    }
    return (0xFF shl 24) or
        ((red / count).toInt() shl 16) or
        ((green / count).toInt() shl 8) or
        (blue / count).toInt()
}

/** Whether a color can be a border: true when near black, false when near white, null otherwise. */
private fun Int.borderTone(): Boolean? {
    val spread = maxOf(red, green, blue) - minOf(red, green, blue)
    if (spread > MAX_BORDER_CHANNEL_SPREAD) return null
    val luma = (red * 299 + green * 587 + blue * 114) / 1000
    return when {
        luma <= MAX_DARK_BORDER_LUMA -> true
        luma >= MIN_LIGHT_BORDER_LUMA -> false
        else -> null
    }
}

private fun Int.isNear(other: Int): Boolean =
    abs(red - other.red) <= BORDER_COLOR_TOLERANCE &&
        abs(green - other.green) <= BORDER_COLOR_TOLERANCE &&
        abs(blue - other.blue) <= BORDER_COLOR_TOLERANCE

private val Int.red: Int get() = (this shr 16) and 0xFF
private val Int.green: Int get() = (this shr 8) and 0xFF
private val Int.blue: Int get() = this and 0xFF
