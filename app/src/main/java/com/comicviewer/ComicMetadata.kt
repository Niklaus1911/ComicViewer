package com.comicviewer

import android.content.Context
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.net.toUri
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.util.Locale
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Element

private const val COMIC_DETAILS_PREFS_NAME = "comic_details"
private const val COMIC_DETAILS_KEY = "items"
private const val MAX_COMIC_INFO_BYTES = 1024 * 1024
private const val MAX_COMIC_INFO_SOURCE_BYTES = 128L * 1024L * 1024L
private const val MAX_COMIC_INFO_ARCHIVE_ENTRIES = 10_000
private const val MAX_METADATA_RECORDS = MAX_LIBRARY_COMICS
private const val MAX_METADATA_TEXT_CHARS = 512
private const val MAX_METADATA_SUMMARY_CHARS = 4_096
private const val MAX_METADATA_LIST_ITEMS = 32
private const val XML_ACCESS_EXTERNAL_DTD = "http://javax.xml.XMLConstants/property/accessExternalDTD"
private const val XML_ACCESS_EXTERNAL_SCHEMA = "http://javax.xml.XMLConstants/property/accessExternalSchema"

data class ComicInfoMetadata(
    val title: String? = null,
    val series: String? = null,
    val number: String? = null,
    val volume: Int? = null,
    val summary: String? = null,
    val writers: List<String> = emptyList(),
    val publisher: String? = null,
    val year: Int? = null,
    val language: String? = null,
    val genres: List<String> = emptyList(),
    val pageCount: Int? = null,
)

data class LibraryComicDetails(
    val sourceType: RecentComicSourceType,
    val path: String,
    val pageCount: Int? = null,
    val fileSizeBytes: Long? = null,
    val lastModifiedAt: Long? = null,
    val metadata: ComicInfoMetadata? = null,
    val refreshedAt: Long = 0L,
)

fun loadLibraryComicDetails(context: Context): List<LibraryComicDetails> {
    val stored = context.getSharedPreferences(COMIC_DETAILS_PREFS_NAME, Context.MODE_PRIVATE)
        .getString(COMIC_DETAILS_KEY, null)
        ?: return emptyList()
    return runCatching { decodeLibraryComicDetails(stored) }.getOrDefault(emptyList())
}

fun saveLibraryComicDetails(context: Context, details: List<LibraryComicDetails>) {
    context.getSharedPreferences(COMIC_DETAILS_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(COMIC_DETAILS_KEY, encodeLibraryComicDetails(details.take(MAX_METADATA_RECORDS)))
        .apply()
}

fun detailsFor(
    comic: LibraryComic,
    details: List<LibraryComicDetails>,
): LibraryComicDetails? = details.firstOrNull {
    it.sourceType == comic.sourceType && it.path == comic.path
}

fun upsertLibraryComicDetails(
    details: List<LibraryComicDetails>,
    item: LibraryComicDetails,
): List<LibraryComicDetails> =
    (listOf(item.normalized()) + details.filterNot {
        it.sourceType == item.sourceType && it.path == item.path
    }).sortedByDescending(LibraryComicDetails::refreshedAt)
        .take(MAX_METADATA_RECORDS)

fun pruneLibraryComicDetails(
    details: List<LibraryComicDetails>,
    libraryComics: List<LibraryComic>,
): List<LibraryComicDetails> {
    val keys = libraryComics.mapTo(mutableSetOf()) { "${it.sourceType.storageName}|${it.path}" }
    return details.filter { "${it.sourceType.storageName}|${it.path}" in keys }
}

suspend fun inspectLibraryComic(
    context: Context,
    comic: LibraryComic,
    recentComic: RecentComic?,
    nowMs: Long = System.currentTimeMillis(),
): LibraryComicDetails = withContext(Dispatchers.IO) {
    val coroutineContext = currentCoroutineContext()
    val shouldContinue = { coroutineContext.isActive }
    val sourceFacts = when (comic.sourceType) {
        RecentComicSourceType.LocalArchive -> inspectLocalArchive(File(comic.path), shouldContinue)
        RecentComicSourceType.LocalFolder -> inspectLocalFolder(File(comic.path), shouldContinue)
        RecentComicSourceType.DocumentArchive -> inspectDocumentArchive(context, comic.path, shouldContinue)
        RecentComicSourceType.DocumentFolder -> inspectDocumentFolder(context, comic.path, shouldContinue)
    }
    LibraryComicDetails(
        sourceType = comic.sourceType,
        path = comic.path,
        pageCount = recentComic?.pageCount?.takeIf { it > 0 }
            ?: sourceFacts.metadata?.pageCount
            ?: sourceFacts.pageCount,
        fileSizeBytes = sourceFacts.fileSizeBytes,
        lastModifiedAt = sourceFacts.lastModifiedAt,
        metadata = sourceFacts.metadata,
        refreshedAt = nowMs,
    ).normalized()
}

internal fun parseComicInfoXml(bytes: ByteArray): ComicInfoMetadata? {
    if (bytes.isEmpty() || bytes.size > MAX_COMIC_INFO_BYTES) return null
    if (bytes.containsForbiddenXmlDeclaration()) return null
    val document = runCatching {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
            runCatching { isXIncludeAware = false }
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setAttribute(XML_ACCESS_EXTERNAL_DTD, "") }
            runCatching { setAttribute(XML_ACCESS_EXTERNAL_SCHEMA, "") }
        }
        factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }.getOrNull() ?: return null
    val root = document.documentElement ?: return null
    if (!root.tagName.equals("ComicInfo", ignoreCase = true)) return null

    fun text(tag: String, maxChars: Int = MAX_METADATA_TEXT_CHARS): String? =
        root.firstText(tag)?.trim()?.takeIf(String::isNotEmpty)?.take(maxChars)

    fun list(tag: String): List<String> = text(tag)
        ?.split(',', ';')
        .orEmpty()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinctBy { it.lowercase(Locale.ROOT) }
        .take(MAX_METADATA_LIST_ITEMS)

    return ComicInfoMetadata(
        title = text("Title"),
        series = text("Series"),
        number = text("Number", 64),
        volume = text("Volume", 16)?.toIntOrNull()?.takeIf { it in 1..10_000 },
        summary = text("Summary", MAX_METADATA_SUMMARY_CHARS),
        writers = list("Writer"),
        publisher = text("Publisher"),
        year = text("Year", 8)?.toIntOrNull()?.takeIf { it in 1..3_000 },
        language = text("LanguageISO") ?: text("Language"),
        genres = list("Genre"),
        pageCount = text("PageCount", 16)?.toIntOrNull()?.takeIf { it in 1..MAX_COMIC_INFO_ARCHIVE_ENTRIES },
    ).normalized().takeUnless(ComicInfoMetadata::isEmpty)
}

private fun ByteArray.containsForbiddenXmlDeclaration(): Boolean {
    val candidateCharsets = buildList {
        add(Charsets.UTF_8)
        add(Charsets.UTF_16LE)
        add(Charsets.UTF_16BE)
        listOf("UTF-32LE", "UTF-32BE").forEach { name ->
            runCatching { Charset.forName(name) }.getOrNull()?.let(::add)
        }
    }
    return candidateCharsets.any { charset ->
        val text = toString(charset)
        text.contains("<!DOCTYPE", ignoreCase = true) || text.contains("<!ENTITY", ignoreCase = true)
    }
}

private fun inspectLocalArchive(
    file: File,
    shouldContinue: () -> Boolean,
): SourceFacts {
    if (!file.isFile || !file.canRead()) return SourceFacts()
    val archiveFacts = runCatching {
        ZipFile(file).use { zip ->
            val entries = zip.entries()
            var visited = 0
            var pageCount = 0
            var tooManyEntries = false
            var metadata: ComicInfoMetadata? = null
            while (entries.hasMoreElements()) {
                if (!shouldContinue()) throw CancellationException()
                val entry = entries.nextElement()
                visited += 1
                if (visited > MAX_COMIC_INFO_ARCHIVE_ENTRIES) {
                    tooManyEntries = true
                    break
                }
                val name = entry.name.replace('\\', '/')
                if (entry.isDirectory || isHiddenComicPath(name)) continue
                if (name.substringAfterLast('/').equals("ComicInfo.xml", ignoreCase = true)) {
                    if (metadata == null && entry.size in 1..MAX_COMIC_INFO_BYTES.toLong()) {
                        metadata = runCatching {
                            zip.getInputStream(entry).use(::readComicInfoBounded)?.let(::parseComicInfoXml)
                        }.getOrNull()
                    }
                } else {
                    val imageFormat = runCatching {
                        zip.getInputStream(entry).use { input -> detectImageFormat(readImageHeader(input)) }
                    }.getOrNull()
                    if (imageFormat != null) pageCount += 1
                }
            }
            SourceFacts(
                pageCount = pageCount.takeIf { it > 0 && !tooManyEntries },
                metadata = metadata,
            )
        }
    }.getOrElse { throwable ->
        if (throwable is CancellationException) throw throwable
        SourceFacts()
    }
    return archiveFacts.copy(
        fileSizeBytes = file.length().takeIf { it >= 0L },
        lastModifiedAt = file.lastModified().takeIf { it > 0L },
    )
}

private fun inspectLocalFolder(
    folder: File,
    shouldContinue: () -> Boolean,
): SourceFacts {
    if (!folder.isDirectory || !folder.canRead()) return SourceFacts()
    val children = runCatching { folder.listFiles().orEmpty().toList() }.getOrDefault(emptyList())
    val metadataFile = children.firstOrNull {
        it.isFile && it.name.equals("ComicInfo.xml", ignoreCase = true) && it.length() in 1..MAX_COMIC_INFO_BYTES.toLong()
    }
    val metadata = metadataFile?.let { file ->
        runCatching {
            file.inputStream().use(::readComicInfoBounded)?.let(::parseComicInfoXml)
        }.getOrNull()
    }
    var validPageCount = 0
    children.forEach { child ->
        if (!shouldContinue()) throw CancellationException()
        if (
            child.isFile &&
            !isHiddenComicPath(child.name) &&
            runCatching { detectImageInfo(child) != null }.getOrDefault(false)
        ) {
            validPageCount += 1
        }
    }
    val pageCount = validPageCount.takeIf { it > 0 }
    return SourceFacts(
        pageCount = pageCount,
        lastModifiedAt = folder.lastModified().takeIf { it > 0L },
        metadata = metadata,
    )
}

private fun inspectDocumentArchive(
    context: Context,
    path: String,
    shouldContinue: () -> Boolean,
): SourceFacts {
    val uri = runCatching { path.toUri() }.getOrNull() ?: return SourceFacts()
    val archiveFacts = runCatching {
        val source = context.contentResolver.openInputStream(uri) ?: return@runCatching null
        ZipInputStream(MetadataLimitedInputStream(source.buffered(), MAX_COMIC_INFO_SOURCE_BYTES)).use { zip ->
            var visited = 0
            var pageCount = 0
            var tooManyEntries = false
            var metadata: ComicInfoMetadata? = null
            while (true) {
                if (!shouldContinue()) throw CancellationException()
                val entry = zip.nextEntry ?: break
                visited += 1
                if (visited > MAX_COMIC_INFO_ARCHIVE_ENTRIES) {
                    tooManyEntries = true
                    break
                }
                val name = entry.name.replace('\\', '/')
                if (entry.isDirectory || isHiddenComicPath(name)) continue
                if (name.substringAfterLast('/').equals("ComicInfo.xml", true)) {
                    if (metadata == null) {
                        metadata = readComicInfoBounded(zip)?.let(::parseComicInfoXml)
                    }
                } else if (detectImageFormat(readImageHeader(zip)) != null) {
                    pageCount += 1
                }
            }
            SourceFacts(
                pageCount = pageCount.takeIf { it > 0 && !tooManyEntries },
                metadata = metadata,
            )
        }
    }.getOrElse { throwable ->
        if (throwable is CancellationException) throw throwable
        null
    } ?: SourceFacts()
    return archiveFacts.copy(
        fileSizeBytes = context.queryLong(uri, OpenableColumns.SIZE),
        lastModifiedAt = context.queryLong(uri, DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            ?.takeIf { it > 0L },
    )
}

private fun inspectDocumentFolder(
    context: Context,
    path: String,
    shouldContinue: () -> Boolean,
): SourceFacts {
    val uri = runCatching { path.toUri() }.getOrNull() ?: return SourceFacts()
    val children = listDocumentChildren(context, uri)
    val metadataDocument = children.firstOrNull {
        it.isFile && it.displayName.equals("ComicInfo.xml", ignoreCase = true)
    }
    val metadata = metadataDocument?.uri?.let { documentUri ->
        runCatching {
            context.contentResolver.openInputStream(documentUri)?.use(::readComicInfoBounded)?.let(::parseComicInfoXml)
        }.getOrNull()
    }
    var validPageCount = 0
    children.forEach { child ->
        if (!shouldContinue()) throw CancellationException()
        if (child.isFile && isSupportedImageName(child.displayName) && !isHiddenComicPath(child.displayName)) {
            validPageCount += 1
        }
    }
    val pageCount = validPageCount.takeIf { it > 0 }
    return SourceFacts(pageCount = pageCount, metadata = metadata)
}

private fun readComicInfoBounded(input: InputStream): ByteArray? {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        if (read == 0) {
            val value = input.read()
            if (value < 0) break
            if (total >= MAX_COMIC_INFO_BYTES) return null
            output.write(value)
            total += 1
            continue
        }
        if (total > MAX_COMIC_INFO_BYTES - read) return null
        output.write(buffer, 0, read)
        total += read
    }
    return output.toByteArray().takeIf { it.isNotEmpty() }
}

private fun Context.queryLong(uri: android.net.Uri, column: String): Long? = runCatching {
    contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(column)
        if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) cursor.getLong(index) else null
    }
}.getOrNull()?.takeIf { it >= 0L }

private fun Element.firstText(tag: String): String? {
    val nodes = getElementsByTagName(tag)
    if (nodes.length == 0) return null
    return nodes.item(0)?.textContent
}

private fun ComicInfoMetadata.normalized(): ComicInfoMetadata = copy(
    writers = writers.map(String::trim).filter(String::isNotEmpty).distinct().take(MAX_METADATA_LIST_ITEMS),
    genres = genres.map(String::trim).filter(String::isNotEmpty).distinct().take(MAX_METADATA_LIST_ITEMS),
)

private fun ComicInfoMetadata.isEmpty(): Boolean =
    title == null && series == null && number == null && volume == null && summary == null &&
        writers.isEmpty() && publisher == null && year == null && language == null && genres.isEmpty() && pageCount == null

private fun LibraryComicDetails.normalized(): LibraryComicDetails = copy(
    pageCount = pageCount?.takeIf { it in 1..MAX_COMIC_INFO_ARCHIVE_ENTRIES },
    fileSizeBytes = fileSizeBytes?.takeIf { it >= 0L },
    lastModifiedAt = lastModifiedAt?.takeIf { it > 0L },
    metadata = metadata?.normalized()?.takeUnless(ComicInfoMetadata::isEmpty),
    refreshedAt = refreshedAt.coerceAtLeast(0L),
)

private data class SourceFacts(
    val pageCount: Int? = null,
    val fileSizeBytes: Long? = null,
    val lastModifiedAt: Long? = null,
    val metadata: ComicInfoMetadata? = null,
)

private class MetadataLimitedInputStream(
    input: InputStream,
    maxBytes: Long,
) : FilterInputStream(input) {
    private var remaining = maxBytes.coerceAtLeast(0L)

    override fun read(): Int {
        if (remaining == 0L) return -1
        return super.read().also { if (it >= 0) remaining -= 1 }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (remaining == 0L) return -1
        val read = super.read(buffer, offset, minOf(length.toLong(), remaining).toInt())
        if (read > 0) remaining -= read
        return read
    }
}

internal fun encodeLibraryComicDetails(details: List<LibraryComicDetails>): String = JSONArray().apply {
    details.take(MAX_METADATA_RECORDS).forEach { item ->
        put(
            JSONObject()
                .put("sourceType", item.sourceType.storageName)
                .put("path", item.path)
                .put("pageCount", item.pageCount ?: JSONObject.NULL)
                .put("fileSizeBytes", item.fileSizeBytes ?: JSONObject.NULL)
                .put("lastModifiedAt", item.lastModifiedAt ?: JSONObject.NULL)
                .put("refreshedAt", item.refreshedAt)
                .put("metadata", item.metadata?.toJson() ?: JSONObject.NULL),
        )
    }
}.toString()

internal fun decodeLibraryComicDetails(stored: String): List<LibraryComicDetails> {
    val array = JSONArray(stored)
    return (0 until minOf(array.length(), MAX_METADATA_RECORDS)).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val sourceType = RecentComicSourceType.fromStorageName(item.optString("sourceType")) ?: return@mapNotNull null
        val path = item.optString("path").takeIf { value ->
            value.isNotBlank() && value.length <= 4_096
        } ?: return@mapNotNull null
        LibraryComicDetails(
            sourceType = sourceType,
            path = path,
            pageCount = item.optNullableInt("pageCount"),
            fileSizeBytes = item.optNullableLong("fileSizeBytes"),
            lastModifiedAt = item.optNullableLong("lastModifiedAt"),
            metadata = item.optJSONObject("metadata")?.toComicInfo(),
            refreshedAt = item.optLong("refreshedAt", 0L),
        ).normalized()
    }.distinctBy { "${it.sourceType.storageName}|${it.path}" }
}

private fun ComicInfoMetadata.toJson(): JSONObject = JSONObject()
    .put("title", title)
    .put("series", series)
    .put("number", number)
    .put("volume", volume ?: JSONObject.NULL)
    .put("summary", summary)
    .put("writers", JSONArray(writers))
    .put("publisher", publisher)
    .put("year", year ?: JSONObject.NULL)
    .put("language", language)
    .put("genres", JSONArray(genres))
    .put("pageCount", pageCount ?: JSONObject.NULL)

private fun JSONObject.toComicInfo(): ComicInfoMetadata = ComicInfoMetadata(
    title = optBoundedString("title"),
    series = optBoundedString("series"),
    number = optBoundedString("number", 64),
    volume = optNullableInt("volume")?.takeIf { it <= 10_000 },
    summary = optBoundedString("summary", MAX_METADATA_SUMMARY_CHARS),
    writers = optStringList("writers"),
    publisher = optBoundedString("publisher"),
    year = optNullableInt("year")?.takeIf { it <= 3_000 },
    language = optBoundedString("language"),
    genres = optStringList("genres"),
    pageCount = optNullableInt("pageCount")?.takeIf { it <= MAX_COMIC_INFO_ARCHIVE_ENTRIES },
).normalized()

private fun JSONObject.optBoundedString(key: String, max: Int = MAX_METADATA_TEXT_CHARS): String? =
    optString(key).trim().takeIf(String::isNotEmpty)?.take(max)

private fun JSONObject.optStringList(key: String): List<String> {
    val array = optJSONArray(key) ?: return emptyList()
    return (0 until minOf(array.length(), MAX_METADATA_LIST_ITEMS)).mapNotNull { index ->
        array.optString(index).trim().takeIf(String::isNotEmpty)?.take(MAX_METADATA_TEXT_CHARS)
    }.distinct()
}

private fun JSONObject.optNullableInt(key: String): Int? = if (isNull(key)) null else optInt(key).takeIf { it > 0 }
private fun JSONObject.optNullableLong(key: String): Long? = if (isNull(key)) null else optLong(key).takeIf { it >= 0L }
