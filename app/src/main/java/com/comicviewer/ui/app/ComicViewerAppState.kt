package com.comicviewer.ui.app

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import com.comicviewer.ComicBook
import com.comicviewer.ComicLoadException
import com.comicviewer.ComicSource
import com.comicviewer.GrantedStorageAccess
import com.comicviewer.R
import com.comicviewer.LibraryAutoScanState
import com.comicviewer.LibraryAutomationPreferences
import com.comicviewer.MAX_LIBRARY_COMICS
import com.comicviewer.LibraryComic
import com.comicviewer.LibraryComicDetails
import com.comicviewer.LibraryComicMetadata
import com.comicviewer.ReaderUiState
import com.comicviewer.RecentComic
import com.comicviewer.RecentComicSourceType
import com.comicviewer.archiveDocumentFor
import com.comicviewer.browserParentPathFor
import com.comicviewer.clearArchiveCache
import com.comicviewer.closeComicArchives
import com.comicviewer.coercePageIndex
import com.comicviewer.documentPermissionCovers
import com.comicviewer.folderDocumentFor
import com.comicviewer.libraryComicForDocumentArchive
import com.comicviewer.libraryComicForDocumentFolder
import com.comicviewer.libraryComicForArchive
import com.comicviewer.libraryComicForFolder
import com.comicviewer.loadLibraryComics
import com.comicviewer.loadLibraryComicDetails
import com.comicviewer.loadLibraryMetadata
import com.comicviewer.loadLibraryAutomationPreferences
import com.comicviewer.loadArchiveComic
import com.comicviewer.loadFolderComic
import com.comicviewer.loadLocalArchiveComic
import com.comicviewer.loadLocalFolderComic
import com.comicviewer.loadRecentComics
import com.comicviewer.loadWelcomeSeen
import com.comicviewer.matchingRecentComic
import com.comicviewer.mergeOpenedRecentComic
import com.comicviewer.newLibraryComicsForArchiveScan
import com.comicviewer.newLibraryComicsForDocumentArchiveScan
import com.comicviewer.newLibraryComicsForDocumentFolderScan
import com.comicviewer.recentComicFor
import com.comicviewer.releasePersistableReadPermission
import com.comicviewer.releaseUnusedPersistableReadPermissions
import com.comicviewer.removeLibraryComic
import com.comicviewer.detailsFor
import com.comicviewer.documentIdentityKey
import com.comicviewer.libraryComicDetailsAreCurrent
import com.comicviewer.markRecentComicRead
import com.comicviewer.removeRecentComic
import com.comicviewer.saveLibraryComics
import com.comicviewer.saveLibraryMetadata
import com.comicviewer.saveRecentComics
import com.comicviewer.saveWelcomeSeen
import com.comicviewer.shouldShowWelcome
import com.comicviewer.scanLibraryArchives
import com.comicviewer.scanLibraryDocumentArchives
import com.comicviewer.upsertLibraryComic
import com.comicviewer.upsertLibraryComics
import com.comicviewer.upsertRecentComic
import com.comicviewer.toggleLibraryFavorite
import com.comicviewer.setLibraryTags
import com.comicviewer.pruneLibraryMetadata
import com.comicviewer.pruneLibraryComicDetails
import com.comicviewer.inspectLibraryComic
import com.comicviewer.saveLibraryComicDetails
import com.comicviewer.upsertLibraryComicDetails
import com.comicviewer.updateRecentComicPage
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

sealed interface ComicDetailsTarget {
    data class Library(val comic: LibraryComic) : ComicDetailsTarget
    data class Recent(val comic: RecentComic) : ComicDetailsTarget
}

class ComicViewerAppState(
    private val appContext: Context,
    private val scope: CoroutineScope,
    uiState: ReaderUiState,
    selectedTabName: String,
    isAddingToLibrary: Boolean,
    showWelcome: Boolean,
    recentComics: List<RecentComic>,
    libraryComics: List<LibraryComic>,
    libraryMetadata: List<LibraryComicMetadata>,
    libraryDetails: List<LibraryComicDetails>,
    private val storageAccess: GrantedStorageAccess,
    currentDirectoryPath: String,
    val browserRootPath: String,
) {
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
    var libraryDetails by mutableStateOf(libraryDetails)
        private set
    var selectedComicDetails by mutableStateOf<ComicDetailsTarget?>(null)
        private set
    var libraryAutoScanState by mutableStateOf<LibraryAutoScanState>(LibraryAutoScanState.Idle)
        private set
    private var loadJob by mutableStateOf<Job?>(null)
    private var cacheCleanupJob: Job? = null

    /**
     * Read grants taken for documents that are still being added. They are not in the library yet,
     * so permission cleanup would otherwise release them while their names are being looked up.
     */
    private val pendingDocumentUris = mutableSetOf<String>()
    private var libraryAutoScanJob: Job? = null
    private val detailsRefreshSemaphore = Semaphore(permits = 2)
    private var detailsRefreshGeneration = 0
    private var autoDeleteTempFiles: Boolean = true
    private var importFolderUri: String? = null

    init {
        loadLibraryAutomationPreferences(appContext).let { preferences ->
            autoDeleteTempFiles = preferences.autoDeleteTempFiles
            importFolderUri = preferences.importFolderUri
        }
        (uiState as? ReaderUiState.Loading)?.let(::launchComicLoad)
    }

    val currentTab: AppTab
        get() = restoredAppTab(selectedTabName)

    val rawFileBrowserEnabled: Boolean
        get() = storageAccess is GrantedStorageAccess.RawFiles

    val documentBrowserRootUri: Uri?
        get() = (storageAccess as? GrantedStorageAccess.DocumentTree)?.uri

    val browserBackPath: String?
        get() = if (rawFileBrowserEnabled) {
            browserParentPathFor(
                currentPath = currentDirectoryPath,
                rootPath = browserRootPath,
            )
        } else {
            null
        }

    val shouldHandleBack: Boolean
        get() = uiState !is ReaderUiState.Empty ||
            selectedComicDetails != null ||
            isAddingToLibrary ||
            currentTab != AppTab.Library

    fun selectTab(tab: AppTab) {
        selectedTabName = tab.name
        selectedComicDetails = null
        if (tab != AppTab.Library) isAddingToLibrary = false
    }

    /** The import file browser replaces the Library grid until files are added or it is closed. */
    fun startArchiveImportBrowser() {
        selectedComicDetails = null
        selectedTabName = AppTab.Library.name
        isAddingToLibrary = true
    }

    fun closeImportBrowser() {
        isAddingToLibrary = false
        selectedTabName = AppTab.Library.name
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
        startArchiveImportBrowser()
    }

    fun changeDirectory(path: String) {
        if (rawFileBrowserEnabled) currentDirectoryPath = path
    }

    fun openArchive(uri: Uri) {
        selectedComicDetails = null
        startComicLoad(
            ReaderUiState.Loading(
                source = ComicSource.Archive(
                    uri = uri,
                    displayName = uri.fallbackDisplayName(appContext.getString(R.string.unnamed_comic_archive)),
                ),
                pageIndex = 0,
            ),
        )
    }

    fun openFolder(uri: Uri) {
        selectedComicDetails = null
        startComicLoad(
            ReaderUiState.Loading(
                source = ComicSource.Folder(
                    uri = uri,
                    displayName = uri.fallbackDisplayName(appContext.getString(R.string.unnamed_comic_folder)),
                ),
                pageIndex = 0,
            ),
        )
    }

    fun openLocalArchive(file: File) {
        selectedComicDetails = null
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
        selectedComicDetails = null
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
        selectedComicDetails = null
        readRecentComic(recentComic)
    }

    fun showRecentComicDetails(recentComic: RecentComic) {
        selectedComicDetails = ComicDetailsTarget.Recent(recentComic)
    }

    fun readRecentComic(recentComic: RecentComic) {
        val loading = ReaderUiState.Loading(
            source = recentComic.toComicSource(),
            pageIndex = recentComic.lastPageIndex,
        )
        // A failure can be temporary (an unmounted card, an offline provider), so the history entry
        // and its progress are kept; the user can remove it from History.
        startComicLoad(loading) { throwable ->
            if (throwable !is CancellationException) {
                selectedComicDetails = null
                showError(throwable)
            }
        }
    }

    fun openLibraryComic(libraryComic: LibraryComic) {
        selectedComicDetails = ComicDetailsTarget.Library(libraryComic)
        refreshComicDetails(libraryComic)
    }

    fun readLibraryComic(libraryComic: LibraryComic) {
        readLibraryComicAt(libraryComic, libraryComic.matchingRecentComic(recentComics)?.lastPageIndex ?: 0)
    }

    fun readLibraryComicAt(libraryComic: LibraryComic, pageIndex: Int) {
        selectedComicDetails = ComicDetailsTarget.Library(libraryComic)
        startComicLoad(
            loading = ReaderUiState.Loading(
                source = libraryComic.toComicSource(),
                pageIndex = pageIndex.coerceAtLeast(0),
            ),
        )
    }

    fun closeComicDetails() {
        selectedComicDetails = null
    }

    fun reloadPersistedLibraryState() {
        libraryAutoScanJob?.cancel()
        libraryAutoScanState = LibraryAutoScanState.Idle
        detailsRefreshGeneration += 1
        recentComics = loadRecentComics(appContext)
        libraryComics = loadLibraryComics(appContext)
        libraryMetadata = loadLibraryMetadata(appContext)
        libraryDetails = loadLibraryComicDetails(appContext)
        loadLibraryAutomationPreferences(appContext).let { preferences ->
            autoDeleteTempFiles = preferences.autoDeleteTempFiles
            importFolderUri = preferences.importFolderUri
        }
        selectedComicDetails = null
        isAddingToLibrary = false
        selectedTabName = AppTab.Library.name
    }

    fun updateAutoDeleteTempFiles(enabled: Boolean) {
        autoDeleteTempFiles = enabled
        if (enabled && uiState is ReaderUiState.Empty) clearArchiveCacheAfter(loadJob)
    }

    fun updateAutomationPreferences(preferences: LibraryAutomationPreferences) {
        importFolderUri = preferences.importFolderUri
        updateAutoDeleteTempFiles(preferences.autoDeleteTempFiles)
        releaseUnusedPersistableReadPermissions(appContext, retainedDocumentUris())
    }

    fun refreshComicDetails(libraryComic: LibraryComic) {
        val refreshGeneration = detailsRefreshGeneration
        scope.launch {
            val currentDetails = detailsFor(libraryComic, libraryDetails)
            if (libraryComicDetailsAreCurrent(appContext, libraryComic, currentDetails)) return@launch
            val inspected = detailsRefreshSemaphore.withPermit {
                try {
                    inspectLibraryComic(
                        context = appContext,
                        comic = libraryComic,
                        recentComic = libraryComic.matchingRecentComic(recentComics),
                    )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    null
                }
            } ?: return@launch
            if (refreshGeneration != detailsRefreshGeneration) return@launch
            if (libraryComics.none { comic ->
                    comic.sourceType == libraryComic.sourceType && comic.path == libraryComic.path
                }
            ) {
                return@launch
            }
            persistLibraryDetails(upsertLibraryComicDetails(libraryDetails, inspected))
        }
    }

    private fun startComicLoad(
        loading: ReaderUiState.Loading,
        onFailure: (Throwable) -> Unit = { throwable ->
            if (throwable !is CancellationException) showError(throwable)
        },
    ) {
        // A cleanup already deleting copies cannot be interrupted, so the load waits for it to end
        // instead of having the new comic's copy and sessions removed underneath it.
        val pendingCleanup = cacheCleanupJob
        pendingCleanup?.cancel()
        cacheCleanupJob = null
        loadJob?.cancel()
        uiState = loading
        launchComicLoad(loading, onFailure, pendingCleanup)
    }

    private fun launchComicLoad(
        loading: ReaderUiState.Loading,
        onFailure: (Throwable) -> Unit = { throwable ->
            if (throwable !is CancellationException) showError(throwable)
        },
        pendingCleanup: Job? = null,
    ) {
        loadJob = scope.launch {
            pendingCleanup?.join()
            try {
                val book = loadComic(loading.source)
                showReadyComic(book = book, pageIndex = loading.pageIndex)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Exception) {
                onFailure(throwable)
            }
        }
    }

    private suspend fun loadComic(source: ComicSource): ComicBook = when (source) {
        is ComicSource.Archive -> loadArchiveComic(context = appContext, uri = source.uri)
        is ComicSource.Folder -> loadFolderComic(appContext, source.uri)
        is ComicSource.LocalArchive -> loadLocalArchiveComic(
            context = appContext,
            archiveFile = File(source.filePath),
        )
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
        RecentComicSourceType.DocumentArchive -> ComicSource.Archive(
            uri = path.toUri(),
            displayName = displayName,
        )
        RecentComicSourceType.DocumentFolder -> ComicSource.Folder(
            uri = path.toUri(),
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
        RecentComicSourceType.DocumentArchive -> ComicSource.Archive(
            uri = path.toUri(),
            displayName = displayName,
        )
        RecentComicSourceType.DocumentFolder -> ComicSource.Folder(
            uri = path.toUri(),
            displayName = displayName,
        )
    }

    fun addArchivesToLibrary(files: List<File>) {
        if (files.isEmpty()) return
        val addedAt = System.currentTimeMillis()
        val newComics = files.mapIndexed { index, file ->
            libraryComicForArchive(file, addedAt = addedAt + index)
        }
        persistAddedLibraryComics(newComics)
        finishAddingToLibrary()
    }

    fun addFolderToLibrary(folder: File) {
        persistAddedLibraryComics(listOf(libraryComicForFolder(folder, addedAt = System.currentTimeMillis())))
        finishAddingToLibrary()
    }

    /**
     * Saves comics the user chose to add. Comics already in the library under another form of the same
     * document URI are skipped, and a full library keeps its comics and says so instead of dropping any.
     */
    private fun persistAddedLibraryComics(newComics: List<LibraryComic>) {
        val existingDocumentKeys = libraryComics
            .filter { comic -> comic.sourceType.isDocumentSource() }
            .associateBy { comic -> comic.sourceType to documentIdentityKey(comic.path) }
        val comicsToAdd = newComics.filter { comic ->
            val existing = existingDocumentKeys[comic.sourceType to documentIdentityKey(comic.path)]
            existing == null || existing.path == comic.path
        }
        val nextLibraryComics = upsertLibraryComics(libraryComics = libraryComics, newComics = comicsToAdd)
        persistLibraryComics(nextLibraryComics)
        val addedComics = comicsToAdd.filter { comic ->
            nextLibraryComics.any { stored -> stored.sourceType == comic.sourceType && stored.path == comic.path }
        }
        addedComics.forEach(::refreshComicDetails)
        val skippedForSpace = comicsToAdd.size - addedComics.size
        if (skippedForSpace > 0) {
            val message = appContext.resources.getQuantityString(
                R.plurals.library_full,
                skippedForSpace,
                skippedForSpace,
                MAX_LIBRARY_COMICS,
            )
            Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
        }
    }

    fun addArchiveDocumentsToLibrary(uris: List<Uri>) {
        val distinctUris = uris.distinctBy(Uri::toString)
        if (distinctUris.isEmpty()) return

        scope.launch {
            holdingDocumentPermissions(distinctUris) {
                val documents = withContext(Dispatchers.IO) {
                    distinctUris.map { uri -> archiveDocumentFor(appContext, uri) }
                }
                val addedAt = System.currentTimeMillis()
                val newComics = documents.mapIndexed { index, document ->
                    libraryComicForDocumentArchive(
                        uri = document.uri.toString(),
                        displayName = document.displayName,
                        addedAt = addedAt + index,
                    )
                }
                persistAddedLibraryComics(newComics)
                finishAddingToLibrary()
            }
            distinctUris.forEach(::releasePermissionIfUnused)
        }
    }

    fun addFolderDocumentToLibrary(uri: Uri) {
        scope.launch {
            holdingDocumentPermissions(listOf(uri)) {
                val document = withContext(Dispatchers.IO) { folderDocumentFor(appContext, uri) }
                val comic = libraryComicForDocumentFolder(
                    uri = document.uri.toString(),
                    displayName = document.displayName,
                    addedAt = System.currentTimeMillis(),
                )
                persistAddedLibraryComics(listOf(comic))
                finishAddingToLibrary()
            }
            releasePermissionIfUnused(uri)
        }
    }

    private fun finishAddingToLibrary() {
        dismissWelcome()
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
        persistLibraryDetails(pruneLibraryComicDetails(libraryDetails, nextLibraryComics))
        val target = selectedComicDetails as? ComicDetailsTarget.Library
        if (target?.comic?.sourceType == libraryComic.sourceType && target.comic.path == libraryComic.path) {
            selectedComicDetails = null
        }
    }

    fun markRead(libraryComic: LibraryComic) {
        persistRecentComics(
            markRecentComicRead(
                recentComics = recentComics,
                libraryComic = libraryComic,
                knownPageCount = detailsFor(libraryComic, libraryDetails)?.pageCount,
                nowMs = System.currentTimeMillis(),
            ),
        )
    }

    fun markUnread(libraryComic: LibraryComic) {
        persistRecentComics(
            removeRecentComic(
                recentComics = recentComics,
                sourceType = libraryComic.sourceType,
                path = libraryComic.path,
            ),
        )
    }

    fun removeFromHistory(recentComic: RecentComic) {
        persistRecentComics(
            nextRecentComics = removeRecentComic(
                recentComics = recentComics,
                sourceType = recentComic.sourceType,
                path = recentComic.path,
            ),
            releasePermissions = false,
        )
    }

    fun restoreToHistory(recentComic: RecentComic) {
        persistRecentComics(upsertRecentComic(recentComics, recentComic), releasePermissions = false)
    }

    fun clearHistory() {
        persistRecentComics(emptyList())
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
        startLibraryAutoScan(rootUri = null)
    }

    fun autoScanLibraryArchives(onFinished: (Boolean) -> Unit) {
        startLibraryAutoScan(rootUri = null, onFinished = onFinished)
    }

    fun autoScanLibraryFolder(rootUri: Uri) {
        startLibraryAutoScan(rootUri = rootUri)
    }

    fun autoScanLibraryFolder(rootUri: Uri, onFinished: (Boolean) -> Unit) {
        startLibraryAutoScan(rootUri = rootUri, onFinished = onFinished)
    }

    private fun startLibraryAutoScan(
        rootUri: Uri?,
        onFinished: ((Boolean) -> Unit)? = null,
    ) {
        if (libraryAutoScanState is LibraryAutoScanState.Scanning) {
            onFinished?.invoke(false)
            return
        }

        libraryAutoScanJob?.cancel()
        libraryAutoScanState = LibraryAutoScanState.Scanning
        libraryAutoScanJob = scope.launch {
            try {
                val result = performLibraryAutoScan(rootUri)
                if (result.newComics.isNotEmpty()) {
                    persistLibraryComics(
                        upsertLibraryComics(
                            libraryComics = libraryComics,
                            newComics = result.newComics,
                        ),
                    )
                    result.newComics.forEach(::refreshComicDetails)
                }
                libraryAutoScanState = LibraryAutoScanState.Complete(
                    foundCount = result.foundCount,
                    addedCount = result.newComics.size,
                    skippedExistingCount = result.skippedExistingCount,
                    scannedDirectoryCount = result.scannedDirectoryCount,
                    libraryLimitReached = result.libraryLimitReached,
                    directoryLimitReached = result.directoryLimitReached,
                )
                onFinished?.invoke(true)
            } catch (cancellation: CancellationException) {
                libraryAutoScanState = LibraryAutoScanState.Idle
                onFinished?.invoke(false)
                throw cancellation
            } catch (throwable: Exception) {
                libraryAutoScanState = LibraryAutoScanState.Failed(
                    (throwable as? ComicLoadException)?.localizedMessage(appContext)
                        ?: appContext.getString(R.string.library_auto_scan_failed),
                )
                onFinished?.invoke(false)
            }
        }
    }

    fun clearLibraryAutoScanResult() {
        if (libraryAutoScanState is LibraryAutoScanState.Complete) {
            libraryAutoScanState = LibraryAutoScanState.Idle
        }
    }

    private suspend fun performLibraryAutoScan(rootUri: Uri?): LibraryScanSummary = when {
        rootUri != null -> {
            val existingArchiveUris = libraryComics
                .filter { comic -> comic.sourceType == RecentComicSourceType.DocumentArchive }
                .mapTo(mutableSetOf()) { comic -> comic.path }
            val existingFolderUris = libraryComics
                .filter { comic -> comic.sourceType == RecentComicSourceType.DocumentFolder }
                .mapTo(mutableSetOf()) { comic -> comic.path }
            val scanResult = scanLibraryDocumentArchives(
                context = appContext,
                rootUri = rootUri,
                includeImageFolders = true,
                includePdfs = true,
                knownUris = existingArchiveUris + existingFolderUris,
            )
            val existingArchiveKeys = existingArchiveUris.mapTo(mutableSetOf(), ::documentIdentityKey)
            val existingFolderKeys = existingFolderUris.mapTo(mutableSetOf(), ::documentIdentityKey)
            val newArchiveCandidateCount = scanResult.candidates
                .map { candidate -> documentIdentityKey(candidate.uri) }
                .distinct()
                .count { key -> key !in existingArchiveKeys }
            val newFolderCandidateCount = scanResult.folderCandidates
                .map { candidate -> documentIdentityKey(candidate.uri) }
                .distinct()
                .count { key -> key !in existingFolderKeys }
            val addedAt = System.currentTimeMillis()
            val newArchives = newLibraryComicsForDocumentArchiveScan(
                libraryComics = libraryComics,
                candidates = scanResult.candidates,
                addedAt = addedAt,
            )
            val newFolders = newLibraryComicsForDocumentFolderScan(
                libraryComics = libraryComics + newArchives,
                candidates = scanResult.folderCandidates,
                addedAt = addedAt + newArchives.size,
            )
            val newComics = newArchives + newFolders
            val foundCount = scanResult.candidates.size + scanResult.folderCandidates.size
            val newCandidateCount = newArchiveCandidateCount + newFolderCandidateCount
            LibraryScanSummary(
                foundCount = foundCount,
                skippedExistingCount = foundCount - newCandidateCount,
                scannedDirectoryCount = scanResult.scannedDirectoryCount,
                libraryLimitReached = scanResult.candidateLimitReached || newCandidateCount > newComics.size,
                directoryLimitReached = scanResult.directoryLimitReached,
                newComics = newComics,
            )
        }

        storageAccess is GrantedStorageAccess.RawFiles -> {
            val access = storageAccess
            val existingPaths = libraryComics
                .filter { comic -> comic.sourceType == RecentComicSourceType.LocalArchive }
                .mapTo(mutableSetOf()) { comic -> comic.path }
            val scanResult = scanLibraryArchives(
                root = File(access.rootPath),
                knownArchivePaths = existingPaths,
            )
            val newCandidateCount = scanResult.candidates
                .map { candidate -> candidate.archiveFile.absolutePath }
                .distinct()
                .count { path -> path !in existingPaths }
            val newComics = newLibraryComicsForArchiveScan(
                libraryComics = libraryComics,
                candidates = scanResult.candidates,
                addedAt = System.currentTimeMillis(),
            )
            LibraryScanSummary(
                foundCount = scanResult.candidates.size,
                skippedExistingCount = scanResult.candidates.size - newCandidateCount,
                scannedDirectoryCount = scanResult.scannedDirectoryCount,
                libraryLimitReached = scanResult.candidateLimitReached || newCandidateCount > newComics.size,
                directoryLimitReached = scanResult.directoryLimitReached,
                newComics = newComics,
            )
        }

        storageAccess is GrantedStorageAccess.DocumentTree -> {
            val access = storageAccess
            val existingUris = libraryComics
                .filter { comic -> comic.sourceType == RecentComicSourceType.DocumentArchive }
                .mapTo(mutableSetOf()) { comic -> comic.path }
            val scanResult = scanLibraryDocumentArchives(
                context = appContext,
                rootUri = access.uri,
                includeImageFolders = false,
                knownUris = existingUris,
            )
            val existingKeys = existingUris.mapTo(mutableSetOf(), ::documentIdentityKey)
            val newCandidateCount = scanResult.candidates
                .map { candidate -> documentIdentityKey(candidate.uri) }
                .distinct()
                .count { key -> key !in existingKeys }
            val newComics = newLibraryComicsForDocumentArchiveScan(
                libraryComics = libraryComics,
                candidates = scanResult.candidates,
                addedAt = System.currentTimeMillis(),
            )
            LibraryScanSummary(
                foundCount = scanResult.candidates.size,
                skippedExistingCount = scanResult.candidates.size - newCandidateCount,
                scannedDirectoryCount = scanResult.scannedDirectoryCount,
                libraryLimitReached = scanResult.candidateLimitReached || newCandidateCount > newComics.size,
                directoryLimitReached = scanResult.directoryLimitReached,
                newComics = newComics,
            )
        }

        else -> error("Unsupported storage access")
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
        val source = uiState.comicSourceOrNull()
        val closingJob = loadJob
        closingJob?.cancel()
        uiState = ReaderUiState.Empty
        if (autoDeleteTempFiles) {
            clearArchiveCacheAfter(closingJob)
        } else {
            scope.launch { closeComicArchives() }
        }
        source?.documentUriOrNull()?.let(::releasePermissionIfUnused)
    }

    fun returnToBrowser() = closeComic()

    fun handleBack() {
        when (uiState) {
            ReaderUiState.Empty -> {
                if (selectedComicDetails != null) {
                    selectedComicDetails = null
                    return
                }
                val backPath = browserBackPath
                if (isAddingToLibrary && backPath != null) {
                    currentDirectoryPath = backPath
                } else if (isAddingToLibrary) {
                    closeImportBrowser()
                } else if (currentTab != AppTab.Library) {
                    selectedTabName = AppTab.Library.name
                }
            }

            is ReaderUiState.Loading,
            is ReaderUiState.Error,
            is ReaderUiState.Ready,
            -> returnToBrowser()
        }
    }

    private fun showError(throwable: Throwable) {
        val failedDocumentUri = uiState.comicSourceOrNull()?.documentUriOrNull()
        uiState = ReaderUiState.Error(
            when (throwable) {
                is ComicLoadException -> throwable.localizedMessage(appContext)
                else -> throwable.message?.takeIf { it.isNotBlank() }
                    ?: appContext.getString(R.string.open_comic_error_message)
            },
        )
        failedDocumentUri?.let(::releasePermissionIfUnused)
    }

    /**
     * With [releasePermissions] false, document permissions no longer needed are kept until the
     * next save, so an Undo can still restore an entry that was opened without adding it.
     */
    private fun persistRecentComics(nextRecentComics: List<RecentComic>, releasePermissions: Boolean = true) {
        recentComics = nextRecentComics
        saveRecentComics(appContext, nextRecentComics)
        if (releasePermissions) releaseUnusedPersistableReadPermissions(appContext, retainedDocumentUris())
    }

    private fun persistLibraryComics(nextLibraryComics: List<LibraryComic>) {
        libraryComics = nextLibraryComics
        saveLibraryComics(appContext, nextLibraryComics)
        releaseUnusedPersistableReadPermissions(appContext, retainedDocumentUris())
    }

    private fun persistLibraryMetadata(nextLibraryMetadata: List<LibraryComicMetadata>) {
        libraryMetadata = nextLibraryMetadata
        saveLibraryMetadata(appContext, nextLibraryMetadata)
    }

    private fun persistLibraryDetails(nextDetails: List<LibraryComicDetails>) {
        libraryDetails = nextDetails
        saveLibraryComicDetails(appContext, nextDetails)
    }

    private fun rememberOpenedComic(book: ComicBook, pageIndex: Int) {
        val recentComic = recentComicFor(
            book = book,
            pageIndex = pageIndex,
            lastOpenedAt = System.currentTimeMillis(),
        ) ?: return
        val existing = recentComics.firstOrNull {
            it.sourceType == recentComic.sourceType && it.path == recentComic.path
        }
        persistRecentComics(
            upsertRecentComic(
                recentComics,
                mergeOpenedRecentComic(existing = existing, opened = recentComic),
            ),
        )
    }

    private fun updateRecentPage(ready: ReaderUiState.Ready, pageIndex: Int) {
        val recentComic = recentComicFor(
            book = ready.book,
            pageIndex = pageIndex,
            lastOpenedAt = 0L,
        ) ?: return
        // A page turn cannot change which documents are kept, so permissions are left alone.
        persistRecentComics(
            updateRecentComicPage(
                recentComics = recentComics,
                sourceType = recentComic.sourceType,
                path = recentComic.path,
                pageIndex = pageIndex,
                pageCount = ready.book.pages.size,
            ),
            releasePermissions = false,
        )
    }

    private fun showReadyComic(book: ComicBook, pageIndex: Int) {
        val clampedPageIndex = coercePageIndex(pageIndex, book.pages.size)
        rememberOpenedComic(book, clampedPageIndex)
        uiState = ReaderUiState.Ready(book = book, pageIndex = clampedPageIndex)
    }

    private fun clearArchiveCacheAfter(job: Job?) {
        cacheCleanupJob?.cancel()
        cacheCleanupJob = scope.launch {
            if (job != null && !job.isCompleted) job.join()
            if (
                autoDeleteTempFiles &&
                uiState is ReaderUiState.Empty &&
                (job == null || loadJob === job)
            ) {
                clearArchiveCache(appContext)
            }
        }
    }

    private fun releasePermissionIfUnused(uri: Uri) {
        val uriString = uri.toString()
        val retainedUris = retainedDocumentUris()
        if (retainedUris.none { retained -> documentPermissionCovers(uriString, retained) }) {
            releasePersistableReadPermission(appContext, uri)
        }
        releaseUnusedPersistableReadPermissions(appContext, retainedUris)
    }

    private fun persistedDocumentUris(): Set<String> = buildSet {
        recentComics.forEach { comic ->
            if (comic.sourceType.isDocumentSource()) add(comic.path)
        }
        libraryComics.forEach { comic ->
            if (comic.sourceType.isDocumentSource()) add(comic.path)
        }
    }

    /** Keeps [uris] out of permission cleanup while [block] adds them to the app's saved state. */
    suspend fun <T> holdingDocumentPermissions(uris: List<Uri>, block: suspend () -> T): T {
        val keys = uris.map(Uri::toString)
        pendingDocumentUris += keys
        try {
            return block()
        } finally {
            pendingDocumentUris -= keys.toSet()
        }
    }

    private fun retainedDocumentUris(): Set<String> = buildSet {
        addAll(pendingDocumentUris)
        (storageAccess as? GrantedStorageAccess.DocumentTree)?.uri?.toString()?.let(::add)
        importFolderUri?.let(::add)
        addAll(persistedDocumentUris())
        uiState.comicSourceOrNull()?.documentUriOrNull()?.toString()?.let(::add)
    }
}

@Composable
fun rememberComicViewerAppState(
    appContext: Context,
    storageAccess: GrantedStorageAccess,
): ComicViewerAppState {
    val scope = rememberCoroutineScope()
    val browserRootPath = (storageAccess as? GrantedStorageAccess.RawFiles)?.rootPath.orEmpty()
    val currentDirectoryPath = rememberSaveable(browserRootPath) { mutableStateOf(browserRootPath) }
    val uiState = rememberSaveable(stateSaver = ReaderUiStateSaver) {
        mutableStateOf<ReaderUiState>(ReaderUiState.Empty)
    }
    val selectedTab = rememberSaveable { mutableStateOf(AppTab.Library.name) }
    val isAddingToLibrary = rememberSaveable { mutableStateOf(false) }
    val recentComics = remember { loadRecentComics(appContext) }
    val libraryComics = remember { loadLibraryComics(appContext) }
    val libraryMetadata = remember { loadLibraryMetadata(appContext) }
    val libraryDetails = remember { loadLibraryComicDetails(appContext) }
    val showWelcome = remember {
        shouldShowWelcome(
            welcomeSeen = loadWelcomeSeen(appContext),
            hasLibraryComics = libraryComics.isNotEmpty(),
            hasRecentComics = recentComics.isNotEmpty(),
        )
    }
    val appState = remember(appContext, scope, storageAccess, browserRootPath) {
        ComicViewerAppState(
            appContext = appContext,
            scope = scope,
            uiState = uiState.value,
            selectedTabName = selectedTab.value,
            isAddingToLibrary = isAddingToLibrary.value,
            showWelcome = showWelcome,
            recentComics = recentComics,
            libraryComics = libraryComics,
            libraryMetadata = libraryMetadata,
            libraryDetails = libraryDetails,
            storageAccess = storageAccess,
            currentDirectoryPath = currentDirectoryPath.value,
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

private data class LibraryScanSummary(
    val foundCount: Int,
    val skippedExistingCount: Int,
    val scannedDirectoryCount: Int,
    val libraryLimitReached: Boolean,
    val directoryLimitReached: Boolean,
    val newComics: List<LibraryComic>,
)

private fun Uri.fallbackDisplayName(fallback: String): String =
    lastPathSegment
        ?.substringAfterLast('/')
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.take(512)
        ?: fallback

private fun ReaderUiState.comicSourceOrNull(): ComicSource? = when (this) {
    is ReaderUiState.Loading -> source
    is ReaderUiState.Ready -> book.source
    ReaderUiState.Empty,
    is ReaderUiState.Error,
    -> null
}

private fun ComicSource.documentUriOrNull(): Uri? = when (this) {
    is ComicSource.Archive -> uri
    is ComicSource.Folder -> uri
    is ComicSource.LocalArchive,
    is ComicSource.LocalFolder,
    -> null
}

private fun RecentComicSourceType.isDocumentSource(): Boolean =
    this == RecentComicSourceType.DocumentArchive || this == RecentComicSourceType.DocumentFolder
