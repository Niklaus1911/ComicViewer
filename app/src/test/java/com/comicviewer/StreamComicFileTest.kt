package com.comicviewer

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StreamComicFileTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun streamComicFileHasPages_closesPdfZipAndEmptySources() {
        val archive = File(temporaryFolder.root, "Comic.cbz")
        writeZipArchive(archive, mapOf("001.jpg" to sampleJpegHeader()))
        for (bytes in listOf("%PDF-1.7".toByteArray(), archive.readBytes(), byteArrayOf())) {
            var closed = false
            val input = object : ByteArrayInputStream(bytes) {
                override fun close() {
                    closed = true
                    super.close()
                }
            }

            assertTrue(streamComicFileHasPages(input, maxEntries = 10) == bytes.isNotEmpty())
            assertTrue(closed)
        }
    }

    @Test
    fun streamComicFileHasPages_closesSourceWhenHeaderReadFailsOrIsCancelled() {
        for (failure in listOf(IOException("Provider failed"), CancellationException("Cancelled"))) {
            var closed = false
            val input = object : InputStream() {
                override fun read(): Int = throw failure

                override fun close() {
                    closed = true
                }
            }

            assertThrows(failure.javaClass) { streamComicFileHasPages(input, maxEntries = 10) }
            assertTrue(closed)
        }
    }

    @Test
    fun streamComicFileHasPages_keepsEntryLimitAndHiddenPathFiltering() {
        val archive = File(temporaryFolder.root, "Hidden.cbz")
        writeZipArchive(archive, linkedMapOf(".hidden/001.jpg" to sampleJpegHeader(), "002.jpg" to sampleJpegHeader()))

        assertFalse(streamComicFileHasPages(archive.inputStream(), maxEntries = 1))
        assertTrue(streamComicFileHasPages(archive.inputStream(), maxEntries = 2))
    }

    @Test
    fun streamComicFileHasPages_skipsLargeDeclaredNonPageBeforeValidPage() {
        val archive = File(temporaryFolder.root, "Large attachment.cbz")
        writeZipArchive(archive, linkedMapOf("notes.txt" to ByteArray(64), "001.jpg" to sampleJpegHeader()), setOf("notes.txt"))

        assertTrue(streamComicFileHasPages(archive.inputStream(), maxEntries = 10, maxEntryBytes = 32))
    }

    @Test
    fun streamComicFileHasPages_skipsLargeDeclaredPageBeforeValidPage() {
        val archive = File(temporaryFolder.root, "Large page.cbz")
        val largePage = sampleJpegHeader() + ByteArray(64)
        writeZipArchive(archive, mapOf("001.jpg" to largePage), setOf("001.jpg"))
        assertFalse(streamComicFileHasPages(archive.inputStream(), maxEntries = 10, maxEntryBytes = 32))

        writeZipArchive(archive, linkedMapOf("001.jpg" to largePage, "002.jpg" to sampleJpegHeader()), setOf("001.jpg"))
        assertTrue(streamComicFileHasPages(archive.inputStream(), maxEntries = 10, maxEntryBytes = 32))
    }

    @Test
    fun streamComicFileHasPages_rejectsInflationBeyondDeclaredSize() {
        val archive = File(temporaryFolder.root, "Corrupt size.cbz")
        writeZipArchive(archive, linkedMapOf("notes.txt" to ByteArray(64), "001.jpg" to sampleJpegHeader()))
        val bytes = archive.readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val centralOffset = buffer.getInt(findEndOfCentralDirectory(bytes)!! + 16)
        buffer.putShort(6, (buffer.getShort(6).toInt() and 8.inv()).toShort())
        buffer.putInt(14, buffer.getInt(centralOffset + 16))
        buffer.putInt(18, buffer.getInt(centralOffset + 20))
        buffer.putInt(22, 32)
        archive.writeBytes(bytes)

        assertThrows(ZipException::class.java) {
            streamComicFileHasPages(archive.inputStream(), maxEntries = 10)
        }
    }
}
