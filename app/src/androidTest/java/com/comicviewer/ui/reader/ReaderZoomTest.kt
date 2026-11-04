package com.comicviewer.ui.reader

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.comicviewer.R
import com.comicviewer.ReaderPageFitMode
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
private const val SHARPER_PAGE_LOAD_WAIT_MS = 1_500L

@RunWith(AndroidJUnit4::class)
class ReaderZoomTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val testDir = File(context.cacheDir, "reader-zoom-test")

    @After
    fun cleanUp() {
        runBlocking { closeComicArchives() }
        testDir.deleteRecursively()
    }

    @Test
    fun pagedReader_pinchKeepsZoomingWhileASharperPageLoads() {
        val book = runBlocking { loadLocalArchiveComic(context, writeRedSquarePdf()) }
        composeRule.setContent {
            MaterialTheme {
                PagedComicReader(
                    book = book,
                    pageIndex = 0,
                    activeReaderPreferences = ReaderPreferences(showPageNumber = false),
                    controlsVisible = false,
                    onControlsVisibleChange = {},
                    onPageSelected = {},
                    onCloseComic = {},
                    onOpenSettings = {},
                    onOpenGrid = {},
                    favorite = null,
                    onToggleFavorite = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        val pageLabel = context.getString(R.string.reader_page_description, 1, 1)
        composeRule.waitUntil(PAGE_LOAD_TIMEOUT_MS) {
            composeRule.onAllNodesWithContentDescription(pageLabel).fetchSemanticsNodes().isNotEmpty()
        }

        // Two fingers spread to about 1.5x, which asks for a sharper copy of the page, then hold still
        // while it loads and spread on to about 3x in the same gesture.
        val root = composeRule.onRoot()
        var center = Offset.Zero
        root.performTouchInput {
            center = this.center
            down(0, center - Offset(60f, 0f))
            down(1, center + Offset(60f, 0f))
            repeat(10) { step ->
                val spread = 60f + 3f * (step + 1)
                moveTo(0, center - Offset(spread, 0f))
                moveTo(1, center + Offset(spread, 0f))
            }
        }
        Thread.sleep(SHARPER_PAGE_LOAD_WAIT_MS)
        composeRule.waitForIdle()
        root.performTouchInput {
            repeat(20) { step ->
                val spread = 90f + 4.5f * (step + 1)
                moveTo(0, center - Offset(spread, 0f))
                moveTo(1, center + Offset(spread, 0f))
            }
            up(0)
            up(1)
        }
        composeRule.waitForIdle()

        // The red square covers the middle half of the page. Near the left edge of the screen it only
        // shows once the page is zoomed well past 1.5x, so a pinch cut short leaves white there.
        val screen = root.captureToImage().asAndroidBitmap()
        assertEquals(Color.RED, screen.getPixel((screen.width * 0.06f).toInt(), screen.height / 2))
    }

    @Test
    fun pagedReader_showsNoErrorWhileItsFirstPageLoads() {
        val book = runBlocking { loadLocalArchiveComic(context, writeRedSquarePdf()) }
        val errorText = context.getString(R.string.page_image_error, book.pages.first().displayName)
        val pageLabel = context.getString(R.string.reader_page_description, 1, 1)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MaterialTheme {
                PagedComicReader(
                    book = book,
                    pageIndex = 0,
                    activeReaderPreferences = ReaderPreferences(),
                    controlsVisible = false,
                    onControlsVisibleChange = {},
                    onPageSelected = {},
                    onCloseComic = {},
                    onOpenSettings = {},
                    onOpenGrid = {},
                    favorite = null,
                    onToggleFavorite = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Step frame by frame until the page is drawn; the error must not appear on any of them.
        var pageShown = false
        repeat(300) {
            if (pageShown) return@repeat
            composeRule.mainClock.advanceTimeByFrame()
            Thread.sleep(10)
            assertTrue(
                "Error shown while the first page loaded",
                composeRule.onAllNodesWithText(errorText).fetchSemanticsNodes().isEmpty(),
            )
            pageShown = composeRule.onAllNodesWithContentDescription(pageLabel).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue("The page never loaded", pageShown)
    }

    @Test
    fun pagedReader_croppedPageStartsAtItsTopAndKeepsItsPlaceOnPinch() {
        val book = runBlocking { loadLocalArchiveComic(context, writeTallMarginedPdf()) }
        var preferences by mutableStateOf(
            ReaderPreferences(pageFitMode = ReaderPageFitMode.FitWidth, showPageNumber = false),
        )
        composeRule.setContent {
            MaterialTheme {
                PagedComicReader(
                    book = book,
                    pageIndex = 0,
                    activeReaderPreferences = preferences,
                    controlsVisible = false,
                    onControlsVisibleChange = {},
                    onPageSelected = {},
                    onCloseComic = {},
                    onOpenSettings = {},
                    onOpenGrid = {},
                    favorite = null,
                    onToggleFavorite = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        val pageLabel = context.getString(R.string.reader_page_description, 1, 1)
        composeRule.waitUntil(PAGE_LOAD_TIMEOUT_MS) {
            composeRule.onAllNodesWithContentDescription(pageLabel).fetchSemanticsNodes().isNotEmpty()
        }
        Thread.sleep(SHARPER_PAGE_LOAD_WAIT_MS)
        composeRule.waitForIdle()

        // Turned on mid-page, the whole page stays on screen while the cropped one loads. Cropped, the
        // page is narrower, so at full width it is taller, and it must start at its top again.
        preferences = preferences.copy(autoCrop = true)
        Thread.sleep(SHARPER_PAGE_LOAD_WAIT_MS)
        composeRule.waitForIdle()
        val root = composeRule.onRoot()
        val atStart = root.captureToImage().asAndroidBitmap()
        assertEquals(Color.BLUE, atStart.getPixel(atStart.width / 2, (atStart.height * 0.04f).toInt()))

        // Scrolled down into the green middle, a pinch zooms where the page is rather than sending it
        // back to its top.
        var center = Offset.Zero
        root.performTouchInput {
            center = this.center
            down(0, center + Offset(0f, 600f))
            repeat(20) { step -> moveTo(0, center + Offset(0f, 600f - 60f * (step + 1))) }
            up(0)
        }
        composeRule.waitForIdle()
        root.performTouchInput {
            down(0, center - Offset(60f, 0f))
            down(1, center + Offset(60f, 0f))
            repeat(10) { step ->
                val spread = 60f + 2f * (step + 1)
                moveTo(0, center - Offset(spread, 0f))
                moveTo(1, center + Offset(spread, 0f))
            }
            up(0)
            up(1)
        }
        composeRule.waitForIdle()
        val zoomed = root.captureToImage().asAndroidBitmap()
        assertEquals(Color.GREEN, zoomed.getPixel(zoomed.width / 2, (zoomed.height * 0.04f).toInt()))
    }

    /** A tall page with white side margins: a thin blue band on top, green below, red at the bottom. */
    private fun writeTallMarginedPdf(): File {
        testDir.mkdirs()
        val file = File(testDir, "Tall.pdf")
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(600, 1800, 1).create())
            page.canvas.drawRect(100f, 0f, 500f, 120f, Paint().apply { color = Color.BLUE })
            page.canvas.drawRect(100f, 120f, 500f, 1500f, Paint().apply { color = Color.GREEN })
            page.canvas.drawRect(100f, 1500f, 500f, 1800f, Paint().apply { color = Color.RED })
            document.finishPage(page)
            file.outputStream().use(document::writeTo)
        } finally {
            document.close()
        }
        return file
    }

    private fun writeRedSquarePdf(): File {
        testDir.mkdirs()
        val file = File(testDir, "Zoom.pdf")
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(612, 792, 1).create())
            page.canvas.drawRect(153f, 198f, 459f, 594f, Paint().apply { color = Color.RED })
            document.finishPage(page)
            file.outputStream().use(document::writeTo)
        } finally {
            document.close()
        }
        return file
    }
}
