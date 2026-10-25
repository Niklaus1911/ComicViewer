package com.comicviewer.ui.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Scale
import com.comicviewer.ComicBook
import com.comicviewer.ComicPage
import com.comicviewer.ComicSource
import com.comicviewer.ReaderPreferences
import com.comicviewer.coercePageIndex
import kotlinx.coroutines.launch

private const val MAX_VERTICAL_ZOOM = 3f
private const val DOUBLE_TAP_ZOOM = 2f
private const val PLACEHOLDER_ASPECT_RATIO = 2f / 3f

/** Pages decode at full screen width; very tall strips are capped so one page can't exhaust memory. */
private const val MAX_PAGE_HEIGHT_TO_WIDTH = 6

/**
 * Continuous top-to-bottom reading for webtoons and manhwa. Pages fill the width; pinch or double-tap
 * zooms the whole strip, and the page in the middle of the screen counts as the current page.
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
    var zoom by remember(book) { mutableFloatStateOf(1f) }
    var panX by remember(book) { mutableFloatStateOf(0f) }
    val measuredPage by remember(listState, pageCount) {
        derivedStateOf { verticalReaderCurrentPage(listState, pageCount) }
    }
    // A restored or jumped-to page is placed at the top of the viewport, but the current page is
    // measured at its middle (or as the last page at the end). Short pages would make every reopen
    // report, and save, a later page, so the measurement only counts once the reader scrolls.
    var readerScrolled by remember(book) { mutableStateOf(false) }
    val currentPage = if (readerScrolled) measuredPage else lastReportedPage

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
                    // Two fingers zoom the strip; one finger pans sideways while zoomed and leaves
                    // vertical movement to the list, so scrolling and panning work together.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pressed = event.changes.filter { it.pressed }
                            val maxPan = (zoom - 1f) * size.width / 2f
                            if (pressed.size >= 2) {
                                zoom = (zoom * event.calculateZoom()).coerceIn(1f, MAX_VERTICAL_ZOOM)
                                val nextMaxPan = (zoom - 1f) * size.width / 2f
                                panX = (panX + event.calculatePan().x).coerceIn(-nextMaxPan, nextMaxPan)
                                event.changes.forEach { it.consume() }
                            } else if (zoom > 1f && pressed.size == 1) {
                                val change = pressed.first()
                                panX = (panX + change.position.x - change.previousPosition.x).coerceIn(-maxPan, maxPan)
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .pointerInput(book) {
                    detectTapGestures(
                        onTap = { onControlsVisibleChange(!currentControlsVisible) },
                        onDoubleTap = {
                            if (zoom > 1f) {
                                zoom = 1f
                                panX = 0f
                            } else {
                                zoom = DOUBLE_TAP_ZOOM
                            }
                        },
                    )
                },
        ) {
            val pageWidthPx = with(LocalDensity.current) { (maxWidth - (preferences.marginDp * 2).dp).roundToPx() }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        translationX = panX
                    }
                    .padding(horizontal = preferences.marginDp.dp),
            ) {
                items(book.pages, key = { page -> page.index }) { page ->
                    VerticalPageImage(page = page, pageCount = pageCount, source = book.source, pageWidthPx = pageWidthPx)
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

@Composable
private fun VerticalPageImage(
    page: ComicPage,
    pageCount: Int,
    source: ComicSource,
    pageWidthPx: Int,
) {
    val context = LocalContext.current
    var aspectRatio by remember(page.imageUri) {
        mutableStateOf(
            if (page.width > 0 && page.height > 0) page.width.toFloat() / page.height else null,
        )
    }
    val request = remember(page.imageUri, pageWidthPx) {
        ImageRequest.Builder(context)
            .data(page.imageUri)
            .memoryCacheKey("${page.imageRequest(context, source).memoryCacheKey}|vertical|$pageWidthPx")
            .size(pageWidthPx.coerceAtLeast(1), (pageWidthPx * MAX_PAGE_HEIGHT_TO_WIDTH).coerceAtLeast(1))
            .scale(Scale.FIT)
            .precision(Precision.INEXACT)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = page.accessibilityLabel(pageCount),
        contentScale = ContentScale.FillWidth,
        onSuccess = { success ->
            val image = success.result.image
            if (image.width > 0 && image.height > 0) {
                aspectRatio = image.width.toFloat() / image.height
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio ?: PLACEHOLDER_ASPECT_RATIO),
    )
}
