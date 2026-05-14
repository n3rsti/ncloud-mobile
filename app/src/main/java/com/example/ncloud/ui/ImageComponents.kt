package com.example.ncloud.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.example.ncloud.models.NcloudFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun FilePreviewShape(
    file: NcloudFile,
    imageCache: MutableMap<String, ImageBitmap>,
    loadImageBytes: suspend (NcloudFile) -> ByteArray
) {
    if (!file.isImageFile()) {
        FileShape(type = file.type)
        return
    }

    val cacheKey = file.imageCacheKey()
    val cachedBitmap = imageCache[cacheKey]
    var loading by remember(cacheKey) { mutableStateOf(cachedBitmap == null) }
    var failed by remember(cacheKey) { mutableStateOf(false) }

    LaunchedEffect(cacheKey) {
        if (cachedBitmap != null) {
            loading = false
            return@LaunchedEffect
        }

        loading = true
        failed = false

        try {
            val bytes = loadImageBytes(file)
            val decoded = withContext(Dispatchers.Default) {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }

            if (decoded != null) {
                imageCache[cacheKey] = decoded
            } else {
                failed = true
            }
        } catch (_: Exception) {
            failed = true
        } finally {
            loading = false
        }
    }

    Box(
        modifier = Modifier
            .width(76.dp)
            .height(72.dp)
            .clip(AppPreviewShape)
            .background(AppPanel),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = imageCache[cacheKey]

        when {
            bitmap != null -> {
                Image(
                    bitmap = bitmap,
                    contentDescription = file.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            loading -> {
                CircularProgressIndicator(
                    modifier = Modifier
                        .width(28.dp)
                        .height(28.dp)
                )
            }
            failed -> {
                FileShape(type = file.type)
            }
        }
    }
}

@Composable
fun FileShape(type: String) {
    val label = when {
        type.startsWith("image/") -> "IMG"
        type.startsWith("video/") -> "VID"
        type == "application/pdf" -> "PDF"
        type.isBlank() -> "FILE"
        else -> "FILE"
    }

    Box(
        modifier = Modifier
            .width(58.dp)
            .height(72.dp)
            .clip(AppPreviewShape)
            .background(AppBlue),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

fun NcloudFile.isImageFile(): Boolean {
    val lowerName = name.lowercase()
    return type.startsWith("image/") ||
        lowerName.endsWith(".jpg") ||
        lowerName.endsWith(".jpeg") ||
        lowerName.endsWith(".png") ||
        lowerName.endsWith(".gif") ||
        lowerName.endsWith(".webp") ||
        lowerName.endsWith(".bmp")
}

fun NcloudFile.imageCacheKey(): String {
    return "$id:$modified"
}
