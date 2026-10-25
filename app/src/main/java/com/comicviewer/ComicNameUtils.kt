package com.comicviewer

import java.util.Locale


private val supportedArchiveExtensions = setOf("zip", "cbz")
private val supportedImageExtensions = setOf("jpg", "jpeg", "png", "webp")

fun isSupportedArchiveName(name: String): Boolean =
    name.extension() in supportedArchiveExtensions

fun isSupportedImageName(name: String): Boolean =
    name.extension() in supportedImageExtensions

fun compareComicPageNames(left: String, right: String): Int =
    NaturalComicNameComparator.compare(left, right)

fun coercePageIndex(index: Int, pageCount: Int): Int =
    if (pageCount <= 0) 0 else index.coerceIn(0, pageCount - 1)

fun comicLoadErrorForPageCount(pageCount: Int): ComicLoadError? =
    if (pageCount == 0) ComicLoadError.NoSupportedImagesFound else null

private fun String.extension(): String =
    substringAfterLast('.', missingDelimiterValue = "")
        .lowercase(Locale.US)

private object NaturalComicNameComparator : Comparator<String> {
    override fun compare(left: String, right: String): Int {
        var leftIndex = 0
        var rightIndex = 0

        while (leftIndex < left.length && rightIndex < right.length) {
            val leftChar = left[leftIndex]
            val rightChar = right[rightIndex]

            if (leftChar.isDigit() && rightChar.isDigit()) {
                val numberCompare = compareNumberRuns(left, leftIndex, right, rightIndex)
                if (numberCompare.result != 0) return numberCompare.result
                leftIndex = numberCompare.nextLeftIndex
                rightIndex = numberCompare.nextRightIndex
            } else {
                val charCompare = leftChar.lowercaseChar().compareTo(rightChar.lowercaseChar())
                if (charCompare != 0) return charCompare
                leftIndex += 1
                rightIndex += 1
            }
        }

        return left.length.compareTo(right.length)
    }

    private fun compareNumberRuns(
        left: String,
        leftStart: Int,
        right: String,
        rightStart: Int,
    ): NumberCompare {
        var leftEnd = leftStart
        var rightEnd = rightStart

        while (leftEnd < left.length && left[leftEnd].isDigit()) leftEnd += 1
        while (rightEnd < right.length && right[rightEnd].isDigit()) rightEnd += 1

        val leftRaw = left.substring(leftStart, leftEnd)
        val rightRaw = right.substring(rightStart, rightEnd)
        val leftNumber = leftRaw.trimStart('0').ifEmpty { "0" }
        val rightNumber = rightRaw.trimStart('0').ifEmpty { "0" }

        val lengthCompare = leftNumber.length.compareTo(rightNumber.length)
        if (lengthCompare != 0) {
            return NumberCompare(lengthCompare, leftEnd, rightEnd)
        }

        val valueCompare = leftNumber.compareTo(rightNumber)
        if (valueCompare != 0) {
            return NumberCompare(valueCompare, leftEnd, rightEnd)
        }

        val rawLengthCompare = leftRaw.length.compareTo(rightRaw.length)
        return NumberCompare(rawLengthCompare, leftEnd, rightEnd)
    }

    private data class NumberCompare(
        val result: Int,
        val nextLeftIndex: Int,
        val nextRightIndex: Int,
    )
}
