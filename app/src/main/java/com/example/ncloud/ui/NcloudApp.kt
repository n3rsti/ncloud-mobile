package com.example.ncloud.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ncloud.models.AuthSession
import com.example.ncloud.models.DirectoryBundle
import com.example.ncloud.models.NcloudActionTarget
import com.example.ncloud.models.NcloudDirectory
import com.example.ncloud.models.NcloudException
import com.example.ncloud.models.NcloudFile
import com.example.ncloud.models.SearchResults
import com.example.ncloud.models.SessionExpiredException
import com.example.ncloud.models.itemName
import com.example.ncloud.network.NcloudApi
import com.example.ncloud.session.SessionStore
import com.example.ncloud.util.directoryIdFromAccessKey
import com.example.ncloud.util.formatTimestamp
import com.example.ncloud.util.readableSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val AppBg = Color(0xFF0B0F1A)
val AppTop = Color(0xFF0E1420)
val AppPanel = Color(0xFF111827)
val AppCard = Color(0xFF0F1420)
val AppBorder = Color(0xFF8A7F68)
val AppText = Color(0xFFE8E8E8)
val AppMuted = Color(0xFFB8B8B8)
val AppBlue = Color(0xFF5785E8)
val AppPurple = Color(0xFF3F2FB8)
val AppPurpleSoft = Color(0xFF2D246F)
val AppError = Color(0xFFFF6B6B)

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
                onSearchQueryChange = { searchQuery = it },
                onOpenDirectory = { loadDirectory(it, true, true) },
                onRefresh = {
                    directory?.current?.let { refreshCurrentDirectory(it) }
                },
                onNavigateBack = { navigateBack() },
                onOpenDrive = { openDriveRoot() },
                onOpenTrash = { openTrash() },
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DriveScreen(
    username: String,
    directory: DirectoryBundle?,
    loading: Boolean,
    searching: Boolean,
    error: String?,
    searchQuery: String,
    searchResults: SearchResults?,
    canNavigateBack: Boolean,
    isTrash: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onOpenDirectory: (NcloudDirectory) -> Unit,
    onRefresh: () -> Unit,
    onNavigateBack: () -> Unit,
    onOpenDrive: () -> Unit,
    onOpenTrash: () -> Unit,
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
    var searchVisible by remember { mutableStateOf(false) }
    var selectedTarget by remember { mutableStateOf<NcloudActionTarget?>(null) }
    var detailsTarget by remember { mutableStateOf<NcloudActionTarget?>(null) }
    var renameTarget by remember { mutableStateOf<NcloudActionTarget?>(null) }
    var deleteTarget by remember { mutableStateOf<NcloudActionTarget?>(null) }
    var pendingDownloadFile by remember { mutableStateOf<NcloudFile?>(null) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val searchActive = searchQuery.trim().isNotBlank()

    val folders = if (searchActive) {
        searchResults?.directories.orEmpty()
    } else {
        directory?.directories.orEmpty()
    }

    val files = if (searchActive) {
        searchResults?.files.orEmpty()
    } else {
        directory?.files.orEmpty()
    }

    BackHandler(enabled = canNavigateBack || searchVisible) {
        if (searchVisible) {
            searchVisible = false
        }

        onNavigateBack()
    }

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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            NcloudDrawer(
                username = username,
                isTrash = isTrash,
                onOpenDrive = {
                    searchVisible = false
                    scope.launch {
                        drawerState.close()
                        onOpenDrive()
                    }
                },
                onOpenTrash = {
                    searchVisible = false
                    scope.launch {
                        drawerState.close()
                        onOpenTrash()
                    }
                }
            )
        }
    ) {
        Scaffold(
            containerColor = AppBg,
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { uploadLauncher.launch("*/*") },
                    containerColor = AppPurple,
                    contentColor = Color.White
                ) {
                    Icon(
                        imageVector = Icons.Filled.Upload,
                        contentDescription = "Upload"
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .background(AppBg)
            ) {
                AppHeader(
                    onMenuClick = {
                        scope.launch {
                            drawerState.open()
                        }
                    },
                    onSearchClick = {
                        searchVisible = true
                    },
                    onLogout = onLogout
                )

                if (searchVisible || searchActive) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AppTop)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        SearchBar(
                            query = searchQuery,
                            onQueryChange = onSearchQueryChange,
                            onClose = {
                                onSearchQueryChange("")
                                searchVisible = false
                            }
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 18.dp)
                ) {
                    Text(
                        text = if (searchActive) "Global search results" else directory?.current?.name ?: "Drive",
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
                            enabled = directory != null && !loading,
                            onClick = { showCreateFolder = true },
                            colors = ButtonDefaults.buttonColors(containerColor = AppPurple),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("New folder")
                        }
                    }

                    if (searchActive) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Searching across your drive",
                            color = AppMuted,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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

                PullToRefreshBox(
                    isRefreshing = loading,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize()
                ) {
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
                                onClick = {
                                    searchVisible = false
                                    onOpenDirectory(folder)
                                },
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

                    if (directory != null && folders.isEmpty() && files.isEmpty() && !loading && !searching) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchActive) "No results found" else "This folder is empty",
                                color = AppMuted
                            )
                        }
                    }

                    if (searching) {
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
fun AppHeader(
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onLogout: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(AppTop)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onMenuClick) {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = "Open menu",
                tint = AppMuted
            )
        }

        Spacer(Modifier.weight(1f))

        IconButton(onClick = onSearchClick) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search",
                tint = AppMuted
            )
        }

        IconButton(onClick = onLogout) {
            Icon(
                imageVector = Icons.Filled.Logout,
                contentDescription = "Logout",
                tint = AppMuted
            )
        }
    }
}

@Composable
fun NcloudDrawer(
    username: String,
    isTrash: Boolean,
    onOpenDrive: () -> Unit,
    onOpenTrash: () -> Unit
) {
    ModalDrawerSheet(
        drawerContainerColor = AppTop,
        drawerContentColor = AppText,
        modifier = Modifier.width(290.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Cloud,
                contentDescription = null,
                tint = AppPurple
            )

            Spacer(Modifier.width(12.dp))

            Column {
                Text(
                    text = "Ncloud",
                    color = AppText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = username,
                    color = AppMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        HorizontalDivider(color = AppPanel)

        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DrawerItem(
                label = "Drive",
                icon = Icons.Filled.Folder,
                selected = !isTrash,
                onClick = onOpenDrive
            )

            DrawerItem(
                label = "Trash",
                icon = Icons.Filled.Delete,
                selected = isTrash,
                onClick = onOpenTrash
            )
        }
    }
}

@Composable
fun DrawerItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) AppPanel else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) AppText else AppMuted
        )

        Spacer(Modifier.width(14.dp))

        Text(
            text = label,
            color = if (selected) AppText else AppMuted,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = "Search files and directories",
                color = AppMuted
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = AppMuted
            )
        },
        trailingIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close search",
                    tint = AppMuted
                )
            }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
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
            text = if (file.size > 0L) readableSize(file.size) else file.type.ifBlank { "File" },
            color = AppMuted,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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
                .background(AppPurple)
        )

        Box(
            modifier = Modifier
                .width(68.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(AppPurple)
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