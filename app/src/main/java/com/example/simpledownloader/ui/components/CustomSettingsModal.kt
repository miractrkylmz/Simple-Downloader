package com.example.simpledownloader.ui.components

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.simpledownloader.domain.model.DownloadSettings
// import com.example.simpledownloader.utils.CookieHelper
import com.example.simpledownloader.utils.PermissionsHelper
import com.example.simpledownloader.utils.YtDlpUpdateManager
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Custom Settings Modal - Bottom sheet style modal for folder settings
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomSettingsModal(
    currentSettings: DownloadSettings,
    onDismissRequest: () -> Unit,
    onSaveSettings: (DownloadSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    var askEveryTime by remember { mutableStateOf(currentSettings.askEveryTime) }
    var selectedFolderPath by remember { mutableStateOf(currentSettings.fixedFolderPath) }
    var hasPermission by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    
    // Check permission status on composition
    LaunchedEffect(Unit) {
        hasPermission = PermissionsHelper.hasStoragePermission(context)
    }
    
    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermission = permissions.values.all { it }
        Timber.d("Permissions granted: $hasPermission")
    }
    
    // Folder picker launcher
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            try {
                // Take persistable permission
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                
                selectedFolderPath = it.toString()
                Timber.d("Selected folder: ${it.path}")
            } catch (e: Exception) {
                Timber.e(e, "Error selecting folder")
            }
        }
    }
    
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f) // Max 85% of screen height
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(28.dp)),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        
                        Text(
                            text = "İndirme Ayarları",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    IconButton(onClick = onDismissRequest) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Kapat",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                )
                
                // PERMISSIONS SECTION
                if (PermissionsHelper.shouldShowStoragePermission()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "İzinler",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = if (hasPermission) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            } else {
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Depolama İzni",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    
                                    Text(
                                        text = PermissionsHelper.getPermissionStatusMessage(context),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (hasPermission) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        }
                                    )
                                }
                                
                                if (!hasPermission) {
                                    FilledTonalButton(
                                        onClick = {
                                            val permissions = PermissionsHelper.getRequiredPermissions()
                                            if (permissions.isNotEmpty()) {
                                                permissionLauncher.launch(permissions)
                                            }
                                        },
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("İzin Ver")
                                    }
                                }
                            }
                        }
                    }
                    
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                    )
                }
                
                // FOLDER SELECTION SECTION
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Klasör Seçimi",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    
                    // Option 1: Ask every time
                    SettingsOptionItem(
                        title = "Her İndirmede Sor",
                        description = "Her video indirirken klasör seçimi sorulacak",
                        isSelected = askEveryTime,
                        onClick = { 
                            askEveryTime = true
                            selectedFolderPath = null
                        }
                    )
                    
                    // Option 2: Fixed folder
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SettingsOptionItem(
                            title = "Sabit Klasör Kullan",
                            description = "Tüm videolar seçtiğiniz klasöre indirilecek",
                            isSelected = !askEveryTime,
                            onClick = { askEveryTime = false }
                        )
                        
                        // Folder selection button (enabled only if fixed folder is selected)
                        FilledTonalButton(
                            onClick = { 
                                if (!askEveryTime) {
                                    folderPickerLauncher.launch(null)
                                }
                            },
                            enabled = !askEveryTime,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = selectedFolderPath?.let { "Klasör Değiştir" } ?: "Klasör Seç",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        
                        // Show selected folder path
                        if (!askEveryTime && selectedFolderPath != null) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = getFolderDisplayName(selectedFolderPath!!),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
                
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                )
                
                // YT-DLP UPDATE SECTION
                YtDlpUpdateSection()
                
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                )
                
                // COOKIE MANAGEMENT SECTION REMOVED
                // CookieManagementSection()
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Save button
                Button(
                    onClick = {
                        val newSettings = DownloadSettings(
                            askEveryTime = askEveryTime,
                            fixedFolderPath = if (askEveryTime) null else selectedFolderPath
                        )
                        onSaveSettings(newSettings)
                        onDismissRequest()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Kaydet", modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingsOptionItem(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        },
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

/**
 * Extract display name from folder URI
 */
private fun getFolderDisplayName(uri: String): String {
    return try {
        val parsedUri = Uri.parse(uri)
        val docId = DocumentsContract.getTreeDocumentId(parsedUri)
        val split = docId.split(":")
        if (split.size >= 2) {
            split[1].ifEmpty { "Root" }
        } else {
            "Seçili Klasör"
        }
    } catch (e: Exception) {
        "Seçili Klasör"
    }
}

/**
 * yt-dlp Update Section for Settings Modal
 */
@Composable
private fun YtDlpUpdateSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updateManager = remember { YtDlpUpdateManager.getInstance(context) }
    
    val updateState by updateManager.updateState.collectAsState()
    val currentVersion by updateManager.currentVersion.collectAsState()
    
    // Track if we should show the notification
    var showNotification by remember { mutableStateOf(false) }
    var notificationMessage by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    
    // Show notification when update completes
    LaunchedEffect(updateState) {
        when (val state = updateState) {
            is YtDlpUpdateManager.UpdateState.Success -> {
                if (!state.wasUpdated) {
                    notificationMessage = "✓ Zaten son sürümündesiniz: ${state.newVersion}"
                    isError = false
                    showNotification = true
                    // Auto-hide after 3 seconds
                    kotlinx.coroutines.delay(3000)
                    showNotification = false
                }
            }
            is YtDlpUpdateManager.UpdateState.Error -> {
                notificationMessage = state.message
                isError = true
                showNotification = true
                // Auto-hide after 4 seconds for errors
                kotlinx.coroutines.delay(4000)
                showNotification = false
            }
            else -> {}
        }
    }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Update,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "yt-dlp Motor Güncelleme",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = when (updateState) {
                is YtDlpUpdateManager.UpdateState.Success -> {
                    if ((updateState as YtDlpUpdateManager.UpdateState.Success).wasUpdated) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                }
                is YtDlpUpdateManager.UpdateState.Error -> 
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "yt-dlp Sürümü",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Text(
                        text = when (updateState) {
                            is YtDlpUpdateManager.UpdateState.Checking -> "Güncelleme kontrol ediliyor..."
                            is YtDlpUpdateManager.UpdateState.Updating -> "Güncelleniyor..."
                            is YtDlpUpdateManager.UpdateState.Success -> {
                                val state = updateState as YtDlpUpdateManager.UpdateState.Success
                                if (state.wasUpdated) {
                                    "Güncellendi! Sürüm: ${state.newVersion}"
                                } else {
                                    "Güncel: ${currentVersion ?: "Bilinmiyor"}"
                                }
                            }
                            is YtDlpUpdateManager.UpdateState.Error -> 
                                (updateState as YtDlpUpdateManager.UpdateState.Error).message
                            else -> currentVersion ?: "Sürüm bilgisi alınamadı"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when (updateState) {
                            is YtDlpUpdateManager.UpdateState.Success -> {
                                if ((updateState as YtDlpUpdateManager.UpdateState.Success).wasUpdated) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                }
                            }
                            is YtDlpUpdateManager.UpdateState.Error -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        }
                    )
                }
                
                val isUpdating = updateState is YtDlpUpdateManager.UpdateState.Checking || 
                                 updateState is YtDlpUpdateManager.UpdateState.Updating
                
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            updateManager.forceUpdate()
                        }
                    },
                    enabled = !isUpdating,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isUpdating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Güncelleniyor")
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Güncelle")
                    }
                }
            }
        }
        
        Text(
            text = "TikTok ve Instagram gibi platformlar için güncel yt-dlp gereklidir.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        
        // Inline notification card
        androidx.compose.animation.AnimatedVisibility(
            visible = showNotification,
            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = if (isError) {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                } else {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                },
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = notificationMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { showNotification = false },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Kapat",
                            tint = if (isError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}


/**
 * Format file size in human readable format
 */
private fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${size / 1024} KB"
        else -> "${size / (1024 * 1024)} MB"
    }
}
