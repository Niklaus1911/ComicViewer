package com.comicviewer.ui.common

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter

/**
 * Rounded cover tile for list rows: shows the [thumbnailUri] cropped to fill when present,
 * otherwise a centered [fallbackIcon]. The Surface shape already clips, so no extra clip is needed.
 */
@Composable
fun CoverThumbnail(
    thumbnailUri: Uri?,
    fallbackIcon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(60.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        if (thumbnailUri != null) {
            Image(
                painter = rememberAsyncImagePainter(model = thumbnailUri),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = fallbackIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
    }
}
