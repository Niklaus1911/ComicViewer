package com.comicviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryViewTest {
    @Test
    fun filterLibraryComicsByQuery_matchesTitleCaseInsensitively() {
        val comics = listOf(
            sampleLibraryComic(title = "Batman 001", path = "/storage/emulated/0/Batman 001.cbz"),
            sampleLibraryComic(title = "Saga 002", path = "/storage/emulated/0/Saga 002.cbz"),
        )

        assertEquals(
            listOf("Batman 001"),
            filterLibraryComicsByQuery(comics, "batMAN").map { comic -> comic.title },
        )
    }

    @Test
    fun filterLibraryComicsByQuery_blankReturnsAll() {
        val comics = listOf(
            sampleLibraryComic(path = "/storage/emulated/0/A.cbz"),
            sampleLibraryComic(path = "/storage/emulated/0/B.cbz"),
        )

        assertEquals(comics, filterLibraryComicsByQuery(comics, "   "))
    }

    @Test
    fun filterLibraryComicsBySource_keepsOnlyMatchingSourceType() {
        val archive = sampleLibraryComic(
            path = "/storage/emulated/0/A.cbz",
            sourceType = RecentComicSourceType.LocalArchive,
        )
        val folder = sampleLibraryComic(
            path = "/storage/emulated/0/Folder",
            sourceType = RecentComicSourceType.LocalFolder,
        )
        val documentArchive = sampleLibraryComic(
            path = "content://provider/document/archive%3AB.cbz",
            sourceType = RecentComicSourceType.DocumentArchive,
        )
        val documentFolder = sampleLibraryComic(
            path = "content://provider/tree/folder%3AFolder",
            sourceType = RecentComicSourceType.DocumentFolder,
        )
        val comics = listOf(archive, folder, documentArchive, documentFolder)

        assertEquals(
            listOf(archive, documentArchive),
            filterLibraryComicsBySource(comics, LibraryFilter.Archives),
        )
        assertEquals(
            listOf(folder, documentFolder),
            filterLibraryComicsBySource(comics, LibraryFilter.Folders),
        )
        assertEquals(comics, filterLibraryComicsBySource(comics, LibraryFilter.All))
    }

    @Test
    fun sortLibraryComics_recentlyAddedOrdersByAddedAtDesc() {
        val older = sampleLibraryComic(path = "/storage/emulated/0/Older.cbz", addedAt = 100L)
        val newer = sampleLibraryComic(path = "/storage/emulated/0/Newer.cbz", addedAt = 200L)

        assertEquals(
            listOf(newer, older),
            sortLibraryComics(listOf(older, newer), LibrarySortOrder.RecentlyAdded, emptyList()),
        )
    }

    @Test
    fun sortLibraryComics_titleSortsCaseInsensitively() {
        val apple = sampleLibraryComic(title = "apple", path = "/storage/emulated/0/a.cbz")
        val banana = sampleLibraryComic(title = "Banana", path = "/storage/emulated/0/b.cbz")
        val cherry = sampleLibraryComic(title = "cherry", path = "/storage/emulated/0/c.cbz")

        assertEquals(
            listOf("apple", "Banana", "cherry"),
            sortLibraryComics(listOf(cherry, banana, apple), LibrarySortOrder.TitleAtoZ, emptyList())
                .map { comic -> comic.title },
        )
    }

    @Test
    fun sortLibraryComics_titleSortsNumbersNaturally() {
        val two = sampleLibraryComic(title = "Batman 2", path = "/storage/emulated/0/Batman 2.cbz")
        val ten = sampleLibraryComic(title = "Batman 10", path = "/storage/emulated/0/Batman 10.cbz")
        val one = sampleLibraryComic(title = "Batman 1", path = "/storage/emulated/0/Batman 1.cbz")

        assertEquals(
            listOf("Batman 1", "Batman 2", "Batman 10"),
            sortLibraryComics(listOf(two, ten, one), LibrarySortOrder.TitleAtoZ, emptyList())
                .map { comic -> comic.title },
        )
    }

    @Test
    fun sortLibraryComics_recentlyReadOrdersByLastOpenedAtWithUnreadLast() {
        val read1 = sampleLibraryComic(path = "/storage/emulated/0/R1.cbz")
        val read2 = sampleLibraryComic(path = "/storage/emulated/0/R2.cbz")
        val unread = sampleLibraryComic(path = "/storage/emulated/0/U.cbz")
        val recents = listOf(
            sampleRecentComic(path = read1.path, lastOpenedAt = 100L),
            sampleRecentComic(path = read2.path, lastOpenedAt = 300L),
        )

        val result = sortLibraryComics(
            listOf(read1, unread, read2),
            LibrarySortOrder.RecentlyRead,
            recents,
        )

        assertEquals(listOf(read2.path, read1.path, unread.path), result.map { comic -> comic.path })
    }

    @Test
    fun sortLibraryComics_isStableForEqualKeys() {
        val first = sampleLibraryComic(path = "/storage/emulated/0/A.cbz", addedAt = 100L)
        val second = sampleLibraryComic(path = "/storage/emulated/0/B.cbz", addedAt = 100L)

        assertEquals(
            listOf(first.path, second.path),
            sortLibraryComics(listOf(second, first), LibrarySortOrder.RecentlyAdded, emptyList())
                .map { comic -> comic.path },
        )
    }

    @Test
    fun libraryProgressFraction_computesOneBasedFractionClampedAndNullWhenUnread() {
        val comic = sampleLibraryComic(path = "/storage/emulated/0/C.cbz")

        assertNull(libraryProgressFraction(comic, emptyList()))

        val recents = listOf(sampleRecentComic(path = comic.path, lastPageIndex = 4, pageCount = 10))
        assertEquals(0.5f, libraryProgressFraction(comic, recents)!!, 0.0001f)

        val zeroPages = listOf(sampleRecentComic(path = comic.path, lastPageIndex = 0, pageCount = 0))
        assertNull(libraryProgressFraction(comic, zeroPages))
    }

    @Test
    fun libraryView_appliesSourceThenQueryThenSort() {
        val archiveMatch = sampleLibraryComic(
            title = "Batman Begins",
            path = "/storage/emulated/0/Batman Begins.cbz",
            sourceType = RecentComicSourceType.LocalArchive,
            addedAt = 100L,
        )
        val archiveOther = sampleLibraryComic(
            title = "Saga",
            path = "/storage/emulated/0/Saga.cbz",
            sourceType = RecentComicSourceType.LocalArchive,
            addedAt = 200L,
        )
        val folderMatch = sampleLibraryComic(
            title = "Batman Folder",
            path = "/storage/emulated/0/BatmanFolder",
            sourceType = RecentComicSourceType.LocalFolder,
            addedAt = 300L,
        )

        val result = libraryView(
            comics = listOf(archiveMatch, archiveOther, folderMatch),
            recentComics = emptyList(),
            query = "batman",
            filter = LibraryFilter.Archives,
            order = LibrarySortOrder.RecentlyAdded,
        )

        assertEquals(listOf(archiveMatch.path), result.map { comic -> comic.path })
    }

    @Test
    fun libraryView_filtersFavoritesProgressAndTags() {
        val favorite = sampleLibraryComic(path = "/storage/emulated/0/Favorite.cbz")
        val unread = sampleLibraryComic(path = "/storage/emulated/0/Unread.cbz")
        val completed = sampleLibraryComic(path = "/storage/emulated/0/Completed.cbz")
        val metadata = listOf(
            LibraryComicMetadata(
                sourceType = favorite.sourceType,
                path = favorite.path,
                favorite = true,
                tags = listOf("Manga"),
                updatedAt = 100L,
            ),
        )
        val recents = listOf(
            sampleRecentComic(path = favorite.path, lastPageIndex = 4, pageCount = 10),
            sampleRecentComic(path = completed.path, lastPageIndex = 9, pageCount = 10),
        )
        val comics = listOf(favorite, unread, completed)

        assertEquals(
            listOf(favorite.path),
            libraryView(
                comics = comics,
                recentComics = recents,
                query = "",
                filter = LibraryFilter.All,
                order = LibrarySortOrder.RecentlyAdded,
                metadata = metadata,
                progressFilter = LibraryProgressFilter.Favorites,
            ).map { comic -> comic.path },
        )
        assertEquals(
            listOf(unread.path),
            libraryView(
                comics = comics,
                recentComics = recents,
                query = "",
                filter = LibraryFilter.All,
                order = LibrarySortOrder.RecentlyAdded,
                metadata = metadata,
                progressFilter = LibraryProgressFilter.Unread,
            ).map { comic -> comic.path },
        )
        assertEquals(
            listOf(completed.path),
            libraryView(
                comics = comics,
                recentComics = recents,
                query = "",
                filter = LibraryFilter.All,
                order = LibrarySortOrder.RecentlyAdded,
                metadata = metadata,
                progressFilter = LibraryProgressFilter.Completed,
            ).map { comic -> comic.path },
        )
        assertEquals(
            listOf(favorite.path),
            libraryView(
                comics = comics,
                recentComics = recents,
                query = "",
                filter = LibraryFilter.All,
                order = LibrarySortOrder.RecentlyAdded,
                metadata = metadata,
                tagFilter = "manga",
            ).map { comic -> comic.path },
        )
    }

    @Test
    fun resolvedLibraryFilters_defaultLockedProFiltersUntilProUnlocked() {
        assertEquals(
            LibraryProgressFilter.All,
            resolvedLibraryProgressFilter(LibraryProgressFilter.Favorites, isProUnlocked = false),
        )
        assertEquals(
            LibraryProgressFilter.Favorites,
            resolvedLibraryProgressFilter(LibraryProgressFilter.Favorites, isProUnlocked = true),
        )
        assertNull(resolvedLibraryTagFilter("Manga", isProUnlocked = false))
        assertEquals("Manga", resolvedLibraryTagFilter("Manga", isProUnlocked = true))
    }

    @Test
    fun filterLibraryComicsByQuery_matchesComicInfoWritersGenresAndSeries() {
        val first = sampleLibraryComic(title = "Issue 1", path = "/storage/emulated/0/a.cbz")
        val second = sampleLibraryComic(title = "Issue 2", path = "/storage/emulated/0/b.cbz")
        val details = listOf(
            LibraryComicDetails(
                sourceType = first.sourceType,
                path = first.path,
                metadata = ComicInfoMetadata(
                    series = "Storm Beacon",
                    writers = listOf("Mara Quell"),
                    publisher = "Northlight Press",
                    genres = listOf("Adventure"),
                ),
            ),
        )

        listOf("quell", "storm", "northlight", "ADVENTURE").forEach { query ->
            assertEquals(listOf(first), filterLibraryComicsByQuery(listOf(first, second), query, details = details))
        }
        assertEquals(emptyList<LibraryComic>(), filterLibraryComicsByQuery(listOf(first, second), "fantasy", details = details))
    }

    @Test
    fun finishedComicsBeingReadAgain_countAsFinishedEverywhere() {
        val comic = sampleLibraryComic(path = "/storage/emulated/0/Saga.cbz")
        // Finished once, then reopened at page 3.
        val rereading = sampleRecentComic(path = comic.path, lastPageIndex = 2, pageCount = 10, lastOpenedAt = 50L)
            .copy(completedAt = 40L)
        val recents = listOf(rereading)

        fun filtered(filter: LibraryProgressFilter) =
            filterLibraryComicsByProgress(listOf(comic), recents, emptyList(), filter)

        assertTrue(rereading.isFinished())
        assertEquals(listOf(comic), filtered(LibraryProgressFilter.Completed))
        assertTrue(filtered(LibraryProgressFilter.InProgress).isEmpty())
        assertTrue(continueReadingComics(listOf(comic), recents).isEmpty())
    }

    @Test
    fun continueReadingComics_keepsStartedUnfinishedComicsByLastOpened() {
        val unread = sampleLibraryComic(title = "Unread", path = "/c/unread.cbz")
        val older = sampleLibraryComic(title = "Older", path = "/c/older.cbz")
        val newer = sampleLibraryComic(title = "Newer", path = "/c/newer.cbz")
        val finished = sampleLibraryComic(title = "Finished", path = "/c/finished.cbz")
        val recent = listOf(
            sampleRecentComic(path = older.path, lastPageIndex = 2, pageCount = 10, lastOpenedAt = 100L),
            sampleRecentComic(path = newer.path, lastPageIndex = 5, pageCount = 10, lastOpenedAt = 200L),
            sampleRecentComic(path = finished.path, lastPageIndex = 9, pageCount = 10, lastOpenedAt = 300L),
        )

        assertEquals(
            listOf(newer, older),
            continueReadingComics(listOf(unread, older, newer, finished), recent),
        )
        assertEquals(listOf(newer), continueReadingComics(listOf(unread, older, newer, finished), recent, maxItems = 1))
    }

    @Test
    fun isNewLibraryComic_onlyForUnopenedComicsAddedThisWeek() {
        val day = 24L * 60L * 60L * 1000L
        val now = 30L * day
        val fresh = sampleLibraryComic(path = "/c/fresh.cbz", addedAt = now - day)
        val old = sampleLibraryComic(path = "/c/old.cbz", addedAt = now - 8L * day)
        val opened = sampleLibraryComic(path = "/c/opened.cbz", addedAt = now - day)
        val recent = listOf(sampleRecentComic(path = opened.path, lastOpenedAt = now))

        assertTrue(isNewLibraryComic(fresh, recent, now))
        assertFalse(isNewLibraryComic(old, recent, now))
        assertFalse(isNewLibraryComic(opened, recent, now))
        assertFalse(isNewLibraryComic(sampleLibraryComic(path = "/c/future.cbz", addedAt = now + day), recent, now))
    }
}
