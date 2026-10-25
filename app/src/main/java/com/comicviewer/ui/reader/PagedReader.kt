package com.comicviewer.ui.reader

import android.content.Context
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Size as CoilSize
import com.comicviewer.ComicBook
import com.comicviewer.ComicPage
import com.comicviewer.ComicSource
import com.comicviewer.R
import com.comicviewer.ReaderPageFitMode
import com.comicviewer.ReaderPageMode
import com.comicviewer.ReaderPreferences
import com.comicviewer.ReaderReadingDirection
import com.comicviewer.coercePageIndex
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SWIPE_VELOCITY_THRESHOLD_DP_PER_SECOND = 700f
private const val SWIPE_RELEASE_ANIMATION_DURATION_MS = 380

private fun adjacentPageTranslationX(
    offsetX: Float,
    viewportWidth: Float,
    direction: ReaderSwipeDirection,
): Float = when (direction) {
    ReaderSwipeDirection.Previous -> offsetX - viewportWidth
    ReaderSwipeDirection.Next -> offsetX + viewportWidth
}

private data class RememberedPagePainter(
    val imageUri: String,
    val painter: Painter,
)

@Composable
internal fun PagedComicReader(
    book: ComicBook,
    pageIndex: Int,
    activeReaderPreferences: ReaderPreferences,
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
    val context = LocalContext.current
    val resources = LocalResources.current
    val density = LocalDensity.current
    // Gesture handlers outlive recompositions, so they read the latest visibility through this.
    val currentControlsVisible by rememberUpdatedState(controlsVisible)
    val scope = rememberCoroutineScope()
    val imageLoader = remember(context) { SingletonImageLoader.get(context) }
    val currentIndex = coercePageIndex(pageIndex, book.pages.size)
    val requestedVisualIndex = if (activeReaderPreferences.pageMode == ReaderPageMode.TwoPage) {
        readerSpreadStartIndex(currentIndex, book.pages.size)
    } else {
        currentIndex
    }
    var visualPageIndex by remember(book) { mutableIntStateOf(requestedVisualIndex) }
    val visualPage = book.pages[coercePageIndex(visualPageIndex, book.pages.size)]
    val spreadIndices = if (activeReaderPreferences.pageMode == ReaderPageMode.TwoPage) {
        readerSpreadPageIndices(visualPageIndex, book.pages.size)
    } else {
        listOf(visualPage.index)
    }
    val secondSpreadPage = spreadIndices.getOrNull(1)?.let(book.pages::getOrNull)
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val originalSize = activeReaderPreferences.pageFitMode == ReaderPageFitMode.Original
    // Detail for pages about to be shown: what the fit mode needs before any pinch.
    var baseDetailStep by remember(book, activeReaderPreferences.pageFitMode) { mutableIntStateOf(1) }
    // Detail for the visible page: it rises while the page is zoomed and never falls, so zooming
    // back out keeps the sharper copy instead of loading the page again.
    var pageDetailStep by remember(
        visualPage.imageUri,
        secondSpreadPage?.imageUri,
        activeReaderPreferences.pageMode,
    ) { mutableIntStateOf(baseDetailStep) }
    val currentRequest = remember(
        book.source,
        visualPage.imageUri,
        visualPage.relativePath,
        viewportSize,
        pageDetailStep,
        originalSize,
    ) {
        visualPage.readerPageRequest(context, book.source, viewportSize, pageDetailStep, originalSize)
    }
    val painter = rememberAsyncImagePainter(
        model = currentRequest,
        imageLoader = imageLoader,
        contentScale = ContentScale.Fit,
    )
    val painterState by painter.state.collectAsState()
    val secondPainter = rememberAsyncImagePainter(
        model = remember(
            book.source,
            secondSpreadPage?.imageUri,
            secondSpreadPage?.relativePath,
            viewportSize,
            pageDetailStep,
            originalSize,
        ) {
            secondSpreadPage?.readerPageRequest(context, book.source, viewportSize, pageDetailStep, originalSize)
        },
        imageLoader = imageLoader,
        contentScale = ContentScale.Fit,
    )
    val secondPainterState by secondPainter.state.collectAsState()
    var lastSuccessfulPainter by remember(book) { mutableStateOf<RememberedPagePainter?>(null) }
    var lastSuccessfulSecondPainter by remember(book) { mutableStateOf<RememberedPagePainter?>(null) }
    var primaryPageLoadError by remember(visualPage.imageUri) { mutableStateOf<String?>(null) }
    var secondaryPageLoadError by remember(secondSpreadPage?.imageUri) { mutableStateOf<String?>(null) }
    var pageScale by remember(
        visualPage.imageUri,
        secondSpreadPage?.imageUri,
        activeReaderPreferences.pageMode,
    ) { mutableFloatStateOf(MIN_PAGE_SCALE) }
    var pageOffset by remember(
        visualPage.imageUri,
        secondSpreadPage?.imageUri,
        activeReaderPreferences.pageMode,
    ) { mutableStateOf(Offset.Zero) }
    // A page larger than the viewport is moved to where reading starts as soon as its size is known,
    // and again when the viewport or the fit settings change, for example when the screen rotates.
    var pageOffsetPlaced by remember(
        visualPage.imageUri,
        secondSpreadPage?.imageUri,
        activeReaderPreferences.pageMode,
        activeReaderPreferences.pageFitMode,
        activeReaderPreferences.readingDirection,
        viewportSize,
    ) { mutableStateOf(false) }
    var swipeOffsetX by remember { mutableFloatStateOf(0f) }
    var swipeDragStartOffsetX by remember { mutableFloatStateOf(0f) }
    var activeSwipeDirection by remember { mutableStateOf<ReaderSwipeDirection?>(null) }
    var swipeSettleJob by remember(book) { mutableStateOf<Job?>(null) }
    val adjacentLogicalDirection = activeSwipeDirection
        ?: readerSwipeDirectionForOffset(swipeOffsetX)?.let { physicalDirection ->
            readerLogicalDirectionForPhysicalDirection(
                physicalDirection = physicalDirection,
                readingDirection = activeReaderPreferences.readingDirection,
            )
        }
    val adjacentPageIndex = adjacentLogicalDirection?.let { direction ->
        if (activeReaderPreferences.pageMode == ReaderPageMode.TwoPage) {
            readerSpreadTargetIndex(
                currentPageIndex = visualPage.index,
                pageCount = book.pages.size,
                direction = direction,
            )
        } else {
            readerSwipeTargetIndex(
                currentPageIndex = visualPage.index,
                pageCount = book.pages.size,
                direction = direction,
            )
        }
    }
    val adjacentPage = adjacentPageIndex?.let(book.pages::getOrNull)
    val adjacentSpreadIndices = if (
        adjacentPageIndex != null && activeReaderPreferences.pageMode == ReaderPageMode.TwoPage
    ) {
        readerSpreadPageIndices(adjacentPageIndex, book.pages.size)
    } else {
        listOfNotNull(adjacentPageIndex)
    }
    val adjacentSecondPage = adjacentSpreadIndices.getOrNull(1)?.let(book.pages::getOrNull)
    // Neighbours use the preload size, so a preloaded page is already in memory when swiped in.
    val adjacentRequest = remember(
        book.source,
        adjacentPage?.imageUri,
        adjacentPage?.relativePath,
        viewportSize,
        baseDetailStep,
        originalSize,
    ) {
        adjacentPage?.readerPageRequest(context, book.source, viewportSize, baseDetailStep, originalSize)
    }
    val adjacentPainter = rememberAsyncImagePainter(
        model = adjacentRequest,
        imageLoader = imageLoader,
        contentScale = ContentScale.Fit,
    )
    val adjacentSecondPainter = rememberAsyncImagePainter(
        model = remember(
            book.source,
            adjacentSecondPage?.imageUri,
            adjacentSecondPage?.relativePath,
            viewportSize,
            baseDetailStep,
            originalSize,
        ) {
            adjacentSecondPage?.readerPageRequest(context, book.source, viewportSize, baseDetailStep, originalSize)
        },
        imageLoader = imageLoader,
        contentScale = ContentScale.Fit,
    )

    DisposableEffect(book) {
        onDispose {
            swipeSettleJob?.cancel()
        }
    }

    LaunchedEffect(book, requestedVisualIndex, activeReaderPreferences.pageMode) {
        if (requestedVisualIndex != visualPageIndex) {
            swipeSettleJob?.cancel()
            visualPageIndex = requestedVisualIndex
            swipeOffsetX = 0f
            swipeDragStartOffsetX = 0f
            activeSwipeDirection = null
        }
        val reportedPageIndex = readerReportedPageIndex(
            visualPageIndex = requestedVisualIndex,
            pageCount = book.pages.size,
            pageMode = activeReaderPreferences.pageMode,
        )
        if (reportedPageIndex != currentIndex) {
            onPageSelected(reportedPageIndex)
        }
    }

    fun resetSwipeState() {
        swipeOffsetX = 0f
        swipeDragStartOffsetX = 0f
        activeSwipeDirection = null
        swipeSettleJob = null
    }

    fun targetPageFor(direction: ReaderSwipeDirection): Int? = readerTargetPageIndex(
        currentPageIndex = visualPage.index,
        pageCount = book.pages.size,
        direction = direction,
        pageMode = activeReaderPreferences.pageMode,
    )

    fun reportVisiblePage(visualIndex: Int) {
        onPageSelected(
            readerReportedPageIndex(
                visualPageIndex = visualIndex,
                pageCount = book.pages.size,
                pageMode = activeReaderPreferences.pageMode,
            ),
        )
    }

    fun navigate(direction: ReaderSwipeDirection) {
        val target = targetPageFor(direction) ?: return
        visualPageIndex = target
        pageScale = MIN_PAGE_SCALE
        pageOffset = Offset.Zero
        reportVisiblePage(target)
    }

    fun startSwipeDrag(direction: ReaderSwipeDirection) {
        swipeSettleJob?.cancel()
        swipeSettleJob = null
        val startOffsetX = readerSwipeStartOffset(swipeOffsetX, direction)
        swipeOffsetX = startOffsetX
        swipeDragStartOffsetX = startOffsetX
        activeSwipeDirection = direction
    }

    fun updateSwipeDrag(offsetX: Float) {
        activeSwipeDirection = readerSwipeDirectionForOffset(offsetX)?.let { physicalDirection ->
            readerLogicalDirectionForPhysicalDirection(
                physicalDirection = physicalDirection,
                readingDirection = activeReaderPreferences.readingDirection,
            )
        } ?: activeSwipeDirection
        swipeOffsetX = resistedReaderSwipeOffset(
            rawOffsetX = swipeDragStartOffsetX + offsetX,
            currentPageIndex = visualPage.index,
            pageCount = book.pages.size,
            readingDirection = activeReaderPreferences.readingDirection,
            pageMode = activeReaderPreferences.pageMode,
        )
    }

    suspend fun animateSwipeRelease(targetOffsetX: Float) {
        animate(
            initialValue = swipeOffsetX,
            targetValue = targetOffsetX,
            animationSpec = tween(
                durationMillis = SWIPE_RELEASE_ANIMATION_DURATION_MS,
                easing = LinearOutSlowInEasing,
            ),
        ) { value, _ ->
            swipeOffsetX = value
        }
    }

    fun finishSwipeDrag(offsetX: Float, velocityX: Float) {
        val velocityThreshold = with(density) { SWIPE_VELOCITY_THRESHOLD_DP_PER_SECOND.dp.toPx() }
        val physicalCommitDirection = readerPhysicalSwipeCommitDirection(
            offsetX = offsetX,
            velocityX = velocityX,
            velocityThresholdPx = velocityThreshold,
        )
        val direction = readerSwipeCommitDirection(
            offsetX = offsetX,
            velocityX = velocityX,
            viewportWidth = viewportSize.width.toFloat(),
            currentPageIndex = visualPage.index,
            pageCount = book.pages.size,
            velocityThresholdPx = velocityThreshold,
            readingDirection = activeReaderPreferences.readingDirection,
            pageMode = activeReaderPreferences.pageMode,
        )
        val targetPageIndex = direction?.let(::targetPageFor)

        if (direction != null && targetPageIndex != null) {
            val incomingOffsetX = readerIncomingPageOffsetAfterCommit(
                direction = physicalCommitDirection ?: readerSwipeDirectionForOffset(swipeOffsetX) ?: direction,
                currentOffsetX = swipeOffsetX,
                viewportWidth = viewportSize.width.toFloat(),
            )
            swipeSettleJob?.cancel()
            visualPageIndex = targetPageIndex
            swipeOffsetX = incomingOffsetX
            swipeDragStartOffsetX = 0f
            activeSwipeDirection = null
            reportVisiblePage(targetPageIndex)
            swipeSettleJob = scope.launch {
                animateSwipeRelease(0f)
                resetSwipeState()
            }
        } else {
            swipeSettleJob?.cancel()
            swipeSettleJob = scope.launch {
                animateSwipeRelease(0f)
                resetSwipeState()
            }
        }
    }

    LaunchedEffect(book.pages, visualPage.index, imageLoader, viewportSize, baseDetailStep, originalSize) {
        // Building a request stats the source file for its cache key, so that happens off the main thread.
        val requests = withContext(Dispatchers.IO) {
            book.pages.preloadRequests(
                currentIndex = visualPage.index,
                context = context,
                source = book.source,
                viewportSize = viewportSize,
                detailStep = baseDetailStep,
                originalSize = originalSize,
            )
        }
        // Preloads for pages left behind, or for a closed reader, are cancelled: they would otherwise
        // delay the visible page and reopen sources that closing the comic just released.
        val preloads = requests.map(imageLoader::enqueue)
        try {
            awaitCancellation()
        } finally {
            preloads.forEach { preload -> preload.dispose() }
        }
    }

    LaunchedEffect(painterState, visualPage.imageUri, resources) {
        when (val state = painterState) {
            is AsyncImagePainter.State.Success -> {
                lastSuccessfulPainter = RememberedPagePainter(
                    imageUri = visualPage.imageUri.toString(),
                    painter = state.painter,
                )
                primaryPageLoadError = null
            }

            is AsyncImagePainter.State.Error -> {
                primaryPageLoadError = resources.getString(R.string.page_image_error, visualPage.displayName)
            }

            else -> Unit
        }
    }

    LaunchedEffect(secondPainterState, secondSpreadPage?.imageUri, resources) {
        val state = secondPainterState
        if (secondSpreadPage != null && state is AsyncImagePainter.State.Success) {
            lastSuccessfulSecondPainter = RememberedPagePainter(
                imageUri = secondSpreadPage.imageUri.toString(),
                painter = state.painter,
            )
        }
        secondaryPageLoadError = when {
            secondSpreadPage == null -> null
            secondPainterState is AsyncImagePainter.State.Error -> resources.getString(
                R.string.page_image_error,
                secondSpreadPage.displayName,
            )
            secondPainterState is AsyncImagePainter.State.Success -> null
            else -> secondaryPageLoadError
        }
    }

    // The decoded image is drawn rather than Coil's painter: when a sharper copy is requested, that
    // painter goes blank a frame before its state says so, which made the page flash. While the
    // sharper copy loads, the one already shown stands in for it.
    val displayedPainter = (painterState as? AsyncImagePainter.State.Success)?.painter
        ?: lastSuccessfulPainter
            ?.takeIf { remembered -> remembered.imageUri == visualPage.imageUri.toString() }
            ?.painter
    val displayedSecondPainter = (secondPainterState as? AsyncImagePainter.State.Success)?.painter
        ?: lastSuccessfulSecondPainter
            ?.takeIf { remembered -> remembered.imageUri == secondSpreadPage?.imageUri?.toString() }
            ?.painter
    val primaryIntrinsicSize = displayedPainter?.intrinsicSize ?: Size.Unspecified
    val secondaryIntrinsicSize = displayedSecondPainter?.intrinsicSize ?: Size.Unspecified
    val primaryPageWidth = readerResolvedPageDimension(visualPage.width, primaryIntrinsicSize.width)
    val primaryPageHeight = readerResolvedPageDimension(visualPage.height, primaryIntrinsicSize.height)
    val secondaryPageWidth = secondSpreadPage?.let { page ->
        readerResolvedPageDimension(page.width, secondaryIntrinsicSize.width)
    } ?: 0
    val secondaryPageHeight = secondSpreadPage?.let { page ->
        readerResolvedPageDimension(page.height, secondaryIntrinsicSize.height)
    } ?: 0
    val layoutPageWidth = if (secondSpreadPage != null) {
        (primaryPageWidth.toLong() + secondaryPageWidth.toLong())
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    } else {
        primaryPageWidth
    }
    val layoutPageHeight = if (secondSpreadPage != null) {
        maxOf(primaryPageHeight, secondaryPageHeight)
    } else {
        primaryPageHeight
    }
    val effectivePageScale = readerEffectivePageScale(
        pageFitMode = activeReaderPreferences.pageFitMode,
        pageWidth = layoutPageWidth,
        pageHeight = layoutPageHeight,
        viewportWidth = viewportSize.width,
        viewportHeight = viewportSize.height,
        userScale = pageScale,
    )
    val pageSizeKnown = layoutPageWidth > 0 && layoutPageHeight > 0 && viewportSize != IntSize.Zero

    LaunchedEffect(pageSizeKnown, effectivePageScale, layoutPageWidth, layoutPageHeight, viewportSize) {
        if (!pageSizeKnown || originalSize) return@LaunchedEffect
        baseDetailStep = readerPageDetailStep(
            readerEffectivePageScale(
                pageFitMode = activeReaderPreferences.pageFitMode,
                pageWidth = layoutPageWidth,
                pageHeight = layoutPageHeight,
                viewportWidth = viewportSize.width,
                viewportHeight = viewportSize.height,
                userScale = MIN_PAGE_SCALE,
            ),
        )
        pageDetailStep = max(pageDetailStep, readerPageDetailStep(effectivePageScale))
    }

    val horizontalSwipeEnabled = readerHorizontalSwipeEnabled(
        pageWidth = layoutPageWidth,
        pageHeight = layoutPageHeight,
        viewportWidth = viewportSize.width,
        viewportHeight = viewportSize.height,
        effectiveScale = effectivePageScale,
        userScale = pageScale,
    )
    val currentHorizontalSwipeEnabled = rememberUpdatedState(horizontalSwipeEnabled)
    // A sharper copy of the page arriving mid-pinch changes its pixel size. The gesture reads the size
    // live instead of restarting, which would cut the pinch off.
    val currentLayoutPageSize by rememberUpdatedState(IntSize(layoutPageWidth, layoutPageHeight))

    LaunchedEffect(
        visualPage.imageUri,
        secondSpreadPage?.imageUri,
        viewportSize,
        layoutPageWidth,
        layoutPageHeight,
        effectivePageScale,
    ) {
        pageOffset = if (!pageOffsetPlaced && pageSizeKnown) {
            pageOffsetPlaced = true
            readerPageStartOffset(
                pageWidth = layoutPageWidth,
                pageHeight = layoutPageHeight,
                scale = effectivePageScale,
                viewportSize = viewportSize,
                readingDirection = activeReaderPreferences.readingDirection,
            )
        } else {
            pageOffset.clampedFor(
                pageWidth = layoutPageWidth,
                pageHeight = layoutPageHeight,
                scale = effectivePageScale,
                viewportSize = viewportSize,
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(activeReaderPreferences.marginDp.dp)
                .clipToBounds()
                .onSizeChanged { viewportSize = it }
                .pointerInput(
                    visualPage.imageUri,
                    secondSpreadPage?.imageUri,
                    viewportSize,
                    activeReaderPreferences.readingDirection,
                    activeReaderPreferences.pageFitMode,
                    activeReaderPreferences.pageMode,
                    activeReaderPreferences.marginDp,
                ) {
                    detectPageGestures(
                        swipingEnabled = { currentHorizontalSwipeEnabled.value },
                        onTap = { tapOffset ->
                            when (
                                readerNavigationDirectionForTap(
                                    tapX = tapOffset.x,
                                    viewportWidth = size.width.toFloat(),
                                    readingDirection = activeReaderPreferences.readingDirection,
                                )
                            ) {
                                ReaderSwipeDirection.Previous -> navigate(ReaderSwipeDirection.Previous)
                                ReaderSwipeDirection.Next -> navigate(ReaderSwipeDirection.Next)
                                null -> onControlsVisibleChange(!currentControlsVisible)
                            }
                        },
                        onSwipeStart = { physicalDirection ->
                            startSwipeDrag(
                                readerLogicalDirectionForPhysicalDirection(
                                    physicalDirection = physicalDirection,
                                    readingDirection = activeReaderPreferences.readingDirection,
                                ),
                            )
                        },
                        onSwipeDrag = ::updateSwipeDrag,
                        onSwipeEnd = ::finishSwipeDrag,
                        onSwipeCancel = {
                            swipeSettleJob?.cancel()
                            swipeSettleJob = scope.launch {
                                animateSwipeRelease(0f)
                                resetSwipeState()
                            }
                        },
                    ) { centroid, pan, zoomChange ->
                        val previousScale = pageScale
                        val nextScale = (previousScale * zoomChange).coerceIn(MIN_PAGE_SCALE, MAX_PAGE_SCALE)
                        val viewportCenter = Offset(
                            x = viewportSize.width / 2f,
                            y = viewportSize.height / 2f,
                        )
                        val centroidFromCenter = centroid - viewportCenter
                        val scaleChange = nextScale / previousScale
                        val nextOffset = pageOffset +
                            pan +
                            centroidFromCenter * (1f - scaleChange)

                        pageScale = nextScale
                        pageOffset = nextOffset.clampedFor(
                            pageWidth = currentLayoutPageSize.width,
                            pageHeight = currentLayoutPageSize.height,
                            scale = readerEffectivePageScale(
                                pageFitMode = activeReaderPreferences.pageFitMode,
                                pageWidth = currentLayoutPageSize.width,
                                pageHeight = currentLayoutPageSize.height,
                                viewportWidth = viewportSize.width,
                                viewportHeight = viewportSize.height,
                                userScale = nextScale,
                            ),
                            viewportSize = viewportSize,
                        )
                    }
                },
        ) {
            val viewportWidth = viewportSize.width.toFloat()
            val targetPage = adjacentPage
            val physicalTargetDirection = readerSwipeDirectionForOffset(swipeOffsetX)
            if (targetPage != null && physicalTargetDirection != null && swipeOffsetX != 0f) {
                val translationX = adjacentPageTranslationX(
                    offsetX = swipeOffsetX,
                    viewportWidth = viewportWidth,
                    direction = physicalTargetDirection,
                )
                if (adjacentSecondPage != null) {
                    ZoomableSpreadImages(
                        firstPainter = adjacentPainter,
                        secondPainter = adjacentSecondPainter,
                        firstDescription = targetPage.accessibilityLabel(book.pages.size),
                        secondDescription = adjacentSecondPage.accessibilityLabel(book.pages.size),
                        readingDirection = activeReaderPreferences.readingDirection,
                        scale = MIN_PAGE_SCALE,
                        offset = Offset.Zero,
                        swipeOffsetX = translationX,
                    )
                } else {
                    SwipeTargetPageImage(
                        painter = adjacentPainter,
                        contentDescription = targetPage.accessibilityLabel(book.pages.size),
                        translationX = translationX,
                    )
                }
            }

            displayedPainter?.let { pagePainter ->
                if (secondSpreadPage != null) {
                    ZoomableSpreadImages(
                        firstPainter = pagePainter,
                        secondPainter = displayedSecondPainter ?: secondPainter,
                        firstDescription = visualPage.accessibilityLabel(book.pages.size),
                        secondDescription = secondSpreadPage.accessibilityLabel(book.pages.size),
                        readingDirection = activeReaderPreferences.readingDirection,
                        scale = effectivePageScale,
                        offset = pageOffset,
                        swipeOffsetX = swipeOffsetX,
                    )
                } else {
                    ZoomablePageImage(
                        painter = pagePainter,
                        contentDescription = visualPage.accessibilityLabel(book.pages.size),
                        scale = effectivePageScale,
                        offset = pageOffset,
                        swipeOffsetX = swipeOffsetX,
                    )
                }
            }
        }

        val pageLoadError = primaryPageLoadError ?: secondaryPageLoadError
        pageLoadError?.let { message ->
            PageImageError(
                message = message,
                relativePath = if (primaryPageLoadError != null) {
                    visualPage.relativePath
                } else {
                    secondSpreadPage?.relativePath.orEmpty()
                },
                modifier = Modifier.align(Alignment.Center),
            )
        }

        val lastVisiblePageIndex = spreadIndices.lastOrNull() ?: visualPage.index
        val displayPageText = if (spreadIndices.size > 1) {
            "${spreadIndices.first() + 1}–${spreadIndices.last() + 1} / ${book.pages.size}"
        } else {
            "${visualPage.index + 1} / ${book.pages.size}"
        }

        ReaderChrome(
            title = book.title,
            pageCount = book.pages.size,
            currentPageIndex = visualPage.index,
            progressPageIndex = lastVisiblePageIndex,
            displayPageText = displayPageText,
            controlsVisible = controlsVisible,
            showPageNumber = activeReaderPreferences.showPageNumber,
            favorite = favorite,
            onToggleFavorite = onToggleFavorite,
            onCloseComic = onCloseComic,
            onPrevious = { navigate(ReaderSwipeDirection.Previous) },
            onNext = { navigate(ReaderSwipeDirection.Next) },
            onPageSelected = ::reportVisiblePage,
            onOpenSettings = onOpenSettings,
            onOpenGrid = onOpenGrid,
        )
    }
}

@Composable
fun ZoomablePageImage(
    painter: androidx.compose.ui.graphics.painter.Painter,
    contentDescription: String?,
    scale: Float,
    offset: Offset,
    modifier: Modifier = Modifier,
    swipeOffsetX: Float = 0f,
) {
    Image(
        painter = painter,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x + swipeOffsetX
                translationY = offset.y
            },
    )
}

@Composable
private fun ZoomableSpreadImages(
    firstPainter: Painter,
    secondPainter: Painter,
    firstDescription: String?,
    secondDescription: String?,
    readingDirection: ReaderReadingDirection,
    scale: Float,
    offset: Offset,
    swipeOffsetX: Float,
) {
    val pages = if (readingDirection == ReaderReadingDirection.RightToLeft) {
        listOf(secondPainter to secondDescription, firstPainter to firstDescription)
    } else {
        listOf(firstPainter to firstDescription, secondPainter to secondDescription)
    }
    Row(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x + swipeOffsetX
                translationY = offset.y
            },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        pages.forEach { (pagePainter, description) ->
            Image(
                painter = pagePainter,
                contentDescription = description,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun SwipeTargetPageImage(
    painter: androidx.compose.ui.graphics.painter.Painter,
    contentDescription: String?,
    translationX: Float,
) {
    ZoomablePageImage(
        painter = painter,
        contentDescription = contentDescription,
        scale = MIN_PAGE_SCALE,
        offset = Offset.Zero,
        swipeOffsetX = translationX,
    )
}

private suspend fun PointerInputScope.detectPageGestures(
    swipingEnabled: () -> Boolean,
    onTap: (Offset) -> Unit,
    onSwipeStart: (ReaderSwipeDirection) -> Unit,
    onSwipeDrag: (offsetX: Float) -> Unit,
    onSwipeEnd: (offsetX: Float, velocityX: Float) -> Unit,
    onSwipeCancel: () -> Unit,
    onTransform: (centroid: Offset, pan: Offset, zoom: Float) -> Unit,
) {
    awaitEachGesture {
        val firstDown = awaitFirstDown(requireUnconsumed = false)
        val swipeAllowedForGesture = swipingEnabled()
        val startPosition = firstDown.position
        val velocityTracker = VelocityTracker().apply {
            addPosition(firstDown.uptimeMillis, firstDown.position)
        }
        val touchSlop = viewConfiguration.touchSlop
        var event: PointerEvent
        var pointerCountExceededTap = false
        var movedPastTapSlop = false
        var pastTransformSlop = false
        var swipeActive = false
        var accumulatedPan = Offset.Zero
        var accumulatedZoom = 1f

        do {
            event = awaitPointerEvent()
            if (event.changes.count { it.pressed } > 1) {
                pointerCountExceededTap = true
                if (swipeActive) {
                    onSwipeCancel()
                    return@awaitEachGesture
                }
            }

            event.changes
                .firstOrNull { it.id == firstDown.id }
                ?.let { firstPointer ->
                    velocityTracker.addPosition(firstPointer.uptimeMillis, firstPointer.position)
                    if ((firstPointer.position - startPosition).getDistance() > touchSlop) {
                        movedPastTapSlop = true
                    }
                }

            val zoomChange = event.calculateZoom()
            val panChange = event.calculatePan()

            if (swipeActive) {
                accumulatedPan += panChange
                event.changes.forEach { change ->
                    if (change.positionChanged()) {
                        change.consume()
                    }
                }
                onSwipeDrag(accumulatedPan.x)
            } else if (!pastTransformSlop) {
                accumulatedZoom *= zoomChange
                accumulatedPan += panChange

                val centroidSize = event.calculateCentroidSize(useCurrent = false)
                val zoomMotion = abs(1f - accumulatedZoom) * centroidSize
                val panMotion = accumulatedPan.getDistance()
                val horizontalSwipeMotion = abs(accumulatedPan.x)
                val verticalSwipeMotion = abs(accumulatedPan.y)
                val horizontalSwipeCandidate = swipeAllowedForGesture &&
                    !pointerCountExceededTap &&
                    zoomMotion <= touchSlop &&
                    horizontalSwipeMotion > verticalSwipeMotion * 1.4f
                val shouldStartSwipe = horizontalSwipeCandidate &&
                    horizontalSwipeMotion > touchSlop &&
                    panMotion > touchSlop

                if (shouldStartSwipe) {
                    swipeActive = true
                    onSwipeStart(
                        if (accumulatedPan.x > 0f) ReaderSwipeDirection.Previous else ReaderSwipeDirection.Next,
                    )
                    event.changes.forEach { change ->
                        if (change.positionChanged()) {
                            change.consume()
                        }
                    }
                    onSwipeDrag(accumulatedPan.x)
                } else if (horizontalSwipeCandidate) {
                    event.changes.forEach { change ->
                        if (change.positionChanged()) {
                            change.consume()
                        }
                    }
                } else if (zoomMotion > touchSlop || panMotion > touchSlop) {
                    pastTransformSlop = true
                }
            }

            if (pastTransformSlop) {
                val centroid = event.calculateCentroid(useCurrent = false)
                if (centroid != Offset.Unspecified && (zoomChange != 1f || panChange != Offset.Zero)) {
                    onTransform(centroid, panChange, zoomChange)
                }
                event.changes.forEach { change ->
                    if (change.positionChanged()) {
                        change.consume()
                    }
                }
            }
        } while (event.changes.any { it.pressed })

        if (swipeActive) {
            onSwipeEnd(accumulatedPan.x, velocityTracker.calculateVelocity().x)
        } else if (!pointerCountExceededTap && !movedPastTapSlop && !pastTransformSlop) {
            onTap(startPosition)
        }
    }
}

private fun List<ComicPage>.preloadRequests(
    currentIndex: Int,
    context: Context,
    source: ComicSource,
    viewportSize: IntSize,
    detailStep: Int,
    originalSize: Boolean,
): List<ImageRequest> =
    listOf(currentIndex + 1, currentIndex + 2, currentIndex - 1, currentIndex - 2)
        .filter { index -> index in indices }
        .mapNotNull { index -> get(index).readerPageRequest(context, source, viewportSize, detailStep, originalSize) }

/**
 * A page request sized for the reader: [detailStep] times the viewport, or the page's own full size in
 * the Original fit mode, so that mode shows every page at its real pixel size. Inexact precision lets
 * Coil keep a decoded size between the request and the page's own instead of scaling a page up.
 * Nothing is requested until the viewport has been measured.
 */
private fun ComicPage.readerPageRequest(
    context: Context,
    source: ComicSource,
    viewportSize: IntSize,
    detailStep: Int,
    originalSize: Boolean,
): ImageRequest? {
    if (viewportSize == IntSize.Zero) return null
    val size = if (originalSize) {
        CoilSize.ORIGINAL
    } else {
        CoilSize(viewportSize.width * detailStep, viewportSize.height * detailStep)
    }
    return imageRequest(context, source).newBuilder()
        .size(size)
        .precision(Precision.INEXACT)
        .build()
}

@Composable
fun PageImageError(
    message: String,
    relativePath: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        color = Color.Black.copy(alpha = 0.72f),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Text(
                text = relativePath,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.78f),
                textAlign = TextAlign.Center,
            )
        }
    }
}
