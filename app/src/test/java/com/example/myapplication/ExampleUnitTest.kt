package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
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
    fun comicLoadErrorForPageCount_reportsEmptyComic() {
        assertEquals(EMPTY_COMIC_MESSAGE, comicLoadErrorForPageCount(0))
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
}

private data class BrowserSortCase(
    val name: String,
    val isDirectory: Boolean,
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
