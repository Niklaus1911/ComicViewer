package com.comicviewer

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import java.io.File
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

suspend fun scanLibraryArchives(
    root: File,
    knownArchivePaths: Set<String> = emptySet(),
): LibraryArchiveScanResult =
    withContext(Dispatchers.IO) {
        val coroutineContext = currentCoroutineContext()
        scanLibraryArchivesInDirectory(
            root = root,
            knownArchivePaths = knownArchivePaths,
            shouldContinue = { coroutineContext.isActive },
        )
    }

/**
 * Walks [root] breadth-first for comic archives. Archives whose absolute path is in
 * [knownArchivePaths] are already in the library, so they are reported without being opened again.
 */
internal fun scanLibraryArchivesInDirectory(
    root: File,
    knownArchivePaths: Set<String> = emptySet(),
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
                    (child.absolutePath in knownArchivePaths || archiveContainsSupportedImage(child)) -> {
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
    includeImageFolders: Boolean,
    includePdfs: Boolean = false,
    knownUris: Set<String> = emptySet(),
): LibraryDocumentArchiveScanResult = withContext(Dispatchers.IO) {
    val rootDocument = DocumentFile.fromTreeUri(context, rootUri)
        ?: throw ComicLoadException(ComicLoadError.FolderNotReadable)
    scanLibraryDocumentArchiveTree(
        root = rootDocument.toArchiveDocumentNode(),
        includeImageFolders = includeImageFolders,
        includePdfs = includePdfs,
        knownUris = knownUris,
        // One query per directory: DocumentFile would issue several provider queries per child.
        listChildren = { node -> listDocumentChildren(context, node.uri.toUri()).map(::archiveDocumentNodeFor) },
        archiveContainsSupportedImage = { node -> documentComicFileHasPages(context, node.uri) },
        imageIsSupported = { node ->
            detectDocumentImageInfo(context, node.uri.toUri()) != null
        },
        shouldContinue = { currentCoroutineContext().isActive },
    )
}

/**
 * Walks a document tree breadth-first for comic archives and, when [includeImageFolders] is set,
 * folders of images. PDFs count only with [includePdfs]: unlike ZIPs they cannot be told apart from
 * ordinary documents, so they are only picked up from a folder the user chose for comics. Archives
 * and folders whose URI is in [knownUris] are already in the library, so they are reported without
 * reading their contents again.
 */
internal suspend fun scanLibraryDocumentArchiveTree(
    root: ArchiveDocumentNode,
    includeImageFolders: Boolean,
    listChildren: suspend (ArchiveDocumentNode) -> List<ArchiveDocumentNode>,
    archiveContainsSupportedImage: suspend (ArchiveDocumentNode) -> Boolean,
    imageIsSupported: suspend (ArchiveDocumentNode) -> Boolean = { true },
    includePdfs: Boolean = false,
    knownUris: Set<String> = emptySet(),
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

        if (includeImageFolders) {
            var containsSupportedDirectImage = directory.uri in knownUris
            for (child in children) {
                if (containsSupportedDirectImage) break
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
        }

        for (child in children) {
            if (!shouldContinue()) throw CancellationException()
            when {
                child.isDirectory && !(isRoot && child.name.equals("Android", ignoreCase = true)) -> {
                    pendingDirectories.add(child to false)
                }

                child.isFile &&
                    (isSupportedArchiveName(child.name) || (includePdfs && isPdfComicName(child.name))) &&
                    (child.uri in knownUris || archiveContainsSupportedImage(child)) -> {
                    candidates += LibraryDocumentArchiveScanCandidate(
                        uri = child.uri,
                        displayName = child.name,
                    )
                    val collectedCandidateCount = candidates.size +
                        if (includeImageFolders) folderCandidates.size else 0
                    if (collectedCandidateCount >= maxCandidates.coerceAtLeast(1)) {
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
    val existingArchiveKeys = libraryComics
        .filter { comic -> comic.sourceType == RecentComicSourceType.DocumentArchive }
        .mapTo(mutableSetOf()) { comic -> documentIdentityKey(comic.path) }
    val seenCandidateKeys = mutableSetOf<String>()
    val availableSlots = (maxItems - libraryComics.size).coerceAtLeast(0)

    return candidates
        .asSequence()
        .filter { candidate -> seenCandidateKeys.add(documentIdentityKey(candidate.uri)) }
        .filterNot { candidate -> documentIdentityKey(candidate.uri) in existingArchiveKeys }
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
    val existingFolderKeys = libraryComics
        .filter { comic -> comic.sourceType == RecentComicSourceType.DocumentFolder }
        .mapTo(mutableSetOf()) { comic -> documentIdentityKey(comic.path) }
    val seenCandidateKeys = mutableSetOf<String>()
    val availableSlots = (maxItems - libraryComics.size).coerceAtLeast(0)

    return candidates
        .asSequence()
        .filter { candidate -> seenCandidateKeys.add(documentIdentityKey(candidate.uri)) }
        .filterNot { candidate -> documentIdentityKey(candidate.uri) in existingFolderKeys }
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
        // A PDF named like an archive is still a readable comic.
        fileLooksLikePdf(archiveFile) || openLocalComicArchive(archiveFile).use { archive ->
            archive.reader.containsComicImage(maxVisitedEntries = maxEntries)
        }
    }.getOrDefault(false)

/** Whether a document is a readable comic file of either format, judged by its content, not its name. */
private fun documentComicFileHasPages(
    context: Context,
    uri: String,
    maxEntries: Int = MAX_ARCHIVE_SCAN_ENTRY_COUNT,
): Boolean = runCatching {
    openSeekableDocumentComicFile(context, uri.toUri())?.use { opened ->
        return@runCatching opened.hasComicPages(maxEntries)
    }
    // Stream-only providers: read from the start, within the source limit. A stream-only PDF is
    // accepted on its header, since counting its pages would need a full copy.
    val source = context.contentResolver.openInputStream(uri.toUri())?.buffered() ?: return@runCatching false
    if (source.startsLikePdf()) {
        source.close()
        return@runCatching true
    }
    ZipInputStream(
        BoundedInputStream(
            input = source,
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

/** A child row without a MIME type is unreadable, matching [DocumentFile.canRead]. */
internal fun archiveDocumentNodeFor(entry: ComicDocumentEntry): ArchiveDocumentNode =
    ArchiveDocumentNode(
        uri = entry.uri.toString(),
        name = entry.displayName,
        isDirectory = entry.isDirectory,
        isFile = !entry.mimeType.isNullOrEmpty() && !entry.isDirectory,
        canRead = !entry.mimeType.isNullOrEmpty(),
    )

private fun DocumentFile.toArchiveDocumentNode(): ArchiveDocumentNode =
    ArchiveDocumentNode(
        uri = uri.toString(),
        name = name.orEmpty().ifBlank { uri.lastPathSegment.orEmpty() },
        isDirectory = isDirectory,
        isFile = isFile,
        canRead = canRead(),
    )
