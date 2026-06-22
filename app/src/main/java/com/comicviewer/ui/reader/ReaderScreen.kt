package com.comicviewer.ui.reader

import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.SingletonImageLoader
import coil3.imageLoader
import coil3.request.ImageRequest
import com.comicviewer.R
import com.comicviewer.ComicBook
import com.comicviewer.ComicPage
import com.comicviewer.ComicSource
import com.comicviewer.coercePageIndex
import com.comicviewer.comicPageImageCacheKey
import com.comicviewer.ui.common.rememberThrottledAction
import com.comicviewer.ui.common.rememberThrottledPageAction
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private const val MIN_PAGE_SCALE = 1f
private const val MAX_PAGE_SCALE = 5f
private const val SWIPE_PAGE_ANIMATION_MS = 190
private const val SWIPE_DISTANCE_FRACTION = 0.18f

@Composable
fun LoadingComicScreen(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.loading_pages),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
fun ErrorComicScreen(
    message: String,
    onBackToBrowser: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val throttledBack = rememberThrottledAction(action = onBackToBrowser)
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.open_comic_error_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(28.dp))
            Button(
                onClick = throttledBack,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.back_to_files))
            }
        }
    }
}

@Composable
fun ComicReaderScreen(
    book: ComicBook,
    pageIndex: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPageSelected: (Int) -> Unit,
    onCloseComic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val imageLoader = remember(context) { SingletonImageLoader.get(context) }
    val currentIndex = coercePageIndex(pageIndex, book.pages.size)
    val page = book.pages[currentIndex]
    val currentRequest = remember(book.source, page.imageUri, page.relativePath) {
        page.imageRequest(context = context, source = book.source)
    }
    val painter = rememberAsyncImagePainter(
        model = currentRequest,
        imageLoader = imageLoader,
        contentScale = ContentScale.Fit,
    )
    val painterState by painter.state.collectAsState()
    var lastSuccessfulPainter by remember(book) { mutableStateOf<androidx.compose.ui.graphics.painter.Painter?>(null) }
    var pageLoadError by remember(page.imageUri) { mutableStateOf<String?>(null) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var pageScale by remember(page.imageUri) { mutableFloatStateOf(MIN_PAGE_SCALE) }
    var pageOffset by remember(page.imageUri) { mutableStateOf(Offset.Zero) }
    var controlsVisible by remember(book) { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val swipeOffset = remember { Animatable(0f) }
    var isSwipeAnimating by remember { mutableStateOf(false) }
    var swipeDirection by remember { mutableStateOf<SwipeDirection?>(null) }
    var swipeTargetIndex by remember { mutableStateOf<Int?>(null) }
    val swipeTargetPage = swipeTargetIndex?.let(book.pages::getOrNull)
    val swipeTargetRequest = remember(book.source, swipeTargetPage?.imageUri, swipeTargetPage?.relativePath) {
        swipeTargetPage?.imageRequest(context = context, source = book.source)
    }
    val swipeTargetPainter = rememberAsyncImagePainter(
        model = swipeTargetRequest,
        imageLoader = imageLoader,
        contentScale = ContentScale.Fit,
    )

    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        if (window == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(window, view)
            val previousSystemBarsBehavior = controller.systemBarsBehavior
            val hadKeepScreenOn = window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.statusBars())

            onDispose {
                controller.show(WindowInsetsCompat.Type.statusBars())
                controller.systemBarsBehavior = previousSystemBarsBehavior
                if (!hadKeepScreenOn) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
    }

    fun animateSwipePage(direction: SwipeDirection) {
        if (isSwipeAnimating || viewportSize.width <= 0) return
        val canMove = when (direction) {
            SwipeDirection.Previous -> currentIndex > 0
            SwipeDirection.Next -> currentIndex < book.pages.lastIndex
        }
        if (!canMove) return
        val targetIndex = when (direction) {
            SwipeDirection.Previous -> currentIndex - 1
            SwipeDirection.Next -> currentIndex + 1
        }

        scope.launch {
            isSwipeAnimating = true
            swipeDirection = direction
            swipeTargetIndex = targetIndex
            try {
                val targetOffset = when (direction) {
                    SwipeDirection.Previous -> viewportSize.width.toFloat()
                    SwipeDirection.Next -> -viewportSize.width.toFloat()
                }
                swipeOffset.snapTo(0f)
                withFrameNanos { }
                swipeOffset.animateTo(
                    targetValue = targetOffset,
                    animationSpec = tween(
                        durationMillis = SWIPE_PAGE_ANIMATION_MS,
                        easing = FastOutSlowInEasing,
                    ),
                )
                when (direction) {
                    SwipeDirection.Previous -> onPrevious()
                    SwipeDirection.Next -> onNext()
                }
            } finally {
                swipeOffset.snapTo(0f)
                swipeDirection = null
                swipeTargetIndex = null
                isSwipeAnimating = false
            }
        }
    }

    LaunchedEffect(book.pages, currentIndex, imageLoader) {
        book.pages.preloadAround(
            currentIndex = currentIndex,
            context = context,
            source = book.source,
            imageLoader = imageLoader,
        )
    }

    LaunchedEffect(painterState, page.imageUri) {
        when (val state = painterState) {
            is AsyncImagePainter.State.Success -> {
                lastSuccessfulPainter = state.painter
                pageLoadError = null
            }

            is AsyncImagePainter.State.Error -> {
                pageLoadError = context.getString(R.string.page_image_error, page.displayName)
            }

            else -> Unit
        }
    }

    LaunchedEffect(page.imageUri, viewportSize, pageScale) {
        pageOffset = pageOffset.clampedFor(page = page, scale = pageScale, viewportSize = viewportSize)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clipToBounds()
                .onSizeChanged { viewportSize = it }
                .pointerInput(page.imageUri, viewportSize, controlsVisible, pageScale, isSwipeAnimating) {
                    detectPageGestures(
                        swipingEnabled = pageScale == MIN_PAGE_SCALE && !isSwipeAnimating,
                        onTap = { tapOffset ->
                            val tapZoneWidth = size.width / 3f
                            if (tapOffset.x < tapZoneWidth) {
                                onPrevious()
                            } else if (tapOffset.x > tapZoneWidth * 2f) {
                                onNext()
                            } else {
                                controlsVisible = !controlsVisible
                            }
                        },
                        onSwipe = ::animateSwipePage,
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
                            page = page,
                            scale = nextScale,
                            viewportSize = viewportSize,
                        )
                    }
                },
        ) {
            val activeSwipeDirection = swipeDirection
            val targetPage = swipeTargetPage
            if (activeSwipeDirection != null && targetPage != null) {
                SwipeTargetPageImage(
                    painter = swipeTargetPainter,
                    contentDescription = targetPage.displayName,
                    swipeOffsetX = swipeOffset.value,
                    viewportWidth = viewportSize.width.toFloat(),
                    direction = activeSwipeDirection,
                )
            }

            if (!isSwipeAnimating) {
                lastSuccessfulPainter?.let { cachedPainter ->
                    ZoomablePageImage(
                        painter = cachedPainter,
                        contentDescription = null,
                        scale = pageScale,
                        offset = pageOffset,
                    )
                }
            }

            if (painterState is AsyncImagePainter.State.Success) {
                ZoomablePageImage(
                    painter = painter,
                    contentDescription = page.displayName,
                    scale = pageScale,
                    offset = pageOffset,
                    swipeOffsetX = swipeOffset.value,
                )
            }
        }

        pageLoadError?.let { message ->
            PageImageError(
                message = message,
                relativePath = page.relativePath,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (controlsVisible) {
            ReaderTopBar(
                title = book.title,
                pageText = "${currentIndex + 1} / ${book.pages.size}",
                onCloseComic = onCloseComic,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .systemBarsPadding()
                    .padding(12.dp),
            )

            ReaderPageScrubber(
                currentPageIndex = currentIndex,
                pageCount = book.pages.size,
                onPageSelected = onPageSelected,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .systemBarsPadding()
                    .padding(12.dp),
            )
        }
    }
}

@Composable
fun ZoomablePageImage(
    painter: androidx.compose.ui.graphics.painter.Painter,
    contentDescription: String?,
    scale: Float,
    offset: Offset,
    swipeOffsetX: Float = 0f,
    modifier: Modifier = Modifier,
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
private fun SwipeTargetPageImage(
    painter: androidx.compose.ui.graphics.painter.Painter,
    contentDescription: String?,
    swipeOffsetX: Float,
    viewportWidth: Float,
    direction: SwipeDirection,
) {
    val targetTranslationX = when (direction) {
        SwipeDirection.Previous -> swipeOffsetX - viewportWidth
        SwipeDirection.Next -> swipeOffsetX + viewportWidth
    }
    ZoomablePageImage(
        painter = painter,
        contentDescription = contentDescription,
        scale = MIN_PAGE_SCALE,
        offset = Offset.Zero,
        swipeOffsetX = targetTranslationX,
    )
}

private enum class SwipeDirection {
    Previous,
    Next,
}

private suspend fun PointerInputScope.detectPageGestures(
    swipingEnabled: Boolean,
    onTap: (Offset) -> Unit,
    onSwipe: (SwipeDirection) -> Unit,
    onTransform: (centroid: Offset, pan: Offset, zoom: Float) -> Unit,
) {
    awaitEachGesture {
        val firstDown = awaitFirstDown(requireUnconsumed = false)
        val startPosition = firstDown.position
        val touchSlop = viewConfiguration.touchSlop
        var event: PointerEvent
        var pointerCountExceededTap = false
        var movedPastTapSlop = false
        var pastTransformSlop = false
        var accumulatedPan = Offset.Zero
        var accumulatedZoom = 1f
        val swipeDistanceThreshold = max(touchSlop * 4f, size.width * SWIPE_DISTANCE_FRACTION)

        do {
            event = awaitPointerEvent()
            if (event.changes.count { it.pressed } > 1) {
                pointerCountExceededTap = true
            }

            event.changes
                .firstOrNull { it.id == firstDown.id }
                ?.let { firstPointer ->
                    if ((firstPointer.position - startPosition).getDistance() > touchSlop) {
                        movedPastTapSlop = true
                    }
                }

            val zoomChange = event.calculateZoom()
            val panChange = event.calculatePan()

            if (!pastTransformSlop) {
                accumulatedZoom *= zoomChange
                accumulatedPan += panChange

                val centroidSize = event.calculateCentroidSize(useCurrent = false)
                val zoomMotion = abs(1f - accumulatedZoom) * centroidSize
                val panMotion = accumulatedPan.getDistance()
                val horizontalSwipeMotion = abs(accumulatedPan.x)
                val verticalSwipeMotion = abs(accumulatedPan.y)
                val horizontalSwipeCandidate = swipingEnabled &&
                    !pointerCountExceededTap &&
                    zoomMotion <= touchSlop &&
                    horizontalSwipeMotion > verticalSwipeMotion * 1.4f
                val shouldSwipe = horizontalSwipeCandidate &&
                    horizontalSwipeMotion >= swipeDistanceThreshold &&
                    panMotion > touchSlop

                if (shouldSwipe) {
                    event.changes.forEach { change ->
                        if (change.positionChanged()) {
                            change.consume()
                        }
                    }
                    onSwipe(if (accumulatedPan.x > 0f) SwipeDirection.Previous else SwipeDirection.Next)
                    return@awaitEachGesture
                }

                if (horizontalSwipeCandidate) {
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

        if (!pointerCountExceededTap && !movedPastTapSlop && !pastTransformSlop) {
            onTap(startPosition)
        }
    }
}

private fun List<ComicPage>.preloadAround(
    currentIndex: Int,
    context: Context,
    source: ComicSource,
    imageLoader: coil3.ImageLoader,
) {
    listOf(currentIndex + 1, currentIndex + 2, currentIndex - 1)
        .filter { index -> index in indices }
        .forEach { index ->
            imageLoader.enqueue(get(index).imageRequest(context = context, source = source))
        }
}

private fun ComicPage.imageRequest(context: Context, source: ComicSource): ImageRequest {
    val cacheKey = comicPageImageCacheKey(source = source, page = this)
    return ImageRequest.Builder(context)
        .data(imageUri)
        .memoryCacheKey(cacheKey)
        .diskCacheKey(cacheKey)
        .build()
}

private fun Offset.clampedFor(
    page: ComicPage,
    scale: Float,
    viewportSize: IntSize,
): Offset {
    if (viewportSize.width <= 0 || viewportSize.height <= 0) return Offset.Zero

    val fitSize = page.fitSizeIn(viewportSize)
    val maxX = max(0f, (fitSize.width * scale - viewportSize.width) / 2f)
    val maxY = max(0f, (fitSize.height * scale - viewportSize.height) / 2f)

    return Offset(
        x = x.coerceIn(-maxX, maxX),
        y = y.coerceIn(-maxY, maxY),
    )
}

private fun ComicPage.fitSizeIn(viewportSize: IntSize): Size {
    val viewportWidth = viewportSize.width.toFloat()
    val viewportHeight = viewportSize.height.toFloat()
    if (viewportWidth <= 0f || viewportHeight <= 0f || width <= 0 || height <= 0) {
        return Size(viewportWidth, viewportHeight)
    }

    val imageAspectRatio = width.toFloat() / height.toFloat()
    val viewportAspectRatio = viewportWidth / viewportHeight
    return if (imageAspectRatio > viewportAspectRatio) {
        Size(width = viewportWidth, height = viewportWidth / imageAspectRatio)
    } else {
        Size(width = viewportHeight * imageAspectRatio, height = viewportHeight)
    }
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

@Composable
fun ReaderTopBar(
    title: String,
    pageText: String,
    onCloseComic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Black.copy(alpha = 0.58f),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 6.dp, end = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pageText,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.78f),
                )
            }
            TextButton(onClick = onCloseComic) {
                Text(
                    text = stringResource(R.string.reader_close_file),
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
fun ReaderPageScrubber(
    currentPageIndex: Int,
    pageCount: Int,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clampedPageIndex = coercePageIndex(currentPageIndex, pageCount)
    var scrubberValue by remember(clampedPageIndex, pageCount) {
        mutableFloatStateOf(clampedPageIndex.toFloat())
    }
    val targetPageIndex = coercePageIndex(scrubberValue.roundToInt(), pageCount)
    val sliderMax = max(pageCount - 1, 0).toFloat()

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Black.copy(alpha = 0.58f),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = if (pageCount <= 0) {
                    stringResource(R.string.page_progress_spaced, 1, 1)
                } else {
                    stringResource(R.string.page_progress_spaced, targetPageIndex + 1, pageCount)
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.86f),
            )
            if (pageCount > 1) {
                Slider(
                    value = scrubberValue.coerceIn(0f, sliderMax),
                    onValueChange = { value -> scrubberValue = value },
                    onValueChangeFinished = {
                        onPageSelected(coercePageIndex(scrubberValue.roundToInt(), pageCount))
                    },
                    valueRange = 0f..sliderMax,
                    steps = max(pageCount - 2, 0),
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
