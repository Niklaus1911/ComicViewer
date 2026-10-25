package com.comicviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryComicsTest {
    @Test
    fun encodeAndDecodeLibraryComics_roundTripsNewestFirst() {
        val older = sampleLibraryComic(
            title = "Older",
            path = "/storage/emulated/0/Older.cbz",
            addedAt = 100L,
        )
        val newer = sampleLibraryComic(
            title = "Newer",
            path = "/storage/emulated/0/Newer.cbz",
            addedAt = 200L,
        )

        val result = decodeLibraryComics(encodeLibraryComics(listOf(older, newer)))

        assertEquals(listOf(newer, older), result)
    }

    @Test
    fun documentLibraryComicFactories_keepUriAndSourceKind() {
        val archive = libraryComicForDocumentArchive(
            uri = "content://provider/document/archive%3ABatman.cbz",
            displayName = "Batman.cbz",
            addedAt = 200L,
        )
        val folder = libraryComicForDocumentFolder(
            uri = "content://provider/tree/folder%3AManga",
            displayName = "Manga",
            addedAt = 100L,
        )

        assertEquals(RecentComicSourceType.DocumentArchive, archive.sourceType)
        assertEquals("content://provider/document/archive%3ABatman.cbz", archive.path)
        assertEquals("Batman", archive.title)
        assertEquals(RecentComicSourceType.DocumentFolder, folder.sourceType)
        assertEquals("content://provider/tree/folder%3AManga", folder.path)
        assertEquals("Manga", folder.title)
    }

    @Test
    fun upsertLibraryComic_dedupesAndKeepsRecentlyAddedFirst() {
        val first = sampleLibraryComic(
            path = "/storage/emulated/0/Batman.cbz",
            addedAt = 100L,
        )
        val second = sampleLibraryComic(
            path = "/storage/emulated/0/Saga.cbz",
            addedAt = 200L,
        )
        val updatedFirst = sampleLibraryComic(
            title = "Batman updated",
            path = first.path,
            addedAt = 300L,
        )

        val result = upsertLibraryComic(listOf(second, first), updatedFirst)

        assertEquals(listOf(first.path, second.path), result.map { comic -> comic.path })
        assertEquals("Batman updated", result.first().title)
    }

    @Test
    fun upsertLibraryComic_keepsExistingComicsWhenLibraryIsFull() {
        val first = sampleLibraryComic(path = "/storage/emulated/0/Batman.cbz", addedAt = 100L)
        val second = sampleLibraryComic(path = "/storage/emulated/0/Saga.cbz", addedAt = 200L)
        val newcomer = sampleLibraryComic(path = "/storage/emulated/0/Monstress.cbz", addedAt = 300L)
        val refreshedFirst = first.copy(addedAt = 400L)

        assertEquals(listOf(second, first), upsertLibraryComic(listOf(second, first), newcomer, maxItems = 2))
        assertEquals(
            listOf(first.path, second.path),
            upsertLibraryComic(listOf(second, first), refreshedFirst, maxItems = 2).map { it.path },
        )
    }

    @Test
    fun upsertLibraryComics_addsMultipleComicsAndKeepsNewestFirst() {
        val existing = sampleLibraryComic(
            path = "/storage/emulated/0/Existing.cbz",
            addedAt = 100L,
        )
        val first = sampleLibraryComic(
            path = "/storage/emulated/0/Batman.cbz",
            addedAt = 200L,
        )
        val second = sampleLibraryComic(
            path = "/storage/emulated/0/Saga.cbz",
            addedAt = 201L,
        )

        val result = upsertLibraryComics(
            libraryComics = listOf(existing),
            newComics = listOf(first, second),
        )

        assertEquals(
            listOf(second.path, first.path, existing.path),
            result.map { comic -> comic.path },
        )
    }

    @Test
    fun upsertLibraryComics_dedupesBatchAgainstExistingLibrary() {
        val existing = sampleLibraryComic(
            title = "Old Batman",
            path = "/storage/emulated/0/Batman.cbz",
            addedAt = 100L,
        )
        val updated = sampleLibraryComic(
            title = "New Batman",
            path = existing.path,
            addedAt = 300L,
        )
        val newComic = sampleLibraryComic(
            path = "/storage/emulated/0/Saga.cbz",
            addedAt = 301L,
        )

        val result = upsertLibraryComics(
            libraryComics = listOf(existing),
            newComics = listOf(updated, newComic),
        )

        assertEquals(listOf(newComic.path, updated.path), result.map { comic -> comic.path })
        assertEquals("New Batman", result.single { comic -> comic.path == existing.path }.title)
    }

    @Test
    fun removeLibraryComic_removesOnlyMatchingSourceAndPath() {
        val archive = sampleLibraryComic(
            path = "/storage/emulated/0/Comic.cbz",
            sourceType = RecentComicSourceType.LocalArchive,
        )
        val folder = sampleLibraryComic(
            path = archive.path,
            sourceType = RecentComicSourceType.LocalFolder,
        )

        val result = removeLibraryComic(
            libraryComics = listOf(archive, folder),
            sourceType = RecentComicSourceType.LocalArchive,
            path = archive.path,
        )

        assertEquals(listOf(folder), result)
    }

    @Test
    fun encodeAndDecodeLibraryMetadata_roundTripsAndNormalizesTags() {
        val metadata = LibraryComicMetadata(
            sourceType = RecentComicSourceType.LocalArchive,
            path = "/storage/emulated/0/Batman.cbz",
            favorite = true,
            tags = listOf("  Manga ", "manga", "DC"),
            updatedAt = 300L,
        )

        val result = decodeLibraryMetadata(encodeLibraryMetadata(listOf(metadata)))

        assertEquals(
            listOf(
                metadata.copy(tags = listOf("DC", "Manga")),
            ),
            result,
        )
    }

    @Test
    fun toggleLibraryFavorite_togglesAndRemovesEmptyMetadataWhenCleared() {
        val comic = sampleLibraryComic(path = "/storage/emulated/0/Batman.cbz")

        val favorited = toggleLibraryFavorite(
            libraryComic = comic,
            metadata = emptyList(),
            nowMs = 100L,
        )
        assertTrue(favorited.single().favorite)

        val cleared = toggleLibraryFavorite(
            libraryComic = comic,
            metadata = favorited,
            nowMs = 200L,
        )
        assertTrue(cleared.isEmpty())
    }

    @Test
    fun setLibraryTags_normalizesAndAvailableTagsSortsNaturally() {
        val comic = sampleLibraryComic(path = "/storage/emulated/0/Batman.cbz")
        val result = setLibraryTags(
            libraryComic = comic,
            metadata = emptyList(),
            tags = listOf("Series 10", "Series 2", "series 2", "  "),
            nowMs = 100L,
        )

        assertEquals(listOf("Series 2", "Series 10"), result.single().tags)
        assertEquals(listOf("Series 2", "Series 10"), availableLibraryTags(result))
    }

    @Test
    fun resolvedLibraryMetadata_hidesMetadataUntilProUnlocked() {
        val metadata = listOf(
            LibraryComicMetadata(
                sourceType = RecentComicSourceType.LocalArchive,
                path = "/storage/emulated/0/Favorite.cbz",
                favorite = true,
                tags = listOf("Manga"),
                updatedAt = 100L,
            ),
        )

        assertTrue(resolvedLibraryMetadata(metadata, isProUnlocked = false).isEmpty())
        assertEquals(metadata, resolvedLibraryMetadata(metadata, isProUnlocked = true))
    }
}
