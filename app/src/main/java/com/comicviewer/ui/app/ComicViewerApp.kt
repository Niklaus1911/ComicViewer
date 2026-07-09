package com.comicviewer.ui.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.comicviewer.R
import com.comicviewer.AppLanguage
import com.comicviewer.AppThemeMode
import com.comicviewer.BuildConfig
import com.comicviewer.LibraryComic
import com.comicviewer.LibraryAutoScanState
import com.comicviewer.LibraryComicMetadata
import com.comicviewer.ReaderPreferences
import com.comicviewer.ReaderUiState
import com.comicviewer.RecentComic
import com.comicviewer.currentAppBuildInfo
import com.comicviewer.effectiveProUnlocked
import com.comicviewer.isTabletWidth
import com.comicviewer.loadDebugProUnlocked
import com.comicviewer.loadReaderPreferences
import com.comicviewer.resolveDarkTheme
import com.comicviewer.resolvedThemePreset
import com.comicviewer.saveDebugProUnlocked
import com.comicviewer.saveReaderPreferences
import com.comicviewer.billing.ProBillingRepository
import com.comicviewer.billing.ProBillingState
import com.comicviewer.ui.browser.FileBrowserScreen
import com.comicviewer.ui.common.ProPurchaseSuccessOverlay
import com.comicviewer.ui.common.rememberThrottledAction
import com.comicviewer.ui.common.softContentTransform
import com.comicviewer.ui.library.LibraryScreen
import com.comicviewer.ui.reader.ComicReaderScreen
import com.comicviewer.ui.reader.ErrorComicScreen
import com.comicviewer.ui.reader.LoadingComicScreen
import com.comicviewer.ui.settings.SettingsScreen
import com.comicviewer.ui.theme.AppColor
import com.comicviewer.ui.theme.AppThemePreset
import com.comicviewer.ui.theme.ComicViewerTheme
import com.comicviewer.ui.welcome.WelcomeScreen
import java.io.File
import kotlin.math.roundToInt

/** Current theme selections surfaced in Settings, threaded down from [MainActivity]. */
data class ThemeUiState(
    val themeMode: AppThemeMode,
    val dynamicColorEnabled: Boolean,
    val accentColor: AppColor,
    val themePreset: AppThemePreset,
    val amoledEnabled: Boolean,
)

@Composable
fun ComicViewerApp(
    selectedLanguage: AppLanguage,
    themeUiState: ThemeUiState,
    onLanguageSelected: (AppLanguage) -> Unit,
    onThemeModeSelected: (AppThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onAccentColorSelected: (AppColor) -> Unit,
    onThemePresetSelected: (AppThemePreset) -> Unit,
    onAmoledThemeChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val appState = rememberComicViewerAppState(
        appContext = context,
        hasFileAccess = hasFileManagerAccess(),
    )
    val billingRepository = remember(context) { ProBillingRepository(context.applicationContext) }
    val proBillingState by billingRepository.state.collectAsState()
    var readerPreferences by remember { mutableStateOf(loadReaderPreferences(context)) }
    var debugProUnlocked by remember { mutableStateOf(loadDebugProUnlocked(context)) }
    var visibleProSuccessEventId by remember { mutableStateOf(0L) }
    var consumedProSuccessEventId by remember { mutableStateOf(0L) }
    val effectiveProUnlockedValue = effectiveProUnlocked(
        billingUnlocked = proBillingState.isProUnlocked,
        debugOverrideUnlocked = debugProUnlocked,
        isDebugBuild = BuildConfig.DEBUG,
    )
    val effectiveProBillingState = proBillingState.copy(isProUnlocked = effectiveProUnlockedValue)
    val effectiveThemePreset = resolvedThemePreset(
        themePreset = themeUiState.themePreset,
        isProUnlocked = effectiveProUnlockedValue,
    )

    val fileAccessLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        appState.refreshFileAccess(hasFileManagerAccess())
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

    DisposableEffect(billingRepository) {
        billingRepository.startConnection()
        onDispose { billingRepository.endConnection() }
    }

    DisposableEffect(context, appState, billingRepository) {
        val activity = context as? ComponentActivity
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                appState.refreshFileAccess(hasFileManagerAccess())
                billingRepository.restorePurchases()
            }
        }
        activity?.lifecycle?.addObserver(observer)

        onDispose {
            activity?.lifecycle?.removeObserver(observer)
        }
    }

    BackHandler(enabled = appState.showWelcome && appState.hasFileAccess) {
        appState.dismissWelcome()
    }

    BackHandler(enabled = appState.shouldHandleBack && !appState.showWelcome) {
        appState.handleBack()
    }

    LaunchedEffect(proBillingState.purchaseSuccessEventId) {
        val eventId = proBillingState.purchaseSuccessEventId
        if (eventId > 0L && eventId != consumedProSuccessEventId) {
            consumedProSuccessEventId = eventId
            visibleProSuccessEventId = eventId
        }
    }

    val darkTheme = resolveDarkTheme(themeUiState.themeMode, isSystemInDarkTheme())
    ComicViewerTheme(
        darkTheme = darkTheme,
        dynamicColor = themeUiState.dynamicColorEnabled,
        appColor = themeUiState.accentColor,
        themePreset = effectiveThemePreset,
        amoledTheme = themeUiState.amoledEnabled,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!appState.hasFileAccess) {
                FileAccessScreen(onOpenSettings = ::openFileAccessSettings)
            } else if (appState.showWelcome) {
                WelcomeScreen(
                    proBillingState = effectiveProBillingState,
                    isDebugBuild = BuildConfig.DEBUG,
                    debugProUnlocked = debugProUnlocked,
                    onAutoScan = appState::startWelcomeAutoScan,
                    onBrowseManually = appState::startWelcomeManualBrowse,
                    onSkip = appState::dismissWelcome,
                    onBuyPro = {
                        (context as? ComponentActivity)?.let(billingRepository::launchPurchase)
                    },
                    onRestorePro = billingRepository::restorePurchases,
                    onDebugProUnlockedChanged = { unlocked ->
                        if (BuildConfig.DEBUG) {
                            saveDebugProUnlocked(context, unlocked)
                            debugProUnlocked = unlocked
                        }
                    },
                )
            } else {
                ComicViewerScreen(
                    state = appState.uiState,
                    selectedTab = appState.currentTab,
                    currentDirectoryPath = appState.currentDirectoryPath,
                    browserRootPath = appState.browserRootPath,
                    libraryComics = appState.libraryComics,
                    libraryMetadata = appState.libraryMetadata,
                    libraryAutoScanState = appState.libraryAutoScanState,
                    recentComics = appState.recentComics,
                    isAddingToLibrary = appState.isAddingToLibrary,
                    proBillingState = effectiveProBillingState,
                    readerPreferences = readerPreferences,
                    isDebugBuild = BuildConfig.DEBUG,
                    debugProUnlocked = debugProUnlocked,
                    selectedLanguage = selectedLanguage,
                    themeUiState = themeUiState,
                    onBuyPro = {
                        (context as? ComponentActivity)?.let(billingRepository::launchPurchase)
                    },
                    onRestorePro = billingRepository::restorePurchases,
                    onReaderPreferencesChanged = { nextPreferences ->
                        saveReaderPreferences(context, nextPreferences)
                        readerPreferences = nextPreferences
                    },
                    onDebugProUnlockedChanged = { unlocked ->
                        if (BuildConfig.DEBUG) {
                            saveDebugProUnlocked(context, unlocked)
                            debugProUnlocked = unlocked
                        }
                    },
                    onLanguageSelected = onLanguageSelected,
                    onThemeModeSelected = onThemeModeSelected,
                    onDynamicColorChanged = onDynamicColorChanged,
                    onAccentColorSelected = onAccentColorSelected,
                    onThemePresetSelected = onThemePresetSelected,
                    onAmoledThemeChanged = onAmoledThemeChanged,
                    onTabSelected = appState::selectTab,
                    onStartAddToLibrary = appState::startAddToLibrary,
                    onAutoScanLibrary = appState::autoScanLibraryArchives,
                    onClearLibraryAutoScanResult = appState::clearLibraryAutoScanResult,
                    onDirectoryChange = appState::changeDirectory,
                    onOpenLocalArchive = appState::openLocalArchive,
                    onOpenLocalFolder = appState::openLocalFolder,
                    onOpenRecentComic = appState::openRecentComic,
                    onOpenLibraryComic = appState::openLibraryComic,
                    onAddLocalArchivesToLibrary = appState::addArchivesToLibrary,
                    onAddLocalFolderToLibrary = appState::addFolderToLibrary,
                    onRemoveLibraryComic = appState::removeFromLibrary,
                    onToggleLibraryFavorite = appState::toggleFavorite,
                    onSetLibraryTags = appState::setTags,
                    onPrevious = appState::previousPage,
                    onNext = appState::nextPage,
                    onPageSelected = appState::selectPage,
                    onCloseComic = appState::closeComic,
                )
            }
            if (visibleProSuccessEventId > 0L) {
                ProPurchaseSuccessOverlay(
                    eventId = visibleProSuccessEventId,
                    onDismiss = { visibleProSuccessEventId = 0L },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
fun ComicViewerScreen(
    state: ReaderUiState,
    selectedTab: AppTab,
    currentDirectoryPath: String,
    browserRootPath: String,
    libraryComics: List<LibraryComic>,
    libraryMetadata: List<LibraryComicMetadata>,
    libraryAutoScanState: LibraryAutoScanState,
    recentComics: List<RecentComic>,
    isAddingToLibrary: Boolean,
    proBillingState: ProBillingState,
    readerPreferences: ReaderPreferences,
    isDebugBuild: Boolean,
    debugProUnlocked: Boolean,
    selectedLanguage: AppLanguage,
    themeUiState: ThemeUiState,
    onBuyPro: () -> Unit,
    onRestorePro: () -> Unit,
    onReaderPreferencesChanged: (ReaderPreferences) -> Unit,
    onDebugProUnlockedChanged: (Boolean) -> Unit,
    onLanguageSelected: (AppLanguage) -> Unit,
    onThemeModeSelected: (AppThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onAccentColorSelected: (AppColor) -> Unit,
    onThemePresetSelected: (AppThemePreset) -> Unit,
    onAmoledThemeChanged: (Boolean) -> Unit,
    onTabSelected: (AppTab) -> Unit,
    onStartAddToLibrary: () -> Unit,
    onAutoScanLibrary: () -> Unit,
    onClearLibraryAutoScanResult: () -> Unit,
    onDirectoryChange: (String) -> Unit,
    onOpenLocalArchive: (File) -> Unit,
    onOpenLocalFolder: (File) -> Unit,
    onOpenRecentComic: (RecentComic) -> Unit,
    onOpenLibraryComic: (LibraryComic) -> Unit,
    onAddLocalArchivesToLibrary: (List<File>) -> Unit,
    onAddLocalFolderToLibrary: (File) -> Unit,
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
                    libraryMetadata = libraryMetadata,
                    libraryAutoScanState = libraryAutoScanState,
                    recentComics = recentComics,
                    isAddingToLibrary = isAddingToLibrary,
                    proBillingState = proBillingState,
                    isDebugBuild = isDebugBuild,
                    debugProUnlocked = debugProUnlocked,
                    selectedLanguage = selectedLanguage,
                    themeUiState = themeUiState,
                    onBuyPro = onBuyPro,
                    onRestorePro = onRestorePro,
                    onDebugProUnlockedChanged = onDebugProUnlockedChanged,
                    onLanguageSelected = onLanguageSelected,
                    onThemeModeSelected = onThemeModeSelected,
                    onDynamicColorChanged = onDynamicColorChanged,
                    onAccentColorSelected = onAccentColorSelected,
                    onThemePresetSelected = onThemePresetSelected,
                    onAmoledThemeChanged = onAmoledThemeChanged,
                    onTabSelected = onTabSelected,
                    onStartAddToLibrary = onStartAddToLibrary,
                    onAutoScanLibrary = onAutoScanLibrary,
                    onClearLibraryAutoScanResult = onClearLibraryAutoScanResult,
                    onDirectoryChange = onDirectoryChange,
                    onOpenLocalArchive = onOpenLocalArchive,
                    onOpenLocalFolder = onOpenLocalFolder,
                    onOpenRecentComic = onOpenRecentComic,
                    onOpenLibraryComic = onOpenLibraryComic,
                    onAddLocalArchivesToLibrary = onAddLocalArchivesToLibrary,
                    onAddLocalFolderToLibrary = onAddLocalFolderToLibrary,
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

@Composable
fun HomeScreen(
    selectedTab: AppTab,
    currentDirectoryPath: String,
    browserRootPath: String,
    libraryComics: List<LibraryComic>,
    libraryMetadata: List<LibraryComicMetadata>,
    libraryAutoScanState: LibraryAutoScanState,
    recentComics: List<RecentComic>,
    isAddingToLibrary: Boolean,
    proBillingState: ProBillingState,
    isDebugBuild: Boolean,
    debugProUnlocked: Boolean,
    selectedLanguage: AppLanguage,
    themeUiState: ThemeUiState,
    onBuyPro: () -> Unit,
    onRestorePro: () -> Unit,
    onDebugProUnlockedChanged: (Boolean) -> Unit,
    onLanguageSelected: (AppLanguage) -> Unit,
    onThemeModeSelected: (AppThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onAccentColorSelected: (AppColor) -> Unit,
    onThemePresetSelected: (AppThemePreset) -> Unit,
    onAmoledThemeChanged: (Boolean) -> Unit,
    onTabSelected: (AppTab) -> Unit,
    onStartAddToLibrary: () -> Unit,
    onAutoScanLibrary: () -> Unit,
    onClearLibraryAutoScanResult: () -> Unit,
    onDirectoryChange: (String) -> Unit,
    onOpenLocalArchive: (File) -> Unit,
    onOpenLocalFolder: (File) -> Unit,
    onOpenRecentComic: (RecentComic) -> Unit,
    onOpenLibraryComic: (LibraryComic) -> Unit,
    onAddLocalArchivesToLibrary: (List<File>) -> Unit,
    onAddLocalFolderToLibrary: (File) -> Unit,
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
                            AppTab.Library -> LibraryScreen(
                                libraryComics = libraryComics,
                                libraryMetadata = libraryMetadata,
                                libraryAutoScanState = libraryAutoScanState,
                                recentComics = recentComics,
                                proBillingState = proBillingState,
                                onStartAddToLibrary = onStartAddToLibrary,
                                onAutoScanLibrary = onAutoScanLibrary,
                                onClearAutoScanResult = onClearLibraryAutoScanResult,
                                onBuyPro = onBuyPro,
                                onRestorePro = onRestorePro,
                                isDebugBuild = isDebugBuild,
                                debugProUnlocked = debugProUnlocked,
                                onDebugProUnlockedChanged = onDebugProUnlockedChanged,
                                onOpenLibraryComic = onOpenLibraryComic,
                                onRemoveLibraryComic = onRemoveLibraryComic,
                                onToggleFavorite = onToggleLibraryFavorite,
                                onSetTags = onSetLibraryTags,
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
                                            onAddLocalArchivesToLibrary = onAddLocalArchivesToLibrary,
                                onAddLocalFolderToLibrary = onAddLocalFolderToLibrary,
                                modifier = Modifier.fillMaxSize(),
                            )

                            AppTab.Settings -> SettingsScreen(
                                selectedLanguage = selectedLanguage,
                                themeUiState = themeUiState,
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
                    Box(modifier = Modifier.weight(1f)) {
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
    val throttledBrowseClick = rememberThrottledAction { onTabSelected(AppTab.Browse) }
    val throttledSettingsClick = rememberThrottledAction { onTabSelected(AppTab.Settings) }
    NavigationBar(modifier = modifier.fillMaxWidth()) {
        NavigationBarItem(
            selected = selectedTab == AppTab.Library,
            onClick = throttledLibraryClick,
            icon = { Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_library)) },
        )
        NavigationBarItem(
            selected = selectedTab == AppTab.Browse,
            onClick = throttledBrowseClick,
            icon = { Icon(Icons.Filled.Folder, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_browse)) },
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
    val throttledBrowseClick = rememberThrottledAction { onTabSelected(AppTab.Browse) }
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
            selected = selectedTab == AppTab.Browse,
            onClick = throttledBrowseClick,
            icon = { Icon(Icons.Filled.Folder, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_browse)) },
        )
        NavigationRailItem(
            selected = selectedTab == AppTab.Settings,
            onClick = throttledSettingsClick,
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_settings)) },
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
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.file_access_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.file_access_message),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(28.dp))
            Button(onClick = onOpenSettings) {
                Text(stringResource(R.string.open_settings))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun FileAccessScreenPreview() {
    ComicViewerTheme {
        FileAccessScreen(onOpenSettings = {})
    }
}
