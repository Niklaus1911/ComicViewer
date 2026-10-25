package com.comicviewer

import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LibraryScanTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

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
    fun scanLibraryArchives_reportsKnownArchivesWithoutOpeningThem() {
        val root = temporaryFolder.newFolder("known-scan-root")
        // Neither file is a readable archive, so only the library's own entry can be reported.
        val knownArchive = File(root, "Known.cbz").apply { writeText("not a zip") }
        File(root, "Unknown.cbz").writeText("not a zip")

        val result = scanLibraryArchivesInDirectory(
            root = root,
            knownArchivePaths = setOf(knownArchive.absolutePath),
        )

        assertEquals(listOf(knownArchive.absolutePath), result.candidates.map { candidate ->
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
    fun archiveContainsSupportedImage_countsHiddenEntriesTowardLimit() {
        val archive = File(temporaryFolder.root, "Hidden first.cbz")
        writeZipArchive(
            archive,
            linkedMapOf(
                ".hidden" to "ignored".encodeToByteArray(),
                "001.jpg" to sampleJpegHeader(),
            ),
        )

        assertFalse(archiveContainsSupportedImage(archive, maxEntries = 1))
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
    fun scanLibraryArchives_stopsAtCandidateLimitAndSupportsCancellation() {
        val root = temporaryFolder.newFolder("bounded-scan-root")
        listOf("One.cbz", "Two.cbz").forEach { name ->
            writeZipArchive(File(root, name), mapOf("001.jpg" to sampleJpegHeader()))
        }

        val limited = scanLibraryArchivesInDirectory(root, maxCandidates = 1)

        assertEquals(1, limited.candidates.size)
        assertTrue(limited.candidateLimitReached)
        assertThrows(CancellationException::class.java) {
            scanLibraryArchivesInDirectory(root, shouldContinue = { false })
        }
    }

    @Test
    fun scanLibraryDocumentArchiveTree_recursesAndSkipsHiddenAndTopLevelAndroid() = runBlocking {
        val root = archiveDocumentNode("root", "Primary", isDirectory = true)
        val android = archiveDocumentNode("android", "Android", isDirectory = true)
        val comics = archiveDocumentNode("comics", "Comics", isDirectory = true)
        val hidden = archiveDocumentNode("hidden", ".hidden", isDirectory = true)
        val nestedAndroid = archiveDocumentNode("nested-android", "Android", isDirectory = true)
        val visibleArchive = archiveDocumentNode("visible", "Visible.cbz", isFile = true)
        val nestedArchive = archiveDocumentNode("nested", "Nested.zip", isFile = true)
        val skippedArchive = archiveDocumentNode("skipped", "Skipped.cbz", isFile = true)
        val children = mapOf(
            root.uri to listOf(android, hidden, comics),
            android.uri to listOf(skippedArchive),
            hidden.uri to listOf(skippedArchive),
            comics.uri to listOf(visibleArchive, nestedAndroid),
            nestedAndroid.uri to listOf(nestedArchive),
        )

        val result = scanLibraryDocumentArchiveTree(
            root = root,
            includeImageFolders = false,
            listChildren = { node -> children[node.uri].orEmpty() },
            archiveContainsSupportedImage = { node -> node.uri != skippedArchive.uri },
        )

        assertEquals(listOf("nested", "visible"), result.candidates.map { candidate -> candidate.uri }.sorted())
        assertEquals(3, result.scannedDirectoryCount)
        assertFalse(result.directoryLimitReached)
        assertFalse(result.candidateLimitReached)
    }

    @Test
    fun scanLibraryDocumentArchiveTree_reportsDirectoryAndCandidateLimits() = runBlocking {
        val root = archiveDocumentNode("root", "Primary", isDirectory = true)
        val folder = archiveDocumentNode("folder", "Comics", isDirectory = true)
        val one = archiveDocumentNode("one", "One.cbz", isFile = true)
        val two = archiveDocumentNode("two", "Two.cbz", isFile = true)

        val directoryLimited = scanLibraryDocumentArchiveTree(
            root = root,
            includeImageFolders = false,
            listChildren = { node -> if (node.uri == root.uri) listOf(folder) else emptyList() },
            archiveContainsSupportedImage = { true },
            maxDirectories = 1,
        )
        val candidateLimited = scanLibraryDocumentArchiveTree(
            root = root,
            includeImageFolders = false,
            listChildren = { node -> if (node.uri == root.uri) listOf(one, two) else emptyList() },
            archiveContainsSupportedImage = { true },
            maxCandidates = 1,
        )

        assertTrue(directoryLimited.directoryLimitReached)
        assertEquals(1, candidateLimited.candidates.size)
        assertTrue(candidateLimited.candidateLimitReached)
    }

    @Test
    fun scanLibraryDocumentArchiveTree_preservesCancellationFromDirectoryEnumeration() {
        val root = archiveDocumentNode("root", "Primary", isDirectory = true)

        assertThrows(CancellationException::class.java) {
            runBlocking {
                scanLibraryDocumentArchiveTree(
                    root = root,
                    includeImageFolders = false,
                    listChildren = { throw CancellationException() },
                    archiveContainsSupportedImage = { true },
                )
            }
        }
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
    fun newLibraryComicsForDocumentArchiveScan_skipsExistingAndRespectsLibraryLimit() {
        val existing = sampleLibraryComic(
            path = "content://provider/existing",
            sourceType = RecentComicSourceType.DocumentArchive,
        )
        val candidates = listOf(
            LibraryDocumentArchiveScanCandidate("content://provider/existing", "Existing.cbz"),
            LibraryDocumentArchiveScanCandidate("content://provider/new", "New.cbz"),
            LibraryDocumentArchiveScanCandidate("content://provider/extra", "Extra.cbz"),
        )

        val result = newLibraryComicsForDocumentArchiveScan(
            libraryComics = listOf(existing),
            candidates = candidates,
            addedAt = 100L,
            maxItems = 2,
        )

        assertEquals(listOf("content://provider/new"), result.map { comic -> comic.path })
        assertEquals(RecentComicSourceType.DocumentArchive, result.single().sourceType)
        assertEquals(100L, result.single().addedAt)
    }

    @Test
    fun shouldRunAutomaticLibraryScan_enforcesIntervalAndHandlesClockEdges() {
        val day = 24L * 60L * 60L * 1_000L

        assertFalse(shouldRunAutomaticLibraryScan(false, 0L, day))
        assertFalse(shouldRunAutomaticLibraryScan(true, 100L, 100L + day - 1L))
        assertTrue(shouldRunAutomaticLibraryScan(true, 100L, 100L + day))
        assertFalse(shouldRunAutomaticLibraryScan(true, 1_000L, 999L))
        assertFalse(
            shouldRunAutomaticLibraryScan(
                enabled = true,
                lastScanAt = Long.MAX_VALUE - 1L,
                nowMs = Long.MAX_VALUE,
                intervalMs = 10L,
            ),
        )
    }

    @Test
    fun scanLibraryDocumentArchiveTree_findsDirectImageComicFolders() = runBlocking {
        val root = archiveDocumentNode("root", "Primary", isDirectory = true)
        val folder = archiveDocumentNode("folder", "Batman", isDirectory = true)
        val image = archiveDocumentNode("image", "001.jpg", isFile = true)
        val archive = archiveDocumentNode("archive", "Annual.cbz", isFile = true)
        val children = mapOf(
            root.uri to listOf(folder),
            folder.uri to listOf(image, archive),
        )

        val result = scanLibraryDocumentArchiveTree(
            root = root,
            includeImageFolders = true,
            listChildren = { node -> children[node.uri].orEmpty() },
            archiveContainsSupportedImage = { true },
        )

        assertEquals(listOf("archive"), result.candidates.map { it.uri })
        assertEquals(listOf("folder"), result.folderCandidates.map { it.uri })
    }

    @Test
    fun scanLibraryDocumentArchiveTree_skipsContentChecksForKnownArchivesAndFolders() = runBlocking {
        val root = archiveDocumentNode("root", "Primary", isDirectory = true)
        val knownFolder = archiveDocumentNode("known-folder", "Batman", isDirectory = true)
        val image = archiveDocumentNode("image", "001.jpg", isFile = true)
        val knownArchive = archiveDocumentNode("known-archive", "Annual.cbz", isFile = true)
        val newArchive = archiveDocumentNode("new-archive", "Special.cbz", isFile = true)
        val children = mapOf(
            root.uri to listOf(knownFolder, knownArchive, newArchive),
            knownFolder.uri to listOf(image),
        )
        val openedArchives = mutableListOf<String>()
        val checkedImages = mutableListOf<String>()

        val result = scanLibraryDocumentArchiveTree(
            root = root,
            includeImageFolders = true,
            listChildren = { node -> children[node.uri].orEmpty() },
            archiveContainsSupportedImage = { node ->
                openedArchives += node.uri
                true
            },
            imageIsSupported = { node ->
                checkedImages += node.uri
                true
            },
            knownUris = setOf(knownFolder.uri, knownArchive.uri),
        )

        assertEquals(listOf("known-archive", "new-archive"), result.candidates.map { it.uri })
        assertEquals(listOf("known-folder"), result.folderCandidates.map { it.uri })
        assertEquals(listOf("new-archive"), openedArchives)
        assertTrue(checkedImages.isEmpty())
    }

    @Test
    fun scanLibraryDocumentArchiveTree_findsPdfsOnlyWhenAsked() = runBlocking {
        val root = archiveDocumentNode("root", "Comics", isDirectory = true)
        val pdf = archiveDocumentNode("pdf", "Annual.pdf", isFile = true)
        val archive = archiveDocumentNode("archive", "Issue 1.cbz", isFile = true)
        val children = mapOf(root.uri to listOf(pdf, archive))
        val checked = mutableListOf<String>()

        suspend fun scan(includePdfs: Boolean) = scanLibraryDocumentArchiveTree(
            root = root,
            includeImageFolders = false,
            listChildren = { node -> children[node.uri].orEmpty() },
            archiveContainsSupportedImage = { node ->
                checked += node.uri
                true
            },
            includePdfs = includePdfs,
        )

        assertEquals(listOf("archive"), scan(includePdfs = false).candidates.map { it.uri })
        assertEquals(listOf("archive"), checked)
        assertEquals(listOf("pdf", "archive"), scan(includePdfs = true).candidates.map { it.uri })
    }

    @Test
    fun scanLibraryDocumentArchiveTree_rejectsFilesThatOnlyLookLikeImages() = runBlocking {
        val root = archiveDocumentNode("root", "Primary", isDirectory = true)
        val folder = archiveDocumentNode("folder", "Broken comic", isDirectory = true)
        val fakeImage = archiveDocumentNode("image", "001.jpg", isFile = true)
        val children = mapOf(
            root.uri to listOf(folder),
            folder.uri to listOf(fakeImage),
        )

        val result = scanLibraryDocumentArchiveTree(
            root = root,
            includeImageFolders = true,
            listChildren = { node -> children[node.uri].orEmpty() },
            archiveContainsSupportedImage = { false },
            imageIsSupported = { false },
        )

        assertTrue(result.folderCandidates.isEmpty())
    }

    @Test
    fun scanLibraryDocumentArchiveTree_archiveOnlySkipsImageValidationAndPreservesArchiveBudget() = runBlocking {
        val root = archiveDocumentNode("root", "Primary", isDirectory = true)
        val folder = archiveDocumentNode("folder", "Batman", isDirectory = true)
        val image = archiveDocumentNode("image", "001.jpg", isFile = true)
        val archive = archiveDocumentNode("archive", "Annual.cbz", isFile = true)
        val children = mapOf(
            root.uri to listOf(folder),
            folder.uri to listOf(image, archive),
        )
        var imageValidationCount = 0

        val result = scanLibraryDocumentArchiveTree(
            root = root,
            includeImageFolders = false,
            listChildren = { node -> children[node.uri].orEmpty() },
            archiveContainsSupportedImage = { true },
            imageIsSupported = {
                imageValidationCount += 1
                true
            },
            maxCandidates = 1,
        )

        assertEquals(0, imageValidationCount)
        assertEquals(listOf("archive"), result.candidates.map { it.uri })
        assertTrue(result.folderCandidates.isEmpty())
        assertTrue(result.candidateLimitReached)
    }

    @Test
    fun scanLibraryDocumentArchiveTree_combinedModeSharesCandidateLimit() = runBlocking {
        val root = archiveDocumentNode("root", "Primary", isDirectory = true)
        val folder = archiveDocumentNode("folder", "Batman", isDirectory = true)
        val image = archiveDocumentNode("image", "001.jpg", isFile = true)
        val archive = archiveDocumentNode("archive", "Annual.cbz", isFile = true)
        val children = mapOf(
            root.uri to listOf(folder),
            folder.uri to listOf(image, archive),
        )

        val result = scanLibraryDocumentArchiveTree(
            root = root,
            includeImageFolders = true,
            listChildren = { node -> children[node.uri].orEmpty() },
            archiveContainsSupportedImage = { true },
            imageIsSupported = { true },
            maxCandidates = 1,
        )

        assertTrue(result.candidates.isEmpty())
        assertEquals(listOf("folder"), result.folderCandidates.map { it.uri })
        assertTrue(result.candidateLimitReached)
    }

    @Test
    fun scanLibraryDocumentArchiveTree_combinedModePreservesImageValidationCancellation() {
        val root = archiveDocumentNode("root", "Primary", isDirectory = true)
        val image = archiveDocumentNode("image", "001.jpg", isFile = true)

        assertThrows(CancellationException::class.java) {
            runBlocking {
                scanLibraryDocumentArchiveTree(
                    root = root,
                    includeImageFolders = true,
                    listChildren = { listOf(image) },
                    archiveContainsSupportedImage = { true },
                    imageIsSupported = { throw CancellationException() },
                )
            }
        }
    }

    @Test
    fun newLibraryComicsForDocumentFolderScan_skipsExistingAndRespectsRemainingSlots() {
        val existing = sampleLibraryComic(
            path = "content://provider/tree/root/document/existing",
            sourceType = RecentComicSourceType.DocumentFolder,
        )
        val result = newLibraryComicsForDocumentFolderScan(
            libraryComics = listOf(existing),
            candidates = listOf(
                LibraryDocumentFolderScanCandidate(existing.path, "Existing"),
                LibraryDocumentFolderScanCandidate("content://provider/tree/root/document/new", "New"),
                LibraryDocumentFolderScanCandidate("content://provider/tree/root/document/extra", "Extra"),
            ),
            addedAt = 100L,
            maxItems = 2,
        )

        assertEquals(listOf("content://provider/tree/root/document/new"), result.map { it.path })
        assertEquals(RecentComicSourceType.DocumentFolder, result.single().sourceType)
    }
}

private fun archiveDocumentNode(
    uri: String,
    name: String,
    isDirectory: Boolean = false,
    isFile: Boolean = false,
): ArchiveDocumentNode =
    ArchiveDocumentNode(
        uri = uri,
        name = name,
        isDirectory = isDirectory,
        isFile = isFile,
        canRead = true,
    )
