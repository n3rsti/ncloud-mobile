package com.example.ncloud.util

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.text.DateFormat
import java.util.Date
import java.util.Locale

fun JSONObject.stringValue(vararg keys: String): String {
    keys.forEach { key ->
        if (has(key) && !isNull(key)) {
            return optString(key)
        }
    }

    return ""
}

fun JSONObject.nullableString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).takeIf { it.isNotBlank() }
}

fun JSONObject.longValue(key: String): Long {
    if (!has(key) || isNull(key)) return 0L
    return optLong(key, 0L)
}

fun BufferedOutputStream.writeString(value: String) {
    write(value.toByteArray(Charsets.UTF_8))
}

fun ContentResolver.displayName(uri: Uri): String {
    query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)

        if (index >= 0 && cursor.moveToFirst()) {
            return cursor.getString(index)
        }
    }

    return uri.lastPathSegment?.substringAfterLast('/') ?: "upload"
}

fun readableSize(size: Long): String {
    if (size < 1024) return "$size B"

    val kb = size / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f kB", kb)

    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)

    val gb = mb / 1024.0
    return String.format(Locale.US, "%.1f GB", gb)
}

fun directoryIdFromAccessKey(accessKey: String): String? {
    return try {
        val parts = accessKey.split(".")

        if (parts.size < 2) {
            return null
        }

        val decoded = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val payload = JSONObject(String(decoded, Charsets.UTF_8))

        payload.optString("id").takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }
}

fun formatTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) {
        return "Unknown"
    }

    return DateFormat.getDateTimeInstance(
        DateFormat.MEDIUM,
        DateFormat.SHORT
    ).format(Date(timestamp))
}
