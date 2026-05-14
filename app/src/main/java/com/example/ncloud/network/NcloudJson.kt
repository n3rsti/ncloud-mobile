package com.example.ncloud.network

import com.example.ncloud.models.DirectoryBundle
import com.example.ncloud.models.NcloudDirectory
import com.example.ncloud.models.NcloudFile
import com.example.ncloud.util.longValue
import com.example.ncloud.util.nullableString
import com.example.ncloud.util.stringValue
import org.json.JSONArray
import org.json.JSONObject

internal fun parseDirectoryBundle(json: JSONObject): DirectoryBundle {
    return DirectoryBundle(
        current = parseDirectory(json),
        directories = json.optJSONArray("directories").toDirectoryList(),
        files = json.optJSONArray("files").toFileList()
    )
}

internal fun JSONArray?.toDirectoryList(): List<NcloudDirectory> {
    if (this == null) return emptyList()
    return List(length()) { index -> parseDirectory(getJSONObject(index)) }
}

internal fun JSONArray?.toFileList(): List<NcloudFile> {
    if (this == null) return emptyList()
    return List(length()) { index -> parseFile(getJSONObject(index)) }
}

internal fun parseDirectory(json: JSONObject): NcloudDirectory {
    return NcloudDirectory(
        id = json.stringValue("_id", "id"),
        name = json.stringValue("name").ifBlank { "Untitled" },
        parentDirectory = json.nullableString("parent_directory"),
        accessKey = json.stringValue("access_key"),
        created = json.longValue("created"),
        modified = json.longValue("modified")
    )
}

internal fun parseFile(json: JSONObject): NcloudFile {
    return NcloudFile(
        id = json.stringValue("_id", "id"),
        name = json.stringValue("name").ifBlank { "Untitled" },
        parentDirectory = json.nullableString("parent_directory"),
        type = json.stringValue("type"),
        size = json.longValue("size"),
        created = json.longValue("created"),
        modified = json.longValue("modified")
    )
}
