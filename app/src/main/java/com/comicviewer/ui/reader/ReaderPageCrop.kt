package com.comicviewer.ui.reader

import android.graphics.Bitmap
import android.os.Build
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.size.Dimension
import coil3.size.Size
import coil3.transform.Transformation
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val MIN_CROP_INPUT_SIZE = 16

/** How far, per color channel, a pixel may be from the border color and still count as border. */
private const val BORDER_COLOR_TOLERANCE = 40

/** Share of a border line that may differ from the border color, for specks and scan dust. */
private const val BORDER_NOISE_FRACTION = 0.02f

/** Light borders are near white or paper colored; yellowed paper is slightly warm. */
private const val MIN_LIGHT_BORDER_LUMA = 170
private const val MAX_LIGHT_BORDER_CHANNEL_SPREAD = 48

/**
 * Dark borders (scanner edges, black margins) are neutral black. Art that is dark at the edge, such
 * as a night sky, is usually tinted, so it is not taken for a border.
 */
private const val MAX_DARK_BORDER_LUMA = 70
private const val MAX_DARK_BORDER_CHANNEL_SPREAD = 24

/**
 * A light paper margin is looked for past a dark band only when that band is thin, as a scanner edge
 * is. Past a thick dark area, a light line is more likely a gutter inside the art.
 */
private const val MAX_SCANNER_EDGE_FRACTION = 0.05f

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

/**
 * Cropped pages are requested this much larger than the screen needs, so a page that loses up to a
 * fifth of each side to its borders still has enough pixels to fill the screen sharply.
 */
private const val CROP_REQUEST_HEADROOM = 1.25f

/**
 * The memory cache key extra Coil itself adds to transformed requests (`MemoryCacheService.EXTRA_SIZE`).
 * With it, a cached copy is reused for exactly the size it was requested at. Without it, Coil compares
 * the cached image with the requested size, and a cropped page, always smaller, never counts as cached.
 */
private const val COIL_SIZE_KEY_EXTRA = "coil#size"

/** The part of a page inside its borders; [right] and [bottom] are exclusive. */
internal data class PageContentBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/** How many lines of border each side of a page has. */
internal data class PageBorderInsets(val top: Int, val bottom: Int, val left: Int, val right: Int) {
    val isEmpty: Boolean get() = top == 0 && bottom == 0 && left == 0 && right == 0
}

/**
 * Trims plain white or black borders from reader pages. Any side that is not a plain border, such
 * as art that reaches the edge, is left as it is.
 */
internal object PageBorderCropTransformation : Transformation() {
    override val cacheKey: String = "page-border-crop-v2"

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

/**
 * Sets the size of a reader page request. A cropped page is requested with headroom for its borders
 * and cached for that exact size.
 */
internal fun ImageRequest.Builder.readerPageSize(size: Size, autoCrop: Boolean): ImageRequest.Builder {
    if (!autoCrop) return size(size)
    val requestSize = readerCropRequestSize(size)
    return size(requestSize).memoryCacheKeyExtra(COIL_SIZE_KEY_EXTRA, requestSize.toString())
}

/** [size] grown by [CROP_REQUEST_HEADROOM]; the original size stays original. */
internal fun readerCropRequestSize(size: Size): Size {
    val width = size.width as? Dimension.Pixels ?: return size
    val height = size.height as? Dimension.Pixels ?: return size
    return Size(
        width = (width.px * CROP_REQUEST_HEADROOM).roundToInt(),
        height = (height.px * CROP_REQUEST_HEADROOM).roundToInt(),
    )
}

/** Reads part of one line of a page, from [start] to [end] along it, into [out]. */
private fun interface PageLineReader {
    fun read(side: PageSide, line: Int, start: Int, end: Int, out: IntArray)
}

private enum class PageSide { Top, Bottom, Left, Right }

/**
 * Looks for borders on the decoded page itself. Only border lines are read, from each edge until the
 * art starts, so a page costs about its margin area; a shrunken copy would be quicker to search but
 * blurs away thin frame lines, which the search would then run past.
 */
private fun Bitmap.pageContentBounds(): PageContentBounds? {
    if (width < MIN_CROP_INPUT_SIZE || height < MIN_CROP_INPUT_SIZE) return null
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && config == Bitmap.Config.HARDWARE) return null
    return detectPageBorderInsets(width, height, lineReader())?.toContentBounds(width, height)
}

/**
 * Finds the content of a page given as ARGB [pixels] in rows, by walking in from each side while
 * the lines are a plain near-white or near-black color. Returns null when there is nothing to trim.
 */
internal fun detectPageContentBounds(pixels: IntArray, width: Int, height: Int): PageContentBounds? {
    if (width <= 0 || height <= 0 || pixels.size < width * height) return null
    val insets = detectPageBorderInsets(width, height, pixels.lineReader(width, height)) ?: return null
    return PageContentBounds(insets.left, insets.top, width - insets.right, height - insets.bottom)
}

/**
 * The page's border insets. Returns null when there is no border, or when what looks like border
 * leaves too little of the page to be one.
 */
private fun detectPageBorderInsets(width: Int, height: Int, reader: PageLineReader): PageBorderInsets? {
    // A side deeper than this leaves too little content on its axis, so the search stops there.
    val topLimit = (height * (1f - MIN_CONTENT_FRACTION)).toInt() + 1
    val bottomLimit = topLimit
    val leftLimit = (width * (1f - MIN_CONTENT_FRACTION)).toInt() + 1
    val rightLimit = leftLimit
    fun depth(side: PageSide, start: Int, end: Int, limit: Int): Int =
        borderDepth(reader, side, lineCount = if (side.isHorizontal) height else width, start, end, limit)

    var top = 0
    var bottom = 0
    var left = 0
    var right = 0
    // A border on one side runs across the others, as a scanner edge down the left crosses the top
    // margin. Each pass looks at the lines only between the borders found so far, until none grows.
    for (pass in 0 until MAX_BORDER_PASSES) {
        val newTop = depth(PageSide.Top, left, width - right, topLimit)
        val newBottom = depth(PageSide.Bottom, left, width - right, bottomLimit)
        val newLeft = depth(PageSide.Left, top, height - bottom, leftLimit)
        val newRight = depth(PageSide.Right, top, height - bottom, rightLimit)
        if (newTop == top && newBottom == bottom && newLeft == left && newRight == right) break
        top = newTop
        bottom = newBottom
        left = newLeft
        right = newRight
    }
    val verticalKept = height - top - bottom >= height * MIN_CONTENT_FRACTION
    val horizontalKept = width - left - right >= width * MIN_CONTENT_FRACTION
    // An axis whose crop is dropped shows its whole width again, so the other axis is measured again
    // across all of it: what sat beside the dropped borders, like a caption, must not be cut.
    when {
        verticalKept && !horizontalKept -> {
            left = 0
            right = 0
            top = depth(PageSide.Top, 0, width, topLimit)
            bottom = depth(PageSide.Bottom, 0, width, bottomLimit)
        }
        !verticalKept && horizontalKept -> {
            top = 0
            bottom = 0
            left = depth(PageSide.Left, 0, height, leftLimit)
            right = depth(PageSide.Right, 0, height, rightLimit)
        }
        !verticalKept && !horizontalKept -> return null
    }
    if (height - top - bottom < height * MIN_CONTENT_FRACTION) {
        top = 0
        bottom = 0
    }
    if (width - left - right < width * MIN_CONTENT_FRACTION) {
        left = 0
        right = 0
    }
    return PageBorderInsets(top, bottom, left, right).takeUnless { it.isEmpty }
}

/**
 * The crop for a [pageWidth] × [pageHeight] page with these insets, keeping a small margin so the
 * crop errs on keeping art. Returns null when the crop would be too small to matter.
 */
internal fun PageBorderInsets.toContentBounds(pageWidth: Int, pageHeight: Int): PageContentBounds? {
    if (pageWidth <= 0 || pageHeight <= 0) return null
    val marginX = ceil(pageWidth * CONTENT_MARGIN_FRACTION.toDouble()).toInt()
    val marginY = ceil(pageHeight * CONTENT_MARGIN_FRACTION.toDouble()).toInt()
    val bounds = PageContentBounds(
        left = if (left > 0) (left - marginX).coerceAtLeast(0) else 0,
        top = if (top > 0) (top - marginY).coerceAtLeast(0) else 0,
        right = if (right > 0) (pageWidth - right + marginX).coerceAtMost(pageWidth) else pageWidth,
        bottom = if (bottom > 0) (pageHeight - bottom + marginY).coerceAtMost(pageHeight) else pageHeight,
    )
    if (bounds.width <= 0 || bounds.height <= 0) return null
    val trimmedX = pageWidth - bounds.width
    val trimmedY = pageHeight - bounds.height
    if (trimmedX < pageWidth * MIN_USEFUL_CROP_FRACTION && trimmedY < pageHeight * MIN_USEFUL_CROP_FRACTION) return null
    return bounds
}

private val PageSide.isHorizontal: Boolean get() = this == PageSide.Top || this == PageSide.Bottom

/**
 * How many lines in from a side are plain border, up to [limit], looking only between [start] and
 * [end] along each line. A thin dark band (a scanner edge) may be followed by a light one (the paper
 * margin); a light band is never followed by a dark one, since past a paper margin a dark line is
 * the art's own frame.
 */
private fun borderDepth(
    reader: PageLineReader,
    side: PageSide,
    lineCount: Int,
    start: Int,
    end: Int,
    limit: Int,
): Int {
    val lineLength = end - start
    val maxDepth = minOf(limit, lineCount)
    if (lineLength <= 0 || maxDepth <= 0) return 0
    val allowedMisses = (lineLength * BORDER_NOISE_FRACTION).toInt()
    val line = IntArray(lineLength)
    fun lineMatches(index: Int, reference: Int): Boolean {
        reader.read(side, index, start, end, line)
        var misses = 0
        for (pixel in line) {
            if (!pixel.isNear(reference) && ++misses > allowedMisses) return false
        }
        return true
    }
    var depth = 0
    var previousBandDark: Boolean? = null
    while (depth < maxDepth) {
        reader.read(side, depth, start, end, line)
        val reference = averageColor(line)
        val dark = reference.borderTone() ?: break
        when (previousBandDark) {
            null -> Unit
            false -> break
            true -> if (dark || depth > lineCount * MAX_SCANNER_EDGE_FRACTION) break
        }
        val bandStart = depth
        while (depth < maxDepth && lineMatches(depth, reference)) depth++
        if (depth == bandStart) break
        previousBandDark = dark
    }
    return depth
}

private fun IntArray.lineReader(width: Int, height: Int) = PageLineReader { side, line, start, end, out ->
    for (position in start until end) {
        out[position - start] = when (side) {
            PageSide.Top -> this[line * width + position]
            PageSide.Bottom -> this[(height - 1 - line) * width + position]
            PageSide.Left -> this[position * width + line]
            PageSide.Right -> this[position * width + (width - 1 - line)]
        }
    }
}

private fun Bitmap.lineReader() = PageLineReader { side, line, start, end, out ->
    val length = end - start
    when (side) {
        PageSide.Top -> getPixels(out, 0, length, start, line, length, 1)
        PageSide.Bottom -> getPixels(out, 0, length, start, height - 1 - line, length, 1)
        PageSide.Left -> getPixels(out, 0, 1, line, start, 1, length)
        PageSide.Right -> getPixels(out, 0, 1, width - 1 - line, start, 1, length)
    }
}

private fun averageColor(pixels: IntArray): Int {
    var red = 0L
    var green = 0L
    var blue = 0L
    for (value in pixels) {
        red += value.red
        green += value.green
        blue += value.blue
    }
    val count = pixels.size
    return (0xFF shl 24) or
        ((red / count).toInt() shl 16) or
        ((green / count).toInt() shl 8) or
        (blue / count).toInt()
}

/** Whether a color can be a border: true when neutral black, false when near white, null otherwise. */
private fun Int.borderTone(): Boolean? {
    val spread = maxOf(red, green, blue) - minOf(red, green, blue)
    val luma = (red * 299 + green * 587 + blue * 114) / 1000
    return when {
        luma <= MAX_DARK_BORDER_LUMA && spread <= MAX_DARK_BORDER_CHANNEL_SPREAD -> true
        luma >= MIN_LIGHT_BORDER_LUMA && spread <= MAX_LIGHT_BORDER_CHANNEL_SPREAD -> false
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
