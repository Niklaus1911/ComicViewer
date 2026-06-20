package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import coil3.SingletonImageLoader
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.launch

private const val MIN_PAGE_SCALE = 1f
private const val MAX_PAGE_SCALE = 5f

private val supportedArchiveMimeTypes = arrayOf(
    "application/zip",
    "application/x-zip-compressed",
    "application/vnd.comicbook+zip",
    "application/x-cbz",
    "application/octet-stream",
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
    var uiState by rememberSaveable(stateSaver = ReaderUiStateSaver) {
        mutableStateOf<ReaderUiState>(ReaderUiState.Empty)
    }

    fun showError(throwable: Throwable) {
        uiState = ReaderUiState.Error(
            throwable.message?.takeIf { it.isNotBlank() }
                ?: "Non e stato possibile aprire questo comic.",
        )
    }

    fun openArchive(uri: Uri) {
        tryTakePersistableReadPermission(context, uri)
        uiState = ReaderUiState.Loading
        scope.launch {
            runCatching { loadArchiveComic(appContext, uri) }
                .onSuccess { book ->
                    uiState = ReaderUiState.Ready(book = book, pageIndex = 0)
                }
                .onFailure { throwable -> showError(throwable) }
        }
    }

    fun openFolder(uri: Uri) {
        uiState = ReaderUiState.Loading
        scope.launch {
            runCatching { loadFolderComic(appContext, uri) }
                .onSuccess { book ->
                    uiState = ReaderUiState.Ready(book = book, pageIndex = 0)
                }
                .onFailure { throwable -> showError(throwable) }
        }
    }

    fun closeComic() {
        clearArchiveCache(appContext)
        uiState = ReaderUiState.Empty
    }

    val archiveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) openArchive(uri)
    }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uri = result.data?.data
        if (uri != null) {
            tryTakePersistableReadPermission(context, uri, result.data?.flags ?: 0)
            openFolder(uri)
        }
    }

    ComicViewerScreen(
        state = uiState,
        onOpenArchive = { archiveLauncher.launch(supportedArchiveMimeTypes) },
        onOpenFolder = {
            folderLauncher.launch(
                Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
                },
            )
        },
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

@Composable
fun ComicViewerScreen(
    state: ReaderUiState,
    onOpenArchive: () -> Unit,
    onOpenFolder: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCloseComic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        ReaderUiState.Empty -> OpenComicScreen(
            onOpenArchive = onOpenArchive,
            onOpenFolder = onOpenFolder,
            modifier = modifier,
        )

        ReaderUiState.Loading -> LoadingComicScreen(modifier = modifier)

        is ReaderUiState.Error -> ErrorComicScreen(
            message = state.message,
            onOpenArchive = onOpenArchive,
            onOpenFolder = onOpenFolder,
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
fun OpenComicScreen(
    onOpenArchive: () -> Unit,
    onOpenFolder: () -> Unit,
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
                text = "Apri un archivio .zip/.cbz o una cartella con pagine numerate.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(28.dp))
            Button(
                onClick = onOpenArchive,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Apri archivio")
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onOpenFolder,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Apri cartella")
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
    onOpenArchive: () -> Unit,
    onOpenFolder: () -> Unit,
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
                onClick = onOpenArchive,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Apri archivio")
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onOpenFolder,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Apri cartella")
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
                    text = "Apri",
                    color = Color.White,
                )
            }
        }
    }
}

private fun tryTakePersistableReadPermission(
    context: Context,
    uri: Uri,
    grantFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION,
) {
    if (grantFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0) return

    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
}

private val ReaderUiStateSaver = listSaver<ReaderUiState, String>(
    save = { state ->
        when (state) {
            ReaderUiState.Empty,
            ReaderUiState.Loading,
            -> listOf("empty")

            is ReaderUiState.Error -> listOf("error", state.message)

            is ReaderUiState.Ready -> buildList {
                add("ready")
                add(if (state.book.source is ComicSource.Archive) "archive" else "folder")
                add(state.book.source.uri.toString())
                add(state.book.source.displayName)
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
    val sourceUri = savedValues.getOrNull(2)?.let(Uri::parse) ?: return ReaderUiState.Empty
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

    val source = if (sourceType == "archive") {
        ComicSource.Archive(uri = sourceUri, displayName = sourceDisplayName)
    } else {
        ComicSource.Folder(uri = sourceUri, displayName = sourceDisplayName)
    }

    return ReaderUiState.Ready(
        book = ComicBook(title = title, source = source, pages = pages),
        pageIndex = coercePageIndex(pageIndex, pages.size),
    )
}

@Preview(showBackground = true)
@Composable
fun OpenComicScreenPreview() {
    MyApplicationTheme {
        OpenComicScreen(
            onOpenArchive = {},
            onOpenFolder = {},
        )
    }
}
