package com.example.simpledownloader.ui.screens.history

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simpledownloader.data.local.dao.DownloadDao
import com.example.simpledownloader.data.local.entity.DownloadEntity
import com.example.simpledownloader.data.preferences.DownloadSettingsManager
import com.example.simpledownloader.domain.model.DownloadSettings
import com.example.simpledownloader.utils.FileHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

/**
 * ViewModel for History Screen
 * Manages download history from Room database
 */
class HistoryViewModel(
    private val downloadDao: DownloadDao,
    private val settingsManager: DownloadSettingsManager,
    private val context: Context
) : ViewModel() {
    
    private val _downloads = MutableStateFlow<List<DownloadEntity>>(emptyList())
    val downloads: StateFlow<List<DownloadEntity>> = _downloads.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _downloadSettings = MutableStateFlow(settingsManager.getCurrentSettings())
    val downloadSettings: StateFlow<DownloadSettings> = _downloadSettings.asStateFlow()
    
    init {
        loadDownloads()
        observeSettings()
    }
    
    /**
     * Load all downloads from database
     */
    fun loadDownloads() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                downloadDao.getAllDownloads().collect { downloadList ->
                    _downloads.value = downloadList
                    _isLoading.value = false
                    Timber.d("Loaded ${downloadList.size} downloads from database")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading downloads")
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Observe settings changes
     */
    private fun observeSettings() {
        viewModelScope.launch {
            settingsManager.settings.collect { settings ->
                _downloadSettings.value = settings
                Timber.d("Settings updated: $settings")
            }
        }
    }
    
    /**
     * Delete a download from list only (keep file on phone)
     */
    fun deleteDownloadFromListOnly(download: DownloadEntity) {
        viewModelScope.launch {
            try {
                downloadDao.deleteDownload(download)
                Timber.d("Deleted download from list: ${download.title}")
            } catch (e: Exception) {
                Timber.e(e, "Error deleting download from list")
            }
        }
    }
    
    /**
     * Delete a download from phone storage (delete both file and database record)
     */
    fun deleteDownloadFromPhone(download: DownloadEntity) {
        viewModelScope.launch {
            try {
                // Delete file from phone storage (handles both MediaStore URI and regular File)
                val path = download.filePath
                if (path.startsWith("content://")) {
                    try {
                        context.contentResolver.delete(android.net.Uri.parse(path), null, null)
                        Timber.d("Deleted content URI: $path")
                    } catch (e: Exception) {
                        Timber.w(e, "Could not delete content URI: $path")
                    }
                } else {
                    val file = File(path)
                    if (file.exists()) {
                        val deleted = file.delete()
                        if (deleted) {
                            Timber.d("Deleted file from phone: ${download.filePath}")
                        } else {
                            Timber.w("Failed to delete file: ${download.filePath}")
                        }
                    }
                }
                
                // Delete from database
                downloadDao.deleteDownload(download)
                Timber.d("Deleted download from phone and list: ${download.title}")
            } catch (e: Exception) {
                Timber.e(e, "Error deleting download from phone")
            }
        }
    }
    
    /**
     * Open folder containing the downloaded file
     */
    fun openFolder(filePath: String) {
        FileHelper.openFileLocation(context, filePath)
    }
    
    /**
     * Clear all download history
     */
    fun clearAllHistory() {
        viewModelScope.launch {
            try {
                downloadDao.clearAll()
                Timber.d("Cleared all download history")
            } catch (e: Exception) {
                Timber.e(e, "Error clearing history")
            }
        }
    }
    
    /**
     * Update download settings
     */
    fun updateDownloadSettings(settings: DownloadSettings) {
        settingsManager.saveSettings(settings)
        Timber.d("Updated download settings: $settings")
    }
}
