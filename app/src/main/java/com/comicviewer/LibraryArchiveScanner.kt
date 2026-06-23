package com.comicviewer

import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_ARCHIVE_SCAN_DIRECTORIES = 20_000
private const val MAX_ARCHIVE_SCAN_ENTRY_COUNT = 10_000

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
)

suspend fun scanLibraryArchives(root: File): LibraryArchiveScanResult =
    withContext(Dispatchers.IO) {
        scanLibraryArchivesInDirectory(root)
    }

internal fun scanLibraryArchivesInDirectory(
    root: File,
    maxDirectories: Int = MAX_ARCHIVE_SCAN_DIRECTORIES,
): LibraryArchiveScanResult {
    val pendingDirectories = ArrayDeque<File>()
    val visitedDirectoryPaths = mutableSetOf<String>()
    val candidates = mutableListOf<LibraryArchiveScanCandidate>()
    val rootCanonicalPath = root.canonicalPathOrAbsolute()
    var scannedDirectoryCount = 0
    var directoryLimitReached = false

    if (root.isDirectory && root.canRead()) {
        pendingDirectories.add(root)
    }

    while (pendingDirectories.isNotEmpty()) {
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

        children.forEach { child ->
            when {
                child.isDirectory -> pendingDirectories.add(child)
                child.isFile &&
                    isSupportedArchiveName(child.name) &&
                    archiveContainsSupportedImage(child) -> {
                    candidates += LibraryArchiveScanCandidate(archiveFile = child.absoluteFile)
                }
            }
        }
    }

    return LibraryArchiveScanResult(
        candidates = candidates.distinctBy { candidate -> candidate.archiveFile.absolutePath },
        scannedDirectoryCount = scannedDirectoryCount,
        directoryLimitReached = directoryLimitReached,
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
                if (entry.isDirectory || isHiddenComicPath(entryName)) continue

                scannedEntries += 1
                if (scannedEntries > maxEntries) return@runCatching false

                val format = zipFile.getInputStream(entry).use { input ->
                    detectImageFormat(readImageHeader(input))
                }
                if (format != null) return@runCatching true
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
