package com.comicviewer

import android.content.Context
import androidx.core.content.edit
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

private const val LIBRARY_COMICS_PREFS_NAME = "library_comics"
private const val LIBRARY_COMICS_KEY = "items"
private const val MAX_PERSISTED_PATH_CHARS = 4_096
private const val MAX_PERSISTED_COMIC_TEXT_CHARS = 512

const val MAX_LIBRARY_COMICS = 1_000

data class LibraryComic(
    val sourceType: RecentComicSourceType,
    val path: String,
    val displayName: String,
    val title: String,
    val addedAt: Long,
)

fun loadLibraryComics(context: Context): List<LibraryComic> {
    val storedValue = context.libraryComicsPrefs().getString(LIBRARY_COMICS_KEY, null) ?: return emptyList()
    return runCatching { decodeLibraryComics(storedValue) }.getOrDefault(emptyList())
}

fun saveLibraryComics(context: Context, libraryComics: List<LibraryComic>) {
    context.libraryComicsPrefs().edit {
        putString(LIBRARY_COMICS_KEY, encodeLibraryComics(libraryComics.take(MAX_LIBRARY_COMICS)))
    }
}

fun libraryComicForArchive(file: File, addedAt: Long): LibraryComic =
    LibraryComic(
        sourceType = RecentComicSourceType.LocalArchive,
        path = file.absolutePath,
        displayName = file.name.ifBlank { file.absolutePath },
        title = file.name.withoutComicExtension(),
        addedAt = addedAt,
    )

fun libraryComicForFolder(folder: File, addedAt: Long): LibraryComic =
    LibraryComic(
        sourceType = RecentComicSourceType.LocalFolder,
        path = folder.absolutePath,
        displayName = folder.name.ifBlank { folder.absolutePath },
        title = folder.name.ifBlank { folder.absolutePath },
        addedAt = addedAt,
    )

fun libraryComicForDocumentArchive(
    uri: String,
    displayName: String,
    addedAt: Long,
): LibraryComic =
    LibraryComic(
        sourceType = RecentComicSourceType.DocumentArchive,
        path = uri,
        displayName = displayName,
        title = displayName.withoutComicExtension(),
        addedAt = addedAt,
    )

fun libraryComicForDocumentFolder(
    uri: String,
    displayName: String,
    addedAt: Long,
): LibraryComic =
    LibraryComic(
        sourceType = RecentComicSourceType.DocumentFolder,
        path = uri,
        displayName = displayName,
        title = displayName,
        addedAt = addedAt,
    )

/**
 * Adds [libraryComic], or refreshes it when the same source is already there. A full library is left
 * unchanged rather than dropping older comics, whose favorites and tags would be lost with them.
 */
fun upsertLibraryComic(
    libraryComics: List<LibraryComic>,
    libraryComic: LibraryComic,
    maxItems: Int = MAX_LIBRARY_COMICS,
): List<LibraryComic> {
    val otherComics = libraryComics.filterNot { comic -> comic.hasSameSourceAs(libraryComic) }
    if (otherComics.size >= maxItems) return libraryComics
    return (listOf(libraryComic) + otherComics).sortedByDescending { comic -> comic.addedAt }
}

fun upsertLibraryComics(
    libraryComics: List<LibraryComic>,
    newComics: List<LibraryComic>,
    maxItems: Int = MAX_LIBRARY_COMICS,
): List<LibraryComic> =
    newComics.fold(libraryComics) { currentComics, libraryComic ->
        upsertLibraryComic(
            libraryComics = currentComics,
            libraryComic = libraryComic,
            maxItems = maxItems,
        )
    }

fun removeLibraryComic(
    libraryComics: List<LibraryComic>,
    sourceType: RecentComicSourceType,
    path: String,
): List<LibraryComic> =
    libraryComics.filterNot { comic -> comic.sourceType == sourceType && comic.path == path }

fun LibraryComic.matchingRecentComic(recentComics: List<RecentComic>): RecentComic? =
    recentComics.firstOrNull { recentComic ->
        recentComic.sourceType == sourceType && recentComic.path == path
    }

private fun Context.libraryComicsPrefs() =
    getSharedPreferences(LIBRARY_COMICS_PREFS_NAME, Context.MODE_PRIVATE)

private fun LibraryComic.hasSameSourceAs(other: LibraryComic): Boolean =
    sourceType == other.sourceType && path == other.path

internal fun encodeLibraryComics(libraryComics: List<LibraryComic>): String =
    JSONArray().apply {
        libraryComics.forEach { comic ->
            put(
                JSONObject()
                    .put("sourceType", comic.sourceType.storageName)
                    .put("path", comic.path)
                    .put("displayName", comic.displayName)
                    .put("title", comic.title)
                    .put("addedAt", comic.addedAt),
            )
        }
    }.toString()

internal fun decodeLibraryComics(storedValue: String): List<LibraryComic> {
    val array = JSONArray(storedValue)
    return (0 until minOf(array.length(), MAX_LIBRARY_COMICS)).mapNotNull { index ->
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
            ?: displayName.withoutComicExtension().take(MAX_PERSISTED_COMIC_TEXT_CHARS)
        LibraryComic(
            sourceType = sourceType,
            path = path,
            displayName = displayName,
            title = title,
            addedAt = item.optLong("addedAt", 0L),
        )
    }.distinctBy { comic -> "${comic.sourceType.storageName}|${comic.path}" }
        .sortedByDescending { comic -> comic.addedAt }
        .take(MAX_LIBRARY_COMICS)
}
