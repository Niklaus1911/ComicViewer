package com.comicviewer

import android.content.ContentValues
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComicPdfTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val testDir = File(context.cacheDir, "pdf-comic-test")
    private val createdUris = mutableListOf<Uri>()

    @After
    fun cleanUp() {
        runBlocking { closeComicArchives() }
        createdUris.forEach { uri -> context.contentResolver.delete(uri, null, null) }
        testDir.deleteRecursively()
    }

    @Test
    fun loadLocalArchiveComic_listsPdfPagesAndRendersThemAtTheRequestedSize() = runBlocking {
        val pdf = writeSamplePdf("Sample Comic.pdf")

        val book = loadLocalArchiveComic(context, pdf)

        assertEquals("Sample Comic", book.title)
        assertEquals(3, book.pages.size)
        assertTrue(book.pages.all { it.imageUri.scheme == COMIC_PDF_PAGE_SCHEME })

        val result = SingletonImageLoader.get(context).execute(
            ImageRequest.Builder(context)
                .data(book.pages[0].imageUri)
                .size(540, 1080)
                .memoryCacheKey("pdf-test-page-0")
                .build(),
        )
        val bitmap = (result as SuccessResult).image.toBitmap()
        assertEquals(540, bitmap.width)
        assertEquals(699, bitmap.height)
        // The page is drawn red on a white page background.
        assertEquals(Color.RED, bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
        assertEquals(Color.WHITE, bitmap.getPixel(1, 1))
    }

    @Test
    fun loadArchiveComic_readsPickedPdfInPlace() = runBlocking {
        val uri = insertDownload("pdf-read-test-${System.nanoTime()}.pdf", writeSamplePdf("Picked.pdf"))

        val book = loadArchiveComic(context, uri)

        assertEquals(3, book.pages.size)
        assertTrue(book.pages.all { it.imageUri.scheme == COMIC_PDF_PAGE_SCHEME })
        assertFalse(File(context.cacheDir, "archive_copies").exists())
        val result = SingletonImageLoader.get(context).execute(
            ImageRequest.Builder(context)
                .data(book.pages[1].imageUri)
                .size(792, 792)
                .memoryCacheKey("pdf-test-document-page-1")
                .build(),
        )
        // The landscape page fits the square box by its width.
        assertEquals(792, (result as SuccessResult).image.width)
        assertEquals(612, result.image.height)
    }

    @Test
    fun pdfComics_getThumbnailsAndPreviews() = runBlocking {
        val pdf = writeSamplePdf("Thumbnail.pdf")

        val thumbnailUri = loadLibraryComicThumbnailUri(context, libraryComicForArchive(pdf, addedAt = 0L))
        val previews = loadComicPagePreviews(context, RecentComicSourceType.LocalArchive, pdf.absolutePath, maxPages = 2)

        assertNotNull(thumbnailUri)
        val thumbnail = File(thumbnailUri!!.path!!)
        assertTrue(thumbnail.isFile)
        thumbnail.delete()
        assertEquals(2, previews.size)
    }

    @Test
    fun comicFiles_areReadByContentNotByName() = runBlocking {
        // A PDF saved with an archive extension still opens, gets a cover and counts as a comic.
        val disguised = File(testDir, "Disguised.cbz").also { writeSamplePdf("Source.pdf").copyTo(it) }

        val book = loadLocalArchiveComic(context, disguised)
        val thumbnailUri = loadLibraryComicThumbnailUri(context, libraryComicForArchive(disguised, addedAt = 0L))

        assertEquals(3, book.pages.size)
        assertTrue(book.pages.all { it.imageUri.scheme == COMIC_PDF_PAGE_SCHEME })
        assertNotNull(thumbnailUri)
        File(thumbnailUri!!.path!!).delete()
        assertTrue(archiveContainsSupportedImage(disguised))
    }

    @Test
    fun loadLocalArchiveComic_rejectsCorruptPdf() = runBlocking {
        testDir.mkdirs()
        val corrupt = File(testDir, "Broken.pdf").apply { writeText("%PDF-1.4\nthis is not a real document") }

        try {
            loadLocalArchiveComic(context, corrupt)
            fail("A corrupt PDF must not load")
        } catch (exception: ComicLoadException) {
            assertEquals(ComicLoadError.InvalidArchive, exception.error)
        }
    }

    private fun insertDownload(displayName: String, source: File): Uri {
        val resolver = context.contentResolver
        val uri = requireNotNull(
            resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                },
            ),
        )
        createdUris += uri
        requireNotNull(resolver.openOutputStream(uri)).use { output -> source.inputStream().use { it.copyTo(output) } }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        return uri
    }

    /** Three pages: a red portrait page, a landscape page and a small page. */
    private fun writeSamplePdf(name: String): File {
        testDir.mkdirs()
        val file = File(testDir, name)
        val document = PdfDocument()
        try {
            listOf(612 to 792, 792 to 612, 200 to 300).forEachIndexed { index, (width, height) ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(width, height, index + 1).create())
                val paint = Paint().apply { color = if (index == 0) Color.RED else Color.BLUE }
                page.canvas.drawRect(width * 0.25f, height * 0.25f, width * 0.75f, height * 0.75f, paint)
                document.finishPage(page)
            }
            file.outputStream().use(document::writeTo)
        } finally {
            document.close()
        }
        return file
    }
}
