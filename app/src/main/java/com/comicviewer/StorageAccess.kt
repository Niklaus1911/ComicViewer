package com.comicviewer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri

private const val STORAGE_ACCESS_PREFS_NAME = "storage_access"
private const val STORAGE_TREE_URI_KEY = "tree_uri"

enum class StorageAccessKind {
    LegacyReadPermission,
    DocumentTree,
    AllFiles,
}

sealed interface GrantedStorageAccess {
    data class RawFiles(val rootPath: String) : GrantedStorageAccess
    data class DocumentTree(val uri: Uri) : GrantedStorageAccess
}

fun storageAccessKindForSdk(sdkInt: Int): StorageAccessKind = when {
    sdkInt >= Build.VERSION_CODES.R -> StorageAccessKind.AllFiles
    sdkInt == Build.VERSION_CODES.Q -> StorageAccessKind.DocumentTree
    else -> StorageAccessKind.LegacyReadPermission
}

fun resolveGrantedStorageAccess(context: Context): GrantedStorageAccess? =
    when (storageAccessKindForSdk(Build.VERSION.SDK_INT)) {
        StorageAccessKind.AllFiles -> if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()
        ) {
            GrantedStorageAccess.RawFiles(primarySharedStoragePath())
        } else {
            null
        }

        StorageAccessKind.DocumentTree -> loadPersistedStorageTreeUri(context)
            ?.let { uri -> GrantedStorageAccess.DocumentTree(uri) }

        StorageAccessKind.LegacyReadPermission -> if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            GrantedStorageAccess.RawFiles(primarySharedStoragePath())
        } else {
            null
        }
    }

fun savePersistedStorageTreeUri(context: Context, uri: Uri) {
    context.storageAccessPrefs().edit {
        putString(STORAGE_TREE_URI_KEY, uri.toString())
    }
}

fun loadPersistedStorageTreeUri(context: Context): Uri? {
    val storedUri = context.storageAccessPrefs().getString(STORAGE_TREE_URI_KEY, null)
        ?.takeIf(String::isNotBlank)
        ?.toUri()
        ?: return null
    val hasReadGrant = runCatching {
        context.contentResolver.persistedUriPermissions.any { permission ->
            permission.uri == storedUri && permission.isReadPermission
        }
    }.getOrDefault(false)
    return storedUri.takeIf { uri -> uri.scheme == "content" && hasReadGrant }
}

@Suppress("DEPRECATION")
fun primarySharedStoragePath(): String =
    Environment.getExternalStorageDirectory().absolutePath

private fun Context.storageAccessPrefs() =
    getSharedPreferences(STORAGE_ACCESS_PREFS_NAME, Context.MODE_PRIVATE)
