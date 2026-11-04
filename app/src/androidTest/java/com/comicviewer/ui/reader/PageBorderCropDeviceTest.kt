package com.comicviewer.ui.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Precision
import coil3.size.Size
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The crop on real bitmaps and through Coil, which the plain pixel tests in ReaderPageCropTest can't reach. */
@RunWith(AndroidJUnit4::class)
class PageBorderCropDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val pageFile = File(context.cacheDir, "crop-test-page.png")

    @After
    fun tearDown() {
        pageFile.delete()
    }

    @Test
    fun transform_trimsBordersOnEverySideOfABitmap() = runBlocking {
        // Art inset 20 px left and right and 30 px top and bottom, with thin scanner edges on the
        // right and bottom, which the bitmap reader reads from the far ends of its rows and columns.
        val page = borderedPage(width = 200, height = 300, artLeft = 20, artTop = 30, scannerEdge = 3)

        val cropped = PageBorderCropTransformation.transform(page, Size(200, 300))

        // Each side keeps a 0.5% margin, rounded up: 1 px across, 2 px down.
        assertEquals(162, cropped.width)
        assertEquals(244, cropped.height)
    }

    @Test
    fun croppedPage_isServedFromMemoryCacheWhenRequestedAgain() = runBlocking {
        // A large page decoded smaller is "sampled": Coil reuses such a cached copy only when told
        // the size it was made for, which readerPageSize does through Coil's own key extra. If a Coil
        // update renames that extra, cropped pages are decoded on every turn and this test fails.
        borderedPage(width = 1200, height = 1800, artLeft = 120, artTop = 180, scannerEdge = 0)
            .compress(Bitmap.CompressFormat.PNG, 100, pageFile.outputStream())
        val imageLoader = ImageLoader.Builder(context)
            .memoryCache { MemoryCache.Builder().maxSizeBytes(64L * 1024 * 1024).build() }
            .diskCache(null)
            .build()
        fun request() = ImageRequest.Builder(context)
            .data(pageFile)
            .memoryCacheKey("crop-test-page")
            .cropPageBorders(true)
            .readerPageSize(Size(400, 600), autoCrop = true)
            .precision(Precision.INEXACT)
            .build()

        val first = imageLoader.execute(request()) as SuccessResult
        val second = imageLoader.execute(request()) as SuccessResult

        // Decoded to fit 500 × 750 (the request plus headroom), then about a tenth trimmed from each side.
        assertTrue("the page should be cropped, was ${first.image.width} wide", first.image.width < 450)
        assertEquals(DataSource.MEMORY_CACHE, second.dataSource)
        imageLoader.shutdown()
    }

    private fun borderedPage(width: Int, height: Int, artLeft: Int, artTop: Int, scannerEdge: Int): Bitmap {
        val page = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(page)
        canvas.drawColor(Color.WHITE)
        val red = Paint().apply { color = Color.rgb(0xCC, 0x22, 0x22) }
        val blue = Paint().apply { color = Color.rgb(0x22, 0x44, 0xCC) }
        val cell = 4
        var y = artTop
        while (y < height - artTop) {
            var x = artLeft
            while (x < width - artLeft) {
                val paint = if ((x / cell + y / cell) % 2 == 0) red else blue
                canvas.drawRect(
                    x.toFloat(),
                    y.toFloat(),
                    minOf(x + cell, width - artLeft).toFloat(),
                    minOf(y + cell, height - artTop).toFloat(),
                    paint,
                )
                x += cell
            }
            y += cell
        }
        if (scannerEdge > 0) {
            val black = Paint().apply { color = Color.BLACK }
            canvas.drawRect((width - scannerEdge).toFloat(), 0f, width.toFloat(), height.toFloat(), black)
            canvas.drawRect(0f, (height - scannerEdge).toFloat(), width.toFloat(), height.toFloat(), black)
        }
        return page
    }
}
