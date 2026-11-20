package com.comicviewer

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ComicMetadataTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun inspectStreamComicArchive_checksCancellationWhileDrainingSkippedEntries() {
        val archive = File(temporaryFolder.root, "Cancelled metadata.cbz")
        writeZipArchive(archive, linkedMapOf(".hidden/data" to ByteArray(4096), "001.jpg" to sampleJpegHeader()))
        var checks = 0

        assertThrows(CancellationException::class.java) {
            inspectStreamComicArchive(archive.inputStream(), shouldContinue = { ++checks <= 7 })
        }
    }

    @Test
    fun inspectStreamComicArchive_closesFailedAndCancelledHeaderProbes() {
        for (failure in listOf(IOException("Provider failed"), CancellationException("Cancelled"))) {
            var closed = false
            val source = object : InputStream() {
                override fun read(): Int = throw failure
                override fun close() {
                    closed = true
                }
            }
            assertThrows(failure.javaClass) { inspectStreamComicArchive(source) }
            assertTrue(closed)
        }
    }

    @Test
    fun inspectStreamComicArchive_keepsMetadataAndClosesPdfZipAndEmptySources() {
        val archive = File(temporaryFolder.root, "Metadata.cbz")
        writeZipArchive(archive, linkedMapOf(
            "ComicInfo.xml" to "<ComicInfo><Title>One &amp; Two</Title></ComicInfo>".toByteArray(),
            "001.jpg" to sampleJpegHeader(),
        ))
        for (bytes in listOf(archive.readBytes(), "%PDF-1.7".toByteArray(), byteArrayOf())) {
            var closed = false
            val source = object : ByteArrayInputStream(bytes) {
                override fun close() {
                    closed = true
                    super.close()
                }
            }
            val facts = inspectStreamComicArchive(source)
            assertTrue(closed)
            if (bytes.contentEquals(archive.readBytes())) {
                assertEquals(1, facts?.pageCount)
                assertEquals("One & Two", facts?.metadata?.title)
            } else {
                assertNull(facts?.pageCount)
            }
        }
    }

    @Test
    fun comicLanguageDisplayName_namesCodesInTheAppLanguage() {
        assertEquals("English", comicLanguageDisplayName("en", java.util.Locale.ENGLISH))
        assertEquals("Inglese", comicLanguageDisplayName("en", java.util.Locale.ITALIAN))
        assertEquals("Portuguese (Brazil)", comicLanguageDisplayName("pt_BR", java.util.Locale.ENGLISH))
        assertEquals("Klingon-ish", comicLanguageDisplayName("Klingon-ish", java.util.Locale.ENGLISH))
        assertEquals("", comicLanguageDisplayName("  ", java.util.Locale.ENGLISH))
    }

    @Test
    fun parseComicInfoXml_readsSupportedFieldsAndNormalizesLists() {
        val metadata = parseComicInfoXml(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <ComicInfo>
                <Title>Year One</Title>
                <Series>Batman</Series>
                <Number>1</Number>
                <Volume>2</Volume>
                <Summary>Bruce Wayne returns to Gotham.</Summary>
                <Writer>Frank Miller; David Mazzucchelli; frank miller</Writer>
                <Publisher>DC Comics</Publisher>
                <Year>1987</Year>
                <LanguageISO>en</LanguageISO>
                <Genre>Crime, Graphic Novel, Crime</Genre>
                <PageCount>184</PageCount>
            </ComicInfo>
            """.trimIndent().toByteArray(),
        )

        assertEquals("Year One", metadata?.title)
        assertEquals("Batman", metadata?.series)
        assertEquals("1", metadata?.number)
        assertEquals(2, metadata?.volume)
        assertEquals(listOf("Frank Miller", "David Mazzucchelli"), metadata?.writers)
        assertEquals(listOf("Crime", "Graphic Novel"), metadata?.genres)
        assertEquals("DC Comics", metadata?.publisher)
        assertEquals(1987, metadata?.year)
        assertEquals("en", metadata?.language)
        assertEquals(184, metadata?.pageCount)
    }

    @Test
    fun parseComicInfoXml_rejectsMalformedOversizedAndDoctypeDocuments() {
        assertNull(parseComicInfoXml("<ComicInfo><Title>Broken".toByteArray()))
        assertNull(parseComicInfoXml(ByteArray(1024 * 1024 + 1)))
        assertNull(
            parseComicInfoXml(
                """
                <!DOCTYPE ComicInfo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <ComicInfo><Title>&xxe;</Title></ComicInfo>
                """.trimIndent().toByteArray(),
            ),
        )
    }

    @Test
    fun parseComicInfoXml_rejectsUtf16AndUtf32DoctypeDocuments() {
        val xml = """
            <!DOCTYPE ComicInfo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <ComicInfo><Title>&xxe;</Title></ComicInfo>
        """.trimIndent()

        assertNull(parseComicInfoXml(xml.toByteArray(Charsets.UTF_16LE)))
        assertNull(parseComicInfoXml(xml.toByteArray(java.nio.charset.Charset.forName("UTF-32BE"))))
    }

    @Test
    fun comicInspectionPageCounts_preferCompleteAndNewestAuthoritativeValues() {
        assertEquals(
            12,
            inspectedComicPageCount(
                sourcePageCount = 12,
                recentPageCount = 10,
                metadataPageCount = 99,
            ),
        )
        assertEquals(
            10,
            inspectedComicPageCount(
                sourcePageCount = null,
                recentPageCount = 10,
                metadataPageCount = 99,
            ),
        )
        assertEquals(
            99,
            inspectedComicPageCount(
                sourcePageCount = null,
                recentPageCount = null,
                metadataPageCount = 99,
            ),
        )

        val recent = sampleRecentComic(pageCount = 20, lastOpenedAt = 300L)
        val olderDetails = LibraryComicDetails(
            sourceType = recent.sourceType,
            path = recent.path,
            pageCount = 10,
            refreshedAt = 200L,
        )
        val newerDetails = olderDetails.copy(pageCount = 30, refreshedAt = 400L)

        assertEquals(20, resolvedComicPageCount(olderDetails, recent))
        assertEquals(30, resolvedComicPageCount(newerDetails, recent))
        assertEquals(20, resolvedComicPageCount(null, recent))
        assertEquals(30, resolvedComicPageCount(newerDetails, null))
    }

    @Test
    fun metadataInspection_doesNotPublishPartialArchivePageCounts() {
        assertEquals(5, inspectedArchivePageCount(pageCount = 5, tooManyEntries = false))
        assertNull(inspectedArchivePageCount(pageCount = 5, tooManyEntries = true))
        assertNull(
            inspectedArchivePageCount(
                pageCount = 5,
                tooManyEntries = false,
                sourceLimitReached = true,
            ),
        )

        val truncated = BoundedInputStream(ByteArrayInputStream(ByteArray(8)), maxBytes = 4)
        assertEquals(4, truncated.readBytes().size)
        assertTrue(truncated.limitReached)

        val complete = BoundedInputStream(ByteArrayInputStream(ByteArray(4)), maxBytes = 8)
        assertEquals(4, complete.readBytes().size)
        assertFalse(complete.limitReached)
    }

    @Test
    fun boundedInputStream_sourceOfExactlyTheLimitIsNotCutOff() {
        val exact = BoundedInputStream(ByteArrayInputStream(ByteArray(4)), maxBytes = 4)

        assertEquals(4, exact.readBytes().size)
        assertEquals(-1, exact.read())
        assertFalse(exact.limitReached)
    }
}
