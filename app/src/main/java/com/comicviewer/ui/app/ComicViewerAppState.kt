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
import com.comicviewer.R
import com.comicviewer.LibraryComic
import com.comicviewer.LibraryAutoScanState
import com.comicviewer.ReaderUiState
import com.comicviewer.RecentComic
import com.comicviewer.RecentComicSourceType
import com.comicviewer.browserParentPathFor
import com.comicviewer.clearArchiveCache
import com.comicviewer.coercePageIndex
import com.comicviewer.libraryComicForArchive
import com.comicviewer.libraryComicForFolder
import com.comicviewer.loadLibraryComics
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
import com.comicviewer.saveRecentComics
import com.comicviewer.saveWelcomeSeen
import com.comicviewer.scanLibraryArchives
import com.comicviewer.shouldShowWelcome
import com.comicviewer.upsertLibraryComic
import com.comicviewer.upsertLibraryComics
import com.comicviewer.upsertRecentComic
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
    var libraryAutoScanState by mutableStateOf<LibraryAutoScanState>(LibraryAutoScanState.Idle)
        private set
    private var loadJob by mutableStateOf<Job?>(null)
    private var libraryAutoScanJob: Job? = null

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
        persistLibraryComics(
            removeLibraryComic(
                libraryComics = libraryComics,
                sourceType = libraryComic.sourceType,
                path = libraryComic.path,
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

            ReaderUiState.Loading,
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
