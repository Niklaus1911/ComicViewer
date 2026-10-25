package com.comicviewer.ui.common

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.comicviewer.R
import java.text.DateFormat
import java.util.Date

@Composable
fun relativeTimeText(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val now = System.currentTimeMillis()
    if (now - timestamp in 0L until DateUtils.MINUTE_IN_MILLIS) return stringResource(R.string.time_just_now)
    return DateUtils.getRelativeTimeSpanString(
        timestamp,
        now,
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
