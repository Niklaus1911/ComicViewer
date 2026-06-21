package com.comicviewer.ui.library

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import com.comicviewer.LibraryComic
import com.comicviewer.RecentComic
import com.comicviewer.TABLET_LIBRARY_CARD_MIN_WIDTH_DP
import com.comicviewer.isTabletWidth
import com.comicviewer.loadLibraryComicThumbnailUri
import com.comicviewer.progressLabel
import com.comicviewer.ui.common.icon
import com.comicviewer.ui.common.rememberThrottledAction
import com.comicviewer.ui.common.softContentTransform
import com.comicviewer.ui.common.softPress
import kotlin.math.roundToInt

@Composable
fun LibraryScreen(
    libraryComics: List<LibraryComic>,
    recentComics: List<RecentComic>,
    onStartAddToLibrary: () -> Unit,
    onOpenLibraryComic: (LibraryComic) -> Unit,
    onRemoveLibraryComic: (LibraryComic) -> Unit,
    modifier: Modifier = Modifier,
) {
    val throttledStartAdd = rememberThrottledAction(action = onStartAddToLibrary)
    var comicPendingRemoval by remember { mutableStateOf<LibraryComic?>(null) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding(),
    ) {
        LibraryHeader(onStartAddToLibrary = throttledStartAdd)

        AnimatedContent(
            targetState = libraryComics.isEmpty(),
            transitionSpec = { softContentTransform() },
            label = "libraryContentTransition",
            modifier = Modifier.weight(1f),
        ) { isEmpty ->
            if (isEmpty) {
                LibraryEmptyContent(onStartAddToLibrary = throttledStartAdd)
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val tabletLayout = isTabletWidth(maxWidth.value.roundToInt())
                    LazyVerticalGrid(
                        columns = if (tabletLayout) {
                            GridCells.Adaptive(TABLET_LIBRARY_CARD_MIN_WIDTH_DP.dp)
                        } else {
                            GridCells.Fixed(2)
                        },
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        gridItems(
                            items = libraryComics,
                            key = { comic -> "${comic.sourceType.storageName}:${comic.path}" },
                        ) { comic ->
                            LibraryComicCard(
                                libraryComic = comic,
                                progressLabel = comic.progressLabel(recentComics),
                                onClick = rememberThrottledAction { onOpenLibraryComic(comic) },
                                onRemove = { comicPendingRemoval = comic },
                            )
                        }
                    }
                }
            }
        }
    }

    comicPendingRemoval?.let { pendingComic ->
        AlertDialog(
            onDismissRequest = { comicPendingRemoval = null },
            title = {
                Text(text = "Rimuovere dalla libreria?")
            },
            text = {
                Text(
                    text = "\"${pendingComic.title}\" verra rimosso dalla libreria. " +
                        "Il file originale non verra eliminato.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveLibraryComic(pendingComic)
                        comicPendingRemoval = null
                    },
                ) {
                    Text("Rimuovi")
                }
            },
            dismissButton = {
                TextButton(onClick = { comicPendingRemoval = null }) {
                    Text("Annulla")
                }
            },
        )
    }
}

@Composable
fun LibraryHeader(
    onStartAddToLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 18.dp, end = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Library",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onStartAddToLibrary) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Aggiungi comic alla libreria",
            )
        }
    }
}

@Composable
fun LibraryEmptyContent(
    onStartAddToLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.LibraryBooks,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(52.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "La tua libreria e vuota",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Aggiungi archivi o cartelle comic dal browser.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onStartAddToLibrary) {
            Text("Aggiungi comic")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryComicCard(
    libraryComic: LibraryComic,
    progressLabel: String,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .softPress(interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onRemove,
            ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            LibraryComicCover(libraryComic = libraryComic)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = libraryComic.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = progressLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun LibraryComicCover(
    libraryComic: LibraryComic,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var thumbnailUri by remember(libraryComic) { mutableStateOf<Uri?>(null) }

    LaunchedEffect(libraryComic) {
        thumbnailUri = loadLibraryComicThumbnailUri(context.applicationContext, libraryComic)
    }

    if (thumbnailUri != null) {
        Image(
            painter = rememberAsyncImagePainter(model = thumbnailUri),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize(),
        )
    } else {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = libraryComic.sourceType.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(42.dp),
            )
        }
    }
}

