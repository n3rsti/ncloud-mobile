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
import com.example.ncloud.util.displayName
import com.example.ncloud.util.longValue
import com.example.ncloud.util.nullableString
import com.example.ncloud.util.stringValue
import com.example.ncloud.util.writeString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

class NcloudApi(
    private val baseUrl: String,
    private val sessionStore: SessionStore
) {
    suspend fun login(username: String, password: String): AuthSession = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("username", username)
            .put("password", password)
            .toString()

        val response = rawRequest("POST", "/api/login", body = body)

        if (response.code !in 200..299) {
            throw NcloudException(parseError(response.body, response.code))
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

        val response = rawRequest("POST", "/api/register", body = body)

        if (response.code !in 200..299) {
            throw NcloudException(parseError(response.body, response.code))
        }

        login(username, password)
    }

    suspend fun loadDirectory(directoryId: String?): DirectoryBundle = withContext(Dispatchers.IO) {
        val path = if (directoryId.isNullOrBlank()) {
            "/api/directories"
        } else {
            "/api/directories/$directoryId"
        }

        val response = authenticatedRequest(
            method = "GET",
            path = path
        )

        val array = JSONArray(response)

        if (array.length() == 0) {
            throw NcloudException("Directory not found")
        }

        parseDirectoryBundle(array.getJSONObject(0))
    }

    suspend fun searchDirectory(query: String): SearchResults = withContext(Dispatchers.IO) {
        val encodedName = URLEncoder.encode(query, "UTF-8")

        val response = authenticatedRequest(
            method = "GET",
            path = "/api/directories/search?name=$encodedName"
        )

        val json = JSONObject(response)
        val directories = (json.optJSONArray("Directories") ?: json.optJSONArray("directories")).toDirectoryList()
        val files = (json.optJSONArray("Files") ?: json.optJSONArray("files")).toFileList()

        SearchResults(
            directories = directories,
            files = files
        )
    }

    suspend fun createDirectory(parent: NcloudDirectory, name: String): NcloudDirectory = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("name", name)
            .toString()

        val response = authenticatedRequest(
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

        authenticatedRequest(
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

        authenticatedRequest(
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

        authenticatedRequest(
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
        val session = sessionStore.load() ?: throw SessionExpiredException()
        var response = uploadFilesOnce(context, session, directory, uris)

        if (response.code == 401) {
            val refreshedSession = refreshSession(session)
            response = uploadFilesOnce(context, refreshedSession, directory, uris)
        }

        if (response.code !in 200..299) {
            throw NcloudException(parseError(response.body, response.code))
        }
    }

    suspend fun renameFile(
        directory: NcloudDirectory,
        file: NcloudFile,
        newName: String
    ) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("name", newName)
            .toString()

        authenticatedRequest(
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

        authenticatedRequest(
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

        authenticatedRequest(
            method = "POST",
            path = "/api/files/delete",
            body = body
        )
    }

    suspend fun loadFileBytes(
        directory: NcloudDirectory,
        file: NcloudFile
    ): ByteArray = withContext(Dispatchers.IO) {
        val session = sessionStore.load() ?: throw SessionExpiredException()
        var response = loadFileBytesOnce(session, directory, file)

        if (response.code == 401) {
            val refreshedSession = refreshSession(session)
            response = loadFileBytesOnce(refreshedSession, directory, file)
        }

        if (response.code !in 200..299) {
            throw NcloudException(parseError(response.errorBody, response.code))
        }

        response.bytes
    }

    suspend fun downloadFile(
        context: Context,
        directory: NcloudDirectory,
        file: NcloudFile,
        targetUri: Uri
    ) = withContext(Dispatchers.IO) {
        val session = sessionStore.load() ?: throw SessionExpiredException()
        var response = downloadFileOnce(context, session, directory, file, targetUri)

        if (response.code == 401) {
            val refreshedSession = refreshSession(session)
            response = downloadFileOnce(context, refreshedSession, directory, file, targetUri)
        }

        if (response.code !in 200..299) {
            throw NcloudException(parseError(response.body, response.code))
        }
    }

    private fun authenticatedRequest(
        method: String,
        path: String,
        directoryAccessKey: String? = null,
        body: String? = null
    ): String {
        val session = sessionStore.load() ?: throw SessionExpiredException()

        var response = rawRequest(
            method = method,
            path = path,
            accessToken = session.accessToken,
            directoryAccessKey = directoryAccessKey,
            body = body
        )

        if (response.code == 401) {
            val refreshedSession = refreshSession(session)

            response = rawRequest(
                method = method,
                path = path,
                accessToken = refreshedSession.accessToken,
                directoryAccessKey = directoryAccessKey,
                body = body
            )
        }

        if (response.code !in 200..299) {
            throw NcloudException(parseError(response.body, response.code))
        }

        return response.body
    }

    private fun refreshSession(session: AuthSession): AuthSession {
        val response = rawRequest(
            method = "GET",
            path = "/api/token/refresh",
            accessToken = session.refreshToken
        )

        if (response.code !in 200..299) {
            sessionStore.clear()
            throw SessionExpiredException()
        }

        val accessToken = try {
            JSONObject(response.body).optString("access_token")
        } catch (_: Exception) {
            ""
        }

        if (accessToken.isBlank()) {
            sessionStore.clear()
            throw SessionExpiredException()
        }

        val refreshedSession = session.copy(accessToken = accessToken)
        sessionStore.save(refreshedSession)

        return refreshedSession
    }

    private fun uploadFilesOnce(
        context: Context,
        session: AuthSession,
        directory: NcloudDirectory,
        uris: List<Uri>
    ): RawResponse {
        val boundary = "Ncloud-${UUID.randomUUID()}"
        val connection = openConnection("/api/upload/${directory.id}")

        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 15000
            connection.readTimeout = 120000
            connection.setRequestProperty("Authorization", "Bearer ${session.accessToken}")
            connection.setRequestProperty("DirectoryAccessKey", directory.accessKey)
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

            BufferedOutputStream(connection.outputStream).use { output ->
                uris.forEach { uri ->
                    val resolver = context.contentResolver
                    val name = resolver.displayName(uri).replace("\"", "'")
                    val type = resolver.getType(uri) ?: "application/octet-stream"

                    output.writeString("--$boundary\r\n")
                    output.writeString("Content-Disposition: form-data; name=\"upload[]\"; filename=\"$name\"\r\n")
                    output.writeString("Content-Type: $type\r\n\r\n")

                    val input = resolver.openInputStream(uri) ?: throw NcloudException("Cannot open $name")
                    input.use { stream ->
                        stream.copyTo(output)
                    }

                    output.writeString("\r\n")
                }

                output.writeString("--$boundary--\r\n")
            }

            val code = connection.responseCode
            val responseBody = readResponse(connection, code)

            return RawResponse(code, responseBody)
        } finally {
            connection.disconnect()
        }
    }

    private fun loadFileBytesOnce(
        session: AuthSession,
        directory: NcloudDirectory,
        file: NcloudFile
    ): RawBytesResponse {
        val connection = openConnection("/files/${file.id}")

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 120000
            connection.setRequestProperty("Authorization", "Bearer ${session.accessToken}")
            connection.setRequestProperty("DirectoryAccessKey", directory.accessKey)

            val code = connection.responseCode

            return if (code in 200..299) {
                RawBytesResponse(
                    code = code,
                    bytes = connection.inputStream.use { it.readBytes() },
                    errorBody = ""
                )
            } else {
                RawBytesResponse(
                    code = code,
                    bytes = ByteArray(0),
                    errorBody = readResponse(connection, code)
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadFileOnce(
        context: Context,
        session: AuthSession,
        directory: NcloudDirectory,
        file: NcloudFile,
        targetUri: Uri
    ): RawResponse {
        val connection = openConnection("/files/${file.id}")

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 120000
            connection.setRequestProperty("Authorization", "Bearer ${session.accessToken}")
            connection.setRequestProperty("DirectoryAccessKey", directory.accessKey)

            val code = connection.responseCode

            if (code !in 200..299) {
                val responseBody = readResponse(connection, code)
                return RawResponse(code, responseBody)
            }

            val output = context.contentResolver.openOutputStream(targetUri)
                ?: throw NcloudException("Cannot open download destination")

            output.use { destination ->
                connection.inputStream.use { source ->
                    source.copyTo(destination)
                }
            }

            return RawResponse(code, "")
        } finally {
            connection.disconnect()
        }
    }

    private fun rawRequest(
        method: String,
        path: String,
        accessToken: String? = null,
        directoryAccessKey: String? = null,
        body: String? = null
    ): RawResponse {
        val connection = openConnection(path)

        try {
            connection.requestMethod = method
            connection.connectTimeout = 15000
            connection.readTimeout = 60000
            connection.setRequestProperty("Accept", "application/json")

            if (accessToken != null) {
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
            }

            if (directoryAccessKey != null) {
                connection.setRequestProperty("DirectoryAccessKey", directoryAccessKey)
            }

            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { output ->
                    output.write(body.toByteArray(Charsets.UTF_8))
                }
            }

            val code = connection.responseCode
            val responseBody = readResponse(connection, code)

            return RawResponse(code, responseBody)
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(path: String): HttpURLConnection {
        return URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
    }

    private fun readResponse(connection: HttpURLConnection, code: Int): String {
        val stream = if (code in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }

        return stream?.bufferedReader()?.use { it.readText() }.orEmpty()
    }

    private fun parseError(response: String, code: Int): String {
        if (response.isBlank()) {
            return "Request failed with HTTP $code"
        }

        return try {
            val json = JSONObject(response)
            json.optString("error").ifBlank { response }
        } catch (_: Exception) {
            response
        }
    }

    private fun parseDirectoryBundle(json: JSONObject): DirectoryBundle {
        return DirectoryBundle(
            current = parseDirectory(json),
            directories = json.optJSONArray("directories").toDirectoryList(),
            files = json.optJSONArray("files").toFileList()
        )
    }

    private fun JSONArray?.toDirectoryList(): List<NcloudDirectory> {
        if (this == null) return emptyList()
        return List(length()) { index -> parseDirectory(getJSONObject(index)) }
    }

    private fun JSONArray?.toFileList(): List<NcloudFile> {
        if (this == null) return emptyList()
        return List(length()) { index -> parseFile(getJSONObject(index)) }
    }

    private fun parseDirectory(json: JSONObject): NcloudDirectory {
        return NcloudDirectory(
            id = json.stringValue("_id", "id"),
            name = json.stringValue("name").ifBlank { "Untitled" },
            parentDirectory = json.nullableString("parent_directory"),
            accessKey = json.stringValue("access_key"),
            created = json.longValue("created"),
            modified = json.longValue("modified")
        )
    }

    private fun parseFile(json: JSONObject): NcloudFile {
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

    private data class RawResponse(
        val code: Int,
        val body: String
    )

    private data class RawBytesResponse(
        val code: Int,
        val bytes: ByteArray,
        val errorBody: String
    )
}
