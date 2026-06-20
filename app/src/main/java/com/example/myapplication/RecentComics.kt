package com.example.myapplication

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

const val MAX_RECENT_COMICS = 10

private const val RECENT_COMICS_PREFS_NAME = "recent_comics"
private const val RECENT_COMICS_KEY = "items"

enum class RecentComicSourceType(val storageName: String) {
    LocalArchive("local_archive"),
    LocalFolder("local_folder"),
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
        is ComicSource.Archive,
        is ComicSource.Folder,
        -> return null
    }
    val path = when (source) {
        is ComicSource.LocalArchive -> source.filePath
        is ComicSource.LocalFolder -> source.folderPath
        is ComicSource.Archive,
        is ComicSource.Folder,
        -> return null
    }

    return RecentComic(
        sourceType = sourceType,
        path = path,
        displayName = source.displayName,
        title = book.title,
        lastPageIndex = coercePageIndex(pageIndex, book.pages.size),
        pageCount = book.pages.size,
        lastOpenedAt = lastOpenedAt,
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

fun updateRecentComicPage(
    recentComics: List<RecentComic>,
    sourceType: RecentComicSourceType,
    path: String,
    pageIndex: Int,
    pageCount: Int,
): List<RecentComic> =
    recentComics.map { comic ->
        if (comic.sourceType == sourceType && comic.path == path) {
            comic.copy(
                lastPageIndex = coercePageIndex(pageIndex, pageCount),
                pageCount = pageCount,
            )
        } else {
            comic
        }
    }

fun removeRecentComic(
    recentComics: List<RecentComic>,
    sourceType: RecentComicSourceType,
    path: String,
): List<RecentComic> =
    recentComics.filterNot { comic -> comic.sourceType == sourceType && comic.path == path }

fun removeUnreadableRecentComics(
    recentComics: List<RecentComic>,
    canRead: (RecentComic) -> Boolean,
): List<RecentComic> =
    recentComics.filter(canRead)

fun RecentComic.isReadableOnDisk(): Boolean {
    val file = File(path)
    return when (sourceType) {
        RecentComicSourceType.LocalArchive -> file.isFile && file.canRead()
        RecentComicSourceType.LocalFolder -> file.isDirectory && file.canRead()
    }
}

private fun Context.recentComicsPrefs() =
    getSharedPreferences(RECENT_COMICS_PREFS_NAME, Context.MODE_PRIVATE)

private fun RecentComic.hasSameSourceAs(other: RecentComic): Boolean =
    sourceType == other.sourceType && path == other.path

private fun RecentComic.withValidProgress(): RecentComic =
    copy(lastPageIndex = coercePageIndex(lastPageIndex, pageCount))

private fun encodeRecentComics(recentComics: List<RecentComic>): String =
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
                    .put("lastOpenedAt", comic.lastOpenedAt),
            )
        }
    }.toString()

private fun decodeRecentComics(storedValue: String): List<RecentComic> {
    val array = JSONArray(storedValue)
    return (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val sourceType = RecentComicSourceType.fromStorageName(item.optString("sourceType"))
            ?: return@mapNotNull null
        val path = item.optString("path").takeIf { path -> path.isNotBlank() }
            ?: return@mapNotNull null
        val displayName = item.optString("displayName").takeIf { name -> name.isNotBlank() }
            ?: File(path).name.ifBlank { path }
        val title = item.optString("title").takeIf { title -> title.isNotBlank() }
            ?: displayName
        RecentComic(
            sourceType = sourceType,
            path = path,
            displayName = displayName,
            title = title,
            lastPageIndex = item.optInt("lastPageIndex", 0),
            pageCount = item.optInt("pageCount", 0),
            lastOpenedAt = item.optLong("lastOpenedAt", 0L),
        ).withValidProgress()
    }.sortedByDescending { comic -> comic.lastOpenedAt }
        .take(MAX_RECENT_COMICS)
}
