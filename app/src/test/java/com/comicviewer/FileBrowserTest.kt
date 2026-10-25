package com.comicviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileBrowserTest {
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
    fun browserEntryTypeFor_opensPdfsLikeArchives() {
        assertEquals(
            BrowserEntryType.Archive,
            browserEntryTypeFor(name = "Annual.PDF", isDirectory = false, isImageFile = false),
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
