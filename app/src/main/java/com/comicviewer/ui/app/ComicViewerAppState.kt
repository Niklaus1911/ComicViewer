package com.comicviewer.ui.app

import android.content.Context
import android.os.Environment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.comicviewer.ComicBook
import com.comicviewer.ComicLoadException
import com.comicviewer.ComicSource
import com.comicviewer.R
import com.comicviewer.LibraryComic
import com.comicviewer.LibraryAutoScanState
import com.comicviewer.LibraryComicMetadata
import com.comicviewer.ReaderUiState
import com.comicviewer.RecentComic
import com.comicviewer.RecentComicSourceType
import com.comicviewer.browserParentPathFor
import com.comicviewer.clearArchiveCache
import com.comicviewer.coercePageIndex
import com.comicviewer.libraryComicForArchive
import com.comicviewer.libraryComicForFolder
import com.comicviewer.loadLibraryComics
import com.comicviewer.loadLibraryMetadata
import com.comicviewer.loadArchiveComic
import com.comicviewer.loadFolderComic
import com.comicviewer.loadLocalArchiveComic
import com.comicviewer.loadLocalFolderComic
import com.comicviewer.loadRecentComics
import com.comicviewer.loadWelcomeSeen
import com.comicviewer.matchingRecentComic
import com.comicviewer.newLibraryComicsForArchiveScan
import com.comicviewer.recentComicFor
import com.comicviewer.removeLibraryComic
import com.comicviewer.removeRecentComic
import com.comicviewer.saveLibraryComics
import com.comicviewer.saveLibraryMetadata
import com.comicviewer.saveRecentComics
import com.comicviewer.saveWelcomeSeen
import com.comicviewer.scanLibraryArchives
import com.comicviewer.shouldShowWelcome
import com.comicviewer.upsertLibraryComic
import com.comicviewer.upsertLibraryComics
import com.comicviewer.upsertRecentComic
import com.comicviewer.toggleLibraryFavorite
import com.comicviewer.setLibraryTags
import com.comicviewer.pruneLibraryMetadata
import com.comicviewer.updateRecentComicPage
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ComicViewerAppState(
    private val appContext: Context,
    private val scope: CoroutineScope,
    hasFileAccess: Boolean,
    currentDirectoryPath: String,
    uiState: ReaderUiState,
    selectedTabName: String,
    isAddingToLibrary: Boolean,
    showWelcome: Boolean,
    recentComics: List<RecentComic>,
    libraryComics: List<LibraryComic>,
    libraryMetadata: List<LibraryComicMetadata>,
    val browserRootPath: String,
) {
    var hasFileAccess by mutableStateOf(hasFileAccess)
        private set
    var currentDirectoryPath by mutableStateOf(currentDirectoryPath)
        private set
    var uiState by mutableStateOf(uiState)
        private set
    var selectedTabName by mutableStateOf(selectedTabName)
        private set
    var isAddingToLibrary by mutableStateOf(isAddingToLibrary)
        private set
    var showWelcome by mutableStateOf(showWelcome)
        private set
    var recentComics by mutableStateOf(recentComics)
        private set
    var libraryComics by mutableStateOf(libraryComics)
        private set
    var libraryMetadata by mutableStateOf(libraryMetadata)
        private set
    var libraryAutoScanState by mutableStateOf<LibraryAutoScanState>(LibraryAutoScanState.Idle)
        private set
    private var loadJob by mutableStateOf<Job?>(null)
    private var libraryAutoScanJob: Job? = null

    init {
        (uiState as? ReaderUiState.Loading)?.let(::launchComicLoad)
    }

    val currentTab: AppTab
        get() = runCatching { AppTab.valueOf(selectedTabName) }.getOrDefault(AppTab.Library)

    val browserBackPath: String?
        get() = browserParentPathFor(
            currentPath = currentDirectoryPath,
            rootPath = browserRootPath,
        )

    val shouldHandleBack: Boolean
        get() = hasFileAccess &&
            (uiState !is ReaderUiState.Empty || isAddingToLibrary || (currentTab == AppTab.Browse && browserBackPath != null))

    fun refreshFileAccess(hasAccess: Boolean) {
        hasFileAccess = hasAccess
    }

    fun selectTab(tab: AppTab) {
        selectedTabName = tab.name
        if (tab != AppTab.Browse) isAddingToLibrary = false
    }

    fun startAddToLibrary() {
        selectedTabName = AppTab.Browse.name
        isAddingToLibrary = true
    }

    fun dismissWelcome() {
        if (!showWelcome) return
        saveWelcomeSeen(appContext, true)
        showWelcome = false
    }

    fun startWelcomeAutoScan() {
        dismissWelcome()
        selectedTabName = AppTab.Library.name
        autoScanLibraryArchives()
    }

    fun startWelcomeManualBrowse() {
        dismissWelcome()
        startAddToLibrary()
    }

    fun changeDirectory(path: String) {
        currentDirectoryPath = path
    }

    fun openLocalArchive(file: File) {
        startComicLoad(
            ReaderUiState.Loading(
                source = ComicSource.LocalArchive(
                    filePath = file.absolutePath,
                    displayName = file.name.ifBlank { file.absolutePath },
                ),
                pageIndex = 0,
            ),
        )
    }

    fun openLocalFolder(folder: File) {
        startComicLoad(
            ReaderUiState.Loading(
                source = ComicSource.LocalFolder(
                    folderPath = folder.absolutePath,
                    displayName = folder.name.ifBlank { folder.absolutePath },
                ),
                pageIndex = 0,
            ),
        )
    }

    fun openRecentComic(recentComic: RecentComic) {
        val loading = ReaderUiState.Loading(
            source = recentComic.toComicSource(),
            pageIndex = recentComic.lastPageIndex,
        )
        startComicLoad(loading) { throwable ->
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

    fun openLibraryComic(libraryComic: LibraryComic) {
        val recentComic = libraryComic.matchingRecentComic(recentComics)
        startComicLoad(
            loading = ReaderUiState.Loading(
                source = libraryComic.toComicSource(),
                pageIndex = recentComic?.lastPageIndex ?: 0,
            ),
        )
    }

    private fun startComicLoad(
        loading: ReaderUiState.Loading,
        onFailure: (Throwable) -> Unit = { throwable ->
            if (throwable !is CancellationException) showError(throwable)
        },
    ) {
        loadJob?.cancel()
        uiState = loading
        launchComicLoad(loading, onFailure)
    }

    private fun launchComicLoad(
        loading: ReaderUiState.Loading,
        onFailure: (Throwable) -> Unit = { throwable ->
            if (throwable !is CancellationException) showError(throwable)
        },
    ) {
        loadJob = scope.launch {
            runCatching { loadComic(loading.source) }
                .onSuccess { book ->
                    showReadyComic(book = book, pageIndex = loading.pageIndex)
                }
                .onFailure(onFailure)
        }
    }

    private suspend fun loadComic(source: ComicSource): ComicBook = when (source) {
        is ComicSource.Archive -> loadArchiveComic(appContext, source.uri)
        is ComicSource.Folder -> loadFolderComic(appContext, source.uri)
        is ComicSource.LocalArchive -> loadLocalArchiveComic(appContext, File(source.filePath))
        is ComicSource.LocalFolder -> loadLocalFolderComic(File(source.folderPath))
    }

    private fun RecentComic.toComicSource(): ComicSource = when (sourceType) {
        RecentComicSourceType.LocalArchive -> ComicSource.LocalArchive(
            filePath = path,
            displayName = displayName,
        )
        RecentComicSourceType.LocalFolder -> ComicSource.LocalFolder(
            folderPath = path,
            displayName = displayName,
        )
    }

    private fun LibraryComic.toComicSource(): ComicSource = when (sourceType) {
        RecentComicSourceType.LocalArchive -> ComicSource.LocalArchive(
            filePath = path,
            displayName = displayName,
        )
        RecentComicSourceType.LocalFolder -> ComicSource.LocalFolder(
            folderPath = path,
            displayName = displayName,
        )
    }

    fun addArchiveToLibrary(file: File) {
        persistLibraryComics(
            upsertLibraryComic(
                libraryComics = libraryComics,
                libraryComic = libraryComicForArchive(file, addedAt = System.currentTimeMillis()),
            ),
        )
        selectedTabName = AppTab.Library.name
        isAddingToLibrary = false
    }

    fun addArchivesToLibrary(files: List<File>) {
        if (files.isEmpty()) return
        val addedAt = System.currentTimeMillis()
        val newComics = files.mapIndexed { index, file ->
            libraryComicForArchive(file, addedAt = addedAt + index)
        }
        persistLibraryComics(
            upsertLibraryComics(
                libraryComics = libraryComics,
                newComics = newComics,
            ),
        )
        selectedTabName = AppTab.Library.name
        isAddingToLibrary = false
    }

    fun addFolderToLibrary(folder: File) {
        persistLibraryComics(
            upsertLibraryComic(
                libraryComics = libraryComics,
                libraryComic = libraryComicForFolder(folder, addedAt = System.currentTimeMillis()),
            ),
        )
        selectedTabName = AppTab.Library.name
        isAddingToLibrary = false
    }

    fun removeFromLibrary(libraryComic: LibraryComic) {
        val nextLibraryComics = removeLibraryComic(
            libraryComics = libraryComics,
            sourceType = libraryComic.sourceType,
            path = libraryComic.path,
        )
        persistLibraryComics(nextLibraryComics)
        persistLibraryMetadata(pruneLibraryMetadata(libraryMetadata, nextLibraryComics))
    }

    fun toggleFavorite(libraryComic: LibraryComic) {
        persistLibraryMetadata(
            toggleLibraryFavorite(
                libraryComic = libraryComic,
                metadata = libraryMetadata,
                nowMs = System.currentTimeMillis(),
            ),
        )
    }

    fun setTags(libraryComic: LibraryComic, tags: List<String>) {
        persistLibraryMetadata(
            setLibraryTags(
                libraryComic = libraryComic,
                metadata = libraryMetadata,
                tags = tags,
                nowMs = System.currentTimeMillis(),
            ),
        )
    }

    fun autoScanLibraryArchives() {
        if (libraryAutoScanState is LibraryAutoScanState.Scanning) return

        libraryAutoScanJob?.cancel()
        libraryAutoScanState = LibraryAutoScanState.Scanning
        libraryAutoScanJob = scope.launch {
            runCatching { scanLibraryArchives(File(browserRootPath)) }
                .onSuccess { scanResult ->
                    val existingArchivePaths = libraryComics
                        .filter { comic -> comic.sourceType == RecentComicSourceType.LocalArchive }
                        .mapTo(mutableSetOf()) { comic -> comic.path }
                    val newCandidateCount = scanResult.candidates
                        .map { candidate -> candidate.archiveFile.absolutePath }
                        .distinct()
                        .count { path -> path !in existingArchivePaths }
                    val newLibraryComics = newLibraryComicsForArchiveScan(
                        libraryComics = libraryComics,
                        candidates = scanResult.candidates,
                        addedAt = System.currentTimeMillis(),
                    )
                    if (newLibraryComics.isNotEmpty()) {
                        persistLibraryComics(
                            upsertLibraryComics(
                                libraryComics = libraryComics,
                                newComics = newLibraryComics,
                            ),
                        )
                    }

                    libraryAutoScanState = LibraryAutoScanState.Complete(
                        foundCount = scanResult.candidates.size,
                        addedCount = newLibraryComics.size,
                        skippedExistingCount = scanResult.candidates.size - newCandidateCount,
                        scannedDirectoryCount = scanResult.scannedDirectoryCount,
                        libraryLimitReached = newCandidateCount > newLibraryComics.size,
                        directoryLimitReached = scanResult.directoryLimitReached,
                    )
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) {
                        libraryAutoScanState = LibraryAutoScanState.Idle
                    } else {
                        libraryAutoScanState = LibraryAutoScanState.Failed(
                            throwable.message?.takeIf { message -> message.isNotBlank() }
                                ?: appContext.getString(R.string.library_auto_scan_failed),
                        )
                    }
                }
        }
    }

    fun clearLibraryAutoScanResult() {
        if (libraryAutoScanState is LibraryAutoScanState.Complete) {
            libraryAutoScanState = LibraryAutoScanState.Idle
        }
    }

    fun previousPage() {
        val ready = uiState as? ReaderUiState.Ready ?: return
        val nextPageIndex = coercePageIndex(ready.pageIndex - 1, ready.book.pages.size)
        updateRecentPage(ready, nextPageIndex)
        uiState = ready.copy(pageIndex = nextPageIndex)
    }

    fun nextPage() {
        val ready = uiState as? ReaderUiState.Ready ?: return
        val nextPageIndex = coercePageIndex(ready.pageIndex + 1, ready.book.pages.size)
        updateRecentPage(ready, nextPageIndex)
        uiState = ready.copy(pageIndex = nextPageIndex)
    }

    fun selectPage(pageIndex: Int) {
        val ready = uiState as? ReaderUiState.Ready ?: return
        val nextPageIndex = coercePageIndex(pageIndex, ready.book.pages.size)
        updateRecentPage(ready, nextPageIndex)
        uiState = ready.copy(pageIndex = nextPageIndex)
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

    fun handleBack() {
        when (uiState) {
            ReaderUiState.Empty -> {
                val backPath = browserBackPath
                if (isAddingToLibrary) {
                    isAddingToLibrary = false
                    selectedTabName = AppTab.Library.name
                } else if (currentTab == AppTab.Browse && backPath != null) {
                    currentDirectoryPath = backPath
                }
            }

            is ReaderUiState.Loading,
            is ReaderUiState.Error,
            is ReaderUiState.Ready,
            -> returnToBrowser()
        }
    }

    private fun showError(throwable: Throwable) {
        uiState = ReaderUiState.Error(
            when (throwable) {
                is ComicLoadException -> throwable.localizedMessage(appContext)
                else -> throwable.message?.takeIf { it.isNotBlank() }
                    ?: appContext.getString(R.string.open_comic_error_message)
            },
        )
    }

    private fun persistRecentComics(nextRecentComics: List<RecentComic>) {
        recentComics = nextRecentComics
        saveRecentComics(appContext, nextRecentComics)
    }

    private fun persistLibraryComics(nextLibraryComics: List<LibraryComic>) {
        libraryComics = nextLibraryComics
        saveLibraryComics(appContext, nextLibraryComics)
    }

    private fun persistLibraryMetadata(nextLibraryMetadata: List<LibraryComicMetadata>) {
        libraryMetadata = nextLibraryMetadata
        saveLibraryMetadata(appContext, nextLibraryMetadata)
    }

    private fun rememberOpenedComic(book: ComicBook, pageIndex: Int) {
        val recentComic = recentComicFor(
            book = book,
            pageIndex = pageIndex,
            lastOpenedAt = System.currentTimeMillis(),
        ) ?: return
        persistRecentComics(upsertRecentComic(recentComics, recentComic))
    }

    private fun updateRecentPage(ready: ReaderUiState.Ready, pageIndex: Int) {
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

    private fun showReadyComic(book: ComicBook, pageIndex: Int) {
        val clampedPageIndex = coercePageIndex(pageIndex, book.pages.size)
        rememberOpenedComic(book, clampedPageIndex)
        uiState = ReaderUiState.Ready(book = book, pageIndex = clampedPageIndex)
    }
}

@Composable
fun rememberComicViewerAppState(
    appContext: Context,
    hasFileAccess: Boolean,
): ComicViewerAppState {
    val scope = rememberCoroutineScope()
    val currentDirectoryPath = rememberSaveable { mutableStateOf(defaultBrowserRootPath()) }
    val uiState = rememberSaveable(stateSaver = ReaderUiStateSaver) {
        mutableStateOf<ReaderUiState>(ReaderUiState.Empty)
    }
    val selectedTab = rememberSaveable { mutableStateOf(AppTab.Library.name) }
    val isAddingToLibrary = rememberSaveable { mutableStateOf(false) }
    val recentComics = remember { loadRecentComics(appContext) }
    val libraryComics = remember { loadLibraryComics(appContext) }
    val libraryMetadata = remember { loadLibraryMetadata(appContext) }
    val showWelcome = remember {
        shouldShowWelcome(
            welcomeSeen = loadWelcomeSeen(appContext),
            hasLibraryComics = libraryComics.isNotEmpty(),
            hasRecentComics = recentComics.isNotEmpty(),
        )
    }
    val browserRootPath = remember { defaultBrowserRootPath() }

    val appState = remember(appContext, scope, browserRootPath) {
        ComicViewerAppState(
            appContext = appContext,
            scope = scope,
            hasFileAccess = hasFileAccess,
            currentDirectoryPath = currentDirectoryPath.value,
            uiState = uiState.value,
            selectedTabName = selectedTab.value,
            isAddingToLibrary = isAddingToLibrary.value,
            showWelcome = showWelcome,
            recentComics = recentComics,
            libraryComics = libraryComics,
            libraryMetadata = libraryMetadata,
            browserRootPath = browserRootPath,
        )
    }

    SideEffect {
        currentDirectoryPath.value = appState.currentDirectoryPath
        uiState.value = appState.uiState
        selectedTab.value = appState.selectedTabName
        isAddingToLibrary.value = appState.isAddingToLibrary
    }

    return appState
}

private fun defaultBrowserRootPath(): String =
    Environment.getExternalStorageDirectory().absolutePath
