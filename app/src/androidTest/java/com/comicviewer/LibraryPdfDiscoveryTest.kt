package com.comicviewer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.random.Random
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

private const val PAGE_SIZE_PT = 300

@RunWith(AndroidJUnit4::class)
class LibraryPdfDiscoveryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val testDir = File(context.cacheDir, "pdf-discovery-test")

    @After
    fun cleanUp() {
        testDir.deleteRecursively()
    }

    @Test
    fun scanLibraryArchives_findsPdfsThatLookLikeComicsOnly() {
        val comic = writePdf("Comic.pdf", pageCount = 10) { canvas -> canvas.drawPicturePage() }
        writePdf("Manual.pdf", pageCount = 10) { canvas -> canvas.drawTextPage() }
        writePdf("Poster.pdf", pageCount = 3) { canvas -> canvas.drawPicturePage() }

        val result = scanLibraryArchivesInDirectory(testDir)

        assertEquals(listOf(comic.absolutePath), result.candidates.map { candidate ->
            candidate.archiveFile.absolutePath
        })
    }

    /**
     * A page-sized picture of noise, different on every page, which compresses so little that each
     * page takes several hundred KB.
     */
    private fun Canvas.drawPicturePage() {
        val pixels = IntArray(500 * 500) { Color.rgb(Random.nextInt(256), Random.nextInt(256), Random.nextInt(256)) }
        val picture = Bitmap.createBitmap(pixels, 500, 500, Bitmap.Config.ARGB_8888)
        drawBitmap(picture, null, RectF(0f, 0f, PAGE_SIZE_PT.toFloat(), PAGE_SIZE_PT.toFloat()), null)
        picture.recycle()
    }

    private fun Canvas.drawTextPage() {
        val paint = Paint().apply { color = Color.BLACK; textSize = 10f }
        repeat(25) { line -> drawText("Chapter ${line + 1}: how to set up the device.", 20f, 20f + line * 11f, paint) }
    }

    private fun writePdf(name: String, pageCount: Int, draw: (Canvas) -> Unit): File {
        testDir.mkdirs()
        val file = File(testDir, name)
        val document = PdfDocument()
        try {
            for (pageNumber in 1..pageCount) {
                val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_SIZE_PT, PAGE_SIZE_PT, pageNumber).create())
                draw(page.canvas)
                document.finishPage(page)
            }
            file.outputStream().use(document::writeTo)
        } finally {
            document.close()
        }
        return file
    }
}
