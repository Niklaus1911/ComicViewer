package com.comicviewer

import com.android.billingclient.api.BillingClient
import com.comicviewer.billing.ProBillingMessage
import com.comicviewer.billing.ProAcknowledgeAction
import com.comicviewer.billing.billingRetryDelayMillis
import com.comicviewer.billing.isRecoverableBillingResponseCode
import com.comicviewer.billing.proAcknowledgementDeadlineMillis
import com.comicviewer.billing.proAcknowledgeActionFor
import com.comicviewer.billing.userMessageForBillingResponseCode
import com.comicviewer.ui.app.restoreReaderUiState
import com.comicviewer.ui.app.saveReaderUiState
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import com.comicviewer.ui.reader.ReaderSwipeDirection
import com.comicviewer.ui.reader.readerEffectivePageScale
import com.comicviewer.ui.reader.readerFitWidthScaleMultiplier
import com.comicviewer.ui.reader.readerLogicalDirectionForPhysicalDirection
import com.comicviewer.ui.reader.readerNavigationDirectionForTap
import com.comicviewer.ui.reader.readerPhysicalSwipeCommitDirection
import com.comicviewer.ui.reader.readerSwipeAdjacentDirection
import com.comicviewer.ui.reader.readerIncomingPageOffsetAfterCommit
import com.comicviewer.ui.reader.readerSwipeDirectionForOffset
import com.comicviewer.ui.reader.readerSwipeCommitDirection
import com.comicviewer.ui.reader.readerSwipeStartOffset
import com.comicviewer.ui.reader.readerSwipeTargetIndex
import com.comicviewer.ui.reader.resistedReaderSwipeOffset
import com.comicviewer.ui.theme.AppThemePreset
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlinx.coroutines.CancellationException

class ExampleUnitTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun isSupportedArchiveName_acceptsZipAndCbzOnly() {
        assertTrue(isSupportedArchiveName("comic.zip"))
        assertTrue(isSupportedArchiveName("comic.CBZ"))

        assertFalse(isSupportedArchiveName("comic.rar"))
        assertFalse(isSupportedArchiveName("comic.pdf"))
        assertFalse(isSupportedArchiveName("comic"))
    }

    @Test
    fun detectImageFormat_acceptsImageBytesWithoutFileExtensions() {
        assertEquals(ImageFormat.JPEG, detectImageFormat(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())))
        assertEquals(
            ImageFormat.PNG,
            detectImageFormat(
                byteArrayOf(
                    0x89.toByte(),
                    0x50,
                    0x4E,
                    0x47,
                    0x0D,
                    0x0A,
                    0x1A,
                    0x0A,
                ),
            ),
        )
        assertEquals(
            ImageFormat.WEBP,
            detectImageFormat(
                byteArrayOf(
                    0x52,
                    0x49,
                    0x46,
                    0x46,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x57,
                    0x45,
                    0x42,
                    0x50,
                ),
            ),
        )
    }

    @Test
    fun detectImageFormat_rejectsNonImages() {
        assertNull(detectImageFormat(byteArrayOf()))
        assertNull(detectImageFormat("not an image".encodeToByteArray()))
    }

    @Test
    fun readImageHeader_readsOnlyHeaderBytes() {
        val bytes = ByteArray(32) { index -> index.toByte() }
        val inputStream = ByteArrayInputStream(bytes)

        val header = readImageHeader(inputStream)

        assertArrayEquals(ByteArray(12) { index -> index.toByte() }, header)
        assertEquals(20, inputStream.available())
    }

    @Test
    fun readImageHeader_padsShortStreams() {
        val header = readImageHeader(ByteArrayInputStream(byteArrayOf(1, 2, 3)))

        assertArrayEquals(
            ByteArray(12) { index -> if (index < 3) (index + 1).toByte() else 0 },
            header,
        )
    }

    @Test
    fun copyToFileBounded_copiesBelowLimit() {
        val bytes = ByteArray(1024) { index -> (index % 251).toByte() }
        val outputFile = File(temporaryFolder.root, "page.bin")

        val copiedBytes = copyToFileBounded(
            inputStream = ByteArrayInputStream(bytes),
            outputFile = outputFile,
            maxBytes = bytes.size.toLong(),
        )

        assertEquals(bytes.size.toLong(), copiedBytes)
        assertArrayEquals(bytes, outputFile.readBytes())
    }

    @Test
    fun copyToFileBounded_failsAboveLimitAndDeletesPartialFile() {
        val outputFile = File(temporaryFolder.root, "partial-page.bin")

        assertThrows(ComicLoadException::class.java) {
            copyToFileBounded(
                inputStream = ByteArrayInputStream(ByteArray(70 * 1024)),
                outputFile = outputFile,
                maxBytes = 64L * 1024L,
            )
        }

        assertFalse(outputFile.exists())
    }

    @Test
    fun archiveExtractionFailureFor_preservesCancellationException() {
        val cancellationException = CancellationException("Cancelled")

        assertSame(cancellationException, archiveExtractionFailureFor(cancellationException))
    }

    @Test
    fun archiveExtractionFailureFor_mapsStorageFailuresToReadableMessage() {
        val exception = archiveExtractionFailureFor(IOException("Disk full"))

        assertTrue(exception is ComicLoadException)
        assertEquals(ComicLoadError.ArchiveExtractionFailed.defaultMessage(), exception.message)
    }

    @Test
    fun compareComicPageNames_sortsNumberedPagesNaturally() {
        val pages = listOf(
            "010.jpg",
            "002.jpg",
            "001.jpg",
            "page-11.png",
            "page-3.png",
        )

        assertEquals(
            listOf("001.jpg", "002.jpg", "010.jpg", "page-3.png", "page-11.png"),
            sortComicPaths(pages),
        )
    }

    @Test
    fun sortComicPaths_sortsArbitraryArchiveNamesNaturally() {
        val pages = listOf(
            "Chapter 2/scan",
            "Chapter 10/scan",
            "Chapter 1/pageB",
            "Chapter 1/pageA",
        )

        assertEquals(
            listOf("Chapter 1/pageA", "Chapter 1/pageB", "Chapter 2/scan", "Chapter 10/scan"),
            sortComicPaths(pages),
        )
    }

    @Test
    fun isHiddenComicPath_skipsHiddenAndMacOsMetadata() {
        assertTrue(isHiddenComicPath("__MACOSX/001"))
        assertTrue(isHiddenComicPath(".hidden/001"))
        assertTrue(isHiddenComicPath("pages/.DS_Store"))

        assertFalse(isHiddenComicPath("pages/001"))
        assertFalse(isHiddenComicPath("chapter 1/page"))
    }

    @Test
    fun coercePageIndex_clampsToAvailablePages() {
        assertEquals(0, coercePageIndex(-1, pageCount = 5))
        assertEquals(3, coercePageIndex(3, pageCount = 5))
        assertEquals(4, coercePageIndex(20, pageCount = 5))
        assertEquals(0, coercePageIndex(20, pageCount = 0))
    }

    @Test
    fun shouldShowWelcome_onlyForFreshUnseenInstalls() {
        assertTrue(
            shouldShowWelcome(
                welcomeSeen = false,
                hasLibraryComics = false,
                hasRecentComics = false,
            ),
        )

        assertFalse(
            shouldShowWelcome(
                welcomeSeen = true,
                hasLibraryComics = false,
                hasRecentComics = false,
            ),
        )
        assertFalse(
            shouldShowWelcome(
                welcomeSeen = false,
                hasLibraryComics = true,
                hasRecentComics = false,
            ),
        )
        assertFalse(
            shouldShowWelcome(
                welcomeSeen = false,
                hasLibraryComics = false,
                hasRecentComics = true,
            ),
        )
    }

    @Test
    fun comicLoadErrorForPageCount_reportsEmptyComic() {
        assertEquals(ComicLoadError.NoSupportedImagesFound, comicLoadErrorForPageCount(0))
        assertNull(comicLoadErrorForPageCount(1))
    }

    @Test
    fun browserEntryTypeFor_keepsDirectoriesAsDirectories() {
        assertEquals(
            BrowserEntryType.Directory,
            browserEntryTypeFor(
                name = "Chapter 1",
                isDirectory = true,
                isImageFile = false,
            ),
        )
        assertEquals(
            BrowserEntryType.Directory,
            browserEntryTypeFor(
                name = "Series",
                isDirectory = true,
                isImageFile = false,
            ),
        )
    }

    @Test
    fun browserEntryTypeFor_classifiesArchivesImagesAndUnsupportedFiles() {
        assertEquals(
            BrowserEntryType.Archive,
            browserEntryTypeFor(
                name = "comic.cbz",
                isDirectory = false,
                isImageFile = false,
            ),
        )
        assertEquals(
            BrowserEntryType.ImageFile,
            browserEntryTypeFor(
                name = "cover",
                isDirectory = false,
                isImageFile = true,
            ),
        )
        assertEquals(
            BrowserEntryType.UnsupportedFile,
            browserEntryTypeFor(
                name = "notes.txt",
                isDirectory = false,
                isImageFile = false,
            ),
        )
    }

    @Test
    fun browserEntry_canOpen_allowsOnlyDirectoriesAndArchives() {
        assertTrue(
            BrowserEntry(
                name = "Series",
                path = "/storage/emulated/0/Series",
                type = BrowserEntryType.Directory,
                thumbnailUri = null,
            ).canOpen,
        )
        assertTrue(
            BrowserEntry(
                name = "comic.cbz",
                path = "/storage/emulated/0/comic.cbz",
                type = BrowserEntryType.Archive,
                thumbnailUri = null,
            ).canOpen,
        )
        assertFalse(
            BrowserEntry(
                name = "cover.jpg",
                path = "/storage/emulated/0/cover.jpg",
                type = BrowserEntryType.ImageFile,
                thumbnailUri = null,
            ).canOpen,
        )
        assertFalse(
            BrowserEntry(
                name = "notes.txt",
                path = "/storage/emulated/0/notes.txt",
                type = BrowserEntryType.UnsupportedFile,
                thumbnailUri = null,
            ).canOpen,
        )
    }

    @Test
    fun filterBrowserEntries_blankQueryReturnsAllEntries() {
        val entries = sampleBrowserEntries()

        assertEquals(entries, filterBrowserEntries(entries, ""))
        assertEquals(entries, filterBrowserEntries(entries, "   "))
    }

    @Test
    fun filterBrowserEntries_matchesNamesCaseInsensitively() {
        val entries = listOf(
            BrowserEntry(
                name = "Batman 001.cbz",
                path = "/storage/emulated/0/Batman 001.cbz",
                type = BrowserEntryType.Archive,
                thumbnailUri = null,
            ),
        )

        assertEquals(
            listOf("Batman 001.cbz"),
            filterBrowserEntries(entries, "batMAN").map { it.name },
        )
    }

    @Test
    fun filterBrowserEntries_matchesAllEntryTypes() {
        val entries = sampleBrowserEntries()

        assertEquals(
            listOf("Comic Folder", "comic archive.cbz", "comic cover.JPG", "comic notes.txt"),
            filterBrowserEntries(entries, "comic").map { it.name },
        )
    }

    @Test
    fun filterBrowserEntries_returnsEmptyListWhenNothingMatches() {
        assertTrue(filterBrowserEntries(sampleBrowserEntries(), "spider").isEmpty())
    }

    @Test
    fun browserParentPathFor_returnsParentInsideRoot() {
        assertEquals(
            "/storage/emulated/0",
            browserParentPathFor(
                currentPath = "/storage/emulated/0/Download",
                rootPath = "/storage/emulated/0",
            ),
        )
        assertEquals(
            "/storage/emulated/0/Download",
            browserParentPathFor(
                currentPath = "/storage/emulated/0/Download/Comics",
                rootPath = "/storage/emulated/0",
            ),
        )
    }

    @Test
    fun browserParentPathFor_returnsNullAtRoot() {
        assertNull(
            browserParentPathFor(
                currentPath = "/storage/emulated/0",
                rootPath = "/storage/emulated/0",
            ),
        )
    }

    @Test
    fun browserParentPathFor_normalizesCurrentPath() {
        assertEquals(
            "/storage/emulated/0",
            browserParentPathFor(
                currentPath = "/storage/emulated/0/Download/Comics/..",
                rootPath = "/storage/emulated/0",
            ),
        )
    }

    @Test
    fun browserParentPathFor_doesNotClimbOutsideRoot() {
        assertNull(
            browserParentPathFor(
                currentPath = "/storage/emulated",
                rootPath = "/storage/emulated/0",
            ),
        )
        assertNull(
            browserParentPathFor(
                currentPath = "/storage/emulated/01",
                rootPath = "/storage/emulated/0",
            ),
        )
    }

    @Test
    fun browserBreadcrumbs_returnsOnlyRootWhenAtRoot() {
        val crumbs = browserBreadcrumbs(
            currentPath = "/storage/emulated/0",
            rootPath = "/storage/emulated/0",
        )

        assertEquals(listOf("/storage/emulated/0"), crumbs.map { crumb -> crumb.path })
    }

    @Test
    fun browserBreadcrumbs_buildsRootToCurrentChain() {
        val crumbs = browserBreadcrumbs(
            currentPath = "/storage/emulated/0/Download/Comics",
            rootPath = "/storage/emulated/0",
        )

        assertEquals(
            listOf(
                "/storage/emulated/0",
                "/storage/emulated/0/Download",
                "/storage/emulated/0/Download/Comics",
            ),
            crumbs.map { crumb -> crumb.path },
        )
        assertEquals(listOf("0", "Download", "Comics"), crumbs.map { crumb -> crumb.name })
    }

    @Test
    fun browserBreadcrumbs_normalizesDotDotSegments() {
        val crumbs = browserBreadcrumbs(
            currentPath = "/storage/emulated/0/Download/Comics/..",
            rootPath = "/storage/emulated/0",
        )

        assertEquals(
            listOf("/storage/emulated/0", "/storage/emulated/0/Download"),
            crumbs.map { crumb -> crumb.path },
        )
    }

    @Test
    fun browserBreadcrumbs_degradesToRootWhenCurrentOutsideRoot() {
        assertEquals(
            listOf("/storage/emulated/0"),
            browserBreadcrumbs(
                currentPath = "/storage/emulated",
                rootPath = "/storage/emulated/0",
            ).map { crumb -> crumb.path },
        )
        assertEquals(
            listOf("/storage/emulated/0"),
            browserBreadcrumbs(
                currentPath = "/storage/emulated/01",
                rootPath = "/storage/emulated/0",
            ).map { crumb -> crumb.path },
        )
    }

    @Test
    fun browserBreadcrumbs_lastCrumbPathEqualsCanonicalCurrent() {
        val crumbs = browserBreadcrumbs(
            currentPath = "/storage/emulated/0/Download/Comics/..",
            rootPath = "/storage/emulated/0",
        )

        assertEquals("/storage/emulated/0/Download", crumbs.last().path)
    }

    @Test
    fun compareBrowserEntries_sortsDirectoriesFirstThenNaturally() {
        val entries = listOf(
            BrowserSortCase("comic-10.cbz", isDirectory = false),
            BrowserSortCase("Chapter 2", isDirectory = true),
            BrowserSortCase("comic-2.cbz", isDirectory = false),
            BrowserSortCase("Chapter 1", isDirectory = true),
        )

        val sorted = entries.sortedWith { left, right ->
            compareBrowserEntries(
                leftName = left.name,
                leftIsDirectory = left.isDirectory,
                rightName = right.name,
                rightIsDirectory = right.isDirectory,
            )
        }

        assertEquals(
            listOf("Chapter 1", "Chapter 2", "comic-2.cbz", "comic-10.cbz"),
            sorted.map { it.name },
        )
    }

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
        val result = (0..11).fold(emptyList<RecentComic>()) { recents, index ->
            upsertRecentComic(
                recents,
                sampleRecentComic(
                    title = "Comic $index",
                    path = "/storage/emulated/0/Comic $index.cbz",
                    lastOpenedAt = index.toLong(),
                ),
            )
        }

        assertEquals(MAX_RECENT_COMICS, result.size)
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
    fun removeUnreadableRecentComics_filtersStaleItems() {
        val readable = sampleRecentComic(path = "/storage/emulated/0/Readable.cbz")
        val stale = sampleRecentComic(path = "/storage/emulated/0/Missing.cbz")

        val result = removeUnreadableRecentComics(listOf(readable, stale)) { comic ->
            comic.path == readable.path
        }

        assertEquals(listOf(readable), result)
    }

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
    fun scanLibraryArchives_findsOnlyArchivesWithSupportedImages() {
        val root = temporaryFolder.newFolder("scan-root")
        val comicArchive = File(root, "Comic.cbz")
        val pdfFile = File(root, "Comic.pdf")
        val nonComicArchive = File(root, "Notes.zip")
        val invalidArchive = File(root, "Broken.zip")

        writeZipArchive(
            comicArchive,
            mapOf("001.jpg" to sampleJpegHeader()),
        )
        writeZipArchive(
            pdfFile,
            mapOf("001.jpg" to sampleJpegHeader()),
        )
        writeZipArchive(
            nonComicArchive,
            mapOf("notes.txt" to "hello".encodeToByteArray()),
        )
        invalidArchive.writeText("not a zip")

        val result = scanLibraryArchivesInDirectory(root)

        assertEquals(listOf(comicArchive.absolutePath), result.candidates.map { candidate ->
            candidate.archiveFile.absolutePath
        })
    }

    @Test
    fun scanLibraryArchives_doesNotAddImageFolders() {
        val root = temporaryFolder.newFolder("folder-scan-root")
        val extractedFolder = File(root, "Extracted Comic").apply { mkdirs() }
        File(extractedFolder, "001.jpg").writeBytes(sampleJpegHeader())

        val result = scanLibraryArchivesInDirectory(root)

        assertTrue(result.candidates.isEmpty())
    }

    @Test
    fun scanLibraryArchives_skipsHiddenAndTopLevelAndroidDirectoryOnly() {
        val root = temporaryFolder.newFolder("skip-scan-root")
        val hiddenArchive = File(File(root, ".hidden").apply { mkdirs() }, "Hidden.cbz")
        val androidArchive = File(File(root, "Android/data").apply { mkdirs() }, "Android.cbz")
        val nestedAndroidArchive = File(File(root, "Comics/Android").apply { mkdirs() }, "Nested Android.cbz")
        val visibleArchive = File(File(root, "Download").apply { mkdirs() }, "Visible.cbz")

        listOf(hiddenArchive, androidArchive, nestedAndroidArchive, visibleArchive).forEach { archive ->
            writeZipArchive(
                archive,
                mapOf("001.jpg" to sampleJpegHeader()),
            )
        }

        val result = scanLibraryArchivesInDirectory(root)

        assertEquals(
            listOf(visibleArchive.absolutePath, nestedAndroidArchive.absolutePath),
            result.candidates.map { candidate ->
                candidate.archiveFile.absolutePath
            },
        )
    }

    @Test
    fun newLibraryComicsForArchiveScan_skipsExistingAndRespectsLibraryLimit() {
        val existingFile = File(temporaryFolder.root, "Existing.cbz")
        val newFile = File(temporaryFolder.root, "New.cbz")
        val extraFile = File(temporaryFolder.root, "Extra.cbz")
        val existingComic = sampleLibraryComic(path = existingFile.absolutePath)
        val candidates = listOf(existingFile, newFile, extraFile).map { archiveFile ->
            LibraryArchiveScanCandidate(archiveFile = archiveFile)
        }

        val result = newLibraryComicsForArchiveScan(
            libraryComics = listOf(existingComic),
            candidates = candidates,
            addedAt = 100L,
            maxItems = 2,
        )

        assertEquals(listOf(newFile.absolutePath), result.map { comic -> comic.path })
        assertEquals(100L, result.single().addedAt)
    }

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
    fun shouldAcceptThrottledAction_blocksClicksInsideWindow() {
        assertFalse(
            shouldAcceptThrottledAction(
                nowMs = 1_200L,
                lastAcceptedAtMs = 1_000L,
                throttleMs = 350L,
            ),
        )
        assertTrue(
            shouldAcceptThrottledAction(
                nowMs = 1_350L,
                lastAcceptedAtMs = 1_000L,
                throttleMs = 350L,
            ),
        )
    }

    @Test
    fun isTabletWidth_usesSixHundredDpThreshold() {
        assertFalse(isTabletWidth(599))
        assertTrue(isTabletWidth(600))
        assertTrue(isTabletWidth(840))
    }

    @Test
    fun currentAppBuildInfo_includesGeneratedBuildTimestamp() {
        val timestampPattern = Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2} (Z|[+-]\d{2}:\d{2})""")

        assertTrue(currentAppBuildInfo().buildTimestamp.matches(timestampPattern))
    }

    @Test
    fun readerSwipeCommitDirection_commitsWhenDistancePassesThreshold() {
        assertEquals(
            ReaderSwipeDirection.Next,
            readerSwipeCommitDirection(
                offsetX = -150f,
                velocityX = 0f,
                viewportWidth = 1000f,
                currentPageIndex = 2,
                pageCount = 5,
                velocityThresholdPx = 700f,
            ),
        )
        assertEquals(
            ReaderSwipeDirection.Previous,
            readerSwipeCommitDirection(
                offsetX = 150f,
                velocityX = 0f,
                viewportWidth = 1000f,
                currentPageIndex = 2,
                pageCount = 5,
                velocityThresholdPx = 700f,
            ),
        )
    }

    @Test
    fun readerSwipeCommitDirection_commitsWhenVelocityPassesThreshold() {
        assertEquals(
            ReaderSwipeDirection.Next,
            readerSwipeCommitDirection(
                offsetX = -20f,
                velocityX = -720f,
                viewportWidth = 1000f,
                currentPageIndex = 2,
                pageCount = 5,
                velocityThresholdPx = 700f,
            ),
        )
        assertEquals(
            ReaderSwipeDirection.Previous,
            readerSwipeCommitDirection(
                offsetX = 20f,
                velocityX = 720f,
                viewportWidth = 1000f,
                currentPageIndex = 2,
                pageCount = 5,
                velocityThresholdPx = 700f,
            ),
        )
    }

    @Test
    fun readerSwipeCommitDirection_doesNotCommitOutOfBounds() {
        assertNull(
            readerSwipeCommitDirection(
                offsetX = 250f,
                velocityX = 900f,
                viewportWidth = 1000f,
                currentPageIndex = 0,
                pageCount = 5,
                velocityThresholdPx = 700f,
            ),
        )
        assertNull(
            readerSwipeCommitDirection(
                offsetX = -250f,
                velocityX = -900f,
                viewportWidth = 1000f,
                currentPageIndex = 4,
                pageCount = 5,
                velocityThresholdPx = 700f,
            ),
        )
    }

    @Test
    fun resistedReaderSwipeOffset_appliesResistanceOnlyAtEdges() {
        assertEquals(70f, resistedReaderSwipeOffset(250f, currentPageIndex = 0, pageCount = 5))
        assertEquals(-70f, resistedReaderSwipeOffset(-250f, currentPageIndex = 4, pageCount = 5))
        assertEquals(250f, resistedReaderSwipeOffset(250f, currentPageIndex = 2, pageCount = 5))
    }

    @Test
    fun resistedReaderSwipeOffset_continuesInterruptedSettleFromCurrentOffset() {
        val settleOffset = 650f
        val dragDelta = -200f

        assertEquals(
            450f,
            resistedReaderSwipeOffset(
                rawOffsetX = settleOffset + dragDelta,
                currentPageIndex = 2,
                pageCount = 5,
            ),
        )
    }

    @Test
    fun readerIncomingPageOffsetAfterCommit_keepsIncomingPageWithinViewport() {
        assertEquals(
            700f,
            readerIncomingPageOffsetAfterCommit(
                direction = ReaderSwipeDirection.Next,
                currentOffsetX = -300f,
                viewportWidth = 1000f,
            ),
        )
        assertEquals(
            -700f,
            readerIncomingPageOffsetAfterCommit(
                direction = ReaderSwipeDirection.Previous,
                currentOffsetX = 300f,
                viewportWidth = 1000f,
            ),
        )
        assertEquals(
            1000f,
            readerIncomingPageOffsetAfterCommit(
                direction = ReaderSwipeDirection.Next,
                currentOffsetX = 300f,
                viewportWidth = 1000f,
            ),
        )
    }

    @Test
    fun readerSwipeTargetIndex_returnsAdjacentPageOnlyWhenAvailable() {
        assertEquals(1, readerSwipeTargetIndex(2, pageCount = 5, ReaderSwipeDirection.Previous))
        assertEquals(3, readerSwipeTargetIndex(2, pageCount = 5, ReaderSwipeDirection.Next))
        assertNull(readerSwipeTargetIndex(0, pageCount = 5, ReaderSwipeDirection.Previous))
        assertNull(readerSwipeTargetIndex(4, pageCount = 5, ReaderSwipeDirection.Next))
    }

    @Test
    fun readerSwipeDirectionForOffset_mapsOffsetSignToPageDirection() {
        assertEquals(ReaderSwipeDirection.Previous, readerSwipeDirectionForOffset(10f))
        assertEquals(ReaderSwipeDirection.Next, readerSwipeDirectionForOffset(-10f))
        assertNull(readerSwipeDirectionForOffset(0f))
    }

    @Test
    fun readerLogicalDirectionForPhysicalDirection_reversesOnlyForRtl() {
        assertEquals(
            ReaderSwipeDirection.Previous,
            readerLogicalDirectionForPhysicalDirection(
                physicalDirection = ReaderSwipeDirection.Previous,
                readingDirection = ReaderReadingDirection.LeftToRight,
            ),
        )
        assertEquals(
            ReaderSwipeDirection.Next,
            readerLogicalDirectionForPhysicalDirection(
                physicalDirection = ReaderSwipeDirection.Previous,
                readingDirection = ReaderReadingDirection.RightToLeft,
            ),
        )
    }

    @Test
    fun readerNavigationDirectionForTap_usesReadingDirectionForOuterZones() {
        assertEquals(
            ReaderSwipeDirection.Previous,
            readerNavigationDirectionForTap(
                tapX = 100f,
                viewportWidth = 900f,
                readingDirection = ReaderReadingDirection.LeftToRight,
            ),
        )
        assertEquals(
            ReaderSwipeDirection.Next,
            readerNavigationDirectionForTap(
                tapX = 100f,
                viewportWidth = 900f,
                readingDirection = ReaderReadingDirection.RightToLeft,
            ),
        )
        assertNull(
            readerNavigationDirectionForTap(
                tapX = 450f,
                viewportWidth = 900f,
                readingDirection = ReaderReadingDirection.RightToLeft,
            ),
        )
    }

    @Test
    fun readerSwipeCommitDirection_usesRtlReadingDirectionForPhysicalSwipes() {
        assertEquals(
            ReaderSwipeDirection.Next,
            readerSwipeCommitDirection(
                offsetX = 150f,
                velocityX = 0f,
                viewportWidth = 1000f,
                currentPageIndex = 2,
                pageCount = 5,
                velocityThresholdPx = 700f,
                readingDirection = ReaderReadingDirection.RightToLeft,
            ),
        )
        assertEquals(
            ReaderSwipeDirection.Previous,
            readerSwipeCommitDirection(
                offsetX = -150f,
                velocityX = 0f,
                viewportWidth = 1000f,
                currentPageIndex = 2,
                pageCount = 5,
                velocityThresholdPx = 700f,
                readingDirection = ReaderReadingDirection.RightToLeft,
            ),
        )
    }

    @Test
    fun readerPhysicalSwipeCommitDirection_prefersVelocityOverOffsetForAnimationSide() {
        assertEquals(
            ReaderSwipeDirection.Next,
            readerPhysicalSwipeCommitDirection(
                offsetX = 80f,
                velocityX = -900f,
                velocityThresholdPx = 700f,
            ),
        )
        assertEquals(
            ReaderSwipeDirection.Previous,
            readerPhysicalSwipeCommitDirection(
                offsetX = -80f,
                velocityX = 900f,
                velocityThresholdPx = 700f,
            ),
        )
    }

    @Test
    fun readerIncomingPageOffsetAfterCommit_usesVelocityResolvedPhysicalDirection() {
        val physicalCommitDirection = readerPhysicalSwipeCommitDirection(
            offsetX = 80f,
            velocityX = -900f,
            velocityThresholdPx = 700f,
        )

        assertEquals(
            1000f,
            readerIncomingPageOffsetAfterCommit(
                direction = physicalCommitDirection!!,
                currentOffsetX = 80f,
                viewportWidth = 1000f,
            ),
        )
    }

    @Test
    fun readerFitWidthScaleMultiplier_scalesTallPagesToViewportWidth() {
        assertEquals(
            2f,
            readerFitWidthScaleMultiplier(
                pageWidth = 1000,
                pageHeight = 2000,
                viewportWidth = 1000,
                viewportHeight = 1000,
            ),
            0.0001f,
        )
        assertEquals(
            1f,
            readerFitWidthScaleMultiplier(
                pageWidth = 0,
                pageHeight = 0,
                viewportWidth = 1000,
                viewportHeight = 1000,
            ),
            0.0001f,
        )
    }

    @Test
    fun readerEffectivePageScale_combinesFitWidthBaseScaleAndUserScale() {
        assertEquals(
            1f,
            readerEffectivePageScale(
                pageFitMode = ReaderPageFitMode.FitScreen,
                pageWidth = 1000,
                pageHeight = 2000,
                viewportWidth = 1000,
                viewportHeight = 1000,
                userScale = 1f,
            ),
            0.0001f,
        )
        assertEquals(
            3f,
            readerEffectivePageScale(
                pageFitMode = ReaderPageFitMode.FitWidth,
                pageWidth = 1000,
                pageHeight = 2000,
                viewportWidth = 1000,
                viewportHeight = 1000,
                userScale = 1.5f,
            ),
            0.0001f,
        )
    }

    @Test
    fun resolvedReaderPreferences_returnsDefaultsUntilProUnlocked() {
        val proPreferences = ReaderPreferences(
            readingDirection = ReaderReadingDirection.RightToLeft,
            pageFitMode = ReaderPageFitMode.FitWidth,
            background = ReaderBackground.Sepia,
        )

        assertEquals(ReaderPreferences(), resolvedReaderPreferences(proPreferences, isProUnlocked = false))
        assertEquals(proPreferences, resolvedReaderPreferences(proPreferences, isProUnlocked = true))
    }

    @Test
    fun effectiveProUnlocked_usesDebugOverrideOnlyForDebugBuilds() {
        assertTrue(
            effectiveProUnlocked(
                billingUnlocked = false,
                debugOverrideUnlocked = true,
                isDebugBuild = true,
            ),
        )
        assertFalse(
            effectiveProUnlocked(
                billingUnlocked = false,
                debugOverrideUnlocked = true,
                isDebugBuild = false,
            ),
        )
        assertTrue(
            effectiveProUnlocked(
                billingUnlocked = true,
                debugOverrideUnlocked = false,
                isDebugBuild = false,
            ),
        )
    }

    @Test
    fun isRecoverableBillingResponseCode_marksTransientPurchaseFailuresRecoverable() {
        assertTrue(isRecoverableBillingResponseCode(BillingClient.BillingResponseCode.NETWORK_ERROR))
        assertTrue(isRecoverableBillingResponseCode(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE))
        assertTrue(isRecoverableBillingResponseCode(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED))
        assertTrue(isRecoverableBillingResponseCode(BillingClient.BillingResponseCode.ERROR))
        assertTrue(isRecoverableBillingResponseCode(BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED))
        assertTrue(isRecoverableBillingResponseCode(BillingClient.BillingResponseCode.ITEM_NOT_OWNED))
    }

    @Test
    fun isRecoverableBillingResponseCode_doesNotRetryUserCancelOrFatalSetupFailures() {
        assertFalse(isRecoverableBillingResponseCode(BillingClient.BillingResponseCode.USER_CANCELED))
        assertFalse(isRecoverableBillingResponseCode(BillingClient.BillingResponseCode.BILLING_UNAVAILABLE))
        assertFalse(isRecoverableBillingResponseCode(BillingClient.BillingResponseCode.DEVELOPER_ERROR))
        assertFalse(isRecoverableBillingResponseCode(BillingClient.BillingResponseCode.ITEM_UNAVAILABLE))
    }

    @Test
    fun userMessageForBillingResponseCode_hidesTechnicalMessagesForRecoverableFailures() {
        assertEquals(
            ProBillingMessage.CheckingPurchase,
            userMessageForBillingResponseCode(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE),
        )
        assertEquals(
            ProBillingMessage.CheckingPurchase,
            userMessageForBillingResponseCode(BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED),
        )
        assertEquals(
            ProBillingMessage.PurchaseFailed,
            userMessageForBillingResponseCode(BillingClient.BillingResponseCode.DEVELOPER_ERROR),
        )
    }

    @Test
    fun billingRetryDelayMillis_usesCappedExponentialBackoff() {
        assertEquals(1_500L, billingRetryDelayMillis(attempt = 1))
        assertEquals(3_000L, billingRetryDelayMillis(attempt = 2))
        assertEquals(6_000L, billingRetryDelayMillis(attempt = 3))
        assertEquals(60_000L, billingRetryDelayMillis(attempt = 20))
    }

    @Test
    fun proAcknowledgeActionFor_retriesOnlyRecoverableFailures() {
        assertEquals(
            ProAcknowledgeAction.Success,
            proAcknowledgeActionFor(BillingClient.BillingResponseCode.OK),
        )
        assertEquals(
            ProAcknowledgeAction.Retry,
            proAcknowledgeActionFor(BillingClient.BillingResponseCode.NETWORK_ERROR),
        )
        assertEquals(
            ProAcknowledgeAction.Retry,
            proAcknowledgeActionFor(BillingClient.BillingResponseCode.ITEM_NOT_OWNED),
        )
        assertEquals(
            ProAcknowledgeAction.Failure,
            proAcknowledgeActionFor(BillingClient.BillingResponseCode.DEVELOPER_ERROR),
        )
    }

    @Test
    fun proAcknowledgementDeadlineMillis_usesDetectionTimeAndHandlesBadValues() {
        assertEquals(
            1_500L,
            proAcknowledgementDeadlineMillis(
                detectedAtMs = 1_000L,
                nowMs = 2_000L,
                windowMs = 500L,
            ),
        )
        assertEquals(
            2_500L,
            proAcknowledgementDeadlineMillis(
                detectedAtMs = 0L,
                nowMs = 2_000L,
                windowMs = 500L,
            ),
        )
        assertEquals(
            Long.MAX_VALUE,
            proAcknowledgementDeadlineMillis(
                detectedAtMs = Long.MAX_VALUE - 100L,
                nowMs = 2_000L,
                windowMs = 500L,
            ),
        )
    }

    @Test
    fun readerSwipeStartOffset_preservesCompatibleInterruptedSettleOffset() {
        assertEquals(
            320f,
            readerSwipeStartOffset(
                currentOffsetX = 320f,
                direction = ReaderSwipeDirection.Previous,
            ),
        )
        assertEquals(
            -320f,
            readerSwipeStartOffset(
                currentOffsetX = -320f,
                direction = ReaderSwipeDirection.Next,
            ),
        )
    }

    @Test
    fun readerSwipeStartOffset_snapsConflictingInterruptedSettleOffsetToCenter() {
        assertEquals(
            0f,
            readerSwipeStartOffset(
                currentOffsetX = 320f,
                direction = ReaderSwipeDirection.Next,
            ),
        )
        assertEquals(
            0f,
            readerSwipeStartOffset(
                currentOffsetX = -320f,
                direction = ReaderSwipeDirection.Previous,
            ),
        )
    }

    @Test
    fun readerSwipeAdjacentDirection_prefersActiveGestureDirectionOverOffsetSign() {
        assertEquals(
            ReaderSwipeDirection.Next,
            readerSwipeAdjacentDirection(
                activeSwipeDirection = ReaderSwipeDirection.Next,
                offsetX = 320f,
            ),
        )
        assertEquals(
            ReaderSwipeDirection.Previous,
            readerSwipeAdjacentDirection(
                activeSwipeDirection = ReaderSwipeDirection.Previous,
                offsetX = -320f,
            ),
        )
    }

    @Test
    fun readerSwipeAdjacentDirection_fallsBackToOffsetSignWithoutActiveGesture() {
        assertEquals(
            ReaderSwipeDirection.Previous,
            readerSwipeAdjacentDirection(
                activeSwipeDirection = null,
                offsetX = 320f,
            ),
        )
        assertEquals(
            ReaderSwipeDirection.Next,
            readerSwipeAdjacentDirection(
                activeSwipeDirection = null,
                offsetX = -320f,
            ),
        )
    }

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
        val comics = listOf(archive, folder)

        assertEquals(listOf(archive), filterLibraryComicsBySource(comics, LibraryFilter.Archives))
        assertEquals(listOf(folder), filterLibraryComicsBySource(comics, LibraryFilter.Folders))
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
    fun resolvedThemePreset_defaultsLockedProPresetsUntilProUnlocked() {
        assertEquals(
            AppThemePreset.Default,
            resolvedThemePreset(AppThemePreset.Ink, isProUnlocked = false),
        )
        assertEquals(
            AppThemePreset.Ink,
            resolvedThemePreset(AppThemePreset.Ink, isProUnlocked = true),
        )
        assertEquals(
            AppThemePreset.Default,
            resolvedThemePreset(AppThemePreset.Default, isProUnlocked = false),
        )
    }

    @Test
    fun themePresetForDynamicColorSelection_resetsPresetWhenDynamicColorIsEnabled() {
        assertEquals(
            AppThemePreset.Default,
            themePresetForDynamicColorSelection(
                dynamicColorEnabled = true,
                currentThemePreset = AppThemePreset.Ink,
            ),
        )
        assertEquals(
            AppThemePreset.Ink,
            themePresetForDynamicColorSelection(
                dynamicColorEnabled = false,
                currentThemePreset = AppThemePreset.Ink,
            ),
        )
    }

    @Test
    fun resolveDarkTheme_followsSystemForSystemMode() {
        assertTrue(resolveDarkTheme(AppThemeMode.System, systemInDark = true))
        assertFalse(resolveDarkTheme(AppThemeMode.System, systemInDark = false))
    }

    @Test
    fun resolveDarkTheme_forcesLightAndDarkRegardlessOfSystem() {
        assertFalse(resolveDarkTheme(AppThemeMode.Light, systemInDark = true))
        assertFalse(resolveDarkTheme(AppThemeMode.Light, systemInDark = false))
        assertTrue(resolveDarkTheme(AppThemeMode.Dark, systemInDark = false))
        assertTrue(resolveDarkTheme(AppThemeMode.Dark, systemInDark = true))
    }

    @Test
    fun readerUiStateSaver_savesOnlySourceAndPageIndex() {
        val source = ComicSource.LocalArchive(
            filePath = "/storage/emulated/0/Long Comic.cbz",
            displayName = "Long Comic.cbz",
        )
        val ready = ReaderUiState.Ready(
            book = ComicBook(
                title = "Long Comic",
                source = source,
                pages = emptyList(),
            ),
            pageIndex = 9_999,
        )

        val savedValues = saveReaderUiState(ready)

        assertEquals(5, savedValues.size)
        assertEquals(
            ReaderUiState.Loading(source = source, pageIndex = 9_999),
            restoreReaderUiState(savedValues),
        )
    }

    @Test
    fun readerUiStateSaver_boundsUntrustedStrings() {
        val savedError = saveReaderUiState(ReaderUiState.Error("x".repeat(100_000)))
        val oversizedSource = ComicSource.LocalArchive(
            filePath = "/" + "a".repeat(9_000),
            displayName = "Comic.cbz",
        )

        assertTrue(savedError.sumOf(String::length) < 2_100)
        assertEquals(
            listOf("empty"),
            saveReaderUiState(
                ReaderUiState.Loading(source = oversizedSource, pageIndex = 0),
            ),
        )
    }

    @Test
    fun archiveEntryCopyLimit_distinguishesPageTotalAndStorageLimits() {
        assertEquals(
            ArchiveCopyLimit(
                maxBytes = 100L,
                limitError = ComicLoadError.ArchivePageTooLarge,
            ),
            archiveEntryCopyLimit(
                remainingTotalBytes = 1_000L,
                cacheWritableBytes = 5_000L,
                maxEntryBytes = 100L,
            ),
        )
        assertEquals(
            ArchiveCopyLimit(
                maxBytes = 50L,
                limitError = ComicLoadError.ArchiveContentsTooLarge,
            ),
            archiveEntryCopyLimit(
                remainingTotalBytes = 50L,
                cacheWritableBytes = 5_000L,
                maxEntryBytes = 100L,
            ),
        )
        assertEquals(
            ArchiveCopyLimit(
                maxBytes = 40L,
                limitError = ComicLoadError.NotEnoughArchiveSpace,
            ),
            archiveEntryCopyLimit(
                remainingTotalBytes = 1_000L,
                cacheWritableBytes = 40L,
                maxEntryBytes = 100L,
            ),
        )
    }

    @Test
    fun copyToFileBounded_reportsConfiguredLogicalLimit() {
        val outputFile = File(temporaryFolder.root, "oversized-page.bin")

        val exception = assertThrows(ComicLoadException::class.java) {
            copyToFileBounded(
                inputStream = ByteArrayInputStream(ByteArray(11)),
                outputFile = outputFile,
                maxBytes = 10L,
                limitError = ComicLoadError.ArchivePageTooLarge,
            )
        }

        assertEquals(ComicLoadError.ArchivePageTooLarge, exception.error)
        assertFalse(outputFile.exists())
    }

    @Test
    fun thumbnailSampleSize_boundsDecodedDimensions() {
        assertEquals(1, thumbnailSampleSize(width = 512, height = 300))
        assertEquals(2, thumbnailSampleSize(width = 513, height = 300))
        assertEquals(8, thumbnailSampleSize(width = 4_000, height = 2_000))
        assertEquals(1, thumbnailSampleSize(width = 0, height = 2_000))
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

private data class BrowserSortCase(
    val name: String,
    val isDirectory: Boolean,
)

private fun sampleRecentComic(
    title: String = "Comic",
    path: String = "/storage/emulated/0/Comic.cbz",
    sourceType: RecentComicSourceType = RecentComicSourceType.LocalArchive,
    lastPageIndex: Int = 0,
    pageCount: Int = 10,
    lastOpenedAt: Long = 0L,
): RecentComic =
    RecentComic(
        sourceType = sourceType,
        path = path,
        displayName = path.substringAfterLast('/'),
        title = title,
        lastPageIndex = lastPageIndex,
        pageCount = pageCount,
        lastOpenedAt = lastOpenedAt,
    )

private fun sampleLibraryComic(
    title: String = "Comic",
    path: String = "/storage/emulated/0/Comic.cbz",
    sourceType: RecentComicSourceType = RecentComicSourceType.LocalArchive,
    addedAt: Long = 0L,
): LibraryComic =
    LibraryComic(
        sourceType = sourceType,
        path = path,
        displayName = path.substringAfterLast('/'),
        title = title,
        addedAt = addedAt,
    )

private fun sampleBrowserEntries(): List<BrowserEntry> = listOf(
    BrowserEntry(
        name = "Comic Folder",
        path = "/storage/emulated/0/Manga",
        type = BrowserEntryType.Directory,
        thumbnailUri = null,
    ),
    BrowserEntry(
        name = "comic archive.cbz",
        path = "/storage/emulated/0/Batman 001.cbz",
        type = BrowserEntryType.Archive,
        thumbnailUri = null,
    ),
    BrowserEntry(
        name = "comic cover.JPG",
        path = "/storage/emulated/0/cover.JPG",
        type = BrowserEntryType.ImageFile,
        thumbnailUri = null,
    ),
    BrowserEntry(
        name = "comic notes.txt",
        path = "/storage/emulated/0/notes.txt",
        type = BrowserEntryType.UnsupportedFile,
        thumbnailUri = null,
    ),
)

private fun sampleJpegHeader(): ByteArray =
    byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00, 0x00, 0x00)

private fun writeZipArchive(
    file: File,
    entries: Map<String, ByteArray>,
) {
    file.parentFile?.mkdirs()
    ZipOutputStream(file.outputStream()).use { output ->
        entries.forEach { (name, bytes) ->
            output.putNextEntry(ZipEntry(name))
            output.write(bytes)
            output.closeEntry()
        }
    }
}
