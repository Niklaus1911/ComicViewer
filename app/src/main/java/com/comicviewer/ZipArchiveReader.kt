package com.comicviewer

import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.SequenceInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import java.util.zip.ZipException
import kotlinx.coroutines.CancellationException

private const val END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50L
private const val END_OF_CENTRAL_DIRECTORY_SIZE = 22
private const val MAX_ARCHIVE_COMMENT_BYTES = 0xFFFF
private const val ZIP64_LOCATOR_SIGNATURE = 0x07064b50L
private const val ZIP64_LOCATOR_SIZE = 20
private const val ZIP64_END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06064b50L
private const val ZIP64_END_OF_CENTRAL_DIRECTORY_SIZE = 56
private const val CENTRAL_HEADER_SIGNATURE = 0x02014b50L
private const val CENTRAL_HEADER_SIZE = 46
private const val LOCAL_HEADER_SIGNATURE = 0x04034b50L
private const val LOCAL_HEADER_SIZE = 30
private const val ZIP64_EXTRA_FIELD_ID = 0x0001
private const val UINT32_MAX = 0xFFFFFFFFL
private const val FLAG_ENCRYPTED = 0x0001
private const val FLAG_UTF8_NAMES = 0x0800
private const val METHOD_STORED = 0
private const val METHOD_DEFLATED = 8
private const val CENTRAL_DIRECTORY_BUFFER_BYTES = 64 * 1024
internal const val ZIP_ENTRY_BUFFER_BYTES = 64 * 1024

class ZipEntryLimitExceededException(val limit: Int) :
    IOException("Archive contains more than $limit entries.")

internal class ZipArchiveEntry(
    val index: Int,
    val name: String,
    val method: Int,
    val flags: Int,
    val compressedSize: Long,
    val size: Long,
    val localHeaderOffset: Long,
) {
    val isDirectory: Boolean
        get() = name.endsWith('/')

    val isEncrypted: Boolean
        get() = flags and FLAG_ENCRYPTED != 0
}

/**
 * Reads a ZIP/CBZ archive through positional reads on [channel], so only the central directory and
 * the entries actually requested are read. Nothing is copied or extracted, which keeps archive size
 * unbounded. Positional reads do not move the channel position, so entries can be streamed
 * concurrently. The caller owns [channel] and closes it.
 */
internal class ZipArchiveReader private constructor(
    private val channel: FileChannel,
    private val archiveStartOffset: Long,
    private val centralDirectoryStart: Long,
    val entries: List<ZipArchiveEntry>,
) {
    fun openEntry(entry: ZipArchiveEntry, bufferSize: Int = ZIP_ENTRY_BUFFER_BYTES): InputStream {
        if (entry.isEncrypted) throw ZipException("Encrypted archive entries are not supported.")
        val headerPosition = archiveStartOffset + entry.localHeaderOffset
        val header = channel.readExactly(headerPosition, LOCAL_HEADER_SIZE)
        if (header.uint32(0) != LOCAL_HEADER_SIGNATURE) throw ZipException("Invalid local entry header.")
        val dataStart = headerPosition + LOCAL_HEADER_SIZE + header.uint16(26) + header.uint16(28)
        if (dataStart > centralDirectoryStart || entry.compressedSize > centralDirectoryStart - dataStart) {
            throw ZipException("Archive entry data is out of bounds.")
        }

        val rawData = ChannelRangeInputStream(channel, dataStart, dataStart + entry.compressedSize)
        val content = when (entry.method) {
            METHOD_STORED -> rawData
            METHOD_DEFLATED -> ZipInflaterInputStream(rawData, bufferSize)
            else -> throw ZipException("Unsupported compression method ${entry.method}.")
        }
        // Ends after the size declared in the central directory, so a lying entry cannot inflate forever.
        return BoundedInputStream(content, entry.size)
    }

    companion object {
        fun open(
            channel: FileChannel,
            maxEntries: Int,
            shouldContinue: () -> Boolean = { true },
        ): ZipArchiveReader {
            val archiveSize = channel.size()
            if (archiveSize < END_OF_CENTRAL_DIRECTORY_SIZE) throw ZipException("Not a ZIP archive.")

            val tailSize = minOf(archiveSize, (END_OF_CENTRAL_DIRECTORY_SIZE + MAX_ARCHIVE_COMMENT_BYTES).toLong())
            val tailStart = archiveSize - tailSize
            val tail = channel.readExactly(tailStart, tailSize.toInt())
            val endOffset = findEndOfCentralDirectory(tail)
                ?: throw ZipException("ZIP end of central directory not found.")

            var entryCount = tail.uint16(endOffset + 10).toLong()
            var centralDirectorySize = tail.uint32(endOffset + 12)
            var centralDirectoryOffset = tail.uint32(endOffset + 16)
            var centralDirectoryEnd = tailStart + endOffset
            val zip64 = readZip64EndOfCentralDirectory(channel, centralDirectoryEnd)
            if (zip64 != null) {
                entryCount = zip64.entryCount
                centralDirectorySize = zip64.centralDirectorySize
                centralDirectoryOffset = zip64.centralDirectoryOffset
                centralDirectoryEnd = zip64.position
            } else if (centralDirectorySize == UINT32_MAX || centralDirectoryOffset == UINT32_MAX) {
                throw ZipException("ZIP64 end of central directory not found.")
            }

            if (
                entryCount < 0L ||
                centralDirectorySize !in 0L..centralDirectoryEnd ||
                centralDirectoryOffset < 0L
            ) {
                throw ZipException("Invalid ZIP central directory.")
            }
            val centralDirectoryStart = centralDirectoryEnd - centralDirectorySize
            // Data prepended to the archive (for example a self-extractor stub) shifts every offset.
            val archiveStartOffset = centralDirectoryStart - centralDirectoryOffset
            if (archiveStartOffset < 0L) throw ZipException("Invalid ZIP central directory offset.")
            if (entryCount > maxEntries) throw ZipEntryLimitExceededException(maxEntries)
            if (entryCount * CENTRAL_HEADER_SIZE > centralDirectorySize) {
                throw ZipException("Truncated ZIP central directory.")
            }

            val entries = ArrayList<ZipArchiveEntry>(entryCount.toInt())
            val directoryInput = BufferedInputStream(
                ChannelRangeInputStream(channel, centralDirectoryStart, centralDirectoryEnd),
                CENTRAL_DIRECTORY_BUFFER_BYTES,
            )
            val fixedHeader = ByteArray(CENTRAL_HEADER_SIZE)
            repeat(entryCount.toInt()) { index ->
                if (!shouldContinue()) throw CancellationException()
                entries += directoryInput.readCentralDirectoryEntry(index, fixedHeader, centralDirectoryOffset)
            }
            return ZipArchiveReader(channel, archiveStartOffset, centralDirectoryStart, entries)
        }
    }
}

private fun InputStream.readCentralDirectoryEntry(
    index: Int,
    fixedHeader: ByteArray,
    centralDirectoryOffset: Long,
): ZipArchiveEntry {
    readFully(fixedHeader)
    if (fixedHeader.uint32(0) != CENTRAL_HEADER_SIGNATURE) throw ZipException("Invalid ZIP central directory entry.")
    val flags = fixedHeader.uint16(8)
    val method = fixedHeader.uint16(10)
    var compressedSize = fixedHeader.uint32(20)
    var size = fixedHeader.uint32(24)
    val nameBytes = ByteArray(fixedHeader.uint16(28)).also { readFully(it) }
    val extra = ByteArray(fixedHeader.uint16(30)).also { readFully(it) }
    skipFully(fixedHeader.uint16(32).toLong())
    var localHeaderOffset = fixedHeader.uint32(42)

    if (size == UINT32_MAX || compressedSize == UINT32_MAX || localHeaderOffset == UINT32_MAX) {
        val zip64Extra = extra.extraField(ZIP64_EXTRA_FIELD_ID) ?: throw ZipException("Missing ZIP64 entry sizes.")
        var position = 0
        if (size == UINT32_MAX) {
            size = zip64Extra.int64(position)
            position += 8
        }
        if (compressedSize == UINT32_MAX) {
            compressedSize = zip64Extra.int64(position)
            position += 8
        }
        if (localHeaderOffset == UINT32_MAX) {
            localHeaderOffset = zip64Extra.int64(position)
        }
    }
    if (size < 0L || compressedSize < 0L || localHeaderOffset !in 0L until centralDirectoryOffset.coerceAtLeast(1L)) {
        throw ZipException("Invalid ZIP entry bounds.")
    }

    return ZipArchiveEntry(
        index = index,
        name = decodeEntryName(nameBytes, flags).replace('\\', '/'),
        method = method,
        flags = flags,
        compressedSize = compressedSize,
        size = size,
        localHeaderOffset = localHeaderOffset,
    )
}

private class Zip64EndOfCentralDirectory(
    val position: Long,
    val entryCount: Long,
    val centralDirectorySize: Long,
    val centralDirectoryOffset: Long,
)

private fun readZip64EndOfCentralDirectory(
    channel: FileChannel,
    endOfCentralDirectoryPosition: Long,
): Zip64EndOfCentralDirectory? {
    val locatorPosition = endOfCentralDirectoryPosition - ZIP64_LOCATOR_SIZE
    if (locatorPosition < 0L) return null
    val locator = channel.readExactly(locatorPosition, ZIP64_LOCATOR_SIZE)
    if (locator.uint32(0) != ZIP64_LOCATOR_SIGNATURE) return null

    // The recorded offset ignores prepended data; the record usually sits right before the locator.
    val candidates = listOf(
        locator.int64(8),
        locatorPosition - ZIP64_END_OF_CENTRAL_DIRECTORY_SIZE,
    ).distinct()
    for (position in candidates) {
        if (position < 0L || position > locatorPosition - ZIP64_END_OF_CENTRAL_DIRECTORY_SIZE) continue
        val record = channel.readExactly(position, ZIP64_END_OF_CENTRAL_DIRECTORY_SIZE)
        if (record.uint32(0) != ZIP64_END_OF_CENTRAL_DIRECTORY_SIGNATURE) continue
        return Zip64EndOfCentralDirectory(
            position = position,
            entryCount = record.int64(32),
            centralDirectorySize = record.int64(40),
            centralDirectoryOffset = record.int64(48),
        )
    }
    throw ZipException("Invalid ZIP64 end of central directory.")
}

internal fun findEndOfCentralDirectory(tail: ByteArray): Int? {
    var fallback: Int? = null
    for (offset in tail.size - END_OF_CENTRAL_DIRECTORY_SIZE downTo 0) {
        if (tail.uint32(offset) != END_OF_CENTRAL_DIRECTORY_SIGNATURE) continue
        val recordEnd = offset + END_OF_CENTRAL_DIRECTORY_SIZE + tail.uint16(offset + 20)
        if (recordEnd == tail.size) return offset
        if (recordEnd < tail.size && fallback == null) fallback = offset
    }
    return fallback
}

private val legacyEntryNameCharset: Charset =
    runCatching { Charset.forName("IBM437") }.getOrDefault(Charsets.ISO_8859_1)

private fun decodeEntryName(bytes: ByteArray, flags: Int): String {
    if (flags and FLAG_UTF8_NAMES != 0) return String(bytes, Charsets.UTF_8)
    // Many archivers write UTF-8 names without setting the flag, so try strict UTF-8 first.
    return try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        String(bytes, legacyEntryNameCharset)
    }
}

private fun ByteArray.extraField(id: Int): ByteArray? {
    var position = 0
    while (position + 4 <= size) {
        val fieldId = uint16(position)
        val fieldSize = uint16(position + 2)
        val dataStart = position + 4
        if (dataStart + fieldSize > size) return null
        if (fieldId == id) return copyOfRange(dataStart, dataStart + fieldSize)
        position = dataStart + fieldSize
    }
    return null
}

private fun ByteArray.uint16(offset: Int): Int =
    (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)

private fun ByteArray.uint32(offset: Int): Long =
    uint16(offset).toLong() or (uint16(offset + 2).toLong() shl 16)

private fun ByteArray.int64(offset: Int): Long {
    if (offset + 8 > size) throw ZipException("Truncated ZIP64 field.")
    return uint32(offset) or (uint32(offset + 4) shl 32)
}

private fun FileChannel.readExactly(position: Long, byteCount: Int): ByteArray {
    val bytes = ByteArray(byteCount)
    val buffer = ByteBuffer.wrap(bytes)
    while (buffer.hasRemaining()) {
        val read = read(buffer, position + buffer.position())
        if (read < 0) throw EOFException("Unexpected end of archive.")
    }
    return bytes
}

private fun InputStream.readFully(bytes: ByteArray) {
    var offset = 0
    while (offset < bytes.size) {
        val read = read(bytes, offset, bytes.size - offset)
        if (read < 0) throw EOFException("Unexpected end of ZIP central directory.")
        offset += read
    }
}

private fun InputStream.skipFully(byteCount: Long) {
    var remaining = byteCount
    while (remaining > 0L) {
        val skipped = skip(remaining)
        if (skipped > 0L) {
            remaining -= skipped
        } else if (read() >= 0) {
            remaining -= 1L
        } else {
            throw EOFException("Unexpected end of ZIP central directory.")
        }
    }
}

/** Streams [start, end) of [channel] with positional reads; closing it leaves the channel open. */
private class ChannelRangeInputStream(
    private val channel: FileChannel,
    private var position: Long,
    private val end: Long,
) : InputStream() {
    override fun read(): Int {
        val single = ByteArray(1)
        return if (read(single, 0, 1) < 0) -1 else single[0].toInt() and 0xFF
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val remaining = end - position
        if (remaining <= 0L) return -1
        val byteBuffer = ByteBuffer.wrap(buffer, offset, minOf(length.toLong(), remaining).toInt())
        var read = 0
        while (read == 0) {
            read = channel.read(byteBuffer, position)
        }
        if (read < 0) throw EOFException("Unexpected end of archive entry.")
        position += read
        return read
    }

    override fun skip(byteCount: Long): Long {
        val skipped = byteCount.coerceIn(0L, (end - position).coerceAtLeast(0L))
        position += skipped
        return skipped
    }

    override fun available(): Int = (end - position).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
}

/**
 * Raw deflate needs one extra dummy byte after the compressed data to finish cleanly, matching what
 * java.util.zip.ZipFile supplies internally.
 */
private class ZipInflaterInputStream(input: InputStream, bufferSize: Int) : InflaterInputStream(
    SequenceInputStream(input, ByteArrayInputStream(byteArrayOf(0))),
    Inflater(true),
    bufferSize,
) {
    private var released = false

    override fun close() {
        if (released) return
        released = true
        try {
            super.close()
        } finally {
            inf.end()
        }
    }
}
