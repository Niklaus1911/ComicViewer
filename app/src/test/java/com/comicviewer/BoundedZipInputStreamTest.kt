package com.comicviewer

import java.io.File
import java.util.zip.ZipException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BoundedZipInputStreamTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun boundedZipInputStream_readsStoredDeflatedAndEmptyEntriesAtLimit() {
        val archive = File(temporaryFolder.root, "At limit.cbz")
        val page = ByteArray(32) { it.toByte() }
        writeZipArchive(archive, linkedMapOf("empty" to byteArrayOf(), "stored" to page, "deflated" to page), setOf("stored"))

        BoundedZipInputStream(archive.inputStream(), maxEntryBytes = 32).use { input ->
            assertEquals("empty", input.nextEntry?.name)
            assertArrayEquals(byteArrayOf(), input.readBytes())
            assertEquals("stored", input.nextEntry?.name)
            assertArrayEquals(page, input.readBytes())
            assertEquals("deflated", input.nextEntry?.name)
            assertArrayEquals(page, input.readBytes())
            assertNull(input.nextEntry)
        }
    }

    @Test
    fun boundedZipInputStream_limitsInflationWhenNextEntryDrainsSkippedData() {
        val archive = File(temporaryFolder.root, "Skipped.cbz")
        writeZipArchive(archive, linkedMapOf(".hidden/data" to ByteArray(4096), "001.jpg" to sampleJpegHeader()))

        BoundedZipInputStream(archive.inputStream(), maxEntryBytes = 32).use { input ->
            assertEquals(".hidden/data", input.nextEntry?.name)
            assertThrows(ZipException::class.java) { input.nextEntry }
        }
    }

    @Test
    fun boundedZipInputStream_limitsUnknownSizesButAllowsKnownOversizedEntries() {
        val archive = File(temporaryFolder.root, "Oversized.cbz")
        writeZipArchive(archive, linkedMapOf("deflated" to ByteArray(33), "stored" to ByteArray(33)), setOf("stored"))

        BoundedZipInputStream(archive.inputStream(), maxEntryBytes = 32).use { input ->
            input.nextEntry
            assertThrows(ZipException::class.java) { input.readBytes() }
        }
        BoundedZipInputStream(archive.inputStream(), maxEntryBytes = 33).use { input ->
            input.nextEntry
            input.closeEntry()
            assertEquals("stored", input.nextEntry?.name)
        }
        val stored = File(temporaryFolder.root, "Stored.cbz")
        writeZipArchive(stored, mapOf("stored" to ByteArray(33)), setOf("stored"))
        BoundedZipInputStream(stored.inputStream(), maxEntryBytes = 32).use { input ->
            assertEquals("stored", input.nextEntry?.name)
            assertArrayEquals(ByteArray(33), input.readBytes())
            assertNull(input.nextEntry)
        }
    }

    @Test
    fun boundedZipInputStream_checksCancellationWhileDrainingAnEntry() {
        val archive = File(temporaryFolder.root, "Cancelled.cbz")
        writeZipArchive(archive, mapOf("data" to ByteArray(4096)))
        var checks = 0

        BoundedZipInputStream(archive.inputStream(), maxEntryBytes = 4096, shouldContinue = { ++checks <= 3 }).use { input ->
            input.nextEntry
            assertThrows(CancellationException::class.java) { input.nextEntry }
        }
    }
}
