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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val MIN_PAGE_SCALE = 1f
private const val MAX_PAGE_SCALE = 5f

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
    var loadJob by remember { mutableStateOf<Job?>(null) }
    val browserRootPath = remember { defaultBrowserRootPath() }

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

    fun openLocalArchive(file: File) {
        loadJob?.cancel()
        uiState = ReaderUiState.Loading
        loadJob = scope.launch {
            runCatching { loadLocalArchiveComic(appContext, file) }
                .onSuccess { book ->
                    uiState = ReaderUiState.Ready(book = book, pageIndex = 0)
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
                    uiState = ReaderUiState.Ready(book = book, pageIndex = 0)
                }
                .onFailure { throwable ->
                    if (throwable !is CancellationException) showError(throwable)
                }
        }
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
        (uiState !is ReaderUiState.Empty || browserBackPath != null)

    BackHandler(enabled = shouldHandleBack) {
        when (uiState) {
            ReaderUiState.Empty -> {
                if (browserBackPath != null) currentDirectoryPath = browserBackPath
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
            currentDirectoryPath = currentDirectoryPath,
            browserRootPath = browserRootPath,
            onDirectoryChange = { path -> currentDirectoryPath = path },
            onOpenLocalArchive = ::openLocalArchive,
            onOpenLocalFolder = ::openLocalFolder,
            onPrevious = {
                val ready = uiState as? ReaderUiState.Ready ?: return@ComicViewerScreen
                uiState = ready.copy(pageIndex = coercePageIndex(ready.pageIndex - 1, ready.book.pages.size))
            },
            onNext = {
                val ready = uiState as? ReaderUiState.Ready ?: return@ComicViewerScreen
                uiState = ready.copy(pageIndex = coercePageIndex(ready.pageIndex + 1, ready.book.pages.size))
            },
            onCloseComic = ::closeComic,
        )
    }
}

@Composable
fun ComicViewerScreen(
    state: ReaderUiState,
    currentDirectoryPath: String,
    browserRootPath: String,
    onDirectoryChange: (String) -> Unit,
    onOpenLocalArchive: (File) -> Unit,
    onOpenLocalFolder: (File) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCloseComic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        ReaderUiState.Empty -> FileBrowserScreen(
            currentDirectoryPath = currentDirectoryPath,
            browserRootPath = browserRootPath,
            onDirectoryChange = onDirectoryChange,
            onOpenLocalArchive = onOpenLocalArchive,
            onOpenLocalFolder = onOpenLocalFolder,
            modifier = modifier,
        )

        ReaderUiState.Loading -> LoadingComicScreen(modifier = modifier)

        is ReaderUiState.Error -> ErrorComicScreen(
            message = state.message,
            onBackToBrowser = onCloseComic,
            modifier = modifier,
        )

        is ReaderUiState.Ready -> ComicReaderScreen(
            book = state.book,
            pageIndex = state.pageIndex,
            onPrevious = onPrevious,
            onNext = onNext,
            onCloseComic = onCloseComic,
            modifier = modifier,
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
    onDirectoryChange: (String) -> Unit,
    onOpenLocalArchive: (File) -> Unit,
    onOpenLocalFolder: (File) -> Unit,
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
                onOpenCurrentFolderAsComic = { onOpenLocalFolder(File(currentDirectoryPath)) },
                onReload = ::reload,
            )

            when (val state = browserState) {
                FileBrowserUiState.Loading -> LoadingFileBrowserContent()
                is FileBrowserUiState.Error -> FileBrowserErrorContent(
                    message = state.message,
                    onRetry = ::reload,
                )

                is FileBrowserUiState.Loaded -> FileBrowserContent(
                    directory = state.directory,
                    searchQuery = searchQuery,
                    onEntryClick = { entry ->
                        when (entry.type) {
                            BrowserEntryType.Directory -> onDirectoryChange(entry.path)
                            BrowserEntryType.Archive -> onOpenLocalArchive(File(entry.path))
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

@Composable
fun FileBrowserHeader(
    currentDirectoryPath: String,
    parentPath: String?,
    canOpenCurrentFolderAsComic: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onDirectoryChange: (String) -> Unit,
    onOpenCurrentFolderAsComic: () -> Unit,
    onReload: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                    text = "File",
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
                onClick = { if (parentPath != null) onDirectoryChange(parentPath) },
                enabled = parentPath != null,
            ) {
                Text("Su")
            }
            TextButton(onClick = onReload) {
                Text("Aggiorna")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onOpenCurrentFolderAsComic,
                enabled = canOpenCurrentFolderAsComic,
                modifier = Modifier.weight(1f),
            ) {
                Text("Apri come comic")
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
    searchQuery: String,
    onEntryClick: (BrowserEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = filterBrowserEntries(directory.entries, searchQuery)

    if (entries.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (searchQuery.isBlank()) "Cartella vuota" else "Nessun risultato",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 18.dp),
    ) {
        items(
            items = entries,
            key = { entry -> entry.path },
        ) { entry ->
            FileBrowserRow(
                entry = entry,
                onClick = { onEntryClick(entry) },
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 88.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f),
            )
        }
    }
}

@Composable
fun FileBrowserRow(
    entry: BrowserEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowAlpha = if (entry.canOpen || entry.type == BrowserEntryType.ImageFile) 1f else 0.52f
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = entry.canOpen, onClick = onClick)
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
        if (entry.canOpen) {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
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
                onClick = onBackToBrowser,
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
    onCloseComic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val imageLoader = remember(context) { SingletonImageLoader.get(context) }
    val currentIndex = coercePageIndex(pageIndex, book.pages.size)
    val page = book.pages[currentIndex]
    val currentRequest = remember(page.imageUri) {
        page.imageRequest(context)
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

    LaunchedEffect(book.pages, currentIndex, imageLoader) {
        book.pages.preloadAround(
            currentIndex = currentIndex,
            context = context,
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
                .pointerInput(page.imageUri, viewportSize) {
                    detectPageGestures(
                        onTap = { tapOffset ->
                            if (tapOffset.x < size.width / 2f) {
                                onPrevious()
                            } else {
                                onNext()
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

        ReaderTopBar(
            title = book.title,
            pageText = "${currentIndex + 1} / ${book.pages.size}",
            onCloseComic = onCloseComic,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .systemBarsPadding()
                .padding(12.dp),
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
    imageLoader: coil3.ImageLoader,
) {
    listOf(currentIndex + 1, currentIndex + 2, currentIndex - 1)
        .filter { index -> index in indices }
        .forEach { index ->
            imageLoader.enqueue(get(index).imageRequest(context))
        }
}

private fun ComicPage.imageRequest(context: Context): ImageRequest {
    val cacheKey = imageUri.toString()
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
                    text = "File",
                    color = Color.White,
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
