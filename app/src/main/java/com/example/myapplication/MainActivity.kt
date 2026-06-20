package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil3.SingletonImageLoader
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.example.myapplication.ui.theme.MyApplicationTheme
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val MIN_PAGE_SCALE = 1f
private const val MAX_PAGE_SCALE = 5f
private const val SOFT_ANIMATION_DURATION_MS = 180
private const val TAP_THROTTLE_MS = 350L

enum class AppTab {
    Library,
    Browse,
}

@Composable
fun rememberThrottledAction(
    throttleMs: Long = TAP_THROTTLE_MS,
    action: () -> Unit,
): () -> Unit {
    var lastClickAt by remember { mutableStateOf(0L) }
    return remember(throttleMs, action) {
        {
            val now = System.currentTimeMillis()
            if (now - lastClickAt >= throttleMs) {
                lastClickAt = now
                action()
            }
        }
    }
}

@Composable
fun rememberThrottledPageAction(
    throttleMs: Long = TAP_THROTTLE_MS,
    action: (Int) -> Unit,
): (Int) -> Unit {
    var lastClickAt by remember { mutableStateOf(0L) }
    return remember(throttleMs, action) {
        { value ->
            val now = System.currentTimeMillis()
            if (now - lastClickAt >= throttleMs) {
                lastClickAt = now
                action(value)
            }
        }
    }
}

fun shouldAcceptThrottledAction(
    nowMs: Long,
    lastAcceptedAtMs: Long,
    throttleMs: Long = TAP_THROTTLE_MS,
): Boolean = nowMs - lastAcceptedAtMs >= throttleMs

private fun Modifier.softPress(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val targetScale = if (enabled && pressed) 0.98f else 1f
    val targetAlpha = if (enabled && pressed) 0.82f else 1f
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(SOFT_ANIMATION_DURATION_MS),
        label = "softPressScale",
    )
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(SOFT_ANIMATION_DURATION_MS),
        label = "softPressAlpha",
    )

    graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.alpha = alpha
    }
}

private fun softContentTransform(): ContentTransform =
    (fadeIn(tween(SOFT_ANIMATION_DURATION_MS)) + scaleIn(
        initialScale = 0.98f,
        animationSpec = tween(SOFT_ANIMATION_DURATION_MS),
    )).togetherWith(
        fadeOut(tween(SOFT_ANIMATION_DURATION_MS)) + scaleOut(
            targetScale = 1.02f,
            animationSpec = tween(SOFT_ANIMATION_DURATION_MS),
        ),
    )

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                ComicViewerApp()
            }
        }
    }
}

@Composable
fun ComicViewerApp() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    var hasFileAccess by remember { mutableStateOf(hasFileManagerAccess()) }
    var currentDirectoryPath by rememberSaveable { mutableStateOf(defaultBrowserRootPath()) }
    var uiState by rememberSaveable(stateSaver = ReaderUiStateSaver) {
        mutableStateOf<ReaderUiState>(ReaderUiState.Empty)
    }
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.Library.name) }
    var isAddingToLibrary by rememberSaveable { mutableStateOf(false) }
    var recentComics by remember { mutableStateOf(loadRecentComics(appContext)) }
    var libraryComics by remember { mutableStateOf(loadLibraryComics(appContext)) }
    var loadJob by remember { mutableStateOf<Job?>(null) }
    val browserRootPath = remember { defaultBrowserRootPath() }
    val currentTab = AppTab.valueOf(selectedTab)

    val fileAccessLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        hasFileAccess = hasFileManagerAccess()
    }

    DisposableEffect(context) {
        val activity = context as? ComponentActivity
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasFileAccess = hasFileManagerAccess()
            }
        }
        activity?.lifecycle?.addObserver(observer)

        onDispose {
            activity?.lifecycle?.removeObserver(observer)
        }
    }

    fun showError(throwable: Throwable) {
        uiState = ReaderUiState.Error(
            throwable.message?.takeIf { it.isNotBlank() }
                ?: "Non e stato possibile aprire questo comic.",
        )
    }

    fun openFileAccessSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val appSettingsIntent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            )
            runCatching {
                fileAccessLauncher.launch(appSettingsIntent)
            }.onFailure {
                fileAccessLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        }
    }

    fun persistRecentComics(nextRecentComics: List<RecentComic>) {
        recentComics = nextRecentComics
        saveRecentComics(appContext, nextRecentComics)
    }

    fun persistLibraryComics(nextLibraryComics: List<LibraryComic>) {
        libraryComics = nextLibraryComics
        saveLibraryComics(appContext, nextLibraryComics)
    }

    fun rememberOpenedComic(book: ComicBook, pageIndex: Int) {
        val recentComic = recentComicFor(
            book = book,
            pageIndex = pageIndex,
            lastOpenedAt = System.currentTimeMillis(),
        ) ?: return
        persistRecentComics(upsertRecentComic(recentComics, recentComic))
    }

    fun updateRecentPage(ready: ReaderUiState.Ready, pageIndex: Int) {
        val recentComic = recentComicFor(
            book = ready.book,
            pageIndex = pageIndex,
            lastOpenedAt = 0L,
        ) ?: return
        persistRecentComics(
            updateRecentComicPage(
                recentComics = recentComics,
                sourceType = recentComic.sourceType,
                path = recentComic.path,
                pageIndex = pageIndex,
                pageCount = ready.book.pages.size,
            ),
        )
    }

    fun showReadyComic(book: ComicBook, pageIndex: Int) {
        val clampedPageIndex = coercePageIndex(pageIndex, book.pages.size)
        rememberOpenedComic(book, clampedPageIndex)
        uiState = ReaderUiState.Ready(book = book, pageIndex = clampedPageIndex)
    }

    fun openLocalArchive(file: File) {
        loadJob?.cancel()
        uiState = ReaderUiState.Loading
        loadJob = scope.launch {
            runCatching { loadLocalArchiveComic(appContext, file) }
                .onSuccess { book ->
                    showReadyComic(book = book, pageIndex = 0)
                }
                .onFailure { throwable ->
                    if (throwable !is CancellationException) showError(throwable)
                }
        }
    }

    fun openLocalFolder(folder: File) {
        loadJob?.cancel()
        uiState = ReaderUiState.Loading
        loadJob = scope.launch {
            runCatching { loadLocalFolderComic(folder) }
                .onSuccess { book ->
                    showReadyComic(book = book, pageIndex = 0)
                }
                .onFailure { throwable ->
                    if (throwable !is CancellationException) showError(throwable)
                }
        }
    }

    fun openRecentComic(recentComic: RecentComic) {
        loadJob?.cancel()
        uiState = ReaderUiState.Loading
        loadJob = scope.launch {
            runCatching {
                when (recentComic.sourceType) {
                    RecentComicSourceType.LocalArchive -> loadLocalArchiveComic(appContext, File(recentComic.path))
                    RecentComicSourceType.LocalFolder -> loadLocalFolderComic(File(recentComic.path))
                }
            }.onSuccess { book ->
                showReadyComic(book = book, pageIndex = recentComic.lastPageIndex)
            }.onFailure { throwable ->
                if (throwable !is CancellationException) {
                    persistRecentComics(
                        removeRecentComic(
                            recentComics = recentComics,
                            sourceType = recentComic.sourceType,
                            path = recentComic.path,
                        ),
                    )
                    showError(throwable)
                }
            }
        }
    }

    fun openLibraryComic(libraryComic: LibraryComic) {
        val recentComic = libraryComic.matchingRecentComic(recentComics)
        loadJob?.cancel()
        uiState = ReaderUiState.Loading
        loadJob = scope.launch {
            runCatching {
                when (libraryComic.sourceType) {
                    RecentComicSourceType.LocalArchive -> loadLocalArchiveComic(appContext, File(libraryComic.path))
                    RecentComicSourceType.LocalFolder -> loadLocalFolderComic(File(libraryComic.path))
                }
            }.onSuccess { book ->
                showReadyComic(book = book, pageIndex = recentComic?.lastPageIndex ?: 0)
            }.onFailure { throwable ->
                if (throwable !is CancellationException) showError(throwable)
            }
        }
    }

    fun addArchiveToLibrary(file: File) {
        persistLibraryComics(
            upsertLibraryComic(
                libraryComics = libraryComics,
                libraryComic = libraryComicForArchive(file, addedAt = System.currentTimeMillis()),
            ),
        )
        selectedTab = AppTab.Library.name
        isAddingToLibrary = false
    }

    fun addFolderToLibrary(folder: File) {
        persistLibraryComics(
            upsertLibraryComic(
                libraryComics = libraryComics,
                libraryComic = libraryComicForFolder(folder, addedAt = System.currentTimeMillis()),
            ),
        )
        selectedTab = AppTab.Library.name
        isAddingToLibrary = false
    }

    fun removeFromLibrary(libraryComic: LibraryComic) {
        persistLibraryComics(
            removeLibraryComic(
                libraryComics = libraryComics,
                sourceType = libraryComic.sourceType,
                path = libraryComic.path,
            ),
        )
    }

    fun closeComic() {
        loadJob?.cancel()
        clearArchiveCache(appContext)
        uiState = ReaderUiState.Empty
    }

    fun returnToBrowser() {
        loadJob?.cancel()
        clearArchiveCache(appContext)
        uiState = ReaderUiState.Empty
    }

    val browserBackPath = remember(currentDirectoryPath, browserRootPath) {
        browserParentPathFor(
            currentPath = currentDirectoryPath,
            rootPath = browserRootPath,
        )
    }
    val shouldHandleBack = hasFileAccess &&
        (uiState !is ReaderUiState.Empty || isAddingToLibrary || (currentTab == AppTab.Browse && browserBackPath != null))

    BackHandler(enabled = shouldHandleBack) {
        when (uiState) {
            ReaderUiState.Empty -> {
                if (isAddingToLibrary) {
                    isAddingToLibrary = false
                    selectedTab = AppTab.Library.name
                } else if (currentTab == AppTab.Browse && browserBackPath != null) {
                    currentDirectoryPath = browserBackPath
                }
            }

            ReaderUiState.Loading,
            is ReaderUiState.Error,
            is ReaderUiState.Ready,
            -> returnToBrowser()
        }
    }

    if (!hasFileAccess) {
        FileAccessScreen(
            onOpenSettings = ::openFileAccessSettings,
        )
    } else {
        ComicViewerScreen(
            state = uiState,
            selectedTab = currentTab,
            currentDirectoryPath = currentDirectoryPath,
            browserRootPath = browserRootPath,
            libraryComics = libraryComics,
            recentComics = recentComics,
            isAddingToLibrary = isAddingToLibrary,
            onTabSelected = { tab ->
                selectedTab = tab.name
                if (tab != AppTab.Browse) isAddingToLibrary = false
            },
            onStartAddToLibrary = {
                selectedTab = AppTab.Browse.name
                isAddingToLibrary = true
            },
            onDirectoryChange = { path -> currentDirectoryPath = path },
            onOpenLocalArchive = ::openLocalArchive,
            onOpenLocalFolder = ::openLocalFolder,
            onOpenRecentComic = ::openRecentComic,
            onOpenLibraryComic = ::openLibraryComic,
            onAddLocalArchiveToLibrary = ::addArchiveToLibrary,
            onAddLocalFolderToLibrary = ::addFolderToLibrary,
            onRemoveLibraryComic = ::removeFromLibrary,
            onPrevious = {
                val ready = uiState as? ReaderUiState.Ready ?: return@ComicViewerScreen
                val nextPageIndex = coercePageIndex(ready.pageIndex - 1, ready.book.pages.size)
                updateRecentPage(ready, nextPageIndex)
                uiState = ready.copy(pageIndex = nextPageIndex)
            },
            onNext = {
                val ready = uiState as? ReaderUiState.Ready ?: return@ComicViewerScreen
                val nextPageIndex = coercePageIndex(ready.pageIndex + 1, ready.book.pages.size)
                updateRecentPage(ready, nextPageIndex)
                uiState = ready.copy(pageIndex = nextPageIndex)
            },
            onPageSelected = { pageIndex ->
                val ready = uiState as? ReaderUiState.Ready ?: return@ComicViewerScreen
                val nextPageIndex = coercePageIndex(pageIndex, ready.book.pages.size)
                updateRecentPage(ready, nextPageIndex)
                uiState = ready.copy(pageIndex = nextPageIndex)
            },
            onCloseComic = ::closeComic,
        )
    }
}

@Composable
fun ComicViewerScreen(
    state: ReaderUiState,
    selectedTab: AppTab,
    currentDirectoryPath: String,
    browserRootPath: String,
    libraryComics: List<LibraryComic>,
    recentComics: List<RecentComic>,
    isAddingToLibrary: Boolean,
    onTabSelected: (AppTab) -> Unit,
    onStartAddToLibrary: () -> Unit,
    onDirectoryChange: (String) -> Unit,
    onOpenLocalArchive: (File) -> Unit,
    onOpenLocalFolder: (File) -> Unit,
    onOpenRecentComic: (RecentComic) -> Unit,
    onOpenLibraryComic: (LibraryComic) -> Unit,
    onAddLocalArchiveToLibrary: (File) -> Unit,
    onAddLocalFolderToLibrary: (File) -> Unit,
    onRemoveLibraryComic: (LibraryComic) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPageSelected: (Int) -> Unit,
    onCloseComic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state is ReaderUiState.Ready) {
        ComicReaderScreen(
            book = state.book,
            pageIndex = state.pageIndex,
            onPrevious = onPrevious,
            onNext = onNext,
            onPageSelected = onPageSelected,
            onCloseComic = onCloseComic,
            modifier = modifier,
        )
    } else {
        AnimatedContent(
            targetState = state,
            transitionSpec = { softContentTransform() },
            label = "appStateTransition",
            modifier = modifier,
        ) { targetState ->
            when (targetState) {
                ReaderUiState.Empty -> HomeScreen(
                    selectedTab = selectedTab,
                    currentDirectoryPath = currentDirectoryPath,
                    browserRootPath = browserRootPath,
                    libraryComics = libraryComics,
                    recentComics = recentComics,
                    isAddingToLibrary = isAddingToLibrary,
                    onTabSelected = onTabSelected,
                    onStartAddToLibrary = onStartAddToLibrary,
                    onDirectoryChange = onDirectoryChange,
                    onOpenLocalArchive = onOpenLocalArchive,
                    onOpenLocalFolder = onOpenLocalFolder,
                    onOpenRecentComic = onOpenRecentComic,
                    onOpenLibraryComic = onOpenLibraryComic,
                    onAddLocalArchiveToLibrary = onAddLocalArchiveToLibrary,
                    onAddLocalFolderToLibrary = onAddLocalFolderToLibrary,
                    onRemoveLibraryComic = onRemoveLibraryComic,
                )

                ReaderUiState.Loading -> LoadingComicScreen()

                is ReaderUiState.Error -> ErrorComicScreen(
                    message = targetState.message,
                    onBackToBrowser = onCloseComic,
                )

                is ReaderUiState.Ready -> Unit
            }
        }
    }
}

@Composable
fun HomeScreen(
    selectedTab: AppTab,
    currentDirectoryPath: String,
    browserRootPath: String,
    libraryComics: List<LibraryComic>,
    recentComics: List<RecentComic>,
    isAddingToLibrary: Boolean,
    onTabSelected: (AppTab) -> Unit,
    onStartAddToLibrary: () -> Unit,
    onDirectoryChange: (String) -> Unit,
    onOpenLocalArchive: (File) -> Unit,
    onOpenLocalFolder: (File) -> Unit,
    onOpenRecentComic: (RecentComic) -> Unit,
    onOpenLibraryComic: (LibraryComic) -> Unit,
    onAddLocalArchiveToLibrary: (File) -> Unit,
    onAddLocalFolderToLibrary: (File) -> Unit,
    onRemoveLibraryComic: (LibraryComic) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = { softContentTransform() },
                    label = "tabContentTransition",
                    modifier = Modifier.fillMaxSize(),
                ) { targetTab ->
                    when (targetTab) {
                        AppTab.Library -> LibraryScreen(
                            libraryComics = libraryComics,
                            recentComics = recentComics,
                            onStartAddToLibrary = onStartAddToLibrary,
                            onOpenLibraryComic = onOpenLibraryComic,
                            onRemoveLibraryComic = onRemoveLibraryComic,
                            modifier = Modifier.fillMaxSize(),
                        )

                        AppTab.Browse -> FileBrowserScreen(
                            currentDirectoryPath = currentDirectoryPath,
                            browserRootPath = browserRootPath,
                            recentComics = recentComics,
                            isAddingToLibrary = isAddingToLibrary,
                            onDirectoryChange = onDirectoryChange,
                            onOpenLocalArchive = onOpenLocalArchive,
                            onOpenLocalFolder = onOpenLocalFolder,
                            onOpenRecentComic = onOpenRecentComic,
                            onAddLocalArchiveToLibrary = onAddLocalArchiveToLibrary,
                            onAddLocalFolderToLibrary = onAddLocalFolderToLibrary,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            AppBottomNavigation(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
            )
        }
    }
}

@Composable
fun LibraryScreen(
    libraryComics: List<LibraryComic>,
    recentComics: List<RecentComic>,
    onStartAddToLibrary: () -> Unit,
    onOpenLibraryComic: (LibraryComic) -> Unit,
    onRemoveLibraryComic: (LibraryComic) -> Unit,
    modifier: Modifier = Modifier,
) {
    val throttledStartAdd = rememberThrottledAction(action = onStartAddToLibrary)
    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding(),
    ) {
        LibraryHeader(onStartAddToLibrary = throttledStartAdd)

        AnimatedContent(
            targetState = libraryComics.isEmpty(),
            transitionSpec = { softContentTransform() },
            label = "libraryContentTransition",
            modifier = Modifier.weight(1f),
        ) { isEmpty ->
            if (isEmpty) {
                LibraryEmptyContent(onStartAddToLibrary = throttledStartAdd)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    gridItems(
                        items = libraryComics,
                        key = { comic -> "${comic.sourceType.storageName}:${comic.path}" },
                    ) { comic ->
                        LibraryComicCard(
                            libraryComic = comic,
                            progressLabel = comic.progressLabel(recentComics),
                            onClick = rememberThrottledAction { onOpenLibraryComic(comic) },
                            onRemove = { onRemoveLibraryComic(comic) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryHeader(
    onStartAddToLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 18.dp, end = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Library",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onStartAddToLibrary) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Aggiungi comic alla libreria",
            )
        }
    }
}

@Composable
fun LibraryEmptyContent(
    onStartAddToLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.LibraryBooks,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(52.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "La tua libreria e vuota",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Aggiungi archivi o cartelle comic dal browser.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onStartAddToLibrary) {
            Text("Aggiungi comic")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryComicCard(
    libraryComic: LibraryComic,
    progressLabel: String,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .softPress(interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onRemove,
            ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            LibraryComicCover(libraryComic = libraryComic)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = libraryComic.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = progressLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun LibraryComicCover(
    libraryComic: LibraryComic,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var thumbnailUri by remember(libraryComic) { mutableStateOf<Uri?>(null) }

    LaunchedEffect(libraryComic) {
        thumbnailUri = loadLibraryComicThumbnailUri(context.applicationContext, libraryComic)
    }

    if (thumbnailUri != null) {
        Image(
            painter = rememberAsyncImagePainter(model = thumbnailUri),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize(),
        )
    } else {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = libraryComic.sourceType.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(42.dp),
            )
        }
    }
}

@Composable
fun AppBottomNavigation(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val throttledLibraryClick = rememberThrottledAction { onTabSelected(AppTab.Library) }
    val throttledBrowseClick = rememberThrottledAction { onTabSelected(AppTab.Browse) }
    NavigationBar(modifier = modifier.fillMaxWidth()) {
        NavigationBarItem(
            selected = selectedTab == AppTab.Library,
            onClick = throttledLibraryClick,
            icon = { Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = null) },
            label = { Text("Library") },
        )
        NavigationBarItem(
            selected = selectedTab == AppTab.Browse,
            onClick = throttledBrowseClick,
            icon = { Icon(Icons.Filled.Folder, contentDescription = null) },
            label = { Text("Browse") },
        )
    }
}

@Composable
fun FileAccessScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                text = "Comic Viewer",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Serve l'accesso speciale ai file per mostrare la libreria.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(28.dp))
            Button(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Apri impostazioni")
            }
        }
    }
}

sealed interface FileBrowserUiState {
    data object Loading : FileBrowserUiState
    data class Loaded(val directory: BrowserDirectory) : FileBrowserUiState
    data class Error(val message: String) : FileBrowserUiState
}

@Composable
fun FileBrowserScreen(
    currentDirectoryPath: String,
    browserRootPath: String,
    recentComics: List<RecentComic>,
    isAddingToLibrary: Boolean,
    onDirectoryChange: (String) -> Unit,
    onOpenLocalArchive: (File) -> Unit,
    onOpenLocalFolder: (File) -> Unit,
    onOpenRecentComic: (RecentComic) -> Unit,
    onAddLocalArchiveToLibrary: (File) -> Unit,
    onAddLocalFolderToLibrary: (File) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var searchQuery by remember(currentDirectoryPath) { mutableStateOf("") }
    var browserState by remember(currentDirectoryPath) {
        mutableStateOf<FileBrowserUiState>(FileBrowserUiState.Loading)
    }

    fun reload() {
        browserState = FileBrowserUiState.Loading
    }

    LaunchedEffect(currentDirectoryPath, browserState is FileBrowserUiState.Loading) {
        if (browserState !is FileBrowserUiState.Loading) return@LaunchedEffect

        runCatching { loadBrowserDirectory(context.applicationContext, File(currentDirectoryPath)) }
            .onSuccess { directory -> browserState = FileBrowserUiState.Loaded(directory) }
            .onFailure { throwable ->
                browserState = FileBrowserUiState.Error(
                    throwable.message?.takeIf { it.isNotBlank() } ?: "Cartella non leggibile.",
                )
            }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
        ) {
            FileBrowserHeader(
                currentDirectoryPath = currentDirectoryPath,
                isAddingToLibrary = isAddingToLibrary,
                parentPath = browserParentPathFor(
                    currentPath = currentDirectoryPath,
                    rootPath = browserRootPath,
                ),
                canOpenCurrentFolderAsComic = (browserState as? FileBrowserUiState.Loaded)
                    ?.directory
                    ?.hasDirectImageEntry()
                    ?: false,
                searchQuery = searchQuery,
                onSearchQueryChange = { query -> searchQuery = query },
                onDirectoryChange = onDirectoryChange,
                onOpenCurrentFolderAsComic = {
                    if (isAddingToLibrary) {
                        onAddLocalFolderToLibrary(File(currentDirectoryPath))
                    } else {
                        onOpenLocalFolder(File(currentDirectoryPath))
                    }
                },
                onReload = ::reload,
            )

            AnimatedContent(
                targetState = browserState,
                transitionSpec = { softContentTransform() },
                label = "browserStateTransition",
                modifier = Modifier.weight(1f),
            ) { state ->
                when (state) {
                    FileBrowserUiState.Loading -> LoadingFileBrowserContent()
                    is FileBrowserUiState.Error -> FileBrowserErrorContent(
                        message = state.message,
                        onRetry = ::reload,
                    )

                    is FileBrowserUiState.Loaded -> FileBrowserContent(
                        directory = state.directory,
                        recentComics = recentComics,
                        searchQuery = searchQuery,
                        isAddingToLibrary = isAddingToLibrary,
                        onRecentComicClick = onOpenRecentComic,
                        onEntryClick = { entry ->
                            when (entry.type) {
                                BrowserEntryType.Directory -> onDirectoryChange(entry.path)
                                BrowserEntryType.Archive -> {
                                    if (isAddingToLibrary) {
                                        onAddLocalArchiveToLibrary(File(entry.path))
                                    } else {
                                        onOpenLocalArchive(File(entry.path))
                                    }
                                }

                                BrowserEntryType.ImageFile,
                                BrowserEntryType.UnsupportedFile,
                                -> Unit
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun FileBrowserHeader(
    currentDirectoryPath: String,
    isAddingToLibrary: Boolean,
    parentPath: String?,
    canOpenCurrentFolderAsComic: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onDirectoryChange: (String) -> Unit,
    onOpenCurrentFolderAsComic: () -> Unit,
    onReload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val throttledParentClick = rememberThrottledAction { if (parentPath != null) onDirectoryChange(parentPath) }
    val throttledOpenFolder = rememberThrottledAction(action = onOpenCurrentFolderAsComic)
    val throttledReload = rememberThrottledAction(action = onReload)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isAddingToLibrary) "Aggiungi alla libreria" else "File",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = currentDirectoryPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(
                onClick = throttledParentClick,
                enabled = parentPath != null,
            ) {
                Text("Su")
            }
            TextButton(onClick = throttledReload) {
                Text("Aggiorna")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = throttledOpenFolder,
                enabled = canOpenCurrentFolderAsComic,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (isAddingToLibrary) "Aggiungi cartella" else "Apri come comic")
            }
        }
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Cancella ricerca",
                        )
                    }
                }
            },
            label = { Text("Cerca in questa cartella") },
        )
        HorizontalDivider()
    }
}

@Composable
fun LoadingFileBrowserContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun FileBrowserErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("Riprova")
        }
    }
}

@Composable
fun FileBrowserContent(
    directory: BrowserDirectory,
    recentComics: List<RecentComic>,
    searchQuery: String,
    isAddingToLibrary: Boolean,
    onRecentComicClick: (RecentComic) -> Unit,
    onEntryClick: (BrowserEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = filterBrowserEntries(directory.entries, searchQuery)
    val showRecentComics = !isAddingToLibrary && searchQuery.isBlank() && recentComics.isNotEmpty()

    AnimatedContent(
        targetState = entries.isEmpty() && !showRecentComics,
        transitionSpec = { softContentTransform() },
        label = "browserContentTransition",
        modifier = modifier.fillMaxSize(),
    ) { isEmpty ->
        if (isEmpty) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (searchQuery.isBlank()) "Cartella vuota" else "Nessun risultato",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 18.dp),
            ) {
                if (showRecentComics) {
                    item(key = "recent_comics_header") {
                        FileBrowserSectionHeader(title = "Recenti")
                    }
                    items(
                        items = recentComics,
                        key = { recentComic -> "recent:${recentComic.sourceType.storageName}:${recentComic.path}" },
                    ) { recentComic ->
                        RecentComicRow(
                            recentComic = recentComic,
                            onClick = { onRecentComicClick(recentComic) },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 88.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f),
                        )
                    }
                    if (entries.isNotEmpty()) {
                        item(key = "browser_entries_header") {
                            FileBrowserSectionHeader(title = "File")
                        }
                    }
                }
                items(
                    items = entries,
                    key = { entry -> entry.path },
                ) { entry ->
                    FileBrowserRow(
                        entry = entry,
                        isAddingToLibrary = isAddingToLibrary,
                        onClick = { onEntryClick(entry) },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 88.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f),
                    )
                }
            }
        }
    }
}

@Composable
fun FileBrowserSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 6.dp),
    )
}

@Composable
fun RecentComicRow(
    recentComic: RecentComic,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val throttledRecentClick = rememberThrottledAction(action = onClick)
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .softPress(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = throttledRecentClick,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        RecentComicThumbnail(recentComic = recentComic)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = recentComic.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = recentComic.subtitleLabel(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = recentComic.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
fun RecentComicThumbnail(
    recentComic: RecentComic,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(58.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = recentComic.sourceType.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
fun FileBrowserRow(
    entry: BrowserEntry,
    isAddingToLibrary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canClick = entry.canOpen || (isAddingToLibrary && entry.type == BrowserEntryType.Archive)
    val rowAlpha = if (canClick || entry.type == BrowserEntryType.ImageFile) 1f else 0.52f
    val throttledClick = rememberThrottledAction(action = onClick)
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .softPress(interactionSource, enabled = canClick)
            .clickable(
                enabled = canClick,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = throttledClick,
            )
            .alpha(rowAlpha)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FileBrowserThumbnail(entry = entry)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.type.label(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (canClick) {
            Icon(
                imageVector = if (isAddingToLibrary && entry.type == BrowserEntryType.Archive) {
                    Icons.Filled.Add
                } else {
                    Icons.Filled.ChevronRight
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
fun FileBrowserThumbnail(
    entry: BrowserEntry,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(58.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        val thumbnailUri = entry.thumbnailUri
        if (thumbnailUri != null) {
            Image(
                painter = rememberAsyncImagePainter(model = thumbnailUri),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp)),
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = entry.type.icon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

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
                text = "Caricamento pagine...",
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
                text = "Impossibile aprire il comic",
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
                Text("Torna ai file")
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
                pageLoadError = "Impossibile visualizzare ${page.displayName}"
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
                .pointerInput(page.imageUri, viewportSize, controlsVisible) {
                    detectPageGestures(
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
            lastSuccessfulPainter?.let { cachedPainter ->
                ZoomablePageImage(
                    painter = cachedPainter,
                    contentDescription = null,
                    scale = pageScale,
                    offset = pageOffset,
                )
            }

            if (painterState is AsyncImagePainter.State.Success) {
                ZoomablePageImage(
                    painter = painter,
                    contentDescription = page.displayName,
                    scale = pageScale,
                    offset = pageOffset,
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
                translationX = offset.x
                translationY = offset.y
            },
    )
}

private suspend fun PointerInputScope.detectPageGestures(
    onTap: (Offset) -> Unit,
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
                if (zoomMotion > touchSlop || panMotion > touchSlop) {
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

fun comicPageImageCacheKey(source: ComicSource, page: ComicPage): String =
    comicPageImageCacheKey(
        source = source,
        pageIndex = page.index,
        relativePath = page.relativePath,
        imageUri = page.imageUri.toString(),
    )

fun comicPageImageCacheKey(
    source: ComicSource,
    pageIndex: Int,
    relativePath: String,
    imageUri: String,
): String =
    listOf(
        "comic-page",
        comicSourceImageCacheKey(source),
        pageIndex.toString(),
        relativePath,
        imageUri,
    ).joinToString(separator = "|")

private fun comicSourceImageCacheKey(source: ComicSource): String = when (source) {
    is ComicSource.Archive -> "archive-uri:${source.displayName}:${source.uri}"
    is ComicSource.Folder -> "folder-uri:${source.displayName}:${source.uri}"
    is ComicSource.LocalArchive -> {
        val file = File(source.filePath)
        "local-archive:${source.filePath}:${file.lastModified()}:${file.length()}"
    }

    is ComicSource.LocalFolder -> "local-folder:${source.folderPath}"
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
                    text = "File",
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
                text = if (pageCount <= 0) "Pagina 1 / 1" else "Pagina ${targetPageIndex + 1} / $pageCount",
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

private fun BrowserEntryType.label(): String = when (this) {
    BrowserEntryType.Directory -> "Cartella"
    BrowserEntryType.Archive -> "Archivio comic"
    BrowserEntryType.ImageFile -> "Immagine"
    BrowserEntryType.UnsupportedFile -> "File"
}

private fun BrowserEntryType.icon(): ImageVector = when (this) {
    BrowserEntryType.Directory -> Icons.Filled.Folder
    BrowserEntryType.Archive -> Icons.Filled.Archive
    BrowserEntryType.ImageFile -> Icons.Filled.Image
    BrowserEntryType.UnsupportedFile -> Icons.Filled.Description
}

private fun RecentComicSourceType.icon(): ImageVector = when (this) {
    RecentComicSourceType.LocalArchive -> Icons.Filled.Archive
    RecentComicSourceType.LocalFolder -> Icons.Filled.Folder
}

private fun RecentComic.progressLabel(): String {
    if (pageCount <= 0) return "Pagina 1"
    return "Pagina ${coercePageIndex(lastPageIndex, pageCount) + 1}/$pageCount"
}

private fun RecentComic.subtitleLabel(): String =
    "$displayName - ${progressLabel()}"

private fun BrowserDirectory.hasDirectImageEntry(): Boolean =
    entries.any { entry -> entry.type == BrowserEntryType.ImageFile }

fun browserParentPathFor(
    currentPath: String,
    rootPath: String,
): String? {
    val currentFile = File(currentPath).canonicalFile
    val rootFile = File(rootPath).canonicalFile

    if (currentFile.absolutePath == rootFile.absolutePath) return null
    if (!currentFile.absolutePath.startsWith(rootFile.absolutePath + File.separator)) return null

    val parentPath = currentFile.parentFile?.absolutePath ?: return null
    return parentPath
        .takeIf { it == rootFile.absolutePath || it.startsWith(rootFile.absolutePath + File.separator) }
        ?.takeUnless { it == currentFile.absolutePath }
}

private fun hasFileManagerAccess(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        true
    }

@Suppress("DEPRECATION")
private fun defaultBrowserRootPath(): String =
    Environment.getExternalStorageDirectory().absolutePath

private val ReaderUiStateSaver = listSaver<ReaderUiState, String>(
    save = { state ->
        when (state) {
            ReaderUiState.Empty,
            ReaderUiState.Loading,
            -> listOf("empty")

            is ReaderUiState.Error -> listOf("error", state.message)

            is ReaderUiState.Ready -> buildList {
                val source = state.book.source
                val sourceType = when (source) {
                    is ComicSource.Archive -> "archive"
                    is ComicSource.Folder -> "folder"
                    is ComicSource.LocalArchive -> "local_archive"
                    is ComicSource.LocalFolder -> "local_folder"
                }
                val sourceLocation = when (source) {
                    is ComicSource.Archive -> source.uri.toString()
                    is ComicSource.Folder -> source.uri.toString()
                    is ComicSource.LocalArchive -> source.filePath
                    is ComicSource.LocalFolder -> source.folderPath
                }
                add("ready")
                add(sourceType)
                add(sourceLocation)
                add(source.displayName)
                add(state.book.title)
                add(state.pageIndex.toString())
                add(state.book.pages.size.toString())
                state.book.pages.forEach { page ->
                    add(page.index.toString())
                    add(page.displayName)
                    add(page.relativePath)
                    add(page.imageUri.toString())
                    add(page.width.toString())
                    add(page.height.toString())
                }
            }
        }
    },
    restore = { savedValues ->
        when (savedValues.firstOrNull()) {
            "error" -> ReaderUiState.Error(savedValues.getOrNull(1).orEmpty())
            "ready" -> restoreReadyState(savedValues)
            else -> ReaderUiState.Empty
        }
    },
)

private fun restoreReadyState(savedValues: List<String>): ReaderUiState {
    val sourceType = savedValues.getOrNull(1) ?: return ReaderUiState.Empty
    val sourceLocation = savedValues.getOrNull(2) ?: return ReaderUiState.Empty
    val sourceDisplayName = savedValues.getOrNull(3) ?: return ReaderUiState.Empty
    val title = savedValues.getOrNull(4) ?: sourceDisplayName
    val pageIndex = savedValues.getOrNull(5)?.toIntOrNull() ?: 0
    val pageCount = savedValues.getOrNull(6)?.toIntOrNull() ?: 0
    val pageValues = savedValues.drop(7)

    val pages = (0 until pageCount).mapNotNull { pageOffset ->
        val valueOffset = pageOffset * 6
        val index = pageValues.getOrNull(valueOffset)?.toIntOrNull()
        val displayName = pageValues.getOrNull(valueOffset + 1)
        val relativePath = pageValues.getOrNull(valueOffset + 2)
        val imageUri = pageValues.getOrNull(valueOffset + 3)?.let(Uri::parse)
        val width = pageValues.getOrNull(valueOffset + 4)?.toIntOrNull() ?: 0
        val height = pageValues.getOrNull(valueOffset + 5)?.toIntOrNull() ?: 0
        if (index != null && displayName != null && imageUri != null) {
            ComicPage(
                index = index,
                displayName = displayName,
                relativePath = relativePath ?: displayName,
                imageUri = imageUri,
                width = width,
                height = height,
            )
        } else {
            null
        }
    }

    if (pages.isEmpty()) return ReaderUiState.Empty

    val source = when (sourceType) {
        "archive" -> ComicSource.Archive(uri = Uri.parse(sourceLocation), displayName = sourceDisplayName)
        "folder" -> ComicSource.Folder(uri = Uri.parse(sourceLocation), displayName = sourceDisplayName)
        "local_archive" -> ComicSource.LocalArchive(filePath = sourceLocation, displayName = sourceDisplayName)
        "local_folder" -> ComicSource.LocalFolder(folderPath = sourceLocation, displayName = sourceDisplayName)
        else -> return ReaderUiState.Empty
    }

    return ReaderUiState.Ready(
        book = ComicBook(title = title, source = source, pages = pages),
        pageIndex = coercePageIndex(pageIndex, pages.size),
    )
}

@Preview(showBackground = true)
@Composable
fun FileAccessScreenPreview() {
    MyApplicationTheme {
        FileAccessScreen(onOpenSettings = {})
    }
}
