package com.comicviewer.ui.app

import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.comicviewer.R
import com.comicviewer.AppLanguage
import com.comicviewer.AppThemeMode
import com.comicviewer.LibraryAutomationPreferences
import com.comicviewer.LibraryAutoScanState
import com.comicviewer.LibraryComic
import com.comicviewer.LibraryComicDetails
import com.comicviewer.LibraryComicMetadata
import com.comicviewer.ReaderPreferences
import com.comicviewer.RecentComic
import com.comicviewer.currentAppBuildInfo
import com.comicviewer.isTabletWidth
import com.comicviewer.billing.ProBillingState
import com.comicviewer.ui.browser.DocumentBrowserScreen
import com.comicviewer.ui.browser.FileBrowserScreen
import com.comicviewer.ui.common.rememberThrottledAction
import com.comicviewer.ui.common.softContentTransform
import com.comicviewer.ui.history.HistoryScreen
import com.comicviewer.ui.library.LibraryScreen
import com.comicviewer.ui.settings.SettingsScreen
import com.comicviewer.ui.theme.AppColor
import com.comicviewer.ui.theme.AppThemePreset
import java.io.File
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
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
    onOpenLibraryComic: (LibraryComic) -> Unit,
    onReadLibraryComic: (LibraryComic) -> Unit,
    onAddLocalArchivesToLibrary: (List<File>) -> Unit,
    onAddLocalFolderToLibrary: (File) -> Unit,
    onAddDocumentArchivesToLibrary: (List<Uri>) -> Unit,
    onRemoveLibraryComic: (LibraryComic) -> Unit,
    onToggleLibraryFavorite: (LibraryComic) -> Unit,
    onSetLibraryTags: (LibraryComic, List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val tabletLayout = isTabletWidth(maxWidth.value.roundToInt())
            val tabContent: @Composable () -> Unit = {
                Box(modifier = Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = { softContentTransform() },
                        label = "tabContentTransition",
                        modifier = Modifier.fillMaxSize(),
                    ) { targetTab ->
                        when (targetTab) {
                            AppTab.Library -> when {
                                rawFileBrowserEnabled && isAddingToLibrary -> FileBrowserScreen(
                                    currentDirectoryPath = currentDirectoryPath,
                                    browserRootPath = browserRootPath,
                                    recentComics = recentComics,
                                    isAddingToLibrary = true,
                                    onDirectoryChange = onDirectoryChange,
                                    onOpenLocalArchive = onOpenLocalArchive,
                                    onOpenLocalFolder = onOpenLocalFolder,
                                    onOpenRecentComic = onOpenRecentComic,
                                    onAddLocalArchivesToLibrary = onAddLocalArchivesToLibrary,
                                    onAddLocalFolderToLibrary = onAddLocalFolderToLibrary,
                                    onCancelImport = onCloseImportBrowser,
                                    modifier = Modifier.fillMaxSize(),
                                )

                                documentBrowserRootUri != null && isAddingToLibrary -> DocumentBrowserScreen(
                                    rootUri = documentBrowserRootUri,
                                    onAddArchives = onAddDocumentArchivesToLibrary,
                                    onCancelImport = onCloseImportBrowser,
                                    modifier = Modifier.fillMaxSize(),
                                )

                                else -> LibraryScreen(
                                    libraryComics = libraryComics,
                                    libraryMetadata = libraryMetadata,
                                    libraryDetails = libraryDetails,
                                    libraryAutoScanState = libraryAutoScanState,
                                    recentComics = recentComics,
                                    proBillingState = proBillingState,
                                    onImportArchives = if (rawFileBrowserEnabled || documentBrowserRootUri != null) {
                                        onStartArchiveImportBrowser
                                    } else {
                                        onAddArchivesPicker
                                    },
                                    onAutoScanLibrary = onAutoScanLibrary,
                                    onScanFolder = onChooseImportFolder,
                                    onAddFolderAsComic = onAddFolderPicker,
                                    onOpenArchiveWithoutAdding = onOpenArchivePicker,
                                    onOpenFolderWithoutAdding = onOpenFolderPicker,
                                    onClearAutoScanResult = onClearLibraryAutoScanResult,
                                    onBuyPro = onBuyPro,
                                    onRestorePro = onRestorePro,
                                    isDebugBuild = isDebugBuild,
                                    debugProUnlocked = debugProUnlocked,
                                    onDebugProUnlockedChanged = onDebugProUnlockedChanged,
                                    onOpenLibraryComic = onOpenLibraryComic,
                                    onReadLibraryComic = onReadLibraryComic,
                                    onMarkRead = onMarkRead,
                                    onMarkUnread = onMarkUnread,
                                    onRemoveLibraryComic = onRemoveLibraryComic,
                                    onToggleFavorite = onToggleLibraryFavorite,
                                    onSetTags = onSetLibraryTags,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            AppTab.History -> HistoryScreen(
                                recentComics = recentComics,
                                libraryDetails = libraryDetails,
                                onOpenComic = onShowRecentComicDetails,
                                onRemoveFromHistory = onRemoveFromHistory,
                                onRestoreToHistory = onRestoreToHistory,
                                onClearHistory = onClearHistory,
                                modifier = Modifier.fillMaxSize(),
                            )

                            AppTab.Settings -> SettingsScreen(
                                selectedLanguage = selectedLanguage,
                                themeUiState = themeUiState,
                                readerPreferences = readerPreferences,
                                automationPreferences = automationPreferences,
                                proBillingState = proBillingState,
                                isDebugBuild = isDebugBuild,
                                debugProUnlocked = debugProUnlocked,
                                buildInfo = currentAppBuildInfo(),
                                onBuyPro = onBuyPro,
                                onRestorePro = onRestorePro,
                                onDebugProUnlockedChanged = onDebugProUnlockedChanged,
                                onLanguageSelected = onLanguageSelected,
                                onThemeModeSelected = onThemeModeSelected,
                                onDynamicColorChanged = onDynamicColorChanged,
                                onAccentColorSelected = onAccentColorSelected,
                                onThemePresetSelected = onThemePresetSelected,
                                onAmoledThemeChanged = onAmoledThemeChanged,
                                onReaderPreferencesChanged = onReaderPreferencesChanged,
                                onChooseImportFolder = onChooseImportFolder,
                                onAutomationPreferencesChanged = onAutomationPreferencesChanged,
                                onExportBackup = onExportBackup,
                                onImportBackup = onImportBackup,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }

            if (tabletLayout) {
                Row(modifier = Modifier.fillMaxSize()) {
                    AppNavigationRail(
                        selectedTab = selectedTab,
                        onTabSelected = onTabSelected,
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
                    ) {
                        tabContent()
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        tabContent()
                    }
                    AppBottomNavigation(
                        selectedTab = selectedTab,
                        onTabSelected = onTabSelected,
                    )
                }
            }
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
    val throttledHistoryClick = rememberThrottledAction { onTabSelected(AppTab.History) }
    val throttledSettingsClick = rememberThrottledAction { onTabSelected(AppTab.Settings) }
    NavigationBar(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        NavigationBarItem(
            selected = selectedTab == AppTab.Library,
            onClick = throttledLibraryClick,
            icon = { Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_library)) },
        )
        NavigationBarItem(
            selected = selectedTab == AppTab.History,
            onClick = throttledHistoryClick,
            icon = { Icon(Icons.Filled.History, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_history)) },
        )
        NavigationBarItem(
            selected = selectedTab == AppTab.Settings,
            onClick = throttledSettingsClick,
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_settings)) },
        )
    }
}

@Composable
fun AppNavigationRail(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val throttledLibraryClick = rememberThrottledAction { onTabSelected(AppTab.Library) }
    val throttledHistoryClick = rememberThrottledAction { onTabSelected(AppTab.History) }
    val throttledSettingsClick = rememberThrottledAction { onTabSelected(AppTab.Settings) }
    NavigationRail(
        modifier = modifier
            .fillMaxHeight()
            .systemBarsPadding(),
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        NavigationRailItem(
            selected = selectedTab == AppTab.Library,
            onClick = throttledLibraryClick,
            icon = { Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_library)) },
        )
        NavigationRailItem(
            selected = selectedTab == AppTab.History,
            onClick = throttledHistoryClick,
            icon = { Icon(Icons.Filled.History, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_history)) },
        )
        NavigationRailItem(
            selected = selectedTab == AppTab.Settings,
            onClick = throttledSettingsClick,
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_settings)) },
        )
    }
}
