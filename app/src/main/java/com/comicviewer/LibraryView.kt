package com.comicviewer

private const val MAX_CONTINUE_READING_ITEMS = 12
private const val NEW_COMIC_WINDOW_MS = 7L * 24L * 60L * 60L * 1000L

/**
 * Pure, UI-agnostic view logic for the Library tab: how the persisted [LibraryComic] list is
 * searched, filtered, sorted, and turned into per-comic reading progress. Kept free of Compose
 * imports so it can be unit-tested on the JVM, mirroring [filterBrowserEntries].
 */

enum class LibrarySortOrder {
    RecentlyAdded,
    TitleAtoZ,
    RecentlyRead,
}

enum class LibraryFilter {
    All,
    Archives,
    Folders,
}

fun filterLibraryComicsByQuery(
    comics: List<LibraryComic>,
    query: String,
    metadata: List<LibraryComicMetadata> = emptyList(),
    details: List<LibraryComicDetails> = emptyList(),
): List<LibraryComic> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return comics
    val metadataBySource = metadata.indexBySource { it.sourceType to it.path }
    val detailsBySource = details.indexBySource { it.sourceType to it.path }

    return comics.filter { comic ->
        comic.title.contains(normalizedQuery, ignoreCase = true) ||
            comic.displayName.contains(normalizedQuery, ignoreCase = true) ||
            metadataBySource[comic.sourceKey]?.tags.orEmpty().any { tag ->
                tag.contains(normalizedQuery, ignoreCase = true)
            } ||
            detailsBySource[comic.sourceKey]?.metadata?.searchableText()?.any { text ->
                text.contains(normalizedQuery, ignoreCase = true)
            } == true
    }
}

private val LibraryComic.sourceKey: Pair<RecentComicSourceType, String>
    get() = sourceType to path

/**
 * Lookups by comic source, built once per call so filtering and sorting stay linear in the library
 * size instead of scanning the history or metadata lists for every comic. The first entry wins,
 * like the list lookups it replaces.
 */
private inline fun <T> List<T>.indexBySource(
    sourceKey: (T) -> Pair<RecentComicSourceType, String>,
): Map<Pair<RecentComicSourceType, String>, T> =
    HashMap<Pair<RecentComicSourceType, String>, T>(size).also { index ->
        forEach { item -> index.putIfAbsent(sourceKey(item), item) }
    }

private fun List<RecentComic>.indexBySource(): Map<Pair<RecentComicSourceType, String>, RecentComic> =
    indexBySource { it.sourceType to it.path }

/** ComicInfo fields a reader is likely to search by: title, series, writers, publisher and genres. */
private fun ComicInfoMetadata.searchableText(): List<String> =
    listOfNotNull(title, series, publisher) + writers + genres

fun filterLibraryComicsBySource(
    comics: List<LibraryComic>,
    filter: LibraryFilter,
): List<LibraryComic> = when (filter) {
    LibraryFilter.All -> comics
    LibraryFilter.Archives -> comics.filter { comic -> comic.sourceType.isArchive }
    LibraryFilter.Folders -> comics.filter { comic -> comic.sourceType.isFolder }
}

fun filterLibraryComicsByProgress(
    comics: List<LibraryComic>,
    recentComics: List<RecentComic>,
    metadata: List<LibraryComicMetadata>,
    filter: LibraryProgressFilter,
): List<LibraryComic> {
    if (filter == LibraryProgressFilter.All) return comics
    val recentBySource = recentComics.indexBySource()
    val metadataBySource = metadata.indexBySource { it.sourceType to it.path }
    return when (filter) {
        LibraryProgressFilter.All -> comics
        LibraryProgressFilter.Favorites -> comics.filter { comic ->
            metadataBySource[comic.sourceKey]?.favorite == true
        }
        LibraryProgressFilter.Unread -> comics.filter { comic -> recentBySource[comic.sourceKey] == null }
        LibraryProgressFilter.InProgress -> comics.filter { comic ->
            val recentComic = recentBySource[comic.sourceKey]
            recentComic?.progressFraction() != null && !recentComic.isFinished()
        }
        LibraryProgressFilter.Completed -> comics.filter { comic ->
            recentBySource[comic.sourceKey]?.isFinished() == true
        }
    }
}

fun filterLibraryComicsByTag(
    comics: List<LibraryComic>,
    metadata: List<LibraryComicMetadata>,
    tag: String?,
): List<LibraryComic> {
    val normalizedTag = tag?.trim()?.takeIf { value -> value.isNotEmpty() } ?: return comics
    val metadataBySource = metadata.indexBySource { it.sourceType to it.path }
    return comics.filter { comic ->
        metadataBySource[comic.sourceKey]?.tags.orEmpty().any { existingTag ->
            existingTag.equals(normalizedTag, ignoreCase = true)
        }
    }
}

fun sortLibraryComics(
    comics: List<LibraryComic>,
    order: LibrarySortOrder,
    recentComics: List<RecentComic>,
): List<LibraryComic> = when (order) {
    LibrarySortOrder.RecentlyAdded ->
        comics.sortedWith(compareByDescending<LibraryComic> { comic -> comic.addedAt }.thenBy { it.path })

    LibrarySortOrder.TitleAtoZ ->
        comics.sortedWith(
            Comparator<LibraryComic> { left, right -> compareComicPageNames(left.title, right.title) }
                .thenBy { it.path },
        )

    LibrarySortOrder.RecentlyRead -> {
        val recentBySource = recentComics.indexBySource()
        // Unread comics (no matching recent) sort last; ties broken by path for stable ordering.
        comics.sortedWith(
            compareByDescending<LibraryComic> { comic ->
                recentBySource[comic.sourceKey]?.lastOpenedAt ?: Long.MIN_VALUE
            }.thenBy { it.path },
        )
    }
}

/**
 * One-based reading progress in 0f..1f for [comic], or null when it has never been opened or its
 * page count is unknown. Page index is clamped so stale data can't overflow the bar.
 */
fun libraryProgressFraction(
    comic: LibraryComic,
    recentComics: List<RecentComic>,
): Float? = comic.matchingRecentComic(recentComics)?.progressFraction()

/**
 * Full pipeline behind the visible grid: source filter, then title query, then sort. Order matters
 * so the sort always operates on the already-narrowed set.
 */
fun libraryView(
    comics: List<LibraryComic>,
    recentComics: List<RecentComic>,
    query: String,
    filter: LibraryFilter,
    order: LibrarySortOrder,
    metadata: List<LibraryComicMetadata> = emptyList(),
    progressFilter: LibraryProgressFilter = LibraryProgressFilter.All,
    tagFilter: String? = null,
    details: List<LibraryComicDetails> = emptyList(),
): List<LibraryComic> {
    val bySource = filterLibraryComicsBySource(comics, filter)
    val byProgress = filterLibraryComicsByProgress(bySource, recentComics, metadata, progressFilter)
    val byTag = filterLibraryComicsByTag(byProgress, metadata, tagFilter)
    val byQuery = filterLibraryComicsByQuery(byTag, query, metadata, details)
    return sortLibraryComics(byQuery, order, recentComics)
}

/** Started comics not yet finished (see [isFinished]), most recently opened first, for the Continue reading shelf. */
fun continueReadingComics(
    comics: List<LibraryComic>,
    recentComics: List<RecentComic>,
    maxItems: Int = MAX_CONTINUE_READING_ITEMS,
): List<LibraryComic> =
    recentComics.indexBySource().let { recentBySource ->
        comics.mapNotNull { comic ->
            val recentComic = recentBySource[comic.sourceKey] ?: return@mapNotNull null
            if (recentComic.progressFraction() == null || recentComic.isFinished()) null else comic to recentComic.lastOpenedAt
        }
    }
        .sortedByDescending { (_, lastOpenedAt) -> lastOpenedAt }
        .take(maxItems)
        .map { (comic, _) -> comic }

/** Added in the last week and never opened: shown with a New badge in the library. */
fun isNewLibraryComic(
    comic: LibraryComic,
    recentComics: List<RecentComic>,
    nowMs: Long,
): Boolean =
    comic.matchingRecentComic(recentComics) == null && nowMs - comic.addedAt in 0L..NEW_COMIC_WINDOW_MS
