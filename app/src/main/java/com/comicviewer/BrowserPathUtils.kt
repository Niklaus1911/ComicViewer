package com.comicviewer

import java.io.File

data class BrowserCrumb(
    val name: String,
    val path: String,
)

fun browserParentPathFor(
    currentPath: String,
    rootPath: String,
): String? {
    val currentFile = File(currentPath).canonicalFile
    val rootFile = File(rootPath).canonicalFile

    if (currentFile.absolutePath == rootFile.absolutePath) return null
    if (!currentFile.absolutePath.startsWith(rootFile.absolutePath + File.separator)) return null

    val parentPath = currentFile.parentFile?.absolutePath ?: return null
    return parentPath
        .takeIf { it == rootFile.absolutePath || it.startsWith(rootFile.absolutePath + File.separator) }
        ?.takeUnless { it == currentFile.absolutePath }
}

/**
 * Breadcrumb chain from [rootPath] down to [currentPath] (inclusive), each crumb carrying the raw
 * segment name and its absolute path. Paths are canonicalized (resolving "..", trailing separators),
 * mirroring [browserParentPathFor]. The first crumb is always the root; if [currentPath] is not at
 * or under the root, the chain degrades to just the root so navigation can never escape it.
 */
fun browserBreadcrumbs(
    currentPath: String,
    rootPath: String,
): List<BrowserCrumb> {
    val currentFile = File(currentPath).canonicalFile
    val rootFile = File(rootPath).canonicalFile
    val rootCrumb = BrowserCrumb(
        name = rootFile.name.ifBlank { rootFile.absolutePath },
        path = rootFile.absolutePath,
    )

    if (currentFile.absolutePath == rootFile.absolutePath) return listOf(rootCrumb)
    if (!currentFile.absolutePath.startsWith(rootFile.absolutePath + File.separator)) {
        return listOf(rootCrumb)
    }

    val relativeSegments = currentFile.absolutePath
        .removePrefix(rootFile.absolutePath + File.separator)
        .split(File.separator)
        .filter { segment -> segment.isNotEmpty() }

    val crumbs = mutableListOf(rootCrumb)
    var accumulatedPath = rootFile.absolutePath
    for (segment in relativeSegments) {
        accumulatedPath += File.separator + segment
        crumbs += BrowserCrumb(name = segment, path = accumulatedPath)
    }
    return crumbs
}
