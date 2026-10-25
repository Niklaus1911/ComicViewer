package com.comicviewer

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

const val MAX_RECENT_COMICS = 200

private const val RECENT_COMICS_PREFS_NAME = "recent_comics"
private const val RECENT_COMICS_KEY = "items"
private const val MAX_PERSISTED_PATH_CHARS = 4_096
private const val MAX_PERSISTED_COMIC_TEXT_CHARS = 512
private const val MAX_PERSISTED_PAGE_COUNT = MAX_COMIC_ARCHIVE_ENTRIES

enum class RecentComicSourceType(val storageName: String) {
    LocalArchive("local_archive"),
    LocalFolder("local_folder"),
    DocumentArchive("document_archive"),
    DocumentFolder("document_folder"),
    ;

    companion object {
        fun fromStorageName(storageName: String): RecentComicSourceType? =
            entries.firstOrNull { type -> type.storageName == storageName }
    }
}

data class RecentComic(
    val sourceType: RecentComicSourceType,
    val path: String,
    val displayName: String,
    val title: String,
    val lastPageIndex: Int,
    val pageCount: Int,
    val lastOpenedAt: Long,
    val completedAt: Long? = null,
)

fun loadRecentComics(context: Context): List<RecentComic> {
    val storedValue = context.recentComicsPrefs().getString(RECENT_COMICS_KEY, null) ?: return emptyList()
    return runCatching { decodeRecentComics(storedValue) }.getOrDefault(emptyList())
}

fun saveRecentComics(context: Context, recentComics: List<RecentComic>) {
    context.recentComicsPrefs()
        .edit()
        .putString(RECENT_COMICS_KEY, encodeRecentComics(recentComics.take(MAX_RECENT_COMICS)))
        .apply()
}

fun recentComicFor(
    book: ComicBook,
    pageIndex: Int,
    lastOpenedAt: Long,
): RecentComic? {
    val source = book.source
    val sourceType = when (source) {
        is ComicSource.LocalArchive -> RecentComicSourceType.LocalArchive
        is ComicSource.LocalFolder -> RecentComicSourceType.LocalFolder
        is ComicSource.Archive -> RecentComicSourceType.DocumentArchive
        is ComicSource.Folder -> RecentComicSourceType.DocumentFolder
    }
    val path = when (source) {
        is ComicSource.LocalArchive -> source.filePath
        is ComicSource.LocalFolder -> source.folderPath
        is ComicSource.Archive -> source.uri.toString()
        is ComicSource.Folder -> source.uri.toString()
    }

    return RecentComic(
        sourceType = sourceType,
        path = path,
        displayName = source.displayName,
        title = book.title,
        lastPageIndex = coercePageIndex(pageIndex, book.pages.size),
        pageCount = book.pages.size,
        lastOpenedAt = lastOpenedAt,
        completedAt = lastOpenedAt.takeIf {
            book.pages.isNotEmpty() && coercePageIndex(pageIndex, book.pages.size) == book.pages.lastIndex
        },
    )
}

fun upsertRecentComic(
    recentComics: List<RecentComic>,
    recentComic: RecentComic,
    maxItems: Int = MAX_RECENT_COMICS,
): List<RecentComic> {
    val normalized = recentComic.withValidProgress()
    return (listOf(normalized) + recentComics.filterNot { comic -> comic.hasSameSourceAs(normalized) })
        .sortedByDescending { comic -> comic.lastOpenedAt }
        .take(maxItems)
}

fun mergeOpenedRecentComic(
    existing: RecentComic?,
    opened: RecentComic,
): RecentComic = opened.copy(
    completedAt = existing?.completedAt
        ?.takeIf { existing.pageCount == opened.pageCount }
        ?: opened.completedAt,
).withValidProgress()

fun updateRecentComicPage(
    recentComics: List<RecentComic>,
    sourceType: RecentComicSourceType,
    path: String,
    pageIndex: Int,
    pageCount: Int,
    nowMs: Long = System.currentTimeMillis(),
): List<RecentComic> =
    recentComics.map { comic ->
        if (comic.sourceType == sourceType && comic.path == path) {
            val normalizedPageCount = pageCount.coerceIn(0, MAX_PERSISTED_PAGE_COUNT)
            comic.copy(
                lastPageIndex = coercePageIndex(pageIndex, normalizedPageCount),
                pageCount = normalizedPageCount,
                completedAt = comic.completedAt ?: nowMs.takeIf {
                    normalizedPageCount > 0 &&
                        coercePageIndex(pageIndex, normalizedPageCount) == normalizedPageCount - 1
                },
            ).withValidProgress()
        } else {
            comic
        }
    }

/**
 * Marks a library comic as finished without opening it. The page count comes from the existing
 * history entry or the inspected archive; when neither is known the comic counts as one page.
 */
fun markRecentComicRead(
    recentComics: List<RecentComic>,
    libraryComic: LibraryComic,
    knownPageCount: Int?,
    nowMs: Long,
): List<RecentComic> {
    val existing = libraryComic.matchingRecentComic(recentComics)
    val pageCount = (existing?.pageCount?.takeIf { it > 0 } ?: knownPageCount?.takeIf { it > 0 } ?: 1)
        .coerceAtMost(MAX_PERSISTED_PAGE_COUNT)
    val finished = RecentComic(
        sourceType = libraryComic.sourceType,
        path = libraryComic.path,
        displayName = existing?.displayName ?: libraryComic.displayName,
        title = existing?.title ?: libraryComic.title,
        lastPageIndex = pageCount - 1,
        pageCount = pageCount,
        lastOpenedAt = nowMs,
        completedAt = nowMs,
    )
    return upsertRecentComic(recentComics, finished)
}

/**
 * Finished once the last page was reached, and it stays finished while the comic is read again,
 * until it is marked unread. History, the library and the details screen all use this.
 */
fun RecentComic.isFinished(): Boolean =
    completedAt != null || (pageCount > 0 && coercePageIndex(lastPageIndex, pageCount) == pageCount - 1)

/** One-based reading progress in 0f..1f, or null when the page count is unknown. */
fun RecentComic.progressFraction(): Float? {
    if (pageCount <= 0) return null
    return ((coercePageIndex(lastPageIndex, pageCount) + 1).toFloat() / pageCount).coerceIn(0f, 1f)
}

fun removeRecentComic(
    recentComics: List<RecentComic>,
    sourceType: RecentComicSourceType,
    path: String,
): List<RecentComic> =
    recentComics.filterNot { comic -> comic.sourceType == sourceType && comic.path == path }

val RecentComicSourceType.isArchive: Boolean
    get() = this == RecentComicSourceType.LocalArchive || this == RecentComicSourceType.DocumentArchive

val RecentComicSourceType.isFolder: Boolean
    get() = this == RecentComicSourceType.LocalFolder || this == RecentComicSourceType.DocumentFolder

private fun Context.recentComicsPrefs() =
    getSharedPreferences(RECENT_COMICS_PREFS_NAME, Context.MODE_PRIVATE)

private fun RecentComic.hasSameSourceAs(other: RecentComic): Boolean =
    sourceType == other.sourceType && path == other.path

private fun RecentComic.withValidProgress(): RecentComic =
    pageCount.coerceIn(0, MAX_PERSISTED_PAGE_COUNT).let { normalizedPageCount ->
        copy(
            lastPageIndex = coercePageIndex(lastPageIndex, normalizedPageCount),
            pageCount = normalizedPageCount,
            lastOpenedAt = lastOpenedAt.coerceAtLeast(0L),
            completedAt = completedAt?.takeIf { it > 0L },
        )
    }

internal fun encodeRecentComics(recentComics: List<RecentComic>): String =
    JSONArray().apply {
        recentComics.forEach { comic ->
            put(
                JSONObject()
                    .put("sourceType", comic.sourceType.storageName)
                    .put("path", comic.path)
                    .put("displayName", comic.displayName)
                    .put("title", comic.title)
                    .put("lastPageIndex", comic.lastPageIndex)
                    .put("pageCount", comic.pageCount)
                    .put("lastOpenedAt", comic.lastOpenedAt)
                    .put("completedAt", comic.completedAt ?: JSONObject.NULL),
            )
        }
    }.toString()

internal fun decodeRecentComics(storedValue: String): List<RecentComic> {
    val array = JSONArray(storedValue)
    return (0 until minOf(array.length(), MAX_RECENT_COMICS)).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val sourceType = RecentComicSourceType.fromStorageName(item.optString("sourceType"))
            ?: return@mapNotNull null
        val path = item.optString("path").takeIf { path ->
            path.isNotBlank() && path.length <= MAX_PERSISTED_PATH_CHARS
        }
            ?: return@mapNotNull null
        val displayName = item.optString("displayName").takeIf { name -> name.isNotBlank() }
            ?.take(MAX_PERSISTED_COMIC_TEXT_CHARS)
            ?: File(path).name.ifBlank { path }.take(MAX_PERSISTED_COMIC_TEXT_CHARS)
        val title = item.optString("title").takeIf { title -> title.isNotBlank() }
            ?.take(MAX_PERSISTED_COMIC_TEXT_CHARS)
            ?: displayName
        RecentComic(
            sourceType = sourceType,
            path = path,
            displayName = displayName,
            title = title,
            lastPageIndex = item.optInt("lastPageIndex", 0),
            pageCount = item.optInt("pageCount", 0),
            lastOpenedAt = item.optLong("lastOpenedAt", 0L),
            completedAt = if (item.isNull("completedAt")) null else item.optLong("completedAt", 0L).takeIf { it > 0L },
        ).withValidProgress()
    }.distinctBy { comic -> "${comic.sourceType.storageName}|${comic.path}" }
        .sortedByDescending { comic -> comic.lastOpenedAt }
        .take(MAX_RECENT_COMICS)
}
