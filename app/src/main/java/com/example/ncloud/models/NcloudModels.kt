package com.example.ncloud.models

data class AuthSession(
    val username: String,
    val accessToken: String,
    val refreshToken: String,
    val trashAccessKey: String
)

data class NcloudDirectory(
    val id: String,
    val name: String,
    val parentDirectory: String?,
    val accessKey: String,
    val created: Long,
    val modified: Long
)

data class NcloudFile(
    val id: String,
    val name: String,
    val parentDirectory: String?,
    val type: String,
    val size: Long,
    val created: Long,
    val modified: Long
)

data class DirectoryBundle(
    val current: NcloudDirectory,
    val directories: List<NcloudDirectory>,
    val files: List<NcloudFile>
)

data class SearchResults(
    val directories: List<NcloudDirectory>,
    val files: List<NcloudFile>
)

sealed class NcloudActionTarget {
    data class FileTarget(val file: NcloudFile) : NcloudActionTarget()
    data class DirectoryTarget(val directory: NcloudDirectory) : NcloudActionTarget()
}

open class NcloudException(message: String) : Exception(message)

class SessionExpiredException(
    message: String = "Session expired. Please log in again."
) : NcloudException(message)

fun NcloudActionTarget.itemName(): String {
    return when (this) {
        is NcloudActionTarget.FileTarget -> file.name
        is NcloudActionTarget.DirectoryTarget -> directory.name
    }
}
