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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.comicviewer.LibraryComic
import com.comicviewer.ReaderUiState
import com.comicviewer.RecentComic
import com.comicviewer.isTabletWidth
import com.comicviewer.ui.browser.FileBrowserScreen
import com.comicviewer.ui.common.rememberThrottledAction
import com.comicviewer.ui.common.softContentTransform
import com.comicviewer.ui.library.LibraryScreen
import com.comicviewer.ui.reader.ComicReaderScreen
import com.comicviewer.ui.reader.ErrorComicScreen
import com.comicviewer.ui.reader.LoadingComicScreen
import com.comicviewer.ui.theme.ComicViewerTheme
import java.io.File
import kotlin.math.roundToInt

@Composable
fun ComicViewerApp() {
    val context = LocalContext.current
    val appState = rememberComicViewerAppState(
        appContext = context.applicationContext,
        hasFileAccess = hasFileManagerAccess(),
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

    DisposableEffect(context, appState) {
        val activity = context as? ComponentActivity
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                appState.refreshFileAccess(hasFileManagerAccess())
            }
        }
        activity?.lifecycle?.addObserver(observer)

        onDispose {
            activity?.lifecycle?.removeObserver(observer)
        }
    }

    BackHandler(enabled = appState.shouldHandleBack) {
        appState.handleBack()
    }

    if (!appState.hasFileAccess) {
        FileAccessScreen(onOpenSettings = ::openFileAccessSettings)
    } else {
        ComicViewerScreen(
            state = appState.uiState,
            selectedTab = appState.currentTab,
            currentDirectoryPath = appState.currentDirectoryPath,
            browserRootPath = appState.browserRootPath,
            libraryComics = appState.libraryComics,
            recentComics = appState.recentComics,
            isAddingToLibrary = appState.isAddingToLibrary,
            onTabSelected = appState::selectTab,
            onStartAddToLibrary = appState::startAddToLibrary,
            onDirectoryChange = appState::changeDirectory,
            onOpenLocalArchive = appState::openLocalArchive,
            onOpenLocalFolder = appState::openLocalFolder,
            onOpenRecentComic = appState::openRecentComic,
            onOpenLibraryComic = appState::openLibraryComic,
            onAddLocalArchiveToLibrary = appState::addArchiveToLibrary,
            onAddLocalFolderToLibrary = appState::addFolderToLibrary,
            onRemoveLibraryComic = appState::removeFromLibrary,
            onPrevious = appState::previousPage,
            onNext = appState::nextPage,
            onPageSelected = appState::selectPage,
            onCloseComic = appState::closeComic,
        )
    }
}

@Composable
fun ComicViewerScreen(
    state: ReaderUiState,
    selectedTab: AppTab,
    currentDirectoryPath: String,
    browserRootPath: String,
    libraryComics: List<LibraryComic>,
    recentComics: List<RecentComic>,
    isAddingToLibrary: Boolean,
    onTabSelected: (AppTab) -> Unit,
    onStartAddToLibrary: () -> Unit,
    onDirectoryChange: (String) -> Unit,
    onOpenLocalArchive: (File) -> Unit,
    onOpenLocalFolder: (File) -> Unit,
    onOpenRecentComic: (RecentComic) -> Unit,
    onOpenLibraryComic: (LibraryComic) -> Unit,
    onAddLocalArchiveToLibrary: (File) -> Unit,
    onAddLocalFolderToLibrary: (File) -> Unit,
    onRemoveLibraryComic: (LibraryComic) -> Unit,
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
                    recentComics = recentComics,
                    isAddingToLibrary = isAddingToLibrary,
                    onTabSelected = onTabSelected,
                    onStartAddToLibrary = onStartAddToLibrary,
                    onDirectoryChange = onDirectoryChange,
                    onOpenLocalArchive = onOpenLocalArchive,
                    onOpenLocalFolder = onOpenLocalFolder,
                    onOpenRecentComic = onOpenRecentComic,
                    onOpenLibraryComic = onOpenLibraryComic,
                    onAddLocalArchiveToLibrary = onAddLocalArchiveToLibrary,
                    onAddLocalFolderToLibrary = onAddLocalFolderToLibrary,
                    onRemoveLibraryComic = onRemoveLibraryComic,
                )

                ReaderUiState.Loading -> LoadingComicScreen()

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
    recentComics: List<RecentComic>,
    isAddingToLibrary: Boolean,
    onTabSelected: (AppTab) -> Unit,
    onStartAddToLibrary: () -> Unit,
    onDirectoryChange: (String) -> Unit,
    onOpenLocalArchive: (File) -> Unit,
    onOpenLocalFolder: (File) -> Unit,
    onOpenRecentComic: (RecentComic) -> Unit,
    onOpenLibraryComic: (LibraryComic) -> Unit,
    onAddLocalArchiveToLibrary: (File) -> Unit,
    onAddLocalFolderToLibrary: (File) -> Unit,
    onRemoveLibraryComic: (LibraryComic) -> Unit,
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
                                recentComics = recentComics,
                                onStartAddToLibrary = onStartAddToLibrary,
                                onOpenLibraryComic = onOpenLibraryComic,
                                onRemoveLibraryComic = onRemoveLibraryComic,
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
                                onAddLocalArchiveToLibrary = onAddLocalArchiveToLibrary,
                                onAddLocalFolderToLibrary = onAddLocalFolderToLibrary,
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
    NavigationBar(modifier = modifier.fillMaxWidth()) {
        NavigationBarItem(
            selected = selectedTab == AppTab.Library,
            onClick = throttledLibraryClick,
            icon = { Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = null) },
            label = { Text("Library") },
        )
        NavigationBarItem(
            selected = selectedTab == AppTab.Browse,
            onClick = throttledBrowseClick,
            icon = { Icon(Icons.Filled.Folder, contentDescription = null) },
            label = { Text("Browse") },
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
            label = { Text("Library") },
        )
        NavigationRailItem(
            selected = selectedTab == AppTab.Browse,
            onClick = throttledBrowseClick,
            icon = { Icon(Icons.Filled.Folder, contentDescription = null) },
            label = { Text("Browse") },
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
                text = "Consenti accesso ai file",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Comic Viewer ha bisogno del permesso per leggere archivi e cartelle sul dispositivo.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(28.dp))
            Button(onClick = onOpenSettings) {
                Text("Apri impostazioni")
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
