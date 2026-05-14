package com.example.ncloud.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.ncloud.models.DirectoryBundle
import com.example.ncloud.models.NcloudActionTarget
import com.example.ncloud.models.NcloudDirectory
import com.example.ncloud.models.NcloudFile
import com.example.ncloud.models.SearchResults
import com.example.ncloud.models.itemName

@OptIn(ExperimentalMaterial3Api::class)
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
    loadImageBytes: suspend (NcloudFile) -> ByteArray,
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
    onDeleteFilePermanently: (NcloudFile) -> Unit,
    onDownloadFile: (NcloudFile, Uri) -> Unit,
    onRenameDirectory: (NcloudDirectory, String) -> Unit,
    onMoveDirectoryToTrash: (NcloudDirectory) -> Unit,
    onDeleteDirectoryPermanently: (NcloudDirectory) -> Unit
) {
    var showCreateFolder by remember { mutableStateOf(false) }
    var searchVisible by remember { mutableStateOf(false) }
    var drawerOpen by remember { mutableStateOf(false) }
    var selectedTarget by remember { mutableStateOf<NcloudActionTarget?>(null) }
    var detailsTarget by remember { mutableStateOf<NcloudActionTarget?>(null) }
    var renameTarget by remember { mutableStateOf<NcloudActionTarget?>(null) }
    var deleteTarget by remember { mutableStateOf<NcloudActionTarget?>(null) }
    var pendingDownloadFile by remember { mutableStateOf<NcloudFile?>(null) }
    var previewFile by remember { mutableStateOf<NcloudFile?>(null) }
    val imageCache = remember { mutableStateMapOf<String, ImageBitmap>() }

    val searchActive = searchQuery.trim().isNotBlank()
    val folders = if (searchActive) searchResults?.directories.orEmpty() else directory?.directories.orEmpty()
    val files = if (searchActive) searchResults?.files.orEmpty() else directory?.files.orEmpty()

    BackHandler(enabled = drawerOpen || canNavigateBack || searchVisible || previewFile != null) {
        when {
            previewFile != null -> previewFile = null
            drawerOpen -> drawerOpen = false
            searchVisible -> {
                searchVisible = false
                onSearchQueryChange("")
            }
            else -> onNavigateBack()
        }
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

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = AppBg,
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { uploadLauncher.launch("*/*") },
                    containerColor = AppPurple,
                    contentColor = Color.White
                ) {
                    Icon(imageVector = Icons.Filled.Upload, contentDescription = "Upload")
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
                    onMenuClick = { drawerOpen = true },
                    onSearchClick = { searchVisible = true },
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

                DriveTitleBar(
                    title = if (searchActive) "Global search results" else directory?.current?.name ?: "Drive",
                    canCreateFolder = directory != null && !loading && !isTrash,
                    searchActive = searchActive,
                    isTrash = isTrash,
                    error = error,
                    onCreateFolderClick = { showCreateFolder = true }
                )

                DriveGrid(
                    folders = folders,
                    files = files,
                    loading = loading,
                    searching = searching,
                    directoryLoaded = directory != null,
                    searchActive = searchActive,
                    imageCache = imageCache,
                    loadImageBytes = loadImageBytes,
                    onRefresh = onRefresh,
                    onOpenDirectory = {
                        searchVisible = false
                        onOpenDirectory(it)
                    },
                    onPreviewFile = { file ->
                        if (file.isImageFile()) previewFile = file
                    },
                    onDirectoryMenu = { selectedTarget = NcloudActionTarget.DirectoryTarget(it) },
                    onFileMenu = { selectedTarget = NcloudActionTarget.FileTarget(it) }
                )
            }
        }

        AppDrawerOverlay(
            visible = drawerOpen,
            username = username,
            isTrash = isTrash,
            onDismiss = { drawerOpen = false },
            onOpenDrive = {
                drawerOpen = false
                searchVisible = false
                onOpenDrive()
            },
            onOpenTrash = {
                drawerOpen = false
                searchVisible = false
                onOpenTrash()
            }
        )
    }

    previewFile?.let { file ->
        ImagePreviewDialog(
            file = file,
            imageCache = imageCache,
            loadImageBytes = loadImageBytes,
            onDismiss = { previewFile = null }
        )
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
            destructiveText = if (isTrash) "Delete permanently" else "Delete",
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
            is NcloudActionTarget.FileTarget -> FileDetailsDialog(
                file = target.file,
                onDismiss = { detailsTarget = null }
            )
            is NcloudActionTarget.DirectoryTarget -> DirectoryDetailsDialog(
                directory = target.directory,
                onDismiss = { detailsTarget = null }
            )
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
            title = if (isTrash) {
                if (target is NcloudActionTarget.FileTarget) "Permanently delete file" else "Permanently delete directory"
            } else {
                if (target is NcloudActionTarget.FileTarget) "Delete file" else "Delete directory"
            },
            itemName = target.itemName(),
            message = if (isTrash) {
                "Do you want to permanently delete this item? This action cannot be undone."
            } else {
                "Do you want to move this item to trash?"
            },
            confirmText = if (isTrash) "Delete permanently" else "Delete",
            onDismiss = { deleteTarget = null },
            onDelete = {
                deleteTarget = null

                when (target) {
                    is NcloudActionTarget.FileTarget -> {
                        if (isTrash) onDeleteFilePermanently(target.file) else onMoveFileToTrash(target.file)
                    }
                    is NcloudActionTarget.DirectoryTarget -> {
                        if (isTrash) onDeleteDirectoryPermanently(target.directory) else onMoveDirectoryToTrash(target.directory)
                    }
                }
            }
        )
    }
}

@Composable
fun DriveTitleBar(
    title: String,
    canCreateFolder: Boolean,
    searchActive: Boolean,
    isTrash: Boolean,
    error: String?,
    onCreateFolderClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = AppText,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Spacer(Modifier.width(14.dp))

            Button(
                enabled = canCreateFolder,
                onClick = onCreateFolderClick,
                colors = ButtonDefaults.buttonColors(containerColor = AppPurple),
                shape = AppButtonShape
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

        if (isTrash) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Items deleted here are removed permanently.",
                color = AppMuted,
                style = MaterialTheme.typography.bodySmall
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveGrid(
    folders: List<NcloudDirectory>,
    files: List<NcloudFile>,
    loading: Boolean,
    searching: Boolean,
    directoryLoaded: Boolean,
    searchActive: Boolean,
    imageCache: MutableMap<String, ImageBitmap>,
    loadImageBytes: suspend (NcloudFile) -> ByteArray,
    onRefresh: () -> Unit,
    onOpenDirectory: (NcloudDirectory) -> Unit,
    onPreviewFile: (NcloudFile) -> Unit,
    onDirectoryMenu: (NcloudDirectory) -> Unit,
    onFileMenu: (NcloudFile) -> Unit
) {
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
                    onClick = { onOpenDirectory(folder) },
                    onMenuClick = { onDirectoryMenu(folder) }
                )
            }

            items(files, key = { "file-${it.id}" }) { file ->
                FileCard(
                    file = file,
                    imageCache = imageCache,
                    loadImageBytes = loadImageBytes,
                    onClick = { onPreviewFile(file) },
                    onMenuClick = { onFileMenu(file) }
                )
            }
        }

        if (directoryLoaded && folders.isEmpty() && files.isEmpty() && !loading && !searching) {
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

@Composable
fun BoxScope.AppDrawerOverlay(
    visible: Boolean,
    username: String,
    isTrash: Boolean,
    onDismiss: () -> Unit,
    onOpenDrive: () -> Unit,
    onOpenTrash: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .fillMaxSize()
            .zIndex(10f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
        )
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
        modifier = Modifier
            .zIndex(11f)
            .align(Alignment.CenterStart)
    ) {
        NcloudDrawer(
            username = username,
            isTrash = isTrash,
            onOpenDrive = onOpenDrive,
            onOpenTrash = onOpenTrash
        )
    }
}
