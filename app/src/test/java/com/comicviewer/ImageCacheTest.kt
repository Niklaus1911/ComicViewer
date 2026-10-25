package com.comicviewer

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImageCacheTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun comicPageImageCacheKey_separatesDifferentArchivesWithSameExtractedPageUri() {
        val firstSource = ComicSource.LocalArchive(
            filePath = "/storage/emulated/0/Batman.cbz",
            displayName = "Batman.cbz",
        )
        val secondSource = ComicSource.LocalArchive(
            filePath = "/storage/emulated/0/Saga.cbz",
            displayName = "Saga.cbz",
        )

        assertNotEquals(
            comicPageImageCacheKey(
                source = firstSource,
                pageIndex = 0,
                relativePath = "001.jpg",
                imageUri = "file:///cache/current_comic_pages/page_00000.jpg",
            ),
            comicPageImageCacheKey(
                source = secondSource,
                pageIndex = 0,
                relativePath = "001.jpg",
                imageUri = "file:///cache/current_comic_pages/page_00000.jpg",
            ),
        )
    }

    @Test
    fun comicPageImageCacheKey_separatesPagesInSameArchive() {
        val source = ComicSource.LocalArchive(
            filePath = "/storage/emulated/0/Batman.cbz",
            displayName = "Batman.cbz",
        )

        assertNotEquals(
            comicPageImageCacheKey(
                source = source,
                pageIndex = 0,
                relativePath = "001.jpg",
                imageUri = "file:///cache/current_comic_pages/page_00000.jpg",
            ),
            comicPageImageCacheKey(
                source = source,
                pageIndex = 1,
                relativePath = "002.jpg",
                imageUri = "file:///cache/current_comic_pages/page_00001.jpg",
            ),
        )
    }

    @Test
    fun comicPageImageCacheKey_keepsFolderPageUriInKey() {
        val source = ComicSource.LocalFolder(
            folderPath = "/storage/emulated/0/Manga",
            displayName = "Manga",
        )
        val pageUri = "file:///storage/emulated/0/Manga/001.jpg"

        assertTrue(
            comicPageImageCacheKey(
                source = source,
                pageIndex = 0,
                relativePath = "001.jpg",
                imageUri = pageUri,
            ).contains(pageUri),
        )
    }

    @Test
    fun comicPageImageCacheKey_changesWithDocumentPageMetadata() {
        val source = ComicSource.LocalFolder(
            folderPath = "/storage/emulated/0/Manga",
            displayName = "Manga",
        )

        val original = comicPageImageCacheKey(
            source = source,
            pageIndex = 0,
            relativePath = "001.jpg",
            imageUri = "content://provider/document/001",
            sourceFingerprint = "1000:2000",
        )
        val updated = comicPageImageCacheKey(
            source = source,
            pageIndex = 0,
            relativePath = "001.jpg",
            imageUri = "content://provider/document/001",
            sourceFingerprint = "1200:3000",
        )

        assertNotEquals(original, updated)
    }

    @Test
    fun thumbnailSampleSize_keepsDecodedImageAtOrAboveScaleTarget() {
        assertEquals(1, thumbnailSampleSize(width = 1_024, height = 600))
        assertEquals(1, thumbnailSampleSize(width = 1_025, height = 600))
        assertEquals(2, thumbnailSampleSize(width = 1_600, height = 2_400))
        assertEquals(2, thumbnailSampleSize(width = 4_000, height = 2_000))
        assertEquals(1, thumbnailSampleSize(width = 0, height = 2_000))
    }

    @Test
    fun thumbnailOutputDimensions_scalesToTargetWithoutUpscalingSmallSources() {
        assertEquals(
            ThumbnailDimensions(width = 683, height = 1_024),
            thumbnailOutputDimensions(width = 1_600, height = 2_400),
        )
        assertEquals(
            ThumbnailDimensions(width = 1_024, height = 683),
            thumbnailOutputDimensions(width = 2_400, height = 1_600),
        )
        assertEquals(
            ThumbnailDimensions(width = 1_024, height = 1_024),
            thumbnailOutputDimensions(width = 2_048, height = 2_048),
        )
        assertEquals(
            ThumbnailDimensions(width = 600, height = 900),
            thumbnailOutputDimensions(width = 600, height = 900),
        )
        assertNull(thumbnailOutputDimensions(width = 0, height = 900))
    }

    @Test
    fun thumbnailCacheIdentity_isStableAndChangesWithEverySourceIdentityField() {
        val identity = thumbnailCacheIdentity(
            sourcePath = "/storage/emulated/0/Comics/Issue.cbz",
            sourceLength = 10_000L,
            sourceLastModified = 20_000L,
            cacheVersion = 2,
        )

        assertEquals(64, identity.length)
        assertEquals(
            identity,
            thumbnailCacheIdentity(
                sourcePath = "/storage/emulated/0/Comics/Issue.cbz",
                sourceLength = 10_000L,
                sourceLastModified = 20_000L,
                cacheVersion = 2,
            ),
        )
        assertNotEquals(
            identity,
            thumbnailCacheIdentity("/storage/emulated/0/Comics/Other.cbz", 10_000L, 20_000L, 2),
        )
        assertNotEquals(
            identity,
            thumbnailCacheIdentity("/storage/emulated/0/Comics/Issue.cbz", 10_001L, 20_000L, 2),
        )
        assertNotEquals(
            identity,
            thumbnailCacheIdentity("/storage/emulated/0/Comics/Issue.cbz", 10_000L, 20_001L, 2),
        )
        assertNotEquals(
            identity,
            thumbnailCacheIdentity("/storage/emulated/0/Comics/Issue.cbz", 10_000L, 20_000L, 3),
        )
    }

    @Test
    fun trimThumbnailCache_removesOldestFilesAndKeepsProtectedFile() {
        val cacheDir = File(temporaryFolder.root, "thumbnail-cache").apply { mkdirs() }
        val oldest = File(cacheDir, "1_thumbnail.jpg").apply {
            writeBytes(ByteArray(10))
            setLastModified(1L)
        }
        val middle = File(cacheDir, "2_thumbnail.jpg").apply {
            writeBytes(ByteArray(10))
            setLastModified(2L)
        }
        val newest = File(cacheDir, "3_thumbnail.jpg").apply {
            writeBytes(ByteArray(10))
            setLastModified(3L)
        }
        val inProgress = File(cacheDir, "thumbnail_in_progress.tmp").apply {
            writeBytes(ByteArray(100))
            setLastModified(0L)
        }

        trimThumbnailCache(
            cacheDir = cacheDir,
            maxBytes = 20L,
            maxFiles = 2,
            protectedFile = newest,
        )

        assertFalse(oldest.exists())
        assertTrue(middle.exists())
        assertTrue(newest.exists())
        assertTrue(inProgress.exists())
    }
}
