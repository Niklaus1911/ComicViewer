package com.comicviewer

import com.comicviewer.ui.reader.ReaderSwipeDirection
import com.comicviewer.ui.reader.readerNavigationDirectionForTap
import com.comicviewer.ui.settings.settingsTapZoneLabels
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsScreenTest {
    @Test
    fun settingsTapZoneLabels_matchesPhysicalTapNavigationInBothDirections() {
        ReaderReadingDirection.entries.forEach { direction ->
            val expected = listOf(100f, 450f, 800f).map { x ->
                when (readerNavigationDirectionForTap(x, 900f, direction)) {
                    ReaderSwipeDirection.Previous -> R.string.reader_previous_page
                    ReaderSwipeDirection.Next -> R.string.reader_next_page
                    null -> R.string.reader_toggle_controls
                }
            }

            assertEquals(expected, settingsTapZoneLabels(direction))
        }
    }

    @Test
    fun settingsTapZoneLabels_keepsAllActionsWithControlsInTheMiddle() {
        ReaderReadingDirection.entries.forEach { direction ->
            val labels = settingsTapZoneLabels(direction)

            assertEquals(3, labels.size)
            assertEquals(R.string.reader_toggle_controls, labels[1])
            assertEquals(
                setOf(R.string.reader_previous_page, R.string.reader_toggle_controls, R.string.reader_next_page),
                labels.toSet(),
            )
        }
    }
}
