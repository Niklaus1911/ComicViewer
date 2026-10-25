package com.comicviewer.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comicviewer.ui.common.tabScreen
import com.comicviewer.LibraryComic
import com.comicviewer.LibraryComicDetails
import com.comicviewer.LibraryComicMetadata
import com.comicviewer.R
import com.comicviewer.RecentComic
import com.comicviewer.compareComicPageNames
import com.comicviewer.detailsFor
import com.comicviewer.matchingRecentComic
import com.comicviewer.metadataFor
import com.comicviewer.resolvedComicPageCount
import com.comicviewer.ui.common.ReferenceScreenHeader
import com.comicviewer.ui.common.StatusContent
import com.comicviewer.ui.library.LibraryComicCover

@Composable
fun SearchScreen(
    libraryComics: List<LibraryComic>,
    libraryMetadata: List<LibraryComicMetadata>,
    libraryDetails: List<LibraryComicDetails>,
    recentComics: List<RecentComic>,
    isProUnlocked: Boolean,
    onOpenComic: (LibraryComic) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query, libraryComics, libraryMetadata, libraryDetails, isProUnlocked) {
        val normalized = query.trim()
        if (normalized.isEmpty()) {
            emptyList()
        } else {
            libraryComics.filter { comic ->
                val metadata = detailsFor(comic, libraryDetails)?.metadata
                val userMetadata = metadataFor(comic, libraryMetadata)
                comic.title.contains(normalized, ignoreCase = true) ||
                    comic.displayName.contains(normalized, ignoreCase = true) ||
                    (isProUnlocked && userMetadata.tags.any { it.contains(normalized, ignoreCase = true) }) ||
                    metadata?.series?.contains(normalized, ignoreCase = true) == true ||
                    metadata?.title?.contains(normalized, ignoreCase = true) == true ||
                    metadata?.writers?.any { it.contains(normalized, ignoreCase = true) } == true ||
                    metadata?.publisher?.contains(normalized, ignoreCase = true) == true ||
                    metadata?.genres?.any { it.contains(normalized, ignoreCase = true) } == true
            }.sortedWith { left, right -> compareComicPageNames(left.title, right.title) }
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.tabScreen),
        ) {
            ReferenceScreenHeader(
                title = stringResource(R.string.nav_search),
                navigation = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, stringResource(R.string.library_clear_search))
                        }
                    }
                },
                placeholder = { Text(stringResource(R.string.library_search_label)) },
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
            )
            when {
                query.isBlank() -> StatusContent(
                    icon = Icons.Filled.Search,
                    title = stringResource(R.string.search_empty_title),
                    message = stringResource(R.string.search_empty_message),
                    modifier = Modifier.weight(1f),
                )

                results.isEmpty() -> StatusContent(
                    icon = Icons.Filled.Search,
                    title = stringResource(R.string.library_no_results_title),
                    message = stringResource(R.string.search_no_results_message),
                    modifier = Modifier.weight(1f),
                )

                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(top = 14.dp, bottom = 24.dp),
                ) {
                    item {
                        Text(
                            text = pluralStringResource(
                                R.plurals.search_results_count,
                                results.size,
                                results.size,
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                        )
                    }
                    items(
                        items = results,
                        key = { "${it.sourceType.storageName}:${it.path}" },
                    ) { comic ->
                        Column {
                            SearchResultRow(
                                comic = comic,
                                pageCount = resolvedComicPageCount(
                                    details = detailsFor(comic, libraryDetails),
                                    recentComic = comic.matchingRecentComic(recentComics),
                                ),
                                author = detailsFor(comic, libraryDetails)?.metadata?.writers?.joinToString(),
                                onClick = { onOpenComic(comic) },
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 92.dp, end = 20.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    comic: LibraryComic,
    pageCount: Int?,
    author: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            modifier = Modifier.size(width = 58.dp, height = 76.dp),
            shape = RoundedCornerShape(7.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            LibraryComicCover(libraryComic = comic)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = comic.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            author?.takeIf(String::isNotBlank)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            pageCount?.takeIf { it > 0 }?.let {
                Text(
                    text = pluralStringResource(R.plurals.comic_pages, it, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
