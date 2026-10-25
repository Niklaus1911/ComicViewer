package com.comicviewer.ui.app

import android.content.Context
import android.net.Uri
import com.comicviewer.GrantedStorageAccess
import com.comicviewer.LibraryComic
import com.comicviewer.RecentComicSourceType
import com.comicviewer.newLibraryComicsForArchiveScan
import com.comicviewer.newLibraryComicsForDocumentArchiveScan
import com.comicviewer.newLibraryComicsForDocumentFolderScan
import com.comicviewer.documentIdentityKey
import com.comicviewer.scanLibraryArchives
import com.comicviewer.scanLibraryDocumentArchives
import java.io.File

/** What one library scan found, and which of it is new. */
internal data class LibraryScanSummary(
    val foundCount: Int,
    val skippedExistingCount: Int,
    val scannedDirectoryCount: Int,
    val libraryLimitReached: Boolean,
    val directoryLimitReached: Boolean,
    val newComics: List<LibraryComic>,
)

/**
 * Scans a chosen import folder ([rootUri]: archives, PDFs and image folders) or, without one, the
 * storage the app was granted (archives only), and returns the comics not yet in the library.
 * [libraryComics] is read again after the scan, so comics added meanwhile are not added twice.
 */
internal suspend fun scanForNewLibraryComics(
    context: Context,
    storageAccess: GrantedStorageAccess,
    rootUri: Uri?,
    libraryComics: () -> List<LibraryComic>,
): LibraryScanSummary = when {
    rootUri != null -> {
        val existingArchiveUris = libraryComics()
            .filter { comic -> comic.sourceType == RecentComicSourceType.DocumentArchive }
            .mapTo(mutableSetOf()) { comic -> comic.path }
        val existingFolderUris = libraryComics()
            .filter { comic -> comic.sourceType == RecentComicSourceType.DocumentFolder }
            .mapTo(mutableSetOf()) { comic -> comic.path }
        val scanResult = scanLibraryDocumentArchives(
            context = context,
            rootUri = rootUri,
            includeImageFolders = true,
            includePdfs = true,
            knownUris = existingArchiveUris + existingFolderUris,
        )
        val existingArchiveKeys = existingArchiveUris.mapTo(mutableSetOf(), ::documentIdentityKey)
        val existingFolderKeys = existingFolderUris.mapTo(mutableSetOf(), ::documentIdentityKey)
        val newArchiveCandidateCount = scanResult.candidates
            .map { candidate -> documentIdentityKey(candidate.uri) }
            .distinct()
            .count { key -> key !in existingArchiveKeys }
        val newFolderCandidateCount = scanResult.folderCandidates
            .map { candidate -> documentIdentityKey(candidate.uri) }
            .distinct()
            .count { key -> key !in existingFolderKeys }
        val addedAt = System.currentTimeMillis()
        val newArchives = newLibraryComicsForDocumentArchiveScan(
            libraryComics = libraryComics(),
            candidates = scanResult.candidates,
            addedAt = addedAt,
        )
        val newFolders = newLibraryComicsForDocumentFolderScan(
            libraryComics = libraryComics() + newArchives,
            candidates = scanResult.folderCandidates,
            addedAt = addedAt + newArchives.size,
        )
        val newComics = newArchives + newFolders
        val foundCount = scanResult.candidates.size + scanResult.folderCandidates.size
        val newCandidateCount = newArchiveCandidateCount + newFolderCandidateCount
        LibraryScanSummary(
            foundCount = foundCount,
            skippedExistingCount = foundCount - newCandidateCount,
            scannedDirectoryCount = scanResult.scannedDirectoryCount,
            libraryLimitReached = scanResult.candidateLimitReached || newCandidateCount > newComics.size,
            directoryLimitReached = scanResult.directoryLimitReached,
            newComics = newComics,
        )
    }

    storageAccess is GrantedStorageAccess.RawFiles -> {
        val access = storageAccess
        val existingPaths = libraryComics()
            .filter { comic -> comic.sourceType == RecentComicSourceType.LocalArchive }
            .mapTo(mutableSetOf()) { comic -> comic.path }
        val scanResult = scanLibraryArchives(
            root = File(access.rootPath),
            knownArchivePaths = existingPaths,
        )
        val newCandidateCount = scanResult.candidates
            .map { candidate -> candidate.archiveFile.absolutePath }
            .distinct()
            .count { path -> path !in existingPaths }
        val newComics = newLibraryComicsForArchiveScan(
            libraryComics = libraryComics(),
            candidates = scanResult.candidates,
            addedAt = System.currentTimeMillis(),
        )
        LibraryScanSummary(
            foundCount = scanResult.candidates.size,
            skippedExistingCount = scanResult.candidates.size - newCandidateCount,
            scannedDirectoryCount = scanResult.scannedDirectoryCount,
            libraryLimitReached = scanResult.candidateLimitReached || newCandidateCount > newComics.size,
            directoryLimitReached = scanResult.directoryLimitReached,
            newComics = newComics,
        )
    }

    storageAccess is GrantedStorageAccess.DocumentTree -> {
        val access = storageAccess
        val existingUris = libraryComics()
            .filter { comic -> comic.sourceType == RecentComicSourceType.DocumentArchive }
            .mapTo(mutableSetOf()) { comic -> comic.path }
        val scanResult = scanLibraryDocumentArchives(
            context = context,
            rootUri = access.uri,
            includeImageFolders = false,
            knownUris = existingUris,
        )
        val existingKeys = existingUris.mapTo(mutableSetOf(), ::documentIdentityKey)
        val newCandidateCount = scanResult.candidates
            .map { candidate -> documentIdentityKey(candidate.uri) }
            .distinct()
            .count { key -> key !in existingKeys }
        val newComics = newLibraryComicsForDocumentArchiveScan(
            libraryComics = libraryComics(),
            candidates = scanResult.candidates,
            addedAt = System.currentTimeMillis(),
        )
        LibraryScanSummary(
            foundCount = scanResult.candidates.size,
            skippedExistingCount = scanResult.candidates.size - newCandidateCount,
            scannedDirectoryCount = scanResult.scannedDirectoryCount,
            libraryLimitReached = scanResult.candidateLimitReached || newCandidateCount > newComics.size,
            directoryLimitReached = scanResult.directoryLimitReached,
            newComics = newComics,
        )
    }

    else -> error("Unsupported storage access")
}
