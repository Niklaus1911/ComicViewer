package com.comicviewer

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private const val MAX_ARCHIVE_SCAN_DIRECTORIES = 20_000
private const val MAX_ARCHIVE_SCAN_ENTRY_COUNT = 10_000
private const val MAX_ARCHIVE_SCAN_SOURCE_BYTES = 64L * 1024L * 1024L
private const val MAX_ARCHIVE_SCAN_CANDIDATES = MAX_LIBRARY_COMICS + 1

sealed interface LibraryAutoScanState {
    data object Idle : LibraryAutoScanState
    data object Scanning : LibraryAutoScanState
    data class Complete(
        val foundCount: Int,
        val addedCount: Int,
        val skippedExistingCount: Int,
        val scannedDirectoryCount: Int,
        val libraryLimitReached: Boolean,
        val directoryLimitReached: Boolean,
    ) : LibraryAutoScanState

    data class Failed(val message: String) : LibraryAutoScanState
}

data class LibraryArchiveScanCandidate(
    val archiveFile: File,
)

data class LibraryArchiveScanResult(
    val candidates: List<LibraryArchiveScanCandidate>,
    val scannedDirectoryCount: Int,
    val directoryLimitReached: Boolean,
    val candidateLimitReached: Boolean = false,
)

suspend fun scanLibraryArchives(root: File): LibraryArchiveScanResult =
    withContext(Dispatchers.IO) {
        val coroutineContext = currentCoroutineContext()
        scanLibraryArchivesInDirectory(
            root = root,
            shouldContinue = { coroutineContext.isActive },
        )
    }

internal fun scanLibraryArchivesInDirectory(
    root: File,
    maxDirectories: Int = MAX_ARCHIVE_SCAN_DIRECTORIES,
    maxCandidates: Int = MAX_ARCHIVE_SCAN_CANDIDATES,
    shouldContinue: () -> Boolean = { true },
): LibraryArchiveScanResult {
    val pendingDirectories = ArrayDeque<File>()
    val visitedDirectoryPaths = mutableSetOf<String>()
    val candidates = mutableListOf<LibraryArchiveScanCandidate>()
    val rootCanonicalPath = root.canonicalPathOrAbsolute()
    var scannedDirectoryCount = 0
    var directoryLimitReached = false
    var candidateLimitReached = false

    if (root.isDirectory && root.canRead()) {
        pendingDirectories.add(root)
    }

    scanLoop@ while (pendingDirectories.isNotEmpty()) {
        if (!shouldContinue()) throw CancellationException()
        if (scannedDirectoryCount >= maxDirectories) {
            directoryLimitReached = true
            break
        }

        val directory = pendingDirectories.removeFirst()
        val canonicalPath = directory.canonicalPathOrAbsolute()
        if (
            !visitedDirectoryPaths.add(canonicalPath) ||
            shouldSkipArchiveScanDirectory(
                directory = directory,
                rootCanonicalPath = rootCanonicalPath,
            )
        ) {
            continue
        }

        scannedDirectoryCount += 1
        val children = directory.safeListFiles()
            .filter { file -> file.canRead() && !isHiddenComicPath(file.name) }
            .sortedWith { left, right -> compareComicPageNames(left.name, right.name) }
        if (!shouldContinue()) throw CancellationException()

        for (child in children) {
            if (!shouldContinue()) throw CancellationException()
            when {
                child.isDirectory -> pendingDirectories.add(child)
                child.isFile &&
                    isSupportedArchiveName(child.name) &&
                    archiveContainsSupportedImage(child) -> {
                    candidates += LibraryArchiveScanCandidate(archiveFile = child.absoluteFile)
                    if (candidates.size >= maxCandidates.coerceAtLeast(1)) {
                        candidateLimitReached = true
                        break@scanLoop
                    }
                }
            }
        }
    }

    return LibraryArchiveScanResult(
        candidates = candidates.distinctBy { candidate -> candidate.archiveFile.absolutePath },
        scannedDirectoryCount = scannedDirectoryCount,
        directoryLimitReached = directoryLimitReached,
        candidateLimitReached = candidateLimitReached,
    )
}

data class ArchiveDocumentNode(
    val uri: String,
    val name: String,
    val isDirectory: Boolean,
    val isFile: Boolean,
    val canRead: Boolean,
)

data class LibraryDocumentArchiveScanCandidate(
    val uri: String,
    val displayName: String,
)

data class LibraryDocumentFolderScanCandidate(
    val uri: String,
    val displayName: String,
)

data class LibraryDocumentArchiveScanResult(
    val candidates: List<LibraryDocumentArchiveScanCandidate>,
    val folderCandidates: List<LibraryDocumentFolderScanCandidate>,
    val scannedDirectoryCount: Int,
    val directoryLimitReached: Boolean,
    val candidateLimitReached: Boolean,
)

suspend fun scanLibraryDocumentArchives(
    context: Context,
    rootUri: Uri,
): LibraryDocumentArchiveScanResult = withContext(Dispatchers.IO) {
    val rootDocument = DocumentFile.fromTreeUri(context, rootUri)
        ?: throw IllegalArgumentException("Storage folder is not readable")
    val documentsByUri = mutableMapOf(rootDocument.uri.toString() to rootDocument)
    scanLibraryDocumentArchiveTree(
        root = rootDocument.toArchiveDocumentNode(),
        listChildren = { node ->
            documentsByUri[node.uri]
                ?.safeListFiles()
                .orEmpty()
                .map { document ->
                    documentsByUri[document.uri.toString()] = document
                    document.toArchiveDocumentNode()
                }
        },
        archiveContainsSupportedImage = { node ->
            documentArchiveContainsSupportedImage(context, node.uri)
        },
        imageIsSupported = { node ->
            detectDocumentImageInfo(context, node.uri.toUri()) != null
        },
        shouldContinue = { currentCoroutineContext().isActive },
    )
}

internal suspend fun scanLibraryDocumentArchiveTree(
    root: ArchiveDocumentNode,
    listChildren: suspend (ArchiveDocumentNode) -> List<ArchiveDocumentNode>,
    archiveContainsSupportedImage: suspend (ArchiveDocumentNode) -> Boolean,
    imageIsSupported: suspend (ArchiveDocumentNode) -> Boolean = { true },
    maxDirectories: Int = MAX_ARCHIVE_SCAN_DIRECTORIES,
    maxCandidates: Int = MAX_ARCHIVE_SCAN_CANDIDATES,
    shouldContinue: suspend () -> Boolean = { true },
): LibraryDocumentArchiveScanResult {
    val pendingDirectories = ArrayDeque<Pair<ArchiveDocumentNode, Boolean>>()
    val visitedDirectoryUris = mutableSetOf<String>()
    val candidates = mutableListOf<LibraryDocumentArchiveScanCandidate>()
    val folderCandidates = mutableListOf<LibraryDocumentFolderScanCandidate>()
    var scannedDirectoryCount = 0
    var directoryLimitReached = false
    var candidateLimitReached = false

    if (root.isDirectory && root.canRead) pendingDirectories.add(root to true)

    scanLoop@ while (pendingDirectories.isNotEmpty()) {
        if (!shouldContinue()) throw CancellationException()
        if (scannedDirectoryCount >= maxDirectories) {
            directoryLimitReached = true
            break
        }

        val (directory, isRoot) = pendingDirectories.removeFirst()
        if (!visitedDirectoryUris.add(directory.uri)) continue
        scannedDirectoryCount += 1
        val children = try {
            listChildren(directory)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            emptyList()
        }
            .filter { child -> child.canRead && !isHiddenComicPath(child.name) }
            .sortedWith { left, right -> compareComicPageNames(left.name, right.name) }
        if (!shouldContinue()) throw CancellationException()

        var containsSupportedDirectImage = false
        for (child in children) {
            if (!shouldContinue()) throw CancellationException()
            if (
                child.isFile &&
                !isHiddenComicPath(child.name) &&
                isSupportedImageName(child.name)
            ) {
                containsSupportedDirectImage = try {
                    imageIsSupported(child)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    false
                }
                if (containsSupportedDirectImage) break
            }
        }

        if (containsSupportedDirectImage) {
            folderCandidates += LibraryDocumentFolderScanCandidate(
                uri = directory.uri,
                displayName = directory.name,
            )
            if (candidates.size + folderCandidates.size >= maxCandidates.coerceAtLeast(1)) {
                candidateLimitReached = true
                break@scanLoop
            }
        }

        for (child in children) {
            if (!shouldContinue()) throw CancellationException()
            when {
                child.isDirectory && !(isRoot && child.name.equals("Android", ignoreCase = true)) -> {
                    pendingDirectories.add(child to false)
                }

                child.isFile &&
                    isSupportedArchiveName(child.name) &&
                    archiveContainsSupportedImage(child) -> {
                    candidates += LibraryDocumentArchiveScanCandidate(
                        uri = child.uri,
                        displayName = child.name,
                    )
                    if (candidates.size + folderCandidates.size >= maxCandidates.coerceAtLeast(1)) {
                        candidateLimitReached = true
                        break@scanLoop
                    }
                }
            }
        }
    }

    return LibraryDocumentArchiveScanResult(
        candidates = candidates.distinctBy(LibraryDocumentArchiveScanCandidate::uri),
        folderCandidates = folderCandidates.distinctBy(LibraryDocumentFolderScanCandidate::uri),
        scannedDirectoryCount = scannedDirectoryCount,
        directoryLimitReached = directoryLimitReached,
        candidateLimitReached = candidateLimitReached,
    )
}

fun newLibraryComicsForArchiveScan(
    libraryComics: List<LibraryComic>,
    candidates: List<LibraryArchiveScanCandidate>,
    addedAt: Long,
    maxItems: Int = MAX_LIBRARY_COMICS,
): List<LibraryComic> {
    val existingArchivePaths = libraryComics
        .filter { comic -> comic.sourceType == RecentComicSourceType.LocalArchive }
        .mapTo(mutableSetOf()) { comic -> comic.path }
    val seenCandidatePaths = mutableSetOf<String>()
    val availableSlots = (maxItems - libraryComics.size).coerceAtLeast(0)

    return candidates
        .asSequence()
        .map { candidate -> candidate.archiveFile.absoluteFile }
        .filter { archiveFile -> seenCandidatePaths.add(archiveFile.absolutePath) }
        .filterNot { archiveFile -> archiveFile.absolutePath in existingArchivePaths }
        .take(availableSlots)
        .mapIndexed { index, archiveFile ->
            libraryComicForArchive(
                file = archiveFile,
                addedAt = addedAt + index,
            )
        }
        .toList()
}

fun newLibraryComicsForDocumentArchiveScan(
    libraryComics: List<LibraryComic>,
    candidates: List<LibraryDocumentArchiveScanCandidate>,
    addedAt: Long,
    maxItems: Int = MAX_LIBRARY_COMICS,
): List<LibraryComic> {
    val existingArchiveUris = libraryComics
        .filter { comic -> comic.sourceType == RecentComicSourceType.DocumentArchive }
        .mapTo(mutableSetOf()) { comic -> comic.path }
    val seenCandidateUris = mutableSetOf<String>()
    val availableSlots = (maxItems - libraryComics.size).coerceAtLeast(0)

    return candidates
        .asSequence()
        .filter { candidate -> seenCandidateUris.add(candidate.uri) }
        .filterNot { candidate -> candidate.uri in existingArchiveUris }
        .take(availableSlots)
        .mapIndexed { index, candidate ->
            libraryComicForDocumentArchive(
                uri = candidate.uri,
                displayName = candidate.displayName,
                addedAt = addedAt + index,
            )
        }
        .toList()
}

fun newLibraryComicsForDocumentFolderScan(
    libraryComics: List<LibraryComic>,
    candidates: List<LibraryDocumentFolderScanCandidate>,
    addedAt: Long,
    maxItems: Int = MAX_LIBRARY_COMICS,
): List<LibraryComic> {
    val existingFolderUris = libraryComics
        .filter { comic -> comic.sourceType == RecentComicSourceType.DocumentFolder }
        .mapTo(mutableSetOf()) { comic -> comic.path }
    val seenCandidateUris = mutableSetOf<String>()
    val availableSlots = (maxItems - libraryComics.size).coerceAtLeast(0)

    return candidates
        .asSequence()
        .filter { candidate -> seenCandidateUris.add(candidate.uri) }
        .filterNot { candidate -> candidate.uri in existingFolderUris }
        .take(availableSlots)
        .mapIndexed { index, candidate ->
            libraryComicForDocumentFolder(
                uri = candidate.uri,
                displayName = candidate.displayName,
                addedAt = addedAt + index,
            )
        }
        .toList()
}

internal fun archiveContainsSupportedImage(
    archiveFile: File,
    maxEntries: Int = MAX_ARCHIVE_SCAN_ENTRY_COUNT,
): Boolean =
    runCatching {
        var scannedEntries = 0
        ZipFile(archiveFile).use { zipFile ->
            val entries = zipFile.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val entryName = entry.name.replace('\\', '/')
                scannedEntries += 1
                if (scannedEntries > maxEntries) return@runCatching false
                if (entry.isDirectory || isHiddenComicPath(entryName)) continue

                val format = zipFile.getInputStream(entry).use { input ->
                    detectImageFormat(readImageHeader(input))
                }
                if (format != null) return@runCatching true
            }
        }
        false
    }.getOrDefault(false)

private fun documentArchiveContainsSupportedImage(
    context: Context,
    uri: String,
    maxEntries: Int = MAX_ARCHIVE_SCAN_ENTRY_COUNT,
): Boolean = runCatching {
    val source = context.contentResolver.openInputStream(uri.toUri()) ?: return@runCatching false
    ZipInputStream(
        ScanLimitedInputStream(
            inputStream = source.buffered(),
            maxBytes = MAX_ARCHIVE_SCAN_SOURCE_BYTES,
        ),
    ).use { archive ->
        var scannedEntries = 0
        while (true) {
            val entry = archive.nextEntry ?: break
            val entryName = entry.name.replace('\\', '/')
            scannedEntries += 1
            if (scannedEntries > maxEntries) return@runCatching false
            if (entry.isDirectory || isHiddenComicPath(entryName)) continue
            if (detectImageFormat(readImageHeader(archive)) != null) return@runCatching true
        }
    }
    false
}.getOrDefault(false)

private fun shouldSkipArchiveScanDirectory(
    directory: File,
    rootCanonicalPath: String,
): Boolean {
    if (!directory.isDirectory || !directory.canRead()) return true
    if (isHiddenComicPath(directory.name)) return true
    val parentPath = directory.parentFile?.canonicalPathOrAbsolute() ?: return false
    return directory.name.equals("Android", ignoreCase = true) && parentPath == rootCanonicalPath
}

private fun File.canonicalPathOrAbsolute(): String =
    runCatching { canonicalPath }.getOrDefault(absolutePath)

private fun File.safeListFiles(): List<File> =
    runCatching { listFiles().orEmpty().toList() }.getOrDefault(emptyList())

private fun DocumentFile.safeListFiles(): List<DocumentFile> =
    runCatching { listFiles().toList() }.getOrDefault(emptyList())

private fun DocumentFile.toArchiveDocumentNode(): ArchiveDocumentNode =
    ArchiveDocumentNode(
        uri = uri.toString(),
        name = name.orEmpty().ifBlank { uri.lastPathSegment.orEmpty() },
        isDirectory = isDirectory,
        isFile = isFile,
        canRead = canRead(),
    )

private class ScanLimitedInputStream(
    inputStream: InputStream,
    maxBytes: Long,
) : FilterInputStream(inputStream) {
    private var remainingBytes = maxBytes.coerceAtLeast(0L)

    override fun read(): Int {
        if (remainingBytes == 0L) return -1
        val value = super.read()
        if (value >= 0) remainingBytes -= 1
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (remainingBytes == 0L) return -1
        val read = super.read(buffer, offset, length.coerceAtMost(remainingBytes.toInt()))
        if (read > 0) remainingBytes -= read
        return read
    }
}
