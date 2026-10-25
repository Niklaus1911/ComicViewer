package com.comicviewer

import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal fun sampleRecentComic(
    title: String = "Comic",
    path: String = "/storage/emulated/0/Comic.cbz",
    sourceType: RecentComicSourceType = RecentComicSourceType.LocalArchive,
    lastPageIndex: Int = 0,
    pageCount: Int = 10,
    lastOpenedAt: Long = 0L,
): RecentComic =
    RecentComic(
        sourceType = sourceType,
        path = path,
        displayName = path.substringAfterLast('/'),
        title = title,
        lastPageIndex = lastPageIndex,
        pageCount = pageCount,
        lastOpenedAt = lastOpenedAt,
    )

internal fun sampleLibraryComic(
    title: String = "Comic",
    path: String = "/storage/emulated/0/Comic.cbz",
    sourceType: RecentComicSourceType = RecentComicSourceType.LocalArchive,
    addedAt: Long = 0L,
): LibraryComic =
    LibraryComic(
        sourceType = sourceType,
        path = path,
        displayName = path.substringAfterLast('/'),
        title = title,
        addedAt = addedAt,
    )

internal fun sampleJpegHeader(): ByteArray =
    byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00, 0x00, 0x00)

internal fun writeZipArchive(
    file: File,
    entries: Map<String, ByteArray>,
    storedNames: Set<String> = emptySet(),
    comment: String? = null,
) {
    file.parentFile?.mkdirs()
    ZipOutputStream(file.outputStream()).use { output ->
        comment?.let(output::setComment)
        entries.forEach { (name, bytes) ->
            val entry = ZipEntry(name)
            if (name in storedNames) {
                entry.method = ZipEntry.STORED
                entry.size = bytes.size.toLong()
                entry.crc = CRC32().apply { update(bytes) }.value
            }
            output.putNextEntry(entry)
            output.write(bytes)
            output.closeEntry()
        }
    }
}
