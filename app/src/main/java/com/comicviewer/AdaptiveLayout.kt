package com.comicviewer

const val TABLET_WIDTH_DP = 600
const val TABLET_LIBRARY_CARD_MIN_WIDTH_DP = 180
const val TABLET_BROWSER_CONTENT_MAX_WIDTH_DP = 840

fun isTabletWidth(widthDp: Int): Boolean = widthDp >= TABLET_WIDTH_DP
