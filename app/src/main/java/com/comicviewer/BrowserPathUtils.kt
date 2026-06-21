package com.comicviewer

import java.io.File

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
