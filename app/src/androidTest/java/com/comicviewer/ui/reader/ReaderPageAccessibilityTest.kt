package com.comicviewer.ui.reader

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.comicviewer.ComicBook
import com.comicviewer.ComicPage
import com.comicviewer.ComicSource
import com.comicviewer.R
import com.comicviewer.ReaderPageMode
import com.comicviewer.ReaderPreferences
import java.io.File
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PAGE_COUNT = 3
private const val PAGE_LOAD_TIMEOUT_MS = 5_000L

@RunWith(AndroidJUnit4::class)
class ReaderPageAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val pageDir = File(context.cacheDir, "reader-accessibility-test")

    @After
    fun cleanUp() {
        pageDir.deleteRecursively()
    }

    @Test
    fun pagedReader_announcesPagePositionInsteadOfFileName() {
        val book = sampleBook()
        composeRule.setContent {
            MaterialTheme {
                PagedComicReader(
                    book = book,
                    pageIndex = 1,
                    activeReaderPreferences = ReaderPreferences(),
                    controlsVisible = false,
                    onControlsVisibleChange = {},
                    onPageSelected = {},
                    onCloseComic = {},
                    onOpenSettings = {},
                    onOpenGrid = {},
                    favorite = null,
                    onToggleFavorite = {},
                )
            }
        }

        composeRule.waitUntil(PAGE_LOAD_TIMEOUT_MS) {
            composeRule.onAllNodesWithContentDescription(pageLabel(2)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithContentDescription("002.png").assertCountEquals(0)
    }

    @Test
    fun verticalReader_announcesPagePositionInsteadOfFileName() {
        val book = sampleBook()
        composeRule.setContent {
            MaterialTheme {
                VerticalComicReader(
                    book = book,
                    pageIndex = 0,
                    preferences = ReaderPreferences(pageMode = ReaderPageMode.Vertical),
                    controlsVisible = false,
                    onControlsVisibleChange = {},
                    onPageSelected = {},
                    onCloseComic = {},
                    onOpenSettings = {},
                    onOpenGrid = {},
                    favorite = null,
                    onToggleFavorite = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription(pageLabel(1)).assertExists()
        composeRule.onAllNodesWithContentDescription("001.png").assertCountEquals(0)
    }

    @Test
    fun readerPageGrid_labelsThumbnailsAndMarksOnlyTheCurrentPage() {
        val book = sampleBook()
        composeRule.setContent {
            MaterialTheme {
                ReaderPageGrid(
                    book = book,
                    currentPageIndex = 1,
                    onPageSelected = {},
                    onDismiss = {},
                )
            }
        }

        val currentPageState = context.getString(R.string.reader_current_page)
        composeRule.onNodeWithContentDescription(pageLabel(2))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, currentPageState))
        composeRule.onNodeWithContentDescription(pageLabel(1))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        composeRule.onAllNodesWithContentDescription("002.png").assertCountEquals(0)
    }

    private fun pageLabel(pageNumber: Int): String =
        context.getString(R.string.reader_page_description, pageNumber, PAGE_COUNT)

    private fun sampleBook(): ComicBook {
        pageDir.mkdirs()
        val pages = (1..PAGE_COUNT).map { pageNumber ->
            val name = "%03d.png".format(pageNumber)
            val file = File(pageDir, name)
            val bitmap = Bitmap.createBitmap(60, 90, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
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
            title = "Accessibility test",
            source = ComicSource.Folder(Uri.fromFile(pageDir), "Accessibility test"),
            pages = pages,
        )
    }
}
