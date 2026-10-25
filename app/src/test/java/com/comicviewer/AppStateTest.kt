package com.comicviewer

import com.comicviewer.ui.app.AppTab
import com.comicviewer.ui.app.restoreReaderUiState
import com.comicviewer.ui.app.restoredAppTab
import com.comicviewer.ui.app.saveReaderUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStateTest {
    @Test
    fun shouldAcceptThrottledAction_blocksClicksInsideWindow() {
        assertFalse(
            shouldAcceptThrottledAction(
                nowMs = 1_200L,
                lastAcceptedAtMs = 1_000L,
                throttleMs = 350L,
            ),
        )
        assertTrue(
            shouldAcceptThrottledAction(
                nowMs = 1_350L,
                lastAcceptedAtMs = 1_000L,
                throttleMs = 350L,
            ),
        )
    }

    @Test
    fun currentAppBuildInfo_includesGeneratedBuildTimestamp() {
        val timestampPattern = Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2} (Z|[+-]\d{2}:\d{2})""")

        assertTrue(currentAppBuildInfo().buildTimestamp.matches(timestampPattern))
    }

    @Test
    fun readerUiStateSaver_savesOnlySourceAndPageIndex() {
        val source = ComicSource.LocalArchive(
            filePath = "/storage/emulated/0/Long Comic.cbz",
            displayName = "Long Comic.cbz",
        )
        val ready = ReaderUiState.Ready(
            book = ComicBook(
                title = "Long Comic",
                source = source,
                pages = emptyList(),
            ),
            pageIndex = 9_999,
        )

        val savedValues = saveReaderUiState(ready)

        assertEquals(5, savedValues.size)
        assertEquals(
            ReaderUiState.Loading(source = source, pageIndex = 9_999),
            restoreReaderUiState(savedValues),
        )
    }

    @Test
    fun readerUiStateSaver_boundsUntrustedStrings() {
        val savedError = saveReaderUiState(ReaderUiState.Error("x".repeat(100_000)))
        val oversizedSource = ComicSource.LocalArchive(
            filePath = "/" + "a".repeat(9_000),
            displayName = "Comic.cbz",
        )

        assertTrue(savedError.sumOf(String::length) < 2_100)
        assertEquals(
            listOf("empty"),
            saveReaderUiState(
                ReaderUiState.Loading(source = oversizedSource, pageIndex = 0),
            ),
        )
    }

    @Test
    fun restoredAppTab_mapsRemovedTabsToLibrary() {
        assertEquals(AppTab.Library, restoredAppTab("Browse"))
        assertEquals(AppTab.Library, restoredAppTab("Import"))
        assertEquals(AppTab.Library, restoredAppTab("Search"))
        assertEquals(AppTab.History, restoredAppTab("History"))
        assertEquals(AppTab.Settings, restoredAppTab("Settings"))
        assertEquals(AppTab.Library, restoredAppTab("Unknown"))
    }
}
