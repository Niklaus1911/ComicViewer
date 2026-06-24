package com.comicviewer

import java.util.Locale

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
): List<LibraryComic> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return comics

    return comics.filter { comic ->
        comic.title.contains(normalizedQuery, ignoreCase = true) ||
            comic.displayName.contains(normalizedQuery, ignoreCase = true)
    }
}

fun filterLibraryComicsBySource(
    comics: List<LibraryComic>,
    filter: LibraryFilter,
): List<LibraryComic> = when (filter) {
    LibraryFilter.All -> comics
    LibraryFilter.Archives -> comics.filter { comic -> comic.sourceType == RecentComicSourceType.LocalArchive }
    LibraryFilter.Folders -> comics.filter { comic -> comic.sourceType == RecentComicSourceType.LocalFolder }
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
            compareBy<LibraryComic> { comic -> comic.title.lowercase(Locale.getDefault()) }.thenBy { it.path },
        )

    LibrarySortOrder.RecentlyRead ->
        // Unread comics (no matching recent) sort last; ties broken by path for stable ordering.
        comics.sortedWith(
            compareByDescending<LibraryComic> { comic ->
                comic.matchingRecentComic(recentComics)?.lastOpenedAt ?: Long.MIN_VALUE
            }.thenBy { it.path },
        )
}

/**
 * One-based reading progress in 0f..1f for [comic], or null when it has never been opened or its
 * page count is unknown. Page index is clamped so stale data can't overflow the bar.
 */
fun libraryProgressFraction(
    comic: LibraryComic,
    recentComics: List<RecentComic>,
): Float? {
    val recentComic = comic.matchingRecentComic(recentComics) ?: return null
    if (recentComic.pageCount <= 0) return null
    val readPage = coercePageIndex(recentComic.lastPageIndex, recentComic.pageCount) + 1
    return (readPage.toFloat() / recentComic.pageCount.toFloat()).coerceIn(0f, 1f)
}

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
): List<LibraryComic> {
    val bySource = filterLibraryComicsBySource(comics, filter)
    val byQuery = filterLibraryComicsByQuery(bySource, query)
    return sortLibraryComics(byQuery, order, recentComics)
}
