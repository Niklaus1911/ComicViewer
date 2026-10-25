package com.comicviewer

import java.util.Calendar
import java.util.TimeZone

enum class HistoryFilter {
    All,
    Reading,
    Finished,
}

/** Date sections in the History tab, newest first. */
enum class HistoryGroup {
    Today,
    Yesterday,
    ThisWeek,
    Earlier,
}

fun RecentComic.isFinished(): Boolean =
    completedAt != null || (pageCount > 0 && coercePageIndex(lastPageIndex, pageCount) == pageCount - 1)

fun RecentComic.readingProgress(): Float {
    if (pageCount <= 0) return 0f
    return ((coercePageIndex(lastPageIndex, pageCount) + 1).toFloat() / pageCount).coerceIn(0f, 1f)
}

/**
 * Buckets a history entry by calendar day in [timeZone]: today, yesterday, the five days before
 * that, or earlier. Future timestamps (a wrong clock) count as today.
 */
fun historyGroupFor(timestamp: Long, nowMs: Long, timeZone: TimeZone = TimeZone.getDefault()): HistoryGroup {
    val startOfToday = Calendar.getInstance(timeZone).apply {
        timeInMillis = nowMs
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (timestamp >= startOfToday.timeInMillis) return HistoryGroup.Today
    val startOfYesterday = (startOfToday.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    if (timestamp >= startOfYesterday.timeInMillis) return HistoryGroup.Yesterday
    val startOfWeek = (startOfToday.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -6) }
    if (timestamp >= startOfWeek.timeInMillis) return HistoryGroup.ThisWeek
    return HistoryGroup.Earlier
}

/** History entries for [filter], newest first, grouped into date sections that skip empty groups. */
fun groupedHistory(
    recentComics: List<RecentComic>,
    filter: HistoryFilter,
    nowMs: Long,
    timeZone: TimeZone = TimeZone.getDefault(),
): List<Pair<HistoryGroup, List<RecentComic>>> =
    recentComics
        .filter { comic ->
            when (filter) {
                HistoryFilter.All -> true
                HistoryFilter.Reading -> !comic.isFinished()
                HistoryFilter.Finished -> comic.isFinished()
            }
        }
        .sortedByDescending(RecentComic::lastOpenedAt)
        .groupBy { comic -> historyGroupFor(comic.lastOpenedAt, nowMs, timeZone) }
        .toSortedMap(compareBy(HistoryGroup::ordinal))
        .map { (group, comics) -> group to comics }
