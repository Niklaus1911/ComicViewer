package com.comicviewer

import java.io.File

fun comicPageImageCacheKey(source: ComicSource, page: ComicPage): String {
    val baseKey = comicPageImageCacheKey(
        source = source,
        pageIndex = page.index,
        relativePath = page.relativePath,
        imageUri = page.imageUri.toString(),
        sourceFingerprint = page.cacheFingerprint,
    )
    val localPageFile = page.imageUri.path?.let(::File)?.takeIf(File::isFile)
    return if (localPageFile == null) {
        baseKey
    } else {
        "$baseKey|file:${localPageFile.lastModified()}:${localPageFile.length()}"
    }
}

fun comicPageImageCacheKey(
    source: ComicSource,
    pageIndex: Int,
    relativePath: String,
    imageUri: String,
    sourceFingerprint: String? = null,
): String = buildList {
    addAll(
        listOf(
            "comic-page",
            comicSourceImageCacheKey(source),
            pageIndex.toString(),
            relativePath,
            imageUri,
        ),
    )
    sourceFingerprint?.let { add("source:$it") }
}.joinToString(separator = "|")

private fun comicSourceImageCacheKey(source: ComicSource): String = when (source) {
    is ComicSource.Archive -> "archive-uri:${source.displayName}:${source.uri}"
    is ComicSource.Folder -> "folder-uri:${source.displayName}:${source.uri}"
    is ComicSource.LocalArchive -> {
        val file = File(source.filePath)
        "local-archive:${source.filePath}:${file.lastModified()}:${file.length()}"
    }

    is ComicSource.LocalFolder -> "local-folder:${source.folderPath}"
}
