package com.comicviewer.ui.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.comicviewer.R
import com.comicviewer.AppLanguage
import com.comicviewer.AppThemeMode
import com.comicviewer.BuildConfig
import com.comicviewer.AppBackupData
import com.comicviewer.ComicDocument
import com.comicviewer.LibraryAutomationPreferences
import com.comicviewer.RestoreMode
import com.comicviewer.StorageAccessKind
import com.comicviewer.collectAppBackupData
import com.comicviewer.decodeAppBackup
import com.comicviewer.encodeAppBackup
import com.comicviewer.effectiveProUnlocked
import com.comicviewer.loadDebugProUnlocked
import com.comicviewer.loadLibraryAutomationPreferences
import com.comicviewer.loadReaderPreferences
import com.comicviewer.resolveDarkTheme
import com.comicviewer.resolveGrantedStorageAccess
import com.comicviewer.resolvedThemePreset
import com.comicviewer.saveDebugProUnlocked
import com.comicviewer.saveLibraryAutomationPreferences
import com.comicviewer.saveReaderPreferences
import com.comicviewer.savePersistedStorageTreeUri
import com.comicviewer.storageAccessKindForSdk
import com.comicviewer.shouldRunAutomaticLibraryScan
import com.comicviewer.takePersistableReadPermission
import com.comicviewer.folderDocumentFor
import com.comicviewer.readBackupBytesBounded
import com.comicviewer.restoreAppBackup
import com.comicviewer.billing.ProBillingRepository
import com.comicviewer.ui.common.ProPurchaseSuccessOverlay
import com.comicviewer.ui.theme.AppColor
import com.comicviewer.ui.theme.AppThemePreset
import com.comicviewer.ui.theme.ComicViewerTheme
import com.comicviewer.ui.welcome.WelcomeScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val COMIC_ARCHIVE_MIME_TYPES = arrayOf(
    "application/zip",
    "application/x-zip-compressed",
    "application/x-cbz",
    "application/vnd.comicbook+zip",
    "application/pdf",
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

// Scrims match the androidx defaults; they only show with three-button navigation.
private val LIGHT_NAVIGATION_BAR_SCRIM = android.graphics.Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DARK_NAVIGATION_BAR_SCRIM = android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b)

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
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var storageAccessRevision by remember { mutableIntStateOf(0) }
    val storageAccessKind = storageAccessKindForSdk(Build.VERSION.SDK_INT)
    val grantedStorageAccess = remember(storageAccessRevision) {
        resolveGrantedStorageAccess(context.applicationContext)
    }
    val appState = if (grantedStorageAccess != null) {
        rememberComicViewerAppState(
            appContext = context,
            storageAccess = grantedStorageAccess,
        )
    } else {
        null
    }
    val billingRepository = remember(context) { ProBillingRepository(context.applicationContext) }
    val proBillingState by billingRepository.state.collectAsState()
    var readerPreferences by remember { mutableStateOf(loadReaderPreferences(context)) }
    var automationPreferences by remember { mutableStateOf(loadLibraryAutomationPreferences(context)) }
    var pendingBackupBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingRestoreData by remember { mutableStateOf<AppBackupData?>(null) }
    var debugProUnlocked by remember { mutableStateOf(loadDebugProUnlocked(context)) }
    var visibleProSuccessEventId by remember { mutableLongStateOf(0L) }
    var consumedProSuccessEventId by remember { mutableLongStateOf(0L) }
    var documentPermissionError by remember { mutableStateOf(false) }
    var legacyPermissionRequested by remember { mutableStateOf(false) }
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

    fun persistAutomationPreferences(next: LibraryAutomationPreferences) {
        saveLibraryAutomationPreferences(context.applicationContext, next)
        automationPreferences = next
        appState?.updateAutomationPreferences(next)
    }

    fun recordSuccessfulAutomaticScan(expectedImportFolderUri: String?) {
        val current = automationPreferences
        if (!current.autoScanEnabled || current.importFolderUri != expectedImportFolderUri) return
        persistAutomationPreferences(current.copy(lastAutoScanAt = System.currentTimeMillis()))
    }

    val legacyReadPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) legacyPermissionRequested = true
        storageAccessRevision += 1
    }
    val allFilesSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        storageAccessRevision += 1
    }
    val storageRootLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null && persistSelection(uri)) {
            savePersistedStorageTreeUri(context.applicationContext, uri)
            storageAccessRevision += 1
        }
    }

    @SuppressLint("InlinedApi")
    fun requestStorageAccess() {
        when (storageAccessKind) {
            StorageAccessKind.LegacyReadPermission -> {
                val activity = context as? ComponentActivity
                val permanentlyDenied = legacyPermissionRequested &&
                    activity?.shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE) == false
                if (permanentlyDenied) {
                    allFilesSettingsLauncher.launch(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            "package:${context.packageName}".toUri(),
                        ),
                    )
                } else {
                    legacyReadPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }

            StorageAccessKind.DocumentTree -> storageRootLauncher.launch(null)
            StorageAccessKind.AllFiles -> {
                val appSettingsIntent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    "package:${context.packageName}".toUri(),
                )
                runCatching { allFilesSettingsLauncher.launch(appSettingsIntent) }
                    .onFailure {
                        allFilesSettingsLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    }
            }
        }
    }

    val openArchiveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && persistSelection(uri)) appState?.openArchive(uri)
    }
    val openFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null && persistSelection(uri)) appState?.openFolder(uri)
    }
    val addArchivesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        val distinctUris = uris.distinctBy(android.net.Uri::toString)
        val persistedUris = distinctUris.filter(::persistSelection)
        if (persistedUris.isNotEmpty()) {
            appState?.addArchiveDocumentsToLibrary(persistedUris)
        }
    }
    val addFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null && persistSelection(uri)) appState?.addFolderDocumentToLibrary(uri)
    }
    val chooseImportFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null && persistSelection(uri)) {
            scope.launch {
                val lookUpFolder: suspend () -> ComicDocument = {
                    withContext(Dispatchers.IO) { folderDocumentFor(context.applicationContext, uri) }
                }
                // The new grant is not saved anywhere until the preferences below are written.
                val document = appState?.holdingDocumentPermissions(listOf(uri), lookUpFolder) ?: lookUpFolder()
                val next = automationPreferences.copy(
                    importFolderUri = uri.toString(),
                    importFolderName = document.displayName,
                    lastAutoScanAt = 0L,
                )
                persistAutomationPreferences(next)
                if (next.autoScanEnabled) {
                    appState?.autoScanLibraryFolder(uri) { success ->
                        if (success) recordSuccessfulAutomaticScan(uri.toString())
                    }
                } else {
                    appState?.autoScanLibraryFolder(uri)
                }
            }
        }
    }
    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val bytes = pendingBackupBytes
        pendingBackupBytes = null
        if (uri != null && bytes != null) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                            ?: error("Unable to open backup destination")
                    }.isSuccess
                }
                Toast.makeText(
                    context,
                    if (saved) R.string.settings_backup_saved else R.string.settings_backup_invalid,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    val importBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val decoded = withContext(Dispatchers.IO) {
                    runCatching {
                        val bytes = context.contentResolver.openInputStream(uri)?.use(::readBackupBytesBounded)
                            ?: error("Unable to open backup")
                        decodeAppBackup(bytes)
                    }.getOrNull()
                }
                if (decoded == null) {
                    Toast.makeText(context, R.string.settings_backup_invalid, Toast.LENGTH_SHORT).show()
                } else {
                    pendingRestoreData = decoded
                }
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
                storageAccessRevision += 1
                billingRepository.restorePurchases()
            }
        }
        activity?.lifecycle?.addObserver(observer)

        onDispose {
            activity?.lifecycle?.removeObserver(observer)
        }
    }

    LaunchedEffect(appState, automationPreferences) {
        appState?.updateAutomationPreferences(automationPreferences)
    }

    LaunchedEffect(
        appState,
        automationPreferences.autoScanEnabled,
        automationPreferences.importFolderUri,
        automationPreferences.lastAutoScanAt,
        appState?.showWelcome,
    ) {
        val state = appState ?: return@LaunchedEffect
        val now = System.currentTimeMillis()
        if (!state.showWelcome && shouldRunAutomaticLibraryScan(
                enabled = automationPreferences.autoScanEnabled,
                lastScanAt = automationPreferences.lastAutoScanAt,
                nowMs = now,
            )
        ) {
            val expectedImportFolderUri = automationPreferences.importFolderUri
            val root = expectedImportFolderUri?.let { runCatching { it.toUri() }.getOrNull() }
            val onFinished: (Boolean) -> Unit = { success ->
                if (success) recordSuccessfulAutomaticScan(expectedImportFolderUri)
            }
            if (root != null) {
                state.autoScanLibraryFolder(root, onFinished)
            } else {
                state.autoScanLibraryArchives(onFinished)
            }
        }
    }

    BackHandler(enabled = appState?.showWelcome == true) {
        appState?.dismissWelcome()
    }

    BackHandler(enabled = appState?.let { state -> state.shouldHandleBack && !state.showWelcome } == true) {
        appState?.handleBack()
    }

    LaunchedEffect(proBillingState.purchaseSuccessEventId) {
        val eventId = proBillingState.purchaseSuccessEventId
        if (eventId > 0L && eventId != consumedProSuccessEventId) {
            consumedProSuccessEventId = eventId
            visibleProSuccessEventId = eventId
        }
    }

    val darkTheme = resolveDarkTheme(themeUiState.themeMode, isSystemInDarkTheme())
    // System bar icons follow the app theme, which can differ from the system dark mode setting.
    DisposableEffect(context, darkTheme) {
        (context as? ComponentActivity)?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
            ) { darkTheme },
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = LIGHT_NAVIGATION_BAR_SCRIM,
                darkScrim = DARK_NAVIGATION_BAR_SCRIM,
            ) { darkTheme },
        )
        onDispose {}
    }
    ComicViewerTheme(
        darkTheme = darkTheme,
        dynamicColor = themeUiState.dynamicColorEnabled,
        appColor = themeUiState.accentColor,
        themePreset = effectiveThemePreset,
        amoledTheme = themeUiState.amoledEnabled,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (appState == null) {
                FileAccessScreen(
                    storageAccessKind = storageAccessKind,
                    onRequestAccess = ::requestStorageAccess,
                )
            } else if (appState.showWelcome) {
                WelcomeScreen(
                    onAutoScan = appState::startWelcomeAutoScan,
                    onBrowseManually = appState::startWelcomeManualBrowse,
                    onSkip = appState::dismissWelcome,
                )
            } else {
                ComicViewerScreen(
                    state = appState.uiState,
                    selectedTab = appState.currentTab,
                    currentDirectoryPath = appState.currentDirectoryPath,
                    browserRootPath = appState.browserRootPath,
                    rawFileBrowserEnabled = appState.rawFileBrowserEnabled,
                    documentBrowserRootUri = appState.documentBrowserRootUri,
                    libraryComics = appState.libraryComics,
                    libraryMetadata = appState.libraryMetadata,
                    libraryDetails = appState.libraryDetails,
                    libraryAutoScanState = appState.libraryAutoScanState,
                    recentComics = appState.recentComics,
                    selectedComicDetails = appState.selectedComicDetails,
                    isAddingToLibrary = appState.isAddingToLibrary,
                    proBillingState = effectiveProBillingState,
                    readerPreferences = readerPreferences,
                    automationPreferences = automationPreferences,
                    isDebugBuild = BuildConfig.DEBUG,
                    debugProUnlocked = debugProUnlocked,
                    selectedLanguage = selectedLanguage,
                    themeUiState = themeUiState.copy(themePreset = effectiveThemePreset),
                    onBuyPro = {
                        (context as? ComponentActivity)?.let(billingRepository::launchPurchase)
                    },
                    onRestorePro = billingRepository::restorePurchases,
                    onReaderPreferencesChanged = { nextPreferences ->
                        saveReaderPreferences(context, nextPreferences)
                        readerPreferences = nextPreferences
                    },
                    onChooseImportFolder = { chooseImportFolderLauncher.launch(null) },
                    onAutomationPreferencesChanged = ::persistAutomationPreferences,
                    onExportBackup = {
                        scope.launch {
                            val encoded = withContext(Dispatchers.IO) {
                                runCatching {
                                    encodeAppBackup(collectAppBackupData(context.applicationContext))
                                }.getOrNull()
                            }
                            if (encoded == null) {
                                Toast.makeText(context, R.string.settings_backup_invalid, Toast.LENGTH_SHORT).show()
                            } else {
                                pendingBackupBytes = encoded
                                val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                                exportBackupLauncher.launch("comic-viewer-backup-$date.json")
                            }
                        }
                    },
                    onImportBackup = { importBackupLauncher.launch(arrayOf("application/json", "text/plain")) },
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
                    onMarkRead = appState::markRead,
                    onMarkUnread = appState::markUnread,
                    onRemoveFromHistory = appState::removeFromHistory,
                    onRestoreToHistory = appState::restoreToHistory,
                    onClearHistory = appState::clearHistory,
                    onStartArchiveImportBrowser = appState::startArchiveImportBrowser,
                    onCloseImportBrowser = appState::closeImportBrowser,
                    onAutoScanLibrary = appState::autoScanLibraryArchives,
                    onClearLibraryAutoScanResult = appState::clearLibraryAutoScanResult,
                    onDirectoryChange = appState::changeDirectory,
                    onOpenLocalArchive = appState::openLocalArchive,
                    onOpenLocalFolder = appState::openLocalFolder,
                    onOpenArchivePicker = { openArchiveLauncher.launch(COMIC_ARCHIVE_MIME_TYPES) },
                    onOpenFolderPicker = { openFolderLauncher.launch(null) },
                    onAddArchivesPicker = { addArchivesLauncher.launch(COMIC_ARCHIVE_MIME_TYPES) },
                    onAddFolderPicker = { addFolderLauncher.launch(null) },
                    onOpenRecentComic = appState::openRecentComic,
                    onShowRecentComicDetails = appState::showRecentComicDetails,
                    onReadRecentComic = appState::readRecentComic,
                    onOpenLibraryComic = appState::openLibraryComic,
                    onReadLibraryComic = appState::readLibraryComic,
                    onReadLibraryComicAt = appState::readLibraryComicAt,
                    onCloseComicDetails = appState::closeComicDetails,
                    onAddLocalArchivesToLibrary = appState::addArchivesToLibrary,
                    onAddLocalFolderToLibrary = appState::addFolderToLibrary,
                    onAddDocumentArchivesToLibrary = appState::addArchiveDocumentsToLibrary,
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
            pendingRestoreData?.let { backup ->
                fun restore(mode: RestoreMode) {
                    pendingRestoreData = null
                    scope.launch {
                        val summary = runCatching {
                            restoreAppBackup(context.applicationContext, backup, mode)
                        }.getOrNull()
                        if (summary == null) {
                            Toast.makeText(context, R.string.settings_backup_invalid, Toast.LENGTH_SHORT).show()
                        } else {
                            appState?.reloadPersistedLibraryState()
                            readerPreferences = loadReaderPreferences(context)
                            automationPreferences = loadLibraryAutomationPreferences(context)
                            Toast.makeText(
                                context,
                                resources.getString(
                                    R.string.settings_restore_complete,
                                    summary.libraryCount,
                                    summary.historyCount,
                                    summary.skippedSources,
                                ),
                                Toast.LENGTH_LONG,
                            ).show()
                            (context as? ComponentActivity)?.recreate()
                        }
                    }
                }
                AlertDialog(
                    onDismissRequest = { pendingRestoreData = null },
                    title = { Text(stringResource(R.string.settings_restore_title)) },
                    text = { Text(stringResource(R.string.settings_restore_message)) },
                    confirmButton = {
                        TextButton(onClick = { restore(RestoreMode.Merge) }) {
                            Text(stringResource(R.string.settings_restore_merge))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { restore(RestoreMode.Replace) }) {
                            Text(stringResource(R.string.settings_restore_replace))
                        }
                    },
                )
            }
        }
    }
}
