package com.comicviewer.ui.common

import android.content.Context
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.text.DateFormat
import java.util.Date

@Composable
fun relativeTimeText(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return DateUtils.getRelativeTimeSpanString(
        timestamp,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
}

@Composable
fun absoluteDateText(timestamp: Long): String =
    if (timestamp <= 0L) "" else DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))

@Composable
fun fileSizeText(bytes: Long?): String? {
    val context = LocalContext.current
    return bytes?.takeIf { it >= 0L }?.let { Formatter.formatShortFileSize(context, it) }
}

fun Context.relativeTimeText(timestamp: Long, nowMs: Long = System.currentTimeMillis()): String =
    DateUtils.getRelativeTimeSpanString(
        timestamp,
        nowMs,
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
