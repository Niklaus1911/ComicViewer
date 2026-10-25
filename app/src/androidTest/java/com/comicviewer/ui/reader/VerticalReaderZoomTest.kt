package com.comicviewer.ui.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.comicviewer.ComicBook
import com.comicviewer.R
import com.comicviewer.ReaderPageMode
import com.comicviewer.ReaderPreferences
import com.comicviewer.closeComicArchives
import com.comicviewer.loadLocalArchiveComic
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PAGE_LOAD_TIMEOUT_MS = 5_000L
private const val SHARP_PAGE_TIMEOUT_MS = 5_000L
private const val DOUBLE_TAP_SETTLE_MS = 1_000L

/** Width and height of the square test pages, in PDF points. */
private const val PAGE_SIZE_PT = 300f

@RunWith(AndroidJUnit4::class)
class VerticalReaderZoomTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val testDir = File(context.cacheDir, "vertical-reader-zoom-test")

    @After
    fun cleanUp() {
        runBlocking { closeComicArchives() }
        testDir.deleteRecursively()
    }

    @Test
    fun verticalReader_pinchZoomsAroundTheFingers() {
        showVerticalReader(writeTargetPdf(), pageShown = ::targetIsShown)
        val root = composeRule.onRoot()
        val target = targetCenterOnScreen()

        // Two fingers spread from 60 to 150 pixels around the red square: about 2.5x.
        root.performTouchInput {
            down(0, target - Offset(60f, 0f))
            down(1, target + Offset(60f, 0f))
            repeat(15) { step ->
                val spread = 60f + 6f * (step + 1)
                moveTo(0, target - Offset(spread, 0f))
                moveTo(1, target + Offset(spread, 0f))
            }
            up(0)
            up(1)
        }
        composeRule.waitForIdle()

        // The square stays under the fingers, and has grown past a point that was white before.
        val screen = root.captureToImage().asAndroidBitmap()
        assertEquals(Color.RED, screen.getPixel(target.x.toInt(), target.y.toInt()))
        assertEquals(Color.RED, screen.getPixel((target.x + targetHalfSizePx() * 1.5f).toInt(), target.y.toInt()))
    }

    @Test
    fun verticalReader_doubleTapDoesNotZoom() {
        showVerticalReader(writeTargetPdf(), pageShown = ::targetIsShown)
        val root = composeRule.onRoot()
        val target = targetCenterOnScreen()
        val outside = target + Offset(targetHalfSizePx() * 1.5f, 0f)

        root.performTouchInput { doubleClick(target) }
        composeRule.mainClock.advanceTimeBy(DOUBLE_TAP_SETTLE_MS)
        composeRule.waitForIdle()

        val screen = root.captureToImage().asAndroidBitmap()
        assertEquals(Color.RED, screen.getPixel(target.x.toInt(), target.y.toInt()))
        assertEquals(Color.WHITE, screen.getPixel(outside.x.toInt(), outside.y.toInt()))
    }

    @Test
    fun verticalReader_zoomedPagesLoadInSharperDetail() {
        // The page is square and fills the width, so its center is half a width down.
        showVerticalReader(writeStripesPdf()) { screen ->
            screen.blackShareOfRow(screen.width / 2) > 0.2f
        }
        val root = composeRule.onRoot()
        val pageCenter = Offset(rootWidth() / 2f, rootWidth() / 2f)

        // Spread from 60 to 180 pixels: 3x, the most the strip zooms.
        root.performTouchInput {
            down(0, pageCenter - Offset(60f, 0f))
            down(1, pageCenter + Offset(60f, 0f))
            repeat(20) { step ->
                val spread = 60f + 6f * (step + 1)
                moveTo(0, pageCenter - Offset(spread, 0f))
                moveTo(1, pageCenter + Offset(spread, 0f))
            }
            up(0)
            up(1)
        }

        // A page loaded for 1x and enlarged three times blurs each stripe edge over several pixels.
        // Loaded for 3x, the edges stay hard: black next to white.
        var hardEdgeShare = 0f
        val sharp = runCatching {
            composeRule.waitUntil(SHARP_PAGE_TIMEOUT_MS) {
                hardEdgeShare = root.captureToImage().asAndroidBitmap().hardEdgeShareOfRow(pageCenter.y.toInt())
                hardEdgeShare > 0.2f
            }
        }.isSuccess
        assertTrue("Only ${"%.2f".format(hardEdgeShare)} of the zoomed stripe edges were hard", sharp)
    }

    @Test
    fun verticalReader_pinchThatMovesTheStripUpdatesTheCurrentPage() {
        val selectedPages = mutableListOf<Int>()
        showVerticalReader(writeTargetPdf(pageCount = 4), onPageSelected = { selectedPages += it }, pageShown = ::targetIsShown)
        // The reader opens with page 1 at the top and has reported nothing yet. Zooming 2x around a
        // point on page 2 keeps that point in place and puts page 2 in the middle of the screen.
        val root = composeRule.onRoot()
        val focus = Offset(rootWidth() / 2f, rootWidth() * 1.6f)
        root.performTouchInput {
            down(0, focus - Offset(60f, 0f))
            down(1, focus + Offset(60f, 0f))
            repeat(10) { step ->
                val spread = 60f + 6f * (step + 1)
                moveTo(0, focus - Offset(spread, 0f))
                moveTo(1, focus + Offset(spread, 0f))
            }
            up(0)
            up(1)
        }
        composeRule.waitForIdle()

        assertEquals(1, selectedPages.lastOrNull())
    }

    private fun showVerticalReader(
        file: File,
        onPageSelected: (Int) -> Unit = {},
        pageShown: (Bitmap) -> Boolean,
    ) {
        val book: ComicBook = runBlocking { loadLocalArchiveComic(context, file) }
        composeRule.setContent {
            MaterialTheme {
                VerticalComicReader(
                    book = book,
                    pageIndex = 0,
                    preferences = ReaderPreferences(pageMode = ReaderPageMode.Vertical, showPageNumber = false),
                    controlsVisible = false,
                    onControlsVisibleChange = {},
                    onPageSelected = onPageSelected,
                    onCloseComic = {},
                    onOpenSettings = {},
                    onOpenGrid = {},
                    favorite = null,
                    onToggleFavorite = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        val pageLabel = context.getString(R.string.reader_page_description, 1, book.pages.size)
        composeRule.waitUntil(PAGE_LOAD_TIMEOUT_MS) {
            composeRule.onAllNodesWithContentDescription(pageLabel).fetchSemanticsNodes().isNotEmpty() &&
                pageShown(composeRule.onRoot().captureToImage().asAndroidBitmap())
        }
        composeRule.waitForIdle()
    }

    private fun targetIsShown(screen: Bitmap): Boolean {
        val target = targetCenterOnScreen()
        return screen.getPixel(target.x.toInt(), target.y.toInt()) == Color.RED
    }

    private fun rootWidth(): Float = composeRule.onRoot().fetchSemanticsNode().size.width.toFloat()

    /** The page fills the screen width, so one point is this many pixels. */
    private fun pixelsPerPoint(): Float = rootWidth() / PAGE_SIZE_PT

    private fun targetCenterOnScreen(): Offset = Offset(TARGET_CENTER_PT, TARGET_CENTER_PT) * pixelsPerPoint()

    private fun targetHalfSizePx(): Float = TARGET_HALF_SIZE_PT * pixelsPerPoint()

    /** White pages with a small red square in their top-left quarter, away from the screen center. */
    private fun writeTargetPdf(pageCount: Int = 1): File = writePdf("Target.pdf", pageCount) { canvas ->
        canvas.drawRect(
            TARGET_CENTER_PT - TARGET_HALF_SIZE_PT,
            TARGET_CENTER_PT - TARGET_HALF_SIZE_PT,
            TARGET_CENTER_PT + TARGET_HALF_SIZE_PT,
            TARGET_CENTER_PT + TARGET_HALF_SIZE_PT,
            Paint().apply { color = Color.RED },
        )
    }

    /** A white page covered in black vertical stripes a quarter point wide, half a point apart. */
    private fun writeStripesPdf(): File = writePdf("Stripes.pdf") { canvas ->
        val paint = Paint().apply { color = Color.BLACK }
        var x = 0f
        while (x < PAGE_SIZE_PT) {
            canvas.drawRect(x, 0f, x + 0.25f, PAGE_SIZE_PT, paint)
            x += 0.5f
        }
    }

    private fun writePdf(name: String, pageCount: Int = 1, draw: (Canvas) -> Unit): File {
        testDir.mkdirs()
        val file = File(testDir, name)
        val document = PdfDocument()
        try {
            val size = PAGE_SIZE_PT.toInt()
            for (pageNumber in 1..pageCount) {
                val page = document.startPage(PdfDocument.PageInfo.Builder(size, size, pageNumber).create())
                draw(page.canvas)
                document.finishPage(page)
            }
            file.outputStream().use(document::writeTo)
        } finally {
            document.close()
        }
        return file
    }

    /** Share of the middle half of row [y] that is solid black. */
    private fun Bitmap.blackShareOfRow(y: Int): Float {
        val columns = width / 4 until width * 3 / 4
        return columns.count { x -> Color.red(getPixel(x, y)) < 16 }.toFloat() / (columns.last - columns.first + 1)
    }

    /** Share of neighbouring pixels in the middle half of row [y] that jump between black and white. */
    private fun Bitmap.hardEdgeShareOfRow(y: Int): Float {
        val columns = width / 4 until width * 3 / 4
        val hardEdges = columns.count { x ->
            kotlin.math.abs(Color.red(getPixel(x, y)) - Color.red(getPixel(x + 1, y))) > 200
        }
        return hardEdges.toFloat() / (columns.last - columns.first + 1)
    }

    private companion object {
        const val TARGET_CENTER_PT = 75f
        const val TARGET_HALF_SIZE_PT = 12f
    }
}
