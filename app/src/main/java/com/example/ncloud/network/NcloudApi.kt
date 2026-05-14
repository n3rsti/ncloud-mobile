package com.example.ncloud.network

import android.content.Context
import android.net.Uri
import com.example.ncloud.models.AuthSession
import com.example.ncloud.models.DirectoryBundle
import com.example.ncloud.models.NcloudDirectory
import com.example.ncloud.models.NcloudException
import com.example.ncloud.models.NcloudFile
import com.example.ncloud.models.SearchResults
import com.example.ncloud.models.SessionExpiredException
import com.example.ncloud.session.SessionStore
import com.example.ncloud.util.directoryIdFromAccessKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class NcloudApi(
    baseUrl: String,
    private val sessionStore: SessionStore
) {
    private val transport = HttpTransport(baseUrl, sessionStore)

    suspend fun login(username: String, password: String): AuthSession = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("username", username)
            .put("password", password)
            .toString()

        val response = transport.unauthenticatedRequest("POST", "/api/login", body)

        if (response.code !in 200..299) {
            throw NcloudException(transport.parseError(response.body, response.code))
        }

        val json = JSONObject(response.body)

        AuthSession(
            username = json.optString("username", username),
            accessToken = json.optString("access_token"),
            refreshToken = json.optString("refresh_token"),
            trashAccessKey = json.optString("trash_access_key")
        )
    }

    suspend fun register(username: String, password: String): AuthSession = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("username", username)
            .put("password", password)
            .toString()

        val response = transport.unauthenticatedRequest("POST", "/api/register", body)

        if (response.code !in 200..299) {
            throw NcloudException(transport.parseError(response.body, response.code))
        }

        login(username, password)
    }

    suspend fun loadDirectory(directoryId: String?): DirectoryBundle = withContext(Dispatchers.IO) {
        val path = if (directoryId.isNullOrBlank()) {
            "/api/directories"
        } else {
            "/api/directories/$directoryId"
        }

        val response = transport.authenticatedRequest("GET", path)
        val array = JSONArray(response)

        if (array.length() == 0) {
            throw NcloudException("Directory not found")
        }

        parseDirectoryBundle(array.getJSONObject(0))
    }

    suspend fun searchDirectory(query: String): SearchResults = withContext(Dispatchers.IO) {
        val encodedName = URLEncoder.encode(query, "UTF-8")
        val response = transport.authenticatedRequest(
            method = "GET",
            path = "/api/directories/search?name=$encodedName"
        )

        val json = JSONObject(response)
        val directories = (json.optJSONArray("Directories") ?: json.optJSONArray("directories")).toDirectoryList()
        val files = (json.optJSONArray("Files") ?: json.optJSONArray("files")).toFileList()

        SearchResults(directories, files)
    }

    suspend fun createDirectory(parent: NcloudDirectory, name: String): NcloudDirectory = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("name", name)
            .toString()

        val response = transport.authenticatedRequest(
            method = "POST",
            path = "/api/directories/${parent.id}",
            directoryAccessKey = parent.accessKey,
            body = body
        )

        parseDirectory(JSONObject(response))
    }

    suspend fun renameDirectory(directory: NcloudDirectory, newName: String) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("name", newName)
            .toString()

        transport.authenticatedRequest(
            method = "PATCH",
            path = "/api/directories/${directory.id}",
            directoryAccessKey = directory.accessKey,
            body = body
        )
    }

    suspend fun moveDirectoryToTrash(
        currentDirectory: NcloudDirectory,
        directory: NcloudDirectory
    ) = withContext(Dispatchers.IO) {
        val session = sessionStore.load() ?: throw SessionExpiredException()
        val trashDirectoryId = directoryIdFromAccessKey(session.trashAccessKey)
            ?: throw NcloudException("Could not read trash directory id")

        val item = JSONObject()
            .put("id", directory.id)
            .put("access_key", directory.accessKey)
            .put("parent_directory", directory.parentDirectory ?: currentDirectory.id)

        val body = JSONObject()
            .put("id", trashDirectoryId)
            .put("access_key", session.trashAccessKey)
            .put("items", JSONArray().put(item))
            .toString()

        transport.authenticatedRequest(
            method = "POST",
            path = "/api/directories/move",
            body = body
        )
    }

    suspend fun deleteDirectoryPermanently(directory: NcloudDirectory) = withContext(Dispatchers.IO) {
        val body = JSONArray()
            .put(
                JSONObject()
                    .put("id", directory.id)
                    .put("access_key", directory.accessKey)
            )
            .toString()

        transport.authenticatedRequest(
            method = "POST",
            path = "/api/directories/delete",
            body = body
        )
    }

    suspend fun uploadFiles(
        context: Context,
        directory: NcloudDirectory,
        uris: List<Uri>
    ) = withContext(Dispatchers.IO) {
        transport.uploadFiles(context, directory, uris)
    }

    suspend fun renameFile(
        directory: NcloudDirectory,
        file: NcloudFile,
        newName: String
    ) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("name", newName)
            .toString()

        transport.authenticatedRequest(
            method = "PATCH",
            path = "/api/files/${file.id}",
            directoryAccessKey = directory.accessKey,
            body = body
        )
    }

    suspend fun moveFileToTrash(
        directory: NcloudDirectory,
        file: NcloudFile
    ) = withContext(Dispatchers.IO) {
        val session = sessionStore.load() ?: throw SessionExpiredException()
        val trashDirectoryId = directoryIdFromAccessKey(session.trashAccessKey)
            ?: throw NcloudException("Could not read trash directory id")

        val sourceDirectory = JSONObject()
            .put("id", directory.id)
            .put("access_key", directory.accessKey)
            .put("files", JSONArray().put(file.id))

        val body = JSONObject()
            .put("id", trashDirectoryId)
            .put("access_key", session.trashAccessKey)
            .put("directories", JSONArray().put(sourceDirectory))
            .toString()

        transport.authenticatedRequest(
            method = "POST",
            path = "/api/files/move",
            body = body
        )
    }

    suspend fun deleteFilePermanently(
        directory: NcloudDirectory,
        file: NcloudFile
    ) = withContext(Dispatchers.IO) {
        val body = JSONArray()
            .put(
                JSONObject()
                    .put("id", directory.id)
                    .put("access_key", directory.accessKey)
                    .put("files", JSONArray().put(file.id))
            )
            .toString()

        transport.authenticatedRequest(
            method = "POST",
            path = "/api/files/delete",
            body = body
        )
    }

    suspend fun loadFileBytes(
        directory: NcloudDirectory,
        file: NcloudFile
    ): ByteArray = withContext(Dispatchers.IO) {
        transport.loadFileBytes(directory, file)
    }

    suspend fun downloadFile(
        context: Context,
        directory: NcloudDirectory,
        file: NcloudFile,
        targetUri: Uri
    ) = withContext(Dispatchers.IO) {
        transport.downloadFile(context, directory, file, targetUri)
    }
}
