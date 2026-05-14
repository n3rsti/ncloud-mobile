package com.example.ncloud.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ncloud.models.NcloudDirectory
import com.example.ncloud.models.NcloudFile
import com.example.ncloud.util.formatTimestamp
import com.example.ncloud.util.readableSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ImagePreviewDialog(
    file: NcloudFile,
    imageCache: MutableMap<String, ImageBitmap>,
    loadImageBytes: suspend (NcloudFile) -> ByteArray,
    onDismiss: () -> Unit
) {
    val cacheKey = file.imageCacheKey()
    var image by remember(cacheKey) { mutableStateOf(imageCache[cacheKey]) }
    var loading by remember(cacheKey) { mutableStateOf(image == null) }
    var error by remember(cacheKey) { mutableStateOf<String?>(null) }

    LaunchedEffect(cacheKey) {
        if (image != null) {
            loading = false
            return@LaunchedEffect
        }

        loading = true
        error = null

        try {
            val bytes = loadImageBytes(file)
            val decoded = withContext(Dispatchers.Default) {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }

            if (decoded == null) {
                error = "Could not decode image"
            } else {
                imageCache[cacheKey] = decoded
                image = decoded
            }
        } catch (e: Exception) {
            error = e.message ?: "Could not load image"
        } finally {
            loading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppPanel,
        shape = AppDialogShape,
        title = {
            Text(
                text = file.name,
                color = AppText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp, max = 520.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    image != null -> {
                        Image(
                            bitmap = image!!,
                            contentDescription = file.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 220.dp, max = 520.dp)
                                .clip(AppCardShape)
                                .background(AppBg)
                        )
                    }
                    loading -> {
                        CircularProgressIndicator()
                    }
                    error != null -> {
                        Text(text = error.orEmpty(), color = AppError)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppPanel,
        shape = AppDialogShape,
        title = {
            Text(text = "New folder", color = AppText)
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Folder name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onCreate(name.trim()) }
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ItemOptionsDialog(
    title: String,
    showDownload: Boolean,
    destructiveText: String,
    onDismiss: () -> Unit,
    onDetails: () -> Unit,
    onRename: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppPanel,
        shape = AppDialogShape,
        title = {
            Text(
                text = title,
                color = AppText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FileActionButton(
                    text = "Details",
                    icon = Icons.Filled.Info,
                    color = AppMuted,
                    onClick = onDetails
                )

                FileActionButton(
                    text = "Rename",
                    icon = Icons.Filled.Edit,
                    color = AppMuted,
                    onClick = onRename
                )

                if (showDownload) {
                    FileActionButton(
                        text = "Download",
                        icon = Icons.Filled.Download,
                        color = AppSuccess,
                        onClick = onDownload
                    )
                }

                FileActionButton(
                    text = destructiveText,
                    icon = if (destructiveText.contains("permanently", ignoreCase = true)) {
                        Icons.Filled.DeleteForever
                    } else {
                        Icons.Filled.Delete
                    },
                    color = AppDanger,
                    onClick = onDelete
                )
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

@Composable
fun FileActionButton(
    text: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppActionShape)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier
                .width(22.dp)
                .height(22.dp)
        )

        Spacer(Modifier.width(12.dp))

        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
fun FileDetailsDialog(
    file: NcloudFile,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppPanel,
        shape = AppDialogShape,
        title = {
            Text(text = "File details", color = AppText)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                DetailRow("Name", file.name)
                DetailRow("Size", if (file.size > 0L) readableSize(file.size) else "Unknown")
                DetailRow("Date created", formatTimestamp(file.created))
                DetailRow("Date modified", formatTimestamp(file.modified))
                DetailRow("Type", file.type.ifBlank { "Unknown" })
                DetailRow("ID", file.id)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun DirectoryDetailsDialog(
    directory: NcloudDirectory,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppPanel,
        shape = AppDialogShape,
        title = {
            Text(text = "Directory details", color = AppText)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                DetailRow("Name", directory.name)
                DetailRow("Date created", formatTimestamp(directory.created))
                DetailRow("Date modified", formatTimestamp(directory.modified))
                DetailRow("ID", directory.id)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun DetailRow(
    label: String,
    value: String
) {
    Column {
        Text(
            text = label,
            color = AppMuted,
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = value,
            color = AppText,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun RenameItemDialog(
    title: String,
    message: String,
    initialName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppPanel,
        shape = AppDialogShape,
        title = {
            Text(text = title, color = AppText)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = message, color = AppMuted)

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = { onRename(name.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = AppPurple),
                shape = AppButtonShape
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DeleteItemDialog(
    title: String,
    itemName: String,
    message: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppPanel,
        shape = AppDialogShape,
        title = {
            Text(text = title, color = AppText)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = message, color = AppMuted)

                Text(
                    text = itemName,
                    color = AppText,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDelete,
                colors = ButtonDefaults.buttonColors(containerColor = AppDangerButton),
                shape = AppButtonShape
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
