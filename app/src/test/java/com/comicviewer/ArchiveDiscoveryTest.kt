package com.comicviewer

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ArchiveDiscoveryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun containsComicImage_rejectsPagesOverReaderSizeLimit() {
        val archive = File(temporaryFolder.root, "Oversized.cbz")
        writeZipArchive(archive, mapOf("001.jpg" to sampleJpegHeader()))
        val bytes = archive.readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val endOffset = findEndOfCentralDirectory(bytes)!!
        val centralOffset = buffer.getInt(endOffset + 16)
        buffer.putInt(centralOffset + 24, (MAX_ARCHIVE_PAGE_BYTES + 1).toInt())
        archive.writeBytes(bytes)

        openLocalComicArchive(archive).use { opened ->
            assertTrue(opened.reader.scanComicPages().pages.isEmpty())
            assertFalse(opened.reader.containsComicImage(maxVisitedEntries = 10))
        }
        assertFalse(archiveContainsSupportedImage(archive))
    }

    @Test
    fun containsComicImage_excludesMetadataAndMatchesReadablePages() {
        for ((name, expected) in listOf("ComicInfo.xml" to false, "001.jpg" to true, ".hidden/001.jpg" to false)) {
            val archive = File(temporaryFolder.root, "Candidate.cbz")
            writeZipArchive(archive, mapOf(name to sampleJpegHeader()))

            openLocalComicArchive(archive).use { opened ->
                assertTrue(opened.reader.containsComicImage(10) == expected)
                assertTrue(opened.reader.scanComicPages().pages.isNotEmpty() == expected)
                assertFalse(opened.reader.containsComicImage(0))
            }
        }
        val empty = File(temporaryFolder.root, "Empty.cbz")
        writeZipArchive(empty, emptyMap())
        assertFalse(archiveContainsSupportedImage(empty))
    }
}
