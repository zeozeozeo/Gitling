package com.manichord.mgit.importrepo

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.sheimi.sgit.R

/** Whether Gitling can work through real filesystem paths outside its own storage. On
 * Android 11+ that's the all-files access toggle; before that, the READ/WRITE runtime grants. */
internal object DeviceStorageAccess {

    fun isGranted(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    /** The app-specific all-files-access screen, falling back to the global list if this
     * device's Settings doesn't have the per-app one. */
    fun settingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}"))
}

/** Explains the broad access the open-in-place route needs; Android shows its own confirmation
 * after this. Nothing here is requested at app startup. */
@Composable
internal fun DeviceFolderPermissionDialog(onGranted: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var denied by remember { mutableStateOf(false) }
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (DeviceStorageAccess.isGranted(context)) onGranted() else denied = true
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (DeviceStorageAccess.isGranted(context)) onGranted() else denied = true
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_open_folder_title)) },
        text = {
            Column {
                Text(stringResource(R.string.dialog_open_folder_permission))
                if (denied) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.dialog_open_folder_permission_denied),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        settingsLauncher.launch(DeviceStorageAccess.settingsIntent(context))
                    } catch (e: ActivityNotFoundException) {
                        settingsLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    }
                } else {
                    permissionLauncher.launch(arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ))
                }
            }) { Text(stringResource(R.string.label_grant_access)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.label_cancel)) }
        }
    )
}

/** A plain filesystem browser -- no content:// URIs, so the chosen folder can be handed to JGit
 * as-is. Hidden folders are skipped (the .git it would match isn't the repo root anyway). */
@Composable
internal fun DeviceFolderBrowserDialog(
    state: ImportRepoViewModel.State.Browsing,
    onNavigate: (File) -> Unit,
    onSelect: (File) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val current = remember(state.path) { File(state.path) }
    val parent = remember(current) { current.parentFile }
    var entries by remember(state.path) { mutableStateOf<List<File>?>(null) }
    var listed by remember(state.path) { mutableStateOf(false) }
    var error by remember(state.path) { mutableStateOf<String?>(null) }

    LaunchedEffect(state.path) {
        entries = withContext(Dispatchers.IO) {
            current.listFiles { file -> file.isDirectory && !file.name.startsWith(".") }
                ?.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }
        listed = true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_open_folder_title)) },
        text = {
            Column {
                Text(
                    current.absolutePath,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                when {
                    entries == null && !listed ->
                        Text(stringResource(R.string.dialog_open_folder_loading))
                    entries == null ->
                        Text(stringResource(R.string.dialog_open_folder_unreadable))
                    else -> {
                        val rows = buildList {
                            if (parent != null) add(parent)
                            addAll(entries!!)
                        }
                        if (rows.isEmpty()) {
                            Text(stringResource(R.string.dialog_open_folder_empty))
                        }
                        LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                            items(rows, key = { it.absolutePath }) { dir ->
                                val isParent = dir == parent
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onNavigate(dir) }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isParent) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Folder,
                                        contentDescription = if (isParent) stringResource(R.string.label_parent_folder) else null,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        if (isParent) stringResource(R.string.label_parent_folder) else dir.name,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        }
                    }
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                try {
                    // Cheap (lists one folder) but lets a wrong folder be corrected without
                    // closing the browser
                    RepoImporter.checkFolder(current)
                    error = null
                    onSelect(current)
                } catch (e: RepoImporter.ImportException) {
                    error = importProblemMessage(context, e.problem, e.detail)
                }
            }) { Text(stringResource(R.string.label_open_this_folder)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.label_cancel)) }
        }
    )
}

/** Confirms an in-place repo was added; unlike an import, no copy exists to report sizes for. */
@Composable
internal fun DeviceFolderOpenedDialog(
    state: ImportRepoViewModel.State.Opened,
    onOpen: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_open_folder_done_title)) },
        text = {
            Text(stringResource(R.string.dialog_open_folder_done_message,
                state.repo.getDiaplayName() ?: ""))
        },
        confirmButton = {
            TextButton(onClick = onOpen) { Text(stringResource(R.string.label_open_repo)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.label_done)) }
        }
    )
}
