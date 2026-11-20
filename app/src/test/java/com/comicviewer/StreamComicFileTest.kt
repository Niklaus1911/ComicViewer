package com.comicviewer

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
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
}
