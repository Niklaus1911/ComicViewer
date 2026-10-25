package com.comicviewer.ui.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Scale
import com.comicviewer.ComicBook
import com.comicviewer.ComicPage
import com.comicviewer.ComicSource
import com.comicviewer.ReaderPreferences
import com.comicviewer.coercePageIndex
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private const val MAX_VERTICAL_ZOOM = 3f
private const val PLACEHOLDER_ASPECT_RATIO = 2f / 3f

/**
 * Pages decode at screen width, times the zoom's detail step while on screen; very tall strips are
 * capped so one page can't exhaust memory.
 */
private const val MAX_PAGE_HEIGHT_TO_WIDTH = 6

/**
 * Continuous top-to-bottom reading for webtoons and manhwa. Pages fill the width; pinching widens the
 * whole strip around the fingers, and the page in the middle of the screen counts as the current page.
 */
@Composable
internal fun VerticalComicReader(
    book: ComicBook,
    pageIndex: Int,
    preferences: ReaderPreferences,
    controlsVisible: Boolean,
    onControlsVisibleChange: (Boolean) -> Unit,
    onPageSelected: (Int) -> Unit,
    onCloseComic: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGrid: () -> Unit,
    favorite: Boolean?,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val pageCount = book.pages.size
    // Gesture handlers outlive recompositions, so they read the latest visibility through this.
    val currentControlsVisible by rememberUpdatedState(controlsVisible)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = coercePageIndex(pageIndex, pageCount))
    var lastReportedPage by remember(book) { mutableIntStateOf(coercePageIndex(pageIndex, pageCount)) }
    // The strip is laid out [zoom] screens wide and shifted sideways by [panX], so zoomed pages load
    // in more detail and the list scrolls over all of them, edges included.
    var zoom by remember(book) { mutableFloatStateOf(1f) }
    var panX by remember(book) { mutableFloatStateOf(0f) }
    val zoomDetailStep by remember(book) { derivedStateOf { readerPageDetailStep(zoom) } }
    val visiblePages by remember(listState) {
        derivedStateOf {
            val visible = listState.layoutInfo.visibleItemsInfo
            if (visible.isEmpty()) IntRange.EMPTY else visible.first().index..visible.last().index
        }
    }
    val measuredPage by remember(listState, pageCount) {
        derivedStateOf { verticalReaderCurrentPage(listState, pageCount) }
    }
    // A restored or jumped-to page is placed at the top of the viewport, but the current page is
    // measured at its middle (or as the last page at the end). Short pages would make every reopen
    // report, and save, a later page, so the measurement only counts once the reader scrolls.
    var readerScrolled by remember(book) { mutableStateOf(false) }
    val currentPage = if (readerScrolled) measuredPage else lastReportedPage
    val marginPx = with(LocalDensity.current) { preferences.marginDp.dp.toPx() }
    val currentMarginPx by rememberUpdatedState(marginPx)
    val stripPlacements = remember { StripPlacements() }

    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) readerScrolled = true
        }
    }

    LaunchedEffect(measuredPage, readerScrolled) {
        if (readerScrolled && measuredPage != lastReportedPage) {
            lastReportedPage = measuredPage
            onPageSelected(measuredPage)
        }
    }

    // Jumps requested from outside the list (slider, page grid) scroll it to the chosen page.
    LaunchedEffect(pageIndex) {
        val target = coercePageIndex(pageIndex, pageCount)
        if (target != lastReportedPage) {
            lastReportedPage = target
            readerScrolled = false
            listState.scrollToItem(target)
        }
    }

    fun scrollBy(pages: Int) {
        val target = coercePageIndex(currentPage + pages, pageCount)
        lastReportedPage = target
        onPageSelected(target)
        scope.launch { listState.animateScrollToItem(target) }
    }

    Box(modifier = modifier) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(book) {
                    // Two fingers zoom the strip around the point between them and carry it along;
                    // one finger pans sideways while zoomed and leaves vertical movement to the list,
                    // so scrolling and panning work together.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var anchor: PinchAnchor? = null
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pressed = event.changes.filter { it.pressed }
                            val viewportWidth = size.width.toFloat()
                            if (pressed.size >= 2) {
                                val oldZoom = zoom
                                val newZoom = (oldZoom * event.calculateZoom()).coerceIn(1f, MAX_VERTICAL_ZOOM)
                                val focus = event.calculateCentroid(useCurrent = false)
                                val fingerPan = event.calculatePan()
                                panX = verticalStripPanX(
                                    viewportWidth = viewportWidth,
                                    marginPx = currentMarginPx,
                                    oldZoom = oldZoom,
                                    oldPanX = panX,
                                    newZoom = newZoom,
                                    focusX = focus.x,
                                    fingerPanX = fingerPan.x,
                                )
                                // Touch events can outpace layout, so until the list is placed again the
                                // pinched page is followed from the position last asked of it.
                                val placements = stripPlacements.count
                                val current = anchor?.takeIf { it.placements == placements }
                                    ?: listState.layoutInfo.itemAt(focus.y)?.let { item ->
                                        PinchAnchor(item.index, item.offset.toFloat(), item.size.toFloat(), placements)
                                    }
                                if (current != null) {
                                    val pageScale = verticalStripPageWidth(viewportWidth, currentMarginPx, newZoom) /
                                        verticalStripPageWidth(viewportWidth, currentMarginPx, oldZoom)
                                    val top = verticalStripItemTop(current.top, current.height, focus.y, fingerPan.y, pageScale)
                                    listState.requestScrollToItem(current.index, -top.roundToInt())
                                    anchor = PinchAnchor(current.index, top, current.height * pageScale, placements)
                                }
                                zoom = newZoom
                                event.changes.forEach { it.consume() }
                            } else if (zoom > 1f && pressed.size == 1) {
                                anchor = null
                                val change = pressed.first()
                                val maxPan = (zoom - 1f) * viewportWidth / 2f
                                panX = (panX + change.position.x - change.previousPosition.x).coerceIn(-maxPan, maxPan)
                            } else {
                                anchor = null
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .pointerInput(book) {
                    detectTapGestures(onTap = { onControlsVisibleChange(!currentControlsVisible) })
                },
        ) {
            val pageWidthPx = with(LocalDensity.current) { (maxWidth - (preferences.marginDp * 2).dp).roundToPx() }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .layout { measurable, constraints ->
                        // Zoom and pan are read here, while placing, so pinching relayouts the strip
                        // without recomposing the reader.
                        val viewportWidth = constraints.maxWidth
                        val stripWidth = (viewportWidth * zoom).roundToInt()
                        val strip = measurable.measure(constraints.copy(minWidth = stripWidth, maxWidth = stripWidth))
                        val maxPan = (stripWidth - viewportWidth) / 2f
                        layout(viewportWidth, constraints.maxHeight) {
                            stripPlacements.count++
                            strip.place(((viewportWidth - stripWidth) / 2f + panX.coerceIn(-maxPan, maxPan)).roundToInt(), 0)
                        }
                    }
                    .padding(horizontal = preferences.marginDp.dp),
            ) {
                items(book.pages, key = { page -> page.index }) { page ->
                    VerticalPageImage(
                        page = page,
                        pageCount = pageCount,
                        source = book.source,
                        pageWidthPx = pageWidthPx,
                        // Only pages on screen load in more detail, which bounds the memory a zoom takes.
                        detailStep = if (page.index in visiblePages) zoomDetailStep else 1,
                    )
                }
            }
        }

        ReaderChrome(
            title = book.title,
            pageCount = pageCount,
            currentPageIndex = currentPage,
            progressPageIndex = currentPage,
            displayPageText = "${currentPage + 1} / $pageCount",
            controlsVisible = controlsVisible,
            showPageNumber = preferences.showPageNumber,
            favorite = favorite,
            onToggleFavorite = onToggleFavorite,
            onCloseComic = onCloseComic,
            onPrevious = { scrollBy(-1) },
            onNext = { scrollBy(1) },
            onPageSelected = onPageSelected,
            onOpenSettings = onOpenSettings,
            onOpenGrid = onOpenGrid,
        )
    }
}

/** The page under the middle of the viewport, or the last page once the strip can't scroll further. */
internal fun verticalReaderCurrentPage(listState: LazyListState, pageCount: Int): Int {
    if (pageCount <= 0) return 0
    if (!listState.canScrollForward && listState.layoutInfo.totalItemsCount > 0) return pageCount - 1
    val layoutInfo = listState.layoutInfo
    val middle = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
    val page = layoutInfo.visibleItemsInfo
        .firstOrNull { item -> item.offset <= middle && item.offset + item.size > middle }
        ?.index
        ?: listState.firstVisibleItemIndex
    return coercePageIndex(page, pageCount)
}

/** Width of a page in the strip at [zoom]: the strip is [zoom] viewports wide, less its margins. */
internal fun verticalStripPageWidth(viewportWidth: Float, marginPx: Float, zoom: Float): Float =
    (viewportWidth * zoom - 2f * marginPx).coerceAtLeast(1f)

/**
 * How far the strip is shifted sideways after a pinch from [oldZoom] to [newZoom] around [focusX]
 * that moved [fingerPanX]: the point under the fingers stays under them, and the strip never pulls
 * away from the screen edges.
 */
internal fun verticalStripPanX(
    viewportWidth: Float,
    marginPx: Float,
    oldZoom: Float,
    oldPanX: Float,
    newZoom: Float,
    focusX: Float,
    fingerPanX: Float,
): Float {
    val oldStripWidth = viewportWidth * oldZoom
    val newStripWidth = viewportWidth * newZoom
    val oldLeft = (viewportWidth - oldStripWidth) / 2f + oldPanX
    val focusFraction = (focusX - oldLeft - marginPx) / verticalStripPageWidth(viewportWidth, marginPx, oldZoom)
    val newLeft = focusX + fingerPanX - marginPx - focusFraction * verticalStripPageWidth(viewportWidth, marginPx, newZoom)
    val maxPan = ((newStripWidth - viewportWidth) / 2f).coerceAtLeast(0f)
    return (newLeft - (viewportWidth - newStripWidth) / 2f).coerceIn(-maxPan, maxPan)
}

/**
 * Where the page under the fingers must start, measured down from the top of the viewport, once its
 * height grows by [pageScale] around [focusY] and the fingers move [fingerPanY], so the point under
 * the fingers stays under them.
 */
internal fun verticalStripItemTop(
    itemTop: Float,
    itemHeight: Float,
    focusY: Float,
    fingerPanY: Float,
    pageScale: Float,
): Float {
    if (itemHeight <= 0f) return itemTop + fingerPanY
    val focusFraction = (focusY - itemTop) / itemHeight
    return focusY + fingerPanY - focusFraction * itemHeight * pageScale
}

/** Counts the strip's placements, so a pinch can tell when the list has caught up with it. */
private class StripPlacements {
    var count = 0
}

/** The page a pinch is anchored to: where it starts and how tall it is, as of [placements]. */
private data class PinchAnchor(val index: Int, val top: Float, val height: Float, val placements: Int)

/** The visible item at [y], or the nearest one when [y] falls past the end of a short list. */
private fun LazyListLayoutInfo.itemAt(y: Float): LazyListItemInfo? =
    visibleItemsInfo.firstOrNull { item -> y >= item.offset && y < item.offset + item.size }
        ?: visibleItemsInfo.lastOrNull { item -> item.offset <= y }
        ?: visibleItemsInfo.firstOrNull()

@Composable
private fun VerticalPageImage(
    page: ComicPage,
    pageCount: Int,
    source: ComicSource,
    pageWidthPx: Int,
    detailStep: Int,
) {
    val context = LocalContext.current
    var aspectRatio by remember(page.imageUri) {
        mutableStateOf(
            if (page.width > 0 && page.height > 0) page.width.toFloat() / page.height else null,
        )
    }
    val requestWidth = pageWidthPx.coerceAtLeast(1) * detailStep
    val request = remember(page.imageUri, requestWidth) {
        ImageRequest.Builder(context)
            .data(page.imageUri)
            .memoryCacheKey("${page.imageRequest(context, source).memoryCacheKey}|vertical|$requestWidth")
            .size(requestWidth, requestWidth * MAX_PAGE_HEIGHT_TO_WIDTH)
            .scale(Scale.FIT)
            .precision(Precision.INEXACT)
            .build()
    }
    val painter = rememberAsyncImagePainter(model = request, contentScale = ContentScale.FillWidth)
    val painterState by painter.state.collectAsState()
    // While a page loads in more (or less) detail, the copy already on screen stays there.
    var shownPainter by remember(page.imageUri) { mutableStateOf<Painter?>(null) }
    LaunchedEffect(painterState) {
        val success = painterState as? AsyncImagePainter.State.Success ?: return@LaunchedEffect
        shownPainter = success.painter
        val image = success.result.image
        if (image.width > 0 && image.height > 0) {
            aspectRatio = image.width.toFloat() / image.height
        }
    }
    val pageLabel = page.accessibilityLabel(pageCount)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio ?: PLACEHOLDER_ASPECT_RATIO)
            .semantics {
                contentDescription = pageLabel
                role = Role.Image
            },
    ) {
        val pagePainter = (painterState as? AsyncImagePainter.State.Success)?.painter ?: shownPainter
        if (pagePainter != null) {
            Image(
                painter = pagePainter,
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
