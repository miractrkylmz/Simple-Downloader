package com.example.simpledownloader.ui.screens.history

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.simpledownloader.ui.components.CustomDialog
import com.example.simpledownloader.ui.components.CustomSettingsModal
import com.example.simpledownloader.ui.components.HistoryItem
import com.example.simpledownloader.ui.components.SettingsButton
import java.io.File

/**
 * History Screen - Displays download history with settings
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val downloads by viewModel.downloads.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val downloadSettings by viewModel.downloadSettings.collectAsState()
    
    var showClearAllDialog by remember { mutableStateOf(false) }
    var showSettingsModal by remember { mutableStateOf(false) }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Custom Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "İndirme Geçmişi",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${downloads.size} video",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Action buttons
                if (downloads.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Settings button - Minimal design
                        SettingsButton(onClick = { showSettingsModal = true })
                        
                        // Clear all button - Purple theme
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable { showClearAllDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Tümünü Sil",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
            
            // Content
            when {
                isLoading -> {
                    // Loading state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                downloads.isEmpty() -> {
                    // Empty state
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "📥",
                            style = MaterialTheme.typography.displayLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Henüz İndirme Yok",
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "İndirdiğiniz videolar burada görünecek",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                else -> {
                    // History list
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 0.dp, start = 16.dp, end = 16.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = downloads,
                            key = { it.id }
                        ) { download ->
                            HistoryItem(
                                download = download,
                                onOpenClick = {
                                    // Try to open the downloaded file (video or image)
                                    try {
                                        val path = download.filePath
                                        val uri = if (path.startsWith("content://")) {
                                            android.net.Uri.parse(path)
                                        } else {
                                            val file = File(path)
                                            if (!file.exists()) {
                                                Toast.makeText(context, "Dosya bulunamadı", Toast.LENGTH_SHORT).show()
                                                return@HistoryItem
                                            }
                                            FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                file
                                            )
                                        }
                                        
                                        val isImage = download.mediaType == "IMAGE" ||
                                                path.endsWith(".jpg", true) ||
                                                path.endsWith(".jpeg", true) ||
                                                path.endsWith(".png", true) ||
                                                path.endsWith(".webp", true) ||
                                                path.endsWith(".gif", true)
                                        
                                        val mimeType = if (isImage) "image/*" else "video/*"
                                        val chooserTitle = if (isImage) "Fotoğrafı görüntüle" else "Video ile aç"
                                        
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, mimeType)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(Intent.createChooser(intent, chooserTitle))
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            context,
                                            "Dosya açılamadı: ${e.message}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                onDeleteFromListOnly = {
                                    viewModel.deleteDownloadFromListOnly(download)
                                    Toast.makeText(
                                        context,
                                        "Listeden silindi (dosya korundu)",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onDeleteFromPhone = {
                                    viewModel.deleteDownloadFromPhone(download)
                                    Toast.makeText(
                                        context,
                                        "Telefondan silindi",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onOpenFolderClick = {
                                    viewModel.openFolder(download.filePath)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Clear all confirmation dialog (Custom)
    if (showClearAllDialog) {
        CustomDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = "Tüm Geçmişi Sil",
            message = "Tüm indirme geçmişini silmek istediğinizden emin misiniz? Bu işlem geri alınamaz. Not: Dosyalar telefonunuzda kalacak, sadece geçmiş kayıtları silinecek.",
            confirmButtonText = "Sil",
            dismissButtonText = "İptal",
            confirmButtonColor = MaterialTheme.colorScheme.error,
            onConfirmClick = {
                showClearAllDialog = false
                viewModel.clearAllHistory()
                Toast.makeText(
                    context,
                    "Tüm geçmiş silindi",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDismissClick = { showClearAllDialog = false }
        )
    }
    
    // Settings modal
    if (showSettingsModal) {
        CustomSettingsModal(
            currentSettings = downloadSettings,
            onDismissRequest = { showSettingsModal = false },
            onSaveSettings = { newSettings ->
                viewModel.updateDownloadSettings(newSettings)
                Toast.makeText(
                    context,
                    "Ayarlar kaydedildi",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }
}
