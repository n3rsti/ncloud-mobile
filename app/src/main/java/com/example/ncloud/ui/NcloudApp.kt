package com.example.ncloud.ui

import android.net.Uri
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.LocalContext
import com.example.ncloud.models.AuthSession
import com.example.ncloud.models.DirectoryBundle
import com.example.ncloud.models.NcloudDirectory
import com.example.ncloud.models.NcloudException
import com.example.ncloud.models.NcloudFile
import com.example.ncloud.models.SearchResults
import com.example.ncloud.models.SessionExpiredException
import com.example.ncloud.network.NcloudApi
import com.example.ncloud.session.SessionStore
import com.example.ncloud.util.directoryIdFromAccessKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun NcloudApp() {
    val context = LocalContext.current
    val sessionStore = remember(context) { SessionStore(context) }
    val scope = rememberCoroutineScope()
    val history = remember { mutableStateListOf<NcloudDirectory>() }

    var baseUrl by remember { mutableStateOf(sessionStore.loadBaseUrl()) }
    var session by remember { mutableStateOf(sessionStore.load()) }
    var directory by remember { mutableStateOf<DirectoryBundle?>(null) }
    var loading by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<SearchResults?>(null) }
    var searchRevision by remember { mutableIntStateOf(0) }

    fun api() = NcloudApi(baseUrl, sessionStore)

    fun clearSearch() {
        searchQuery = ""
        searchResults = null
        searching = false
    }

    fun expireSession(message: String = "Session expired. Please log in again.") {
        sessionStore.clear()
        session = null
        directory = null
        history.clear()
        clearSearch()
        error = message
    }

    fun handleFailure(exception: Exception, fallback: String) {
        if (exception is SessionExpiredException) {
            expireSession(exception.message ?: "Session expired. Please log in again.")
        } else {
            error = exception.message ?: fallback
        }
    }

    suspend fun fileParentDirectory(file: NcloudFile, currentDirectory: NcloudDirectory): NcloudDirectory {
        val parentId = file.parentDirectory
        return if (parentId.isNullOrBlank() || parentId == currentDirectory.id) {
            currentDirectory
        } else {
            api().loadDirectory(parentId).current
        }
    }

    suspend fun loadImageBytes(file: NcloudFile): ByteArray {
        val activeDirectory = directory?.current ?: throw NcloudException("No active directory")
        val parentDirectory = fileParentDirectory(file, activeDirectory)
        return api().loadFileBytes(parentDirectory, file)
    }

    fun refreshCurrentDirectory(activeDirectory: NcloudDirectory) {
        scope.launch {
            loading = true
            error = null

            try {
                directory = api().loadDirectory(activeDirectory.id)
                searchRevision++
            } catch (e: Exception) {
                handleFailure(e, "Failed to refresh directory")
            } finally {
                loading = false
            }
        }
    }

    fun loadDirectory(
        target: NcloudDirectory? = null,
        pushHistory: Boolean = false,
        resetSearch: Boolean = false
    ) {
        if (session == null) return

        scope.launch {
            loading = true
            error = null

            try {
                val loaded = api().loadDirectory(target?.id)

                if (pushHistory) {
                    directory?.current?.let { history.add(it) }
                }

                directory = loaded

                if (resetSearch) {
                    clearSearch()
                }

                sessionStore.saveBaseUrl(baseUrl)
                searchRevision++
            } catch (e: Exception) {
                handleFailure(e, "Failed to load directory")
            } finally {
                loading = false
            }
        }
    }

    fun openDriveRoot() {
        history.clear()
        loadDirectory(null, false, true)
    }

    fun openTrash() {
        val trashAccessKey = session?.trashAccessKey.orEmpty()
        val trashDirectoryId = directoryIdFromAccessKey(trashAccessKey)

        if (trashDirectoryId.isNullOrBlank()) {
            error = "Could not open trash"
            return
        }

        history.clear()
        loadDirectory(
            target = NcloudDirectory(
                id = trashDirectoryId,
                name = "Trash",
                parentDirectory = null,
                accessKey = trashAccessKey,
                created = 0L,
                modified = 0L
            ),
            pushHistory = false,
            resetSearch = true
        )
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

                val result: AuthSession = if (register) {
                    api().register(trimmedUsername, password)
                } else {
                    api().login(trimmedUsername, password)
                }

                sessionStore.save(result)
                sessionStore.saveBaseUrl(baseUrl)
                session = result
                history.clear()
                clearSearch()
                directory = api().loadDirectory(null)
                searchRevision++
            } catch (e: Exception) {
                handleFailure(e, "Authentication failed")
            } finally {
                loading = false
            }
        }
    }

    fun logout() {
        sessionStore.clear()
        session = null
        directory = null
        history.clear()
        clearSearch()
    }

    fun createFolder(name: String) {
        val activeDirectory = directory?.current ?: return

        scope.launch {
            loading = true
            error = null

            try {
                api().createDirectory(activeDirectory, name)
                directory = api().loadDirectory(activeDirectory.id)
                searchRevision++
            } catch (e: Exception) {
                handleFailure(e, "Failed to create folder")
            } finally {
                loading = false
            }
        }
    }

    fun uploadFiles(uris: List<Uri>) {
        val activeDirectory = directory?.current ?: return
        if (uris.isEmpty()) return

        scope.launch {
            loading = true
            error = null

            try {
                api().uploadFiles(context, activeDirectory, uris)
                directory = api().loadDirectory(activeDirectory.id)
                searchRevision++
            } catch (e: Exception) {
                handleFailure(e, "Failed to upload files")
            } finally {
                loading = false
            }
        }
    }

    fun renameFile(file: NcloudFile, newName: String) {
        val activeDirectory = directory?.current ?: return

        scope.launch {
            loading = true
            error = null

            try {
                if (newName.isBlank()) {
                    throw NcloudException("File name is required")
                }

                val parentDirectory = fileParentDirectory(file, activeDirectory)
                api().renameFile(parentDirectory, file, newName.trim())
                directory = api().loadDirectory(activeDirectory.id)
                searchRevision++
            } catch (e: Exception) {
                handleFailure(e, "Failed to rename file")
            } finally {
                loading = false
            }
        }
    }

    fun moveFileToTrash(file: NcloudFile) {
        val activeDirectory = directory?.current ?: return

        scope.launch {
            loading = true
            error = null

            try {
                val parentDirectory = fileParentDirectory(file, activeDirectory)
                api().moveFileToTrash(parentDirectory, file)
                directory = api().loadDirectory(activeDirectory.id)
                searchRevision++
            } catch (e: Exception) {
                handleFailure(e, "Failed to move file to trash")
            } finally {
                loading = false
            }
        }
    }

    fun deleteFilePermanently(file: NcloudFile) {
        val activeDirectory = directory?.current ?: return

        scope.launch {
            loading = true
            error = null

            try {
                val parentDirectory = fileParentDirectory(file, activeDirectory)
                api().deleteFilePermanently(parentDirectory, file)
                directory = api().loadDirectory(activeDirectory.id)
                searchRevision++
            } catch (e: Exception) {
                handleFailure(e, "Failed to permanently delete file")
            } finally {
                loading = false
            }
        }
    }

    fun downloadFile(file: NcloudFile, targetUri: Uri) {
        val activeDirectory = directory?.current ?: return

        scope.launch {
            loading = true
            error = null

            try {
                val parentDirectory = fileParentDirectory(file, activeDirectory)
                api().downloadFile(context, parentDirectory, file, targetUri)
            } catch (e: Exception) {
                handleFailure(e, "Failed to download file")
            } finally {
                loading = false
            }
        }
    }

    fun renameDirectory(targetDirectory: NcloudDirectory, newName: String) {
        val activeDirectory = directory?.current ?: return

        scope.launch {
            loading = true
            error = null

            try {
                if (newName.isBlank()) {
                    throw NcloudException("Directory name is required")
                }

                val fullTargetDirectory = api().loadDirectory(targetDirectory.id).current
                api().renameDirectory(fullTargetDirectory, newName.trim())
                directory = api().loadDirectory(activeDirectory.id)
                searchRevision++
            } catch (e: Exception) {
                handleFailure(e, "Failed to rename directory")
            } finally {
                loading = false
            }
        }
    }

    fun moveDirectoryToTrash(targetDirectory: NcloudDirectory) {
        val activeDirectory = directory?.current ?: return

        scope.launch {
            loading = true
            error = null

            try {
                val fullTargetDirectory = api().loadDirectory(targetDirectory.id).current
                api().moveDirectoryToTrash(activeDirectory, fullTargetDirectory)
                directory = api().loadDirectory(activeDirectory.id)
                searchRevision++
            } catch (e: Exception) {
                handleFailure(e, "Failed to move directory to trash")
            } finally {
                loading = false
            }
        }
    }

    fun deleteDirectoryPermanently(targetDirectory: NcloudDirectory) {
        val activeDirectory = directory?.current ?: return

        scope.launch {
            loading = true
            error = null

            try {
                val fullTargetDirectory = api().loadDirectory(targetDirectory.id).current
                api().deleteDirectoryPermanently(fullTargetDirectory)
                directory = api().loadDirectory(activeDirectory.id)
                searchRevision++
            } catch (e: Exception) {
                handleFailure(e, "Failed to permanently delete directory")
            } finally {
                loading = false
            }
        }
    }

    fun navigateBack() {
        if (searchQuery.isNotBlank()) {
            clearSearch()
            return
        }

        if (history.isNotEmpty()) {
            val previous = history.removeAt(history.lastIndex)
            loadDirectory(previous, false, true)
        }
    }

    LaunchedEffect(session?.username) {
        if (session != null && directory == null && !loading) {
            loadDirectory()
        }
    }

    LaunchedEffect(searchQuery, session?.username, searchRevision) {
        val query = searchQuery.trim()

        if (query.isBlank()) {
            searchResults = null
            searching = false
            return@LaunchedEffect
        }

        if (session == null) {
            return@LaunchedEffect
        }

        delay(300)
        searching = true

        try {
            searchResults = api().searchDirectory(query)
        } catch (e: Exception) {
            handleFailure(e, "Search failed")
            searchResults = SearchResults(emptyList(), emptyList())
        } finally {
            searching = false
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = AppBg) {
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
            val trashId = directoryIdFromAccessKey(session?.trashAccessKey.orEmpty())
            val isTrash = directory?.current?.id == trashId

            DriveScreen(
                username = session?.username.orEmpty(),
                directory = directory,
                loading = loading,
                searching = searching,
                error = error,
                searchQuery = searchQuery,
                searchResults = searchResults,
                canNavigateBack = history.isNotEmpty() || searchQuery.isNotBlank(),
                isTrash = isTrash,
                loadImageBytes = { loadImageBytes(it) },
                onSearchQueryChange = { searchQuery = it },
                onOpenDirectory = { loadDirectory(it, true, true) },
                onRefresh = { directory?.current?.let { refreshCurrentDirectory(it) } },
                onNavigateBack = { navigateBack() },
                onOpenDrive = { openDriveRoot() },
                onOpenTrash = { openTrash() },
                onLogout = { logout() },
                onCreateFolder = { createFolder(it) },
                onUpload = { uploadFiles(it) },
                onRenameFile = { file, newName -> renameFile(file, newName) },
                onMoveFileToTrash = { moveFileToTrash(it) },
                onDeleteFilePermanently = { deleteFilePermanently(it) },
                onDownloadFile = { file, targetUri -> downloadFile(file, targetUri) },
                onRenameDirectory = { targetDirectory, newName -> renameDirectory(targetDirectory, newName) },
                onMoveDirectoryToTrash = { moveDirectoryToTrash(it) },
                onDeleteDirectoryPermanently = { deleteDirectoryPermanently(it) }
            )
        }
    }
}
