package com.example.ncloud

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ncloud.ui.theme.NcloudTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private val AppBg = Color(0xFF0B0F1A)
private val AppTop = Color(0xFF0E1420)
private val AppPanel = Color(0xFF111827)
private val AppCard = Color(0xFF0F1420)
private val AppBorder = Color(0xFF8A7F68)
private val AppText = Color(0xFFE8E8E8)
private val AppMuted = Color(0xFFB8B8B8)
private val AppBlue = Color(0xFF5785E8)
private val AppPurple = Color(0xFF3F2FB8)
private val AppPurpleSoft = Color(0xFF2D246F)
private val AppError = Color(0xFFFF6B6B)

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

sealed class NcloudActionTarget {
    data class FileTarget(val file: NcloudFile) : NcloudActionTarget()
    data class DirectoryTarget(val directory: NcloudDirectory) : NcloudActionTarget()
}

class NcloudException(message: String) : Exception(message)

class NcloudApi(private val baseUrl: String) {
    suspend fun login(username: String, password: String): AuthSession = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("username", username)
            .put("password", password)
            .toString()

        val response = request("POST", "/api/login", body = body)
        val json = JSONObject(response)

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

        request("POST", "/api/register", body = body)
        login(username, password)
    }

    suspend fun loadDirectory(session: AuthSession, directoryId: String?): DirectoryBundle = withContext(Dispatchers.IO) {
        val path = if (directoryId.isNullOrBlank()) {
            "/api/directories"
        } else {
            "/api/directories/$directoryId"
        }

        val response = request(
            method = "GET",
            path = path,
            accessToken = session.accessToken
        )

        val array = JSONArray(response)

        if (array.length() == 0) {
            throw NcloudException("Directory not found")
        }

        parseDirectoryBundle(array.getJSONObject(0))
    }

    suspend fun createDirectory(session: AuthSession, parent: NcloudDirectory, name: String): NcloudDirectory = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("name", name)
            .toString()

        val response = request(
            method = "POST",
            path = "/api/directories/${parent.id}",
            accessToken = session.accessToken,
            directoryAccessKey = parent.accessKey,
            body = body
        )

        parseDirectory(JSONObject(response))
    }

    suspend fun renameDirectory(
        session: AuthSession,
        directory: NcloudDirectory,
        newName: String
    ) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("name", newName)
            .toString()

        request(
            method = "PATCH",
            path = "/api/directories/${directory.id}",
            accessToken = session.accessToken,
            directoryAccessKey = directory.accessKey,
            body = body
        )
    }

    suspend fun moveDirectoryToTrash(
        session: AuthSession,
        currentDirectory: NcloudDirectory,
        directory: NcloudDirectory
    ) = withContext(Dispatchers.IO) {
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

        request(
            method = "POST",
            path = "/api/directories/move",
            accessToken = session.accessToken,
            body = body
        )
    }

    suspend fun uploadFiles(
        context: Context,
        session: AuthSession,
        directory: NcloudDirectory,
        uris: List<Uri>
    ) = withContext(Dispatchers.IO) {
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
            val response = readResponse(connection, code)

            if (code !in 200..299) {
                throw NcloudException(parseError(response, code))
            }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun renameFile(
        session: AuthSession,
        directory: NcloudDirectory,
        file: NcloudFile,
        newName: String
    ) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("name", newName)
            .toString()

        request(
            method = "PATCH",
            path = "/api/files/${file.id}",
            accessToken = session.accessToken,
            directoryAccessKey = directory.accessKey,
            body = body
        )
    }

    suspend fun moveFileToTrash(
        session: AuthSession,
        directory: NcloudDirectory,
        file: NcloudFile
    ) = withContext(Dispatchers.IO) {
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

        request(
            method = "POST",
            path = "/api/files/move",
            accessToken = session.accessToken,
            body = body
        )
    }

    suspend fun downloadFile(
        context: Context,
        session: AuthSession,
        directory: NcloudDirectory,
        file: NcloudFile,
        targetUri: Uri
    ) = withContext(Dispatchers.IO) {
        val connection = openConnection("/files/${file.id}")

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 120000
            connection.setRequestProperty("Authorization", "Bearer ${session.accessToken}")
            connection.setRequestProperty("DirectoryAccessKey", directory.accessKey)

            val code = connection.responseCode

            if (code !in 200..299) {
                val response = readResponse(connection, code)
                throw NcloudException(parseError(response, code))
            }

            val output = context.contentResolver.openOutputStream(targetUri)
                ?: throw NcloudException("Cannot open download destination")

            output.use { destination ->
                connection.inputStream.use { source ->
                    source.copyTo(destination)
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun request(
        method: String,
        path: String,
        accessToken: String? = null,
        directoryAccessKey: String? = null,
        body: String? = null
    ): String {
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
            val response = readResponse(connection, code)

            if (code !in 200..299) {
                throw NcloudException(parseError(response, code))
            }

            return response
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
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            NcloudTheme {
                MaterialTheme(
                    colorScheme = darkColorScheme(
                        background = AppBg,
                        surface = AppPanel,
                        primary = AppPurple,
                        onPrimary = Color.White,
                        onSurface = AppText
                    )
                ) {
                    NcloudApp()
                }
            }
        }
    }
}

@Composable
fun NcloudApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val history = remember { mutableStateListOf<NcloudDirectory>() }

    var baseUrl by remember { mutableStateOf(loadBaseUrl(context)) }
    var session by remember { mutableStateOf(loadSession(context)) }
    var directory by remember { mutableStateOf<DirectoryBundle?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun api() = NcloudApi(baseUrl)

    fun loadDirectory(target: NcloudDirectory? = null, pushHistory: Boolean = false) {
        val activeSession = session ?: return

        scope.launch {
            loading = true
            error = null

            try {
                val loaded = api().loadDirectory(activeSession, target?.id)

                if (pushHistory) {
                    directory?.current?.let { history.add(it) }
                }

                directory = loaded
                saveBaseUrl(context, baseUrl)
            } catch (e: Exception) {
                error = e.message ?: "Failed to load directory"
            } finally {
                loading = false
            }
        }
    }

    fun authenticate(username: String, password: String, register: Boolean) {
        scope.launch {
            loading = true
            error = null

            try {
                val trimmedUsername = username.trim()

                if (trimmedUsername.isBlank() || password.isBlank()) {
                    throw NcloudException("Username and password are required")
                }

                val result = if (register) {
                    api().register(trimmedUsername, password)
                } else {
                    api().login(trimmedUsername, password)
                }

                session = result
                saveSession(context, result)
                saveBaseUrl(context, baseUrl)
                history.clear()
                directory = api().loadDirectory(result, null)
            } catch (e: Exception) {
                error = e.message ?: "Authentication failed"
            } finally {
                loading = false
            }
        }
    }

    fun logout() {
        session = null
        directory = null
        history.clear()
        clearSession(context)
    }

    fun createFolder(name: String) {
        val activeSession = session ?: return
        val activeDirectory = directory?.current ?: return

        scope.launch {
            loading = true
            error = null

            try {
                api().createDirectory(activeSession, activeDirectory, name)
                directory = api().loadDirectory(activeSession, activeDirectory.id)
            } catch (e: Exception) {
                error = e.message ?: "Failed to create folder"
            } finally {
                loading = false
            }
        }
    }

    fun uploadFiles(uris: List<Uri>) {
        val activeSession = session ?: return
        val activeDirectory = directory?.current ?: return

        if (uris.isEmpty()) return

        scope.launch {
            loading = true
            error = null

            try {
                api().uploadFiles(context, activeSession, activeDirectory, uris)
                directory = api().loadDirectory(activeSession, activeDirectory.id)
            } catch (e: Exception) {
                error = e.message ?: "Failed to upload files"
            } finally {
                loading = false
            }
        }
    }

    fun renameFile(file: NcloudFile, newName: String) {
        val activeSession = session ?: return
        val activeDirectory = directory?.current ?: return

        scope.launch {
            loading = true
            error = null

            try {
                if (newName.isBlank()) {
                    throw NcloudException("File name is required")
                }

                api().renameFile(activeSession, activeDirectory, file, newName.trim())
                directory = api().loadDirectory(activeSession, activeDirectory.id)
            } catch (e: Exception) {
                error = e.message ?: "Failed to rename file"
            } finally {
                loading = false
            }
        }
    }

    fun moveFileToTrash(file: NcloudFile) {
        val activeSession = session ?: return
        val activeDirectory = directory?.current ?: return

        scope.launch {
            loading = true
            error = null

            try {
                api().moveFileToTrash(activeSession, activeDirectory, file)
                directory = api().loadDirectory(activeSession, activeDirectory.id)
            } catch (e: Exception) {
                error = e.message ?: "Failed to move file to trash"
            } finally {
                loading = false
            }
        }
    }

    fun downloadFile(file: NcloudFile, targetUri: Uri) {
        val activeSession = session ?: return
        val activeDirectory = directory?.current ?: return

        scope.launch {
            loading = true
            error = null

            try {
                api().downloadFile(context, activeSession, activeDirectory, file, targetUri)
            } catch (e: Exception) {
                error = e.message ?: "Failed to download file"
            } finally {
                loading = false
            }
        }
    }

    fun renameDirectory(targetDirectory: NcloudDirectory, newName: String) {
        val activeSession = session ?: return
        val activeDirectory = directory?.current ?: return

        scope.launch {
            loading = true
            error = null

            try {
                if (newName.isBlank()) {
                    throw NcloudException("Directory name is required")
                }

                api().renameDirectory(activeSession, targetDirectory, newName.trim())
                directory = api().loadDirectory(activeSession, activeDirectory.id)
            } catch (e: Exception) {
                error = e.message ?: "Failed to rename directory"
            } finally {
                loading = false
            }
        }
    }

    fun moveDirectoryToTrash(targetDirectory: NcloudDirectory) {
        val activeSession = session ?: return
        val activeDirectory = directory?.current ?: return

        scope.launch {
            loading = true
            error = null

            try {
                api().moveDirectoryToTrash(activeSession, activeDirectory, targetDirectory)
                directory = api().loadDirectory(activeSession, activeDirectory.id)
            } catch (e: Exception) {
                error = e.message ?: "Failed to move directory to trash"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(session?.accessToken) {
        if (session != null && directory == null && !loading) {
            loadDirectory()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = AppBg
    ) {
        if (session == null) {
            AuthScreen(
                baseUrl = baseUrl,
                loading = loading,
                error = error,
                onBaseUrlChange = { baseUrl = it },
                onSubmit = { username, password, register ->
                    authenticate(username, password, register)
                }
            )
        } else {
            DriveScreen(
                username = session?.username.orEmpty(),
                directory = directory,
                loading = loading,
                error = error,
                canGoBack = history.isNotEmpty(),
                onOpenDirectory = { loadDirectory(it, true) },
                onRefresh = { loadDirectory(directory?.current) },
                onBack = {
                    if (history.isNotEmpty()) {
                        val previous = history.removeAt(history.lastIndex)
                        loadDirectory(previous, false)
                    }
                },
                onLogout = { logout() },
                onCreateFolder = { createFolder(it) },
                onUpload = { uploadFiles(it) },
                onRenameFile = { file, newName -> renameFile(file, newName) },
                onMoveFileToTrash = { moveFileToTrash(it) },
                onDownloadFile = { file, targetUri -> downloadFile(file, targetUri) },
                onRenameDirectory = { targetDirectory, newName -> renameDirectory(targetDirectory, newName) },
                onMoveDirectoryToTrash = { moveDirectoryToTrash(it) }
            )
        }
    }
}

@Composable
fun AuthScreen(
    baseUrl: String,
    loading: Boolean,
    error: String?,
    onBaseUrlChange: (String) -> Unit,
    onSubmit: (String, String, Boolean) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = AppPanel),
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, AppPurpleSoft),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Ncloud",
                    color = AppText,
                    style = MaterialTheme.typography.headlineMedium
                )

                Text(
                    text = "Sign in or create an account",
                    color = AppMuted,
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = onBaseUrlChange,
                    label = { Text("API base URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                if (error != null) {
                    Text(
                        text = error,
                        color = AppError,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        enabled = !loading,
                        onClick = { onSubmit(username, password, false) },
                        colors = ButtonDefaults.buttonColors(containerColor = AppPurple),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Login")
                    }

                    Button(
                        enabled = !loading,
                        onClick = { onSubmit(username, password, true) },
                        colors = ButtonDefaults.buttonColors(containerColor = AppBlue),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Register")
                    }
                }

                if (loading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DriveScreen(
    username: String,
    directory: DirectoryBundle?,
    loading: Boolean,
    error: String?,
    canGoBack: Boolean,
    onOpenDirectory: (NcloudDirectory) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onUpload: (List<Uri>) -> Unit,
    onRenameFile: (NcloudFile, String) -> Unit,
    onMoveFileToTrash: (NcloudFile) -> Unit,
    onDownloadFile: (NcloudFile, Uri) -> Unit,
    onRenameDirectory: (NcloudDirectory, String) -> Unit,
    onMoveDirectoryToTrash: (NcloudDirectory) -> Unit
) {
    var showCreateFolder by remember { mutableStateOf(false) }
    var selectedTarget by remember { mutableStateOf<NcloudActionTarget?>(null) }
    var detailsTarget by remember { mutableStateOf<NcloudActionTarget?>(null) }
    var renameTarget by remember { mutableStateOf<NcloudActionTarget?>(null) }
    var deleteTarget by remember { mutableStateOf<NcloudActionTarget?>(null) }
    var pendingDownloadFile by remember { mutableStateOf<NcloudFile?>(null) }

    val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        onUpload(uris)
    }

    val downloadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val file = pendingDownloadFile

        if (uri != null && file != null) {
            onDownloadFile(file, uri)
        }

        pendingDownloadFile = null
    }

    Scaffold(
        containerColor = AppBg,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { uploadLauncher.launch("*/*") },
                containerColor = AppPurple,
                contentColor = Color.White
            ) {
                Text("Upload")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(AppBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppTop)
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Ncloud",
                        color = AppText,
                        style = MaterialTheme.typography.titleLarge
                    )

                    Spacer(Modifier.weight(1f))

                    TextButton(onClick = onLogout) {
                        Text("Logout")
                    }
                }

                Text(
                    text = username,
                    color = AppMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Text(
                    text = directory?.current?.name ?: "Drive",
                    color = AppText,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(14.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        enabled = canGoBack && !loading,
                        onClick = onBack,
                        colors = ButtonDefaults.buttonColors(containerColor = AppPanel)
                    ) {
                        Text("Back")
                    }

                    Button(
                        enabled = !loading,
                        onClick = onRefresh,
                        colors = ButtonDefaults.buttonColors(containerColor = AppPanel)
                    ) {
                        Text("Refresh")
                    }

                    Button(
                        enabled = directory != null && !loading,
                        onClick = { showCreateFolder = true },
                        colors = ButtonDefaults.buttonColors(containerColor = AppPurple)
                    ) {
                        Text("New folder")
                    }
                }

                if (error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = error,
                        color = AppError,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Box(Modifier.fillMaxSize()) {
                val folders = directory?.directories.orEmpty()
                val files = directory?.files.orEmpty()

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(145.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(folders, key = { "dir-${it.id}" }) { folder ->
                        FolderCard(
                            folder = folder,
                            onClick = { onOpenDirectory(folder) },
                            onMenuClick = {
                                selectedTarget = NcloudActionTarget.DirectoryTarget(folder)
                            }
                        )
                    }

                    items(files, key = { "file-${it.id}" }) { file ->
                        FileCard(
                            file = file,
                            onClick = {},
                            onMenuClick = {
                                selectedTarget = NcloudActionTarget.FileTarget(file)
                            }
                        )
                    }
                }

                if (directory != null && folders.isEmpty() && files.isEmpty() && !loading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "This folder is empty",
                            color = AppMuted
                        )
                    }
                }

                if (loading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(AppBg.copy(alpha = 0.62f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }

    if (showCreateFolder) {
        CreateFolderDialog(
            onDismiss = { showCreateFolder = false },
            onCreate = {
                showCreateFolder = false
                onCreateFolder(it)
            }
        )
    }

    selectedTarget?.let { target ->
        ItemOptionsDialog(
            title = target.itemName(),
            showDownload = target is NcloudActionTarget.FileTarget,
            onDismiss = { selectedTarget = null },
            onDetails = {
                selectedTarget = null
                detailsTarget = target
            },
            onRename = {
                selectedTarget = null
                renameTarget = target
            },
            onDownload = {
                selectedTarget = null

                if (target is NcloudActionTarget.FileTarget) {
                    pendingDownloadFile = target.file
                    downloadLauncher.launch(target.file.name)
                }
            },
            onDelete = {
                selectedTarget = null
                deleteTarget = target
            }
        )
    }

    detailsTarget?.let { target ->
        when (target) {
            is NcloudActionTarget.FileTarget -> {
                FileDetailsDialog(
                    file = target.file,
                    onDismiss = { detailsTarget = null }
                )
            }

            is NcloudActionTarget.DirectoryTarget -> {
                DirectoryDetailsDialog(
                    directory = target.directory,
                    onDismiss = { detailsTarget = null }
                )
            }
        }
    }

    renameTarget?.let { target ->
        RenameItemDialog(
            title = if (target is NcloudActionTarget.FileTarget) "Rename file" else "Rename directory",
            message = if (target is NcloudActionTarget.FileTarget) {
                "Do you want to rename the file?"
            } else {
                "Do you want to rename the directory?"
            },
            initialName = target.itemName(),
            onDismiss = { renameTarget = null },
            onRename = { newName ->
                renameTarget = null

                when (target) {
                    is NcloudActionTarget.FileTarget -> onRenameFile(target.file, newName)
                    is NcloudActionTarget.DirectoryTarget -> onRenameDirectory(target.directory, newName)
                }
            }
        )
    }

    deleteTarget?.let { target ->
        DeleteItemDialog(
            title = if (target is NcloudActionTarget.FileTarget) "Delete file" else "Delete directory",
            itemName = target.itemName(),
            onDismiss = { deleteTarget = null },
            onDelete = {
                deleteTarget = null

                when (target) {
                    is NcloudActionTarget.FileTarget -> onMoveFileToTrash(target.file)
                    is NcloudActionTarget.DirectoryTarget -> onMoveDirectoryToTrash(target.directory)
                }
            }
        )
    }
}

@Composable
fun FolderCard(
    folder: NcloudDirectory,
    onClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    DriveCard(
        onClick = onClick,
        onMenuClick = onMenuClick
    ) {
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
    onClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    DriveCard(
        onClick = onClick,
        onMenuClick = onMenuClick
    ) {
        FileShape(type = file.type)

        Spacer(Modifier.height(12.dp))

        Text(
            text = file.name,
            color = AppText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = readableSize(file.size),
            color = AppMuted,
            style = MaterialTheme.typography.bodySmall
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
        shape = RoundedCornerShape(10.dp),
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
                verticalArrangement = Arrangement.Center,
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
fun DotsButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .width(34.dp)
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
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
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(13.dp)
                .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                .background(AppBlue)
        )

        Box(
            modifier = Modifier
                .width(68.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(AppBlue)
        )
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
            .clip(RoundedCornerShape(8.dp))
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

@Composable
fun CreateFolderDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppPanel,
        title = {
            Text(
                text = "New folder",
                color = AppText
            )
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
    onDismiss: () -> Unit,
    onDetails: () -> Unit,
    onRename: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppPanel,
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
                    color = AppMuted,
                    onClick = onDetails
                )

                FileActionButton(
                    text = "Rename",
                    color = AppMuted,
                    onClick = onRename
                )

                if (showDownload) {
                    FileActionButton(
                        text = "Download",
                        color = Color(0xFF28E26D),
                        onClick = onDownload
                    )
                }

                FileActionButton(
                    text = "Delete",
                    color = Color(0xFFFF4B55),
                    onClick = onDelete
                )
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

@Composable
fun FileActionButton(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 13.dp)
    )
}

@Composable
fun FileDetailsDialog(file: NcloudFile, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppPanel,
        title = {
            Text(
                text = "File details",
                color = AppText
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                DetailRow("Name", file.name)
                DetailRow("Size", readableSize(file.size))
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
fun DirectoryDetailsDialog(directory: NcloudDirectory, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppPanel,
        title = {
            Text(
                text = "Directory details",
                color = AppText
            )
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
fun DetailRow(label: String, value: String) {
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
        title = {
            Text(
                text = title,
                color = AppText
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = message,
                    color = AppMuted
                )

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
                colors = ButtonDefaults.buttonColors(containerColor = AppPurple)
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
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppPanel,
        title = {
            Text(
                text = title,
                color = AppText
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Do you want to move this item to trash?",
                    color = AppMuted
                )

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
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE52632))
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

fun NcloudActionTarget.itemName(): String {
    return when (this) {
        is NcloudActionTarget.FileTarget -> file.name
        is NcloudActionTarget.DirectoryTarget -> directory.name
    }
}

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

fun loadBaseUrl(context: Context): String {
    return context.getSharedPreferences("ncloud", Context.MODE_PRIVATE)
        .getString("base_url", "http://10.0.2.2:8080")
        ?: "http://10.0.2.2:8080"
}

fun saveBaseUrl(context: Context, baseUrl: String) {
    context.getSharedPreferences("ncloud", Context.MODE_PRIVATE)
        .edit()
        .putString("base_url", baseUrl)
        .apply()
}

fun loadSession(context: Context): AuthSession? {
    val prefs = context.getSharedPreferences("ncloud", Context.MODE_PRIVATE)

    val username = prefs.getString("username", null) ?: return null
    val accessToken = prefs.getString("access_token", null) ?: return null
    val refreshToken = prefs.getString("refresh_token", null) ?: return null
    val trashAccessKey = prefs.getString("trash_access_key", null) ?: ""

    return AuthSession(
        username = username,
        accessToken = accessToken,
        refreshToken = refreshToken,
        trashAccessKey = trashAccessKey
    )
}

fun saveSession(context: Context, session: AuthSession) {
    context.getSharedPreferences("ncloud", Context.MODE_PRIVATE)
        .edit()
        .putString("username", session.username)
        .putString("access_token", session.accessToken)
        .putString("refresh_token", session.refreshToken)
        .putString("trash_access_key", session.trashAccessKey)
        .apply()
}

fun clearSession(context: Context) {
    context.getSharedPreferences("ncloud", Context.MODE_PRIVATE)
        .edit()
        .remove("username")
        .remove("access_token")
        .remove("refresh_token")
        .remove("trash_access_key")
        .apply()
}