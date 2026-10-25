package com.comicviewer.ui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.comicviewer.R
import com.comicviewer.StorageAccessKind

@Composable
fun FileAccessScreen(
    storageAccessKind: StorageAccessKind,
    onRequestAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val messageRes = when (storageAccessKind) {
        StorageAccessKind.LegacyReadPermission -> R.string.file_access_message_legacy
        StorageAccessKind.DocumentTree -> R.string.file_access_message_android_10
        StorageAccessKind.AllFiles -> R.string.file_access_message_all_files
    }
    val actionRes = when (storageAccessKind) {
        StorageAccessKind.LegacyReadPermission -> R.string.allow_file_access
        StorageAccessKind.DocumentTree -> R.string.choose_storage_folder
        StorageAccessKind.AllFiles -> R.string.open_settings
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.file_access_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(messageRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(28.dp))
            Button(onClick = onRequestAccess) {
                Text(stringResource(actionRes))
            }
        }
    }
}
