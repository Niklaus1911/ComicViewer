package com.comicviewer.ui.reader

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.request.ImageRequest
import com.comicviewer.ComicBook
import com.comicviewer.ComicPage
import com.comicviewer.ComicSource
import com.comicviewer.R
import com.comicviewer.ReaderBackground
import com.comicviewer.ReaderPageMode
import com.comicviewer.ReaderPreferences
import com.comicviewer.billing.ProBillingState
import com.comicviewer.coercePageIndex
import com.comicviewer.comicPageImageCacheKey
import com.comicviewer.resolvedReaderPreferences
import com.comicviewer.ui.common.rememberThrottledAction
import com.comicviewer.ui.library.ProUpgradeDialog

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
                text = stringResource(R.string.loading_pages),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
fun ErrorComicScreen(
    message: String,
    onBackToBrowser: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val throttledBack = rememberThrottledAction(action = onBackToBrowser)
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
                text = stringResource(R.string.open_comic_error_title),
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
                onClick = throttledBack,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.back_to_files))
            }
        }
    }
}

/**
 * The reader shell: window behavior, shared controls and sheets, and either the paged reader or the
 * vertical scroll reader depending on the reading mode.
 */
@Composable
fun ComicReaderScreen(
    book: ComicBook,
    pageIndex: Int,
    readerPreferences: ReaderPreferences,
    isProUnlocked: Boolean,
    proBillingState: ProBillingState,
    isDebugBuild: Boolean,
    debugProUnlocked: Boolean,
    onReaderPreferencesChanged: (ReaderPreferences) -> Unit,
    onBuyPro: () -> Unit,
    onRestorePro: () -> Unit,
    onDebugProUnlockedChanged: (Boolean) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPageSelected: (Int) -> Unit,
    onCloseComic: () -> Unit,
    modifier: Modifier = Modifier,
    favorite: Boolean? = null,
    onToggleFavorite: () -> Unit = {},
) {
    val activeReaderPreferences = resolvedReaderPreferences(
        readerPreferences = readerPreferences,
        isProUnlocked = isProUnlocked,
    )
    var controlsVisible by remember(book) { mutableStateOf(true) }
    var showReaderSettings by remember(book) { mutableStateOf(false) }
    var showPageGrid by remember(book) { mutableStateOf(false) }
    var showProDialog by remember(book) { mutableStateOf(false) }

    ReaderWindowEffect(preferences = activeReaderPreferences)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(activeReaderPreferences.background.readerBackgroundColor()),
    ) {
        if (activeReaderPreferences.pageMode == ReaderPageMode.Vertical) {
            VerticalComicReader(
                book = book,
                pageIndex = pageIndex,
                preferences = activeReaderPreferences,
                controlsVisible = controlsVisible,
                onControlsVisibleChange = { controlsVisible = it },
                onPageSelected = onPageSelected,
                onCloseComic = onCloseComic,
                onOpenSettings = { showReaderSettings = true },
                onOpenGrid = { showPageGrid = true },
                favorite = favorite,
                onToggleFavorite = { if (isProUnlocked) onToggleFavorite() else showProDialog = true },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            PagedComicReader(
                book = book,
                pageIndex = pageIndex,
                activeReaderPreferences = activeReaderPreferences,
                controlsVisible = controlsVisible,
                onControlsVisibleChange = { controlsVisible = it },
                onPageSelected = onPageSelected,
                onCloseComic = onCloseComic,
                onOpenSettings = { showReaderSettings = true },
                onOpenGrid = { showPageGrid = true },
                favorite = favorite,
                onToggleFavorite = { if (isProUnlocked) onToggleFavorite() else showProDialog = true },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (showReaderSettings) {
            ReaderSettingsDialog(
                readerPreferences = readerPreferences,
                activeReaderPreferences = activeReaderPreferences,
                isProUnlocked = isProUnlocked,
                onReaderPreferencesChanged = onReaderPreferencesChanged,
                onRequestPro = { showProDialog = true },
                onDismiss = { showReaderSettings = false },
                livePreview = true,
            )
        }

        if (showPageGrid) {
            ReaderPageGrid(
                book = book,
                currentPageIndex = coercePageIndex(pageIndex, book.pages.size),
                onPageSelected = { selected ->
                    onPageSelected(selected)
                    showPageGrid = false
                },
                onDismiss = { showPageGrid = false },
            )
        }

        if (showProDialog) {
            ProUpgradeDialog(
                billingState = proBillingState,
                onBuyPro = onBuyPro,
                onRestorePro = onRestorePro,
                isDebugBuild = isDebugBuild,
                debugProUnlocked = debugProUnlocked,
                onDebugProUnlockedChanged = onDebugProUnlockedChanged,
                onDismiss = { showProDialog = false },
            )
        }
    }
}

internal fun ComicPage.imageRequest(context: Context, source: ComicSource): ImageRequest {
    val cacheKey = comicPageImageCacheKey(source = source, page = this)
    return ImageRequest.Builder(context)
        .data(imageUri)
        .memoryCacheKey(cacheKey)
        .diskCacheKey(cacheKey)
        .build()
}

/** Screen readers announce a page by its position in the comic, not by its archive file name. */
@Composable
internal fun ComicPage.accessibilityLabel(pageCount: Int): String =
    stringResource(R.string.reader_page_description, index + 1, pageCount)

internal fun ReaderBackground.readerBackgroundColor(): Color = when (this) {
    ReaderBackground.Black -> Color.Black
    ReaderBackground.DarkGray -> Color(0xFF202124)
    ReaderBackground.Sepia -> Color(0xFF2C2418)
    ReaderBackground.White -> Color.White
}

internal tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
