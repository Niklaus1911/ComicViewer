package com.comicviewer

import android.content.Context
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

private const val LIBRARY_METADATA_PREFS_NAME = "library_metadata"
private const val LIBRARY_METADATA_KEY = "items"
private const val MAX_LIBRARY_METADATA_RECORDS = MAX_LIBRARY_COMICS
private const val MAX_LIBRARY_TAGS = 64
private const val MAX_LIBRARY_TAG_CHARS = 128
private const val MAX_PERSISTED_PATH_CHARS = 4_096

data class LibraryComicMetadata(
    val sourceType: RecentComicSourceType,
    val path: String,
    val favorite: Boolean = false,
    val tags: List<String> = emptyList(),
    val updatedAt: Long = 0L,
)

enum class LibraryProgressFilter {
    All,
    Favorites,
    Unread,
    InProgress,
    Completed,
}

fun resolvedLibraryMetadata(
    metadata: List<LibraryComicMetadata>,
    isProUnlocked: Boolean,
): List<LibraryComicMetadata> =
    if (isProUnlocked) metadata else emptyList()

fun resolvedLibraryProgressFilter(
    progressFilter: LibraryProgressFilter,
    isProUnlocked: Boolean,
): LibraryProgressFilter =
    if (isProUnlocked) progressFilter else LibraryProgressFilter.All

fun resolvedLibraryTagFilter(
    tagFilter: String?,
    isProUnlocked: Boolean,
): String? =
    if (isProUnlocked) tagFilter else null

fun loadLibraryMetadata(context: Context): List<LibraryComicMetadata> {
    val storedValue = context.libraryMetadataPrefs().getString(LIBRARY_METADATA_KEY, null) ?: return emptyList()
    return runCatching { decodeLibraryMetadata(storedValue) }.getOrDefault(emptyList())
}

fun saveLibraryMetadata(context: Context, metadata: List<LibraryComicMetadata>) {
    context.libraryMetadataPrefs()
        .edit()
        .putString(LIBRARY_METADATA_KEY, encodeLibraryMetadata(metadata))
        .apply()
}

fun LibraryComic.metadataKey(): String = libraryMetadataKey(sourceType, path)

fun LibraryComicMetadata.metadataKey(): String = libraryMetadataKey(sourceType, path)

fun metadataFor(
    libraryComic: LibraryComic,
    metadata: List<LibraryComicMetadata>,
): LibraryComicMetadata =
    metadata.firstOrNull { item -> item.metadataKey() == libraryComic.metadataKey() }
        ?: LibraryComicMetadata(sourceType = libraryComic.sourceType, path = libraryComic.path)

fun upsertLibraryMetadata(
    metadata: List<LibraryComicMetadata>,
    item: LibraryComicMetadata,
): List<LibraryComicMetadata> =
    (listOf(item.normalized()) + metadata.filterNot { existing -> existing.metadataKey() == item.metadataKey() })
        .filter { existing -> existing.favorite || existing.tags.isNotEmpty() }
        .sortedByDescending { existing -> existing.updatedAt }

fun pruneLibraryMetadata(
    metadata: List<LibraryComicMetadata>,
    libraryComics: List<LibraryComic>,
): List<LibraryComicMetadata> {
    val libraryKeys = libraryComics.mapTo(mutableSetOf()) { comic -> comic.metadataKey() }
    return metadata.filter { item -> item.metadataKey() in libraryKeys }
}

fun toggleLibraryFavorite(
    libraryComic: LibraryComic,
    metadata: List<LibraryComicMetadata>,
    nowMs: Long,
): List<LibraryComicMetadata> {
    val current = metadataFor(libraryComic, metadata)
    return upsertLibraryMetadata(
        metadata = metadata,
        item = current.copy(favorite = !current.favorite, updatedAt = nowMs),
    )
}

fun setLibraryTags(
    libraryComic: LibraryComic,
    metadata: List<LibraryComicMetadata>,
    tags: List<String>,
    nowMs: Long,
): List<LibraryComicMetadata> {
    val current = metadataFor(libraryComic, metadata)
    return upsertLibraryMetadata(
        metadata = metadata,
        item = current.copy(tags = normalizeLibraryTags(tags), updatedAt = nowMs),
    )
}

fun availableLibraryTags(metadata: List<LibraryComicMetadata>): List<String> =
    metadata.flatMap { item -> item.tags }
        .distinctBy { tag -> tag.lowercase(Locale.ROOT) }
        .sortedWith { left, right -> compareComicPageNames(left, right) }

internal fun encodeLibraryMetadata(metadata: List<LibraryComicMetadata>): String =
    JSONArray().apply {
        metadata.forEach { item ->
            put(
                JSONObject()
                    .put("sourceType", item.sourceType.storageName)
                    .put("path", item.path)
                    .put("favorite", item.favorite)
                    .put("tags", JSONArray(item.tags))
                    .put("updatedAt", item.updatedAt),
            )
        }
    }.toString()

internal fun decodeLibraryMetadata(storedValue: String): List<LibraryComicMetadata> {
    val array = JSONArray(storedValue)
    return (0 until minOf(array.length(), MAX_LIBRARY_METADATA_RECORDS)).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val sourceType = RecentComicSourceType.fromStorageName(item.optString("sourceType"))
            ?: return@mapNotNull null
        val path = item.optString("path").takeIf { path ->
            path.isNotBlank() && path.length <= MAX_PERSISTED_PATH_CHARS
        }
            ?: return@mapNotNull null
        LibraryComicMetadata(
            sourceType = sourceType,
            path = path,
            favorite = item.optBoolean("favorite", false),
            tags = item.optJSONArray("tags")?.let(::decodeTags).orEmpty(),
            updatedAt = item.optLong("updatedAt", 0L),
        ).normalized()
    }.filter { item -> item.favorite || item.tags.isNotEmpty() }
        .sortedByDescending { item -> item.updatedAt }
}

private fun decodeTags(array: JSONArray): List<String> =
    (0 until minOf(array.length(), MAX_LIBRARY_TAGS)).mapNotNull { index ->
        array.optString(index).takeIf { tag -> tag.isNotBlank() }?.take(MAX_LIBRARY_TAG_CHARS)
    }

private fun normalizeLibraryTags(tags: List<String>): List<String> =
    tags.map { tag -> tag.trim() }
        .filter { tag -> tag.isNotEmpty() }
        .map { tag -> tag.take(MAX_LIBRARY_TAG_CHARS) }
        .distinctBy { tag -> tag.lowercase(Locale.ROOT) }
        .sortedWith { left, right -> compareComicPageNames(left, right) }
        .take(MAX_LIBRARY_TAGS)

private fun LibraryComicMetadata.normalized(): LibraryComicMetadata =
    copy(tags = normalizeLibraryTags(tags))

private fun libraryMetadataKey(sourceType: RecentComicSourceType, path: String): String =
    "${sourceType.storageName}|$path"

private fun Context.libraryMetadataPrefs() =
    getSharedPreferences(LIBRARY_METADATA_PREFS_NAME, Context.MODE_PRIVATE)
