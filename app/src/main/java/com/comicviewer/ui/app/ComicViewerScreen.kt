package com.comicviewer.ui.app

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.filled.Folder
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.comicviewer.AppLanguage
import com.comicviewer.AppThemeMode
import com.comicviewer.LibraryAutomationPreferences
import com.comicviewer.LibraryAutoScanState
import com.comicviewer.LibraryComic
import com.comicviewer.LibraryComicDetails
import com.comicviewer.LibraryComicMetadata
import com.comicviewer.ComicSource
import com.comicviewer.ReaderPreferences
import com.comicviewer.ReaderUiState
import com.comicviewer.RecentComic
import com.comicviewer.RecentComicSourceType
import com.comicviewer.metadataFor
import com.comicviewer.billing.ProBillingState
import com.comicviewer.ui.common.softContentTransform
import com.comicviewer.ui.library.ComicDetailsScreen
import com.comicviewer.ui.reader.ComicReaderScreen
import com.comicviewer.ui.reader.ErrorComicScreen
import com.comicviewer.ui.reader.LoadingComicScreen
import com.comicviewer.ui.theme.AppColor
import com.comicviewer.ui.theme.AppThemePreset
import java.io.File

@Composable
fun ComicViewerScreen(
    state: ReaderUiState,
    selectedTab: AppTab,
    currentDirectoryPath: String,
    browserRootPath: String,
    rawFileBrowserEnabled: Boolean,
    documentBrowserRootUri: Uri?,
    libraryComics: List<LibraryComic>,
    libraryMetadata: List<LibraryComicMetadata>,
    libraryDetails: List<LibraryComicDetails>,
    libraryAutoScanState: LibraryAutoScanState,
    recentComics: List<RecentComic>,
    selectedComicDetails: ComicDetailsTarget?,
    isAddingToLibrary: Boolean,
    proBillingState: ProBillingState,
    readerPreferences: ReaderPreferences,
    automationPreferences: LibraryAutomationPreferences,
    isDebugBuild: Boolean,
    debugProUnlocked: Boolean,
    selectedLanguage: AppLanguage,
    themeUiState: ThemeUiState,
    onBuyPro: () -> Unit,
    onRestorePro: () -> Unit,
    onReaderPreferencesChanged: (ReaderPreferences) -> Unit,
    onChooseImportFolder: () -> Unit,
    onAutomationPreferencesChanged: (LibraryAutomationPreferences) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onDebugProUnlockedChanged: (Boolean) -> Unit,
    onLanguageSelected: (AppLanguage) -> Unit,
    onThemeModeSelected: (AppThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onAccentColorSelected: (AppColor) -> Unit,
    onThemePresetSelected: (AppThemePreset) -> Unit,
    onAmoledThemeChanged: (Boolean) -> Unit,
    onTabSelected: (AppTab) -> Unit,
    onMarkRead: (LibraryComic) -> Unit,
    onMarkUnread: (LibraryComic) -> Unit,
    onRemoveFromHistory: (RecentComic) -> Unit,
    onRestoreToHistory: (RecentComic) -> Unit,
    onClearHistory: () -> Unit,
    onStartArchiveImportBrowser: () -> Unit,
    onCloseImportBrowser: () -> Unit,
    onAutoScanLibrary: () -> Unit,
    onClearLibraryAutoScanResult: () -> Unit,
    onDirectoryChange: (String) -> Unit,
    onOpenLocalArchive: (File) -> Unit,
    onOpenLocalFolder: (File) -> Unit,
    onOpenArchivePicker: () -> Unit,
    onOpenFolderPicker: () -> Unit,
    onAddArchivesPicker: () -> Unit,
    onAddFolderPicker: () -> Unit,
    onOpenRecentComic: (RecentComic) -> Unit,
    onShowRecentComicDetails: (RecentComic) -> Unit,
    onReadRecentComic: (RecentComic) -> Unit,
    onOpenLibraryComic: (LibraryComic) -> Unit,
    onReadLibraryComic: (LibraryComic) -> Unit,
    onReadLibraryComicAt: (LibraryComic, Int) -> Unit,
    onCloseComicDetails: () -> Unit,
    onAddLocalArchivesToLibrary: (List<File>) -> Unit,
    onAddLocalFolderToLibrary: (File) -> Unit,
    onAddDocumentArchivesToLibrary: (List<Uri>) -> Unit,
    onRemoveLibraryComic: (LibraryComic) -> Unit,
    onToggleLibraryFavorite: (LibraryComic) -> Unit,
    onSetLibraryTags: (LibraryComic, List<String>) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPageSelected: (Int) -> Unit,
    onCloseComic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state is ReaderUiState.Ready) {
        val readerLibraryComic = libraryComics.firstOrNull { it.matches(state.book.source) }
        ComicReaderScreen(
            book = state.book,
            pageIndex = state.pageIndex,
            readerPreferences = readerPreferences,
            isProUnlocked = proBillingState.isProUnlocked,
            proBillingState = proBillingState,
            isDebugBuild = isDebugBuild,
            debugProUnlocked = debugProUnlocked,
            onReaderPreferencesChanged = onReaderPreferencesChanged,
            onBuyPro = onBuyPro,
            onRestorePro = onRestorePro,
            onDebugProUnlockedChanged = onDebugProUnlockedChanged,
            onPrevious = onPrevious,
            onNext = onNext,
            onPageSelected = onPageSelected,
            onCloseComic = onCloseComic,
            favorite = readerLibraryComic?.let {
                proBillingState.isProUnlocked && metadataFor(it, libraryMetadata).favorite
            },
            onToggleFavorite = {
                readerLibraryComic?.let(onToggleLibraryFavorite)
            },
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
                ReaderUiState.Empty -> if (selectedComicDetails != null) {
                    ComicDetailsScreen(
                        target = selectedComicDetails,
                        libraryComics = libraryComics,
                        libraryMetadata = libraryMetadata,
                        libraryDetails = libraryDetails,
                        recentComics = recentComics,
                        proBillingState = proBillingState,
                        isDebugBuild = isDebugBuild,
                        debugProUnlocked = debugProUnlocked,
                        onDebugProUnlockedChanged = onDebugProUnlockedChanged,
                        onBuyPro = onBuyPro,
                        onRestorePro = onRestorePro,
                        onReadLibraryComic = onReadLibraryComic,
                        onReadLibraryComicAt = onReadLibraryComicAt,
                        onReadRecentComic = onReadRecentComic,
                        onMarkRead = onMarkRead,
                        onMarkUnread = onMarkUnread,
                        onToggleFavorite = onToggleLibraryFavorite,
                        onSetTags = onSetLibraryTags,
                        onRemoveComic = onRemoveLibraryComic,
                        onBack = onCloseComicDetails,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else HomeScreen(
                    selectedTab = selectedTab,
                    currentDirectoryPath = currentDirectoryPath,
                    browserRootPath = browserRootPath,
                    rawFileBrowserEnabled = rawFileBrowserEnabled,
                    documentBrowserRootUri = documentBrowserRootUri,
                    libraryComics = libraryComics,
                    libraryMetadata = libraryMetadata,
                    libraryDetails = libraryDetails,
                    libraryAutoScanState = libraryAutoScanState,
                    recentComics = recentComics,
                    isAddingToLibrary = isAddingToLibrary,
                    proBillingState = proBillingState,
                    readerPreferences = readerPreferences,
                    automationPreferences = automationPreferences,
                    isDebugBuild = isDebugBuild,
                    debugProUnlocked = debugProUnlocked,
                    selectedLanguage = selectedLanguage,
                    themeUiState = themeUiState,
                    onBuyPro = onBuyPro,
                    onRestorePro = onRestorePro,
                    onReaderPreferencesChanged = onReaderPreferencesChanged,
                    onChooseImportFolder = onChooseImportFolder,
                    onAutomationPreferencesChanged = onAutomationPreferencesChanged,
                    onExportBackup = onExportBackup,
                    onImportBackup = onImportBackup,
                    onDebugProUnlockedChanged = onDebugProUnlockedChanged,
                    onLanguageSelected = onLanguageSelected,
                    onThemeModeSelected = onThemeModeSelected,
                    onDynamicColorChanged = onDynamicColorChanged,
                    onAccentColorSelected = onAccentColorSelected,
                    onThemePresetSelected = onThemePresetSelected,
                    onAmoledThemeChanged = onAmoledThemeChanged,
                    onTabSelected = onTabSelected,
                    onReadLibraryComic = onReadLibraryComic,
                    onMarkRead = onMarkRead,
                    onMarkUnread = onMarkUnread,
                    onRemoveFromHistory = onRemoveFromHistory,
                    onRestoreToHistory = onRestoreToHistory,
                    onClearHistory = onClearHistory,
                    onStartArchiveImportBrowser = onStartArchiveImportBrowser,
                    onCloseImportBrowser = onCloseImportBrowser,
                    onAutoScanLibrary = onAutoScanLibrary,
                    onClearLibraryAutoScanResult = onClearLibraryAutoScanResult,
                    onDirectoryChange = onDirectoryChange,
                    onOpenLocalArchive = onOpenLocalArchive,
                    onOpenLocalFolder = onOpenLocalFolder,
                    onOpenArchivePicker = onOpenArchivePicker,
                    onOpenFolderPicker = onOpenFolderPicker,
                    onAddArchivesPicker = onAddArchivesPicker,
                    onAddFolderPicker = onAddFolderPicker,
                    onOpenRecentComic = onOpenRecentComic,
                    onShowRecentComicDetails = onShowRecentComicDetails,
                    onOpenLibraryComic = onOpenLibraryComic,
                    onAddLocalArchivesToLibrary = onAddLocalArchivesToLibrary,
                    onAddLocalFolderToLibrary = onAddLocalFolderToLibrary,
                    onAddDocumentArchivesToLibrary = onAddDocumentArchivesToLibrary,
                    onRemoveLibraryComic = onRemoveLibraryComic,
                    onToggleLibraryFavorite = onToggleLibraryFavorite,
                    onSetLibraryTags = onSetLibraryTags,
                )

                is ReaderUiState.Loading -> LoadingComicScreen()

                is ReaderUiState.Error -> ErrorComicScreen(
                    message = targetState.message,
                    onBackToBrowser = onCloseComic,
                )

                is ReaderUiState.Ready -> Unit
            }
        }
    }
}

private fun LibraryComic.matches(source: ComicSource): Boolean = when (source) {
    is ComicSource.LocalArchive -> sourceType == RecentComicSourceType.LocalArchive && path == source.filePath
    is ComicSource.LocalFolder -> sourceType == RecentComicSourceType.LocalFolder && path == source.folderPath
    is ComicSource.Archive -> sourceType == RecentComicSourceType.DocumentArchive && path == source.uri.toString()
    is ComicSource.Folder -> sourceType == RecentComicSourceType.DocumentFolder && path == source.uri.toString()
}
