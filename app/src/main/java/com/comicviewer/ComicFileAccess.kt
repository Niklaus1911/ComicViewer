package com.comicviewer

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.File

private const val MAX_OPEN_COMIC_FILES = 2

/**
 * A comic file opened for reading, as whichever format its first bytes show: a ZIP-based archive
 * (CBZ/ZIP) or a PDF. Every reader of comic files goes through these openers, so the format is
 * decided in one place and a picked document is opened only once.
 */
internal sealed interface OpenedComicFile : Closeable {
    val isOpen: Boolean

    class Zip(val archive: OpenedComicArchive) : OpenedComicFile {
        override val isOpen: Boolean
            get() = archive.isOpen

        override fun close() = archive.close()
    }

    class Pdf(val pdf: OpenedComicPdf) : OpenedComicFile {
        override val isOpen: Boolean = true

        override fun close() = pdf.close()
    }
}

/** Opens a local comic file. Local opens are cheap, so the format is checked with a plain read first. */
internal fun openLocalComicFile(
    file: File,
    maxEntries: Int = MAX_COMIC_ARCHIVE_ENTRIES,
    shouldContinue: () -> Boolean = { true },
): OpenedComicFile =
    if (fileLooksLikePdf(file)) {
        OpenedComicFile.Pdf(openComicPdf(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)))
    } else {
        OpenedComicFile.Zip(openLocalComicArchive(file, maxEntries, shouldContinue))
    }

/**
 * Opens a picked document through a single file descriptor, reading its first bytes in place to
 * decide the format. Returns null when the provider only offers a one-way stream.
 */
internal fun openSeekableDocumentComicFile(
    context: Context,
    uri: Uri,
    maxEntries: Int = MAX_COMIC_ARCHIVE_ENTRIES,
    shouldContinue: () -> Boolean = { true },
): OpenedComicFile? {
    val descriptor = openSeekableDocumentDescriptor(context, uri) ?: return null
    val isPdf = try {
        descriptor.looksLikePdf()
    } catch (throwable: Throwable) {
        descriptor.close()
        throw throwable
    }
    return if (isPdf) {
        OpenedComicFile.Pdf(openComicPdf(descriptor))
    } else {
        OpenedComicFile.Zip(openComicArchive(descriptor, maxEntries, shouldContinue))
    }
}

private fun openComicFileForReading(
    context: Context,
    location: ComicArchiveLocation,
    shouldContinue: () -> Boolean,
): OpenedComicFile = when (location) {
    is ComicArchiveLocation.LocalFile -> openLocalComicFile(File(location.path), shouldContinue = shouldContinue)
    is ComicArchiveLocation.Document ->
        openSeekableDocumentComicFile(context, location.uri, shouldContinue = shouldContinue)
            ?: openLocalComicFile(
                file = copyDocumentToCache(context, location.uri, shouldContinue),
                shouldContinue = shouldContinue,
            )
}

/** The reader's open comic files, shared by the archive and PDF page fetchers. */
internal object ComicFileSessions : ComicSourceSessions<OpenedComicFile>(
    maxOpenSources = MAX_OPEN_COMIC_FILES,
    openSource = ::openComicFileForReading,
    isUsable = OpenedComicFile::isOpen,
)

/** Image URIs for the first [maxPages] pages, for preview strips. */
internal fun OpenedComicFile.previewPageUris(location: ComicArchiveLocation, maxPages: Int): List<Uri> = when (this) {
    is OpenedComicFile.Zip -> archive.reader.firstComicPages(maxPages).map { comicArchivePageUri(location, it) }
    is OpenedComicFile.Pdf -> pdf.previewPageUris(location, maxPages)
}

/** Whether the file has at least one readable page: an image among its first entries, or a PDF page. */
internal fun OpenedComicFile.hasComicPages(maxEntries: Int): Boolean = when (this) {
    is OpenedComicFile.Zip -> archive.reader.containsComicImage(maxVisitedEntries = maxEntries)
    is OpenedComicFile.Pdf -> pdf.pageCount > 0
}
