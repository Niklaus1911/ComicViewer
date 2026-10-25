package com.comicviewer.ui.reader

import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.SingletonImageLoader
import coil3.imageLoader
import coil3.request.ImageRequest
import com.comicviewer.R
import com.comicviewer.ComicBook
import com.comicviewer.ComicPage
import com.comicviewer.ComicSource
import com.comicviewer.ReaderBackground
import com.comicviewer.ReaderPageFitMode
import com.comicviewer.ReaderPageMode
import com.comicviewer.ReaderPreferences
import com.comicviewer.ReaderReadingDirection
import com.comicviewer.coercePageIndex
import com.comicviewer.comicPageImageCacheKey
import com.comicviewer.resolvedReaderPreferences
import com.comicviewer.billing.ProBillingState
import com.comicviewer.ui.common.rememberThrottledAction
import com.comicviewer.ui.library.ProUpgradeDialog
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val MIN_PAGE_SCALE = 1f
private const val MAX_PAGE_SCALE = 5f
internal const val READER_SWIPE_DISTANCE_FRACTION = 0.14f
private const val SWIPE_VELOCITY_THRESHOLD_DP_PER_SECOND = 700f
private const val SWIPE_RELEASE_ANIMATION_DURATION_MS = 380
private const val EDGE_SWIPE_RESISTANCE = 0.28f

internal enum class ReaderSwipeDirection {
    Previous,
    Next,
}

internal fun readerPageGridInitialItemIndex(
    pageIndices: List<Int>,
    currentPageIndex: Int,
): Int {
    if (pageIndices.isEmpty()) return 0
    val matchingItemIndex = pageIndices.indexOf(currentPageIndex)
    return if (matchingItemIndex >= 0) {
        matchingItemIndex
    } else {
        currentPageIndex.coerceIn(0, pageIndices.lastIndex)
    }
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

internal fun readerSpreadVisualPageIndices(
    pageIndex: Int,
    pageCount: Int,
    readingDirection: ReaderReadingDirection,
): List<Int> {
    val indices = readerSpreadPageIndices(pageIndex, pageCount)
    return if (readingDirection == ReaderReadingDirection.RightToLeft && indices.size > 1) {
        indices.reversed()
    } else {
        indices
    }
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

internal fun readerSwipeAdjacentDirection(
    activeSwipeDirection: ReaderSwipeDirection?,
    offsetX: Float,
): ReaderSwipeDirection? =
    activeSwipeDirection ?: readerSwipeDirectionForOffset(offsetX)

private fun adjacentPageTranslationX(
    offsetX: Float,
    viewportWidth: Float,
    direction: ReaderSwipeDirection,
): Float = when (direction) {
    ReaderSwipeDirection.Previous -> offsetX - viewportWidth
    ReaderSwipeDirection.Next -> offsetX + viewportWidth
}

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

private data class RememberedPagePainter(
    val imageUri: String,
    val painter: Painter,
)

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

/**
 * The reader shell: window behavior, shared controls and sheets, and either the paged reader or the
 * vertical scroll reader depending on the reading mode.
 */
@Composable
fun ComicReaderScreen(
    book: ComicBook,
    pageIndex: Int,
    readerPreferences: ReaderPreferences,
    isProUnlocked: Boolean,
    proBillingState: ProBillingState,
    isDebugBuild: Boolean,
    debugProUnlocked: Boolean,
    onReaderPreferencesChanged: (ReaderPreferences) -> Unit,
    onBuyPro: () -> Unit,
    onRestorePro: () -> Unit,
    onDebugProUnlockedChanged: (Boolean) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPageSelected: (Int) -> Unit,
    onCloseComic: () -> Unit,
    modifier: Modifier = Modifier,
    favorite: Boolean? = null,
    onToggleFavorite: () -> Unit = {},
) {
    val activeReaderPreferences = resolvedReaderPreferences(
        readerPreferences = readerPreferences,
        isProUnlocked = isProUnlocked,
    )
    var controlsVisible by remember(book) { mutableStateOf(true) }
    var showReaderSettings by remember(book) { mutableStateOf(false) }
    var showPageGrid by remember(book) { mutableStateOf(false) }
    var showProDialog by remember(book) { mutableStateOf(false) }

    ReaderWindowEffect(preferences = activeReaderPreferences)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(activeReaderPreferences.background.readerBackgroundColor()),
    ) {
        if (activeReaderPreferences.pageMode == ReaderPageMode.Vertical) {
            VerticalComicReader(
                book = book,
                pageIndex = pageIndex,
                preferences = activeReaderPreferences,
                controlsVisible = controlsVisible,
                onControlsVisibleChange = { controlsVisible = it },
                onPageSelected = onPageSelected,
                onCloseComic = onCloseComic,
                onOpenSettings = { showReaderSettings = true },
                onOpenGrid = { showPageGrid = true },
                favorite = favorite,
                onToggleFavorite = { if (isProUnlocked) onToggleFavorite() else showProDialog = true },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            PagedComicReader(
                book = book,
                pageIndex = pageIndex,
                activeReaderPreferences = activeReaderPreferences,
                controlsVisible = controlsVisible,
                onControlsVisibleChange = { controlsVisible = it },
                onPageSelected = onPageSelected,
                onCloseComic = onCloseComic,
                onOpenSettings = { showReaderSettings = true },
                onOpenGrid = { showPageGrid = true },
                favorite = favorite,
                onToggleFavorite = { if (isProUnlocked) onToggleFavorite() else showProDialog = true },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (showReaderSettings) {
            ReaderSettingsDialog(
                readerPreferences = readerPreferences,
                activeReaderPreferences = activeReaderPreferences,
                isProUnlocked = isProUnlocked,
                onReaderPreferencesChanged = onReaderPreferencesChanged,
                onRequestPro = { showProDialog = true },
                onDismiss = { showReaderSettings = false },
                livePreview = true,
            )
        }

        if (showPageGrid) {
            ReaderPageGrid(
                book = book,
                currentPageIndex = coercePageIndex(pageIndex, book.pages.size),
                onPageSelected = { selected ->
                    onPageSelected(selected)
                    showPageGrid = false
                },
                onDismiss = { showPageGrid = false },
            )
        }

        if (showProDialog) {
            ProUpgradeDialog(
                billingState = proBillingState,
                onBuyPro = onBuyPro,
                onRestorePro = onRestorePro,
                isDebugBuild = isDebugBuild,
                debugProUnlocked = debugProUnlocked,
                onDebugProUnlockedChanged = onDebugProUnlockedChanged,
                onDismiss = { showProDialog = false },
            )
        }
    }
}

@Composable
private fun PagedComicReader(
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
    val currentRequest = remember(book.source, visualPage.imageUri, visualPage.relativePath) {
        visualPage.imageRequest(context = context, source = book.source)
    }
    val painter = rememberAsyncImagePainter(
        model = currentRequest,
        imageLoader = imageLoader,
        contentScale = ContentScale.Fit,
    )
    val painterState by painter.state.collectAsState()
    val secondPainter = rememberAsyncImagePainter(
        model = remember(book.source, secondSpreadPage?.imageUri, secondSpreadPage?.relativePath) {
            secondSpreadPage?.imageRequest(context = context, source = book.source)
        },
        imageLoader = imageLoader,
        contentScale = ContentScale.Fit,
    )
    val secondPainterState by secondPainter.state.collectAsState()
    var lastSuccessfulPainter by remember(book) { mutableStateOf<RememberedPagePainter?>(null) }
    var primaryPageLoadError by remember(visualPage.imageUri) { mutableStateOf<String?>(null) }
    var secondaryPageLoadError by remember(secondSpreadPage?.imageUri) { mutableStateOf<String?>(null) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
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
    val adjacentRequest = remember(book.source, adjacentPage?.imageUri, adjacentPage?.relativePath) {
        adjacentPage?.imageRequest(context = context, source = book.source)
    }
    val adjacentPainter = rememberAsyncImagePainter(
        model = adjacentRequest,
        imageLoader = imageLoader,
        contentScale = ContentScale.Fit,
    )
    val adjacentSecondPainter = rememberAsyncImagePainter(
        model = remember(book.source, adjacentSecondPage?.imageUri, adjacentSecondPage?.relativePath) {
            adjacentSecondPage?.imageRequest(context = context, source = book.source)
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

    LaunchedEffect(book.pages, visualPage.index, imageLoader) {
        book.pages.preloadAround(
            currentIndex = visualPage.index,
            context = context,
            source = book.source,
            imageLoader = imageLoader,
        )
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

    val primaryIntrinsicSize = (painterState as? AsyncImagePainter.State.Success)
        ?.painter
        ?.intrinsicSize
        ?: Size.Unspecified
    val secondaryIntrinsicSize = (secondPainterState as? AsyncImagePainter.State.Success)
        ?.painter
        ?.intrinsicSize
        ?: Size.Unspecified
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
    val horizontalSwipeEnabled = readerHorizontalSwipeEnabled(
        pageWidth = layoutPageWidth,
        pageHeight = layoutPageHeight,
        viewportWidth = viewportSize.width,
        viewportHeight = viewportSize.height,
        effectiveScale = effectivePageScale,
        userScale = pageScale,
    )
    val currentHorizontalSwipeEnabled = rememberUpdatedState(horizontalSwipeEnabled)

    LaunchedEffect(
        visualPage.imageUri,
        secondSpreadPage?.imageUri,
        viewportSize,
        effectivePageScale,
    ) {
        pageOffset = pageOffset.clampedFor(
            pageWidth = layoutPageWidth,
            pageHeight = layoutPageHeight,
            scale = effectivePageScale,
            viewportSize = viewportSize,
        )
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
                    layoutPageWidth,
                    layoutPageHeight,
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
                            pageWidth = layoutPageWidth,
                            pageHeight = layoutPageHeight,
                            scale = readerEffectivePageScale(
                                pageFitMode = activeReaderPreferences.pageFitMode,
                                pageWidth = layoutPageWidth,
                                pageHeight = layoutPageHeight,
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
                        firstDescription = targetPage.displayName,
                        secondDescription = adjacentSecondPage.displayName,
                        readingDirection = activeReaderPreferences.readingDirection,
                        scale = MIN_PAGE_SCALE,
                        offset = Offset.Zero,
                        swipeOffsetX = translationX,
                    )
                } else {
                    SwipeTargetPageImage(
                        painter = adjacentPainter,
                        contentDescription = targetPage.displayName,
                        translationX = translationX,
                    )
                }
            }

            val fallbackPainter = lastSuccessfulPainter
                ?.takeIf { remembered -> remembered.imageUri == visualPage.imageUri.toString() }
                ?.painter
            val displayedPainter = if (painterState is AsyncImagePainter.State.Success) painter else fallbackPainter
            displayedPainter?.let { pagePainter ->
                if (secondSpreadPage != null) {
                    ZoomableSpreadImages(
                        firstPainter = pagePainter,
                        secondPainter = secondPainter,
                        firstDescription = visualPage.displayName,
                        secondDescription = secondSpreadPage.displayName,
                        readingDirection = activeReaderPreferences.readingDirection,
                        scale = effectivePageScale,
                        offset = pageOffset,
                        swipeOffsetX = swipeOffsetX,
                    )
                } else {
                    ZoomablePageImage(
                        painter = pagePainter,
                        contentDescription = visualPage.displayName,
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

private fun List<ComicPage>.preloadAround(
    currentIndex: Int,
    context: Context,
    source: ComicSource,
    imageLoader: coil3.ImageLoader,
) {
    listOf(currentIndex + 1, currentIndex + 2, currentIndex - 1, currentIndex - 2)
        .filter { index -> index in indices }
        .forEach { index ->
            imageLoader.enqueue(get(index).imageRequest(context = context, source = source))
        }
}

internal fun ComicPage.imageRequest(context: Context, source: ComicSource): ImageRequest {
    val cacheKey = comicPageImageCacheKey(source = source, page = this)
    return ImageRequest.Builder(context)
        .data(imageUri)
        .memoryCacheKey(cacheKey)
        .diskCacheKey(cacheKey)
        .build()
}

private fun Offset.clampedFor(
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

private fun fitPageSizeInViewport(
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

internal fun ReaderBackground.readerBackgroundColor(): Color = when (this) {
    ReaderBackground.Black -> Color.Black
    ReaderBackground.DarkGray -> Color(0xFF202124)
    ReaderBackground.Sepia -> Color(0xFF2C2418)
    ReaderBackground.White -> Color.White
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderPageGrid(
    book: ComicBook,
    currentPageIndex: Int,
    onPageSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialGridItemIndex = remember(book.pages, currentPageIndex) {
        readerPageGridInitialItemIndex(
            pageIndices = book.pages.map(ComicPage::index),
            currentPageIndex = currentPageIndex,
        )
    }
    val compactGridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = initialGridItemIndex,
    )
    val fullScreenGridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = initialGridItemIndex,
    )
    val modalSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val fullScreenVisibility = remember(book) { MutableTransitionState(false) }
    var fullScreenDialogComposed by remember(book) { mutableStateOf(false) }
    var dismissAllAfterAnimation by remember(book) { mutableStateOf(false) }

    fun openFullScreen() {
        fullScreenGridState.requestScrollToItem(
            index = compactGridState.firstVisibleItemIndex,
            scrollOffset = compactGridState.firstVisibleItemScrollOffset,
        )
        dismissAllAfterAnimation = false
        fullScreenDialogComposed = true
        fullScreenVisibility.targetState = true
    }

    fun collapseToSheet() {
        dismissAllAfterAnimation = false
        fullScreenVisibility.targetState = false
    }

    fun closeFromFullScreen() {
        dismissAllAfterAnimation = true
        fullScreenVisibility.targetState = false
    }

    LaunchedEffect(
        fullScreenDialogComposed,
        fullScreenVisibility.currentState,
        fullScreenVisibility.isIdle,
    ) {
        if (
            fullScreenDialogComposed &&
            fullScreenVisibility.isIdle &&
            !fullScreenVisibility.currentState
        ) {
            fullScreenDialogComposed = false
            if (dismissAllAfterAnimation) {
                dismissAllAfterAnimation = false
                onDismiss()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (!fullScreenDialogComposed) onDismiss()
        },
        sheetState = modalSheetState,
        sheetGesturesEnabled = !fullScreenDialogComposed,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        ReaderPageGridContent(
            book = book,
            currentPageIndex = currentPageIndex,
            gridState = compactGridState,
            fullScreen = false,
            onPageSelected = onPageSelected,
            onToggleFullScreen = ::openFullScreen,
            onDismiss = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }

    if (fullScreenDialogComposed) {
        ReaderPageGridFullScreenDialog(
            book = book,
            currentPageIndex = currentPageIndex,
            gridState = fullScreenGridState,
            visibleState = fullScreenVisibility,
            onPageSelected = onPageSelected,
            onCollapse = ::collapseToSheet,
            onDismiss = ::closeFromFullScreen,
        )
    }
}

@Composable
private fun ReaderPageGridFullScreenDialog(
    book: ComicBook,
    currentPageIndex: Int,
    gridState: LazyGridState,
    visibleState: MutableTransitionState<Boolean>,
    onPageSelected: (Int) -> Unit,
    onCollapse: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onCollapse,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val view = LocalView.current
        val window = (view.parent as? DialogWindowProvider)?.window
        DisposableEffect(window, view) {
            val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
            val previousSystemBarsBehavior = controller?.systemBarsBehavior
            window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.statusBars())
            onDispose {
                previousSystemBarsBehavior?.let { controller.systemBarsBehavior = it }
            }
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                visibleState = visibleState,
                enter = fadeIn(
                    animationSpec = tween(
                        durationMillis = 220,
                        easing = LinearOutSlowInEasing,
                    ),
                ) + slideInVertically(
                    animationSpec = tween(
                        durationMillis = 220,
                        easing = LinearOutSlowInEasing,
                    ),
                    initialOffsetY = { height -> height / 8 },
                ) + scaleIn(
                    animationSpec = tween(
                        durationMillis = 220,
                        easing = LinearOutSlowInEasing,
                    ),
                    initialScale = 0.96f,
                    transformOrigin = TransformOrigin(0.5f, 1f),
                ),
                exit = fadeOut(
                    animationSpec = tween(
                        durationMillis = 180,
                        easing = FastOutLinearInEasing,
                    ),
                ) + slideOutVertically(
                    animationSpec = tween(
                        durationMillis = 180,
                        easing = FastOutLinearInEasing,
                    ),
                    targetOffsetY = { height -> height / 8 },
                ) + scaleOut(
                    animationSpec = tween(
                        durationMillis = 180,
                        easing = FastOutLinearInEasing,
                    ),
                    targetScale = 0.96f,
                    transformOrigin = TransformOrigin(0.5f, 1f),
                ),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    ReaderPageGridContent(
                        book = book,
                        currentPageIndex = currentPageIndex,
                        gridState = gridState,
                        fullScreen = true,
                        onPageSelected = onPageSelected,
                        onToggleFullScreen = onCollapse,
                        onDismiss = onDismiss,
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderPageGridContent(
    book: ComicBook,
    currentPageIndex: Int,
    gridState: LazyGridState,
    fullScreen: Boolean,
    onPageSelected: (Int) -> Unit,
    onToggleFullScreen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val imageLoader = remember(context) { SingletonImageLoader.get(context) }
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (fullScreen) 8.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.reader_page_grid),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onToggleFullScreen) {
                Icon(
                    imageVector = if (fullScreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    contentDescription = stringResource(
                        if (fullScreen) {
                            R.string.reader_page_grid_collapse
                        } else {
                            R.string.reader_page_grid_expand
                        },
                    ),
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, stringResource(R.string.cancel))
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(if (fullScreen) 100.dp else 88.dp),
            state = gridState,
            modifier = if (fullScreen) {
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
            } else {
                Modifier.fillMaxWidth()
            },
            contentPadding = PaddingValues(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(if (fullScreen) 8.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(book.pages, key = ComicPage::index) { page ->
                Column(
                    modifier = Modifier.clickable { onPageSelected(page.index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.72f),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        border = if (page.index == currentPageIndex) {
                            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            null
                        },
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(
                                model = page.imageRequest(context, book.source),
                                imageLoader = imageLoader,
                            ),
                            contentDescription = page.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = (page.index + 1).toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (page.index == currentPageIndex) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

internal tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
