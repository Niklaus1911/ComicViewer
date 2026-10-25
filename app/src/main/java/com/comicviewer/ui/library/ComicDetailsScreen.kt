package com.comicviewer.ui.library

import android.net.Uri
import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.comicviewer.LibraryComic
import com.comicviewer.LibraryComicDetails
import com.comicviewer.LibraryComicMetadata
import com.comicviewer.R
import com.comicviewer.RecentComic
import com.comicviewer.RecentComicSourceType
import com.comicviewer.billing.ProBillingState
import com.comicviewer.coercePageIndex
import com.comicviewer.comicLanguageDisplayName
import com.comicviewer.detailsFor
import com.comicviewer.isFinished
import com.comicviewer.loadComicPagePreviews
import com.comicviewer.matchingRecentComic
import com.comicviewer.metadataFor
import com.comicviewer.progressFraction
import com.comicviewer.resolvedComicPageCount
import com.comicviewer.ui.app.ComicDetailsTarget
import com.comicviewer.ui.browser.RecentComicThumbnail
import com.comicviewer.ui.common.ScreenHorizontalPadding
import com.comicviewer.ui.common.absoluteDateText
import com.comicviewer.ui.common.fileSizeText
import com.comicviewer.ui.common.readableContentWidth
import kotlin.math.roundToInt

private const val MAX_PAGE_PREVIEWS = 12
private const val SUMMARY_COLLAPSED_LINES = 4
private val HeroCoverShape = RoundedCornerShape(14.dp)
private val HeroCollapseOffset = 300.dp

@Composable
fun ComicDetailsScreen(
    target: ComicDetailsTarget,
    libraryComics: List<LibraryComic>,
    libraryMetadata: List<LibraryComicMetadata>,
    libraryDetails: List<LibraryComicDetails>,
    recentComics: List<RecentComic>,
    proBillingState: ProBillingState,
    isDebugBuild: Boolean,
    debugProUnlocked: Boolean,
    onDebugProUnlockedChanged: (Boolean) -> Unit,
    onBuyPro: () -> Unit,
    onRestorePro: () -> Unit,
    onReadLibraryComic: (LibraryComic) -> Unit,
    onReadLibraryComicAt: (LibraryComic, Int) -> Unit,
    onReadRecentComic: (RecentComic) -> Unit,
    onMarkRead: (LibraryComic) -> Unit,
    onMarkUnread: (LibraryComic) -> Unit,
    onToggleFavorite: (LibraryComic) -> Unit,
    onSetTags: (LibraryComic, List<String>) -> Unit,
    onRemoveComic: (LibraryComic) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val libraryComic = when (target) {
        is ComicDetailsTarget.Library -> target.comic
        is ComicDetailsTarget.Recent -> libraryComics.firstOrNull {
            it.sourceType == target.comic.sourceType && it.path == target.comic.path
        }
    }
    val recentComic = when (target) {
        is ComicDetailsTarget.Library -> target.comic.matchingRecentComic(recentComics)
        is ComicDetailsTarget.Recent -> currentRecentComicForDetails(target.comic, recentComics)
    }
    val title = libraryComic?.title ?: recentComic?.title.orEmpty()
    val displayName = libraryComic?.displayName ?: recentComic?.displayName.orEmpty()
    val sourceType = libraryComic?.sourceType ?: recentComic?.sourceType
    val path = libraryComic?.path ?: recentComic?.path
    val details = libraryComic?.let { detailsFor(it, libraryDetails) }
        ?: recentComic?.let { recent ->
            libraryDetails.firstOrNull { it.sourceType == recent.sourceType && it.path == recent.path }
        }
    val metadata = details?.metadata
    val userMetadata = libraryComic?.let { comic ->
        metadataFor(comic, if (proBillingState.isProUnlocked) libraryMetadata else emptyList())
    }
    val pageCount = resolvedComicPageCount(details, recentComic)
    val progress = recentComic?.progressFraction()
    val completed = recentComic?.isFinished() == true
    val currentPage = recentComic?.let { coercePageIndex(it.lastPageIndex, it.pageCount) + 1 }
    val authors = metadata?.writers?.joinToString()?.takeIf(String::isNotBlank)
    val tags = (metadata?.genres.orEmpty() + userMetadata?.tags.orEmpty()).distinct()
    val archiveFormat = sourceType
        ?.takeIf { it == RecentComicSourceType.LocalArchive || it == RecentComicSourceType.DocumentArchive }
        ?.let { displayName.substringAfterLast('.', "").uppercase().takeIf(String::isNotBlank) }
    var pagePreviews by remember(sourceType, path) { mutableStateOf<List<Uri>>(emptyList()) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }
    var showProDialog by remember { mutableStateOf(false) }

    LaunchedEffect(sourceType, path) {
        if (sourceType != null && path != null) {
            pagePreviews = loadComicPagePreviews(context.applicationContext, sourceType, path, MAX_PAGE_PREVIEWS)
        }
    }

    fun read(fromStart: Boolean) {
        when (target) {
            is ComicDetailsTarget.Library ->
                if (fromStart) onReadLibraryComicAt(target.comic, 0) else onReadLibraryComic(target.comic)
            is ComicDetailsTarget.Recent ->
                recentComic?.let { recent -> onReadRecentComic(if (fromStart) recent.copy(lastPageIndex = 0) else recent) }
        }
    }

    fun readAt(pageIndex: Int) {
        when (target) {
            is ComicDetailsTarget.Library -> onReadLibraryComicAt(target.comic, pageIndex)
            is ComicDetailsTarget.Recent -> recentComic?.let { onReadRecentComic(it.copy(lastPageIndex = pageIndex)) }
        }
    }

    val cover: @Composable (Modifier) -> Unit = { coverModifier ->
        when (target) {
            is ComicDetailsTarget.Library -> LibraryComicCover(libraryComic = target.comic, modifier = coverModifier)
            is ComicDetailsTarget.Recent -> RecentComicThumbnail(recentComic = target.comic, modifier = coverModifier)
        }
    }

    val scrollState = rememberScrollState()
    val collapseThresholdPx = with(LocalDensity.current) { HeroCollapseOffset.toPx() }
    val topBarSolid by remember { derivedStateOf { scrollState.value > collapseThresholdPx } }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .navigationBarsPadding(),
            ) {
                DetailsHero(cover = cover)
                Column(modifier = Modifier.readableContentWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ScreenHorizontalPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = metadata?.title ?: title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        authors?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                            )
                        }
                        Text(
                            text = listOfNotNull(
                                pageCount?.let { pluralStringResource(R.plurals.comic_pages, it, it) },
                                fileSizeText(details?.fileSizeBytes),
                                archiveFormat,
                            ).joinToString("  •  "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        if (tags.isNotEmpty()) {
                            DetailTags(tags = tags, modifier = Modifier.padding(top = 4.dp))
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ScreenHorizontalPadding, vertical = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = { read(fromStart = completed) },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(if (completed) Icons.Filled.Replay else Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = when {
                                    completed -> stringResource(R.string.read_again)
                                    currentPage != null && currentPage > 1 -> stringResource(R.string.continue_reading_page, currentPage)
                                    else -> stringResource(R.string.start_reading)
                                },
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        if (libraryComic != null) {
                            val favorite = userMetadata?.favorite == true
                            FilledTonalIconButton(
                                onClick = {
                                    if (proBillingState.isProUnlocked) onToggleFavorite(libraryComic) else showProDialog = true
                                },
                                modifier = Modifier.size(52.dp),
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Icon(
                                    imageVector = if (favorite) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                    contentDescription = stringResource(
                                        if (favorite) R.string.library_remove_favorite else R.string.library_add_favorite,
                                    ),
                                )
                            }
                        }
                    }

                    if (recentComic != null && progress != null) {
                        ReadingProgressCard(
                            progress = progress,
                            completed = completed,
                            currentPage = currentPage ?: 1,
                            pageCount = recentComic.pageCount,
                            completedAt = recentComic.completedAt,
                            modifier = Modifier.padding(horizontal = ScreenHorizontalPadding),
                        )
                    }

                    if (pagePreviews.isNotEmpty()) {
                        DetailsSectionTitle(stringResource(R.string.comic_pages_section))
                        PagePreviewStrip(
                            pages = pagePreviews,
                            pageCount = pageCount,
                            currentPageIndex = currentPage?.minus(1),
                            onPageSelected = ::readAt,
                        )
                    }

                    metadata?.summary?.let { summary ->
                        DetailsSectionTitle(stringResource(R.string.comic_summary_section))
                        ExpandableSummary(
                            text = summary,
                            modifier = Modifier.padding(horizontal = ScreenHorizontalPadding),
                        )
                    }

                    DetailsSectionTitle(stringResource(R.string.comic_details))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ScreenHorizontalPadding),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            val locale = LocalConfiguration.current.locales[0]
                            val rows = listOfNotNull(
                                metadata?.series?.let { series ->
                                    stringResource(R.string.comic_series) to (
                                        metadata.number?.let { number -> stringResource(R.string.comic_series_issue, series, number) }
                                            ?: series
                                        )
                                },
                                metadata?.publisher?.let { stringResource(R.string.comic_publisher) to it },
                                metadata?.year?.let { stringResource(R.string.comic_published) to it.toString() },
                                metadata?.language?.let { stringResource(R.string.comic_language) to comicLanguageDisplayName(it, locale) },
                                pageCount?.let { stringResource(R.string.comic_pages_label) to it.toString() },
                                displayName.takeIf(String::isNotBlank)?.let { stringResource(R.string.comic_file) to it },
                                libraryComic?.addedAt?.takeIf { it > 0L }?.let { stringResource(R.string.comic_added) to absoluteDateText(it) },
                            )
                            rows.forEachIndexed { index, (label, value) ->
                                if (index > 0) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                }
                                DetailRow(label = label, value = value)
                            }
                            if (metadata == null && details != null) {
                                if (rows.isNotEmpty()) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                }
                                Text(
                                    text = stringResource(R.string.comic_metadata_unavailable),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 12.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                }
            }

            DetailsTopBar(
                title = metadata?.title ?: title,
                solid = topBarSolid,
                onBack = onBack,
                menu = if (libraryComic != null) {
                    {
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            if (!completed) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.mark_as_read)) },
                                    leadingIcon = { Icon(Icons.Filled.CheckCircle, null) },
                                    onClick = {
                                        menuExpanded = false
                                        onMarkRead(libraryComic)
                                    },
                                )
                            }
                            if (recentComic != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.mark_as_unread)) },
                                    leadingIcon = { Icon(Icons.Filled.RemoveDone, null) },
                                    onClick = {
                                        menuExpanded = false
                                        onMarkUnread(libraryComic)
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.library_edit_tags)) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, null) },
                                onClick = {
                                    menuExpanded = false
                                    if (proBillingState.isProUnlocked) showTagDialog = true else showProDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.remove_from_library_action)) },
                                leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                onClick = {
                                    menuExpanded = false
                                    showRemoveDialog = true
                                },
                            )
                        }
                    }
                } else {
                    null
                },
                onMenu = { menuExpanded = true },
            )
        }
    }

    if (showRemoveDialog && libraryComic != null) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text(stringResource(R.string.remove_from_library_title)) },
            text = { Text(stringResource(R.string.remove_from_library_message, libraryComic.title)) },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveDialog = false
                    onRemoveComic(libraryComic)
                }) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showTagDialog && libraryComic != null) {
        var tagsText by remember(libraryComic, showTagDialog) {
            mutableStateOf(userMetadata?.tags.orEmpty().joinToString(", "))
        }
        AlertDialog(
            onDismissRequest = { showTagDialog = false },
            title = { Text(stringResource(R.string.library_edit_tags)) },
            text = {
                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    label = { Text(stringResource(R.string.library_tags_input_label)) },
                    supportingText = { Text(stringResource(R.string.library_tags_input_helper)) },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onSetTags(libraryComic, tagsText.split(','))
                    showTagDialog = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showTagDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
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

/** Blurred, dimmed cover filling the top of the screen behind the sharp cover. */
@Composable
private fun DetailsHero(cover: @Composable (Modifier) -> Unit) {
    val background = MaterialTheme.colorScheme.background
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { alpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.75f else 0.35f }
                .blur(32.dp),
        ) {
            cover(Modifier.fillMaxSize())
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to background.copy(alpha = 0.35f),
                        0.55f to background.copy(alpha = 0.55f),
                        1f to background,
                    ),
                ),
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(64.dp))
            Box(
                modifier = Modifier
                    .width(172.dp)
                    .aspectRatio(2f / 3f)
                    .shadow(elevation = 18.dp, shape = HeroCoverShape)
                    .clip(HeroCoverShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                cover(Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun DetailsTopBar(
    title: String,
    solid: Boolean,
    onBack: () -> Unit,
    menu: (@Composable () -> Unit)?,
    onMenu: () -> Unit,
) {
    val barColor by animateColorAsState(
        targetValue = if (solid) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent,
        label = "detailsTopBarColor",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(barColor)
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TopBarButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
        }
        AnimatedVisibility(
            visible = solid,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!solid) Spacer(Modifier.weight(1f))
        if (menu != null) {
            Box {
                TopBarButton(onClick = onMenu) {
                    Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options))
                }
                menu()
            }
        }
    }
}

@Composable
private fun TopBarButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        IconButton(onClick = onClick, content = content)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailTags(tags: List<String>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tags.forEach { tag ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(
                    text = tag,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun ReadingProgressCard(
    progress: Float,
    completed: Boolean,
    currentPage: Int,
    pageCount: Int,
    completedAt: Long?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (completed) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = when {
                        completed && completedAt != null && completedAt > 0L ->
                            stringResource(R.string.comic_finished_on, absoluteDateText(completedAt))
                        completed -> stringResource(R.string.comic_finished)
                        else -> stringResource(R.string.page_progress_spaced, currentPage, pageCount)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.comic_progress_percent, (progress * 100).roundToInt()),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
private fun DetailsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(
            start = ScreenHorizontalPadding,
            end = ScreenHorizontalPadding,
            top = 24.dp,
            bottom = 10.dp,
        ),
    )
}

@Composable
private fun PagePreviewStrip(
    pages: List<Uri>,
    pageCount: Int?,
    currentPageIndex: Int?,
    onPageSelected: (Int) -> Unit,
) {
    val currentPageState = stringResource(R.string.reader_current_page)
    LazyRow(
        contentPadding = PaddingValues(horizontal = ScreenHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(pages, key = { _, uri -> uri.toString() }) { index, uri ->
            val current = index == currentPageIndex
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .width(86.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onPageSelected(index) }
                    .semantics { if (current) stateDescription = currentPageState },
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    border = if (current) {
                        androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        null
                    },
                ) {
                    AsyncImage(
                        model = uri,
                        // The strip only previews the first pages, so label against the whole comic.
                        contentDescription = if (pageCount != null) {
                            stringResource(R.string.reader_page_description, index + 1, pageCount)
                        } else {
                            stringResource(R.string.reader_page_number_description, index + 1)
                        },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Text(
                    text = (index + 1).toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ExpandableSummary(text: String, modifier: Modifier = Modifier) {
    var expanded by remember(text) { mutableStateOf(false) }
    var overflowing by remember(text) { mutableStateOf(false) }
    Column(modifier = modifier.animateContentSize()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (expanded) Int.MAX_VALUE else SUMMARY_COLLAPSED_LINES,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result -> if (!expanded) overflowing = result.hasVisualOverflow },
        )
        if (overflowing || expanded) {
            TextButton(
                onClick = { expanded = !expanded },
                contentPadding = PaddingValues(horizontal = 0.dp),
            ) {
                Text(stringResource(if (expanded) R.string.show_less else R.string.show_more))
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.6f),
        )
    }
}

internal fun currentRecentComicForDetails(
    selectedComic: RecentComic,
    recentComics: List<RecentComic>,
): RecentComic = recentComics.firstOrNull { comic ->
    comic.sourceType == selectedComic.sourceType && comic.path == selectedComic.path
} ?: selectedComic
