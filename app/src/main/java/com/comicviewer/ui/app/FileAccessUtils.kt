package com.comicviewer.ui.app

import android.os.Build
import android.os.Environment

fun hasFileManagerAccess(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        true
    }
