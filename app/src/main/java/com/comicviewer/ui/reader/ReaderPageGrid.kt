package com.comicviewer.ui.reader

import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.SingletonImageLoader
import coil3.compose.rememberAsyncImagePainter
import com.comicviewer.ComicBook
import com.comicviewer.ComicPage
import com.comicviewer.R

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
    val currentPageState = stringResource(R.string.reader_current_page)
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
                    modifier = Modifier
                        .clickable { onPageSelected(page.index) }
                        .semantics {
                            if (page.index == currentPageIndex) stateDescription = currentPageState
                        },
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
                            contentDescription = page.accessibilityLabel(book.pages.size),
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
