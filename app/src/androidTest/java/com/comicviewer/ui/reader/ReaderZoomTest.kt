package com.comicviewer.ui.reader

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.comicviewer.R
import com.comicviewer.ReaderPreferences
import com.comicviewer.closeComicArchives
import com.comicviewer.loadLocalArchiveComic
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
