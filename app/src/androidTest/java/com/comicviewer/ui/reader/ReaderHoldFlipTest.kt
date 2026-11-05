package com.comicviewer.ui.reader

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.comicviewer.ComicBook
import com.comicviewer.ComicPage
import com.comicviewer.ComicSource
import com.comicviewer.R
import com.comicviewer.ReaderPreferences
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PAGE_COUNT = 40

@RunWith(AndroidJUnit4::class)
class ReaderHoldFlipTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val pageDir = File(context.cacheDir, "reader-hold-flip-test")
    private val selectedPages = mutableListOf<Int>()

    @After
    fun cleanUp() {
        pageDir.deleteRecursively()
    }

    @Test
    fun holdingTheRightEdge_flipsPagesUntilTheFingerLifts() {
        showReader(controlsVisible = false)
        // The clock only moves when the test moves it, so the hold lasts exactly as long as below.
        composeRule.mainClock.autoAdvance = false

        composeRule.onRoot().performTouchInput { down(Offset(width * 0.9f, height / 2f)) }
        composeRule.mainClock.advanceTimeBy(2_000L)
        val flippedWhileHeld = selectedPages.size
        composeRule.onRoot().performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(1_000L)

        // A 2 s hold flips one page when the hold is recognized and several more as it speeds up.
        assertTrue("flipped $flippedWhileHeld pages", flippedWhileHeld >= 5)
        assertEquals((1..flippedWhileHeld).toList(), selectedPages.toList())
        // Lifting the finger stops it without one more page turn from the release.
        assertEquals(flippedWhileHeld, selectedPages.size)
    }

    @Test
    fun holdingTheLeftEdge_flipsBackwards() {
        showReader(controlsVisible = false, startPage = 20)
        composeRule.mainClock.autoAdvance = false

        composeRule.onRoot().performTouchInput { down(Offset(width * 0.1f, height / 2f)) }
        composeRule.mainClock.advanceTimeBy(1_500L)
        composeRule.onRoot().performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(500L)

        assertTrue("flipped ${selectedPages.size} pages", selectedPages.size >= 3)
        assertEquals((19 downTo 20 - selectedPages.size).toList(), selectedPages.toList())
    }

    @Test
    fun slidingTheFingerUpWhileHolding_flipsAtTheTopSpeed() {
        showReader(controlsVisible = false)
        composeRule.mainClock.autoAdvance = false

        composeRule.onRoot().performTouchInput { down(Offset(width * 0.9f, height * 0.7f)) }
        composeRule.mainClock.advanceTimeBy(500L)
        composeRule.onRoot().performTouchInput { moveBy(Offset(0f, -200.dp.toPx())) }
        composeRule.mainClock.advanceTimeBy(1_500L)

        composeRule.onNodeWithText(speedLabel(20)).assertExists()
        val flipped = selectedPages.size
        composeRule.onRoot().performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(500L)
        assertTrue("flipped $flipped pages", flipped >= 8)
    }

    @Test
    fun slidingTheFingerDownWhileHolding_slowsToOnePageASecond() {
        showReader(controlsVisible = false)
        composeRule.mainClock.autoAdvance = false

        composeRule.onRoot().performTouchInput { down(Offset(width * 0.9f, height * 0.3f)) }
        composeRule.mainClock.advanceTimeBy(500L)
        composeRule.onRoot().performTouchInput { moveBy(Offset(0f, 300.dp.toPx())) }
        composeRule.mainClock.advanceTimeBy(2_000L)

        composeRule.onNodeWithText(speedLabel(1)).assertExists()
        val flipped = selectedPages.size
        composeRule.onRoot().performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(500L)
        // One page when the hold is recognized, then one a second.
        assertTrue("flipped $flipped pages", flipped in 1..3)
    }

    @Test
    fun tappingTheRightEdge_stillTurnsOnePage() {
        showReader(controlsVisible = false)

        composeRule.onRoot().performTouchInput { click(Offset(width * 0.9f, height / 2f)) }
        composeRule.waitForIdle()

        assertEquals(listOf(1), selectedPages.toList())
    }

    @Test
    fun swipingFromTheEdge_stillTurnsOnePage() {
        showReader(controlsVisible = false)

        composeRule.onRoot().performTouchInput {
            swipeLeft(startX = width * 0.9f, endX = width * 0.2f, durationMillis = 200L)
        }
        composeRule.waitForIdle()

        assertEquals(listOf(1), selectedPages.toList())
    }

    private fun speedLabel(pagesPerSecond: Int): String =
        context.resources.getQuantityString(R.plurals.reader_hold_flip_speed, pagesPerSecond, pagesPerSecond)

    private fun showReader(controlsVisible: Boolean, startPage: Int = 0) {
        val book = sampleBook()
        composeRule.setContent {
            var pageIndex by remember { mutableIntStateOf(startPage) }
            MaterialTheme {
                PagedComicReader(
                    book = book,
                    pageIndex = pageIndex,
                    activeReaderPreferences = ReaderPreferences(),
                    controlsVisible = controlsVisible,
                    onControlsVisibleChange = {},
                    onPageSelected = { page ->
                        if (page != pageIndex) selectedPages += page
                        pageIndex = page
                    },
                    onCloseComic = {},
                    onOpenSettings = {},
                    onOpenGrid = {},
                    favorite = null,
                    onToggleFavorite = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun sampleBook(): ComicBook {
        pageDir.mkdirs()
        val pages = (1..PAGE_COUNT).map { pageNumber ->
            val name = "%03d.png".format(pageNumber)
            val file = File(pageDir, name)
            val bitmap = Bitmap.createBitmap(60, 90, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.rgb(pageNumber * 6, 80, 160))
            }
            file.outputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output) }
            ComicPage(
                index = pageNumber - 1,
                displayName = name,
                relativePath = name,
                imageUri = Uri.fromFile(file),
                width = 60,
                height = 90,
            )
        }
        return ComicBook(
            title = "Hold flip test",
            source = ComicSource.Folder(Uri.fromFile(pageDir), "Hold flip test"),
            pages = pages,
        )
    }
}
