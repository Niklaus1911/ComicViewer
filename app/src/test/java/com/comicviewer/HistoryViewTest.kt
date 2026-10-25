package com.comicviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryViewTest {
    @Test
    fun historyGroupFor_bucketsByCalendarDayInTheGivenZone() {
        val zone = java.util.TimeZone.getTimeZone("Europe/Rome")
        val calendar = java.util.Calendar.getInstance(zone).apply {
            clear()
            set(2026, java.util.Calendar.OCTOBER, 25, 9, 0)
        }
        val now = calendar.timeInMillis
        fun at(daysAgo: Int, hour: Int): Long = (calendar.clone() as java.util.Calendar).apply {
            add(java.util.Calendar.DAY_OF_YEAR, -daysAgo)
            set(java.util.Calendar.HOUR_OF_DAY, hour)
        }.timeInMillis

        assertEquals(HistoryGroup.Today, historyGroupFor(at(0, 0), now, zone))
        assertEquals(HistoryGroup.Today, historyGroupFor(now + 60_000L, now, zone))
        assertEquals(HistoryGroup.Yesterday, historyGroupFor(at(1, 23), now, zone))
        assertEquals(HistoryGroup.Yesterday, historyGroupFor(at(1, 0), now, zone))
        assertEquals(HistoryGroup.ThisWeek, historyGroupFor(at(2, 12), now, zone))
        assertEquals(HistoryGroup.ThisWeek, historyGroupFor(at(6, 1), now, zone))
        assertEquals(HistoryGroup.Earlier, historyGroupFor(at(7, 23), now, zone))
    }

    @Test
    fun groupedHistory_filtersFinishedAndReadingAndSkipsEmptyGroups() {
        val zone = java.util.TimeZone.getTimeZone("UTC")
        val day = 24L * 60L * 60L * 1000L
        val now = 100L * day + 12L * 60L * 60L * 1000L
        val readingToday = sampleRecentComic(path = "/c/a.cbz", lastPageIndex = 2, pageCount = 10, lastOpenedAt = now - 1_000L)
        val finishedOld = sampleRecentComic(path = "/c/b.cbz", lastPageIndex = 9, pageCount = 10, lastOpenedAt = now - 30L * day)
        val readingOld = sampleRecentComic(path = "/c/c.cbz", lastPageIndex = 1, pageCount = 10, lastOpenedAt = now - 20L * day)
        val all = listOf(finishedOld, readingToday, readingOld)

        assertEquals(
            listOf(HistoryGroup.Today to listOf(readingToday), HistoryGroup.Earlier to listOf(readingOld, finishedOld)),
            groupedHistory(all, HistoryFilter.All, now, zone),
        )
        assertEquals(
            listOf(HistoryGroup.Earlier to listOf(finishedOld)),
            groupedHistory(all, HistoryFilter.Finished, now, zone),
        )
        assertEquals(listOf(readingToday, readingOld), groupedHistory(all, HistoryFilter.Reading, now, zone).flatMap { it.second })
        assertTrue(groupedHistory(emptyList(), HistoryFilter.All, now, zone).isEmpty())
    }
}
