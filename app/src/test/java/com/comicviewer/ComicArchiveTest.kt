package com.comicviewer

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ComicArchiveTest {
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
    fun copyToFileBounded_cancellationDeletesPartialFile() {
        val outputFile = File(temporaryFolder.root, "cancelled-page.bin")
        var continuationChecks = 0

        assertThrows(CancellationException::class.java) {
            copyToFileBounded(
                inputStream = ByteArrayInputStream(ByteArray(16 * 1024)),
                outputFile = outputFile,
                maxBytes = 32L * 1024L,
                shouldContinue = { continuationChecks++ == 0 },
            )
        }

        assertFalse(outputFile.exists())
    }

    @Test
    fun zipArchiveReader_readsStoredAndDeflatedEntriesInPlace() {
        val archive = File(temporaryFolder.root, "Mixed.cbz")
        val storedPage = sampleJpegHeader() + ByteArray(4_096) { (it % 251).toByte() }
        val deflatedPage = samplePngHeader() + ByteArray(64 * 1024) { (it % 7).toByte() }
        writeZipArchive(
            archive,
            linkedMapOf("pages/001.jpg" to storedPage, "pages/002.png" to deflatedPage),
            storedNames = setOf("pages/001.jpg"),
        )

        openLocalComicArchive(archive).use { opened ->
            val reader = opened.reader
            assertEquals(listOf("pages/001.jpg", "pages/002.png"), reader.entries.map { it.name })
            assertArrayEquals(storedPage, reader.openEntry(reader.entries[0]).use { it.readBytes() })
            assertArrayEquals(deflatedPage, reader.openEntry(reader.entries[1]).use { it.readBytes() })
            assertTrue(reader.entries[1].compressedSize < reader.entries[1].size)
        }
    }

    @Test
    fun zipArchiveReader_handlesPrependedDataAndArchiveComments() {
        val plain = File(temporaryFolder.root, "Plain.cbz")
        writeZipArchive(plain, linkedMapOf("001.jpg" to sampleJpegHeader()), comment = "Scanned by someone")
        val prefixed = File(temporaryFolder.root, "Prefixed.cbz")
        prefixed.writeBytes(ByteArray(1_000) { 0x42 } + plain.readBytes())

        listOf(plain, prefixed).forEach { archive ->
            openLocalComicArchive(archive).use { opened ->
                val entry = opened.reader.entries.single()
                assertArrayEquals(sampleJpegHeader(), opened.reader.openEntry(entry).use { it.readBytes() })
            }
        }
    }

    @Test
    fun zipArchiveReader_readsZip64ArchivesWithManyEntries() {
        val archive = File(temporaryFolder.root, "Omnibus.cbz")
        val entryCount = 70_000
        ZipOutputStream(archive.outputStream().buffered()).use { output ->
            repeat(entryCount) { index ->
                output.putNextEntry(ZipEntry("p/$index"))
                if (index == entryCount - 1) output.write(sampleJpegHeader())
                output.closeEntry()
            }
        }

        openLocalComicArchive(archive).use { opened ->
            assertEquals(entryCount, opened.reader.entries.size)
            val last = opened.reader.entries.last()
            assertEquals("p/${entryCount - 1}", last.name)
            assertArrayEquals(sampleJpegHeader(), opened.reader.openEntry(last).use { it.readBytes() })
        }
    }

    @Test
    fun zipArchiveReader_enforcesEntryLimitAndRejectsCorruptArchives() {
        val archive = File(temporaryFolder.root, "Limited.cbz")
        writeZipArchive(archive, linkedMapOf("001.jpg" to sampleJpegHeader(), "002.jpg" to sampleJpegHeader()))
        val limitFailure = assertThrows(ZipEntryLimitExceededException::class.java) {
            openLocalComicArchive(archive, maxEntries = 1)
        }
        assertEquals(1, limitFailure.limit)
        assertEquals(
            ComicLoadError.ArchiveTooLarge,
            (archiveReadFailureFor(limitFailure) as ComicLoadException).error,
        )

        val notZip = File(temporaryFolder.root, "Not a zip.cbz").apply { writeBytes(ByteArray(4_096) { 7 }) }
        assertThrows(ZipException::class.java) { openLocalComicArchive(notZip) }
        val truncated = File(temporaryFolder.root, "Truncated.cbz").apply {
            writeBytes(archive.readBytes().copyOf(archive.length().toInt() - 10))
        }
        assertThrows(ZipException::class.java) { openLocalComicArchive(truncated) }
    }

    @Test
    fun scanComicPages_listsValidImagesNaturallyWithoutExtracting() {
        val archive = File(temporaryFolder.root, "Scan.cbz")
        writeZipArchive(
            archive,
            linkedMapOf(
                "Chapter 10/page 1.jpg" to sampleJpegHeader(),
                "Chapter 2/page 1.png" to samplePngHeader(),
                "Chapter 2/notes.txt" to "not an image".encodeToByteArray(),
                "ComicInfo.xml" to "<ComicInfo/>".encodeToByteArray(),
                "__MACOSX/Chapter 2/._page 1.png" to samplePngHeader(),
                "Chapter 2/" to ByteArray(0),
                "Chapter 2/no extension" to sampleJpegHeader(),
            ),
        )

        val scan = openLocalComicArchive(archive).use { opened -> opened.reader.scanComicPages() }

        assertEquals(
            listOf("Chapter 2/no extension", "Chapter 2/page 1.png", "Chapter 10/page 1.jpg"),
            scan.pages.map { it.name },
        )
        assertEquals("ComicInfo.xml", scan.comicInfoEntry?.name)
        assertEquals(listOf("notes.txt"), scan.rejectedExamples)
        assertEquals(5, scan.scannedEntries)
    }

    @Test
    fun scanComicPages_skipsOversizedPagesAndReportsEmptyArchives() {
        val archive = File(temporaryFolder.root, "Oversized.cbz")
        writeZipArchive(
            archive,
            linkedMapOf(
                "001.jpg" to sampleJpegHeader() + ByteArray(100),
                "002.jpg" to sampleJpegHeader(),
            ),
        )
        val withOversizedPage = openLocalComicArchive(archive).use { opened ->
            opened.reader.scanComicPages(maxPageBytes = 10L)
        }
        assertEquals(listOf("002.jpg"), withOversizedPage.pages.map { it.name })
        assertEquals(listOf("001.jpg"), withOversizedPage.rejectedExamples)

        val empty = File(temporaryFolder.root, "Empty.cbz")
        writeZipArchive(empty, linkedMapOf("readme.txt" to "hello".encodeToByteArray()))
        val scan = openLocalComicArchive(empty).use { opened -> opened.reader.scanComicPages() }
        assertTrue(scan.pages.isEmpty())
        assertEquals(ComicLoadError.NoValidImagesFoundWithExamples, scan.emptyException().error)
    }

    @Test
    fun firstComicPageEntry_picksFirstValidImageInReadingOrder() {
        val archive = File(temporaryFolder.root, "Cover.cbz")
        writeZipArchive(
            archive,
            linkedMapOf(
                "010.jpg" to sampleJpegHeader(),
                "001.jpg" to "corrupt".encodeToByteArray(),
                "002.jpg" to sampleJpegHeader() + ByteArray(1_000),
                "003.jpg" to sampleJpegHeader(),
            ),
        )

        openLocalComicArchive(archive).use { opened ->
            assertEquals("002.jpg", opened.reader.firstComicPageEntry(maxPageBytes = 2_000L)?.name)
            assertEquals("003.jpg", opened.reader.firstComicPageEntry(maxPageBytes = 100L)?.name)
            assertThrows(CancellationException::class.java) {
                opened.reader.firstComicPageEntry(maxPageBytes = 100L, shouldContinue = { false })
            }
        }
    }

    @Test
    fun archiveReadFailureFor_preservesCancellationException() {
        val cancellationException = CancellationException("Cancelled")

        assertSame(cancellationException, archiveReadFailureFor(cancellationException))
    }

    @Test
    fun archiveReadFailureFor_mapsStorageFailuresToReadableMessage() {
        val exception = archiveReadFailureFor(IOException("Disk full"))

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
            sortedComicPaths(pages),
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
            sortedComicPaths(pages),
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
    fun firstComicPages_matchesTheStartOfTheReaderPageOrder() {
        val archive = File(temporaryFolder.root, "Preview order.cbz")
        writeZipArchive(
            archive,
            linkedMapOf(
                "010.jpg" to sampleJpegHeader(),
                "002.jpg" to sampleJpegHeader(),
                "001.jpg" to "corrupt".encodeToByteArray(),
                "ComicInfo.xml" to "<ComicInfo/>".encodeToByteArray(),
                "003.png" to samplePngHeader(),
                "__MACOSX/._000.jpg" to sampleJpegHeader(),
            ),
        )

        openLocalComicArchive(archive).use { opened ->
            val readerOrder = opened.reader.scanComicPages().pages.map { it.name }
            assertEquals(listOf("002.jpg", "003.png", "010.jpg"), readerOrder)
            assertEquals(readerOrder.take(2), opened.reader.firstComicPages(2).map { it.name })
            assertEquals(readerOrder, opened.reader.firstComicPages(10).map { it.name })
            assertTrue(opened.reader.firstComicPages(0).isEmpty())
        }
    }
}

private fun samplePngHeader(): ByteArray =
    byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D)

private fun sortedComicPaths(paths: List<String>): List<String> = paths.sortedWith(::compareComicPageNames)
