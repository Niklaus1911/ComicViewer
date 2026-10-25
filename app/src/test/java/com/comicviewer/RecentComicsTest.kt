package com.comicviewer

import com.comicviewer.ui.library.currentRecentComicForDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecentComicsTest {
    @Test
    fun upsertRecentComic_dedupesAndKeepsNewestFirst() {
        val first = sampleRecentComic(
            title = "Batman 001",
            path = "/storage/emulated/0/Batman 001.cbz",
            lastOpenedAt = 100L,
        )
        val second = sampleRecentComic(
            title = "Saga 001",
            path = "/storage/emulated/0/Saga 001.cbz",
            lastOpenedAt = 200L,
        )
        val updatedFirst = sampleRecentComic(
            title = "Batman 001 updated",
            path = first.path,
            lastPageIndex = 4,
            pageCount = 12,
            lastOpenedAt = 300L,
        )

        val result = upsertRecentComic(listOf(second, first), updatedFirst)

        assertEquals(listOf(first.path, second.path), result.map { comic -> comic.path })
        assertEquals("Batman 001 updated", result.first().title)
        assertEquals(4, result.first().lastPageIndex)
    }

    @Test
    fun upsertRecentComic_trimsToMaxRecentComics() {
        val maxItems = 10
        val result = (0..11).fold(emptyList<RecentComic>()) { recents, index ->
            upsertRecentComic(
                recents,
                sampleRecentComic(
                    title = "Comic $index",
                    path = "/storage/emulated/0/Comic $index.cbz",
                    lastOpenedAt = index.toLong(),
                ),
                maxItems = maxItems,
            )
        }

        assertEquals(maxItems, result.size)
        assertEquals(
            (11 downTo 2).map { index -> "/storage/emulated/0/Comic $index.cbz" },
            result.map { comic -> comic.path },
        )
    }

    @Test
    fun updateRecentComicPage_clampsPageIndexToAvailablePages() {
        val comic = sampleRecentComic(
            path = "/storage/emulated/0/Batman 001.cbz",
            lastPageIndex = 1,
            pageCount = 20,
        )

        val result = updateRecentComicPage(
            recentComics = listOf(comic),
            sourceType = RecentComicSourceType.LocalArchive,
            path = comic.path,
            pageIndex = 99,
            pageCount = 7,
        )

        assertEquals(6, result.single().lastPageIndex)
        assertEquals(7, result.single().pageCount)
    }

    @Test
    fun encodeAndDecodeRecentComics_roundTripsDocumentSources() {
        val archive = sampleRecentComic(
            title = "Document archive",
            path = "content://provider/document/archive%3AComic.cbz",
            sourceType = RecentComicSourceType.DocumentArchive,
            lastOpenedAt = 200L,
        )
        val folder = sampleRecentComic(
            title = "Document folder",
            path = "content://provider/tree/folder%3AComics",
            sourceType = RecentComicSourceType.DocumentFolder,
            lastOpenedAt = 100L,
        )

        val result = decodeRecentComics(encodeRecentComics(listOf(folder, archive)))

        assertEquals(listOf(archive, folder), result)
    }

    @Test
    fun decodeRecentComics_boundsUntrustedProgressValues() {
        val decoded = decodeRecentComics(
            """
            [
              {"sourceType":"local_archive","path":"/one.cbz","displayName":"one.cbz","title":"One","lastPageIndex":250000,"pageCount":250000,"lastOpenedAt":-5},
              {"sourceType":"local_archive","path":"/two.cbz","displayName":"two.cbz","title":"Two","lastPageIndex":5,"pageCount":-10,"lastOpenedAt":10}
            ]
            """.trimIndent(),
        )

        assertEquals(100_000, decoded.first { it.path == "/one.cbz" }.pageCount)
        assertEquals(99_999, decoded.first { it.path == "/one.cbz" }.lastPageIndex)
        assertEquals(0L, decoded.first { it.path == "/one.cbz" }.lastOpenedAt)
        assertEquals(0, decoded.first { it.path == "/two.cbz" }.pageCount)
        assertEquals(0, decoded.first { it.path == "/two.cbz" }.lastPageIndex)
    }

    @Test
    fun currentRecentComicForDetails_usesLatestPersistedProgress() {
        val selected = sampleRecentComic(lastPageIndex = 1, pageCount = 10, lastOpenedAt = 100L)
        val updated = selected.copy(lastPageIndex = 7, lastOpenedAt = 200L)

        assertEquals(updated, currentRecentComicForDetails(selected, listOf(updated)))
        assertEquals(selected, currentRecentComicForDetails(selected, emptyList()))
    }

    @Test
    fun markRecentComicRead_finishesUnopenedAndStartedComics() {
        val unopened = sampleLibraryComic(title = "Unopened", path = "/c/unopened.cbz")
        val started = sampleLibraryComic(title = "Started", path = "/c/started.cbz")
        val recent = listOf(sampleRecentComic(path = started.path, lastPageIndex = 3, pageCount = 20, lastOpenedAt = 5L))

        val afterUnopened = markRecentComicRead(recent, unopened, knownPageCount = 24, nowMs = 1_000L)
        val finishedUnopened = afterUnopened.first { it.path == unopened.path }
        assertEquals(23, finishedUnopened.lastPageIndex)
        assertEquals(24, finishedUnopened.pageCount)
        assertEquals(1_000L, finishedUnopened.completedAt)
        assertEquals(1f, libraryProgressFraction(unopened, afterUnopened))

        val afterStarted = markRecentComicRead(recent, started, knownPageCount = null, nowMs = 2_000L)
        assertEquals(1, afterStarted.size)
        assertEquals(19, afterStarted.single().lastPageIndex)
        assertEquals(1f, libraryProgressFraction(started, afterStarted))

        val unknownCount = markRecentComicRead(emptyList(), unopened, knownPageCount = null, nowMs = 3_000L)
        assertEquals(1f, libraryProgressFraction(unopened, unknownCount))
    }

    @Test
    fun updateRecentComicPage_recordsCompletionTimestampOnce() {
        val comic = sampleRecentComic(lastPageIndex = 3, pageCount = 10)

        val unfinished = updateRecentComicPage(
            recentComics = listOf(comic),
            sourceType = comic.sourceType,
            path = comic.path,
            pageIndex = 8,
            pageCount = 10,
            nowMs = 100L,
        ).single()
        val completed = updateRecentComicPage(
            recentComics = listOf(unfinished),
            sourceType = comic.sourceType,
            path = comic.path,
            pageIndex = 9,
            pageCount = 10,
            nowMs = 200L,
        ).single()
        val reopened = updateRecentComicPage(
            recentComics = listOf(completed),
            sourceType = comic.sourceType,
            path = comic.path,
            pageIndex = 9,
            pageCount = 10,
            nowMs = 300L,
        ).single()

        assertNull(unfinished.completedAt)
        assertEquals(200L, completed.completedAt)
        assertEquals(200L, reopened.completedAt)
    }

    @Test
    fun mergeOpenedRecentComic_clearsStaleCompletionWhenPageCountChanges() {
        val completed = sampleRecentComic(
            lastPageIndex = 9,
            pageCount = 10,
            lastOpenedAt = 100L,
        ).copy(completedAt = 100L)
        val sameEditionReopenedEarlier = completed.copy(lastPageIndex = 4, lastOpenedAt = 200L, completedAt = null)
        val expandedEdition = completed.copy(
            lastPageIndex = 9,
            pageCount = 20,
            lastOpenedAt = 300L,
            completedAt = null,
        )

        assertEquals(100L, mergeOpenedRecentComic(completed, sameEditionReopenedEarlier).completedAt)
        assertNull(mergeOpenedRecentComic(completed, expandedEdition).completedAt)
    }
}
