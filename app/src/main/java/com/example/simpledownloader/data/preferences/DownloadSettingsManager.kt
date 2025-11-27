package com.example.simpledownloader.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.example.simpledownloader.domain.model.DownloadSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * Manager for download settings using SharedPreferences
 */
class DownloadSettingsManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    
    private val _settings = MutableStateFlow(loadSettings())
    val settings: Flow<DownloadSettings> = _settings.asStateFlow()
    
    /**
     * Load settings from SharedPreferences
     */
    private fun loadSettings(): DownloadSettings {
        val askEveryTime = prefs.getBoolean(KEY_ASK_EVERY_TIME, true)
        val fixedFolderPath = prefs.getString(KEY_FIXED_FOLDER, null)
        
        Timber.d("Loaded settings: askEveryTime=$askEveryTime, fixedFolderPath=$fixedFolderPath")
        
        return DownloadSettings(
            askEveryTime = askEveryTime,
            fixedFolderPath = fixedFolderPath
        )
    }
    
    /**
     * Save settings to SharedPreferences
     */
    fun saveSettings(settings: DownloadSettings) {
        prefs.edit().apply {
            putBoolean(KEY_ASK_EVERY_TIME, settings.askEveryTime)
            putString(KEY_FIXED_FOLDER, settings.fixedFolderPath)
            apply()
        }
        
        _settings.value = settings
        Timber.d("Saved settings: $settings")
    }
    
    /**
     * Get current settings synchronously
     */
    fun getCurrentSettings(): DownloadSettings {
        return _settings.value
    }
    
    companion object {
        private const val PREFS_NAME = "download_settings"
        private const val KEY_ASK_EVERY_TIME = "download_ask_every_time"
        private const val KEY_FIXED_FOLDER = "download_fixed_folder"
    }
}
