package com.comicviewer.ui.app

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.comicviewer.R
import com.comicviewer.AppLanguage
import com.comicviewer.AppThemeMode
import com.comicviewer.BuildConfig
import com.comicviewer.LibraryComic
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
import com.comicviewer.takePersistableReadPermission
import com.comicviewer.billing.ProBillingRepository
import com.comicviewer.billing.ProBillingState
import com.comicviewer.ui.browser.DocumentBrowserScreen
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
import kotlin.math.roundToInt

private val COMIC_ARCHIVE_MIME_TYPES = arrayOf(
    "application/zip",
    "application/x-zip-compressed",
    "application/x-cbz",
    "application/vnd.comicbook+zip",
    "application/octet-stream",
)

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
    val appState = rememberComicViewerAppState(appContext = context)
    val billingRepository = remember(context) { ProBillingRepository(context.applicationContext) }
    val proBillingState by billingRepository.state.collectAsState()
    var readerPreferences by remember { mutableStateOf(loadReaderPreferences(context)) }
    var debugProUnlocked by remember { mutableStateOf(loadDebugProUnlocked(context)) }
    var visibleProSuccessEventId by remember { mutableStateOf(0L) }
    var consumedProSuccessEventId by remember { mutableStateOf(0L) }
    var documentPermissionError by remember { mutableStateOf(false) }
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

    fun persistSelection(uri: android.net.Uri): Boolean {
        val persisted = takePersistableReadPermission(context, uri)
        if (!persisted) documentPermissionError = true
        return persisted
    }

    val openArchiveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && persistSelection(uri)) appState.openArchive(uri)
    }
    val openFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null && persistSelection(uri)) appState.openFolder(uri)
    }
    val addArchivesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        val distinctUris = uris.distinctBy(android.net.Uri::toString)
        val persistedUris = distinctUris.filter(::persistSelection)
        if (persistedUris.isNotEmpty()) {
            appState.addArchiveDocumentsToLibrary(persistedUris)
        }
    }
    val addFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null && persistSelection(uri)) appState.addFolderDocumentToLibrary(uri)
    }

    DisposableEffect(billingRepository) {
        billingRepository.startConnection()
        onDispose { billingRepository.endConnection() }
    }

    DisposableEffect(context, appState, billingRepository) {
        val activity = context as? ComponentActivity
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                billingRepository.restorePurchases()
            }
        }
        activity?.lifecycle?.addObserver(observer)

        onDispose {
            activity?.lifecycle?.removeObserver(observer)
        }
    }

    BackHandler(enabled = appState.showWelcome) {
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
            if (appState.showWelcome) {
                WelcomeScreen(
                    proBillingState = effectiveProBillingState,
                    isDebugBuild = BuildConfig.DEBUG,
                    debugProUnlocked = debugProUnlocked,
                    onImportArchives = { addArchivesLauncher.launch(COMIC_ARCHIVE_MIME_TYPES) },
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
                    libraryComics = appState.libraryComics,
                    libraryMetadata = appState.libraryMetadata,
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
                    onOpenArchivePicker = { openArchiveLauncher.launch(COMIC_ARCHIVE_MIME_TYPES) },
                    onOpenFolderPicker = { openFolderLauncher.launch(null) },
                    onAddArchivesPicker = { addArchivesLauncher.launch(COMIC_ARCHIVE_MIME_TYPES) },
                    onAddFolderPicker = { addFolderLauncher.launch(null) },
                    onOpenRecentComic = appState::openRecentComic,
                    onOpenLibraryComic = appState::openLibraryComic,
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
            if (documentPermissionError) {
                AlertDialog(
                    onDismissRequest = { documentPermissionError = false },
                    title = { Text(stringResource(R.string.document_permission_error_title)) },
                    text = { Text(stringResource(R.string.document_permission_error_message)) },
                    confirmButton = {
                        TextButton(onClick = { documentPermissionError = false }) {
                            Text(stringResource(R.string.ok))
                        }
                    },
                )
            }
        }
    }
}

@Composable
fun ComicViewerScreen(
    state: ReaderUiState,
    selectedTab: AppTab,
    libraryComics: List<LibraryComic>,
    libraryMetadata: List<LibraryComicMetadata>,
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
    onOpenArchivePicker: () -> Unit,
    onOpenFolderPicker: () -> Unit,
    onAddArchivesPicker: () -> Unit,
    onAddFolderPicker: () -> Unit,
    onOpenRecentComic: (RecentComic) -> Unit,
    onOpenLibraryComic: (LibraryComic) -> Unit,
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
                    libraryComics = libraryComics,
                    libraryMetadata = libraryMetadata,
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
                    onOpenArchivePicker = onOpenArchivePicker,
                    onOpenFolderPicker = onOpenFolderPicker,
                    onAddArchivesPicker = onAddArchivesPicker,
                    onAddFolderPicker = onAddFolderPicker,
                    onOpenRecentComic = onOpenRecentComic,
                    onOpenLibraryComic = onOpenLibraryComic,
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
    libraryComics: List<LibraryComic>,
    libraryMetadata: List<LibraryComicMetadata>,
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
    onOpenArchivePicker: () -> Unit,
    onOpenFolderPicker: () -> Unit,
    onAddArchivesPicker: () -> Unit,
    onAddFolderPicker: () -> Unit,
    onOpenRecentComic: (RecentComic) -> Unit,
    onOpenLibraryComic: (LibraryComic) -> Unit,
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
                                recentComics = recentComics,
                                proBillingState = proBillingState,
                                onStartAddToLibrary = onStartAddToLibrary,
                                onImportArchives = onAddArchivesPicker,
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

                            AppTab.Browse -> DocumentBrowserScreen(
                                recentComics = recentComics,
                                isAddingToLibrary = isAddingToLibrary,
                                onOpenArchivePicker = onOpenArchivePicker,
                                onOpenFolderPicker = onOpenFolderPicker,
                                onAddArchivesPicker = onAddArchivesPicker,
                                onAddFolderPicker = onAddFolderPicker,
                                onOpenRecentComic = onOpenRecentComic,
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
