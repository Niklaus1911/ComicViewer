package com.comicviewer.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import com.comicviewer.isFinished
import com.comicviewer.ui.common.softPress
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comicviewer.LibraryComic
import com.comicviewer.R
import com.comicviewer.RecentComic
import com.comicviewer.coercePageIndex
import kotlin.math.roundToInt

internal val CoverShape = RoundedCornerShape(12.dp)
internal const val CoverAspectRatio = 2f / 3f

internal class ComicCardState(
    val comic: LibraryComic,
    val recentComic: RecentComic?,
    val favorite: Boolean,
    val isNew: Boolean,
    val pageCount: Int?,
    val progress: Float?,
) {
    val completed: Boolean
        get() = recentComic?.isFinished() == true

    val inProgress: Boolean
        get() = progress != null && !completed
}

@Composable
private fun ComicCardState.metaText(): String {
    val recent = recentComic
    return when {
        completed -> stringResource(R.string.library_filter_completed)
        inProgress && recent != null -> stringResource(
            R.string.page_progress_spaced,
            coercePageIndex(recent.lastPageIndex, recent.pageCount) + 1,
            recent.pageCount,
        )
        pageCount != null -> pluralStringResource(R.plurals.comic_pages, pageCount, pageCount)
        else -> comic.displayName
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.comicClickable(
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
): Modifier {
    val haptics = LocalHapticFeedback.current
    return softPress(interactionSource).combinedClickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick,
        onLongClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onLongClick()
        },
        onLongClickLabel = stringResource(R.string.more_options),
    )
}

/** Cover at comic proportions with New, favorite, finished and progress overlays. */
@Composable
private fun ComicCoverArt(
    card: ComicCardState,
    modifier: Modifier = Modifier,
    showBadges: Boolean = true,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(CoverAspectRatio)
            .shadow(elevation = 3.dp, shape = CoverShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, CoverShape)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), CoverShape)
            .clip(CoverShape),
    ) {
        LibraryComicCover(libraryComic = card.comic)
        overlay()
        if (showBadges) {
            if (card.isNew) {
                NewBadge(modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
            }
            Column(
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (card.completed) {
                    CoverIconBadge(
                        icon = Icons.Filled.Check,
                        contentDescription = stringResource(R.string.library_filter_completed),
                        emphasized = true,
                    )
                }
                if (card.favorite) {
                    CoverIconBadge(
                        icon = Icons.Filled.Bookmark,
                        contentDescription = stringResource(R.string.library_filter_favorites),
                    )
                }
            }
        }
        if (card.inProgress) {
            LinearProgressIndicator(
                progress = { card.progress ?: 0f },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Black.copy(alpha = 0.45f),
                strokeCap = StrokeCap.Butt,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
private fun NewBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Text(
            text = stringResource(R.string.library_badge_new),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun CoverIconBadge(
    icon: ImageVector,
    contentDescription: String,
    emphasized: Boolean = false,
) {
    Surface(
        shape = CircleShape,
        color = if (emphasized) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.55f),
        contentColor = if (emphasized) MaterialTheme.colorScheme.onPrimary else Color.White,
    ) {
        Icon(icon, contentDescription, modifier = Modifier.padding(4.dp).size(14.dp))
    }
}

@Composable
internal fun ComicGridCard(
    card: ComicCardState,
    onOpen: () -> Unit,
    onActions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(modifier = modifier.comicClickable(interactionSource, onOpen, onActions)) {
        ComicCoverArt(card = card)
        Spacer(Modifier.height(8.dp))
        Text(
            text = card.comic.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = card.metaText(),
            style = MaterialTheme.typography.bodySmall,
            color = if (card.completed || card.inProgress) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun ComicCompactCard(
    card: ComicCardState,
    onOpen: () -> Unit,
    onActions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    ComicCoverArt(
        card = card,
        modifier = modifier.comicClickable(interactionSource, onOpen, onActions),
        overlay = {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))),
            )
            Text(
                text = card.comic.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 7.dp, end = 7.dp, bottom = 9.dp),
            )
        },
    )
}

@Composable
internal fun ComicListRow(
    card: ComicCardState,
    onOpen: () -> Unit,
    onActions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier.comicClickable(interactionSource, onOpen, onActions),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ComicCoverArt(card = card, modifier = Modifier.width(58.dp), showBadges = false)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = card.comic.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (card.isNew) NewBadge()
                }
                Text(
                    text = card.metaText(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (card.inProgress) {
                    LibraryProgressLine(progress = card.progress ?: 0f)
                }
            }
            if (card.favorite) {
                Icon(
                    imageVector = Icons.Filled.Bookmark,
                    contentDescription = stringResource(R.string.library_filter_favorites),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            if (card.completed) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.library_filter_completed),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
internal fun LibraryProgressLine(progress: Float) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .weight(1f)
                .height(4.dp),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            drawStopIndicator = {},
        )
        Text(
            text = stringResource(R.string.comic_progress_percent, (progress * 100).roundToInt()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
