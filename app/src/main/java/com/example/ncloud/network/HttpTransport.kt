package com.example.ncloud.network

import android.content.Context
import android.net.Uri
import com.example.ncloud.models.AuthSession
import com.example.ncloud.models.NcloudDirectory
import com.example.ncloud.models.NcloudException
import com.example.ncloud.models.NcloudFile
import com.example.ncloud.models.SessionExpiredException
import com.example.ncloud.session.SessionStore
import com.example.ncloud.util.displayName
import com.example.ncloud.util.writeString
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

internal class HttpTransport(
    private val baseUrl: String,
    private val sessionStore: SessionStore
) {
    fun authenticatedRequest(
        method: String,
        path: String,
        directoryAccessKey: String? = null,
        body: String? = null
    ): String {
        val session = sessionStore.load() ?: throw SessionExpiredException()
        var response = rawRequest(method, path, session.accessToken, directoryAccessKey, body)

        if (response.code == 401) {
            val refreshedSession = refreshSession(session)
            response = rawRequest(method, path, refreshedSession.accessToken, directoryAccessKey, body)
        }

        if (response.code !in 200..299) {
            throw NcloudException(parseError(response.body, response.code))
        }

        return response.body
    }

    fun unauthenticatedRequest(
        method: String,
        path: String,
        body: String? = null
    ): RawResponse {
        return rawRequest(method, path, body = body)
    }

    fun uploadFiles(
        context: Context,
        directory: NcloudDirectory,
        uris: List<Uri>
    ) {
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

    fun loadFileBytes(
        directory: NcloudDirectory,
        file: NcloudFile
    ): ByteArray {
        val session = sessionStore.load() ?: throw SessionExpiredException()
        var response = loadFileBytesOnce(session, directory, file)

        if (response.code == 401) {
            val refreshedSession = refreshSession(session)
            response = loadFileBytesOnce(refreshedSession, directory, file)
        }

        if (response.code !in 200..299) {
            throw NcloudException(parseError(response.errorBody, response.code))
        }

        return response.bytes
    }

    fun downloadFile(
        context: Context,
        directory: NcloudDirectory,
        file: NcloudFile,
        targetUri: Uri
    ) {
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

    fun parseError(response: String, code: Int): String {
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
}
