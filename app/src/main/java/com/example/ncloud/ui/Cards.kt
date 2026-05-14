package com.example.ncloud.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ncloud.models.NcloudDirectory
import com.example.ncloud.models.NcloudFile
import com.example.ncloud.util.readableSize

@Composable
fun FolderCard(
    folder: NcloudDirectory,
    onClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    DriveCard(onClick = onClick, onMenuClick = onMenuClick) {
        FolderShape()

        Spacer(Modifier.height(12.dp))

        Text(
            text = folder.name,
            color = AppText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun FileCard(
    file: NcloudFile,
    imageCache: MutableMap<String, ImageBitmap>,
    loadImageBytes: suspend (NcloudFile) -> ByteArray,
    onClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    DriveCard(onClick = onClick, onMenuClick = onMenuClick) {
        FilePreviewShape(
            file = file,
            imageCache = imageCache,
            loadImageBytes = loadImageBytes
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = file.name,
            color = AppText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = if (file.size > 0L) readableSize(file.size) else file.type.ifBlank { "File" },
            color = AppMuted,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun DriveCard(
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AppCard),
        shape = AppCardShape,
        border = BorderStroke(1.dp, AppBorder),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.05f)
            .clickable { onClick() }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                content = content
            )

            DotsButton(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
                onClick = onMenuClick
            )
        }
    }
}

@Composable
fun DotsButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .width(34.dp)
            .height(34.dp)
            .clip(AppIconButtonShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "⋮",
            color = AppMuted,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
fun FolderShape() {
    Icon(
        imageVector = Icons.Filled.Folder,
        contentDescription = null,
        tint = AppPurple,
        modifier = Modifier
            .width(78.dp)
            .height(78.dp)
    )
}
